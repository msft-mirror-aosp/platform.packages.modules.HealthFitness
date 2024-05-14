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

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.MEDICAL_RESOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.DATA_SOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getHexString;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.health.connect.MedicalIdFilter;
import android.health.connect.aidl.MedicalIdFiltersParcel;
import android.health.connect.internal.datatypes.MedicalResourceInternal;

import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class MedicalResourceHelperTest {
    private MedicalResourceHelper mMedicalResourceHelper;

    @Before
    public void setup() {
        mMedicalResourceHelper = MedicalResourceHelper.getInstance();
    }

    @Test
    public void getUpsertTableRequest_correctResult() {
        MedicalResourceInternal medicalResourceInternal =
                new MedicalResourceInternal().setDataSourceId("nhs/123").setType(1);

        UpsertTableRequest upsertRequest =
                mMedicalResourceHelper.getUpsertTableRequest(medicalResourceInternal);
        ContentValues contentValues = upsertRequest.getContentValues();

        assertThat(upsertRequest.getTable()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(upsertRequest.getUniqueColumnsCount()).isEqualTo(1);
        assertThat(contentValues.size()).isEqualTo(2);
        assertThat(contentValues.get(RESOURCE_TYPE_COLUMN_NAME)).isEqualTo(1);
        assertThat(contentValues.get(DATA_SOURCE_ID_COLUMN_NAME)).isEqualTo("nhs/123");
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
