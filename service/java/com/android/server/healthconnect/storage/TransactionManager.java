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

package com.android.server.healthconnect.storage;

import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.HealthConnectException.ERROR_INTERNAL;
import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_DELETE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_UPSERT;

import static com.android.internal.util.Preconditions.checkArgument;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.APP_INFO_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.UUID_COLUMN_NAME;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.health.connect.Constants;
import android.health.connect.HealthConnectException;
import android.health.connect.PageTokenWrapper;
import android.health.connect.internal.datatypes.RecordInternal;
import android.os.UserHandle;
import android.util.Pair;
import android.util.Slog;

import androidx.annotation.VisibleForTesting;

import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.request.AggregateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTransactionRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTransactionRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.TableColumnPair;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * A class to handle all the DB transaction request from the clients. {@link TransactionManager}
 * acts as a layer b/w the DB and the data type helper classes and helps perform actual operations
 * on the DB.
 *
 * @hide
 */
public final class TransactionManager {
    private static final String TAG = "HealthConnectTransactionMan";
    private static final ConcurrentHashMap<UserHandle, HealthConnectDatabase>
            mUserHandleToDatabaseMap = new ConcurrentHashMap<>();

    @Nullable private static volatile TransactionManager sTransactionManager;

    private volatile HealthConnectDatabase mHealthConnectDatabase;
    private UserHandle mUserHandle;

    private TransactionManager(@NonNull HealthConnectUserContext context) {
        mHealthConnectDatabase = new HealthConnectDatabase(context);
        mUserHandleToDatabaseMap.put(context.getCurrentUserHandle(), mHealthConnectDatabase);
        mUserHandle = context.getCurrentUserHandle();
    }

    public void onUserUnlocked(@NonNull HealthConnectUserContext healthConnectUserContext) {
        if (!mUserHandleToDatabaseMap.containsKey(
                healthConnectUserContext.getCurrentUserHandle())) {
            mUserHandleToDatabaseMap.put(
                    healthConnectUserContext.getCurrentUserHandle(),
                    new HealthConnectDatabase(healthConnectUserContext));
        }

        mHealthConnectDatabase =
                mUserHandleToDatabaseMap.get(healthConnectUserContext.getCurrentUserHandle());
        mUserHandle = healthConnectUserContext.getCurrentUserHandle();
    }

    /**
     * Inserts all the {@link RecordInternal} in {@code request} into the HealthConnect database.
     *
     * @param request an insert request.
     * @return List of uids of the inserted {@link RecordInternal}, in the same order as they
     *     presented to {@code request}.
     */
    public List<String> insertAll(@NonNull UpsertTransactionRequest request)
            throws SQLiteException {
        if (Constants.DEBUG) {
            Slog.d(TAG, "Inserting " + request.getUpsertRequests().size() + " requests.");
        }

        final SQLiteDatabase db = getWritableDb();

        long currentTime = Instant.now().toEpochMilli();
        ChangeLogsHelper.ChangeLogs insertionChangelogs =
                new ChangeLogsHelper.ChangeLogs(OPERATION_TYPE_UPSERT, currentTime);
        ChangeLogsHelper.ChangeLogs modificationChangelogs =
                new ChangeLogsHelper.ChangeLogs(OPERATION_TYPE_UPSERT, currentTime);
        db.beginTransaction();
        try {
            for (UpsertTableRequest upsertRequest : request.getUpsertRequests()) {
                insertionChangelogs.addUUID(
                        upsertRequest.getRecordInternal().getRecordType(),
                        upsertRequest.getRecordInternal().getAppInfoId(),
                        upsertRequest.getRecordInternal().getUuid());
                addChangelogsForOtherModifiedRecords(upsertRequest, modificationChangelogs);
                insertOrReplaceRecord(db, upsertRequest);
            }

            for (UpsertTableRequest insertRequestsForChangeLog :
                    insertionChangelogs.getUpsertTableRequests()) {
                insertRecord(db, insertRequestsForChangeLog);
            }
            for (UpsertTableRequest modificationChangelog :
                    modificationChangelogs.getUpsertTableRequests()) {
                insertRecord(db, modificationChangelog);
            }

            for (UpsertTableRequest insertRequestsForAccessLogs : request.getAccessLogs()) {
                insertRecord(db, insertRequestsForAccessLogs);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        return request.getUUIdsInOrder();
    }

    /**
     * Ignores if a record is already present. This does not generate changelogs and should only be
     * used for backup and restore.
     */
    public void insertAll(@NonNull List<UpsertTableRequest> requests) throws SQLiteException {
        final SQLiteDatabase db = getWritableDb();
        db.beginTransaction();
        try {
            for (UpsertTableRequest request : requests) {
                insertOrIgnore(db, request);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Inserts or replaces all the {@link UpsertTableRequest} into the HealthConnect database.
     *
     * @param upsertTableRequests a list of insert table requests.
     */
    public void insertOrReplaceAll(@NonNull List<UpsertTableRequest> upsertTableRequests)
            throws SQLiteException {
        insertAll(upsertTableRequests, this::insertOrReplaceRecord);
    }

    /**
     * Inserts or replaces all the {@link UpsertTableRequest} into the given database.
     *
     * @param db a {@link SQLiteDatabase}.
     * @param upsertTableRequests a list of insert table requests.
     */
    public void insertOrReplaceAll(
            @NonNull SQLiteDatabase db, @NonNull List<UpsertTableRequest> upsertTableRequests) {
        insertRequests(db, upsertTableRequests, this::insertOrReplaceRecord);
    }

    /**
     * Inserts or ignore on conflicts all the {@link UpsertTableRequest} into the HealthConnect
     * database.
     *
     * @param upsertTableRequests a list of insert table requests.
     */
    public void insertOrIgnoreOnConflict(@NonNull List<UpsertTableRequest> upsertTableRequests) {
        final SQLiteDatabase db = getWritableDb();
        db.beginTransaction();
        try {
            upsertTableRequests.forEach(
                    (upsertTableRequest) -> insertOrIgnore(db, upsertTableRequest));
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Deletes all the {@link RecordInternal} in {@code request} into the HealthConnect database.
     *
     * <p>NOTE: Please don't add logic to explicitly delete child table entries here as they should
     * be deleted via cascade
     *
     * @param request a delete request.
     */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public int deleteAll(@NonNull DeleteTransactionRequest request) throws SQLiteException {
        final SQLiteDatabase db = getWritableDb();
        long currentTime = Instant.now().toEpochMilli();
        ChangeLogsHelper.ChangeLogs deletionChangelogs =
                new ChangeLogsHelper.ChangeLogs(OPERATION_TYPE_DELETE, currentTime);
        ChangeLogsHelper.ChangeLogs modificationChangelogs =
                new ChangeLogsHelper.ChangeLogs(OPERATION_TYPE_UPSERT, currentTime);
        db.beginTransaction();
        int numberOfRecordsDeleted = 0;
        try {
            for (DeleteTableRequest deleteTableRequest : request.getDeleteTableRequests()) {
                final RecordHelper<?> recordHelper =
                        RecordHelperProvider.getRecordHelper(deleteTableRequest.getRecordType());
                if (deleteTableRequest.requiresRead()) {
                    /*
                    Delete request needs UUID before the entry can be
                    deleted, fetch and set it in {@code request}
                    */
                    try (Cursor cursor = db.rawQuery(deleteTableRequest.getReadCommand(), null)) {
                        int numberOfUuidsToDelete = 0;
                        while (cursor.moveToNext()) {
                            numberOfUuidsToDelete++;
                            long appInfoId =
                                    StorageUtils.getCursorLong(
                                            cursor, deleteTableRequest.getPackageColumnName());
                            if (deleteTableRequest.requiresPackageCheck()) {
                                request.enforcePackageCheck(
                                        StorageUtils.getCursorUUID(
                                                cursor, deleteTableRequest.getIdColumnName()),
                                        appInfoId);
                            }
                            UUID deletedRecordUuid =
                                    StorageUtils.getCursorUUID(
                                            cursor, deleteTableRequest.getIdColumnName());
                            deletionChangelogs.addUUID(
                                    deleteTableRequest.getRecordType(),
                                    appInfoId,
                                    deletedRecordUuid);

                            // Add changelogs for affected records, e.g. a training plan being
                            // deleted will create changelogs for affected exercise sessions.
                            for (ReadTableRequest additionalChangelogUuidRequest :
                                    recordHelper.getReadRequestsForRecordsModifiedByDeletion(
                                            deletedRecordUuid)) {
                                Cursor cursorAdditionalUuids = read(additionalChangelogUuidRequest);
                                while (cursorAdditionalUuids.moveToNext()) {
                                    modificationChangelogs.addUUID(
                                            additionalChangelogUuidRequest
                                                    .getRecordHelper()
                                                    .getRecordIdentifier(),
                                            StorageUtils.getCursorLong(
                                                    cursorAdditionalUuids, APP_INFO_ID_COLUMN_NAME),
                                            StorageUtils.getCursorUUID(
                                                    cursorAdditionalUuids, UUID_COLUMN_NAME));
                                }
                                cursorAdditionalUuids.close();
                            }
                        }
                        deleteTableRequest.setNumberOfUuidsToDelete(numberOfUuidsToDelete);
                    }
                }
                numberOfRecordsDeleted += deleteTableRequest.getTotalNumberOfRecordsDeleted();
                db.execSQL(deleteTableRequest.getDeleteCommand());
            }

            for (UpsertTableRequest insertRequestsForChangeLog :
                    deletionChangelogs.getUpsertTableRequests()) {
                insertRecord(db, insertRequestsForChangeLog);
            }
            for (UpsertTableRequest modificationChangelog :
                    modificationChangelogs.getUpsertTableRequests()) {
                insertRecord(db, modificationChangelog);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return numberOfRecordsDeleted;
    }

    /**
     * Handles the aggregation requests for {@code aggregateTableRequest}
     *
     * @param aggregateTableRequest an aggregate request.
     */
    @NonNull
    public void populateWithAggregation(AggregateTableRequest aggregateTableRequest) {
        final SQLiteDatabase db = getReadableDb();
        if (!aggregateTableRequest.getRecordHelper().isRecordOperationsEnabled()) {
            return;
        }
        try (Cursor cursor = db.rawQuery(aggregateTableRequest.getAggregationCommand(), null);
                Cursor metaDataCursor =
                        db.rawQuery(
                                aggregateTableRequest.getCommandToFetchAggregateMetadata(), null)) {
            aggregateTableRequest.onResultsFetched(cursor, metaDataCursor);
        }
    }

    /**
     * Reads the records {@link RecordInternal} stored in the HealthConnect database.
     *
     * @param request a read request.
     * @return List of records read {@link RecordInternal} from table based on ids.
     * @throws IllegalArgumentException if the {@link ReadTransactionRequest} contains pagination
     *     information, which should use {@link #readRecordsAndPageToken(ReadTransactionRequest)}
     *     instead.
     */
    public List<RecordInternal<?>> readRecordsByIds(@NonNull ReadTransactionRequest request)
            throws SQLiteException {
        // TODO(b/308158714): Make this build time check once we have different classes.
        checkArgument(
                request.getPageToken() == null && request.getPageSize().isEmpty(),
                "Expect read by id request, but request contains pagination info.");
        List<RecordInternal<?>> recordInternals = new ArrayList<>();
        for (ReadTableRequest readTableRequest : request.getReadRequests()) {
            RecordHelper<?> helper = readTableRequest.getRecordHelper();
            requireNonNull(helper);
            if (helper.isRecordOperationsEnabled()) {
                try (Cursor cursor = read(readTableRequest)) {
                    List<RecordInternal<?>> internalRecords = helper.getInternalRecords(cursor);
                    populateInternalRecordsWithExtraData(internalRecords, readTableRequest);
                    recordInternals.addAll(internalRecords);
                }
            }
        }
        return recordInternals;
    }

    /**
     * Reads the records {@link RecordInternal} stored in the HealthConnect database and returns the
     * next page token.
     *
     * @param request a read request. Only one {@link ReadTableRequest} is expected in the {@link
     *     ReadTransactionRequest request}.
     * @return Pair containing records list read {@link RecordInternal} from the table and a page
     *     token for pagination.
     * @throws IllegalArgumentException if the {@link ReadTransactionRequest} doesn't contain
     *     pagination information, which should use {@link
     *     #readRecordsByIds(ReadTransactionRequest)} instead.
     */
    public Pair<List<RecordInternal<?>>, PageTokenWrapper> readRecordsAndPageToken(
            @NonNull ReadTransactionRequest request) throws SQLiteException {
        // TODO(b/308158714): Make these build time checks once we have different classes.
        checkArgument(
                request.getPageToken() != null && request.getPageSize().isPresent(),
                "Expect read by filter request, but request doesn't contain pagination info.");
        checkArgument(
                request.getReadRequests().size() == 1,
                "Expected read by filter request, but request contains multiple read requests.");
        ReadTableRequest readTableRequest = request.getReadRequests().get(0);
        List<RecordInternal<?>> recordInternalList;
        RecordHelper<?> helper = readTableRequest.getRecordHelper();
        requireNonNull(helper);
        if (!helper.isRecordOperationsEnabled()) {
            recordInternalList = new ArrayList<>(0);
            return Pair.create(recordInternalList, EMPTY_PAGE_TOKEN);
        }

        PageTokenWrapper pageToken;
        try (Cursor cursor = read(readTableRequest)) {
            Pair<List<RecordInternal<?>>, PageTokenWrapper> readResult =
                    helper.getNextInternalRecordsPageAndToken(
                            cursor,
                            request.getPageSize().orElse(DEFAULT_PAGE_SIZE),
                            // pageToken is never null for read by filter requests
                            requireNonNull(request.getPageToken()));
            recordInternalList = readResult.first;
            pageToken = readResult.second;
            populateInternalRecordsWithExtraData(recordInternalList, readTableRequest);
        }
        return Pair.create(recordInternalList, pageToken);
    }

    /**
     * Inserts record into the table in {@code request} into the HealthConnect database.
     *
     * <p>NOTE: PLEASE ONLY USE THIS FUNCTION IF YOU WANT TO INSERT A SINGLE RECORD PER API. PLEASE
     * DON'T USE THIS FUNCTION INSIDE A FOR LOOP OR REPEATEDLY: The reason is that this function
     * tries to insert a record inside its own transaction and if you are trying to insert multiple
     * things using this method in the same api call, they will all get inserted in their separate
     * transactions and will be less performant. If at all, the requirement is to insert them in
     * different transactions, as they are not related to each, then this method can be used.
     *
     * @param request an insert request.
     * @return rowId of the inserted record.
     */
    public long insert(@NonNull UpsertTableRequest request) {
        final SQLiteDatabase db = getWritableDb();
        return insertRecord(db, request);
    }

    /**
     * Inserts record into the table in {@code request} into the HealthConnect database using the
     * given {@link SQLiteDatabase}.
     *
     * <p>NOTE: PLEASE ONLY USE THIS FUNCTION IF YOU WANT TO INSERT A SINGLE RECORD PER API. PLEASE
     * DON'T USE THIS FUNCTION INSIDE A FOR LOOP OR REPEATEDLY: The reason is that this function
     * tries to insert a record inside its own transaction and if you are trying to insert multiple
     * things using this method in the same api call, they will all get inserted in their separate
     * transactions and will be less performant. If at all, the requirement is to insert them in
     * different transactions, as they are not related to each, then this method can be used.
     *
     * @param db a {@link SQLiteDatabase}.
     * @param request an insert request.
     * @return rowId of the inserted record.
     */
    public long insert(SQLiteDatabase db, @NonNull UpsertTableRequest request) {
        return insertRecord(db, request);
    }

    /**
     * Update record into the table in {@code request} into the HealthConnect database.
     *
     * <p>NOTE: PLEASE ONLY USE THIS FUNCTION IF YOU WANT TO UPDATE A SINGLE RECORD PER API. PLEASE
     * DON'T USE THIS FUNCTION INSIDE A FOR LOOP OR REPEATEDLY: The reason is that this function
     * tries to update a record inside its own transaction and if you are trying to insert multiple
     * things using this method in the same api call, they will all get updates in their separate
     * transactions and will be less performant. If at all, the requirement is to update them in
     * different transactions, as they are not related to each, then this method can be used.
     *
     * @param request an update request.
     */
    public void update(@NonNull UpsertTableRequest request) {
        final SQLiteDatabase db = getWritableDb();
        updateRecord(db, request);
    }

    /**
     * Inserts (or updates if the row exists) record into the table in {@code request} into the
     * HealthConnect database.
     *
     * <p>NOTE: PLEASE ONLY USE THIS FUNCTION IF YOU WANT TO UPSERT A SINGLE RECORD. PLEASE DON'T
     * USE THIS FUNCTION INSIDE A FOR LOOP OR REPEATEDLY: The reason is that this function tries to
     * insert a record out of a transaction and if you are trying to insert a record before or after
     * opening up a transaction please rethink if you really want to use this function.
     *
     * <p>NOTE: INSERT + WITH_CONFLICT_REPLACE only works on unique columns, else in case of
     * conflict it leads to abort of the transaction.
     *
     * @param request an insert request.
     * @return rowId of the inserted or updated record.
     */
    public long insertOrReplace(@NonNull UpsertTableRequest request) {
        final SQLiteDatabase db = getWritableDb();
        return insertOrReplaceRecord(db, request);
    }

    /** Note: It is the responsibility of the caller to close the returned cursor */
    @NonNull
    public Cursor read(@NonNull ReadTableRequest request) {
        if (Constants.DEBUG) {
            Slog.d(TAG, "Read query: " + request.getReadCommand());
        }
        return getReadableDb().rawQuery(request.getReadCommand(), null);
    }

    /**
     * Reads the given {@link SQLiteDatabase} using the given {@link ReadTableRequest}.
     *
     * <p>Note: It is the responsibility of the caller to close the returned cursor.
     *
     * @param db a {@link SQLiteDatabase}.
     * @param request a {@link ReadTableRequest}.
     */
    public Cursor read(SQLiteDatabase db, @NonNull ReadTableRequest request) {
        if (Constants.DEBUG) {
            Slog.d(TAG, "Read query: " + request.getReadCommand());
        }
        return db.rawQuery(request.getReadCommand(), null);
    }

    public long getLastRowIdFor(String tableName) {
        final SQLiteDatabase db = getReadableDb();
        try (Cursor cursor = db.rawQuery(StorageUtils.getMaxPrimaryKeyQuery(tableName), null)) {
            cursor.moveToFirst();
            return cursor.getLong(cursor.getColumnIndex(PRIMARY_COLUMN_NAME));
        }
    }

    /**
     * Get number of entries in the given table.
     *
     * @param tableName Name of table
     * @return Number of entries in the given table
     */
    public long getNumberOfEntriesInTheTable(@NonNull String tableName) {
        requireNonNull(tableName);
        return DatabaseUtils.queryNumEntries(getReadableDb(), tableName);
    }

    /**
     * Size of Health Connect database in bytes.
     *
     * @param context Context
     * @return Size of the database
     */
    public long getDatabaseSize(@NonNull Context context) {
        requireNonNull(context);
        return context.getDatabasePath(getReadableDb().getPath()).length();
    }

    /**
     * Delete data using the given request.
     *
     * @param request the request specifying what to delete
     */
    public void delete(DeleteTableRequest request) {
        delete(getWritableDb(), request);
    }

    /**
     * Delete data using the given request on the DB.
     *
     * @param db the database to delete from
     * @param request the request specifying what to delete
     */
    public void delete(SQLiteDatabase db, DeleteTableRequest request) {
        db.execSQL(request.getDeleteCommand());
    }

    /**
     * Updates all the {@link RecordInternal} in {@code request} into the HealthConnect database.
     *
     * @param request an update request.
     */
    public void updateAll(@NonNull UpsertTransactionRequest request) {
        final SQLiteDatabase db = getWritableDb();
        long currentTime = Instant.now().toEpochMilli();
        ChangeLogsHelper.ChangeLogs updateChangelogs =
                new ChangeLogsHelper.ChangeLogs(OPERATION_TYPE_UPSERT, currentTime);
        ChangeLogsHelper.ChangeLogs modificationChangelogs =
                new ChangeLogsHelper.ChangeLogs(OPERATION_TYPE_UPSERT, currentTime);
        db.beginTransaction();
        try {
            for (UpsertTableRequest upsertRequest : request.getUpsertRequests()) {
                updateChangelogs.addUUID(
                        upsertRequest.getRecordInternal().getRecordType(),
                        upsertRequest.getRecordInternal().getAppInfoId(),
                        upsertRequest.getRecordInternal().getUuid());
                // Add changelogs for affected records, e.g. a training plan being deleted will
                // create changelogs for affected exercise sessions.
                addChangelogsForOtherModifiedRecords(upsertRequest, modificationChangelogs);
                updateRecord(db, upsertRequest);
            }

            for (UpsertTableRequest insertRequestsForChangeLog :
                    updateChangelogs.getUpsertTableRequests()) {
                insertRecord(db, insertRequestsForChangeLog);
            }
            for (UpsertTableRequest modificationChangelog :
                    modificationChangelogs.getUpsertTableRequests()) {
                insertRecord(db, modificationChangelog);
            }

            for (UpsertTableRequest insertRequestsForAccessLogs : request.getAccessLogs()) {
                insertRecord(db, insertRequestsForAccessLogs);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * @return list of distinct packageNames corresponding to the input table name after querying
     *     the table.
     */
    public Map<Integer, Set<Long>> getDistinctPackageIdsForRecordsTable(Set<Integer> recordTypes)
            throws SQLiteException {
        final SQLiteDatabase db = getReadableDb();
        HashMap<Integer, Set<Long>> recordTypeToPackageIdsMap = new HashMap<>();
        for (Integer recordType : recordTypes) {
            RecordHelper<?> recordHelper = RecordHelperProvider.getRecordHelper(recordType);
            HashSet<Long> packageIds = new HashSet<>();
            try (Cursor cursorForDistinctPackageNames =
                    db.rawQuery(
                            /* sql query */
                            recordHelper
                                    .getReadTableRequestWithDistinctAppInfoIds()
                                    .getReadCommand(),
                            /* selectionArgs */ null)) {
                if (cursorForDistinctPackageNames.getCount() > 0) {
                    while (cursorForDistinctPackageNames.moveToNext()) {
                        packageIds.add(
                                cursorForDistinctPackageNames.getLong(
                                        cursorForDistinctPackageNames.getColumnIndex(
                                                APP_INFO_ID_COLUMN_NAME)));
                    }
                }
            }
            recordTypeToPackageIdsMap.put(recordType, packageIds);
        }

        return recordTypeToPackageIdsMap;
    }

    /**
     * ONLY DO OPERATIONS IN A SINGLE TRANSACTION HERE
     *
     * <p>This is because this function is called from {@link AutoDeleteService}, and we want to
     * make sure that either all its operation succeed or fail in a single run.
     */
    public void deleteWithoutChangeLogs(@NonNull List<DeleteTableRequest> deleteTableRequests) {
        requireNonNull(deleteTableRequests);
        final SQLiteDatabase db = getWritableDb();
        db.beginTransaction();
        try {
            for (DeleteTableRequest deleteTableRequest : deleteTableRequests) {
                db.execSQL(deleteTableRequest.getDeleteCommand());
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public void onUserSwitching() {
        mHealthConnectDatabase.close();
    }

    private void insertAll(
            @NonNull List<UpsertTableRequest> upsertTableRequests,
            @NonNull BiConsumer<SQLiteDatabase, UpsertTableRequest> insert) {
        final SQLiteDatabase db = getWritableDb();
        db.beginTransaction();
        try {
            insertRequests(db, upsertTableRequests, insert);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void insertRequests(
            @NonNull SQLiteDatabase db,
            @NonNull List<UpsertTableRequest> upsertTableRequests,
            @NonNull BiConsumer<SQLiteDatabase, UpsertTableRequest> insert) {
        upsertTableRequests.forEach((upsertTableRequest) -> insert.accept(db, upsertTableRequest));
    }

    public <E extends Throwable> void runAsTransaction(TransactionRunnable<E> task) throws E {
        final SQLiteDatabase db = getWritableDb();
        db.beginTransaction();
        try {
            task.run(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Runs a {@link TransactionRunnableWithReturn} task in a Transaction.
     *
     * @param task is a {@link TransactionRunnableWithReturn}.
     * @param <R> is the return type of the {@code task}.
     * @param <E> is the exception thrown by the {@code task}.
     */
    public <R, E extends Throwable> R runAsTransaction(TransactionRunnableWithReturn<R, E> task)
            throws E {
        final SQLiteDatabase db = getWritableDb();
        db.beginTransaction();
        try {
            R result = task.run(db);
            db.setTransactionSuccessful();
            return result;
        } finally {
            db.endTransaction();
        }
    }

    /** Assumes that caller will be closing {@code db} and handling the transaction if required */
    public long insertRecord(@NonNull SQLiteDatabase db, @NonNull UpsertTableRequest request) {
        long rowId = db.insertOrThrow(request.getTable(), null, request.getContentValues());
        request.getChildTableRequests()
                .forEach(childRequest -> insertRecord(db, childRequest.withParentKey(rowId)));
        for (String postUpsertCommand : request.getPostUpsertCommands()) {
            db.execSQL(postUpsertCommand);
        }

        return rowId;
    }

    /**
     * Inserts the provided {@link UpsertTableRequest} into the database.
     *
     * <p>Assumes that caller will be closing {@code db} and handling the transaction if required.
     *
     * @return the row ID of the newly inserted row or <code>-1</code> if an error occurred.
     */
    public long insertOrIgnore(@NonNull SQLiteDatabase db, @NonNull UpsertTableRequest request) {
        long rowId =
                db.insertWithOnConflict(
                        request.getTable(),
                        null,
                        request.getContentValues(),
                        SQLiteDatabase.CONFLICT_IGNORE);

        if (rowId != -1) {
            request.getChildTableRequests()
                    .forEach(childRequest -> insertRecord(db, childRequest.withParentKey(rowId)));
            for (String postUpsertCommand : request.getPostUpsertCommands()) {
                db.execSQL(postUpsertCommand);
            }
        }

        return rowId;
    }

    /** Note: NEVER close this DB */
    @NonNull
    private SQLiteDatabase getReadableDb() {
        SQLiteDatabase sqLiteDatabase = mHealthConnectDatabase.getReadableDatabase();

        if (sqLiteDatabase == null) {
            throw new InternalError("SQLite DB not found");
        }
        return sqLiteDatabase;
    }

    /** Note: NEVER close this DB */
    @NonNull
    private SQLiteDatabase getWritableDb() {
        SQLiteDatabase sqLiteDatabase = mHealthConnectDatabase.getWritableDatabase();

        if (sqLiteDatabase == null) {
            throw new InternalError("SQLite DB not found");
        }
        return sqLiteDatabase;
    }

    public File getDatabasePath() {
        return mHealthConnectDatabase.getDatabasePath();
    }

    public void updateTable(UpsertTableRequest upsertTableRequest) {
        getWritableDb()
                .update(
                        upsertTableRequest.getTable(),
                        upsertTableRequest.getContentValues(),
                        upsertTableRequest.getUpdateWhereClauses().get(false),
                        null);
    }

    public int getDatabaseVersion() {
        return getReadableDb().getVersion();
    }

    private void updateRecord(SQLiteDatabase db, UpsertTableRequest request) {
        // Perform an update operation where UUID and packageName (mapped by appInfoId) is same
        // as that of the update request.
        try {
            long numberOfRowsUpdated =
                    db.update(
                            request.getTable(),
                            request.getContentValues(),
                            request.getUpdateWhereClauses().get(/* withWhereKeyword */ false),
                            /* WHERE args */ null);
            for (String postUpsertCommand : request.getPostUpsertCommands()) {
                db.execSQL(postUpsertCommand);
            }

            // throw an exception if the no row was updated, i.e. the uuid with corresponding
            // app_id_info for this request is not found in the table.
            if (numberOfRowsUpdated == 0) {
                throw new IllegalArgumentException(
                        "No record found for the following input : "
                                + new StorageUtils.RecordIdentifierData(
                                        request.getContentValues()));
            }
        } catch (SQLiteConstraintException e) {
            try (Cursor cursor = db.rawQuery(request.getReadRequest().getReadCommand(), null)) {
                cursor.moveToFirst();
                throw new IllegalArgumentException(
                        StorageUtils.getConflictErrorMessageForRecord(
                                cursor, request.getContentValues()));
            }
        }

        if (request.getAllChildTables().isEmpty()) {
            return;
        }

        try (Cursor cursor =
                db.rawQuery(request.getReadRequestUsingUpdateClause().getReadCommand(), null)) {
            if (!cursor.moveToFirst()) {
                throw new HealthConnectException(
                        ERROR_INTERNAL, "Expected to read an entry for update, but none found");
            }
            final long rowId = StorageUtils.getCursorLong(cursor, request.getRowIdColName());
            deleteChildTableRequest(request, rowId, db);
            insertChildTableRequest(request, rowId, db);
        }
    }

    /**
     * Do extra sql requests to populate optional extra data. Used to populate {@link
     * android.health.connect.internal.datatypes.ExerciseRouteInternal}.
     */
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

    /**
     * Assumes that caller will be closing {@code db}. Returns -1 in case the update was triggered
     * and reading the row_id was not supported on the table.
     *
     * <p>Note: This function updates rather than the traditional delete + insert in SQLite
     */
    private long insertOrReplaceRecord(
            @NonNull SQLiteDatabase db, @NonNull UpsertTableRequest request) {
        try {
            if (request.getUniqueColumnsCount() == 0) {
                throw new RuntimeException(
                        "insertOrReplaceRecord should only be called with unique columns set");
            }

            long rowId =
                    db.insertWithOnConflict(
                            request.getTable(),
                            null,
                            request.getContentValues(),
                            SQLiteDatabase.CONFLICT_FAIL);
            insertChildTableRequest(request, rowId, db);
            for (String postUpsertCommand : request.getPostUpsertCommands()) {
                db.execSQL(postUpsertCommand);
            }

            return rowId;
        } catch (SQLiteConstraintException e) {
            try (Cursor cursor = db.rawQuery(request.getReadRequest().getReadCommand(), null)) {
                if (!cursor.moveToFirst()) {
                    throw new HealthConnectException(
                            ERROR_INTERNAL, "Conflict found, but couldn't read the entry.", e);
                }

                long updateResult = updateEntriesIfRequired(db, request, cursor);
                for (String postUpsertCommand : request.getPostUpsertCommands()) {
                    db.execSQL(postUpsertCommand);
                }
                return updateResult;
            }
        }
    }

    private long updateEntriesIfRequired(
            SQLiteDatabase db, UpsertTableRequest request, Cursor cursor) {
        if (!request.requiresUpdate(cursor, request)) {
            return -1;
        }
        db.update(
                request.getTable(),
                request.getContentValues(),
                request.getUpdateWhereClauses().get(/* withWhereKeyword */ false),
                /* WHERE args */ null);
        if (cursor.getColumnIndex(request.getRowIdColName()) == -1) {
            // The table is not explicitly using row_ids hence returning -1 here is ok, as
            // the rowid is of no use to this table.
            // NOTE: Such tables in HC don't support child tables either as child tables
            // inherently require row_ids to have support parent key.
            return -1;
        }
        final long rowId = StorageUtils.getCursorLong(cursor, request.getRowIdColName());
        deleteChildTableRequest(request, rowId, db);
        insertChildTableRequest(request, rowId, db);

        return rowId;
    }

    private void deleteChildTableRequest(
            UpsertTableRequest request, long rowId, SQLiteDatabase db) {
        for (TableColumnPair childTableAndColumn :
                request.getChildTablesWithRowsToBeDeletedDuringUpdate()) {
            DeleteTableRequest deleteTableRequest =
                    new DeleteTableRequest(childTableAndColumn.getTableName())
                            .setId(childTableAndColumn.getColumnName(), String.valueOf(rowId));
            db.execSQL(deleteTableRequest.getDeleteCommand());
        }
    }

    private void insertChildTableRequest(
            UpsertTableRequest request, long rowId, SQLiteDatabase db) {
        for (UpsertTableRequest childTableRequest : request.getChildTableRequests()) {
            String tableName = childTableRequest.getTable();
            ContentValues contentValues = childTableRequest.withParentKey(rowId).getContentValues();
            long childRowId = db.insertOrThrow(tableName, null, contentValues);
            insertChildTableRequest(childTableRequest, childRowId, db);
        }
    }

    private void addChangelogsForOtherModifiedRecords(
            UpsertTableRequest upsertRequest, ChangeLogsHelper.ChangeLogs modificationChangelogs) {
        // Carries out read requests provided by the record helper and uses the results to add
        // changelogs to the transaction.
        final RecordHelper<?> recordHelper =
                RecordHelperProvider.getRecordHelper(upsertRequest.getRecordType());
        for (ReadTableRequest additionalChangelogUuidRequest :
                recordHelper.getReadRequestsForRecordsModifiedByUpsertion(
                        upsertRequest.getRecordInternal().getUuid(), upsertRequest)) {
            Cursor cursorAdditionalUuids = read(additionalChangelogUuidRequest);
            while (cursorAdditionalUuids.moveToNext()) {
                modificationChangelogs.addUUID(
                        additionalChangelogUuidRequest.getRecordHelper().getRecordIdentifier(),
                        StorageUtils.getCursorLong(cursorAdditionalUuids, APP_INFO_ID_COLUMN_NAME),
                        StorageUtils.getCursorUUID(cursorAdditionalUuids, UUID_COLUMN_NAME));
            }
            cursorAdditionalUuids.close();
        }
    }

    public interface TransactionRunnable<E extends Throwable> {
        void run(SQLiteDatabase db) throws E;
    }

    /**
     * Runnable interface where run method throws Throwable or its subclasses and returns any data
     * type R.
     *
     * @param <E> Throwable or its subclass.
     * @param <R> any data type.
     */
    public interface TransactionRunnableWithReturn<R, E extends Throwable> {
        /** Task to be executed that throws throwable of type E and returns type R. */
        R run(SQLiteDatabase db) throws E;
    }

    @NonNull
    public static synchronized TransactionManager initializeInstance(
            @NonNull HealthConnectUserContext context) {
        if (sTransactionManager == null) {
            sTransactionManager = new TransactionManager(context);
        }

        return sTransactionManager;
    }

    @NonNull
    public static TransactionManager getInitialisedInstance() {
        requireNonNull(sTransactionManager);

        return sTransactionManager;
    }

    /** Used in testing to clear the instance to clear and re-reference the mocks. */
    @com.android.internal.annotations.VisibleForTesting
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public static synchronized void clearInstanceForTest() {
        sTransactionManager = null;
    }

    /** Cleans up the database and this manager, so unit tests can run correctly. */
    @VisibleForTesting
    public static void cleanUpForTest() {
        if (sTransactionManager != null) {
            // Close the DB before we delete the DB file to avoid the exception in b/333679690.
            sTransactionManager.getWritableDb().close();
            sTransactionManager.getReadableDb().close();
            SQLiteDatabase.deleteDatabase(sTransactionManager.getDatabasePath());
            sTransactionManager = null;
        }
    }

    @NonNull
    public UserHandle getCurrentUserHandle() {
        return mUserHandle;
    }
}
