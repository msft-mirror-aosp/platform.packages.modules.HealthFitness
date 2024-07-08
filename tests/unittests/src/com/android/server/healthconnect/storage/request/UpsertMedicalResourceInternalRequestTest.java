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

package com.android.server.healthconnect.storage.request;

import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_LONG_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceId;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceType;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.UpsertMedicalResourceRequest;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;

public class UpsertMedicalResourceInternalRequestTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @DisableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testMedicalResourceInternal_convertFromUpsertRequest_flagOff() {
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_LONG_ID, FHIR_DATA_IMMUNIZATION)
                        .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> UpsertMedicalResourceInternalRequest.fromUpsertRequest(request));
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testMedicalResourceInternal_convertFromUpsertRequest_success()
            throws JSONException {
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_LONG_ID, FHIR_DATA_IMMUNIZATION)
                        .build();
        UpsertMedicalResourceInternalRequest expected =
                new UpsertMedicalResourceInternalRequest()
                        .setDataSourceId(DATA_SOURCE_ID)
                        .setFhirResourceType(getFhirResourceType(FHIR_DATA_IMMUNIZATION))
                        .setFhirResourceId(getFhirResourceId(FHIR_DATA_IMMUNIZATION))
                        .setData(FHIR_DATA_IMMUNIZATION);

        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                UpsertMedicalResourceInternalRequest.fromUpsertRequest(request);

        assertThat(upsertMedicalResourceInternalRequest).isEqualTo(expected);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testMedicalResourceInternal_convertFromUpsertRequest_invalidJson() {
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_LONG_ID, "{\"resourceType\" : \"Immunization}")
                        .build();

        assertThrows(
                JSONException.class,
                () -> UpsertMedicalResourceInternalRequest.fromUpsertRequest(request));
    }
}
