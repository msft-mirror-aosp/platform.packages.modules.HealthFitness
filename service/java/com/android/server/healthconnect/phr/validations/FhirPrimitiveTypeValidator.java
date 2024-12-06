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

import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_BOOLEAN;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_CANONICAL;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_CODE;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_DATE;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_DATE_TIME;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_INSTANT;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_INTEGER;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_STRING;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_SYSTEM_STRING;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_TIME;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_URI;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.proto.R4FhirType;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Performs validation on FHIR primitive values.
 *
 * @hide
 */
public class FhirPrimitiveTypeValidator {
    private static final Map<R4FhirType, Pattern> sR4PrimitiveStringTypeToPatternMap =
            new HashMap<>();

    // All regex below are copied from https://hl7.org/fhir/R4/datatypes.html. Please keep the regex
    // patterns below SORTED.
    // TODO(b/361775172): Support all primitive types from the website. The missing ones now are:
    // base64Binary, decimal, integer64, markdown, oid, positiveInt, unsignedInt, url, uuid.
    private static final Pattern CANONICAL_R4_PATTERN = Pattern.compile("\\S*");
    private static final Pattern CODE_R4_PATTERN = Pattern.compile("[^\\s]+(\\s[^\\s]+)*");
    private static final Pattern DATE_R4_PATTERN =
            Pattern.compile(
                    "([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)"
                            + "(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?");
    // With the R4 regex, if a time is specified, a timezone offset must be populated.
    private static final Pattern DATE_TIME_R4_PATTERN =
            Pattern.compile(
                    "([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)"
                            + "(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])"
                            + "(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)"
                            + "(\\.[0-9]+)?(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?");
    private static final Pattern ID_R4_PATTERN = Pattern.compile("[A-Za-z0-9\\-\\.]{1,64}");
    private static final Pattern INSTANT_R4_PATTERN =
            Pattern.compile(
                    "([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)"
                            + "-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])"
                            + "T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)"
                            + "(\\.[0-9]+)?(Z|(\\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))");
    private static final Pattern STRING_R4_PATTERN = Pattern.compile("[ \\r\\n\\t\\S]+");
    private static final Pattern TIME_R4_PATTERN =
            Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\\.[0-9]+)?");
    private static final Pattern URI_R4_PATTERN = Pattern.compile("\\S*");

    static void validate(Object fieldObject, String fieldName, R4FhirType type) {
        if (!Flags.phrFhirPrimitiveTypeValidation()) {
            throw new UnsupportedOperationException(
                    "Validating FHIR primitive types is not supported.");
        }
        if (fieldObject == null) {
            throw new IllegalStateException(
                    "The fieldObject cannot be null in primitive kind field: " + fieldName);
        }
        populateR4PrimitiveStringTypeToPatternMap();
        switch (type) {
            case R4_FHIR_TYPE_BOOLEAN:
                validateBooleanType(fieldObject, fieldName);
                break;
            case R4_FHIR_TYPE_INTEGER:
                validateIntegerType(fieldObject, fieldName);
                break;
            case R4_FHIR_TYPE_CANONICAL:
            case R4_FHIR_TYPE_CODE:
            case R4_FHIR_TYPE_DATE:
            case R4_FHIR_TYPE_DATE_TIME:
            case R4_FHIR_TYPE_SYSTEM_STRING:
            case R4_FHIR_TYPE_INSTANT:
            case R4_FHIR_TYPE_STRING:
            case R4_FHIR_TYPE_TIME:
            case R4_FHIR_TYPE_URI:
                validateStringType(fieldObject, fieldName);
                validateValuePattern(
                        fieldObject.toString(), getR4PrimitiveStringTypePattern(type), fieldName);
                break;
            default:
                throw new IllegalStateException(
                        "Type is not supported. Found unexpected type "
                                + type.name()
                                + " in primitive kind field: "
                                + fieldName);
        }
    }

    private static void validateBooleanType(Object fieldObject, String fieldName) {
        if (!(fieldObject instanceof Boolean)) {
            throw new IllegalArgumentException(
                    "Invalid resource structure. Found non boolean object in field: " + fieldName);
        }
    }

    private static void validateIntegerType(Object fieldObject, String fieldName) {
        if (!(fieldObject instanceof Integer)) {
            throw new IllegalArgumentException(
                    "Invalid resource structure. Found non integer object in field: " + fieldName);
        }
    }

    private static void validateStringType(Object fieldObject, String fieldName) {
        if (!(fieldObject instanceof String)) {
            throw new IllegalArgumentException(
                    "Invalid resource structure. Found non string object in field: " + fieldName);
        }
    }

    private static void validateValuePattern(String value, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Found invalid field value in primitive field: "
                            + fieldName
                            + ". The value found is: "
                            + value);
        }
    }

    private static Pattern getR4PrimitiveStringTypePattern(R4FhirType type) {
        populateR4PrimitiveStringTypeToPatternMap();

        Pattern pattern = sR4PrimitiveStringTypeToPatternMap.get(type);
        if (pattern != null) {
            return pattern;
        }

        throw new IllegalStateException(
                "Could not find the regex pattern for primitive string type " + type.name());
    }

    private static synchronized void populateR4PrimitiveStringTypeToPatternMap() {
        if (!sR4PrimitiveStringTypeToPatternMap.isEmpty()) {
            return;
        }
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_CANONICAL, CANONICAL_R4_PATTERN);
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_CODE, CODE_R4_PATTERN);
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_DATE, DATE_R4_PATTERN);
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_DATE_TIME, DATE_TIME_R4_PATTERN);
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_SYSTEM_STRING, ID_R4_PATTERN);
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_INSTANT, INSTANT_R4_PATTERN);
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_STRING, STRING_R4_PATTERN);
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_TIME, TIME_R4_PATTERN);
        sR4PrimitiveStringTypeToPatternMap.put(R4_FHIR_TYPE_URI, URI_R4_PATTERN);
    }
}
