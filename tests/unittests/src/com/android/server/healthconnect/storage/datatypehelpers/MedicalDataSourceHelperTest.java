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

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.DISPLAY_NAME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.FHIR_BASE_URI_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.MEDICAL_DATA_SOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.APP_INFO_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;

import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;

import com.android.server.healthconnect.storage.request.CreateTableRequest;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class MedicalDataSourceHelperTest {
    private final MedicalDataSourceHelper mMedicalDataSourceHelper = new MedicalDataSourceHelper();

    @Test
    public void getCreateTableRequest_correctResult() {
        List<Pair<String, String>> columnInfo =
                List.of(
                        Pair.create(PRIMARY_COLUMN_NAME, PRIMARY),
                        Pair.create(APP_INFO_ID_COLUMN_NAME, INTEGER),
                        Pair.create(DISPLAY_NAME_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_BASE_URI_COLUMN_NAME, TEXT_NOT_NULL));
        CreateTableRequest expected =
                new CreateTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME, columnInfo)
                        .addForeignKey(
                                AppInfoHelper.TABLE_NAME,
                                Collections.singletonList(APP_INFO_ID_COLUMN_NAME),
                                Collections.singletonList(PRIMARY_COLUMN_NAME));

        CreateTableRequest result = mMedicalDataSourceHelper.getCreateTableRequest();

        assertThat(result).isEqualTo(expected);
    }
}
