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
import static android.health.connect.Constants.MAXIMUM_ALLOWED_CURSOR_COUNT;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_DELETE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_READ;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_UPSERT;
import static android.health.connect.datatypes.FhirVersion.parseFhirVersion;

import static com.android.server.healthconnect.storage.HealthConnectDatabase.createTable;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getAppIdsWhereClause;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getIntersectionOfResourceTypesReadAndGrantedReadPermissions;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getJoinWithIndicesTableFilterOnMedicalResourceTypes;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getJoinWithMedicalDataSourceFilterOnAppIds;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getJoinWithMedicalDataSourceFilterOnDataSourceIds;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getJoinWithMedicalDataSourceFilterOnDataSourceIdsAndAppId;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getReadRequestForDistinctResourceTypesBelongingToDataSourceIds;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getMedicalResourceTypeColumnName;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.LAST_MODIFIED_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.request.ReadTableRequest.UNION;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.health.connect.Constants;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.datatypes.FhirVersion;
import android.health.connect.datatypes.MedicalDataSource;
import android.net.Uri;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateIndexRequest;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;
import com.android.server.healthconnect.utils.TimeSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Helper class for MedicalDataSource.
 *
 * @hide
 */
public class MedicalDataSourceHelper {
    // The number of {@link MedicalDataSource}s that an app is allowed to create
    @VisibleForTesting static final int MAX_ALLOWED_MEDICAL_DATA_SOURCES = 20;

    @VisibleForTesting
    static final String MEDICAL_DATA_SOURCE_TABLE_NAME = "medical_data_source_table";

    @VisibleForTesting static final String DISPLAY_NAME_COLUMN_NAME = "display_name";
    @VisibleForTesting static final String FHIR_BASE_URI_COLUMN_NAME = "fhir_base_uri";
    @VisibleForTesting static final String FHIR_VERSION_COLUMN_NAME = "fhir_version";
    @VisibleForTesting static final String DATA_SOURCE_UUID_COLUMN_NAME = "data_source_uuid";
    private static final String APP_INFO_ID_COLUMN_NAME = "app_info_id";
    private static final String MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME =
            "medical_data_source_row_id";
    private static final List<Pair<String, Integer>> UNIQUE_COLUMNS_INFO =
            List.of(new Pair<>(DATA_SOURCE_UUID_COLUMN_NAME, UpsertTableRequest.TYPE_BLOB));

    private final TransactionManager mTransactionManager;
    private final AppInfoHelper mAppInfoHelper;
    private final TimeSource mTimeSource;
    private final AccessLogsHelper mAccessLogsHelper;

    public MedicalDataSourceHelper(
            TransactionManager transactionManager,
            AppInfoHelper appInfoHelper,
            TimeSource timeSource,
            AccessLogsHelper accessLogsHelper) {
        mTransactionManager = transactionManager;
        mAppInfoHelper = appInfoHelper;
        mTimeSource = timeSource;
        mAccessLogsHelper = accessLogsHelper;
    }

    public static String getMainTableName() {
        return MEDICAL_DATA_SOURCE_TABLE_NAME;
    }

    public static String getPrimaryColumnName() {
        return MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME;
    }

    public static String getDataSourceUuidColumnName() {
        return DATA_SOURCE_UUID_COLUMN_NAME;
    }

    public static String getAppInfoIdColumnName() {
        return APP_INFO_ID_COLUMN_NAME;
    }

    public static String getFhirVersionColumnName() {
        return FHIR_VERSION_COLUMN_NAME;
    }

    private static List<Pair<String, String>> getColumnInfo() {
        return List.of(
                Pair.create(MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME, PRIMARY),
                Pair.create(APP_INFO_ID_COLUMN_NAME, INTEGER_NOT_NULL),
                Pair.create(DISPLAY_NAME_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(FHIR_BASE_URI_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(FHIR_VERSION_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(DATA_SOURCE_UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL),
                Pair.create(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER_NOT_NULL));
    }

    public static CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME, getColumnInfo())
                .addForeignKey(
                        AppInfoHelper.TABLE_NAME,
                        List.of(APP_INFO_ID_COLUMN_NAME),
                        List.of(PRIMARY_COLUMN_NAME));
    }

    /** Creates the medical_data_source table. */
    public static void onInitialUpgrade(SQLiteDatabase db) {
        createTable(db, getCreateTableRequest());
        // There's no significant difference between a unique constraint and unique index.
        // The latter would allow us to drop or recreate it later.
        // The combination of (display_name, app_info_id) should be unique.
        db.execSQL(
                new CreateIndexRequest(
                                MEDICAL_DATA_SOURCE_TABLE_NAME,
                                MEDICAL_DATA_SOURCE_TABLE_NAME + "_display_name_idx",
                                /* isUnique= */ true,
                                List.of(DISPLAY_NAME_COLUMN_NAME, APP_INFO_ID_COLUMN_NAME))
                        .getCommand());
    }

    /**
     * Creates {@link ReadTableRequest} that joins with {@link AppInfoHelper#TABLE_NAME} and filters
     * for the given list of {@code ids}, and restricts to the given apps.
     *
     * @param ids the data source ids to restrict to, if empty allows all data sources
     * @param appInfoRestriction the apps to restrict to, if null allows all apps
     */
    public static ReadTableRequest getReadTableRequest(
            List<UUID> ids, @Nullable Long appInfoRestriction) {
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
    public static WhereClauses getWhereClauses(List<UUID> ids, @Nullable Long appInfoRestriction) {
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
    private static ReadTableRequest getReadTableRequestJoinWithAppInfo() {
        return new ReadTableRequest(getMainTableName())
                .setJoinClause(getJoinClauseWithAppInfoTable());
    }

    /**
     * Creates {@link ReadTableRequest} that joins with {@link AppInfoHelper#TABLE_NAME} and filters
     * for the given list of {@code ids}.
     */
    public static ReadTableRequest getReadTableRequestJoinWithAppInfo(List<UUID> ids) {
        return getReadTableRequest(ids).setJoinClause(getJoinClauseWithAppInfoTable());
    }

    /** Creates {@link ReadTableRequest} for the given list of {@code ids}. */
    public static ReadTableRequest getReadTableRequest(List<UUID> ids) {
        return new ReadTableRequest(getMainTableName())
                .setWhereClause(getReadTableWhereClause(ids));
    }

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
    public static WhereClauses getReadTableWhereClause(List<UUID> ids) {
        return new WhereClauses(AND)
                .addWhereInClauseWithoutQuotes(
                        DATA_SOURCE_UUID_COLUMN_NAME, StorageUtils.getListOfHexStrings(ids));
    }

    /**
     * Returns List of {@link MedicalDataSource}s from the cursor. If the cursor contains more than
     * {@link Constants#MAXIMUM_ALLOWED_CURSOR_COUNT} data sources, it throws {@link
     * IllegalArgumentException}.
     */
    private static List<MedicalDataSource> getMedicalDataSources(Cursor cursor) {
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

    private static MedicalDataSource getMedicalDataSource(Cursor cursor) {
        return new MedicalDataSource.Builder(
                        /* id= */ getCursorUUID(cursor, DATA_SOURCE_UUID_COLUMN_NAME).toString(),
                        /* packageName= */ getCursorString(
                                cursor, AppInfoHelper.PACKAGE_COLUMN_NAME),
                        /* fhirBaseUri= */ Uri.parse(
                                getCursorString(cursor, FHIR_BASE_URI_COLUMN_NAME)),
                        /* displayName= */ getCursorString(cursor, DISPLAY_NAME_COLUMN_NAME))
                .setFhirVersion(parseFhirVersion(getCursorString(cursor, FHIR_VERSION_COLUMN_NAME)))
                // TODO(b/365756516) Populate this value from DB
                .setLastDataUpdateTime(null)
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
    public MedicalDataSource createMedicalDataSource(
            Context context, CreateMedicalDataSourceRequest request, String packageName) {
        try {
            return mTransactionManager.runAsTransaction(
                    (TransactionManager.TransactionRunnableWithReturn<
                                    MedicalDataSource, RuntimeException>)
                            db ->
                                    createMedicalDataSourceAndAppInfoAndCheckLimits(
                                            db,
                                            context,
                                            request,
                                            packageName,
                                            mTimeSource.getInstantNow()));
        } catch (SQLiteConstraintException e) {
            String exceptionMessage = e.getMessage();
            if (exceptionMessage != null && exceptionMessage.contains(DISPLAY_NAME_COLUMN_NAME)) {
                throw new IllegalArgumentException("display name should be unique per calling app");
            }
            throw e;
        }
    }

    private MedicalDataSource createMedicalDataSourceAndAppInfoAndCheckLimits(
            SQLiteDatabase db,
            Context context,
            CreateMedicalDataSourceRequest request,
            String packageName,
            Instant instant) {
        long appInfoId = mAppInfoHelper.getOrInsertAppInfoId(db, packageName, context);

        if (getMedicalDataSourcesCount(appInfoId) >= MAX_ALLOWED_MEDICAL_DATA_SOURCES) {
            throw new IllegalArgumentException(
                    "The maximum number of data sources has been reached.");
        }

        UUID dataSourceUuid = UUID.randomUUID();
        UpsertTableRequest upsertTableRequest =
                getUpsertTableRequest(dataSourceUuid, request, appInfoId, instant);
        mTransactionManager.insert(db, upsertTableRequest);
        mAccessLogsHelper.addAccessLog(
                db,
                packageName,
                /* medicalResourceTypes= */ Set.of(),
                OPERATION_TYPE_UPSERT,
                /* accessedMedicalDataSource= */ true);
        return buildMedicalDataSource(dataSourceUuid, request, packageName);
    }

    private int getMedicalDataSourcesCount(long appInfoId) {
        ReadTableRequest readTableRequest =
                new ReadTableRequest(getMainTableName())
                        .setColumnNames(List.of("COUNT(*)"))
                        .setJoinClause(getJoinClauseWithAppInfoTable());
        readTableRequest.setWhereClause(
                new WhereClauses(AND)
                        .addWhereInLongsClause(APP_INFO_ID_COLUMN_NAME, List.of(appInfoId)));
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                throw new IllegalStateException("Could not get data sources count");
            }
        }
    }

    /**
     * Reads the {@link MedicalDataSource}s stored in the HealthConnect database using the given
     * list of {@code ids}.
     *
     * @param ids a list of {@link MedicalDataSource} ids.
     * @return List of {@link MedicalDataSource}s read from medical_data_source table based on ids.
     */
    public List<MedicalDataSource> getMedicalDataSourcesByIdsWithoutPermissionChecks(List<UUID> ids)
            throws SQLiteException {
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
     * @throws IllegalArgumentException if {@code callingPackageName} has not written any data
     *     sources so the appId does not exist in the {@link AppInfoHelper#TABLE_NAME} and the
     *     {@code callingPackageName} has no read permissions either.
     */
    public List<MedicalDataSource> getMedicalDataSourcesByIdsWithPermissionChecks(
            List<UUID> ids,
            Set<Integer> grantedReadMedicalResourceTypes,
            String callingPackageName,
            boolean hasWritePermission,
            boolean isCalledFromBgWithoutBgRead,
            AppInfoHelper appInfoHelper)
            throws SQLiteException {
        // TODO(b/359892459): Add CTS tests once it is properly implemented.
        if (!hasWritePermission && grantedReadMedicalResourceTypes.isEmpty()) {
            throw new IllegalStateException("no read or write permission");
        }

        long appId = appInfoHelper.getAppInfoId(callingPackageName);
        // This is an optimization to not hit the db, when we know that the app has not
        // created any dataSources hence appId does not exist (so no self data to read)
        // and has no read permission, so won't be able to read dataSources written by
        // other apps either.
        if (appId == DEFAULT_LONG && grantedReadMedicalResourceTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "app has not written any data and does not have any read permission");
        }
        return mTransactionManager.runAsTransaction(
                (TransactionManager.TransactionRunnableWithReturn<
                                List<MedicalDataSource>, RuntimeException>)
                        db -> {
                            ReadTableRequest readRequest =
                                    getReadRequestBasedOnPermissionFilters(
                                            ids,
                                            grantedReadMedicalResourceTypes,
                                            appId,
                                            hasWritePermission,
                                            isCalledFromBgWithoutBgRead);

                            return readMedicalDataSourcesAndAddAccessLog(
                                    db,
                                    readRequest,
                                    grantedReadMedicalResourceTypes,
                                    callingPackageName,
                                    isCalledFromBgWithoutBgRead);
                        });
    }

    private List<MedicalDataSource> readMedicalDataSourcesAndAddAccessLog(
            SQLiteDatabase db,
            ReadTableRequest request,
            Set<Integer> grantedReadMedicalResourceTypes,
            String callingPackageName,
            boolean isCalledFromBgWithoutBgRead) {
        List<MedicalDataSource> medicalDataSources;
        try (Cursor cursor = mTransactionManager.read(db, request)) {
            medicalDataSources = getMedicalDataSources(cursor);
        }

        // If the app is called from background but without background read
        // permission, the most the app can do, is to read their own data. Same
        // when the grantedReadMedicalResourceTypes is empty. And we don't need
        // to add access logs when an app intends to access their own data. If
        // medicalDataSources is empty, it means that the app hasn't read any
        // dataSources out, so no need to add access logs either.
        if (!isCalledFromBgWithoutBgRead
                && !grantedReadMedicalResourceTypes.isEmpty()
                && !medicalDataSources.isEmpty()) {
            // We need to figure out from the dataSources that were read, what
            // is the resource types relevant to those dataSources, we add
            // access logs only if there's any intersection between read
            // permissions and resource types's dataSources. If intersection is
            // empty, it means that the data read was accessed through self
            // read, hence no access log needed.
            Set<Integer> resourceTypes =
                    getIntersectionOfResourceTypesReadAndGrantedReadPermissions(
                            getMedicalResourceTypesBelongingToDataSourceIds(
                                    getUUIDsRead(medicalDataSources)),
                            grantedReadMedicalResourceTypes);
            if (!resourceTypes.isEmpty()) {
                mAccessLogsHelper.addAccessLog(
                        db,
                        callingPackageName,
                        /* medicalResourceTypes= */ Set.of(),
                        OPERATION_TYPE_READ,
                        /* accessedMedicalDataSource= */ true);
            }
        }
        return medicalDataSources;
    }

    private Set<Integer> getMedicalResourceTypesBelongingToDataSourceIds(List<UUID> dataSourceIds) {
        Set<Integer> resourceTypes = new HashSet<>();
        ReadTableRequest readRequest =
                getReadRequestForDistinctResourceTypesBelongingToDataSourceIds(dataSourceIds);
        try (Cursor cursor = mTransactionManager.read(readRequest)) {
            if (cursor.moveToFirst()) {
                do {
                    resourceTypes.add(getCursorInt(cursor, getMedicalResourceTypeColumnName()));
                } while (cursor.moveToNext());
            }
        }
        return resourceTypes;
    }

    private static List<UUID> getUUIDsRead(List<MedicalDataSource> dataSources) {
        return dataSources.stream()
                .map(MedicalDataSource::getId)
                .map(UUID::fromString)
                .collect(Collectors.toList());
    }

    private static ReadTableRequest getReadRequestBasedOnPermissionFilters(
            List<UUID> ids,
            Set<Integer> grantedReadMedicalResourceTypes,
            long appId,
            boolean hasWritePermission,
            boolean isCalledFromBgWithoutBgRead) {
        // Reading all dataSource ids that are written by the calling package.
        ReadTableRequest readAllIdsWrittenByCallingPackage =
                getReadTableRequestForAllDataSourcesWrittenByCallingApp(ids, appId);

        // App is calling the API from background without background read permission.
        if (isCalledFromBgWithoutBgRead) {
            // App has writePermission.
            // App can read all dataSources they wrote themselves.
            if (hasWritePermission) {
                return readAllIdsWrittenByCallingPackage;
            }
            // App does not have writePermission.
            // App has normal read permission for some medicalResourceTypes.
            // App can read the dataSources that belong to those medicalResourceTypes
            // and were written by the app itself.
            return getReadTableRequestForDataSourceWrittenByAppIdFilterOnResourceTypes(
                    ids, grantedReadMedicalResourceTypes, appId);
        }

        // The request to read out all dataSource ids belonging to the medicalResourceTypes of
        // the grantedReadMedicalResourceTypes.
        ReadTableRequest readIdsOfTheGrantedMedicalResourceTypes =
                getReadTableRequestForDataSourcesFilterOnResourceTypes(
                        ids, grantedReadMedicalResourceTypes);

        // App is in background with backgroundReadPermission or in foreground.
        // App has writePermission.
        if (hasWritePermission) {
            // App does not have any read permissions for any medicalResourceTypes.
            // App can read all dataSources they wrote themselves.
            if (grantedReadMedicalResourceTypes.isEmpty()) {
                return readAllIdsWrittenByCallingPackage;
            }
            // App has some read permissions for medicalResourceTypes.
            // App can read all dataSources they wrote themselves and the dataSources belonging to
            // the medicalResourceTypes they have read permission for.
            // UNION ALL allows for duplicate values, but we want the rows to be distinct.
            // Hence why we use normal UNION.
            return readAllIdsWrittenByCallingPackage
                    .setUnionReadRequests(List.of(readIdsOfTheGrantedMedicalResourceTypes))
                    .setUnionType(UNION);
        }
        // App is in background with background read permission or in foreground.
        // App has some read permissions for medicalResourceTypes.
        // App does not have write permission.
        // App can read all dataSources belonging to the granted medicalResourceType read
        // permissions.
        return readIdsOfTheGrantedMedicalResourceTypes;
    }

    @VisibleForTesting
    static ReadTableRequest getReadTableRequestForDataSourceWrittenByAppIdFilterOnResourceTypes(
            List<UUID> ids, Set<Integer> medicalResourceTypes, long appId) {
        SqlJoin joinWithDataSource =
                getJoinWithMedicalDataSourceFilterOnDataSourceIdsAndAppId(
                        ids, appId, getJoinClauseWithAppInfoTable());
        SqlJoin joinWithIndices =
                getJoinWithIndicesTableFilterOnMedicalResourceTypes(medicalResourceTypes);
        return getReadTableRequestForDataSources(joinWithDataSource.attachJoin(joinWithIndices));
    }

    @VisibleForTesting
    static ReadTableRequest getReadTableRequestForDataSourcesFilterOnResourceTypes(
            List<UUID> ids, Set<Integer> medicalResourceTypes) {
        SqlJoin joinWithDataSource =
                getJoinWithMedicalDataSourceFilterOnDataSourceIds(
                        ids, getJoinClauseWithAppInfoTable());
        SqlJoin joinWithIndices =
                getJoinWithIndicesTableFilterOnMedicalResourceTypes(medicalResourceTypes);
        return getReadTableRequestForDataSources(joinWithDataSource.attachJoin(joinWithIndices));
    }

    /**
     * Creates a {@link ReadTableRequest} filtering on the given {@code appIds} and {@code
     * medicalResourceTypes}. If either sets are empty, they won't be taken into account when
     * filtering.
     */
    private static ReadTableRequest getReadTableRequestFilterOnAppIdAndResourceTypes(
            Set<Long> appIds, Set<Integer> medicalResourceTypes) {
        SqlJoin joinWithDataSource =
                getJoinWithMedicalDataSourceFilterOnAppIds(appIds, getJoinClauseWithAppInfoTable());
        SqlJoin joinWithIndices =
                getJoinWithIndicesTableFilterOnMedicalResourceTypes(medicalResourceTypes);
        return getReadTableRequestForDataSources(joinWithDataSource.attachJoin(joinWithIndices));
    }

    private static ReadTableRequest getReadTableRequestForDataSources(SqlJoin joinClause) {
        return new ReadTableRequest(MedicalResourceHelper.getMainTableName())
                .setDistinctClause(true)
                .setColumnNames(
                        List.of(
                                AppInfoHelper.PACKAGE_COLUMN_NAME,
                                DATA_SOURCE_UUID_COLUMN_NAME,
                                FHIR_BASE_URI_COLUMN_NAME,
                                DISPLAY_NAME_COLUMN_NAME,
                                FHIR_VERSION_COLUMN_NAME))
                .setJoinClause(joinClause);
    }

    /**
     * Creates {@link ReadTableRequest} that joins with {@link AppInfoHelper#TABLE_NAME} and filters
     * for the given list of {@code ids} and {@code appId}.
     */
    private static ReadTableRequest getReadTableRequestForAllDataSourcesWrittenByCallingApp(
            List<UUID> ids, long appId) {
        return getReadTableRequest(ids, appId)
                .setDistinctClause(true)
                .setColumnNames(
                        List.of(
                                AppInfoHelper.PACKAGE_COLUMN_NAME,
                                DATA_SOURCE_UUID_COLUMN_NAME,
                                FHIR_BASE_URI_COLUMN_NAME,
                                DISPLAY_NAME_COLUMN_NAME,
                                FHIR_VERSION_COLUMN_NAME))
                .setJoinClause(getJoinClauseWithAppInfoTable());
    }

    /**
     * Creates {@link ReadTableRequest} that joins with {@link AppInfoHelper#TABLE_NAME} and filters
     * for the given {@code appId}.
     */
    private static ReadTableRequest getReadRequestForDataSourcesWrittenByCallingApp(long appId) {
        return new ReadTableRequest(getMainTableName())
                .setWhereClause(getAppIdsWhereClause(Set.of(appId)))
                .setColumnNames(
                        List.of(
                                AppInfoHelper.PACKAGE_COLUMN_NAME,
                                DATA_SOURCE_UUID_COLUMN_NAME,
                                FHIR_BASE_URI_COLUMN_NAME,
                                DISPLAY_NAME_COLUMN_NAME,
                                FHIR_VERSION_COLUMN_NAME))
                .setJoinClause(getJoinClauseWithAppInfoTable());
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
    public List<MedicalDataSource> getMedicalDataSourcesByPackageWithoutPermissionChecks(
            Set<String> packageNames) throws SQLiteException {
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
     * Returns the {@link MedicalDataSource}s stored in the HealthConnect database filtering on the
     * given {@code packageNames} if not empty and based on the {@code callingPackageName}'s
     * permissions.
     *
     * <p>If {@code packageNames} is empty, returns all dataSources, otherwise returns only
     * dataSources belonging to the given apps.
     *
     * @throws IllegalArgumentException if {@code callingPackageName} has not written any data
     *     sources so the appId does not exist in the {@link AppInfoHelper#TABLE_NAME} and the
     *     {@code callingPackageName} has no read permissions either. Or if the app can only read
     *     self data and the app is filtering using {@code packageNames} but the app itself is not
     *     included in it.
     */
    // TODO(b/359892459): Add CTS tests once it is properly implemented.
    public List<MedicalDataSource> getMedicalDataSourcesByPackageWithPermissionChecks(
            Set<String> packageNames,
            Set<Integer> grantedReadMedicalResourceTypes,
            String callingPackageName,
            boolean hasWritePermission,
            boolean isCalledFromBgWithoutBgRead)
            throws SQLiteException {
        long callingAppId = mAppInfoHelper.getAppInfoId(callingPackageName);
        // This is an optimization to not hit the db, when we know that the app has not
        // created any dataSources hence appId does not exist (so no self data to read)
        // and has no read permission, so won't be able to read dataSources written by
        // other apps either.
        if (callingAppId == DEFAULT_LONG && grantedReadMedicalResourceTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "app has not written any data and does not have any read permission");
        }

        List<Long> appIds = mAppInfoHelper.getAppInfoIds(packageNames.stream().toList());

        // App is in bg without bg read permission so the app can only read dataSources written by
        // itself, but if the request is filtering on a set of packageNames (packageNames not empty)
        // and the app itself is not in the packageNames, there is nothing to be read.
        boolean intendsToReadOnlyOtherAppsData =
                !packageNames.isEmpty() && !packageNames.contains(callingPackageName);
        if (isCalledFromBgWithoutBgRead && intendsToReadOnlyOtherAppsData) {
            throw new IllegalArgumentException(
                    "app doesn't have permission to read based on the given packages");
        }

        // Same with if app in foreground or app in bg with bg read perm, app has write permission
        // but no read permission, app can only read dataSource it has written itself.
        // However, if the request is filtering on a set of packageNames (packageNames not empty)
        // and the app itself is not in the packageNames, there is nothing to be read.
        boolean canReadSelfDataOnly =
                !isCalledFromBgWithoutBgRead
                        && hasWritePermission
                        && grantedReadMedicalResourceTypes.isEmpty();
        if (canReadSelfDataOnly && intendsToReadOnlyOtherAppsData) {
            throw new IllegalArgumentException(
                    "app doesn't have permission to read based on the given packages");
        }
        return mTransactionManager.runAsTransaction(
                (TransactionManager.TransactionRunnableWithReturn<
                                List<MedicalDataSource>, RuntimeException>)
                        db -> {
                            ReadTableRequest readRequest =
                                    getReadRequestByPackagesWithPermissionChecks(
                                            new HashSet<>(appIds),
                                            grantedReadMedicalResourceTypes,
                                            callingAppId,
                                            hasWritePermission,
                                            isCalledFromBgWithoutBgRead);

                            return readMedicalDataSourcesAndAddAccessLog(
                                    db,
                                    readRequest,
                                    grantedReadMedicalResourceTypes,
                                    callingPackageName,
                                    isCalledFromBgWithoutBgRead);
                        });
    }

    private static ReadTableRequest getReadRequestByPackagesWithPermissionChecks(
            Set<Long> appIds,
            Set<Integer> grantedReadMedicalResourceTypes,
            long callingAppId,
            boolean hasWritePermission,
            boolean isCalledFromBgWithoutBgRead) {
        // Reading all dataSources written by the calling app.
        ReadTableRequest readAllDataSourcesWrittenByCallingPackage =
                getReadRequestForDataSourcesWrittenByCallingApp(callingAppId);

        // App is calling the API from background without background read permission.
        if (isCalledFromBgWithoutBgRead) {
            // App has writePermission.
            // App can read all dataSources they wrote themselves.
            if (hasWritePermission) {
                return readAllDataSourcesWrittenByCallingPackage;
            }
            // App does not have writePermission.
            // App has normal read permission for some medicalResourceTypes.
            // App can read the dataSources that belong to those medicalResourceTypes
            // and were written by the app itself.
            return getReadTableRequestFilterOnAppIdAndResourceTypes(
                    Set.of(callingAppId), grantedReadMedicalResourceTypes);
        }

        // The request to read out all dataSources belonging to the medicalResourceTypes of
        // the grantedReadMedicalResourceTypes and written by the given packageNames.
        ReadTableRequest readDataSourcesOfTheGrantedMedicalResourceTypes =
                getReadTableRequestFilterOnAppIdAndResourceTypes(
                        appIds, grantedReadMedicalResourceTypes);

        // App is in background with backgroundReadPermission or in foreground.
        // App has writePermission.
        if (hasWritePermission) {
            // App does not have any read permissions for any medicalResourceTypes.
            // App can read all dataSources they wrote themselves.
            if (grantedReadMedicalResourceTypes.isEmpty()) {
                return readAllDataSourcesWrittenByCallingPackage;
            }
            // If our set of appIds is not empty, means the request is filtering based on
            // packageNames. So we don't include self data, if request is filtering based on
            // packageNames but the callingAppId is not in the set of the given packageNames's
            // appIds.
            if (!appIds.isEmpty() && !appIds.contains(callingAppId)) {
                return readDataSourcesOfTheGrantedMedicalResourceTypes;
            }
            // App has some read permissions for medicalResourceTypes.
            // App can read all dataSources they wrote themselves and the dataSources belonging to
            // the medicalResourceTypes they have read permission for.
            // UNION ALL allows for duplicate values, but we want the rows to be distinct.
            // Hence why we use normal UNION.
            return readDataSourcesOfTheGrantedMedicalResourceTypes
                    .setUnionReadRequests(List.of(readAllDataSourcesWrittenByCallingPackage))
                    .setUnionType(UNION);
        }
        // App is in background with background read permission or in foreground.
        // App has some read permissions for medicalResourceTypes.
        // App does not have write permission.
        // App can read all dataSources belonging to the granted medicalResourceType read
        // permissions.
        return readDataSourcesOfTheGrantedMedicalResourceTypes;
    }

    /**
     * Creates {@link UpsertTableRequest} for the given {@link CreateMedicalDataSourceRequest} and
     * {@code appInfoId}.
     */
    public static UpsertTableRequest getUpsertTableRequest(
            UUID uuid,
            CreateMedicalDataSourceRequest createMedicalDataSourceRequest,
            long appInfoId,
            Instant instant) {
        ContentValues contentValues =
                getContentValues(uuid, createMedicalDataSourceRequest, appInfoId, instant);
        return new UpsertTableRequest(getMainTableName(), contentValues, UNIQUE_COLUMNS_INFO);
    }

    private static DeleteTableRequest getDeleteRequestForDataSourceUuid(
            UUID id, @Nullable Long appInfoIdRestriction) {
        DeleteTableRequest request =
                new DeleteTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME)
                        .setIds(
                                DATA_SOURCE_UUID_COLUMN_NAME,
                                StorageUtils.getListOfHexStrings(List.of(id)));
        if (appInfoIdRestriction == null) {
            return request;
        }
        return request.setPackageFilter(APP_INFO_ID_COLUMN_NAME, List.of(appInfoIdRestriction));
    }

    /**
     * Deletes the {@link MedicalDataSource}s stored in the HealthConnect database using the given
     * {@code id}.
     *
     * <p>Note that this deletes without producing change logs, or access logs.
     *
     * @param id the id to delete.
     * @throws IllegalArgumentException if the id does not exist.
     */
    public void deleteMedicalDataSourceWithoutPermissionChecks(UUID id) throws SQLiteException {
        mTransactionManager.runAsTransaction(
                db -> {
                    try (Cursor cursor =
                            mTransactionManager.read(
                                    db,
                                    getReadTableRequest(
                                            List.of(id), /* appInfoRestriction= */ null))) {
                        if (cursor.getCount() != 1) {
                            throw new IllegalArgumentException("Id " + id + " does not exist");
                        }
                    }
                    // This also deletes the contained data, because they are
                    // referenced by foreign key, and so are handled by ON DELETE
                    // CASCADE in the db.
                    mTransactionManager.delete(
                            db,
                            getDeleteRequestForDataSourceUuid(
                                    id, /* appInfoIdRestriction= */ null));
                });
    }

    /**
     * Deletes the {@link MedicalDataSource}s stored in the HealthConnect database using the given
     * {@code id}.
     *
     * <p>Note that this deletes without producing change logs.
     *
     * @param id the id to delete.
     * @param callingPackageName restricts any deletions to data sources owned by the given app.
     * @throws IllegalArgumentException if the id does not exist, or dataSource exists but it is not
     *     owned by the {@code callingPackageName}.
     */
    public void deleteMedicalDataSourceWithPermissionChecks(UUID id, String callingPackageName)
            throws SQLiteException {
        long appId = mAppInfoHelper.getAppInfoId(callingPackageName);
        if (appId == Constants.DEFAULT_LONG) {
            throw new IllegalArgumentException(
                    "Deletion not permitted as app has inserted no data.");
        }
        mTransactionManager.runAsTransaction(
                db -> {
                    try (Cursor cursor =
                            mTransactionManager.read(db, getReadTableRequest(List.of(id), appId))) {
                        if (cursor.getCount() != 1) {
                            throw new IllegalArgumentException(
                                    "Id " + id + " does not exist or is owned by another app");
                        }
                    }

                    // Medical resource types that belong to this dataSource and will be deleted.
                    Set<Integer> medicalResourceTypes =
                            getMedicalResourceTypesBelongingToDataSourceIds(List.of(id));
                    // This also deletes the contained data, because they are
                    // referenced by foreign key, and so are handled by ON DELETE
                    // CASCADE in the db.
                    mTransactionManager.delete(db, getDeleteRequestForDataSourceUuid(id, appId));
                    mAccessLogsHelper.addAccessLog(
                            db,
                            callingPackageName,
                            medicalResourceTypes,
                            OPERATION_TYPE_DELETE,
                            /* accessedMedicalDataSource= */ true);
                });
    }

    /**
     * Creates a {@link MedicalDataSource} for the given {@code uuid}, {@link
     * CreateMedicalDataSourceRequest} and the {@code packageName}.
     */
    public static MedicalDataSource buildMedicalDataSource(
            UUID uuid, CreateMedicalDataSourceRequest request, String packageName) {
        return new MedicalDataSource.Builder(
                        uuid.toString(),
                        packageName,
                        request.getFhirBaseUri(),
                        request.getDisplayName())
                .setFhirVersion(request.getFhirVersion())
                .build();
    }

    /**
     * Creates a UUID string to row ID and FHIR version map for {@link MedicalDataSource}s stored in
     * {@code MEDICAL_DATA_SOURCE_TABLE} that were created by the app matching the {@code *
     * appInfoIdRestriction}.
     */
    public Map<String, Pair<Long, FhirVersion>> getUuidToRowIdAndVersionMap(
            SQLiteDatabase db, long appInfoIdRestriction, List<UUID> dataSourceUuids) {
        Map<String, Pair<Long, FhirVersion>> uuidToRowIdAndVersion = new HashMap<>();
        try (Cursor cursor =
                mTransactionManager.read(
                        db, getReadTableRequest(dataSourceUuids, appInfoIdRestriction))) {
            if (cursor.moveToFirst()) {
                do {
                    UUID uuid = getCursorUUID(cursor, DATA_SOURCE_UUID_COLUMN_NAME);
                    long rowId = getCursorLong(cursor, MEDICAL_DATA_SOURCE_PRIMARY_COLUMN_NAME);
                    FhirVersion fhirVersion =
                            parseFhirVersion(getCursorString(cursor, FHIR_VERSION_COLUMN_NAME));
                    uuidToRowIdAndVersion.put(uuid.toString(), new Pair(rowId, fhirVersion));
                } while (cursor.moveToNext());
            }
        }
        return uuidToRowIdAndVersion;
    }

    /**
     * Creates a row ID to {@link MedicalDataSource} map for all {@link MedicalDataSource}s stored
     * in {@code MEDICAL_DATA_SOURCE_TABLE}.
     */
    public Map<Long, MedicalDataSource> getAllRowIdToDataSourceMap(SQLiteDatabase db) {
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

    /**
     * Gets all distinct app info ids from {@code APP_INFO_ID_COLUMN_NAME} for all {@link
     * MedicalDataSource}s stored in {@code MEDICAL_DATA_SOURCE_TABLE}.
     */
    public Set<Long> getAllContributorAppInfoIds() {
        ReadTableRequest readTableRequest =
                new ReadTableRequest(getMainTableName())
                        .setDistinctClause(true)
                        .setColumnNames(List.of(APP_INFO_ID_COLUMN_NAME));
        Set<Long> appInfoIds = new HashSet<>();
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            if (cursor.moveToFirst()) {
                do {
                    appInfoIds.add(getCursorLong(cursor, APP_INFO_ID_COLUMN_NAME));
                } while (cursor.moveToNext());
            }
        }
        return appInfoIds;
    }

    private static ContentValues getContentValues(
            UUID uuid,
            CreateMedicalDataSourceRequest createMedicalDataSourceRequest,
            long appInfoId,
            Instant instant) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(DATA_SOURCE_UUID_COLUMN_NAME, StorageUtils.convertUUIDToBytes(uuid));
        contentValues.put(
                DISPLAY_NAME_COLUMN_NAME, createMedicalDataSourceRequest.getDisplayName());
        contentValues.put(
                FHIR_BASE_URI_COLUMN_NAME,
                createMedicalDataSourceRequest.getFhirBaseUri().toString());
        contentValues.put(
                FHIR_VERSION_COLUMN_NAME,
                createMedicalDataSourceRequest.getFhirVersion().toString());
        contentValues.put(APP_INFO_ID_COLUMN_NAME, appInfoId);
        contentValues.put(LAST_MODIFIED_TIME_COLUMN_NAME, instant.toEpochMilli());
        return contentValues;
    }
}
