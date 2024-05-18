/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_DATA_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.MEDICAL_RESOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.DATA_SOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getHexString;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.health.connect.MedicalIdFilter;
import android.health.connect.aidl.MedicalIdFiltersParcel;
import android.health.connect.internal.datatypes.MedicalResourceInternal;

import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class MedicalResourceHelperTest {
    private static final UUID MEDICAL_RESOURCE_ID =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String DATA_SOURCE_ID = "nhs/123";
    private static final String FHIR_DATA = "{\"resourceType\" : \"Immunization\"}";
    private MedicalResourceHelper mMedicalResourceHelper;

    @Before
    public void setup() {
        mMedicalResourceHelper = MedicalResourceHelper.getInstance();
    }

    @Test
    public void getUpsertTableRequest_correctResult() {
        MedicalResourceInternal medicalResourceInternal =
                new MedicalResourceInternal()
                        .setDataSourceId(DATA_SOURCE_ID)
                        .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                        .setData(FHIR_DATA)
                        .setUuid(MEDICAL_RESOURCE_ID);

        UpsertTableRequest upsertRequest =
                mMedicalResourceHelper.getUpsertTableRequest(medicalResourceInternal);
        ContentValues contentValues = upsertRequest.getContentValues();

        assertThat(upsertRequest.getTable()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(upsertRequest.getUniqueColumnsCount()).isEqualTo(1);
        assertThat(contentValues.size()).isEqualTo(4);
        assertThat(contentValues.get(RESOURCE_TYPE_COLUMN_NAME))
                .isEqualTo(MEDICAL_RESOURCE_TYPE_UNKNOWN);
        assertThat(contentValues.get(DATA_SOURCE_ID_COLUMN_NAME)).isEqualTo(DATA_SOURCE_ID);
        assertThat(contentValues.get(FHIR_DATA_COLUMN_NAME)).isEqualTo(FHIR_DATA);
        assertThat(contentValues.get(UUID_COLUMN_NAME))
                .isEqualTo(StorageUtils.convertUUIDToBytes(MEDICAL_RESOURCE_ID));
    }

    @Test
    public void getReadTableRequest_usingMedicalIdFilter_correctQuery() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        ReadTableRequest readRequest =
                mMedicalResourceHelper.getReadTableRequest(
                        new MedicalIdFiltersParcel(
                                List.of(
                                        MedicalIdFilter.fromId(uuid1.toString()),
                                        MedicalIdFilter.fromId(uuid2.toString()))));
        List<String> hexValues = List.of(getHexString(uuid1), getHexString(uuid2));

        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM medical_resource_table WHERE uuid IN ("
                                + String.join(", ", hexValues)
                                + ")");
    }
}
