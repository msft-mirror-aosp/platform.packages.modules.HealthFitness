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
import static android.health.connect.datatypes.FhirResource.validateFhirResourceType;

import android.health.connect.datatypes.FhirVersion;
import android.util.ArrayMap;

import com.android.server.healthconnect.proto.FhirDataTypeConfig;
import com.android.server.healthconnect.proto.FhirResourceSpec;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Contains the parsed Fhir resource spec, which can be used to validate FHIR resources against the
 * spec.
 *
 * @hide
 */
public class FhirSpecProvider {
    private static final String R4_FHIR_SPEC_FILE_NAME = "fhirspec-r4.binarypb";

    private Map<Integer, FhirDataTypeConfig> mResourceTypeIntToFhirSpecMap = new ArrayMap<>();

    /**
     * Parses the {@link FhirResourceSpec} proto file for the provided {@link FhirVersion} *
     *
     * @throws IllegalArgumentException if the file cannot be read or if the provided {@code
     *     fhirVersion} is not supported.
     * @hide
     */
    public FhirSpecProvider(FhirVersion fhirVersion) {
        if (!fhirVersion.isSupportedFhirVersion() || fhirVersion.getMajor() != 4) {
            throw new IllegalArgumentException("Fhir version not supported in validator.");
        }

        FhirResourceSpec r4FhirResourceSpec;
        try (InputStream stream =
                this.getClass().getClassLoader().getResourceAsStream(R4_FHIR_SPEC_FILE_NAME)) {
            if (stream == null) {
                throw new IllegalStateException(R4_FHIR_SPEC_FILE_NAME + " not found.");
            }
            r4FhirResourceSpec = FhirResourceSpec.parseFrom(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse file");
        }
        Map<Integer, FhirDataTypeConfig> resourceTypeToConfig =
                r4FhirResourceSpec.getResourceTypeToConfigMap();
        resourceTypeToConfig.forEach(
                (resourceType, config) -> {
                    validateFhirResourceType(resourceType);
                    mResourceTypeIntToFhirSpecMap.put(resourceType, config);
                });
    }

    /**
     * Returns the {@link FhirDataTypeConfig} for the provided {@code resourceType}
     *
     * @throws IllegalArgumentException if no config exists for the specified type.
     */
    public FhirDataTypeConfig getFhirDataTypeConfigForResourceType(
            @FhirResourceType int resourceType) {
        validateFhirResourceType(resourceType);

        FhirDataTypeConfig config = mResourceTypeIntToFhirSpecMap.get(resourceType);
        if (config == null) {
            throw new IllegalArgumentException(
                    "Could not find config for resource type " + resourceType);
        }
        return config;
    }
}
