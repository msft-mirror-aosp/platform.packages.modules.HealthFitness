/*
 * Copyright (C) 2023 The Android Open Source Project
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
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN;
import static android.health.connect.datatypes.FhirResource.FhirResourceType;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MedicalResourceType;
import static android.health.connect.internal.datatypes.utils.FhirResourceTypeStringToIntMapper.getFhirResourceTypeInt;

import android.annotation.NonNull;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.FhirVersion;

import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Performs MedicalResource validation and extractions on an {@link UpsertMedicalResourceRequest}.
 *
 * @hide
 */
public class MedicalResourceValidator {
    private static final FhirVersion R4_FHIR_VERSION = FhirVersion.parseFhirVersion("4.0.1");
    private static final FhirVersion R4B_FHIR_VERSION = FhirVersion.parseFhirVersion("4.3.0");
    private static final List<FhirVersion> SUPPORTED_FHIR_VERSIONS =
            List.of(R4_FHIR_VERSION, R4B_FHIR_VERSION);

    @NonNull private final String mFhirData;
    @NonNull private final FhirVersion mFhirVersion;
    @NonNull private final String mDataSourceId;

    /** Returns a validator for the provided {@link UpsertMedicalResourceRequest}. */
    public MedicalResourceValidator(UpsertMedicalResourceRequest request) {
        mFhirData = request.getData();
        mFhirVersion = request.getFhirVersion();
        mDataSourceId = request.getDataSourceId();
    }

    /**
     * Validates the values provided in the {@link UpsertMedicalResourceRequest}.
     *
     * <p>It performs the following checks
     *
     * <ul>
     *   <li>The extracted FHIR resource id cannot be empty
     *   <li>Fhir version needs to be a supported version
     *   <li>The extracted FHIR resource type needs to be a supported type
     *   <li>The resource needs to map to one of our permission categories
     * </ul>
     *
     * <p>Returns a validated {@link UpsertMedicalResourceInternalRequest}
     *
     * @throws IllegalArgumentException if {@link UpsertMedicalResourceRequest#getData()} is invalid
     *     json, if the id field or resourceType field cannot be found or if any of the above checks
     *     fail.
     */
    @NonNull
    public UpsertMedicalResourceInternalRequest validateAndCreateInternalRequest()
            throws IllegalArgumentException {
        JSONObject parsedFhirJsonObj = parseJsonResource(mFhirData);
        String extractedFhirResourceId = extractResourceId(parsedFhirJsonObj);
        String extractedFhirResourceTypeString =
                extractResourceType(parsedFhirJsonObj, extractedFhirResourceId);

        validateResourceId(extractedFhirResourceId);
        validateFhirVersion(mFhirVersion, extractedFhirResourceId);

        @FhirResourceType
        int fhirResourceTypeInt =
                validateAndGetResourceType(
                        extractedFhirResourceTypeString, extractedFhirResourceId);

        // TODO(b/350010200) Perform structural FHIR validation after FhirResourceValidator is
        // implemented.

        @MedicalResourceType
        int medicalResourceTypeInt =
                calculateMedicalResourceTypeInt(
                        fhirResourceTypeInt,
                        extractedFhirResourceTypeString,
                        extractedFhirResourceId);

        return new UpsertMedicalResourceInternalRequest()
                .setMedicalResourceType(medicalResourceTypeInt)
                .setFhirResourceId(extractedFhirResourceId)
                .setFhirResourceType(fhirResourceTypeInt)
                .setDataSourceId(mDataSourceId)
                .setFhirVersion(mFhirVersion)
                .setData(mFhirData);
    }

    private static JSONObject parseJsonResource(String fhirData) {
        try {
            return new JSONObject(fhirData);
        } catch (JSONException e) {
            throw new IllegalArgumentException("FHIR data is invalid json");
        }
    }

    private static String extractResourceId(JSONObject fhirJsonObj) {
        try {
            return fhirJsonObj.getString("id");
        } catch (JSONException e) {
            throw new IllegalArgumentException("Resource is missing id field");
        }
    }

    private static String extractResourceType(JSONObject fhirJsonObj, String resourceId) {
        try {
            return fhirJsonObj.getString("resourceType");
        } catch (JSONException e) {
            throw new IllegalArgumentException(
                    "Missing resourceType field for resource with id " + resourceId);
        }
    }

    private static void validateResourceId(String resourceId) {
        if (resourceId.isEmpty()) {
            throw new IllegalArgumentException("Resource id cannot be empty");
        }
    }

    private static void validateFhirVersion(FhirVersion fhirVersion, String resourceId) {
        if (!SUPPORTED_FHIR_VERSIONS.contains(fhirVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported FHIR version "
                            + fhirVersion
                            + " for resource with id "
                            + resourceId);
        }
    }

    /**
     * Returns the corresponding {@code IntDef} {@link FhirResourceType} of the fhir resource.
     *
     * @throws IllegalArgumentException if the type is not supported.
     */
    @FhirResourceType
    private static int validateAndGetResourceType(String fhirResourceType, String fhirResourceId) {
        int fhirResourceTypeInt = getFhirResourceTypeInt(fhirResourceType);
        if (fhirResourceTypeInt == FHIR_RESOURCE_TYPE_UNKNOWN) {
            throw new IllegalArgumentException(
                    "Unsupported resource type "
                            + fhirResourceType
                            + " for resource with id "
                            + fhirResourceId);
        }

        return fhirResourceTypeInt;
    }

    /**
     * Returns the corresponding {@code IntDef} {@link MedicalResourceType} of the fhir resource.
     *
     * @throws IllegalArgumentException if the type can not be mapped.
     */
    @MedicalResourceType
    private static int calculateMedicalResourceTypeInt(
            int fhirResourceType, String fhirResourceTypeString, String fhirResourceId) {
        // TODO(b/342574702): add mapping logic for more FHIR resource types and improve error
        // message.
        if (fhirResourceType == FHIR_RESOURCE_TYPE_IMMUNIZATION) {
            return MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
        } else {
            throw new IllegalArgumentException(
                    "Resource with type "
                            + fhirResourceTypeString
                            + " and id "
                            + fhirResourceId
                            + " could not be mapped to a permissions category.");
        }
    }
}
