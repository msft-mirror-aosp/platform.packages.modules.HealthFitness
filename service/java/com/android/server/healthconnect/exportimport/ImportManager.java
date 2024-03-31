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
import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_PLANNED_EXERCISE_SESSION;

import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorBlob;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.datatypes.Record;
import android.health.connect.internal.datatypes.PlannedExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.RecordMapper;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.request.UpsertTransactionRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;
import com.android.server.healthconnect.utils.FilesUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages import related tasks.
 *
 * @hide
 */
public class ImportManager {

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

    @VisibleForTesting static final String STAGED_DATABASE_NAME = "healthconnect_import.db";

    private static final String TAG = "HealthConnectImportManager";

    private final Map<Long, String> mStagedPackageNamesByAppIds = new ArrayMap<>();

    final Object mMergingLock = new Object();

    @GuardedBy("mMergingLock")
    private HealthConnectDatabase mStagedDatabase;

    private final StagedDatabaseContext mStagedDbContext;
    private final Context mContext;

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public ImportManager(@NonNull Context context) {
        requireNonNull(context);
        mContext = context;
        mStagedDbContext = new StagedDatabaseContext(context, mContext.getUser());
    }

    /** Reads the backup data from a local file. */
    public void runImport(Path pathToImport) {
        prepareImportFile(pathToImport);
        if (canMerge()) {
            prepInternalDataPerStagedDb();
            mergeRecords();
        }
    }

    /**
     * Stages all health connect remote data for merging later.
     *
     * <p>This should be called on the proper thread.
     */
    private void prepareImportFile(Path pathToImport) {
        File destination = mStagedDbContext.getDatabasePath(STAGED_DATABASE_NAME);
        try {
            Path destinationPath = FileSystems.getDefault().getPath(destination.getAbsolutePath());
            Files.copy(pathToImport, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SecurityException e) {
            Slog.e(TAG, "Failed to get copy to destination: " + destination.getName(), e);
            destination.delete();
        }
    }

    @VisibleForTesting
    private boolean canMerge() {
        int currentDbVersion = TransactionManager.getInitialisedInstance().getDatabaseVersion();
        File stagedDbFile = mStagedDbContext.getDatabasePath(STAGED_DATABASE_NAME);
        if (stagedDbFile.exists()) {
            try (SQLiteDatabase stagedDb =
                    SQLiteDatabase.openDatabase(
                            stagedDbFile, new SQLiteDatabase.OpenParams.Builder().build())) {
                int stagedDbVersion = stagedDb.getVersion();
                Slog.i(
                        TAG,
                        "merging staged data, current version = "
                                + currentDbVersion
                                + ", staged version = "
                                + stagedDbVersion);
                if (currentDbVersion < stagedDbVersion) {
                    Slog.i(TAG, "Module needs upgrade for merging to version " + stagedDbVersion);
                    return false;
                }
            }
        } else {
            Slog.i(TAG, "No database file found to merge.");
            return false;
        }

        Slog.i(TAG, "Starting the data merge.");
        return true;
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private void mergeRecords() {
        if (!mStagedDbContext.getDatabasePath(STAGED_DATABASE_NAME).exists()) {
            Slog.i(TAG, "No staged db found.");
            // no db was staged
            return;
        }

        // Determine the order in which we should migrate data types. This involves first
        // migrating data types according to the specified ordering overrides. Remaining
        // records are migrated in no particular order.
        List<Integer> recordTypesWithOrderingOverrides =
                RECORD_TYPE_MIGRATION_ORDERING_OVERRIDES.stream().flatMap(List::stream).toList();
        List<Integer> recordTypesWithoutOrderingOverrides =
                RecordMapper.getInstance().getRecordIdToExternalRecordClassMap().keySet().stream()
                        .filter(it -> !recordTypesWithOrderingOverrides.contains(it))
                        .toList();

        // Migrate special case records in their defined order.
        for (List<Integer> recordTypeMigrationGroup : RECORD_TYPE_MIGRATION_ORDERING_OVERRIDES) {
            for (Integer recordTypeToMigrate : recordTypeMigrationGroup) {
                mergeRecordsOfType(
                        recordTypeToMigrate,
                        RecordMapper.getInstance()
                                .getRecordIdToExternalRecordClassMap()
                                .get(recordTypeToMigrate));
            }
            // Delete records within a group together, once all records within that group
            // have been migrated. This ensures referential integrity is preserved during
            // migration.
            for (Integer recordTypeToMigrate : recordTypeMigrationGroup) {
                deleteRecordsOfType(
                        recordTypeToMigrate,
                        RecordMapper.getInstance()
                                .getRecordIdToExternalRecordClassMap()
                                .get(recordTypeToMigrate));
            }
        }
        // Migrate remaining record types in no particular order.
        for (Integer recordTypeToMigrate : recordTypesWithoutOrderingOverrides) {
            mergeRecordsOfType(
                    recordTypeToMigrate,
                    RecordMapper.getInstance()
                            .getRecordIdToExternalRecordClassMap()
                            .get(recordTypeToMigrate));
            deleteRecordsOfType(
                    recordTypeToMigrate,
                    RecordMapper.getInstance()
                            .getRecordIdToExternalRecordClassMap()
                            .get(recordTypeToMigrate));
        }

        Slog.i(TAG, "Sync app info records after restored data merge.");
        AppInfoHelper.getInstance().syncAppInfoRecordTypesUsed();

        // Delete the staged db as we are done merging.
        Slog.i(TAG, "Deleting staged db after merging.");
        mStagedDbContext.deleteDatabase(STAGED_DATABASE_NAME);
        synchronized (mMergingLock) {
            mStagedDatabase = null;
        }
    }

    private <T extends Record> void mergeRecordsOfType(int recordType, Class<T> recordTypeClass) {
        RecordHelper<?> recordHelper =
                RecordHelperProvider.getInstance().getRecordHelper(recordType);
        // Read all the records of the given type from the staged db and insert them into the
        // existing healthconnect db.
        PageTokenWrapper token = EMPTY_PAGE_TOKEN;
        do {
            var recordsToMergeAndToken = getRecordsToMerge(recordTypeClass, token, recordHelper);
            if (recordsToMergeAndToken.first.isEmpty()) {
                break;
            }
            Slog.d(TAG, "Found record to merge: " + recordsToMergeAndToken.first.getClass());
            if (recordType == RECORD_TYPE_PLANNED_EXERCISE_SESSION) {
                // For training plans we nullify any autogenerated references to exercise sessions.
                // When the corresponding exercise sessions get migrated, these references will be
                // automatically generated again.
                recordsToMergeAndToken.first.forEach(
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
                            recordsToMergeAndToken.first,
                            mContext,
                            true /* isInsertRequest */,
                            true /* useProvidedUuid */,
                            true /* skipPackageNameAndLogs */);
            TransactionManager.getInitialisedInstance()
                    .insertAll(upsertTransactionRequest.getUpsertRequests());

            token = recordsToMergeAndToken.second;
        } while (!token.isEmpty());
    }

    private <T extends Record> void deleteRecordsOfType(int recordType, Class<T> recordTypeClass) {
        RecordHelper<?> recordHelper =
                RecordHelperProvider.getInstance().getRecordHelper(recordType);
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

        getStagedDatabase().getWritableDatabase().execSQL(deleteTableRequest.getDeleteCommand());
    }

    private <T extends Record> Pair<List<RecordInternal<?>>, PageTokenWrapper> getRecordsToMerge(
            Class<T> recordTypeClass, PageTokenWrapper requestToken, RecordHelper<?> recordHelper) {
        ReadRecordsRequestUsingFilters<T> readRecordsRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(recordTypeClass)
                        .setPageSize(2000)
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
        try (Cursor cursor = read(readTableRequest)) {
            Pair<List<RecordInternal<?>>, PageTokenWrapper> readResult =
                    recordHelper.getNextInternalRecordsPageAndToken(
                            cursor,
                            readTransactionRequest.getPageSize().orElse(DEFAULT_PAGE_SIZE),
                            requireNonNull(readTransactionRequest.getPageToken()),
                            mStagedPackageNamesByAppIds);
            recordInternalList = readResult.first;
            token = readResult.second;
            populateInternalRecordsWithExtraData(recordInternalList, readTableRequest);
        }
        return Pair.create(recordInternalList, token);
    }

    private Cursor read(ReadTableRequest request) {
        synchronized (mMergingLock) {
            return getStagedDatabase()
                    .getReadableDatabase()
                    .rawQuery(request.getReadCommand(), null);
        }
    }

    private void populateInternalRecordsWithExtraData(
            List<RecordInternal<?>> records, ReadTableRequest request) {
        if (request.getExtraReadRequests() == null) {
            return;
        }
        for (ReadTableRequest extraDataRequest : request.getExtraReadRequests()) {
            Cursor cursorExtraData = read(extraDataRequest);
            request.getRecordHelper()
                    .updateInternalRecordsWithExtraFields(
                            records, cursorExtraData, extraDataRequest.getTableName());
        }
    }

    private void prepInternalDataPerStagedDb() {
        try (Cursor cursor = read(new ReadTableRequest(AppInfoHelper.TABLE_NAME))) {
            while (cursor.moveToNext()) {
                long rowId = getCursorLong(cursor, RecordHelper.PRIMARY_COLUMN_NAME);
                String packageName = getCursorString(cursor, AppInfoHelper.PACKAGE_COLUMN_NAME);
                String appName = getCursorString(cursor, AppInfoHelper.APPLICATION_COLUMN_NAME);
                byte[] icon = getCursorBlob(cursor, AppInfoHelper.APP_ICON_COLUMN_NAME);
                mStagedPackageNamesByAppIds.put(rowId, packageName);

                // If this package is not installed on the target device and is not present in the
                // health db, then fill the health db with the info from source db.
                AppInfoHelper.getInstance()
                        .addOrUpdateAppInfoIfNotInstalled(
                                mContext, packageName, appName, icon, false /* onlyReplace */);
            }
        }
    }

    @VisibleForTesting
    HealthConnectDatabase getStagedDatabase() {
        synchronized (mMergingLock) {
            if (mStagedDatabase == null) {
                mStagedDatabase = new HealthConnectDatabase(mStagedDbContext, STAGED_DATABASE_NAME);
            }
            return mStagedDatabase;
        }
    }

    /**
     * {@link Context} for the staged health connect db.
     *
     * @hide
     */
    static final class StagedDatabaseContext extends ContextWrapper {

        private final UserHandle mCurrentForegroundUser;

        @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
        StagedDatabaseContext(@NonNull Context context, UserHandle userHandle) {
            super(context);
            requireNonNull(context);
            mCurrentForegroundUser = userHandle;
        }

        @Override
        public File getDatabasePath(String name) {
            File stagedDataDir =
                    FilesUtil.getDataSystemCeHCDirectoryForUser(
                            mCurrentForegroundUser.getIdentifier());
            stagedDataDir.mkdirs();
            return new File(stagedDataDir, name);
        }
    }
}
