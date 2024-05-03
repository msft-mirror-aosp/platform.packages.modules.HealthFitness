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

package com.android.server.healthconnect.storage.request;

import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getUpsertTransactionRequest;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.internal.datatypes.MedicalResourceInternal;

import org.junit.Test;

import java.util.List;

public class UpsertTransactionRequestTest {
    @Test
    public void testUpsertTransaction_correctUpsertRequests() {
        MedicalResourceInternal medicalResourceInternal1 =
                new MedicalResourceInternal().setId("1234").setDataSourceId("nhs/123").setType(1);
        MedicalResourceInternal medicalResourceInternal2 =
                new MedicalResourceInternal().setId("5678").setDataSourceId("nhs/567").setType(2);

        UpsertTransactionRequest request =
                getUpsertTransactionRequest(
                        List.of(medicalResourceInternal1, medicalResourceInternal2));

        assertThat(request.getUpsertRequests()).hasSize(2);
    }
}
