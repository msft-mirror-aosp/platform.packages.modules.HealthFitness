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

import static com.android.healthfitness.flags.Flags.FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION;
import static com.android.healthfitness.flags.Flags.FLAG_PHR_FHIR_STRUCTURAL_VALIDATION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.healthconnect.cts.phr.utils.AllergyBuilder;
import android.healthconnect.cts.phr.utils.ImmunizationBuilder;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.json.JSONArray;
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

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeFieldContainsNull_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is of primitive type "code"
        JSONObject immunizationJson =
                new JSONObject(new ImmunizationBuilder().set("status", JSONObject.NULL).toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Found null value in field: status");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeExtensionFieldContainsNull_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is of primitive type "code", and the "_status" field can contain a
        // primitive type extension
        JSONObject immunizationJson =
                new JSONObject(new ImmunizationBuilder().set("_status", JSONObject.NULL).toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Found null value in field: _status");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_complexTypeFieldContainsNull_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "statusReason" field is of complex type "CodeableConcept"
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder().set("statusReason", JSONObject.NULL).toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown).hasMessageThat().contains("Found null value in field: statusReason");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_fieldContainsNullSetViaJsonString_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // This test tests that a "null" value in a json string is parsed to JSONObject.NULL as
        // expected and therefore throws the expected error.
        JSONObject allergyJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"AllergyIntolerance\","
                                + "  \"id\": \"allergyintolerance-1\","
                                + "  \"type\": null,"
                                + "  \"patient\": {"
                                + "    \"reference\": \"Patient/patient-1\","
                                + "    \"display\": \"Example, Anne\""
                                + "  }"
                                + "}");

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE));
        assertThat(thrown).hasMessageThat().contains("Found null value in field: type");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeArrayFieldIsNull_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "category" field is an array of primitive type "code"
        JSONObject allergyJson =
                new JSONObject(new AllergyBuilder().set("category", JSONObject.NULL).toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected array for field: category");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeArrayFieldContainsNull_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "category" field is an array of primitive type "code"
        JSONObject allergyJson =
                new JSONObject(
                        new AllergyBuilder()
                                .set("category", new JSONArray("[\"value\", null, null]"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE));
        assertThat(thrown).hasMessageThat().contains("Found null value in field: category");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeExtensionArrayFieldContainsNull_succeeds()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "category" field is an array of primitive type "code" and has the primitive type
        // extension "_category". Primitive type extension arrays are allowed to contain null values
        // as they act as placeholders if there is no extension for an item in the value array.
        JSONObject allergyJson =
                new JSONObject(
                        new AllergyBuilder()
                                .set("_category", new JSONArray("[null, null]"))
                                .toJson());

        validator.validateFhirResource(allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeExtensionNotJSONObject_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is of primitive type "code", and the "_status" field can contain a
        // primitive type extension, which is expected to be an object.
        JSONObject immunizationJson =
                new JSONObject(new ImmunizationBuilder().set("_status", "simple_string").toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: _status");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeFieldIsJSONObject_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is of primitive type "code", so should be a primitive type (string in
        // this case) and not a json object.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set("status", new JSONObject("{\"id\": \"123\"}"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "Invalid resource structure. Found json object but expected primitive type"
                                + " in field: status");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_primitiveTypeFieldIsJSONArray_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is of primitive type "code", so should be a primitive type (string in
        // this case) and not a json array.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder().set("status", new JSONArray("[]")).toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "Invalid resource structure. Found json array but expected primitive type"
                                + " in field: status");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_complexTypeFieldNotJSONObject_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "statusReason" field is of complex type "CodeableConcept", which is expected to be
        // a json object
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder().set("statusReason", "simple string").toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: statusReason");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_complexTypeFieldIsArrayNotJSONObject_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "statusReason" field is of complex type "CodeableConcept", which is expected to be
        // a json object
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set("statusReason", new JSONArray("[{\"text\": \"test\"}]"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: statusReason");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_arrayFieldIsNotArray_throws() throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "identifier" field is an array of complex type "Identifier"
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set("identifier", new JSONObject("{\"value\": \"123\"}"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected array for field: identifier");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_arrayOfPrimitiveTypeExtensions_succeeds()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "category" field is an array of primitive type "code", which can have a primitive
        // type extension array "_category"
        JSONObject allergyJson =
                new JSONObject(
                        new AllergyBuilder()
                                .set(
                                        "_category",
                                        new JSONArray("[{\"id\": \"123\"}, {\"id\": \"456\"}]"))
                                .toJson());

        validator.validateFhirResource(allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_arrayOfPrimitiveTypeExtensionsIfNotArray_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "status" field is a primitive type field of type "code", but is not an array. So
        // the extension field "_status" is not allowed to be an array.
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set(
                                        "_status",
                                        new JSONArray("[{\"id\": \"123\"}, {\"id\": \"456\"}]"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: _status");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_arrayOfComplexType_succeeds() throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "identifier" field is an array of complex type "Identifier"
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set(
                                        "identifier",
                                        new JSONArray(
                                                "[{\"value\": \"123\"}, {\"value\": \"456\"}]"))
                                .toJson());

        validator.validateFhirResource(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_arrayOfPrimitiveType_succeeds() throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "category" field is an array of primitive type "code",
        JSONObject allergyJson =
                new JSONObject(
                        new AllergyBuilder()
                                .set("category", new JSONArray("[\"food\", \"medication\"]"))
                                .toJson());

        validator.validateFhirResource(allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_arrayOfPrimitiveTypeExtensionsNotJsonObject_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "category" field is an array of primitive type "code", which can have a primitive
        // type extension array "_category" which is expected to be an array of json objects.
        JSONObject allergyJson =
                new JSONObject(
                        new AllergyBuilder()
                                .set("_category", new JSONArray("[\"value1\", \"value2\"]"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: _category");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_arrayOfComplexTypeNotJsonObject_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "identifier" field is an array of complex type "Identifier"
        JSONObject immunizationJson =
                new JSONObject(
                        new ImmunizationBuilder()
                                .set(
                                        "identifier",
                                        new JSONArray("[\"simple_string_1\", \"simple_string_2\"]"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: identifier");
    }

    @EnableFlags({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION, FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidateFhirResource_arrayOfPrimitiveTypesNotPrimitive_throws()
            throws JSONException {
        FhirResourceValidator validator = new FhirResourceValidator();
        // The "category" field is an array of primitive type "code", that should not be of type
        // json object
        JSONObject allergyJson =
                new JSONObject(
                        new AllergyBuilder()
                                .set("category", new JSONArray("[{\"id\": \"123\"}]"))
                                .toJson());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validateFhirResource(
                                        allergyJson, FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE));
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "Invalid resource structure. Found json object but expected primitive type"
                                + " in field: category");
    }
}
