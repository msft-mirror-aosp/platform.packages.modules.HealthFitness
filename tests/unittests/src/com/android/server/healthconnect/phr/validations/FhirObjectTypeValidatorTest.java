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

import static com.android.healthfitness.flags.Flags.FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION;
import static com.android.healthfitness.flags.Flags.FLAG_PHR_FHIR_PRIMITIVE_TYPE_VALIDATION;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_BOOLEAN;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_CODE;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_CODEABLE_CONCEPT;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_DATE_TIME;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_ID;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_IDENTIFIER;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_STRING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.healthconnect.proto.FhirComplexTypeConfig;
import com.android.server.healthconnect.proto.FhirFieldConfig;
import com.android.server.healthconnect.proto.MultiTypeFieldConfig;
import com.android.server.healthconnect.proto.R4FhirType;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class FhirObjectTypeValidatorTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock FhirSpecProvider mFhirSpec;

    @Test
    public void testValidate_byImmunizationResourceType_succeeds() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .addRequiredFields("status")
                                .addMultiTypeFields(
                                        MultiTypeFieldConfig.newBuilder()
                                                .setName("occurrence[x]")
                                                .addAllTypedFieldNames(
                                                        List.of(
                                                                "occurrenceDateTime",
                                                                "occurrenceString"))
                                                .setIsRequired(true))
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "identifier",
                                                        createFhirFieldConfig(
                                                                true, R4_FHIR_TYPE_IDENTIFIER)),
                                                Map.entry(
                                                        "status",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_CODE)),
                                                Map.entry(
                                                        "occurrenceDateTime",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_DATE_TIME)),
                                                Map.entry(
                                                        "occurrenceString",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        when(mFhirSpec.isPrimitiveType(R4_FHIR_TYPE_IDENTIFIER)).thenReturn(false);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"status\": \"completed\","
                                + "  \"occurrenceDateTime\": \"2018-05-21\","
                                + "  \"identifier\": [{"
                                + "    \"use\": \"secondary\","
                                + "    \"value\": \"123\""
                                + "  }]"
                                + "}");

        validator.validate(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    public void testValidate_missingRequiredPrimitiveField_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .addRequiredFields("status")
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "status",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_CODE))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\""
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception).hasMessageThat().contains("Missing required field status");
    }

    @Test
    public void testValidate_missingRequiredComplexTypeField_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .addAllRequiredFields(List.of("status", "vaccineCode"))
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "status",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_CODE)),
                                                Map.entry(
                                                        "vaccineCode",
                                                        createFhirFieldConfig(
                                                                false,
                                                                R4_FHIR_TYPE_CODEABLE_CONCEPT))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        when(mFhirSpec.isPrimitiveType(R4_FHIR_TYPE_CODEABLE_CONCEPT)).thenReturn(false);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"status\": \"completed\""
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception).hasMessageThat().contains("Missing required field vaccineCode");
    }

    @Test
    public void testValidate_primitiveTypeExtensionForRequiredField_succeeds()
            throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .addRequiredFields("status")
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "status",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_CODE))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"_status\": {\"id\": \"123\"}"
                                + "}");

        validator.validate(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    public void testValidate_oneRequiredMultiTypeFieldPresent_succeeds() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .addMultiTypeFields(
                                        MultiTypeFieldConfig.newBuilder()
                                                .setName("occurrence[x]")
                                                .addAllTypedFieldNames(
                                                        List.of(
                                                                "occurrenceDateTime",
                                                                "occurrenceString"))
                                                .setIsRequired(true))
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "occurrenceString",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "occurrenceDateTime",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_DATE_TIME))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"occurrenceString\": \"2024\""
                                + "}");

        validator.validate(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    public void testValidate_requiredMultiTypeFieldMissing_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .addMultiTypeFields(
                                        MultiTypeFieldConfig.newBuilder()
                                                .setName("occurrence[x]")
                                                .addAllTypedFieldNames(
                                                        List.of(
                                                                "occurrenceDateTime",
                                                                "occurrenceString"))
                                                .setIsRequired(true))
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "occurrenceString",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "occurrenceDateTime",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_DATE_TIME))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\""
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception).hasMessageThat().contains("Missing required field occurrence[x]");
    }

    @Test
    public void testValidate_moreThanOneMultiTypeFieldPresent_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .addMultiTypeFields(
                                        MultiTypeFieldConfig.newBuilder()
                                                .setName("occurrence[x]")
                                                .addAllTypedFieldNames(
                                                        List.of(
                                                                "occurrenceDateTime",
                                                                "occurrenceString"))
                                                .setIsRequired(true))
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "occurrenceString",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "occurrenceDateTime",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_DATE_TIME))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"occurrenceString\": \"November 2024\","
                                + "  \"occurrenceDateTime\": \"2024-11\""
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception)
                .hasMessageThat()
                .contains("Only one type should be set for field occurrence[x]");
    }

    @Test
    public void testValidate_unknownField_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"unknown_field\": \"test\""
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception).hasMessageThat().contains("Found unexpected field unknown_field");
    }

    @EnableFlags(FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION)
    @Test
    public void testValidate_complexTypeFieldNotJsonObject_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "statusReason",
                                                        createFhirFieldConfig(
                                                                false,
                                                                R4_FHIR_TYPE_CODEABLE_CONCEPT))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        when(mFhirSpec.isPrimitiveType(R4_FHIR_TYPE_CODEABLE_CONCEPT)).thenReturn(false);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"statusReason\": \"simple_string\""
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: statusReason");
    }

    @EnableFlags(FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION)
    @Test
    public void testValidate_complexTypeFieldIsNull_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "statusReason",
                                                        createFhirFieldConfig(
                                                                false,
                                                                R4_FHIR_TYPE_CODEABLE_CONCEPT))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        when(mFhirSpec.isPrimitiveType(R4_FHIR_TYPE_CODEABLE_CONCEPT)).thenReturn(false);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"statusReason\": null"
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception).hasMessageThat().contains("Found null value in field: statusReason");
    }

    @EnableFlags(FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION)
    @Test
    public void testValidate_primitiveTypeExtensionNotJsonObject_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "status",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_CODE))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"_status\": \"completed\""
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: _status");
    }

    @Test
    public void testValidate_arrayField_succeeds() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "identifier",
                                                        createFhirFieldConfig(
                                                                true, R4_FHIR_TYPE_IDENTIFIER))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        when(mFhirSpec.isPrimitiveType(R4_FHIR_TYPE_IDENTIFIER)).thenReturn(false);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"identifier\": [{"
                                + "    \"use\": \"secondary\","
                                + "    \"value\": \"123\""
                                + "  }, {"
                                + "    \"use\": \"secondary\","
                                + "    \"value\": \"456\""
                                + "  }]"
                                + "}");

        validator.validate(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    public void testValidate_primitiveTypeExtensionArrayForArrayField_canContainNull()
            throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "primitiveArrayField",
                                                        createFhirFieldConfig(
                                                                true, R4_FHIR_TYPE_STRING))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"_primitiveArrayField\": [{"
                                + "    \"id\": \"123\""
                                + "  }, null ]"
                                + "}");

        validator.validate(immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    @EnableFlags(FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION)
    @Test
    public void testValidate_arrayFieldIsNotArray_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "identifier",
                                                        createFhirFieldConfig(
                                                                true, R4_FHIR_TYPE_IDENTIFIER))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        when(mFhirSpec.isPrimitiveType(R4_FHIR_TYPE_IDENTIFIER)).thenReturn(false);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"identifier\": {"
                                + "    \"use\": \"secondary\","
                                + "    \"value\": \"123\""
                                + "  }"
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected array for field: identifier");
    }

    @EnableFlags(FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION)
    @Test
    public void testValidate_complexTypeArrayItemNotJsonObject_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "identifier",
                                                        createFhirFieldConfig(
                                                                true, R4_FHIR_TYPE_IDENTIFIER))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        when(mFhirSpec.isPrimitiveType(R4_FHIR_TYPE_IDENTIFIER)).thenReturn(false);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"identifier\": [{"
                                + "    \"use\": \"secondary\","
                                + "    \"value\": \"123\""
                                + "  }, \"simple_string_not_array\"]"
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception)
                .hasMessageThat()
                .contains("Invalid resource structure. Expected object in field: identifier");
    }

    @EnableFlags({FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidate_primitiveTypeFieldIsJsonObject_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "primarySource",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_BOOLEAN))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"primarySource\": {\"id\": \"123\"}"
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Invalid resource structure. Found json object but expected primitive type"
                                + " in field: primarySource");
    }

    @EnableFlags({FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION})
    @Test
    public void testValidate_primitiveTypeFieldIsNull_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "primarySource",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_BOOLEAN))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"primarySource\": null"
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception).hasMessageThat().contains("Found null value in field: primarySource");
    }

    @EnableFlags({
        FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION,
        FLAG_PHR_FHIR_PRIMITIVE_TYPE_VALIDATION
    })
    @Test
    public void testValidate_primitiveTypeFieldIsArray_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "primarySource",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_BOOLEAN))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"primarySource\": [True]"
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Invalid resource structure. Found json array but expected primitive type"
                                + " in field: primarySource");
    }

    @EnableFlags({
        FLAG_PHR_FHIR_BASIC_COMPLEX_TYPE_VALIDATION,
        FLAG_PHR_FHIR_PRIMITIVE_TYPE_VALIDATION
    })
    @Test
    public void testValidate_primitiveTypeFieldIsWrongType_throws() throws JSONException {
        FhirObjectTypeValidator validator = new FhirObjectTypeValidator(mFhirSpec);
        when(mFhirSpec.getFhirResourceTypeConfig(FHIR_RESOURCE_TYPE_IMMUNIZATION))
                .thenReturn(
                        FhirComplexTypeConfig.newBuilder()
                                .putAllAllowedFieldNamesToConfig(
                                        Map.ofEntries(
                                                Map.entry(
                                                        "id",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_ID)),
                                                Map.entry(
                                                        "resourceType",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_STRING)),
                                                Map.entry(
                                                        "primarySource",
                                                        createFhirFieldConfig(
                                                                false, R4_FHIR_TYPE_BOOLEAN))))
                                .build());
        when(mFhirSpec.isPrimitiveType(any())).thenReturn(true);
        JSONObject immunizationJson =
                new JSONObject(
                        "{"
                                + "  \"resourceType\": \"Immunization\","
                                + "  \"id\": \"immunization-1\","
                                + "  \"primarySource\": \"yes\""
                                + "}");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                validator.validate(
                                        immunizationJson, FHIR_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Invalid resource structure. Found non boolean object in field:"
                                + " primarySource");
    }

    private static FhirFieldConfig createFhirFieldConfig(boolean isArray, R4FhirType r4Type) {
        return FhirFieldConfig.newBuilder().setIsArray(isArray).setR4Type(r4Type).build();
    }
}
