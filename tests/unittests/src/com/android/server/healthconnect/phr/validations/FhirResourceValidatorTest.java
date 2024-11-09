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

import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;

import static com.android.healthfitness.flags.Flags.FLAG_PHR_FHIR_STRUCTURAL_VALIDATION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

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
    public void testValidateFhirResource_primitiveTypeFieldOnlyExtensionNoValue_succeeds()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
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
                new JSONObject(new ImmunizationBuilder().set("_unknown_field", "test").toJson());

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
}
