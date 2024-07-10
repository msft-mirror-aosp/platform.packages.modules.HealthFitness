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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceAllergy;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResourceBuilder;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.MedicalResource;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
public class MedicalResourceTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testMedicalResourceBuilder_requiredFieldsOnly() {
        FhirResource fhirResource = getFhirResource();
        MedicalResource resource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION, DATA_SOURCE_ID, fhirResource)
                        .build();

        assertThat(resource.getType()).isEqualTo(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        assertThat(resource.getDataSourceId()).isEqualTo(DATA_SOURCE_ID);
        assertThat(resource.getFhirResource()).isEqualTo(fhirResource);
    }

    @Test
    public void testMedicalResourceBuilder_setAllFields() {
        FhirResource differentFhirResource = getFhirResourceAllergy();
        MedicalResource resource =
                getMedicalResourceBuilder()
                        .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                        .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .setFhirResource(differentFhirResource)
                        .build();

        assertThat(resource.getType()).isEqualTo(MEDICAL_RESOURCE_TYPE_UNKNOWN);
        assertThat(resource.getDataSourceId()).isEqualTo(DIFFERENT_DATA_SOURCE_ID);
        assertThat(resource.getFhirResource()).isEqualTo(differentFhirResource);
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
        FhirResource fhirResource = getFhirResource();
        MedicalResource resource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION, DATA_SOURCE_ID, fhirResource)
                        .build();
        String expectedPropertiesString =
                String.format(
                        "type=%d,dataSourceId=%s,fhirResource=%s",
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, DATA_SOURCE_ID, fhirResource);

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
        MedicalResource resourceDifferentType =
                new MedicalResource.Builder(resource)
                        .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                        .build();
        MedicalResource resourceDifferentDataSourceId =
                new MedicalResource.Builder(resource)
                        .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .build();
        MedicalResource resourceDifferentFhirResource =
                new MedicalResource.Builder(resource)
                        .setFhirResource(getFhirResourceAllergy())
                        .build();

        assertThat(resourceDifferentType.equals(resource)).isFalse();
        assertThat(resourceDifferentDataSourceId.equals(resource)).isFalse();
        assertThat(resourceDifferentFhirResource.equals(resource)).isFalse();
        assertThat(resourceDifferentType.hashCode()).isNotEqualTo(resource.hashCode());
        assertThat(resourceDifferentDataSourceId.hashCode()).isNotEqualTo(resource.hashCode());
        assertThat(resourceDifferentFhirResource.hashCode()).isNotEqualTo(resource.hashCode());
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
