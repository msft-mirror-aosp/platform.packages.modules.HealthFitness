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

import static android.health.connect.Constants.MAXIMUM_ALLOWED_CURSOR_COUNT;

import static com.android.server.healthconnect.storage.HealthConnectDatabase.createTable;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.health.connect.Constants;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.datatypes.MedicalDataSource;
import android.net.Uri;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class for MedicalDataSource.
 *
 * @hide
 */
public class MedicalDataSourceHelper {
    @VisibleForTesting
    static final String MEDICAL_DATA_SOURCE_TABLE_NAME = "medical_data_source_table";

    @VisibleForTesting static final String DISPLAY_NAME_COLUMN_NAME = "display_name";
    @VisibleForTesting static final String FHIR_BASE_URI_COLUMN_NAME = "fhir_base_uri";
    @VisibleForTesting static final String DATA_SOURCE_UUID_COLUMN_NAME = "data_source_uuid";
    private static final String APP_INFO_ID_COLUMN_NAME = "app_info_id";
    private static final String MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME =
            "medical_data_source_row_id";
    private static final List<Pair<String, Integer>> UNIQUE_COLUMNS_INFO =
            List.of(new Pair<>(DATA_SOURCE_UUID_COLUMN_NAME, UpsertTableRequest.TYPE_BLOB));

    private final TransactionManager mTransactionManager;
    private final AppInfoHelper mAppInfoHelper;

    public MedicalDataSourceHelper(
            @NonNull TransactionManager transactionManager, @NonNull AppInfoHelper appInfoHelper) {
        mTransactionManager = transactionManager;
        mAppInfoHelper = appInfoHelper;
    }

    @NonNull
    public static String getMainTableName() {
        return MEDICAL_DATA_SOURCE_TABLE_NAME;
    }

    @NonNull
    public static String getPrimaryColumnName() {
        return MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME;
    }

    @NonNull
    public static String getDataSourceUuidColumnName() {
        return DATA_SOURCE_UUID_COLUMN_NAME;
    }

    @NonNull
    public static String getAppInfoIdColumnName() {
        return APP_INFO_ID_COLUMN_NAME;
    }

    @NonNull
    private static List<Pair<String, String>> getColumnInfo() {
        return List.of(
                Pair.create(MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME, PRIMARY),
                Pair.create(APP_INFO_ID_COLUMN_NAME, INTEGER_NOT_NULL),
                Pair.create(DISPLAY_NAME_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(FHIR_BASE_URI_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(DATA_SOURCE_UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL));
    }

    @NonNull
    public static CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME, getColumnInfo())
                .addForeignKey(
                        AppInfoHelper.TABLE_NAME,
                        List.of(APP_INFO_ID_COLUMN_NAME),
                        List.of(PRIMARY_COLUMN_NAME));
    }

    /** Creates the medical_data_source table. */
    public static void onInitialUpgrade(@NonNull SQLiteDatabase db) {
        createTable(db, getCreateTableRequest());
    }

    /**
     * Creates {@link ReadTableRequest} that joins with {@link AppInfoHelper#TABLE_NAME} and filters
     * for the given list of {@code ids}, and restricts to the given apps.
     *
     * @param ids the data source ids to restrict to, if empty allows all data sources
     * @param appInfoRestriction the apps to restrict to, if null allows all apps
     */
    @NonNull
    public static ReadTableRequest getReadTableRequest(
            @NonNull List<UUID> ids, @Nullable Long appInfoRestriction) {
        ReadTableRequest readTableRequest = new ReadTableRequest(getMainTableName());
        WhereClauses whereClauses = getWhereClauses(ids, appInfoRestriction);
        return readTableRequest.setWhereClause(whereClauses);
    }

    /**
     * Gets a where clauses that filters the data source table by the given restrictions.
     *
     * @param ids the ids to include, or if empty do not filter by ids
     * @param appInfoRestriction the app info id to restrict to, or if null do not filter by app
     *     info
     */
    public static @NonNull WhereClauses getWhereClauses(
            @NonNull List<UUID> ids, @Nullable Long appInfoRestriction) {
        WhereClauses whereClauses;
        if (ids.isEmpty()) {
            whereClauses = new WhereClauses(AND);
        } else {
            whereClauses = getReadTableWhereClause(ids);
        }
        if (appInfoRestriction != null) {
            whereClauses.addWhereInLongsClause(
                    APP_INFO_ID_COLUMN_NAME, List.of(appInfoRestriction));
        }
        return whereClauses;
    }

    /** Creates {@link ReadTableRequest} that joins with {@link AppInfoHelper#TABLE_NAME}. */
    @NonNull
    private static ReadTableRequest getReadTableRequestJoinWithAppInfo() {
        return new ReadTableRequest(getMainTableName())
                .setJoinClause(getJoinClauseWithAppInfoTable());
    }

    /**
     * Creates {@link ReadTableRequest} that joins with {@link AppInfoHelper#TABLE_NAME} and filters
     * for the given list of {@code ids}.
     */
    @NonNull
    public static ReadTableRequest getReadTableRequestJoinWithAppInfo(@NonNull List<UUID> ids) {
        return getReadTableRequest(ids).setJoinClause(getJoinClauseWithAppInfoTable());
    }

    /** Creates {@link ReadTableRequest} for the given list of {@code ids}. */
    @NonNull
    public static ReadTableRequest getReadTableRequest(@NonNull List<UUID> ids) {
        return new ReadTableRequest(getMainTableName())
                .setWhereClause(getReadTableWhereClause(ids));
    }

    @NonNull
    private static SqlJoin getJoinClauseWithAppInfoTable() {
        return new SqlJoin(
                        MEDICAL_DATA_SOURCE_TABLE_NAME,
                        AppInfoHelper.TABLE_NAME,
                        APP_INFO_ID_COLUMN_NAME,
                        PRIMARY_COLUMN_NAME)
                .setJoinType(SqlJoin.SQL_JOIN_INNER);
    }

    /**
     * Returns a {@link WhereClauses} that limits to data sources with id in {@code ids}.
     *
     * @param ids the ids to limit to.
     */
    @NonNull
    public static WhereClauses getReadTableWhereClause(@NonNull List<UUID> ids) {
        return new WhereClauses(AND)
                .addWhereInClauseWithoutQuotes(
                        DATA_SOURCE_UUID_COLUMN_NAME, StorageUtils.getListOfHexStrings(ids));
    }

    /**
     * Returns List of {@link MedicalDataSource}s from the cursor. If the cursor contains more than
     * {@link Constants#MAXIMUM_ALLOWED_CURSOR_COUNT} data sources, it throws {@link
     * IllegalArgumentException}.
     */
    @NonNull
    private static List<MedicalDataSource> getMedicalDataSources(@NonNull Cursor cursor) {
        if (cursor.getCount() > MAXIMUM_ALLOWED_CURSOR_COUNT) {
            throw new IllegalArgumentException(
                    "Too many data sources in the cursor. Max allowed: "
                            + MAXIMUM_ALLOWED_CURSOR_COUNT);
        }
        List<MedicalDataSource> medicalDataSources = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                medicalDataSources.add(getMedicalDataSource(cursor));
            } while (cursor.moveToNext());
        }
        return medicalDataSources;
    }

    @NonNull
    private static MedicalDataSource getMedicalDataSource(@NonNull Cursor cursor) {
        return new MedicalDataSource.Builder(
                        /* id= */ getCursorUUID(cursor, DATA_SOURCE_UUID_COLUMN_NAME).toString(),
                        /* packageName= */ getCursorString(
                                cursor, AppInfoHelper.PACKAGE_COLUMN_NAME),
                        /* fhirBaseUri= */ Uri.parse(
                                getCursorString(cursor, FHIR_BASE_URI_COLUMN_NAME)),
                        /* displayName= */ getCursorString(cursor, DISPLAY_NAME_COLUMN_NAME))
                .build();
    }

    /**
     * Inserts the {@link MedicalDataSource} created from the given {@link
     * CreateMedicalDataSourceRequest} and {@code packageName} into the HealthConnect database.
     *
     * @param request a {@link CreateMedicalDataSourceRequest}.
     * @param packageName is the package name of the application wanting to create a {@link
     *     MedicalDataSource}.
     * @return The {@link MedicalDataSource} created and inserted into the database.
     */
    @NonNull
    public MedicalDataSource createMedicalDataSource(
            @NonNull Context context,
            @NonNull CreateMedicalDataSourceRequest request,
            @NonNull String packageName) {
        // TODO(b/344781394): Add support for access logs.
        return mTransactionManager.runAsTransaction(
                (TransactionManager.TransactionRunnableWithReturn<
                                MedicalDataSource, RuntimeException>)
                        db -> createMedicalDataSourceAndAppInfo(db, context, request, packageName));
    }

    private MedicalDataSource createMedicalDataSourceAndAppInfo(
            @NonNull SQLiteDatabase db,
            @NonNull Context context,
            @NonNull CreateMedicalDataSourceRequest request,
            @NonNull String packageName) {
        long appInfoId = mAppInfoHelper.getOrInsertAppInfoId(db, packageName, context);
        UUID dataSourceUuid = UUID.randomUUID();
        UpsertTableRequest upsertTableRequest =
                getUpsertTableRequest(dataSourceUuid, request, appInfoId);
        mTransactionManager.insert(db, upsertTableRequest);
        return buildMedicalDataSource(dataSourceUuid, request, packageName);
    }

    /**
     * Reads the {@link MedicalDataSource}s stored in the HealthConnect database using the given
     * list of {@code ids}.
     *
     * @param ids a list of {@link MedicalDataSource} ids.
     * @return List of {@link MedicalDataSource}s read from medical_data_source table based on ids.
     */
    @NonNull
    public List<MedicalDataSource> getMedicalDataSourcesByIdsWithoutPermissionChecks(
            @NonNull List<UUID> ids) throws SQLiteException {
        ReadTableRequest readTableRequest = getReadTableRequestJoinWithAppInfo(ids);
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            return getMedicalDataSources(cursor);
        }
    }

    /**
     * Reads the {@link MedicalDataSource}s stored in the HealthConnect database using the given
     * list of {@code ids} based on the {@code callingPackageName}'s permissions.
     *
     * @return List of {@link MedicalDataSource}s read from medical_data_source table based on ids.
     * @throws IllegalStateException if {@code hasWritePermission} is false and {@code
     *     grantedReadMedicalResourceTypes} is empty.
     */
    @NonNull
    public List<MedicalDataSource> getMedicalDataSourcesByIdsWithPermissionChecks(
            @NonNull List<UUID> ids,
            @NonNull Set<Integer> ignoredGrantedReadMedicalResourceTypes,
            @NonNull String ignoredCallingPackageName,
            boolean ignoredHasWritePermission,
            boolean ignoredIsCalledFromBgWithoutBgRead)
            throws SQLiteException {
        // TODO(b/360785035): Use ignored fields for permission checks in read table request.
        // TODO(b/359892459): Add CTS tests once it is properly implemented.
        ReadTableRequest readTableRequest = getReadTableRequestJoinWithAppInfo(ids);
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            return getMedicalDataSources(cursor);
        }
    }

    /**
     * Returns the {@link MedicalDataSource}s stored in the HealthConnect database, optionally
     * restricted by package name.
     *
     * <p>If {@code packageNames} is empty, returns all dataSources, otherwise returns only
     * dataSources belonging to the given apps.
     *
     * @param packageNames list of packageNames of apps to restrict to
     */
    @NonNull
    public List<MedicalDataSource> getMedicalDataSourcesByPackageWithoutPermissionChecks(
            @NonNull Set<String> packageNames) throws SQLiteException {
        ReadTableRequest readTableRequest = getReadTableRequestJoinWithAppInfo();
        if (!packageNames.isEmpty()) {
            List<Long> appInfoIds = mAppInfoHelper.getAppInfoIds(packageNames.stream().toList());
            readTableRequest.setWhereClause(
                    new WhereClauses(AND)
                            .addWhereInLongsClause(APP_INFO_ID_COLUMN_NAME, appInfoIds));
        }
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            return getMedicalDataSources(cursor);
        }
    }

    /**
     * Returns the {@link MedicalDataSource}s stored in the HealthConnect database, optionally
     * restricted by package name.
     *
     * <p>If {@code packageNames} is empty, returns all dataSources, otherwise returns only
     * dataSources belonging to the given apps.
     */
    @NonNull
    public List<MedicalDataSource> getMedicalDataSourcesByPackageWithPermissionChecks(
            @NonNull Set<String> packageNames,
            @NonNull Set<Integer> ignoredGrantedReadMedicalResourceTypes,
            @NonNull String ignoredCallingPackageName,
            boolean ignoredHasWritePermission,
            boolean ignoredIsCalledFromBgWithoutBgRead)
            throws SQLiteException {
        // TODO(b/361540290): Use ignored fields for permission checks in read table request.
        // TODO(b/359892459): Add CTS tests once it is properly implemented.
        ReadTableRequest readTableRequest = getReadTableRequestJoinWithAppInfo();
        if (!packageNames.isEmpty()) {
            List<Long> appInfoIds = mAppInfoHelper.getAppInfoIds(packageNames.stream().toList());
            readTableRequest.setWhereClause(
                    new WhereClauses(AND)
                            .addWhereInLongsClause(APP_INFO_ID_COLUMN_NAME, appInfoIds));
        }
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            return getMedicalDataSources(cursor);
        }
    }

    /**
     * Creates {@link UpsertTableRequest} for the given {@link CreateMedicalDataSourceRequest} and
     * {@code appInfoId}.
     */
    @NonNull
    public static UpsertTableRequest getUpsertTableRequest(
            @NonNull UUID uuid,
            @NonNull CreateMedicalDataSourceRequest createMedicalDataSourceRequest,
            long appInfoId) {
        ContentValues contentValues =
                getContentValues(uuid, createMedicalDataSourceRequest, appInfoId);
        return new UpsertTableRequest(getMainTableName(), contentValues, UNIQUE_COLUMNS_INFO);
    }

    /**
     * Deletes the {@link MedicalDataSource}s stored in the HealthConnect database using the given
     * list of {@code ids}.
     *
     * <p>Note that this deletes without producing change logs, or access logs.
     *
     * @param id the id to delete.
     * @param appInfoIdRestriction if non-null, restricts any deletions to data sources owned by the
     *     given app. If null allows deletions of any data sources.
     * @throws IllegalArgumentException if the id does not exist, or there is an
     *     appInfoIdRestriction and the data source is owned by a different app
     */
    public void deleteMedicalDataSource(@NonNull UUID id, @Nullable Long appInfoIdRestriction)
            throws SQLiteException {
        DeleteTableRequest request =
                new DeleteTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME)
                        .setIds(
                                DATA_SOURCE_UUID_COLUMN_NAME,
                                StorageUtils.getListOfHexStrings(List.of(id)));
        if (appInfoIdRestriction != null) {
            request.setPackageFilter(APP_INFO_ID_COLUMN_NAME, List.of(appInfoIdRestriction));
        }
        ReadTableRequest readTableRequest = getReadTableRequest(List.of(id), appInfoIdRestriction);
        boolean success =
                mTransactionManager.runAsTransaction(
                        db -> {
                            try (Cursor cursor = mTransactionManager.read(db, readTableRequest)) {
                                if (cursor.getCount() != 1) {
                                    return false;
                                }
                            }
                            // This also deletes the contained data, because they are
                            // referenced by foreign key, and so are handled by ON DELETE
                            // CASCADE in the db.
                            mTransactionManager.delete(db, request);
                            return true;
                        });
        if (!success) {
            if (appInfoIdRestriction == null) {
                throw new IllegalArgumentException("Id " + id + " does not exist");
            } else {
                throw new IllegalArgumentException(
                        "Id " + id + " does not exist or is owned by another app");
            }
        }
    }

    /**
     * Creates a {@link MedicalDataSource} for the given {@code uuid}, {@link
     * CreateMedicalDataSourceRequest} and the {@code packageName}.
     */
    @NonNull
    public static MedicalDataSource buildMedicalDataSource(
            @NonNull UUID uuid,
            @NonNull CreateMedicalDataSourceRequest request,
            @NonNull String packageName) {
        return new MedicalDataSource.Builder(
                        uuid.toString(),
                        packageName,
                        request.getFhirBaseUri(),
                        request.getDisplayName())
                .build();
    }

    /**
     * Creates a UUID string to row ID map for all {@link MedicalDataSource}s stored in {@code
     * MEDICAL_DATA_SOURCE_TABLE}.
     */
    @NonNull
    public Map<String, Long> getUuidToRowIdMap(
            @NonNull SQLiteDatabase db, @NonNull List<UUID> dataSourceUuids) {
        Map<String, Long> uuidToRowId = new HashMap<>();
        try (Cursor cursor = mTransactionManager.read(db, getReadTableRequest(dataSourceUuids))) {
            if (cursor.moveToFirst()) {
                do {
                    long rowId = getCursorLong(cursor, MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME);
                    UUID uuid = getCursorUUID(cursor, DATA_SOURCE_UUID_COLUMN_NAME);
                    uuidToRowId.put(uuid.toString(), rowId);
                } while (cursor.moveToNext());
            }
        }
        return uuidToRowId;
    }

    /**
     * Creates a row ID to {@link MedicalDataSource} map for all {@link MedicalDataSource}s stored
     * in {@code MEDICAL_DATA_SOURCE_TABLE}.
     */
    @NonNull
    public Map<Long, MedicalDataSource> getAllRowIdToDataSourceMap(@NonNull SQLiteDatabase db) {
        ReadTableRequest readTableRequest = getReadTableRequestJoinWithAppInfo();
        Map<Long, MedicalDataSource> rowIdToDataSourceMap = new HashMap<>();
        try (Cursor cursor = mTransactionManager.read(db, readTableRequest)) {
            if (cursor.moveToFirst()) {
                do {
                    long rowId = getCursorLong(cursor, MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME);
                    MedicalDataSource dataSource = getMedicalDataSource(cursor);
                    rowIdToDataSourceMap.put(rowId, dataSource);
                } while (cursor.moveToNext());
            }
        }
        return rowIdToDataSourceMap;
    }

    @NonNull
    private static ContentValues getContentValues(
            @NonNull UUID uuid,
            @NonNull CreateMedicalDataSourceRequest createMedicalDataSourceRequest,
            long appInfoId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DATA_SOURCE_UUID_COLUMN_NAME, StorageUtils.convertUUIDToBytes(uuid));
        contentValues.put(
                DISPLAY_NAME_COLUMN_NAME, createMedicalDataSourceRequest.getDisplayName());
        contentValues.put(
                FHIR_BASE_URI_COLUMN_NAME,
                createMedicalDataSourceRequest.getFhirBaseUri().toString());
        contentValues.put(APP_INFO_ID_COLUMN_NAME, appInfoId);
        return contentValues;
    }
}
