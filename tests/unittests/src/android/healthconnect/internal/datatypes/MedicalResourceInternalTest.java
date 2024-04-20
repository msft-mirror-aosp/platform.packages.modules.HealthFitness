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

package android.healthconnect.internal.datatypes;

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.MedicalResource;

import org.junit.Test;

public class MedicalResourceInternalTest {
    @Test
    public void testMedicalResourceBuilder_defaultFields() {
        MedicalResource resource = new MedicalResource.Builder().build();

        assertThat(resource.getType()).isEqualTo(MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    public void testMedicalResourceBuilder_correctFields() {
        MedicalResource resource =
                new MedicalResource.Builder().setType(MEDICAL_RESOURCE_TYPE_UNKNOWN).build();

        assertThat(resource.getType()).isEqualTo(MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    public void testMedicalResourceBuilder_fromExistingInstance() {
        MedicalResource original =
                new MedicalResource.Builder().setType(MEDICAL_RESOURCE_TYPE_UNKNOWN).build();
        MedicalResource resource = new MedicalResource.Builder(original).build();

        assertThat(resource.getType()).isEqualTo(original.getType());
    }
}
