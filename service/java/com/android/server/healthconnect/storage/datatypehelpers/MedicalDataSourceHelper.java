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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.health.connect.Constants;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.datatypes.MedicalDataSource;
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
    @VisibleForTesting static final String APP_INFO_ID_COLUMN_NAME = "app_info_id";
    @VisibleForTesting static final String DATA_SOURCE_UUID_COLUMN_NAME = "data_source_uuid";
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
    public static String getDataSourceUuidColumnName() {
        return DATA_SOURCE_UUID_COLUMN_NAME;
    }

    @NonNull
    private static List<Pair<String, String>> getColumnInfo() {
        return List.of(
                Pair.create(PRIMARY_COLUMN_NAME, PRIMARY),
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
     * for the given list of {@code ids}.
     */
    @NonNull
    public static ReadTableRequest getReadTableRequestJoinWithAppInfo(@NonNull List<String> ids) {
        return getReadTableRequest(ids).setJoinClause(getJoinClauseWithAppInfoTable());
    }

    /** Creates {@link ReadTableRequest} for the given list of {@code ids}. */
    @NonNull
    public static ReadTableRequest getReadTableRequest(@NonNull List<String> ids) {
        return new ReadTableRequest(getMainTableName())
                .setWhereClause(getReadTableWhereClause(ids));
    }

    /**
     * Returns a {@link WhereClauses} that limits to data sources with id in {@code ids}.
     *
     * @param ids the ids to limit to.
     */
    @NonNull
    private static SqlJoin getJoinClauseWithAppInfoTable() {
        return new SqlJoin(
                        MEDICAL_DATA_SOURCE_TABLE_NAME,
                        AppInfoHelper.TABLE_NAME,
                        APP_INFO_ID_COLUMN_NAME,
                        PRIMARY_COLUMN_NAME)
                .setJoinType(SqlJoin.SQL_JOIN_INNER);
    }

    @NonNull
    private static WhereClauses getReadTableWhereClause(@NonNull List<String> ids) {
        List<UUID> uuids = ids.stream().map(UUID::fromString).toList();
        return new WhereClauses(AND)
                .addWhereInClauseWithoutQuotes(
                        DATA_SOURCE_UUID_COLUMN_NAME, StorageUtils.getListOfHexStrings(uuids));
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
                        /* fhirBaseUri= */ getCursorString(cursor, FHIR_BASE_URI_COLUMN_NAME),
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
    public List<MedicalDataSource> getMedicalDataSources(@NonNull List<String> ids)
            throws SQLiteException {
        ReadTableRequest readTableRequest = getReadTableRequestJoinWithAppInfo(ids);
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
     * @throws IllegalArgumentException if the id does not exist
     */
    public static void deleteMedicalDataSource(@NonNull String id) throws SQLiteException {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Id " + id + " does not exist");
        }
        DeleteTableRequest request =
                new DeleteTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME)
                        .setIds(
                                DATA_SOURCE_UUID_COLUMN_NAME,
                                StorageUtils.getListOfHexStrings(List.of(uuid)));
        ReadTableRequest readTableRequest = getReadTableRequest(List.of(id));
        TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        boolean success =
                transactionManager.runAsTransaction(
                        (TransactionManager.TransactionRunnableWithReturn<Boolean, SQLiteException>)
                                db -> {
                                    try (Cursor cursor =
                                            transactionManager.read(db, readTableRequest)) {
                                        if (cursor.getCount() != 1) {
                                            return false;
                                        }
                                    }
                                    // This also deletes the contained data, because they are
                                    // referenced by foreign key, and so are handled by ON DELETE
                                    // CASCADE in the db.
                                    transactionManager.delete(db, request);
                                    return true;
                                });
        if (!success) {
            throw new IllegalArgumentException("Id " + id + " does not exist");
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
            @NonNull SQLiteDatabase db, @NonNull List<String> dataSourceUuids) {
        Map<String, Long> uuidToRowId = new HashMap<>();
        try (Cursor cursor = mTransactionManager.read(db, getReadTableRequest(dataSourceUuids))) {
            if (cursor.moveToFirst()) {
                do {
                    long rowId = getCursorLong(cursor, PRIMARY_COLUMN_NAME);
                    UUID uuid = getCursorUUID(cursor, DATA_SOURCE_UUID_COLUMN_NAME);
                    uuidToRowId.put(uuid.toString(), rowId);
                } while (cursor.moveToNext());
            }
        }
        return uuidToRowId;
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
                FHIR_BASE_URI_COLUMN_NAME, createMedicalDataSourceRequest.getFhirBaseUri());
        contentValues.put(APP_INFO_ID_COLUMN_NAME, appInfoId);
        return contentValues;
    }
}
