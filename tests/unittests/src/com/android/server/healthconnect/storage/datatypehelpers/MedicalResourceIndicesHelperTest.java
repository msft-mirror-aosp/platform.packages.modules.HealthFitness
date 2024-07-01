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

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.MEDICAL_RESOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.MEDICAL_RESOURCE_ID;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.MEDICAL_RESOURCE_INDICES_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.MEDICAL_RESOURCE_TYPE;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getCreateMedicalResourceIndicesTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;

import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;

import com.android.server.healthconnect.storage.request.CreateTableRequest;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class MedicalResourceIndicesHelperTest {
    @Test
    public void getCreateTableRequest_correctResult() {
        List<Pair<String, String>> columnInfo =
                List.of(
                        Pair.create(MEDICAL_RESOURCE_ID, INTEGER_NOT_NULL),
                        Pair.create(MEDICAL_RESOURCE_TYPE, INTEGER_NOT_NULL));
        CreateTableRequest expected =
                new CreateTableRequest(MEDICAL_RESOURCE_INDICES_TABLE_NAME, columnInfo)
                        .addForeignKey(
                                MEDICAL_RESOURCE_TABLE_NAME,
                                Collections.singletonList(MEDICAL_RESOURCE_ID),
                                Collections.singletonList(PRIMARY_COLUMN_NAME));

        CreateTableRequest result = getCreateMedicalResourceIndicesTableRequest();

        assertThat(result).isEqualTo(expected);
    }
}
