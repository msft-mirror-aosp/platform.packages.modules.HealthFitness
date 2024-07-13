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

package com.android.server.healthconnect.exportimport;

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.Constants.MAXIMUM_PAGE_SIZE;
import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_PLANNED_EXERCISE_SESSION;

import static com.android.healthfitness.flags.Flags.exportImport;
import static com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper.APP_ID_PRIORITY_ORDER_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper.HEALTH_DATA_CATEGORY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper.PRIORITY_TABLE_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.DELIMITER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.database.Cursor;
import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.datatypes.Record;
import android.health.connect.internal.datatypes.PlannedExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.RecordMapper;
import android.util.ArrayMap;
import android.util.Pair;
import android.util.Slog;

import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.request.UpsertTransactionRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Merges a secondary database's contents with the HC database. This will be used in D2D migration
 * and Export/Import.
 *
 * @hide
 */
public final class DatabaseMerger {

    private static final String TAG = "HealthConnectDatabaseMerger";

    private final Context mContext;
    private final TransactionManager mTransactionManager;
    private final AppInfoHelper mAppInfoHelper;
    private final RecordMapper mRecordMapper;

    /*
     * Record types in this list will always be migrated such that the ordering here is respected.
     * When adding a new priority override, group the types that need to migrated together within
     * their own list. This makes the logical separate clear and also reduces storage usage during
     * migration, as we delete the original records
     */
    private static final List<List<Integer>> RECORD_TYPE_MIGRATION_ORDERING_OVERRIDES =
            List.of(
                    // Training plans must be migrated before exercise sessions. Exercise sessions
                    // may contain a reference to a training plan, so the training plan needs to
                    // exist so that the foreign key constraints are not violated.
                    List.of(RECORD_TYPE_PLANNED_EXERCISE_SESSION, RECORD_TYPE_EXERCISE_SESSION));

    public DatabaseMerger(@NonNull Context context) {
        requireNonNull(context);
        mContext = context;
        mTransactionManager = TransactionManager.getInitialisedInstance();
        mAppInfoHelper = AppInfoHelper.getInstance();
        mRecordMapper = RecordMapper.getInstance();
    }

    /** Merge data */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public synchronized void merge(HealthConnectDatabase stagedDatabase) {
        Slog.i(TAG, "Merging app info");

        Map<Long, String> stagedPackageNamesByAppIds = new ArrayMap<>();
        try (Cursor cursor = read(stagedDatabase, new ReadTableRequest(AppInfoHelper.TABLE_NAME))) {
            while (cursor.moveToNext()) {
                long rowId = getCursorLong(cursor, RecordHelper.PRIMARY_COLUMN_NAME);
                String packageName = getCursorString(cursor, AppInfoHelper.PACKAGE_COLUMN_NAME);
                String appName = getCursorString(cursor, AppInfoHelper.APPLICATION_COLUMN_NAME);
                stagedPackageNamesByAppIds.put(rowId, packageName);

                // If this package is not installed on the target device and is not present in the
                // health db, then fill the health db with the info from source db. According to the
                // security review b/341253579, we should not parse the imported icon.
                mAppInfoHelper.addOrUpdateAppInfoIfNotInstalled(
                        mContext, packageName, appName, false /* onlyReplace */);
            }
        }

        Slog.i(TAG, "Merging records");

        // Determine the order in which we should migrate data types. This involves first
        // migrating data types according to the specified ordering overrides. Remaining
        // records are migrated in no particular order.
        List<Integer> recordTypesWithOrderingOverrides =
                RECORD_TYPE_MIGRATION_ORDERING_OVERRIDES.stream().flatMap(List::stream).toList();
        List<Integer> recordTypesWithoutOrderingOverrides =
                mRecordMapper.getRecordIdToExternalRecordClassMap().keySet().stream()
                        .filter(it -> !recordTypesWithOrderingOverrides.contains(it))
                        .toList();

        // Migrate special case records in their defined order.
        for (List<Integer> recordTypeMigrationGroup : RECORD_TYPE_MIGRATION_ORDERING_OVERRIDES) {
            for (int recordTypeToMigrate : recordTypeMigrationGroup) {
                mergeRecordsOfType(
                        stagedDatabase,
                        stagedPackageNamesByAppIds,
                        recordTypeToMigrate,
                        mRecordMapper
                                .getRecordIdToExternalRecordClassMap()
                                .get(recordTypeToMigrate));
            }
            // Delete records within a group together, once all records within that group
            // have been migrated. This ensures referential integrity is preserved during
            // migration.
            for (int recordTypeToMigrate : recordTypeMigrationGroup) {
                deleteRecordsOfType(
                        stagedDatabase,
                        recordTypeToMigrate,
                        mRecordMapper
                                .getRecordIdToExternalRecordClassMap()
                                .get(recordTypeToMigrate));
            }
        }
        // Migrate remaining record types in no particular order.
        for (Integer recordTypeToMigrate : recordTypesWithoutOrderingOverrides) {
            Class<? extends Record> recordClass =
                    mRecordMapper.getRecordIdToExternalRecordClassMap().get(recordTypeToMigrate);
            mergeRecordsOfType(
                    stagedDatabase, stagedPackageNamesByAppIds, recordTypeToMigrate, recordClass);
            deleteRecordsOfType(stagedDatabase, recordTypeToMigrate, recordClass);
        }

        Slog.i(TAG, "Syncing app info records after restored data merge");
        mAppInfoHelper.syncAppInfoRecordTypesUsed();

        if (exportImport()) {
            Slog.i(TAG, "Merging priority list");
            mergePriorityList(stagedDatabase);
        }

        Slog.i(TAG, "Merging done");
    }

    private void mergePriorityList(HealthConnectDatabase stagedDatabase) {
        Map<Integer, List<String>> importPriorityMap = new HashMap<>();
        try (Cursor cursor = read(stagedDatabase, new ReadTableRequest(PRIORITY_TABLE_NAME))) {
            while (cursor.moveToNext()) {
                int dataCategory =
                        cursor.getInt(
                                cursor.getColumnIndexOrThrow(HEALTH_DATA_CATEGORY_COLUMN_NAME));
                List<Long> appIdsInOrder =
                        StorageUtils.getCursorLongList(
                                cursor, APP_ID_PRIORITY_ORDER_COLUMN_NAME, DELIMITER);
                importPriorityMap.put(
                        dataCategory, AppInfoHelper.getInstance().getPackageNames(appIdsInOrder));
            }
        }

        HealthDataCategoryPriorityHelper priorityHelper =
                HealthDataCategoryPriorityHelper.getInstance();
        importPriorityMap.forEach(
                (category, importPriorityList) -> {
                    if (importPriorityList.isEmpty()) {
                        return;
                    }

                    List<String> currentPriorityList =
                            priorityHelper.getPriorityOrder(category, mContext);
                    List<String> newPriorityList =
                            Stream.concat(currentPriorityList.stream(), importPriorityList.stream())
                                    .distinct()
                                    .toList();
                    priorityHelper.setPriorityOrder(category, newPriorityList);
                    Slog.d(
                            TAG,
                            "Added "
                                    + importPriorityList.size()
                                    + " apps to priority list of category "
                                    + category);
                });
    }

    private <T extends Record> void mergeRecordsOfType(
            HealthConnectDatabase stagedDatabase,
            Map<Long, String> stagedPackageNamesByAppIds,
            int recordType,
            Class<T> recordTypeClass) {
        RecordHelper<?> recordHelper = RecordHelperProvider.getRecordHelper(recordType);
        if (!StorageUtils.checkTableExists(stagedDatabase, recordHelper.getMainTableName())) {
            return;
        }

        // Read all the records of the given type from the staged db and insert them into the
        // existing healthconnect db.
        PageTokenWrapper currentToken = EMPTY_PAGE_TOKEN;
        do {
            var recordsToMergeAndToken =
                    getRecordsToMerge(
                            stagedDatabase,
                            stagedPackageNamesByAppIds,
                            recordTypeClass,
                            currentToken,
                            recordHelper);
            List<RecordInternal<?>> records = recordsToMergeAndToken.first;
            PageTokenWrapper token = recordsToMergeAndToken.second;
            if (records.isEmpty()) {
                Slog.d(TAG, "No records to merge: " + recordTypeClass);
                break;
            }
            Slog.d(TAG, "Found records to merge: " + recordTypeClass);
            if (recordType == RECORD_TYPE_PLANNED_EXERCISE_SESSION) {
                // For training plans we nullify any autogenerated references to exercise sessions.
                // When the corresponding exercise sessions get migrated, these references will be
                // automatically generated again.
                records.forEach(
                        it -> {
                            PlannedExerciseSessionRecordInternal record =
                                    (PlannedExerciseSessionRecordInternal) it;
                            record.setCompletedExerciseSessionId(null);
                        });
            }
            // Using null package name for making insertion for two reasons:
            // 1. we don't want to update the logs for this package.
            // 2. we don't want to update the package name in the records as they already have the
            //    correct package name.
            UpsertTransactionRequest upsertTransactionRequest =
                    new UpsertTransactionRequest(
                            null /* packageName */,
                            records,
                            mContext,
                            true /* isInsertRequest */,
                            true /* useProvidedUuid */,
                            true /* skipPackageNameAndLogs */);
            mTransactionManager.insertAll(upsertTransactionRequest.getUpsertRequests());

            currentToken = token;
        } while (!currentToken.isEmpty());
    }

    private <T extends Record> void deleteRecordsOfType(
            HealthConnectDatabase stagedDatabase, int recordType, Class<T> recordTypeClass) {
        RecordHelper<?> recordHelper = RecordHelperProvider.getRecordHelper(recordType);
        if (!StorageUtils.checkTableExists(stagedDatabase, recordHelper.getMainTableName())) {
            return;
        }

        // Passing -1 for startTime and endTime as we don't want to have time based filtering in the
        // final query.
        Slog.d(TAG, "Deleting table for: " + recordTypeClass);
        @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
        DeleteTableRequest deleteTableRequest =
                recordHelper.getDeleteTableRequest(
                        null /* packageFilters */,
                        DEFAULT_LONG /* startTime */,
                        DEFAULT_LONG /* endTime */,
                        false /* useLocalTimeFilter */);

        stagedDatabase.getWritableDatabase().execSQL(deleteTableRequest.getDeleteCommand());
    }

    private <T extends Record> Pair<List<RecordInternal<?>>, PageTokenWrapper> getRecordsToMerge(
            HealthConnectDatabase stagedDatabase,
            Map<Long, String> stagedPackageNamesByAppIds,
            Class<T> recordTypeClass,
            PageTokenWrapper requestToken,
            RecordHelper<?> recordHelper) {
        ReadRecordsRequestUsingFilters<T> readRecordsRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(recordTypeClass)
                        .setPageSize(MAXIMUM_PAGE_SIZE)
                        .setPageToken(requestToken.encode())
                        .build();

        Set<String> grantedExtraReadPermissions =
                Set.copyOf(recordHelper.getExtraReadPermissions());

        // Working with startDateAccess of -1 as we don't want to have time based filtering in the
        // query.
        @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
        ReadTransactionRequest readTransactionRequest =
                new ReadTransactionRequest(
                        null,
                        readRecordsRequest.toReadRecordsRequestParcel(),
                        // Avoid time based filtering.
                        /* startDateAccessMillis= */ DEFAULT_LONG,
                        /* enforceSelfRead= */ false,
                        grantedExtraReadPermissions,
                        // Make sure foreground only types get included in the response.
                        /* isInForeground= */ true);

        List<RecordInternal<?>> recordInternalList;
        PageTokenWrapper token;
        ReadTableRequest readTableRequest = readTransactionRequest.getReadRequests().get(0);
        try (Cursor cursor = read(stagedDatabase, readTableRequest)) {
            Pair<List<RecordInternal<?>>, PageTokenWrapper> readResult =
                    recordHelper.getNextInternalRecordsPageAndToken(
                            cursor,
                            readTransactionRequest.getPageSize().orElse(MAXIMUM_PAGE_SIZE),
                            requireNonNull(readTransactionRequest.getPageToken()),
                            stagedPackageNamesByAppIds);
            recordInternalList = readResult.first;
            token = readResult.second;
            if (readTableRequest.getExtraReadRequests() != null) {
                for (ReadTableRequest extraDataRequest : readTableRequest.getExtraReadRequests()) {
                    Cursor cursorExtraData = read(stagedDatabase, extraDataRequest);
                    readTableRequest
                            .getRecordHelper()
                            .updateInternalRecordsWithExtraFields(
                                    recordInternalList,
                                    cursorExtraData,
                                    extraDataRequest.getTableName());
                }
            }
        }
        return Pair.create(recordInternalList, token);
    }

    private synchronized Cursor read(
            HealthConnectDatabase stagedDatabase, ReadTableRequest request) {
        Slog.d(TAG, "Running command: " + request.getReadCommand());
        Cursor cursor =
                stagedDatabase.getReadableDatabase().rawQuery(request.getReadCommand(), null);
        Slog.d(TAG, "Cursor count: " + cursor.getCount());
        return cursor;
    }
}
