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

        if (Flags.phrFhirOneofValidation()) {
            validateMultiTypeFields(fhirJsonObject, config.getMultiTypeFieldsList(), fieldToConfig);
        }

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
            String field = fieldIterator.next();
            boolean fieldStartsWithUnderscore = field.startsWith("_");

            // Strip leading underscore in case the field is a primitive type extension, which will
            // have a leading underscore, e.g. _status, see
            // https://build.fhir.org/json.html#primitive.
            String fieldWithoutLeadingUnderscore =
                    fieldStartsWithUnderscore ? field.substring(1) : field;

            FhirFieldConfig fieldConfig = fieldToConfig.get(fieldWithoutLeadingUnderscore);
            if (fieldConfig == null
                    || (fieldStartsWithUnderscore
                            && !fieldConfig.getKind().equals(KIND_PRIMITIVE_TYPE))) {
                // TODO: b/374953896 - Improve error message to include type and id.
                throw new IllegalArgumentException("Found unexpected field " + field);
            }
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
