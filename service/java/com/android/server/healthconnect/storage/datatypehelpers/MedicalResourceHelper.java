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

import static com.android.healthfitness.flags.Flags.personalHealthRecord;
import static com.android.server.healthconnect.storage.HealthConnectDatabase.createTable;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.LAST_MODIFIED_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.generateMedicalResourceUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.MedicalResourceId;
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.internal.datatypes.MedicalResourceInternal;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Helper class for MedicalResource table.
 *
 * @hide
 */
public final class MedicalResourceHelper {
    @VisibleForTesting static final String MEDICAL_RESOURCE_TABLE_NAME = "medical_resource_table";
    @VisibleForTesting static final String FHIR_RESOURCE_TYPE_COLUMN_NAME = "fhir_resource_type";
    @VisibleForTesting static final String FHIR_DATA_COLUMN_NAME = "fhir_data";
    @VisibleForTesting static final String FHIR_VERSION_COLUMN_NAME = "fhir_version";
    @VisibleForTesting static final String DATA_SOURCE_ID_COLUMN_NAME = "data_source_id";
    @VisibleForTesting static final String FHIR_RESOURCE_ID_COLUMN_NAME = "fhir_resource_id";
    private static final List<Pair<String, Integer>> UNIQUE_COLUMNS_INFO =
            List.of(new Pair<>(UUID_COLUMN_NAME, UpsertTableRequest.TYPE_BLOB));
    private static final String FHIR_RESOURCE_TYPE_IMMUNIZATION = "IMMUNIZATION";
    private static final int FHIR_RESOURCE_TYPE_UNKNOWN = 0;
    private static final int FHIR_RESOURCE_TYPE_IMMUNIZATION_INT = 1;
    // This maps the fhir_resource_type string to an integer representation. The integer
    // representation does not necessarily match the MEDICAL_RESOURCE_TYPE.
    // As multiple fhir_resource_type(s) could belong to a single MEDICAL_RESOURCE_TYPE.
    private static final Map<String, Integer> FHIR_RESOURCE_TYPE_TO_INT = new HashMap<>();

    static {
        FHIR_RESOURCE_TYPE_TO_INT.put(
                FHIR_RESOURCE_TYPE_IMMUNIZATION, FHIR_RESOURCE_TYPE_IMMUNIZATION_INT);
    }

    private static final Map<Integer, Integer> FHIR_RESOURCE_TYPE_TO_MEDICAL_RESOURCE_TYPE =
            new HashMap<>();

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
                Pair.create(DATA_SOURCE_ID_COLUMN_NAME, INTEGER),
                Pair.create(UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL),
                Pair.create(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER));
    }

    // TODO(b/338198993): add unit tests covering getCreateTableRequest.
    @NonNull
    public static CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(MEDICAL_RESOURCE_TABLE_NAME, getColumnInfo());
    }

    /** Creates the medical_resource table. */
    public static void onInitialUpgrade(@NonNull SQLiteDatabase db) {
        createTable(db, getCreateTableRequest());
    }

    // TODO(b/345464102): We need to update this logic to join with indices table once we
    // have that.
    /** Creates {@link ReadTableRequest} for the given {@link MedicalResourceId}s. */
    @NonNull
    public static ReadTableRequest getReadTableRequest(
            @NonNull List<MedicalResourceId> medicalResourceIds) {
        return new ReadTableRequest(getMainTableName())
                .setWhereClause(getReadTableWhereClause(medicalResourceIds));
    }

    private static WhereClauses getReadTableWhereClause(
            @NonNull List<MedicalResourceId> medicalResourceIds) {
        List<UUID> ids =
                medicalResourceIds.stream()
                        .map(
                                medicalResourceId ->
                                        generateMedicalResourceUUID(
                                                medicalResourceId.getFhirResourceId(),
                                                medicalResourceId.getFhirResourceType(),
                                                medicalResourceId.getDataSourceId()))
                        .toList();
        return new WhereClauses(AND)
                .addWhereInClauseWithoutQuotes(
                        UUID_COLUMN_NAME, StorageUtils.getListOfHexStrings(ids));
    }

    /** Creates {@link UpsertTableRequest} for the given {@link MedicalResourceInternal}. */
    @NonNull
    public static UpsertTableRequest getUpsertTableRequest(
            @NonNull UUID uuid, @NonNull MedicalResourceInternal medicalResourceInternal) {
        ContentValues contentValues = getContentValues(uuid, medicalResourceInternal);
        return new UpsertTableRequest(getMainTableName(), contentValues, UNIQUE_COLUMNS_INFO);
    }

    // TODO(b/337020055): populate the rest of the fields.
    @NonNull
    private static ContentValues getContentValues(
            @NonNull UUID uuid, @NonNull MedicalResourceInternal medicalResourceInternal) {
        ContentValues resourceContentValues = new ContentValues();
        resourceContentValues.put(UUID_COLUMN_NAME, StorageUtils.convertUUIDToBytes(uuid));
        resourceContentValues.put(
                DATA_SOURCE_ID_COLUMN_NAME,
                Long.parseLong(medicalResourceInternal.getDataSourceId()));
        resourceContentValues.put(FHIR_DATA_COLUMN_NAME, medicalResourceInternal.getData());
        resourceContentValues.put(
                FHIR_RESOURCE_TYPE_COLUMN_NAME,
                getFhirResourceTypeInt(medicalResourceInternal.getFhirResourceType()));
        resourceContentValues.put(
                FHIR_RESOURCE_ID_COLUMN_NAME, medicalResourceInternal.getFhirResourceId());
        return resourceContentValues;
    }

    /**
     * Creates a {@link MedicalResource} for the given {@code uuid} and {@link
     * MedicalResourceInternal}.
     */
    public static MedicalResource buildMedicalResource(
            @NonNull UUID uuid, @NonNull MedicalResourceInternal medicalResourceInternal) {
        return new MedicalResource.Builder(
                        uuid.toString(),
                        getMedicalResourceType(medicalResourceInternal.getFhirResourceType()),
                        medicalResourceInternal.getDataSourceId(),
                        medicalResourceInternal.getData())
                .build();
    }

    /**
     * Returns List of {@code MedicalResource}s from the cursor. If the cursor contains more than
     * {@link MAXIMUM_ALLOWED_CURSOR_COUNT} records, it throws {@link IllegalArgumentException}.
     */
    public static List<MedicalResource> getMedicalResources(Cursor cursor) {
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
     * Returns the {@link MedicalResource.MedicalResourceType} integer representation of the {@code
     * fhirResourceType}.
     */
    private static int getMedicalResourceType(@NonNull String fhirResourceType) {
        int fhirResourceTypeInt = getFhirResourceTypeInt(fhirResourceType);
        return getMedicalResourceType(fhirResourceTypeInt);
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

    /** Returns the integer representation of the given {@code fhirResourceType}. */
    static int getFhirResourceTypeInt(@NonNull String fhirResourceType) {
        // TODO(b/342574702): remove the default value once we have validation and it is more
        // clear what resources should through to the database.
        return FHIR_RESOURCE_TYPE_TO_INT.getOrDefault(
                fhirResourceType.toUpperCase(Locale.ROOT), FHIR_RESOURCE_TYPE_UNKNOWN);
    }

    private static MedicalResource getMedicalResource(Cursor cursor) {
        int fhirResourceTypeInt = getCursorInt(cursor, FHIR_RESOURCE_TYPE_COLUMN_NAME);
        return new MedicalResource.Builder(
                        getCursorUUID(cursor, UUID_COLUMN_NAME).toString(),
                        getMedicalResourceType(fhirResourceTypeInt),
                        String.valueOf(getCursorLong(cursor, DATA_SOURCE_ID_COLUMN_NAME)),
                        getCursorString(cursor, FHIR_DATA_COLUMN_NAME))
                .build();
    }

    private static Map<Integer, Integer> initIfNecessaryAndGetFhirResourceToMedicalResourceMap() {
        if (personalHealthRecord()) {
            FHIR_RESOURCE_TYPE_TO_MEDICAL_RESOURCE_TYPE.put(
                    FHIR_RESOURCE_TYPE_IMMUNIZATION_INT,
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
            return FHIR_RESOURCE_TYPE_TO_MEDICAL_RESOURCE_TYPE;
        }
        return new HashMap<>();
    }

    private MedicalResourceHelper() {}
}
