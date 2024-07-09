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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.datatypes.FhirResource;

import org.json.JSONException;
import org.junit.Test;

public class FhirJsonExtractorTest {
    @Test
    public void getFhirResourceType_immunization_success() throws JSONException {
        FhirJsonExtractor extractor = new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION);

        assertThat(extractor.getFhirResourceType())
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    public void getFhirResourceType_unknown_success() throws JSONException {
        FhirJsonExtractor extractor = new FhirJsonExtractor(FHIR_DATA_ALLERGY);

        assertThat(extractor.getFhirResourceType())
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    public void getFhirResourceId_success() throws JSONException {
        FhirJsonExtractor extractor = new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION);
        String expected = getFhirResourceId(FHIR_DATA_IMMUNIZATION);

        String result = extractor.getFhirResourceId();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void getFhirResourceId_notExists_fails() throws JSONException {
        FhirJsonExtractor extractor = new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS);

        assertThrows(JSONException.class, extractor::getFhirResourceId);
    }

    @Test
    public void getFhirResourceType_notExists_fails() throws JSONException {
        FhirJsonExtractor extractor =
                new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS);

        assertThrows(JSONException.class, extractor::getFhirResourceType);
    }

    @Test
    public void constructWithInvalidJson_fails() {
        assertThrows(
                JSONException.class,
                () -> new FhirJsonExtractor(FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID));
    }
}
