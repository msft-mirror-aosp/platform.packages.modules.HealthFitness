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

import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_CONDITION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_ENCOUNTER;
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
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN;
import static android.health.connect.datatypes.FhirResource.FhirResourceType;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PRACTITIONER_DETAILS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PROCEDURES;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_VISITS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS;
import static android.health.connect.datatypes.MedicalResource.MedicalResourceType;
import static android.health.connect.internal.datatypes.utils.FhirResourceTypeStringToIntMapper.getFhirResourceTypeInt;

import android.annotation.Nullable;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.FhirVersion;

import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    // For the values in these codes see
    // https://build.fhir.org/ig/HL7/fhir-ips/StructureDefinition-Observation-pregnancy-status-uv-ips.html
    private static final Set<String> PREGNANCY_LOINC_CODES =
            Set.of(
                    "82810-3", "11636-8", "11637-6", "11638-4", "11639-2", "11640-0", "11612-9",
                    "11613-7", "11614-5", "33065-4", "11778-8", "11779-6", "11780-4");
    // Defined from IPS Artifacts (Alcohol and Tobacco):
    // https://build.fhir.org/ig/HL7/fhir-ips/artifacts.html
    // https://build.fhir.org/ig/HL7/fhir-ips/StructureDefinition-Observation-alcoholuse-uv-ips.html
    // https://build.fhir.org/ig/HL7/fhir-ips/StructureDefinition-Observation-tobaccouse-uv-ips.html
    private static final Set<String> SOCIAL_HISTORY_LOINC_CODES = Set.of("74013-4", "72166-2");
    // Defined from https://hl7.org/fhir/R5/observation-vitalsigns.html
    private static final Set<String> VITAL_SIGNS_LOINC_CODES =
            Set.of(
                    "85353-1", "9279-1", "8867-4", "2708-6", "8310-5", "8302-2", "9843-4",
                    "29463-7", "39156-5", "85354-9", "8480-6", "8462-4");
    // From http://terminology.hl7.org/CodeSystem/observation-category
    private static final String OBSERVATION_CATEGORY_SOCIAL_HISTORY = "social-history";
    private static final String OBSERVATION_CATEGORY_VITAL_SIGNS = "vital-signs";
    private static final String OBSERVATION_CATEGORY_LABORATORY = "laboratory";

    private final String mFhirData;
    private final FhirVersion mFhirVersion;
    private final String mDataSourceId;

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
                calculateMedicalResourceType(
                        fhirResourceTypeInt,
                        extractedFhirResourceTypeString,
                        extractedFhirResourceId,
                        parsedFhirJsonObj);

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
    private static int calculateMedicalResourceType(
            int fhirResourceType,
            String fhirResourceTypeString,
            String fhirResourceId,
            JSONObject json) {
        // TODO(b/342574702): add mapping logic for more FHIR resource types and improve error
        // message.
        switch (fhirResourceType) {
            case FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE:
                return MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE;
            case FHIR_RESOURCE_TYPE_CONDITION:
                return MEDICAL_RESOURCE_TYPE_CONDITIONS;
            case FHIR_RESOURCE_TYPE_ENCOUNTER,
                    FHIR_RESOURCE_TYPE_LOCATION,
                    FHIR_RESOURCE_TYPE_ORGANIZATION:
                return MEDICAL_RESOURCE_TYPE_VISITS;
            case FHIR_RESOURCE_TYPE_IMMUNIZATION:
                return MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
            case FHIR_RESOURCE_TYPE_OBSERVATION:
                Integer classification = classifyObservation(json);
                if (classification != null) {
                    return classification;
                } else {
                    break;
                }
            case FHIR_RESOURCE_TYPE_PATIENT:
                return MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS;
            case FHIR_RESOURCE_TYPE_PRACTITIONER, FHIR_RESOURCE_TYPE_PRACTITIONER_ROLE:
                return MEDICAL_RESOURCE_TYPE_PRACTITIONER_DETAILS;
            case FHIR_RESOURCE_TYPE_PROCEDURE:
                return MEDICAL_RESOURCE_TYPE_PROCEDURES;
            case FHIR_RESOURCE_TYPE_MEDICATION,
                    FHIR_RESOURCE_TYPE_MEDICATION_REQUEST,
                    FHIR_RESOURCE_TYPE_MEDICATION_STATEMENT:
                return MEDICAL_RESOURCE_TYPE_MEDICATIONS;
            default:
                break;
        }
        throw new IllegalArgumentException(
                "Resource with type "
                        + fhirResourceTypeString
                        + " and id "
                        + fhirResourceId
                        + " could not be mapped to a permissions category.");
    }

    @Nullable
    @MedicalResourceType
    private static Integer classifyObservation(JSONObject json) {
        /*
        The priority order of categories to check is
         - Pregnancy
         - Social History
         - Vital Signs
         - Imaging
         - Labs

         Pregnancy is based on code alone.
         Social History is based on code or category
         Vital signs is based on code or category
         Labs are based on category alone.
         For now we only consider LOINC codes nad default FHIR categories.
         */
        Set<String> loincCodes;
        try {
            JSONObject codeEntry = json.getJSONObject("code");
            loincCodes = getCodesOfType(codeEntry, "http://loinc.org");
        } catch (JSONException ex) {
            loincCodes = Set.of();
        }
        Set<String> categories = new HashSet<>();
        try {
            JSONArray categoryList = json.getJSONArray("category");
            for (int i = 0; i < categoryList.length(); i++) {
                categories.addAll(
                        getCodesOfType(
                                categoryList.getJSONObject(i),
                                "http://terminology.hl7.org/CodeSystem/observation-category"));
            }
        } catch (JSONException ex) {
            // If an error is hit fetching category, assume no categories.
        }
        if (!Collections.disjoint(PREGNANCY_LOINC_CODES, loincCodes)) {
            return MEDICAL_RESOURCE_TYPE_PREGNANCY;
        }
        if (!Collections.disjoint(SOCIAL_HISTORY_LOINC_CODES, loincCodes)
                || categories.contains(OBSERVATION_CATEGORY_SOCIAL_HISTORY)) { //
            return MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY;
        }
        if (!Collections.disjoint(VITAL_SIGNS_LOINC_CODES, loincCodes)
                || categories.contains(OBSERVATION_CATEGORY_VITAL_SIGNS)) { //
            return MEDICAL_RESOURCE_TYPE_VITAL_SIGNS;
        }
        if (categories.contains(OBSERVATION_CATEGORY_LABORATORY)) { //
            return MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS;
        }
        return null;
    }

    private static Set<String> getCodesOfType(JSONObject codeableConcept, String codingSystem) {
        Set<String> codes = new HashSet<>();
        try {
            JSONArray codings = codeableConcept.getJSONArray("coding");
            for (int i = 0; i < codings.length(); i++) {
                JSONObject coding = codings.getJSONObject(i);
                try {
                    String system = coding.getString("system");
                    String code = coding.getString("code");
                    if (codingSystem.equals(system)) {
                        codes.add(code);
                    }
                } catch (JSONException ex) {
                    // On exception, carry on to try the next coding
                }
            }
        } catch (JSONException ex) {
            // Swallow any missing value issue
        }
        return codes;
    }
}
