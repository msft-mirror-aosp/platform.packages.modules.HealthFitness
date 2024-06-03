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
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.aidl.MedicalIdFiltersParcel;
import android.health.connect.internal.datatypes.MedicalResourceInternal;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Helper class for MedicalResource.
 *
 * @hide
 */
public class MedicalResourceHelper {
    @VisibleForTesting static final String MEDICAL_RESOURCE_TABLE_NAME = "medical_resource_table";
    @VisibleForTesting static final String RESOURCE_TYPE_COLUMN_NAME = "resource_type";
    @VisibleForTesting static final String FHIR_DATA_COLUMN_NAME = "fhir_data";
    @VisibleForTesting static final String FHIR_VERSION_COLUMN_NAME = "fhir_version";
    @VisibleForTesting static final String DATA_SOURCE_ID_COLUMN_NAME = "data_source_id";
    @VisibleForTesting static final String FHIR_RESOURCE_ID_COLUMN_NAME = "fhir_resource_id";
    private static final List<Pair<String, Integer>> UNIQUE_COLUMNS_INFO =
            List.of(new Pair<>(UUID_COLUMN_NAME, UpsertTableRequest.TYPE_BLOB));

    @NonNull
    public String getMainTableName() {
        return MEDICAL_RESOURCE_TABLE_NAME;
    }

    @NonNull
    final List<Pair<String, String>> getColumnInfo() {
        return List.of(
                Pair.create(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT),
                Pair.create(RESOURCE_TYPE_COLUMN_NAME, INTEGER_NOT_NULL),
                Pair.create(FHIR_RESOURCE_ID_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(FHIR_DATA_COLUMN_NAME, TEXT_NOT_NULL),
                Pair.create(FHIR_VERSION_COLUMN_NAME, TEXT_NULL),
                Pair.create(DATA_SOURCE_ID_COLUMN_NAME, INTEGER),
                Pair.create(UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL),
                Pair.create(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER));
    }

    // TODO(b/338198993): add unit tests covering getCreateTableRequest.
    @NonNull
    public CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(MEDICAL_RESOURCE_TABLE_NAME, getColumnInfo());
    }

    /** Creates the Medical Resource related tables. */
    public void onInitialUpgrade(@NonNull SQLiteDatabase db) {
        createTable(db, getCreateTableRequest());
    }

    /** Creates {@link ReadTableRequest} for the given {@code medicalIdFiltersParcel}. */
    @NonNull
    public ReadTableRequest getReadTableRequest(MedicalIdFiltersParcel medicalIdFiltersParcel) {
        return new ReadTableRequest(getMainTableName())
                .setWhereClause(getReadTableWhereClause(medicalIdFiltersParcel));
    }

    private WhereClauses getReadTableWhereClause(MedicalIdFiltersParcel medicalIdFiltersParcel) {
        List<UUID> ids =
                medicalIdFiltersParcel.getMedicalIdFilters().stream()
                        .map(StorageUtils::getUUIDFor)
                        .toList();
        return new WhereClauses(AND)
                .addWhereInClauseWithoutQuotes(
                        UUID_COLUMN_NAME, StorageUtils.getListOfHexStrings(ids));
    }

    /** Creates {@link UpsertTableRequest} for {@code medicalResourceInternal}. */
    @NonNull
    public UpsertTableRequest getUpsertTableRequest(
            @NonNull MedicalResourceInternal medicalResourceInternal) {
        ContentValues upsertValues = getContentValues(medicalResourceInternal);
        return new UpsertTableRequest(getMainTableName(), upsertValues, UNIQUE_COLUMNS_INFO);
    }

    // TODO(b/337020055): populate the rest of the fields.
    @NonNull
    private ContentValues getContentValues(
            @NonNull MedicalResourceInternal medicalResourceInternal) {
        ContentValues resourceContentValues = new ContentValues();
        resourceContentValues.put(
                UUID_COLUMN_NAME,
                StorageUtils.convertUUIDToBytes(medicalResourceInternal.getUuid()));
        resourceContentValues.put(RESOURCE_TYPE_COLUMN_NAME, medicalResourceInternal.getType());
        resourceContentValues.put(
                DATA_SOURCE_ID_COLUMN_NAME, medicalResourceInternal.getDataSourceId());
        resourceContentValues.put(FHIR_DATA_COLUMN_NAME, medicalResourceInternal.getData());
        return resourceContentValues;
    }

    /**
     * Returns List of {@code MedicalResourceInternal}s from the cursor. If the cursor contains more
     * than {@link MAXIMUM_ALLOWED_CURSOR_COUNT} records, it throws {@link
     * IllegalArgumentException}.
     */
    public List<MedicalResourceInternal> getMedicalResourceInternals(Cursor cursor) {
        if (cursor.getCount() > MAXIMUM_ALLOWED_CURSOR_COUNT) {
            throw new IllegalArgumentException(
                    "Too many resources in the cursor. Max allowed: "
                            + MAXIMUM_ALLOWED_CURSOR_COUNT);
        }
        List<MedicalResourceInternal> medicalResourceInternals = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                medicalResourceInternals.add(getMedicalResource(cursor));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return medicalResourceInternals;
    }

    private static MedicalResourceInternal getMedicalResource(Cursor cursor) {
        MedicalResourceInternal resource = new MedicalResourceInternal();
        resource.setUuid(getCursorUUID(cursor, UUID_COLUMN_NAME));
        resource.setType(getCursorInt(cursor, RESOURCE_TYPE_COLUMN_NAME));
        resource.setDataSourceId(String.valueOf(getCursorInt(cursor, DATA_SOURCE_ID_COLUMN_NAME)));
        resource.setData(getCursorString(cursor, FHIR_DATA_COLUMN_NAME));
        return resource;
    }
}
