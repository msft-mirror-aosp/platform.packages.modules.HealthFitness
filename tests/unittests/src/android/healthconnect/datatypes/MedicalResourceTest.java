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

package android.healthconnect.datatypes;

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.MedicalResource;

import org.junit.Test;

import java.time.Instant;

public class MedicalResourceTest {
    private static final String MEDICAL_RESOURCE_ID = "medical_resource_id";
    private static final String DATA_SOURCE_ID = "data_source_id";
    private static final String DISPLAY_NAME = "display name";
    private static final Instant RELEVANT_TIME = Instant.now();

    @Test
    public void testMedicalResourceBuilder_requiredFieldsOnly() {
        MedicalResource resource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_ID,
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                DATA_SOURCE_ID,
                                DISPLAY_NAME)
                        .build();

        assertThat(resource.getId()).isEqualTo(MEDICAL_RESOURCE_ID);
        assertThat(resource.getType()).isEqualTo(MEDICAL_RESOURCE_TYPE_UNKNOWN);
        assertThat(resource.getDataSourceId()).isEqualTo(DATA_SOURCE_ID);
        assertThat(resource.getDisplayName()).isEqualTo(DISPLAY_NAME);
    }

    @Test
    public void testMedicalResourceBuilder_fromExistingBuilder() {
        MedicalResource.Builder original =
                new MedicalResource.Builder(
                        MEDICAL_RESOURCE_ID,
                        MEDICAL_RESOURCE_TYPE_UNKNOWN,
                        DATA_SOURCE_ID,
                        DISPLAY_NAME);
        MedicalResource resource = new MedicalResource.Builder(original).build();

        assertThat(resource).isEqualTo(original.build());
    }

    @Test
    public void testMedicalResourceBuilder_fromExistingInstance() {
        MedicalResource original =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_ID,
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                DATA_SOURCE_ID,
                                DISPLAY_NAME)
                        .build();
        MedicalResource resource = new MedicalResource.Builder(original).build();

        assertThat(resource).isEqualTo(original);
    }

    @Test
    public void testMedicalResourceBuilder_resetRequiredFields() {
        MedicalResource resource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_ID,
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                DATA_SOURCE_ID,
                                DISPLAY_NAME)
                        .setId("another_id")
                        .setDataSourceId("another_data_source_id")
                        .setDisplayName("another_display_name")
                        .build();

        assertThat(resource.getId()).isEqualTo("another_id");
        assertThat(resource.getDataSourceId()).isEqualTo("another_data_source_id");
        assertThat(resource.getDisplayName()).isEqualTo("another_display_name");
    }

    @Test
    public void testMedicalResource_toString_requiredFieldsOnly() {
        MedicalResource resource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_ID,
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                DATA_SOURCE_ID,
                                DISPLAY_NAME)
                        .build();
        String expectedPropertiesString =
                String.format(
                        "id=%s,type=%d,dataSourceId=%s,displayName=%s",
                        MEDICAL_RESOURCE_ID,
                        MEDICAL_RESOURCE_TYPE_UNKNOWN,
                        DATA_SOURCE_ID,
                        DISPLAY_NAME);

        assertThat(resource.toString())
                .isEqualTo(String.format("MedicalResource{%s}", expectedPropertiesString));
    }
}
