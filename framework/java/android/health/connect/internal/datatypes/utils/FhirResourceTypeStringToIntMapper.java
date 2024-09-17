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
package android.health.connect.internal.datatypes.utils;

import static com.android.healthfitness.flags.AconfigFlagHelper.isPersonalHealthRecordEnabled;

import android.annotation.NonNull;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.FhirResource.FhirResourceType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** @hide */
public final class FhirResourceTypeStringToIntMapper {
    private static final Map<String, Integer> sFhirResourceTypeStringToIntMap = new HashMap<>();

    private static final String FHIR_RESOURCE_TYPE_IMMUNIZATION_STR = "IMMUNIZATION";
    private static final String FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE_STR = "ALLERGYINTOLERANCE";
    private static final String FHIR_RESOURCE_TYPE_OBSERVATION_STR = "OBSERVATION";
    private static final String FHIR_RESOURCE_TYPE_CONDITION_STR = "CONDITION";

    /**
     * Returns the corresponding {@code IntDef} {@link FhirResourceType} from a {@code String}
     * {@code fhirResourceType}.
     */
    @FhirResourceType
    public static int getFhirResourceTypeInt(@NonNull String fhirResourceType) {
        if (!isPersonalHealthRecordEnabled()) {
            throw new UnsupportedOperationException("getFhirResourceTypeInt is not supported");
        }

        populateFhirResourceTypeStringToIntMap();

        return sFhirResourceTypeStringToIntMap.getOrDefault(
                fhirResourceType.toUpperCase(Locale.ROOT), FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN);
    }

    @SuppressWarnings("FlaggedApi") // Initial if statement checks flag, but lint can't know that
    private static void populateFhirResourceTypeStringToIntMap() {
        if (!isPersonalHealthRecordEnabled()) {
            throw new UnsupportedOperationException(
                    "populateFhirResourceTypeStringToIntMap is not supported");
        }

        if (!sFhirResourceTypeStringToIntMap.isEmpty()) {
            return;
        }

        sFhirResourceTypeStringToIntMap.put(
                FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE_STR,
                FhirResource.FHIR_RESOURCE_TYPE_ALLERGY_INTOLERANCE);
        sFhirResourceTypeStringToIntMap.put(
                FHIR_RESOURCE_TYPE_IMMUNIZATION_STR, FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION);
        sFhirResourceTypeStringToIntMap.put(
                FHIR_RESOURCE_TYPE_OBSERVATION_STR, FhirResource.FHIR_RESOURCE_TYPE_OBSERVATION);
        sFhirResourceTypeStringToIntMap.put(
                FHIR_RESOURCE_TYPE_CONDITION_STR, FhirResource.FHIR_RESOURCE_TYPE_CONDITION);
    }
}
