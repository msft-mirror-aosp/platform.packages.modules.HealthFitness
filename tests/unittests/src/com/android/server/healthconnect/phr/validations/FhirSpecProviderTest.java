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

import static com.android.server.healthconnect.proto.Kind.KIND_COMPLEX_TYPE;
import static com.android.server.healthconnect.proto.Kind.KIND_PRIMITIVE_TYPE;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_BOOLEAN;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_CODE;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_COMPLEX;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_DATE;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_DATE_TIME;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_STRING;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_SYSTEM_STRING;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_URI;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.datatypes.FhirVersion;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.healthconnect.proto.FhirDataTypeConfig;
import com.android.server.healthconnect.proto.FhirFieldConfig;
import com.android.server.healthconnect.proto.Kind;
import com.android.server.healthconnect.proto.MultiTypeFieldConfig;
import com.android.server.healthconnect.proto.R4FhirType;

import com.google.common.truth.Correspondence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class FhirSpecProviderTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final Correspondence<MultiTypeFieldConfig, MultiTypeFieldConfig>
            MULTI_TYPE_CONFIG_EQUIVALENCE =
                    Correspondence.from(
                            FhirSpecProviderTest::isMultiTypeFieldConfigEqual,
                            "isMultiTypeFieldConfigEqual");

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
        List<MultiTypeFieldConfig> expectedMultiTypeFieldConfigs =
                List.of(
                        MultiTypeFieldConfig.newBuilder()
                                .setName("occurrence[x]")
                                .addAllTypedFieldNames(
                                        List.of("occurrenceDateTime", "occurrenceString"))
                                .setIsRequired(true)
                                .build());
        Map<String, FhirFieldConfig> expectedFieldConfigsMap =
                Map.ofEntries(
                        Map.entry(
                                "id",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_SYSTEM_STRING, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "resourceType",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_STRING, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "meta",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "implicitRules",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_URI, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "language",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_CODE, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "text",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "contained",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "extension",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "modifierExtension",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "identifier",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "status",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_CODE, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "statusReason",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "vaccineCode",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "patient",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "encounter",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "occurrenceDateTime",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_DATE_TIME, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "occurrenceString",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_STRING, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "recorded",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_DATE_TIME, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "primarySource",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_BOOLEAN, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "reportOrigin",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "location",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "manufacturer",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "lotNumber",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_STRING, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "expirationDate",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_DATE, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "site",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "route",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "doseQuantity",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "performer",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "note",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "reasonCode",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "reasonReference",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "isSubpotent",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_BOOLEAN, KIND_PRIMITIVE_TYPE)),
                        Map.entry(
                                "subpotentReason",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "education",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "programEligibility",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "fundingSource",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "reaction",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)),
                        Map.entry(
                                "protocolApplied",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE)));

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_IMMUNIZATION);

        assertThat(immunizationConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(immunizationConfig.getMultiTypeFieldsList())
                .comparingElementsUsing(MULTI_TYPE_CONFIG_EQUIVALENCE)
                .containsExactlyElementsIn(expectedMultiTypeFieldConfigs);
        assertThat(immunizationConfig.getAllowedFieldNamesToConfigMap())
                .containsExactlyEntriesIn(expectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_allergy_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("patient");
        List<MultiTypeFieldConfig> expectedMultiTypeFieldConfigs =
                List.of(
                        MultiTypeFieldConfig.newBuilder()
                                .setName("onset[x]")
                                .addAllTypedFieldNames(
                                        List.of(
                                                "onsetDateTime",
                                                "onsetAge",
                                                "onsetPeriod",
                                                "onsetRange",
                                                "onsetString"))
                                .setIsRequired(false)
                                .build());
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "id",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_SYSTEM_STRING, KIND_PRIMITIVE_TYPE),
                        "resourceType",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_STRING, KIND_PRIMITIVE_TYPE),
                        "meta",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "clinicalStatus",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "criticality",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_CODE, KIND_PRIMITIVE_TYPE),
                        "onsetRange",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "contained",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

        FhirDataTypeConfig allergyConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);

        assertThat(allergyConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(allergyConfig.getMultiTypeFieldsList())
                .comparingElementsUsing(MULTI_TYPE_CONFIG_EQUIVALENCE)
                .containsExactlyElementsIn(expectedMultiTypeFieldConfigs);
        assertThat(allergyConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_observation_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("status", "code");
        List<MultiTypeFieldConfig> expectedMultiTypeFieldConfigs =
                List.of(
                        MultiTypeFieldConfig.newBuilder()
                                .setName("effective[x]")
                                .addAllTypedFieldNames(
                                        List.of(
                                                "effectiveDateTime",
                                                "effectivePeriod",
                                                "effectiveTiming",
                                                "effectiveInstant"))
                                .setIsRequired(false)
                                .build(),
                        MultiTypeFieldConfig.newBuilder()
                                .setName("value[x]")
                                .addAllTypedFieldNames(
                                        List.of(
                                                "valueQuantity",
                                                "valueCodeableConcept",
                                                "valueString",
                                                "valueBoolean",
                                                "valueInteger",
                                                "valueRange",
                                                "valueRatio",
                                                "valueSampledData",
                                                "valueTime",
                                                "valueDateTime",
                                                "valuePeriod"))
                                .setIsRequired(false)
                                .build());
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "encounter",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "valueQuantity",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "valueDateTime",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_DATE_TIME, KIND_PRIMITIVE_TYPE),
                        "dataAbsentReason",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "referenceRange",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

        FhirDataTypeConfig observationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_OBSERVATION);

        assertThat(observationConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(observationConfig.getMultiTypeFieldsList())
                .comparingElementsUsing(MULTI_TYPE_CONFIG_EQUIVALENCE)
                .containsExactlyElementsIn(expectedMultiTypeFieldConfigs);
        assertThat(observationConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_condition_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);
        List<String> expectedRequiredFields = List.of("subject");
        Map<String, FhirFieldConfig> atLeastExpectedFieldConfigsMap =
                Map.of(
                        "id",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_SYSTEM_STRING, KIND_PRIMITIVE_TYPE),
                        "resourceType",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_STRING, KIND_PRIMITIVE_TYPE),
                        "code",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "onsetDateTime",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_DATE_TIME, KIND_PRIMITIVE_TYPE),
                        "onsetAge",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "id",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_SYSTEM_STRING, KIND_PRIMITIVE_TYPE),
                        "resourceType",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_STRING, KIND_PRIMITIVE_TYPE),
                        "subject",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "performedRange",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "note",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "manufacturer",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "ingredient",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "batch",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "doNotPerform",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_BOOLEAN, KIND_PRIMITIVE_TYPE),
                        "medicationCodeableConcept",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "medicationReference",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "dosageInstruction",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "priorPrescription",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "dateAsserted",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_DATE_TIME, KIND_PRIMITIVE_TYPE),
                        "dosage",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "active",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_BOOLEAN, KIND_PRIMITIVE_TYPE),
                        "name",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "deceasedBoolean",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_BOOLEAN, KIND_PRIMITIVE_TYPE),
                        "photo",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "contact",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "name",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "telecom",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "qualification",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "practitioner",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "healthcareService",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "availableTime",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "endpoint",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "operationalStatus",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "address",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE),
                        "position",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

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
                        "name",
                                createFhirFieldConfig(
                                        false, R4_FHIR_TYPE_STRING, KIND_PRIMITIVE_TYPE),
                        "alias",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_STRING, KIND_PRIMITIVE_TYPE),
                        "contact",
                                createFhirFieldConfig(
                                        true, R4_FHIR_TYPE_COMPLEX, KIND_COMPLEX_TYPE));

        FhirDataTypeConfig organizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_ORGANIZATION);

        assertThat(organizationConfig.getRequiredFieldsList())
                .containsExactlyElementsIn(expectedRequiredFields);
        assertThat(organizationConfig.getAllowedFieldNamesToConfigMap())
                .containsAtLeastEntriesIn(atLeastExpectedFieldConfigsMap);
    }

    private static FhirFieldConfig createFhirFieldConfig(
            boolean isArray, R4FhirType r4Type, Kind kind) {
        return FhirFieldConfig.newBuilder()
                .setIsArray(isArray)
                .setR4Type(r4Type)
                .setKind(kind)
                .build();
    }

    /**
     * Given two {@link MultiTypeFieldConfig}s, compare whether they are equal or not. This ignores
     * the list order when comparing {@link MultiTypeFieldConfig#getTypedFieldNamesList()} ()}.
     */
    private static boolean isMultiTypeFieldConfigEqual(
            MultiTypeFieldConfig actual, MultiTypeFieldConfig expected) {
        List<String> actualFieldNames = actual.getTypedFieldNamesList();
        List<String> expectedFieldNames = expected.getTypedFieldNamesList();
        if (actualFieldNames.size() != expectedFieldNames.size()
                || !actualFieldNames.containsAll(expectedFieldNames)) {
            return false;
        }

        MultiTypeFieldConfig actualWithoutFieldNames =
                actual.toBuilder().clearTypedFieldNames().build();
        MultiTypeFieldConfig expectedWithoutFieldNames =
                expected.toBuilder().clearTypedFieldNames().build();

        return actualWithoutFieldNames.equals(expectedWithoutFieldNames);
    }
}
