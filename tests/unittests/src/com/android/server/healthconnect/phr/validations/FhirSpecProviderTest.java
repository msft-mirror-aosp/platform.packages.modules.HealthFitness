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
import com.android.server.healthconnect.proto.FhirFieldConfig;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;

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
        // Compare the full expected Fhir spec for Immunization. For the other resources we just
        // check a number of fields are as expected instead of the full list.
        // The test is based on the published spec at - https://hl7.org/fhir/R4/immunization.html
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("status", "vaccineCode", "patient");
        Map<String, FhirFieldConfig> expectedFieldConfigsMap =
                Map.ofEntries(
                        Map.entry("id", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("resourceType", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("meta", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("implicitRules", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("language", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("text", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("contained", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("extension", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("modifierExtension", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("identifier", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("status", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("statusReason", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("vaccineCode", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("patient", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("encounter", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("occurrenceDateTime", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("occurrenceString", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("recorded", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("primarySource", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("reportOrigin", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("location", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("manufacturer", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("lotNumber", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("expirationDate", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("site", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("route", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("doseQuantity", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("performer", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("note", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("reasonCode", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("reasonReference", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("isSubpotent", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("subpotentReason", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("education", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("programEligibility", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("fundingSource", createFhirFieldConfigWithIsArray(false)),
                        Map.entry("reaction", createFhirFieldConfigWithIsArray(true)),
                        Map.entry("protocolApplied", createFhirFieldConfigWithIsArray(true)));

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_IMMUNIZATION);

        assertThat(immunizationConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(immunizationConfig.getAllowedFieldNamesToConfigMap())
                .containsExactlyEntriesIn(expectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_allergy_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("patient");
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "id", createFhirFieldConfigWithIsArray(false),
                        "resourceType", createFhirFieldConfigWithIsArray(false),
                        "meta", createFhirFieldConfigWithIsArray(false),
                        "clinicalStatus", createFhirFieldConfigWithIsArray(false),
                        "criticality", createFhirFieldConfigWithIsArray(false),
                        "onsetRange", createFhirFieldConfigWithIsArray(false),
                        "contained", createFhirFieldConfigWithIsArray(true));

        FhirDataTypeConfig allergyConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);

        assertThat(allergyConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(allergyConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_observation_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("status", "code");
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "encounter", createFhirFieldConfigWithIsArray(false),
                        "valueQuantity", createFhirFieldConfigWithIsArray(false),
                        "valueDateTime", createFhirFieldConfigWithIsArray(false),
                        "dataAbsentReason", createFhirFieldConfigWithIsArray(false),
                        "referenceRange", createFhirFieldConfigWithIsArray(true));

        FhirDataTypeConfig observationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_OBSERVATION);

        assertThat(observationConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(observationConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_condition_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("subject");
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "id", createFhirFieldConfigWithIsArray(false),
                        "resourceType", createFhirFieldConfigWithIsArray(false),
                        "code", createFhirFieldConfigWithIsArray(false),
                        "onsetDateTime", createFhirFieldConfigWithIsArray(false),
                        "onsetAge", createFhirFieldConfigWithIsArray(false));

        FhirDataTypeConfig conditionConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_CONDITION);

        assertThat(conditionConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(conditionConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_procedure_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("status", "subject");
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "id", createFhirFieldConfigWithIsArray(false),
                        "resourceType", createFhirFieldConfigWithIsArray(false),
                        "subject", createFhirFieldConfigWithIsArray(false),
                        "performedRange", createFhirFieldConfigWithIsArray(false),
                        "note", createFhirFieldConfigWithIsArray(true));

        FhirDataTypeConfig procedureConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_PROCEDURE);

        assertThat(procedureConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(procedureConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_medication_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of();
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "manufacturer", createFhirFieldConfigWithIsArray(false),
                        "ingredient", createFhirFieldConfigWithIsArray(true),
                        "batch", createFhirFieldConfigWithIsArray(false));

        FhirDataTypeConfig medicationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_MEDICATION);

        assertThat(medicationConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(medicationConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_medicationRequest_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("status", "intent", "subject");
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "doNotPerform", createFhirFieldConfigWithIsArray(false),
                        "medicationCodeableConcept", createFhirFieldConfigWithIsArray(false),
                        "medicationReference", createFhirFieldConfigWithIsArray(false),
                        "dosageInstruction", createFhirFieldConfigWithIsArray(true),
                        "priorPrescription", createFhirFieldConfigWithIsArray(false));

        FhirDataTypeConfig medicationRequestConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_MEDICATION_REQUEST);

        assertThat(medicationRequestConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(medicationRequestConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_medicationStatement_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("status", "subject");
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "dateAsserted", createFhirFieldConfigWithIsArray(false),
                        "dosage", createFhirFieldConfigWithIsArray(true));

        FhirDataTypeConfig medicationStatementConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT);

        assertThat(medicationStatementConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(medicationStatementConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_patient_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of();
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "active", createFhirFieldConfigWithIsArray(false),
                        "name", createFhirFieldConfigWithIsArray(true),
                        "deceasedBoolean", createFhirFieldConfigWithIsArray(false),
                        "photo", createFhirFieldConfigWithIsArray(true),
                        "contact", createFhirFieldConfigWithIsArray(true));

        FhirDataTypeConfig patientConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_PATIENT);

        assertThat(patientConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(patientConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_practitioner_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of();
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "name", createFhirFieldConfigWithIsArray(true),
                        "telecom", createFhirFieldConfigWithIsArray(true),
                        "qualification", createFhirFieldConfigWithIsArray(true));

        FhirDataTypeConfig practitionerConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_PRACTITIONER);

        assertThat(practitionerConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(practitionerConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_practitionerRole_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of();
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "practitioner", createFhirFieldConfigWithIsArray(false),
                        "healthcareService", createFhirFieldConfigWithIsArray(true),
                        "availableTime", createFhirFieldConfigWithIsArray(true),
                        "endpoint", createFhirFieldConfigWithIsArray(true));

        FhirDataTypeConfig practitionerRoleConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_PRACTITIONER_ROLE);

        assertThat(practitionerRoleConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(practitionerRoleConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_location_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of();
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "operationalStatus", createFhirFieldConfigWithIsArray(false),
                        "address", createFhirFieldConfigWithIsArray(false),
                        "position", createFhirFieldConfigWithIsArray(false));

        FhirDataTypeConfig locationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_LOCATION);

        assertThat(locationConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(locationConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_organization_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of();
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "name", createFhirFieldConfigWithIsArray(false),
                        "alias", createFhirFieldConfigWithIsArray(true),
                        "contact", createFhirFieldConfigWithIsArray(true));

        FhirDataTypeConfig organizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_ORGANIZATION);

        assertThat(organizationConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(organizationConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    private static FhirFieldConfig createFhirFieldConfigWithIsArray(boolean isArray) {
        return FhirFieldConfig.newBuilder().setIsArray(isArray).build();
    }
}
