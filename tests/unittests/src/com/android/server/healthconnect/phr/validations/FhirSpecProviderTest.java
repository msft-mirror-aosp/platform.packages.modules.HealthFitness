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

package com.android.server.healthconnect.phr.validations;

import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_CONDITION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_LOCATION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_MEDICATION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_REQUEST;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_OBSERVATION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_ORGANIZATION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_PATIENT;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER_ROLE;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_PROCEDURE;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_VERSION_R4B;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.datatypes.FhirVersion;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.server.healthconnect.proto.FhirDataTypeConfig;

import org.junit.Rule;
import org.junit.Test;

public class FhirSpecProviderTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void testConstructor_r4FhirVersion_parsesProtoFileAndSucceeds() {
        new FhirSpecProvider(FHIR_VERSION_R4);
    }

    @Test
    public void testConstructor_r4BFhirVersion_succeeds() {
        new FhirSpecProvider(FHIR_VERSION_R4B);
    }

    @Test
    public void testConstructor_otherFhirVersion_throws() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new FhirSpecProvider(FhirVersion.parseFhirVersion("4.0.0")));
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_immunization_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_IMMUNIZATION);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast(
                        "id",
                        "resourceType",
                        "status",
                        "vaccineCode",
                        "occurrenceDateTime",
                        "extension",
                        "modifierExtension");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_allergy_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast(
                        "id",
                        "resourceType",
                        "meta",
                        "clinicalStatus",
                        "criticality",
                        "onsetRange",
                        "contained");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_observation_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_OBSERVATION);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast(
                        "encounter",
                        "valueQuantity",
                        "valueDateTime",
                        "dataAbsentReason",
                        "referenceRange");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_condition_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_CONDITION);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("id", "resourceType", "code", "onsetDateTime", "onsetAge");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_procedure_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_PROCEDURE);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("id", "resourceType", "subject", "performedRange", "note");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_medication_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_MEDICATION);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("manufacturer", "ingredient", "batch");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_medicationRequest_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_MEDICATION_REQUEST);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast(
                        "doNotPerform",
                        "medicationCodeableConcept",
                        "medicationReference",
                        "dosageInstruction",
                        "priorPrescription");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_medicationStatement_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("dateAsserted", "dosage");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_patient_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_PATIENT);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("active", "name", "deceasedBoolean", "photo", "contact");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_practitioner_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_PRACTITIONER);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("name", "telecom", "qualification");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_practitionerRole_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_PRACTITIONER_ROLE);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("practitioner", "healthcareService", "availableTime", "endpoint");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_location_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_LOCATION);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("operationalStatus", "address", "position");
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_organization_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_ORGANIZATION);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast("name", "alias", "contact");
    }
}
