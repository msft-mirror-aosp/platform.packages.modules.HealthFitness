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
package android.healthconnect.internal.datatypes.utils;

import static android.health.connect.internal.datatypes.utils.FhirResourceTypeStringToIntMapper.getFhirResourceTypeInt;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.datatypes.FhirResource;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;

public class FhirResourceTypeStringToIntMapperTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @DisableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFlagOff() {
        assertThrows(UnsupportedOperationException.class, () -> getFhirResourceTypeInt(""));
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_immunizationType() {
        assertThat(getFhirResourceTypeInt("immunization"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION);
        assertThat(getFhirResourceTypeInt("Immunization"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION);
        assertThat(getFhirResourceTypeInt("IMMUNIZATION"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_allergyIntoleranceType() {
        assertThat(getFhirResourceTypeInt("allergyintolerance"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);
        assertThat(getFhirResourceTypeInt("AllergyIntolerance"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);
        assertThat(getFhirResourceTypeInt("ALLERGYINTOLERANCE"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_observationType() {
        assertThat(getFhirResourceTypeInt("observation"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_OBSERVATION);
        assertThat(getFhirResourceTypeInt("Observation"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_OBSERVATION);
        assertThat(getFhirResourceTypeInt("OBSERVATION"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_OBSERVATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_conditionType() {
        assertThat(getFhirResourceTypeInt("condition"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_CONDITION);
        assertThat(getFhirResourceTypeInt("Condition"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_CONDITION);
        assertThat(getFhirResourceTypeInt("CONDITION"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_CONDITION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_procedureType() {
        assertThat(getFhirResourceTypeInt("procedure"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PROCEDURE);
        assertThat(getFhirResourceTypeInt("Procedure"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PROCEDURE);
        assertThat(getFhirResourceTypeInt("PROCEDURE"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PROCEDURE);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_medicationType() {
        assertThat(getFhirResourceTypeInt("medication"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION);
        assertThat(getFhirResourceTypeInt("Medication"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION);
        assertThat(getFhirResourceTypeInt("MEDICATION"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_medicationStatementType() {
        assertThat(getFhirResourceTypeInt("medicationstatement"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT);
        assertThat(getFhirResourceTypeInt("MedicationStatement"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT);
        assertThat(getFhirResourceTypeInt("MEDICATIONSTATEMENT"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_medicationRequestType() {
        assertThat(getFhirResourceTypeInt("medicationrequest"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_REQUEST);
        assertThat(getFhirResourceTypeInt("MedicationRequest"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_REQUEST);
        assertThat(getFhirResourceTypeInt("MEDICATIONREQUEST"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_MEDICATION_REQUEST);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_patientType() {
        assertThat(getFhirResourceTypeInt("patient"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PATIENT);
        assertThat(getFhirResourceTypeInt("Patient"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PATIENT);
        assertThat(getFhirResourceTypeInt("PATIENT"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PATIENT);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_practitionerType() {
        assertThat(getFhirResourceTypeInt("practitioner"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER);
        assertThat(getFhirResourceTypeInt("Practitioner"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER);
        assertThat(getFhirResourceTypeInt("PRACTITIONER"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_practitionerRoleType() {
        assertThat(getFhirResourceTypeInt("practitionerRole"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER_ROLE);
        assertThat(getFhirResourceTypeInt("PractitionerRole"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER_ROLE);
        assertThat(getFhirResourceTypeInt("PRACTITIONERROLE"))
                .isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_PRACTITIONER_ROLE);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testFhirResourceTypeInt_unknownType() {
        int fhirResourceTypeInt = getFhirResourceTypeInt("researchstudy");

        assertThat(fhirResourceTypeInt).isEqualTo(FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN);
    }
}
