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

import static android.health.connect.datatypes.FhirResource.FhirResourceType;

import static com.android.server.healthconnect.proto.Kind.KIND_PRIMITIVE_TYPE;

import android.health.connect.datatypes.FhirVersion;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.proto.FhirDataTypeConfig;
import com.android.server.healthconnect.proto.FhirFieldConfig;
import com.android.server.healthconnect.proto.MultiTypeFieldConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Performs validation on a FHIR JSON Object, based on the FHIR version R4.
 *
 * @hide
 */
public class FhirResourceValidator {

    private final FhirSpecProvider mFhirSpec;

    public FhirResourceValidator() {
        if (!Flags.phrFhirStructuralValidation()) {
            throw new UnsupportedOperationException("Validating FHIR resources is not supported.");
        }

        // TODO: b/374058373 - When we support R5 or other versions this needs to be updated to
        //  support other fhir versions.
        mFhirSpec = new FhirSpecProvider(FhirVersion.parseFhirVersion("4.0.1"));
    }

    /**
     * Validates the provided {@code fhirJsonObject} against the schema of the provided {@code
     * fhirResourceType}
     *
     * @throws IllegalArgumentException if the resource is invalid.
     */
    // TODO: b/374949383 - Improve this to validate each field content by type
    public void validateFhirResource(
            JSONObject fhirJsonObject, @FhirResourceType int fhirResourceType) {
        FhirDataTypeConfig config =
                mFhirSpec.getFhirDataTypeConfigForResourceType(fhirResourceType);

        Map<String, FhirFieldConfig> fieldToConfig = config.getAllowedFieldNamesToConfigMap();

        validatePresenceOfRequiredFields(
                fhirJsonObject, config.getRequiredFieldsList(), fieldToConfig);

        validateMultiTypeFields(fhirJsonObject, config.getMultiTypeFieldsList(), fieldToConfig);

        validateResourceStructure(fhirJsonObject, fieldToConfig);
    }

    private void validatePresenceOfRequiredFields(
            JSONObject fhirJsonObject,
            List<String> requiredFields,
            Map<String, FhirFieldConfig> fieldToConfig) {
        for (String requiredField : requiredFields) {
            FhirFieldConfig fieldConfig = fieldToConfig.get(requiredField);
            if (fieldConfig == null) {
                throw new IllegalStateException(
                        "Could not find field config for required field " + requiredField);
            }
            boolean fieldIsPrimitiveType = fieldConfig.getKind().equals(KIND_PRIMITIVE_TYPE);

            if (!fieldIsPresent(fhirJsonObject, requiredField, fieldIsPrimitiveType)) {
                throw new IllegalArgumentException("Missing required field " + requiredField);
            }
        }

        // TODO: b/377717422 -  If the field is an array also check that it's not empty.
        // This case does not happen for top level resource field validation, so should be
        // handled as part of implementing complex type validation.

    }

    private void validateMultiTypeFields(
            JSONObject fhirJsonObject,
            List<MultiTypeFieldConfig> multiTypeFieldConfigs,
            Map<String, FhirFieldConfig> fieldToConfig) {
        for (MultiTypeFieldConfig multiTypeFieldConfig : multiTypeFieldConfigs) {
            int presentFieldCount = 0;
            for (String typedField : multiTypeFieldConfig.getTypedFieldNamesList()) {
                FhirFieldConfig fieldConfig = fieldToConfig.get(typedField);
                if (fieldConfig == null) {
                    throw new IllegalStateException(
                            "Could not find field config for field " + typedField);
                }
                boolean fieldIsPrimitiveType = fieldConfig.getKind().equals(KIND_PRIMITIVE_TYPE);

                if (fieldIsPresent(fhirJsonObject, typedField, fieldIsPrimitiveType)) {
                    presentFieldCount++;
                }
            }

            if (multiTypeFieldConfig.getIsRequired() && presentFieldCount == 0) {
                throw new IllegalArgumentException(
                        "Missing required field " + multiTypeFieldConfig.getName());
            }

            if (presentFieldCount > 1) {
                throw new IllegalArgumentException(
                        "Only one type should be set for field " + multiTypeFieldConfig.getName());
            }
        }
    }

    private void validateResourceStructure(
            JSONObject fhirJsonObject, Map<String, FhirFieldConfig> fieldToConfig) {
        Iterator<String> fieldIterator = fhirJsonObject.keys();

        while (fieldIterator.hasNext()) {
            String fieldName = fieldIterator.next();
            boolean fieldStartsWithUnderscore = fieldName.startsWith("_");

            // Strip leading underscore in case the field is a primitive type extension, which will
            // have a leading underscore, e.g. _status, see
            // https://build.fhir.org/json.html#primitive.
            String fieldWithoutLeadingUnderscore =
                    fieldStartsWithUnderscore ? fieldName.substring(1) : fieldName;

            FhirFieldConfig fieldConfig = fieldToConfig.get(fieldWithoutLeadingUnderscore);
            if (fieldConfig == null
                    || (fieldStartsWithUnderscore
                            && !fieldConfig.getKind().equals(KIND_PRIMITIVE_TYPE))) {
                // TODO: b/374953896 - Improve error message to include type and id.
                throw new IllegalArgumentException("Found unexpected field " + fieldName);
            }

            if (Flags.phrFhirBasicComplexTypeValidation()) {
                Object fieldObject;
                try {
                    fieldObject = fhirJsonObject.get(fieldName);
                } catch (JSONException exception) {
                    throw new IllegalStateException(
                            "Expected field to be present in json object: " + fieldName);
                }

                if (fieldConfig.getIsArray()) {
                    validateArrayFieldAndContents(fieldObject, fieldName, fieldConfig);
                } else {
                    validateField(fieldObject, fieldName, fieldConfig);
                }
            }
        }
    }

    private void validateArrayFieldAndContents(
            Object fieldObject, String fieldName, FhirFieldConfig fieldConfig) {
        if (!(fieldObject instanceof JSONArray)) {
            throw new IllegalArgumentException(
                    "Invalid resource structure. Expected array for field: " + fieldName);
        }
        JSONArray jsonArray = (JSONArray) fieldObject;

        boolean isPrimitiveTypeExtension =
                fieldConfig.getKind().equals(KIND_PRIMITIVE_TYPE) && fieldName.startsWith("_");

        for (int i = 0; i < jsonArray.length(); i++) {
            Object objectInArray;
            try {
                objectInArray = jsonArray.get(i);
            } catch (JSONException exception) {
                throw new IllegalStateException(
                        "Expected item to be present in json array at index "
                                + i
                                + " for field "
                                + fieldName);
            }

            if (isPrimitiveTypeExtension && objectInArray.equals(JSONObject.NULL)) {
                // Arrays that contain primitive type extensions are allowed to contain null values
                // (would be parsed to JSONObject.NULL). See
                // https://hl7.org/fhir/R4/json.html#primitive for an explanation.
                continue;
            }

            validateField(objectInArray, fieldName, fieldConfig);
        }
    }

    /**
     * Performs basic field validation according to the field type.
     *
     * <p>Null values are not allowed. Note that the fieldObject should not be an array. For
     * validating arrays use {@link #validateArrayFieldAndContents}.
     */
    private void validateField(Object fieldObject, String fieldName, FhirFieldConfig fieldConfig) {
        switch (fieldConfig.getKind()) {
            case KIND_PRIMITIVE_TYPE:
                // If the field is a primitive type extension, then it will be an object with
                // fields "id" and/or "extension" fields.
                if (fieldName.startsWith("_")) {
                    validateObjectIsJSONObject(fieldObject, fieldName);
                } else {
                    if (fieldObject.equals(JSONObject.NULL)) {
                        throw new IllegalArgumentException(
                                "Found null value in field: " + fieldName);
                    }
                    if (fieldObject instanceof JSONObject) {
                        throw new IllegalArgumentException(
                                "Invalid resource structure. Found json object but expected"
                                        + " primitive type in field: "
                                        + fieldName);
                    }
                    if (fieldObject instanceof JSONArray) {
                        throw new IllegalArgumentException(
                                "Invalid resource structure. Found json array but expected"
                                        + " primitive type in field: "
                                        + fieldName);
                    }
                    // TODO: b/361775172 - Call primitive type validator.
                }
                break;
            case KIND_RESOURCE:
            case KIND_COMPLEX_TYPE:
                validateObjectIsJSONObject(fieldObject, fieldName);
                break;
            default:
                throw new IllegalStateException(
                        "Encountered unexpected type kind: " + fieldConfig.getKind());
        }
    }

    private void validateObjectIsJSONObject(Object fieldObject, String fieldName) {
        if (fieldObject.equals(JSONObject.NULL)) {
            throw new IllegalArgumentException("Found null value in field: " + fieldName);
        }
        if (!(fieldObject instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    "Invalid resource structure. Expected object in field: " + fieldName);
        }
    }

    private boolean fieldIsPresent(
            JSONObject fhirJsonObject, String fieldName, boolean isPrimitiveType) {
        boolean fieldIsPresent = fhirJsonObject.has(fieldName);

        // For primitive type fields, a primitive type extension with leading underscore may be
        // present instead. See https://build.fhir.org/extensibility.html#primitives, which
        // states that "extensions may appear in place of the value of the primitive datatype".
        if (isPrimitiveType) {
            fieldIsPresent = fieldIsPresent || fhirJsonObject.has("_" + fieldName);
        }

        return fieldIsPresent;
    }
}
