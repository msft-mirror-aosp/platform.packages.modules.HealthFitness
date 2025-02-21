/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.server.healthconnect.backuprestore;

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_UNKNOWN;

import static com.android.healthfitness.flags.Flags.FLAG_CLOUD_BACKUP_AND_RESTORE;
import static com.android.server.healthconnect.backuprestore.RecordProtoConverter.PROTO_VERSION;
import static com.android.server.healthconnect.exportimport.DatabaseMerger.RECORD_TYPE_MIGRATION_ORDERING_OVERRIDES;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.FlaggedApi;
import android.annotation.Nullable;
import android.database.Cursor;
import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.backuprestore.BackupChange;
import android.health.connect.backuprestore.GetChangesForBackupResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.PlannedExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.util.Pair;

import com.android.server.healthconnect.proto.backuprestore.BackupData;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BackupChangeTokenHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Performs various operations on the Health Connect database for cloud backup.
 *
 * @hide
 */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
public class CloudBackupDatabaseHelper {
    private final AppInfoHelper mAppInfoHelper;
    private final TransactionManager mTransactionManager;
    private final DeviceInfoHelper mDeviceInfoHelper;
    private final HealthConnectMappings mHealthConnectMappings;
    private final InternalHealthConnectMappings mInternalHealthConnectMappings;
    private final ChangeLogsHelper mChangeLogsHelper;
    private final ChangeLogsRequestHelper mChangeLogsRequestHelper;
    private final RecordProtoConverter mRecordProtoConverter = new RecordProtoConverter();
    private final List<Integer> mRecordTypes;

    private static final String TAG = "CloudBackupRestoreDatabaseHelper";

    public CloudBackupDatabaseHelper(
            TransactionManager transactionManager,
            AppInfoHelper appInfoHelper,
            DeviceInfoHelper deviceInfoHelper,
            HealthConnectMappings healthConnectMappings,
            InternalHealthConnectMappings internalHealthConnectMappings,
            ChangeLogsHelper changeLogsHelper,
            ChangeLogsRequestHelper changeLogsRequestHelper) {
        mTransactionManager = transactionManager;
        mAppInfoHelper = appInfoHelper;
        mDeviceInfoHelper = deviceInfoHelper;
        mHealthConnectMappings = healthConnectMappings;
        mInternalHealthConnectMappings = internalHealthConnectMappings;
        mChangeLogsHelper = changeLogsHelper;
        mChangeLogsRequestHelper = changeLogsRequestHelper;
        mRecordTypes =
                Stream.concat(
                                RECORD_TYPE_MIGRATION_ORDERING_OVERRIDES.stream()
                                        .flatMap(List::stream),
                                mHealthConnectMappings
                                        .getRecordIdToExternalRecordClassMap()
                                        .keySet()
                                        .stream())
                        .distinct()
                        .toList();
    }

    /**
     * Verifies whether the provided change logs token is still valid. The token is valid if the
     * next change log still exists or the token points to the end of the change logs table.
     */
    boolean isChangeLogsTokenValid(@Nullable String changeLogsPageToken) {
        if (changeLogsPageToken == null) {
            return false;
        }
        ChangeLogsRequestHelper.TokenRequest tokenRequest =
                mChangeLogsRequestHelper.getRequest(/* packageName= */ "", changeLogsPageToken);
        if (tokenRequest.getRowIdChangeLogs() == mChangeLogsHelper.getLatestRowId()) {
            return true;
        }
        WhereClauses whereClauses =
                new WhereClauses(AND)
                        .addWhereEqualsClause(
                                PRIMARY_COLUMN_NAME,
                                String.valueOf(tokenRequest.getRowIdChangeLogs() + 1));
        ReadTableRequest readTableRequest =
                new ReadTableRequest(ChangeLogsHelper.TABLE_NAME).setWhereClause(whereClauses);
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            return cursor.getCount() == 1;
        }
    }

    /**
     * Retrieves backup changes from the data tables, used for the initial call of a full data
     * backup.
     */
    GetChangesForBackupResponse getChangesAndTokenFromDataTables() {
        return getChangesAndTokenFromDataTables(
                RECORD_TYPE_UNKNOWN, EMPTY_PAGE_TOKEN.encode(), null);
    }

    /**
     * Retrieves backup changes from data tables, used for the subsequent calls of a full data
     * backup.
     */
    GetChangesForBackupResponse getChangesAndTokenFromDataTables(
            @RecordTypeIdentifier.RecordType int dataRecordType,
            long dataTablePageToken,
            @Nullable String changeLogsPageToken) {
        // For the first call of a full data backup, page token of the chane logs is passed as null
        // so we generate one to be used for incremental backups. In subsequent calls of a full data
        // backup, we just need to preserve the previous page token instead of creating a new one.
        String changeLogsTablePageToken =
                changeLogsPageToken == null ? getChangeLogsPageToken() : changeLogsPageToken;

        List<BackupChange> backupChanges = new ArrayList<>();
        long nextDataTablePageToken = dataTablePageToken;
        int pageSize = DEFAULT_PAGE_SIZE;
        int nextRecordType = dataRecordType;

        for (var recordType : mRecordTypes) {
            RecordHelper<?> recordHelper =
                    mInternalHealthConnectMappings.getRecordHelper(recordType);
            if (nextRecordType != RECORD_TYPE_UNKNOWN && recordType != nextRecordType) {
                // Skip the current record type as it has already been backed up.
                continue;
            }
            Set<String> grantedExtraReadPermissions =
                    Set.copyOf(recordHelper.getExtraReadPermissions());
            while (pageSize > 0) {
                ReadRecordsRequestUsingFilters<? extends Record> readRecordsRequest =
                        new ReadRecordsRequestUsingFilters.Builder<>(
                                        mHealthConnectMappings
                                                .getRecordIdToExternalRecordClassMap()
                                                .get(recordType))
                                .setPageSize(pageSize)
                                .setPageToken(nextDataTablePageToken)
                                .build();
                ReadTransactionRequest readTransactionRequest =
                        new ReadTransactionRequest(
                                mAppInfoHelper,
                                // Keep as empty to avoid package name filters.
                                /* callingPackageName= */ "",
                                readRecordsRequest.toReadRecordsRequestParcel(),
                                // Avoid start date access based filters.
                                /* startDateAccessMillis= */ DEFAULT_LONG,
                                // Avoid package name filters.
                                /* enforceSelfRead= */ false,
                                grantedExtraReadPermissions,
                                // Only used when querying the API call quota. Cloud backup &
                                // restore APIs enforce no quota limits so this value is irrelevant.
                                /* isInForeground= */ true);
                Pair<List<RecordInternal<?>>, PageTokenWrapper> readResult =
                        mTransactionManager.readRecordsAndPageTokenWithoutAccessLogs(
                                readTransactionRequest,
                                mAppInfoHelper,
                                mDeviceInfoHelper,
                                /* packageNamesByAppIds= */ null);
                backupChanges.addAll(convertRecordsToBackupChange(readResult.first));
                nextDataTablePageToken = readResult.second.encode();
                pageSize = DEFAULT_PAGE_SIZE - backupChanges.size();
                nextRecordType = recordHelper.getRecordIdentifier();
                if (nextDataTablePageToken == EMPTY_PAGE_TOKEN.encode()) {
                    int recordIndex = mRecordTypes.indexOf(recordType);
                    // An empty page token indicates no more data in one data table, update the
                    // data type to the next data type.
                    if (recordIndex + 1 >= mRecordTypes.size()) {
                        nextRecordType = RECORD_TYPE_UNKNOWN;
                    } else {
                        RecordHelper<?> nextRecordHelper =
                                mInternalHealthConnectMappings.getRecordHelper(
                                        mRecordTypes.get(recordIndex + 1));
                        nextRecordType = nextRecordHelper.getRecordIdentifier();
                    }
                    break;
                }
            }
            // Retrieved data reaches the max page size.
            if (pageSize <= 0) {
                break;
            }
        }
        String backupChangeTokenRowId =
                BackupChangeTokenHelper.getBackupChangeTokenRowId(
                        mTransactionManager,
                        nextRecordType,
                        nextDataTablePageToken,
                        changeLogsTablePageToken);
        return new GetChangesForBackupResponse(
                PROTO_VERSION, backupChanges, backupChangeTokenRowId);
    }

    private String getChangeLogsPageToken() {
        long rowId = mChangeLogsHelper.getLatestRowId();
        ChangeLogsRequestHelper.TokenRequest tokenRequest =
                new ChangeLogsRequestHelper.TokenRequest(
                        List.of(),
                        mRecordTypes,
                        // Pass empty string to avoid package filters.
                        /* requestingPackageName= */ "",
                        rowId);
        return mChangeLogsRequestHelper.getNextPageToken(tokenRequest, rowId);
    }

    /** Gets incremental data changes based on change logs. */
    GetChangesForBackupResponse getIncrementalChanges(@Nullable String changeLogsPageToken) {
        if (changeLogsPageToken == null) {
            throw new IllegalStateException("No proper change logs token");
        }
        ChangeLogsRequestHelper.TokenRequest changeLogsTokenRequest =
                mChangeLogsRequestHelper.getRequest(/* packageName= */ "", changeLogsPageToken);
        // Use the default page size (1000) for now.
        ChangeLogsRequest request = new ChangeLogsRequest.Builder(changeLogsPageToken).build();
        ChangeLogsHelper.ChangeLogsResponse changeLogsResponse =
                mChangeLogsHelper.getChangeLogs(
                        mAppInfoHelper, changeLogsTokenRequest, request, mChangeLogsRequestHelper);

        // Only UUIDs for upsert requests are returned.
        Map<Integer, List<UUID>> recordTypeToInsertedUuids =
                ChangeLogsHelper.getRecordTypeToInsertedUuids(
                        changeLogsResponse.getChangeLogsMap());

        Set<String> grantedExtraReadPermissions =
                recordTypeToInsertedUuids.keySet().stream()
                        .map(mInternalHealthConnectMappings::getRecordHelper)
                        .flatMap(recordHelper -> recordHelper.getExtraReadPermissions().stream())
                        .collect(Collectors.toSet());

        List<RecordInternal<?>> internalRecords =
                mTransactionManager.readRecordsByIdsWithoutAccessLogs(
                        new ReadTransactionRequest(
                                mAppInfoHelper,
                                /* packageName= */ "",
                                recordTypeToInsertedUuids,
                                DEFAULT_LONG,
                                grantedExtraReadPermissions,
                                /* isInForeground= */ true,
                                /* isReadingSelfData= */ false),
                        mAppInfoHelper,
                        mDeviceInfoHelper);

        // Read the exercise sessions that refer to any training plans included in the changes and
        // append them to the list of changes. This is to always have exercise sessions restore
        // after planned sessions that they refer to.
        var sessionIds = new ArrayList<UUID>();
        for (var record : internalRecords) {
            if (record instanceof PlannedExerciseSessionRecordInternal plannedSession) {
                var completedSessionId = plannedSession.getCompletedExerciseSessionId();
                if (completedSessionId != null) {
                    sessionIds.add(completedSessionId);
                }
            }
        }
        List<RecordInternal<?>> exerciseSessions =
                mTransactionManager.readRecordsByIdsWithoutAccessLogs(
                        new ReadTransactionRequest(
                                mAppInfoHelper,
                                /* packageName= */ "",
                                Map.of(
                                        RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION,
                                        sessionIds),
                                DEFAULT_LONG,
                                grantedExtraReadPermissions,
                                /* isInForeground= */ true,
                                /* isReadingSelfData= */ false),
                        mAppInfoHelper,
                        mDeviceInfoHelper);
        internalRecords.addAll(exerciseSessions);

        List<BackupChange> backupChanges =
                new ArrayList<>(convertRecordsToBackupChange(internalRecords));

        // Include UUIDs for all deleted records.
        List<ChangeLogsResponse.DeletedLog> deletedLogs =
                ChangeLogsHelper.getDeletedLogs(changeLogsResponse.getChangeLogsMap());
        backupChanges.addAll(convertDeletedLogsToBackupChange(deletedLogs));

        String backupChangeTokenRowId =
                BackupChangeTokenHelper.getBackupChangeTokenRowId(
                        mTransactionManager,
                        RECORD_TYPE_UNKNOWN,
                        EMPTY_PAGE_TOKEN.encode(),
                        changeLogsResponse.getNextPageToken());
        return new GetChangesForBackupResponse(
                PROTO_VERSION, backupChanges, backupChangeTokenRowId);
    }

    private List<BackupChange> convertRecordsToBackupChange(List<RecordInternal<?>> records) {
        return records.stream()
                .map(
                        record -> {
                            if (record.getUuid() == null) {
                                throw new IllegalStateException(
                                        "Record does not have a UUID, this should not happen");
                            }
                            return new BackupChange(
                                    record.getUuid().toString(),
                                    /* isDeletion= */ false,
                                    serializeRecordInternal(record));
                        })
                .toList();
    }

    private List<BackupChange> convertDeletedLogsToBackupChange(
            List<ChangeLogsResponse.DeletedLog> deletedLogs) {
        return deletedLogs.stream()
                .map(
                        deletedLog ->
                                new BackupChange(
                                        deletedLog.getDeletedRecordId(),
                                        /* isDeletion= */ true,
                                        null))
                .toList();
    }

    private byte[] serializeRecordInternal(RecordInternal<?> recordInternal) {
        return BackupData.newBuilder()
                .setRecord(mRecordProtoConverter.toRecordProto(recordInternal))
                .build()
                .toByteArray();
    }
}
