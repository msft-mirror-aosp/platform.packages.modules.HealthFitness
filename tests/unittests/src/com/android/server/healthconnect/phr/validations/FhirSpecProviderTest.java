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

import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4B;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.datatypes.FhirVersion;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.server.healthconnect.proto.FhirDataTypeConfig;

import org.junit.Rule;
import org.junit.Test;

public class FhirSpecProviderTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void testConstructor_r4FhirVersion_parsesProtoFileAndSucceeds() {
        new FhirSpecProvider(FHIR_VERSION_R4);
    }

    @Test
    public void testConstructor_r4BFhirVersion_succeeds() {
        new FhirSpecProvider(FHIR_VERSION_R4B);
    }

    @Test
    public void testConstructor_otherFhirVersion_throws() {

        assertThrows(
                IllegalArgumentException.class,
                () -> new FhirSpecProvider(FhirVersion.parseFhirVersion("4.0.0")));
    }

    @Test
    public void testGetFhirDataTypeConfigForResourceType_immunization_returnsConfig() {
        FhirSpecProvider spec = new FhirSpecProvider(FHIR_VERSION_R4);

        FhirDataTypeConfig immunizationConfig =
                spec.getFhirDataTypeConfigForResourceType(FHIR_RESOURCE_TYPE_IMMUNIZATION);

        assertThat(immunizationConfig.getFieldNamesList())
                .containsAtLeast(
                        "id", "resourceType", "status", "vaccineCode", "occurrenceDateTime");
    }
}
