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

import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_CHILD_TYPE_SKIP_VALIDATION;
import static com.android.server.healthconnect.proto.R4FhirType.R4_FHIR_TYPE_RESOURCE;

import android.annotation.Nullable;
import android.health.connect.datatypes.FhirVersion;
import android.util.ArrayMap;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.proto.FhirComplexTypeConfig;
import com.android.server.healthconnect.proto.FhirDataType;
import com.android.server.healthconnect.proto.FhirResourceSpec;
import com.android.server.healthconnect.proto.R4FhirType;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Contains the parsed Fhir resource spec, which can be used to validate FHIR resources against the
 * spec.
 *
 * @hide
 */
public class FhirSpecProvider {
    private static final String R4_FHIR_SPEC_FILE_NAME = "fhirspec-r4.binarypb";

    // The list of complex types for which no FhirComplexTypeConfig exists in this spec provider.
    // This means that we don't support validation of this type yet, and the type should not be
    // validated.
    private static final Set<R4FhirType> FHIR_COMPLEX_TYPES_WITHOUT_CONFIG =
            Set.of(
                    // TODO: b/376462255 - Implement validation of "Resource" data type when
                    //  supporting contained resources.
                    R4_FHIR_TYPE_RESOURCE,
                    // TODO: b/377704968 - Implement validation of "BackboneElement" and "Element"
                    // types that are used to define child types of Resources and Complex types
                    // respectively.
                    R4_FHIR_TYPE_CHILD_TYPE_SKIP_VALIDATION);

    private Map<Integer, FhirComplexTypeConfig> mResourceTypeIntToFhirSpecMap = new ArrayMap<>();

    private Map<R4FhirType, FhirComplexTypeConfig> mFhirComplexTypeToFhirSpecMap = new ArrayMap<>();

    private Set<R4FhirType> mPrimitiveTypes = new HashSet<>();

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
        Map<Integer, FhirComplexTypeConfig> resourceTypeToConfig =
                r4FhirResourceSpec.getResourceTypeToConfigMap();
        resourceTypeToConfig.forEach(
                (resourceType, config) -> {
                    validateFhirResourceType(resourceType);
                    mResourceTypeIntToFhirSpecMap.put(resourceType, config);
                });

        for (FhirDataType dataTypeConfig : r4FhirResourceSpec.getFhirDataTypeConfigsList()) {
            R4FhirType fhirType = dataTypeConfig.getFhirType();
            switch (dataTypeConfig.getKind()) {
                case KIND_PRIMITIVE_TYPE:
                    mPrimitiveTypes.add(fhirType);
                    break;
                case KIND_COMPLEX_TYPE:
                    if (Flags.phrFhirComplexTypeValidation()) {
                        if (FHIR_COMPLEX_TYPES_WITHOUT_CONFIG.contains(fhirType)) {
                            continue;
                        }
                        if (!dataTypeConfig.hasFhirComplexTypeConfig()) {
                            throw new IllegalArgumentException(
                                    "Missing config for data type: "
                                            + dataTypeConfig.getKind().name());
                        }

                        mFhirComplexTypeToFhirSpecMap.put(
                                fhirType, dataTypeConfig.getFhirComplexTypeConfig());
                    }
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unexpected type kind: " + dataTypeConfig.getKind().name());
            }
        }
    }

    /**
     * Returns the {@link FhirComplexTypeConfig} for the provided {@code resourceType}
     *
     * @throws IllegalArgumentException if no config exists for the specified type.
     */
    public FhirComplexTypeConfig getFhirResourceTypeConfig(@FhirResourceType int resourceType) {
        validateFhirResourceType(resourceType);

        FhirComplexTypeConfig config = mResourceTypeIntToFhirSpecMap.get(resourceType);
        if (config == null) {
            throw new IllegalArgumentException(
                    "Could not find config for resource type " + resourceType);
        }
        return config;
    }

    /**
     * Returns the {@link FhirComplexTypeConfig} for the provided {@link R4FhirType}
     *
     * @return the {@link FhirComplexTypeConfig} for the type or {@code null}, if the type is a
     *     complex type that should not be validated, such as "Resource" or "BackboneElement".
     * @throws IllegalArgumentException if the type needs validation and no config exists for the
     *     specified type, for example if a primitive type is provided.
     */
    public @Nullable FhirComplexTypeConfig getFhirComplexTypeConfig(R4FhirType fhirType) {
        if (FHIR_COMPLEX_TYPES_WITHOUT_CONFIG.contains(fhirType)) {
            return null;
        }
        FhirComplexTypeConfig config = mFhirComplexTypeToFhirSpecMap.get(fhirType);
        if (config == null) {
            throw new IllegalArgumentException(
                    "Could not find config for fhir type " + fhirType.name());
        }
        return config;
    }

    /** Returns the {@code true} if the provided {@code fhirType} is a primitive type. */
    public boolean isPrimitiveType(R4FhirType fhirType) {
        return mPrimitiveTypes.contains(fhirType);

        // TODO(b/377701407) After we extract a complex type config - throw exception if the spec is
        //  not aware of the type.
    }
}
