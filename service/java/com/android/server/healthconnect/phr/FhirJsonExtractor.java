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

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Helper class to extract fields from the FHIR JSON.
 *
 * @hide
 */
public class FhirJsonExtractor {
    @NonNull private String mFhirJson = "";
    private JSONObject mFhirJsonObj;
    private static final String RESOURCE_TYPE = "resourceType";
    private static final String RESOURCE_ID = "id";

    public FhirJsonExtractor(@NonNull String fhirJson) throws JSONException {
        mFhirJson = fhirJson;
        mFhirJsonObj = new JSONObject(mFhirJson);
    }

    /** Returns the FHIR JSON string. */
    @NonNull
    public String getFhirJson() {
        return mFhirJson;
    }

    /** Returns the FHIR resource type. */
    @NonNull
    public String getFhirResourceType() throws JSONException {
        return mFhirJsonObj.getString(RESOURCE_TYPE);
    }

    /** Returns the FHIR resource id. */
    @NonNull
    public String getFhirResourceId() throws JSONException {
        return mFhirJsonObj.getString(RESOURCE_ID);
    }
}
