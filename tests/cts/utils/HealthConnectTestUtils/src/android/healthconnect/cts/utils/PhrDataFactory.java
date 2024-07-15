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

package android.healthconnect.cts.utils;

import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN;
import static android.health.connect.datatypes.FhirVersion.parseFhirVersion;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;

import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.MedicalResourceId;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;

import org.json.JSONException;
import org.json.JSONObject;

public class PhrDataFactory {
    public static final String DATA_SOURCE_ID = "123";
    public static final String DATA_SOURCE_PACKAGE_NAME = "com.example.app";
    public static final String DATA_SOURCE_FHIR_BASE_URI = "https://fhir.com/oauth/api/FHIR/R4/";
    public static final String DATA_SOURCE_DISPLAY_NAME = "Hospital X";
    public static final String DIFFERENT_DATA_SOURCE_ID = "456";
    public static final String DIFFERENT_DATA_SOURCE_PACKAGE_NAME = "com.other.app";
    public static final String DIFFERENT_DATA_SOURCE_BASE_URI =
            "https://fhir.com/oauth/api/FHIR/R5/";
    public static final String DIFFERENT_DATA_SOURCE_DISPLAY_NAME = "Doctor Y";

    public static final String FHIR_DATA_IMMUNIZATION =
            "{\"resourceType\" : \"Immunization\", \"id\" : \"Immunization1\"}";
    public static final String DIFFERENT_FHIR_DATA_IMMUNIZATION =
            "{\"resourceType\" : \"Immunization\", \"id\" : \"Immunization2\"}";
    public static final String DIFFERENT_FHIR_RESOURCE_ID_IMMUNIZATION = "Immunization2";

    public static final String FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS =
            "{\"resourceType\" : \"Immunization\"}";
    public static final String FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS =
            "{\"id\" : \"Immunization1\"}";
    public static final String FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID = "{\"id\" : }";
    public static final String FHIR_RESOURCE_ID_IMMUNIZATION = "Immunization1";

    public static final String FHIR_DATA_ALLERGY =
            "{\"resourceType\" : \"Allergy\", \"id\" : \"Allergy1\"}";
    public static final String FHIR_RESOURCE_TYPE_ALLERGY = "Allergy";
    public static final String FHIR_RESOURCE_ID_ALLERGY = "Allergy1";

    public static final String RESOURCE_TYPE_FIELD_NAME = "resourceType";
    public static final String RESOURCE_ID_FIELD_NAME = "id";
    public static final String FHIR_VERSION_R4 = "4.0.1";
    public static final String FHIR_VERSION_R4B = "4.3.0";
    public static final String PAGE_TOKEN = "111";

    /** Creates and returns a {@link MedicalDataSource.Builder} with default arguments. */
    public static MedicalDataSource.Builder getMedicalDataSourceBuilder() {
        return new MedicalDataSource.Builder(
                DATA_SOURCE_ID,
                DATA_SOURCE_PACKAGE_NAME,
                DATA_SOURCE_FHIR_BASE_URI,
                DATA_SOURCE_DISPLAY_NAME);
    }

    /** Creates and returns a {@link MedicalResource} with default arguments. */
    public static MedicalDataSource getMedicalDataSource() {
        return getMedicalDataSourceBuilder().build();
    }

    /**
     * Creates and returns a {@link CreateMedicalDataSourceRequest.Builder} with default arguments.
     */
    public static CreateMedicalDataSourceRequest.Builder
            getCreateMedicalDataSourceRequestBuilder() {
        return new CreateMedicalDataSourceRequest.Builder(
                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME);
    }

    /** Creates and returns a {@link CreateMedicalDataSourceRequest} with default arguments. */
    public static CreateMedicalDataSourceRequest getCreateMedicalDataSourceRequest() {
        return getCreateMedicalDataSourceRequestBuilder().build();
    }

    /**
     * Creates and returns a {@link FhirResource.Builder} with default arguments.
     *
     * <p>By default, it contains the {@link PhrDataFactory#FHIR_DATA_IMMUNIZATION}.
     */
    public static FhirResource.Builder getFhirResourceBuilder() {
        return new FhirResource.Builder(
                FHIR_RESOURCE_TYPE_IMMUNIZATION,
                FHIR_RESOURCE_ID_IMMUNIZATION,
                FHIR_DATA_IMMUNIZATION);
    }

    /**
     * Creates and returns a {@link FhirResource} with default arguments.
     *
     * <p>By default, it contains the {@link PhrDataFactory#FHIR_DATA_IMMUNIZATION}.
     */
    public static FhirResource getFhirResource() {
        return getFhirResourceBuilder().build();
    }

    /**
     * Creates and returns a {@link FhirResource} with the status field of the {@link
     * PhrDataFactory#FHIR_DATA_IMMUNIZATION} updated.
     */
    public static FhirResource getUpdatedImmunizationFhirResource() throws JSONException {
        return new FhirResource.Builder(
                        FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION,
                        addCompletedStatus(FHIR_DATA_IMMUNIZATION))
                .build();
    }

    /**
     * Creates and returns a {@link FhirResource} with the status field of the {@link
     * PhrDataFactory#FHIR_DATA_ALLERGY} updated.
     */
    public static FhirResource getUpdatedAllergyFhirResource() throws JSONException {
        return new FhirResource.Builder(
                        FHIR_RESOURCE_TYPE_UNKNOWN,
                        FHIR_RESOURCE_ID_ALLERGY,
                        addCompletedStatus(FHIR_DATA_ALLERGY))
                .build();
    }

    /**
     * Creates and returns a {@link FhirResource} with {@link
     * PhrDataFactory#DIFFERENT_FHIR_DATA_IMMUNIZATION} data.
     */
    public static FhirResource getFhirResourceDifferentImmunization() {
        return new FhirResource.Builder(
                        FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        DIFFERENT_FHIR_RESOURCE_ID_IMMUNIZATION,
                        DIFFERENT_FHIR_DATA_IMMUNIZATION)
                .build();
    }

    /**
     * Creates and returns a {@link FhirResource} with Allergy data.
     *
     * <p>{@code FHIR_RESOURCE_TYPE_UNKNOWN} is used here before we create a FHIR resource type for
     * Allergy.
     */
    public static FhirResource getFhirResourceAllergy() {
        return new FhirResource.Builder(
                        FHIR_RESOURCE_TYPE_UNKNOWN, FHIR_RESOURCE_ID_ALLERGY, FHIR_DATA_ALLERGY)
                .build();
    }

    /**
     * Creates and returns a {@link MedicalResource.Builder} with default arguments.
     *
     * <p>By default, it contains the {@link PhrDataFactory#FHIR_DATA_IMMUNIZATION}.
     */
    public static MedicalResource.Builder getMedicalResourceBuilder() {
        return new MedicalResource.Builder(
                MEDICAL_RESOURCE_TYPE_IMMUNIZATION, DATA_SOURCE_ID, getFhirResource());
    }

    /**
     * Creates and returns a {@link MedicalResource} with default arguments.
     *
     * <p>By default, it contains the {@link PhrDataFactory#FHIR_DATA_IMMUNIZATION}.
     */
    public static MedicalResource getMedicalResource() {
        return getMedicalResourceBuilder().build();
    }

    /** Creates and returns a {@link UpsertMedicalResourceRequest} with default arguments. */
    public static UpsertMedicalResourceRequest getUpsertMedicalResourceRequest() {
        return new UpsertMedicalResourceRequest.Builder(
                        DATA_SOURCE_ID, parseFhirVersion(FHIR_VERSION_R4), FHIR_DATA_IMMUNIZATION)
                .build();
    }

    /**
     * Creates and returns a {@link MedicalResourceId} with default arguments.
     *
     * <p>By default, it contains the {@link FhirResource#FHIR_RESOURCE_TYPE_IMMUNIZATION} and
     * {@link PhrDataFactory#FHIR_RESOURCE_ID_IMMUNIZATION}.
     */
    public static MedicalResourceId getMedicalResourceId() {
        return new MedicalResourceId(
                DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_IMMUNIZATION, FHIR_RESOURCE_ID_IMMUNIZATION);
    }

    /** Returns the FHIR resource type field from the given {@code fhirJSON} string. */
    public static String getFhirResourceTypeString(String fhirJSON) throws JSONException {
        return new JSONObject(fhirJSON).getString(RESOURCE_TYPE_FIELD_NAME);
    }

    /** Returns the FHIR resource id field from the given {@code fhirJSON} string. */
    public static String getFhirResourceId(String fhirJSON) throws JSONException {
        return new JSONObject(fhirJSON).getString(RESOURCE_ID_FIELD_NAME);
    }

    /** Returns an updated FHIR JSON string with an added status field. */
    public static String addCompletedStatus(String fhirJSON) throws JSONException {
        JSONObject jsonObj = new JSONObject(fhirJSON);
        jsonObj.put("status", "completed");
        return jsonObj.toString();
    }
}
