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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;

import android.health.connect.datatypes.MedicalResource;

public class PhrDataFactory {
    public static final String MEDICAL_RESOURCE_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    public static final String DATA_SOURCE_ID = "nhs/123";
    public static final String FHIR_DATA_IMMUNIZATION = "{\"resourceType\" : \"Immunization\"}";

    public static final String DIFFERENT_MEDICAL_RESOURCE_ID =
            "ffffffff-gggg-hhhh-iiii-jjjjjjjjjjjj";
    public static final String DIFFERENT_DATA_SOURCE_ID = "nhs/456";
    public static final String FHIR_DATA_ALLERGY = "{\"resourceType\" : \"Allergy\"}";

    /**
     * Creates and returns a {@link MedicalResource.Builder} with default arguments.
     *
     * <p>By default, it contains the {@link PhrDataFactory#FHIR_DATA_IMMUNIZATION}.
     */
    public static MedicalResource.Builder getMedicalResourceBuilder() {
        return new MedicalResource.Builder(
                MEDICAL_RESOURCE_ID,
                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                DATA_SOURCE_ID,
                FHIR_DATA_IMMUNIZATION);
    }

    /**
     * Creates and returns a {@link MedicalResource} with default arguments.
     *
     * <p>By default, it contains the {@link PhrDataFactory#FHIR_DATA_IMMUNIZATION}.
     */
    public static MedicalResource getMedicalResource() {
        return getMedicalResourceBuilder().build();
    }
}
