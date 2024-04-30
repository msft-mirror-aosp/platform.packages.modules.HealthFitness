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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.android.server.healthconnect.storage.request.CreateTableRequest;

import java.util.List;

/**
 * Helper class for MedicalResource.
 *
 * @hide
 */
public class MedicalResourceHelper {
    private static final String MEDICAL_RESOURCE_TABLE_NAME = "medical_resource_table";
    private static final String RESOURCE_TYPE_COLUMN_NAME = "resource_type";
    private static final String FHIR_DATA_COLUMN_NAME = "fhir_data";
    private static final String FHIR_VERSION_COLUMN_NAME = "fhir_version";
    private static final String DATA_SOURCE_ID_COLUMN_NAME = "data_source_id";
    private static final String FHIR_RESOURCE_ID_COLUMN_NAME = "fhir_resource_id";

    @Nullable private static MedicalResourceHelper sMedicalResourceHelper;

    @NonNull
    public String getMainTableName() {
        return MEDICAL_RESOURCE_TABLE_NAME;
    }

    /** Returns an instance of MedicalResourceHelper class. */
    public static synchronized MedicalResourceHelper getInstance() {
        if (sMedicalResourceHelper == null) {
            sMedicalResourceHelper = new MedicalResourceHelper();
        }
        return sMedicalResourceHelper;
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

    @NonNull
    public CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(MEDICAL_RESOURCE_TABLE_NAME, getColumnInfo());
    }

    /** Creates the Medical Resource related tables. */
    public void onInitialUpgrade(@NonNull SQLiteDatabase db) {
        createTable(db, getCreateTableRequest());
    }
}
