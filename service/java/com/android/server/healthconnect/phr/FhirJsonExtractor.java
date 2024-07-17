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

package com.android.server.healthconnect.phr;

import android.annotation.NonNull;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.FhirResource.FhirResourceType;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Helper class to extract fields from the FHIR JSON.
 *
 * @hide
 */
public class FhirJsonExtractor {
    @NonNull private final JSONObject mFhirJsonObj;
    private static final String FHIR_RESOURCE_TYPE_FIELD_NAME = "resourceType";
    private static final String FHIR_RESOURCE_ID_FIELD_NAME = "id";
    private static final String FHIR_RESOURCE_TYPE_IMMUNIZATION_STR = "IMMUNIZATION";
    private static final Map<String, Integer> FHIR_RESOURCE_TYPE_TO_INT = new HashMap<>();

    static {
        FHIR_RESOURCE_TYPE_TO_INT.put(
                FHIR_RESOURCE_TYPE_IMMUNIZATION_STR, FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION);
    }

    public FhirJsonExtractor(@NonNull String fhirJson) throws JSONException {
        mFhirJsonObj = new JSONObject(fhirJson);
    }

    /**
     * Returns the FHIR resource type. This is extracted from the "resourceType" field in {@code
     * mFhirJsonObj}, and mapped into an {@code IntDef} {@link FhirResourceType}.
     */
    @FhirResourceType
    public int getFhirResourceType() throws JSONException {
        return getFhirResourceTypeInt(getFhirResourceTypeString());
    }

    /** Returns the FHIR resource id. */
    @NonNull
    public String getFhirResourceId() throws JSONException {
        return mFhirJsonObj.getString(FHIR_RESOURCE_ID_FIELD_NAME);
    }

    /**
     * Returns the corresponding {@code IntDef} {@link FhirResourceType} from a {@code String}
     * {@code fhirResourceType}.
     */
    public static int getFhirResourceTypeInt(@NonNull String fhirResourceType) {
        // TODO(b/342574702): remove the default value once we have validation and it is more
        // clear what resources should through to the database.
        return FHIR_RESOURCE_TYPE_TO_INT.getOrDefault(
                fhirResourceType.toUpperCase(Locale.ROOT), FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN);
    }

    /**
     * Returns the FHIR resource type as string. This is extracted from the "resourceType" field in
     * {@code mFhirJsonObj}.
     */
    @NonNull
    private String getFhirResourceTypeString() throws JSONException {
        return mFhirJsonObj.getString(FHIR_RESOURCE_TYPE_FIELD_NAME);
    }
}
