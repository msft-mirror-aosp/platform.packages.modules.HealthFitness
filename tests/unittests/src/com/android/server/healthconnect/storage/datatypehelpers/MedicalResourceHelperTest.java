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

import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceId;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceType;

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.DATA_SOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_DATA_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.MEDICAL_RESOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.generateMedicalResourceUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getHexString;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.health.connect.MedicalResourceId;
import android.health.connect.internal.datatypes.MedicalResourceInternal;

import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class MedicalResourceHelperTest {

    @Test
    public void getUpsertTableRequest_correctResult() throws JSONException {
        String fhirResourceId = getFhirResourceId(FHIR_DATA_IMMUNIZATION);
        String fhirResourceType = getFhirResourceType(FHIR_DATA_IMMUNIZATION);
        MedicalResourceInternal medicalResourceInternal =
                new MedicalResourceInternal()
                        .setFhirResourceId(fhirResourceId)
                        .setFhirResourceType(fhirResourceType)
                        .setData(FHIR_DATA_IMMUNIZATION)
                        .setDataSourceId(DATA_SOURCE_ID);
        UUID uuid = generateMedicalResourceUUID(fhirResourceId, fhirResourceType, DATA_SOURCE_ID);

        UpsertTableRequest upsertRequest =
                MedicalResourceHelper.getUpsertTableRequest(uuid, medicalResourceInternal);
        ContentValues contentValues = upsertRequest.getContentValues();

        assertThat(upsertRequest.getTable()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(upsertRequest.getUniqueColumnsCount()).isEqualTo(1);
        assertThat(contentValues.size()).isEqualTo(5);
        assertThat(contentValues.get(FHIR_RESOURCE_TYPE_COLUMN_NAME))
                .isEqualTo(MedicalResourceHelper.getFhirResourceTypeInt(fhirResourceType));
        assertThat(contentValues.get(DATA_SOURCE_ID_COLUMN_NAME))
                .isEqualTo(Long.parseLong(DATA_SOURCE_ID));
        assertThat(contentValues.get(FHIR_DATA_COLUMN_NAME)).isEqualTo(FHIR_DATA_IMMUNIZATION);
        assertThat(contentValues.get(UUID_COLUMN_NAME))
                .isEqualTo(StorageUtils.convertUUIDToBytes(uuid));
    }

    @Test
    public void getReadTableRequest_usingMedicalResourceId_correctQuery() throws JSONException {
        String fhirResourceId1 = getFhirResourceId(FHIR_DATA_IMMUNIZATION);
        String fhirResourceType1 = getFhirResourceType(FHIR_DATA_IMMUNIZATION);
        String fhirResourceId2 = getFhirResourceId(FHIR_DATA_ALLERGY);
        String fhirResourceType2 = getFhirResourceType(FHIR_DATA_ALLERGY);
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(DATA_SOURCE_ID, fhirResourceType1, fhirResourceId1);
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(DIFFERENT_DATA_SOURCE_ID, fhirResourceType2, fhirResourceId2);
        List<MedicalResourceId> medicalResourceIds =
                List.of(medicalResourceId1, medicalResourceId2);
        UUID uuid1 =
                generateMedicalResourceUUID(fhirResourceId1, fhirResourceType1, DATA_SOURCE_ID);
        UUID uuid2 =
                generateMedicalResourceUUID(
                        fhirResourceId2, fhirResourceType2, DIFFERENT_DATA_SOURCE_ID);
        List<String> hexValues = List.of(getHexString(uuid1), getHexString(uuid2));

        ReadTableRequest readRequest =
                MedicalResourceHelper.getReadTableRequest(medicalResourceIds);

        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM medical_resource_table WHERE uuid IN ("
                                + String.join(", ", hexValues)
                                + ")");
    }
}
