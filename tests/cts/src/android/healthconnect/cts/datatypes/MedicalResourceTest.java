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

package android.healthconnect.cts.datatypes;

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_MEDICAL_RESOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.MEDICAL_RESOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResourceBuilder;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.MedicalResource;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MedicalResourceTest {
    @Test
    public void testMedicalResourceBuilder_requiredFieldsOnly() {
        MedicalResource resource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_ID,
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                DATA_SOURCE_ID,
                                FHIR_DATA_IMMUNIZATION)
                        .build();

        assertThat(resource.getId()).isEqualTo(MEDICAL_RESOURCE_ID);
        assertThat(resource.getType()).isEqualTo(MEDICAL_RESOURCE_TYPE_UNKNOWN);
        assertThat(resource.getDataSourceId()).isEqualTo(DATA_SOURCE_ID);
        assertThat(resource.getData()).isEqualTo(FHIR_DATA_IMMUNIZATION);
    }

    @Test
    public void testMedicalResourceBuilder_setAllFields() {
        MedicalResource resource =
                getMedicalResourceBuilder()
                        .setId(DIFFERENT_MEDICAL_RESOURCE_ID)
                        .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .setData(FHIR_DATA_ALLERGY)
                        .build();

        // TODO(b/342161039): Set type and assert it's equal here when we add more types.
        assertThat(resource.getId()).isEqualTo(DIFFERENT_MEDICAL_RESOURCE_ID);
        assertThat(resource.getDataSourceId()).isEqualTo(DIFFERENT_DATA_SOURCE_ID);
        assertThat(resource.getData()).isEqualTo(FHIR_DATA_ALLERGY);
    }

    @Test
    public void testMedicalResourceBuilder_fromExistingBuilder() {
        MedicalResource.Builder original = getMedicalResourceBuilder();
        MedicalResource resource = new MedicalResource.Builder(original).build();

        assertThat(resource).isEqualTo(original.build());
    }

    @Test
    public void testMedicalResourceBuilder_fromExistingInstance() {
        MedicalResource original = getMedicalResource();
        MedicalResource resource = new MedicalResource.Builder(original).build();

        assertThat(resource).isEqualTo(original);
    }

    @Test
    public void testMedicalResource_toString() {
        MedicalResource resource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_ID,
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                DATA_SOURCE_ID,
                                FHIR_DATA_IMMUNIZATION)
                        .build();
        String expectedPropertiesString =
                String.format(
                        "id=%s,type=%d,dataSourceId=%s,data=%s",
                        MEDICAL_RESOURCE_ID,
                        MEDICAL_RESOURCE_TYPE_UNKNOWN,
                        DATA_SOURCE_ID,
                        FHIR_DATA_IMMUNIZATION);

        assertThat(resource.toString())
                .isEqualTo(String.format("MedicalResource{%s}", expectedPropertiesString));
    }

    @Test
    public void testMedicalResource_equals() {
        MedicalResource resource1 = getMedicalResource();
        MedicalResource resource2 = getMedicalResource();

        assertThat(resource1.equals(resource2)).isTrue();
        assertThat(resource1.hashCode()).isEqualTo(resource2.hashCode());
    }

    @Test
    public void testMedicalResource_equals_comparesAllValues() {
        MedicalResource resource = getMedicalResource();
        MedicalResource resourceDifferentId =
                new MedicalResource.Builder(resource).setId(DIFFERENT_MEDICAL_RESOURCE_ID).build();
        // TODO(b/342161039): Add a resourceDifferentType case when we add more types.
        MedicalResource resourceDifferentDataSourceId =
                new MedicalResource.Builder(resource)
                        .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .build();
        MedicalResource resourceDifferentData =
                new MedicalResource.Builder(resource).setData(FHIR_DATA_ALLERGY).build();

        assertThat(resourceDifferentId.equals(resource)).isFalse();
        assertThat(resourceDifferentDataSourceId.equals(resource)).isFalse();
        assertThat(resourceDifferentData.equals(resource)).isFalse();
        assertThat(resourceDifferentId.hashCode()).isNotEqualTo(resource.hashCode());
        assertThat(resourceDifferentDataSourceId.hashCode()).isNotEqualTo(resource.hashCode());
        assertThat(resourceDifferentData.hashCode()).isNotEqualTo(resource.hashCode());
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        MedicalResource original = getMedicalResource();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MedicalResource restored = MedicalResource.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
