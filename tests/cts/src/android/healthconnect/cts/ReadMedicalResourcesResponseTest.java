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

package android.healthconnect.cts;

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_MEDICAL_RESOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResourceBuilder;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.ReadMedicalResourcesResponse;
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

import java.util.List;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
public class ReadMedicalResourcesResponseTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testReadMedicalResourcesResponse_constructor_emptyList() {
        ReadMedicalResourcesResponse response = new ReadMedicalResourcesResponse(List.of());

        assertThat(response.getMedicalResources()).isEqualTo(List.of());
    }

    @Test
    public void testReadMedicalResourcesResponse_constructor_singleton() {
        List<MedicalResource> medicalResources = List.of(getMedicalResource());
        ReadMedicalResourcesResponse response = new ReadMedicalResourcesResponse(medicalResources);

        assertThat(response.getMedicalResources()).isEqualTo(medicalResources);
    }

    @Test
    public void testReadMedicalResourcesResponse_constructor_multipleEntries() {
        List<MedicalResource> medicalResources =
                List.of(
                        getMedicalResource(),
                        getMedicalResourceBuilder()
                                .setId(DIFFERENT_MEDICAL_RESOURCE_ID)
                                .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                                .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                                .setData(FHIR_DATA_ALLERGY)
                                .build());
        ReadMedicalResourcesResponse response = new ReadMedicalResourcesResponse(medicalResources);

        assertThat(response.getMedicalResources()).isEqualTo(medicalResources);
    }

    @Test
    public void testReadMedicalResourcesResponse_equals() {
        List<MedicalResource> medicalResources = List.of(getMedicalResource());
        ReadMedicalResourcesResponse response1 = new ReadMedicalResourcesResponse(medicalResources);
        ReadMedicalResourcesResponse response2 = new ReadMedicalResourcesResponse(medicalResources);

        assertThat(response1.equals(response2)).isTrue();
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    public void testReadMedicalResourcesResponse_equals_comparesAllValues() {
        List<MedicalResource> medicalResources = List.of(getMedicalResource());
        ReadMedicalResourcesResponse response = new ReadMedicalResourcesResponse(medicalResources);
        ReadMedicalResourcesResponse responseDifferentList =
                new ReadMedicalResourcesResponse(
                        List.of(
                                getMedicalResourceBuilder()
                                        .setId(DIFFERENT_MEDICAL_RESOURCE_ID)
                                        .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                                        .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                                        .setData(FHIR_DATA_ALLERGY)
                                        .build()));

        assertThat(responseDifferentList.equals(response)).isFalse();
        assertThat(responseDifferentList.hashCode()).isNotEqualTo(response.hashCode());
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        List<MedicalResource> medicalResources =
                List.of(
                        getMedicalResource(),
                        getMedicalResourceBuilder()
                                .setId(DIFFERENT_MEDICAL_RESOURCE_ID)
                                .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                                .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                                .setData(FHIR_DATA_ALLERGY)
                                .build());
        ReadMedicalResourcesResponse original = new ReadMedicalResourcesResponse(medicalResources);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ReadMedicalResourcesResponse restored =
                ReadMedicalResourcesResponse.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
