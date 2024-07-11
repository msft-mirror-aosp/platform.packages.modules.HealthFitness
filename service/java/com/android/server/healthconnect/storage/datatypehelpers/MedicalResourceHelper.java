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

package com.android.server.healthconnect.storage.datatypehelpers;

import static android.health.connect.Constants.MAXIMUM_ALLOWED_CURSOR_COUNT;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;

import static com.android.healthfitness.flags.Flags.personalHealthRecord;
import static com.android.server.healthconnect.phr.FhirJsonExtractor.getFhirResourceTypeInt;
import static com.android.server.healthconnect.storage.HealthConnectDatabase.createTable;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.getDataSourceUuidColumnName;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getChildTableUpsertRequests;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getCreateMedicalResourceIndicesTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getMedicalResourceTypeColumnName;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getTableName;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.LAST_MODIFIED_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.SqlJoin.SQL_JOIN_INNER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.generateMedicalResourceUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.health.connect.Constants;
import android.health.connect.MedicalResourceId;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.MedicalResource;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.TransactionManager.TransactionRunnableWithReturn;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.TableColumnPair;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class for MedicalResource table.
 *
 * @hide
 */
public final class MedicalResourceHelper {
    private static final String TAG = "MedicalResourceHelper";
    @VisibleForTesting static final String MEDICAL_RESOURCE_TABLE_NAME = "medical_resource_table";
    @VisibleForTesting static final String FHIR_RESOURCE_TYPE_COLUMN_NAME = "fhir_resource_type";
    @VisibleForTesting static final String FHIR_DATA_COLUMN_NAME = "fhir_data";
    @VisibleForTesting static final String FHIR_VERSION_COLUMN_NAME = "fhir_version";
    @VisibleForTesting static final String DATA_SOURCE_ID_COLUMN_NAME = "data_source_id";
    @VisibleForTesting static final String FHIR_RESOURCE_ID_COLUMN_NAME = "fhir_resource_id";
    private static final List<Pair<String, Integer>> UNIQUE_COLUMNS_INFO =
            List.of(new Pair<>(UUID_COLUMN_NAME, UpsertTableRequest.TYPE_BLOB));

    private static final Map<Integer, Integer> FHIR_RESOURCE_TYPE_TO_MEDICAL_RESOURCE_TYPE =
            new HashMap<>();

    private final TransactionManager mTransactionManager;
    private final MedicalDataSourceHelper mMedicalDataSourceHelper;

    public MedicalResourceHelper(
            @NonNull TransactionManager transactionManager,
            @NonNull MedicalDataSourceHelper medicalDataSourceHelper) {
        mTransactionManager = transactionManager;
        mMedicalDataSourceHelper = medicalDataSourceHelper;
    }

    @NonNull
    public static String getMainTableName() {
        return MEDICAL_RESOURCE_TABLE_NAME;
    }

    @NonNull
    private static List<Pair<String, String>> getColumnInfo() {
        return List.of(
                Pair.create(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT),
                Pair.create(FHIR_RESOURCE_TYPE_COLUMN_NAME, INTEGER_NOT_NULL),
                Pair.create(FHIR_RESOURCE_ID_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(FHIR_DATA_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(FHIR_VERSION_COLUMN_NAME, TEXT_NULL),
                Pair.create(DATA_SOURCE_ID_COLUMN_NAME, INTEGER_NOT_NULL),
                Pair.create(UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL),
                Pair.create(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER));
    }

    // TODO(b/352010531): Remove the use of setChildTableRequests and upsert child table directly
    // in {@code upsertMedicalResources} to improve readability.
    @NonNull
    public static CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(MEDICAL_RESOURCE_TABLE_NAME, getColumnInfo())
                .addForeignKey(
                        MedicalDataSourceHelper.getMainTableName(),
                        Collections.singletonList(DATA_SOURCE_ID_COLUMN_NAME),
                        Collections.singletonList(PRIMARY_COLUMN_NAME))
                .setChildTableRequests(
                        Collections.singletonList(getCreateMedicalResourceIndicesTableRequest()));
    }

    /** Creates the medical_resource table. */
    public static void onInitialUpgrade(@NonNull SQLiteDatabase db) {
        createTable(db, getCreateTableRequest());
    }

    /**
     * Reads the {@link MedicalResource}s stored in the HealthConnect database.
     *
     * @param medicalResourceIds a {@link MedicalResourceId}.
     * @return List of {@link MedicalResource}s read from medical_resource table based on ids.
     */
    public List<MedicalResource> readMedicalResourcesByIds(
            @NonNull List<MedicalResourceId> medicalResourceIds) throws SQLiteException {
        List<MedicalResource> medicalResources;
        ReadTableRequest readTableRequest = getReadTableRequest(medicalResourceIds);
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            medicalResources = getMedicalResources(cursor);
        }
        return medicalResources;
    }

    /** Creates {@link ReadTableRequest} for the given {@link MedicalResourceId}s. */
    @NonNull
    @VisibleForTesting
    static ReadTableRequest getReadTableRequest(
            @NonNull List<MedicalResourceId> medicalResourceIds) {
        return new ReadTableRequest(getMainTableName())
                .setWhereClause(getResourceIdsWhereClause(medicalResourceIds))
                .setJoinClause(getReadRequestJoin());
    }

    /**
     * Creates {@link SqlJoin} that is a left join from medical_resource_table to
     * medical_resource_indices_table followed by another left join from medical_resource_table to
     * medical_data_source_table.
     */
    @NonNull
    private static SqlJoin getReadRequestJoin() {
        return joinWithMedicalResourceIndicesTable().attachJoin(joinWithMedicalDataSourceTable());
    }

    @NonNull
    private static SqlJoin joinWithMedicalResourceIndicesTable() {
        return new SqlJoin(
                        MEDICAL_RESOURCE_TABLE_NAME,
                        getTableName(),
                        PRIMARY_COLUMN_NAME,
                        MedicalResourceIndicesHelper.getParentColumnReference())
                .setJoinType(SqlJoin.SQL_JOIN_INNER);
    }

    @NonNull
    private static SqlJoin joinWithMedicalDataSourceTable() {
        return new SqlJoin(
                        MEDICAL_RESOURCE_TABLE_NAME,
                        MedicalDataSourceHelper.getMainTableName(),
                        DATA_SOURCE_ID_COLUMN_NAME,
                        PRIMARY_COLUMN_NAME)
                .setJoinType(SQL_JOIN_INNER);
    }

    private static WhereClauses getResourceIdsWhereClause(
            @NonNull List<MedicalResourceId> medicalResourceIds) {
        List<String> hexUuids = medicalResourceIdsToHexUuids(medicalResourceIds);
        return new WhereClauses(AND).addWhereInClauseWithoutQuotes(UUID_COLUMN_NAME, hexUuids);
    }

    @NonNull
    private static List<String> medicalResourceIdsToHexUuids(
            @NonNull List<MedicalResourceId> medicalResourceIds) {
        // TODO(b/351138955): Temp use getFhirResourceTypeInt for converting before updating
        // MedicalResourceId to use the IntDef FhirResourceType directly.
        List<UUID> ids =
                medicalResourceIds.stream()
                        .map(
                                medicalResourceId ->
                                        generateMedicalResourceUUID(
                                                medicalResourceId.getFhirResourceId(),
                                                getFhirResourceTypeInt(
                                                        medicalResourceId.getFhirResourceType()),
                                                medicalResourceId.getDataSourceId()))
                        .toList();
        return StorageUtils.getListOfHexStrings(ids);
    }

    /**
     * Upserts (insert/update) a list of {@link MedicalResource}s created based on the given list of
     * {@link UpsertMedicalResourceInternalRequest}s into the HealthConnect database.
     *
     * @param upsertMedicalResourceInternalRequests a list of {@link
     *     UpsertMedicalResourceInternalRequest}.
     * @return List of {@link MedicalResource}s that were upserted into the database, in the same
     *     order as their associated {@link UpsertMedicalResourceInternalRequest}s.
     */
    public List<MedicalResource> upsertMedicalResources(
            @NonNull
                    List<UpsertMedicalResourceInternalRequest>
                            upsertMedicalResourceInternalRequests)
            throws SQLiteException {
        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Upserting "
                            + upsertMedicalResourceInternalRequests.size()
                            + " "
                            + UpsertMedicalResourceInternalRequest.class.getSimpleName()
                            + "(s).");
        }

        // TODO(b/337018927): Add support for change logs and access logs.
        // TODO(b/350697473): Add cts tests covering upsert journey with data source creation.
        return mTransactionManager.runAsTransaction(
                (TransactionRunnableWithReturn<List<MedicalResource>, RuntimeException>)
                        db ->
                                readDataSourcesAndUpsertMedicalResources(
                                        db, upsertMedicalResourceInternalRequests));
    }

    private List<MedicalResource> readDataSourcesAndUpsertMedicalResources(
            @NonNull SQLiteDatabase db,
            @NonNull
                    List<UpsertMedicalResourceInternalRequest>
                            upsertMedicalResourceInternalRequests) {
        List<String> dataSourceUuids =
                upsertMedicalResourceInternalRequests.stream()
                        .map(UpsertMedicalResourceInternalRequest::getDataSourceId)
                        .toList();
        Map<String, Long> dataSourceUuidToRowId =
                mMedicalDataSourceHelper.getUuidToRowIdMap(db, dataSourceUuids);

        List<UpsertTableRequest> requests =
                createUpsertTableRequests(
                        upsertMedicalResourceInternalRequests, dataSourceUuidToRowId);
        mTransactionManager.insertOrReplaceAll(db, requests);

        List<MedicalResource> upsertedMedicalResources = new ArrayList<>();
        for (UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest :
                upsertMedicalResourceInternalRequests) {
            upsertedMedicalResources.add(
                    buildMedicalResource(upsertMedicalResourceInternalRequest));
        }
        return upsertedMedicalResources;
    }

    @NonNull
    private static List<UpsertTableRequest> createUpsertTableRequests(
            @NonNull
                    List<UpsertMedicalResourceInternalRequest>
                            upsertMedicalResourceInternalRequests,
            @NonNull Map<String, Long> dataSourceUuidToRowId) {
        List<UpsertTableRequest> requests = new ArrayList<>();
        for (UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest :
                upsertMedicalResourceInternalRequests) {
            // TODO(b/347193220): instead of generating a uuid here, set the uuid inside the
            // UpsertMedicalResourceInternalRequest.fromUpsertRequest in the service layer after
            // ag/27893719
            // submitted.
            UUID uuid =
                    StorageUtils.generateMedicalResourceUUID(
                            upsertMedicalResourceInternalRequest.getFhirResourceId(),
                            upsertMedicalResourceInternalRequest.getFhirResourceType(),
                            upsertMedicalResourceInternalRequest.getDataSourceId());
            Long dataSourceRowId =
                    dataSourceUuidToRowId.get(
                            upsertMedicalResourceInternalRequest.getDataSourceId());
            // TODO(b/348406569): make this a HealthConnectException instead otherwise it will get
            // mapped to ERROR_INTERNAL: http://shortn/_oNnq2lzx5E
            if (dataSourceRowId == null) {
                throw new IllegalArgumentException(
                        "Invalid data source id: "
                                + upsertMedicalResourceInternalRequest.getDataSourceId());
            }
            UpsertTableRequest upsertTableRequest =
                    getUpsertTableRequest(
                            uuid, dataSourceRowId, upsertMedicalResourceInternalRequest);

            requests.add(upsertTableRequest);
        }
        return requests;
    }

    /**
     * Creates {@link UpsertTableRequest} for the given {@link
     * UpsertMedicalResourceInternalRequest}.
     */
    @NonNull
    static UpsertTableRequest getUpsertTableRequest(
            @NonNull UUID uuid,
            long dataSourceRowId,
            @NonNull UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest) {
        ContentValues contentValues =
                getContentValues(uuid, dataSourceRowId, upsertMedicalResourceInternalRequest);
        int medicalResourceType =
                getMedicalResourceType(upsertMedicalResourceInternalRequest.getFhirResourceType());
        return new UpsertTableRequest(getMainTableName(), contentValues, UNIQUE_COLUMNS_INFO)
                .setChildTableRequests(List.of(getChildTableUpsertRequests(medicalResourceType)))
                .setChildTablesWithRowsToBeDeletedDuringUpdate(getChildTableColumnPairs());
    }

    @NonNull
    private static List<TableColumnPair> getChildTableColumnPairs() {
        return List.of(
                new TableColumnPair(
                        getTableName(), MedicalResourceIndicesHelper.getParentColumnReference()));
    }

    // TODO(b/337020055): populate the rest of the fields.
    @NonNull
    private static ContentValues getContentValues(
            @NonNull UUID uuid,
            long dataSourceRowId,
            @NonNull UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest) {
        ContentValues resourceContentValues = new ContentValues();
        resourceContentValues.put(UUID_COLUMN_NAME, StorageUtils.convertUUIDToBytes(uuid));
        resourceContentValues.put(DATA_SOURCE_ID_COLUMN_NAME, dataSourceRowId);
        resourceContentValues.put(
                FHIR_DATA_COLUMN_NAME, upsertMedicalResourceInternalRequest.getData());
        resourceContentValues.put(
                FHIR_RESOURCE_TYPE_COLUMN_NAME,
                upsertMedicalResourceInternalRequest.getFhirResourceType());
        resourceContentValues.put(
                FHIR_RESOURCE_ID_COLUMN_NAME,
                upsertMedicalResourceInternalRequest.getFhirResourceId());
        return resourceContentValues;
    }

    /**
     * Creates a {@link MedicalResource} for the given {@code uuid} and {@link
     * UpsertMedicalResourceInternalRequest}.
     */
    private static MedicalResource buildMedicalResource(
            @NonNull UpsertMedicalResourceInternalRequest internalRequest) {
        int fhirResourceType = internalRequest.getFhirResourceType();
        FhirResource fhirResource =
                new FhirResource.Builder(
                                fhirResourceType,
                                internalRequest.getFhirResourceId(),
                                internalRequest.getData())
                        .build();
        return new MedicalResource.Builder(
                        getMedicalResourceType(fhirResourceType),
                        internalRequest.getDataSourceId(),
                        fhirResource)
                .build();
    }

    /**
     * Returns List of {@code MedicalResource}s from the cursor. If the cursor contains more than
     * {@link MAXIMUM_ALLOWED_CURSOR_COUNT} records, it throws {@link IllegalArgumentException}.
     */
    private static List<MedicalResource> getMedicalResources(@NonNull Cursor cursor) {
        if (cursor.getCount() > MAXIMUM_ALLOWED_CURSOR_COUNT) {
            throw new IllegalArgumentException(
                    "Too many resources in the cursor. Max allowed: "
                            + MAXIMUM_ALLOWED_CURSOR_COUNT);
        }
        List<MedicalResource> medicalResources = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                medicalResources.add(getMedicalResource(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return medicalResources;
    }

    /**
     * Deletes a list of {@link MedicalResource}s created based on the given list of {@link
     * MedicalResourceId}s into the HealthConnect database.
     *
     * @param medicalResourceIds list of {@link UpsertMedicalResourceInternalRequest}.
     */
    public void deleteMedicalResourcesByIds(@NonNull List<MedicalResourceId> medicalResourceIds)
            throws SQLiteException {

        mTransactionManager.delete(getDeleteRequest(medicalResourceIds));
    }

    /**
     * Create an SQL string to delete a list of medical records.
     *
     * @param medicalResourceIds the ids to delete
     * @return A {@link DeleteTableRequest} which when executed will delete those ids
     */
    @NonNull
    @VisibleForTesting
    static DeleteTableRequest getDeleteRequest(
            @NonNull List<MedicalResourceId> medicalResourceIds) {
        if (medicalResourceIds.isEmpty()) {
            throw new IllegalArgumentException("Cannot delete without filters");
        }
        List<String> hexUuids = medicalResourceIdsToHexUuids(medicalResourceIds);
        return new DeleteTableRequest(getMainTableName()).setIds(UUID_COLUMN_NAME, hexUuids);
    }

    /**
     * Returns the {@link MedicalResource.MedicalResourceType} integer representation of the given
     * {@code fhirResourceTypeInt}.
     */
    private static int getMedicalResourceType(int fhirResourceTypeInt) {
        // TODO(b/342574702): remove the default value once we have validation and it is more
        // clear what resources should through to the database.
        if (personalHealthRecord()) {
            return initIfNecessaryAndGetFhirResourceToMedicalResourceMap()
                    .getOrDefault(
                            fhirResourceTypeInt, MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN);
        }
        throw new UnsupportedOperationException(
                "this case should never happen because we have a check at the top of the API impl"
                        + " in HealthConnectServiceImpl");
    }

    @NonNull
    private static MedicalResource getMedicalResource(@NonNull Cursor cursor) {
        int fhirResourceTypeInt = getCursorInt(cursor, FHIR_RESOURCE_TYPE_COLUMN_NAME);
        FhirResource fhirResource =
                new FhirResource.Builder(
                                fhirResourceTypeInt,
                                getCursorString(cursor, FHIR_RESOURCE_ID_COLUMN_NAME),
                                getCursorString(cursor, FHIR_DATA_COLUMN_NAME))
                        .build();
        return new MedicalResource.Builder(
                        getCursorInt(cursor, getMedicalResourceTypeColumnName()),
                        getCursorUUID(cursor, getDataSourceUuidColumnName()).toString(),
                        fhirResource)
                .build();
    }

    private static Map<Integer, Integer> initIfNecessaryAndGetFhirResourceToMedicalResourceMap() {
        if (personalHealthRecord()) {
            FHIR_RESOURCE_TYPE_TO_MEDICAL_RESOURCE_TYPE.put(
                    FHIR_RESOURCE_TYPE_IMMUNIZATION,
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
            return FHIR_RESOURCE_TYPE_TO_MEDICAL_RESOURCE_TYPE;
        }
        return new HashMap<>();
    }
}
