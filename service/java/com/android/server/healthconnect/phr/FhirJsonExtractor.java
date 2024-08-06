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

import static android.health.connect.internal.datatypes.utils.FhirResourceTypeStringToIntMapper.getFhirResourceTypeInt;

import static com.android.healthfitness.flags.Flags.personalHealthRecord;

import android.annotation.NonNull;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.FhirResource.FhirResourceType;
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.datatypes.MedicalResource.MedicalResourceType;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Helper class to extract fields from the FHIR JSON.
 *
 * @hide
 */
public class FhirJsonExtractor {
    private static final String FHIR_RESOURCE_TYPE_FIELD_NAME = "resourceType";
    private static final String FHIR_RESOURCE_ID_FIELD_NAME = "id";

    private final int mFhirResourceTypeInt;
    private final int mMedicalResourceTypeInt;
    @NonNull private final String mFhirResourceId;

    public FhirJsonExtractor(@NonNull String fhirJson) throws JSONException {
        JSONObject fhirJsonObj = new JSONObject(fhirJson);
        String fhirResourceTypeString = fhirJsonObj.getString(FHIR_RESOURCE_TYPE_FIELD_NAME);
        mFhirResourceId = fhirJsonObj.getString(FHIR_RESOURCE_ID_FIELD_NAME);
        mFhirResourceTypeInt = getFhirResourceTypeInt(fhirResourceTypeString);
        mMedicalResourceTypeInt = calculateMedicalResourceTypeInt();
    }

    /**
     * Returns the FHIR resource type. This is extracted from the "resourceType" field in {@code
     * mFhirJsonObj}, and mapped into an {@code IntDef} {@link FhirResourceType}.
     */
    @FhirResourceType
    public int getFhirResourceType() {
        return mFhirResourceTypeInt;
    }

    /** Returns the {@code IntDef} {@link MedicalResourceType}. */
    @MedicalResourceType
    public int getMedicalResourceType() {
        return mMedicalResourceTypeInt;
    }

    /** Returns the FHIR resource id. */
    @NonNull
    public String getFhirResourceId() {
        return mFhirResourceId;
    }

    /**
     * Returns the corresponding {@code IntDef} {@link MedicalResourceType} of the given {@code
     * mFhirJsonObj}.
     */
    @MedicalResourceType
    private int calculateMedicalResourceTypeInt() {
        if (personalHealthRecord()) {
            if (mFhirResourceTypeInt == FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION) {
                return MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
            }
            // TODO(b/342574702): add mapping logic for more FHIR resources type and remove the
            // default value once we have validation.
            return MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
        }
        throw new UnsupportedOperationException(
                "this case should never happen because we have a check at the top of the API impl"
                        + " in HealthConnectServiceImpl");
    }
}
