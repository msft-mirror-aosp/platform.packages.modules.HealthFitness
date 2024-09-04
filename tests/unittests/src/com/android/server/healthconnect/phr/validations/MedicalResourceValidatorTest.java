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

package com.android.server.healthconnect.phr.validations;

import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_ID_EMPTY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_UNSUPPORTED_RESOURCE_TYPE;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_ID_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_TYPE_UNSUPPORTED;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4B;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_UNSUPPORTED;
import static android.healthconnect.cts.utils.PhrDataFactory.getUpsertMedicalResourceRequestBuilder;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.MedicalResource;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;

import org.junit.Rule;
import org.junit.Test;

public class MedicalResourceValidatorTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void testValidateAndCreateInternalRequest_validAndR4_populatesInternalRequest() {
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, FHIR_DATA_IMMUNIZATION)
                        .build();
        UpsertMedicalResourceInternalRequest expected =
                new UpsertMedicalResourceInternalRequest()
                        .setDataSourceId(DATA_SOURCE_ID)
                        .setMedicalResourceType(MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setFhirResourceType(FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION)
                        .setFhirResourceId(FHIR_RESOURCE_ID_IMMUNIZATION)
                        .setFhirVersion(FHIR_VERSION_R4)
                        .setData(FHIR_DATA_IMMUNIZATION);

        MedicalResourceValidator validator = new MedicalResourceValidator(upsertRequest);
        UpsertMedicalResourceInternalRequest validatedRequest =
                validator.validateAndCreateInternalRequest();

        assertThat(validatedRequest).isEqualTo(expected);
    }

    @Test
    public void testValidateAndCreateInternalRequest_validAndR4B_populatesInternalRequest() {
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4B, FHIR_DATA_IMMUNIZATION)
                        .build();
        UpsertMedicalResourceInternalRequest expected =
                new UpsertMedicalResourceInternalRequest()
                        .setDataSourceId(DATA_SOURCE_ID)
                        .setMedicalResourceType(MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setFhirResourceType(FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION)
                        .setFhirResourceId(FHIR_RESOURCE_ID_IMMUNIZATION)
                        .setFhirVersion(FHIR_VERSION_R4B)
                        .setData(FHIR_DATA_IMMUNIZATION);

        MedicalResourceValidator validator = new MedicalResourceValidator(upsertRequest);
        UpsertMedicalResourceInternalRequest validatedRequest =
                validator.validateAndCreateInternalRequest();

        assertThat(validatedRequest).isEqualTo(expected);
    }

    @Test
    public void testValidateAndCreateInternalRequest_fhirResourceWithoutType_throws() {
        UpsertMedicalResourceRequest upsertRequest =
                getUpsertMedicalResourceRequestBuilder()
                        .setData(FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS)
                        .build();
        MedicalResourceValidator validator = new MedicalResourceValidator(upsertRequest);

        var thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.validateAndCreateInternalRequest());
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "Missing resourceType field for resource with id "
                                + FHIR_RESOURCE_ID_IMMUNIZATION);
    }

    @Test
    public void testValidateAndCreateInternalRequest_fhirResourceWithoutId_throws() {
        UpsertMedicalResourceRequest upsertRequest =
                getUpsertMedicalResourceRequestBuilder()
                        .setData(FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS)
                        .build();
        MedicalResourceValidator validator = new MedicalResourceValidator(upsertRequest);

        var thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.validateAndCreateInternalRequest());
        assertThat(thrown).hasMessageThat().contains("Resource is missing id field");
    }

    @Test
    public void testValidateAndCreateInternalRequest_invalidJson_throws() {
        UpsertMedicalResourceRequest upsertRequest =
                getUpsertMedicalResourceRequestBuilder()
                        .setData(FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID)
                        .build();
        MedicalResourceValidator validator = new MedicalResourceValidator(upsertRequest);

        var thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.validateAndCreateInternalRequest());
        assertThat(thrown).hasMessageThat().contains("invalid json");
    }

    @Test
    public void testValidateAndCreateInternalRequest_emptyId_throws() {
        UpsertMedicalResourceRequest upsertRequest =
                getUpsertMedicalResourceRequestBuilder()
                        .setData(FHIR_DATA_IMMUNIZATION_ID_EMPTY)
                        .build();
        MedicalResourceValidator validator = new MedicalResourceValidator(upsertRequest);

        var thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.validateAndCreateInternalRequest());
        assertThat(thrown).hasMessageThat().contains("id cannot be empty");
    }

    @Test
    public void testValidateAndCreateInternalRequest_unsupportedFhirVersion_throws() {
        UpsertMedicalResourceRequest upsertRequest =
                getUpsertMedicalResourceRequestBuilder()
                        .setFhirVersion(FHIR_VERSION_UNSUPPORTED)
                        .build();
        MedicalResourceValidator validator = new MedicalResourceValidator(upsertRequest);

        var thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.validateAndCreateInternalRequest());
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "Unsupported FHIR version "
                                + FHIR_VERSION_UNSUPPORTED
                                + " for resource with id "
                                + FHIR_RESOURCE_ID_IMMUNIZATION);
    }

    @Test
    public void testValidateAndCreateInternalRequest_unsupportedResourceType_throws() {
        UpsertMedicalResourceRequest upsertRequest =
                getUpsertMedicalResourceRequestBuilder()
                        .setData(FHIR_DATA_IMMUNIZATION_UNSUPPORTED_RESOURCE_TYPE)
                        .build();
        MedicalResourceValidator validator = new MedicalResourceValidator(upsertRequest);

        var thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> validator.validateAndCreateInternalRequest());
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "Unsupported resource type "
                                + FHIR_RESOURCE_TYPE_UNSUPPORTED
                                + " for resource with id "
                                + FHIR_RESOURCE_ID_IMMUNIZATION);
    }
}
