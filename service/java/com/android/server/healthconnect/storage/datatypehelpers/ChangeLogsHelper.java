/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.healthconnect.storage.datatypehelpers;

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.Constants.DELETE;
import static android.health.connect.Constants.UPSERT;

import static com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper.DEFAULT_CHANGE_LOG_TIME_PERIOD_IN_DAYS;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import static java.lang.Integer.min;

import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.accesslog.AccessLog.OperationType;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse.DeletedLog;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.util.ArrayMap;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A helper class to fetch and store the change logs.
 *
 * @hide
 */
public final class ChangeLogsHelper extends DatabaseHelper {
    public static final String TABLE_NAME = "change_logs_table";
    @VisibleForTesting public static final String RECORD_TYPE_COLUMN_NAME = "record_type";
    @VisibleForTesting public static final String APP_ID_COLUMN_NAME = "app_id";
    @VisibleForTesting public static final String UUIDS_COLUMN_NAME = "uuids";
    @VisibleForTesting public static final String OPERATION_TYPE_COLUMN_NAME = "operation_type";
    @VisibleForTesting public static final String TIME_COLUMN_NAME = "time";
    private static final int NUM_COLS = 5;

    private final TransactionManager mTransactionManager;

    public ChangeLogsHelper(
            TransactionManager transactionManager, DatabaseHelpers databaseHelpers) {
        super(databaseHelpers);
        mTransactionManager = transactionManager;
    }

    public static DeleteTableRequest getDeleteRequestForAutoDelete() {
        return new DeleteTableRequest(TABLE_NAME)
                .setTimeFilter(
                        TIME_COLUMN_NAME,
                        Instant.EPOCH.toEpochMilli(),
                        Instant.now()
                                .minus(DEFAULT_CHANGE_LOG_TIME_PERIOD_IN_DAYS, ChronoUnit.DAYS)
                                .toEpochMilli());
    }

    public static CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(TABLE_NAME, getColumnInfo())
                .createIndexOn(RECORD_TYPE_COLUMN_NAME)
                .createIndexOn(APP_ID_COLUMN_NAME);
    }

    /** Returns datatypes being written/updates in past 30 days. */
    public Set<Integer> getRecordTypesWrittenInPast30Days() {
        Set<Integer> recordTypesWrittenInPast30Days = new HashSet<>();
        WhereClauses whereClauses =
                new WhereClauses(AND)
                        .addWhereEqualsClause(
                                OPERATION_TYPE_COLUMN_NAME,
                                String.valueOf(OperationType.OPERATION_TYPE_UPSERT))
                        .addWhereGreaterThanOrEqualClause(
                                TIME_COLUMN_NAME,
                                Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli());

        final ReadTableRequest readTableRequest =
                new ReadTableRequest(TABLE_NAME)
                        .setColumnNames(List.of(RECORD_TYPE_COLUMN_NAME))
                        .setWhereClause(whereClauses)
                        .setDistinctClause(true);

        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            while (cursor.moveToNext()) {
                recordTypesWrittenInPast30Days.add(getCursorInt(cursor, RECORD_TYPE_COLUMN_NAME));
            }
        }
        return recordTypesWrittenInPast30Days;
    }

    @Override
    protected String getMainTableName() {
        return TABLE_NAME;
    }

    /** Returns change logs post the time when {@code changeLogTokenRequest} was generated */
    public ChangeLogsResponse getChangeLogs(
            AppInfoHelper appInfoHelper,
            ChangeLogsRequestHelper.TokenRequest changeLogTokenRequest,
            ChangeLogsRequest changeLogsRequest,
            ChangeLogsRequestHelper changeLogsRequestHelper) {
        long token = changeLogTokenRequest.getRowIdChangeLogs();
        WhereClauses whereClause =
                new WhereClauses(AND)
                        .addWhereGreaterThanClause(PRIMARY_COLUMN_NAME, String.valueOf(token));
        if (!changeLogTokenRequest.getRecordTypes().isEmpty()) {
            whereClause.addWhereInIntsClause(
                    RECORD_TYPE_COLUMN_NAME, changeLogTokenRequest.getRecordTypes());
        }

        if (!changeLogTokenRequest.getPackageNamesToFilter().isEmpty()) {
            whereClause.addWhereInLongsClause(
                    APP_ID_COLUMN_NAME,
                    appInfoHelper.getAppInfoIds(changeLogTokenRequest.getPackageNamesToFilter()));
        }

        // We set limit size to requested pageSize plus extra 1 record so that if number of records
        // queried is more than pageSize we know there are more records available to return for the
        // next read.
        int pageSize = changeLogsRequest.getPageSize();
        final ReadTableRequest readTableRequest =
                new ReadTableRequest(TABLE_NAME).setWhereClause(whereClause).setLimit(pageSize + 1);

        Map<Integer, ChangeLogs> operationToChangeLogMap = new ArrayMap<>();
        long nextChangesToken = DEFAULT_LONG;
        boolean hasMoreRecords = false;
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            int count = 0;
            while (cursor.moveToNext()) {
                if (count >= pageSize) {
                    hasMoreRecords = true;
                    break;
                }
                count += addChangeLogs(cursor, operationToChangeLogMap);
                nextChangesToken = getCursorInt(cursor, PRIMARY_COLUMN_NAME);
            }
        }

        String nextToken =
                nextChangesToken != DEFAULT_LONG
                        ? changeLogsRequestHelper.getNextPageToken(
                                changeLogTokenRequest, nextChangesToken)
                        : changeLogsRequest.getToken();

        return new ChangeLogsResponse(operationToChangeLogMap, nextToken, hasMoreRecords);
    }

    public long getLatestRowId() {
        return mTransactionManager.getLastRowIdFor(TABLE_NAME);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private static int addChangeLogs(Cursor cursor, Map<Integer, ChangeLogs> changeLogs) {
        @RecordTypeIdentifier.RecordType
        int recordType = getCursorInt(cursor, RECORD_TYPE_COLUMN_NAME);
        @OperationType.OperationTypes
        int operationType = getCursorInt(cursor, OPERATION_TYPE_COLUMN_NAME);
        List<UUID> uuidList = StorageUtils.getCursorUUIDList(cursor, UUIDS_COLUMN_NAME);
        long appId = getCursorLong(cursor, APP_ID_COLUMN_NAME);
        changeLogs.putIfAbsent(
                operationType,
                new ChangeLogs(operationType, getCursorLong(cursor, TIME_COLUMN_NAME)));
        changeLogs.get(operationType).addUUIDs(recordType, appId, uuidList);
        return uuidList.size();
    }

    private static List<Pair<String, String>> getColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>(NUM_COLS);
        columnInfo.add(new Pair<>(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT));
        columnInfo.add(new Pair<>(RECORD_TYPE_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(APP_ID_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(UUIDS_COLUMN_NAME, BLOB_NON_NULL));
        columnInfo.add(new Pair<>(OPERATION_TYPE_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(TIME_COLUMN_NAME, INTEGER));

        return columnInfo;
    }

    public static List<DeletedLog> getDeletedLogs(Map<Integer, ChangeLogs> operationToChangeLogs) {
        ChangeLogs logs = operationToChangeLogs.get(DELETE);

        if (!Objects.isNull(logs)) {
            List<UUID> ids = logs.getUUIds();
            long timeStamp = logs.getChangeLogTimeStamp();
            List<DeletedLog> deletedLogs = new ArrayList<>(ids.size());
            for (UUID id : ids) {
                deletedLogs.add(new DeletedLog(id.toString(), timeStamp));
            }

            return deletedLogs;
        }
        return new ArrayList<>();
    }

    public static Map<Integer, List<UUID>> getRecordTypeToInsertedUuids(
            Map<Integer, ChangeLogs> operationToChangeLogs) {
        ChangeLogs logs = operationToChangeLogs.getOrDefault(UPSERT, null);

        if (!Objects.isNull(logs)) {
            return logs.getRecordTypeToUUIDMap();
        }

        return new ArrayMap<>(0);
    }

    public static final class ChangeLogs {
        private final Map<RecordTypeAndAppIdPair, List<UUID>> mRecordTypeAndAppIdToUUIDMap =
                new ArrayMap<>();
        @OperationType.OperationTypes private final int mOperationType;
        private final long mChangeLogTimeStamp;

        /**
         * Creates a change logs object used to add a new change log for {@code operationType}
         * logged at time {@code timeStamp }
         *
         * @param operationType Type of the operation for which change log is added whether insert
         *     or delete.
         * @param timeStamp Time when the change log is added.
         */
        public ChangeLogs(@OperationType.OperationTypes int operationType, long timeStamp) {
            mOperationType = operationType;
            mChangeLogTimeStamp = timeStamp;
        }

        private Map<Integer, List<UUID>> getRecordTypeToUUIDMap() {
            Map<Integer, List<UUID>> recordTypeToUUIDMap = new ArrayMap<>();
            mRecordTypeAndAppIdToUUIDMap.forEach(
                    (recordTypeAndAppIdPair, uuids) -> {
                        recordTypeToUUIDMap.putIfAbsent(
                                recordTypeAndAppIdPair.getRecordType(), new ArrayList<>());
                        Objects.requireNonNull(
                                        recordTypeToUUIDMap.get(
                                                recordTypeAndAppIdPair.getRecordType()))
                                .addAll(uuids);
                    });
            return recordTypeToUUIDMap;
        }

        public List<UUID> getUUIds() {
            return mRecordTypeAndAppIdToUUIDMap.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        public long getChangeLogTimeStamp() {
            return mChangeLogTimeStamp;
        }

        /** Function to add an uuid corresponding to given pair of @recordType and @appId */
        @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
        public void addUUID(
                @RecordTypeIdentifier.RecordType int recordType, long appId, UUID uuid) {
            Objects.requireNonNull(uuid);

            RecordTypeAndAppIdPair recordTypeAndAppIdPair =
                    new RecordTypeAndAppIdPair(recordType, appId);
            mRecordTypeAndAppIdToUUIDMap.putIfAbsent(recordTypeAndAppIdPair, new ArrayList<>());
            mRecordTypeAndAppIdToUUIDMap.get(recordTypeAndAppIdPair).add(uuid);
        }

        /**
         * @return List of {@link UpsertTableRequest} for change log table as per {@code
         *     mRecordTypeAndAppIdPairToUUIDMap}
         */
        public List<UpsertTableRequest> getUpsertTableRequests() {
            List<UpsertTableRequest> requests =
                    new ArrayList<>(mRecordTypeAndAppIdToUUIDMap.size());
            mRecordTypeAndAppIdToUUIDMap.forEach(
                    (recordTypeAndAppIdPair, uuids) -> {
                        for (int i = 0; i < uuids.size(); i += DEFAULT_PAGE_SIZE) {
                            ContentValues contentValues = new ContentValues();
                            contentValues.put(
                                    RECORD_TYPE_COLUMN_NAME,
                                    recordTypeAndAppIdPair.getRecordType());
                            contentValues.put(
                                    APP_ID_COLUMN_NAME, recordTypeAndAppIdPair.getAppId());
                            contentValues.put(OPERATION_TYPE_COLUMN_NAME, mOperationType);
                            contentValues.put(TIME_COLUMN_NAME, mChangeLogTimeStamp);
                            contentValues.put(
                                    UUIDS_COLUMN_NAME,
                                    StorageUtils.getSingleByteArray(
                                            uuids.subList(
                                                    i, min(i + DEFAULT_PAGE_SIZE, uuids.size()))));
                            requests.add(new UpsertTableRequest(TABLE_NAME, contentValues));
                        }
                    });
            return requests;
        }

        /** Adds {@code uuids} to {@link ChangeLogs}. */
        @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
        public ChangeLogs addUUIDs(
                @RecordTypeIdentifier.RecordType int recordType, long appId, List<UUID> uuids) {
            RecordTypeAndAppIdPair recordTypeAndAppIdPair =
                    new RecordTypeAndAppIdPair(recordType, appId);
            mRecordTypeAndAppIdToUUIDMap.putIfAbsent(recordTypeAndAppIdPair, new ArrayList<>());
            mRecordTypeAndAppIdToUUIDMap.get(recordTypeAndAppIdPair).addAll(uuids);
            return this;
        }

        /** A helper class to create a pair of recordType and appId */
        private static final class RecordTypeAndAppIdPair {
            private final int mRecordType;
            private final long mAppId;

            private RecordTypeAndAppIdPair(int recordType, long appId) {
                mRecordType = recordType;
                mAppId = appId;
            }

            public int getRecordType() {
                return mRecordType;
            }

            public long getAppId() {
                return mAppId;
            }

            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || obj.getClass() != this.getClass()) return false;
                RecordTypeAndAppIdPair recordTypeAndAppIdPair = (RecordTypeAndAppIdPair) obj;
                return (recordTypeAndAppIdPair.mRecordType == this.mRecordType
                        && recordTypeAndAppIdPair.mAppId == this.mAppId);
            }

            public int hashCode() {
                return Objects.hash(this.mRecordType, this.mAppId);
            }
        }
    }

    /** A class to represent the token for pagination for the change logs response */
    public static final class ChangeLogsResponse {
        private final Map<Integer, ChangeLogsHelper.ChangeLogs> mChangeLogsMap;
        private final String mNextPageToken;
        private final boolean mHasMorePages;

        public ChangeLogsResponse(
                Map<Integer, ChangeLogsHelper.ChangeLogs> changeLogsMap,
                String nextPageToken,
                boolean hasMorePages) {
            mChangeLogsMap = changeLogsMap;
            mNextPageToken = nextPageToken;
            mHasMorePages = hasMorePages;
        }

        /** Returns map of operation type to change logs */
        public Map<Integer, ChangeLogs> getChangeLogsMap() {
            return mChangeLogsMap;
        }

        /** Returns the next page token for the change logs */
        public String getNextPageToken() {
            return mNextPageToken;
        }

        /** Returns true if there are more change logs to be read */
        public boolean hasMorePages() {
            return mHasMorePages;
        }
    }
}
