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
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;

import static com.android.healthfitness.flags.Flags.FLAG_PHR_FHIR_STRUCTURAL_VALIDATION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.healthconnect.cts.phr.utils.AllergyBuilder;
import android.healthconnect.cts.phr.utils.ImmunizationBuilder;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class FhirResourceValidatorTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testConstructor_succeeds() {
        new FhirResourceValidator();
    }

    @DisableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testConstructor_structuralValidationDisabled_throws() {
        assertThrows(UnsupportedOperationException.class, () -> new FhirResourceValidator());
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_invalidTypeInt_throws() throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        JSONObject immunizationJson = new JSONObject(FHIR_DATA_IMMUNIZATION);

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFhirResource(immunizationJson, 100));
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_validResource_succeeds() throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        JSONObject immunizationJson = new JSONObject(FHIR_DATA_IMMUNIZATION);

        validator.validateFhirResource(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeFieldValueAndExtension_succeeds()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is of type "code" which is a primitive type. Therefore the field
        // itself and the extension field "_status" are allowed.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set("status", "completed")
                                .set("_status", new JSONObject("{\"id\": \"1234\"}"))
                                .toJson());

        validator.validateFhirResource(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_onlyRequiredPrimitiveTypeExtensionField_succeeds()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is a required field of type "code", which is a primitive type.
        // Therefore the field itself and the extension field "_status" are allowed, and only one
        // field is required to be present to fulfill the "required" check.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .removeField("status")
                                .set("_status", new JSONObject("{\"id\": \"1234\"}"))
                                .toJson());

        validator.validateFhirResource(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_onlyRequiredPrimitiveTypeValueField_succeeds()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is a required field of type "code" which is a primitive type.
        // Therefore the field itself and the extension field "_status" are allowed, and only one
        // field is required to be present to fulfill the "required" check.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set("status", "completed")
                                .removeField("_status")
                                .toJson());

        validator.validateFhirResource(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_missingRequiredPrimitiveTypeField_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is a required primitive type field, meaning either the field "status"
        // or the primitive type extension "_status" needs to be present.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .removeField("status")
                                .removeField("_status")
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Missing required field status");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_unknownField_throws() throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        JSONObject immunizationJson =
                new JSONObject(new ImmunizationBuilder().set("unknown_field", "test").toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Found unexpected field unknown_field");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_unknownFieldWithUnderscore_throws() throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set("_unknown_field", new JSONObject("{\"id\": \"123\"}"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Found unexpected field _unknown_field");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_nonPrimitiveFieldWithUnderscore_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "identifier" field is of type "Identifier", which is a complex type and not a
        // primitive type. Since only primitive types can have primitive type extensions (fields
        // starting with "_") this is not a valid field.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set("_identifier", new JSONObject("{\"value\": \"test\"}"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Found unexpected field _identifier");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_missingRequiredField_throws() throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        JSONObject immunizationJson =
                new JSONObject(new ImmunizationBuilder().removeField("vaccineCode").toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Missing required field vaccineCode");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_requiredNonPrimitiveFieldWithUnderscore_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "vaccineCode" field is not a primitive type and therefore cannot have a primitive
        // type extension (field starting with "_"), so this does not fulfill the required check.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .removeField("vaccineCode")
                                .set("_vaccineCode", new JSONObject("{\"text\": \"test\"}"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Missing required field vaccineCode");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_missingRequiredMultiTypeField_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // Immunization has a required type choice field "occurrence[x]" which can be set to string
        // or dateTime data type. Therefore if none of these fields are set we expected a validation
        // error
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .removeField("occurrenceDateTime")
                                .removeField("occurrenceString")
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Missing required field occurrence[x]");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_multipleTypesSetRequiredField_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // Immunization has a required type choice field "occurrence[x]" which can be set to string
        // or dateTime date type. If more than one type field is set we expect a validation error.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set("occurrenceDateTime", "2023")
                                .set("occurrenceString", "last year")
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Only one type should be set for field occurrence[x]");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    @Test
    public void testValidateFhirResource_multipleTypesSetOptionalField_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // Allergy has an optional type choice field "onset[x]" which can be set to string
        // or dateTime date type. If more than on type field is set we expect a validation error.
        JSONObject allergyJson =
                new JSONObject(
                        new AllergyBuilder()
                                .set("onsetDateTime", "2023")
                                .set("onsetString", "last year")
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Only one type should be set for field onset[x]");
    }
}
