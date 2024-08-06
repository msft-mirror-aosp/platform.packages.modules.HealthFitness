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

package com.android.server.healthconnect.phr;

import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceId;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.MedicalResource;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;

public class FhirJsonExtractorTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void testConstructor_fhirResourceWithoutType_throws() {
        assertThrows(
                JSONException.class,
                () -> new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS));
    }

    @Test
    public void testConstructor_fhirResourceWithoutId_throws() {
        assertThrows(
                JSONException.class,
                () -> new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS));
    }

    @Test
    public void testConstructor_invalidJson_throws() {
        assertThrows(
                JSONException.class,
                () -> new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID));
    }

    @Test
    @DisableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFlagOff() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION));
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void getFhirResourceType_success() throws JSONException {
        FhirJsonExtractor extractor = new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION);

        assertThat(extractor.getFhirResourceType())
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void getMedicalResourceType_immunization_success() throws JSONException {
        FhirJsonExtractor extractor = new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION);

        assertThat(extractor.getMedicalResourceType())
                .isEqualTo(MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void getMedicalResourceType_unknown_success() throws JSONException {
        FhirJsonExtractor extractor = new FhirJsonExtractor(FHIR_DATA_ALLERGY);

        assertThat(extractor.getMedicalResourceType())
                .isEqualTo(MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void getFhirResourceId_success() throws JSONException {
        FhirJsonExtractor extractor = new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION);
        String expectedId = getFhirResourceId(FHIR_DATA_IMMUNIZATION);

        assertThat(extractor.getFhirResourceId()).isEqualTo(expectedId);
    }
}
