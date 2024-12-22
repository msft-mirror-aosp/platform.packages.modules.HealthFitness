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
import static android.healthconnect.cts.utils.PhrDataFactory.PAGE_TOKEN;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceAllergy;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResourceBuilder;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.ReadMedicalResourcesResponse;
import android.health.connect.datatypes.MedicalResource;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
        ReadMedicalResourcesResponse response = new ReadMedicalResourcesResponse(List.of(), null);

        assertThat(response.getMedicalResources()).isEqualTo(List.of());
        assertThat(response.getNextPageToken()).isNull();
    }

    @Test
    public void testReadMedicalResourcesResponse_constructor_singleton() {
        List<MedicalResource> medicalResources = List.of(getMedicalResource());
        ReadMedicalResourcesResponse response =
                new ReadMedicalResourcesResponse(medicalResources, null);

        assertThat(response.getMedicalResources()).isEqualTo(medicalResources);
        assertThat(response.getNextPageToken()).isNull();
    }

    @Test
    public void testReadMedicalResourcesResponse_constructor_multipleEntries() {
        List<MedicalResource> medicalResources =
                List.of(
                        getMedicalResource(),
                        getMedicalResourceBuilder()
                                .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                                .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                                .setFhirResource(getFhirResourceAllergy())
                                .build());
        ReadMedicalResourcesResponse response =
                new ReadMedicalResourcesResponse(medicalResources, null);

        assertThat(response.getMedicalResources()).isEqualTo(medicalResources);
        assertThat(response.getNextPageToken()).isNull();
    }

    @Test
    public void testReadMedicalResourcesResponse_constructor_withPageToken() {
        List<MedicalResource> medicalResources = List.of(getMedicalResource());
        ReadMedicalResourcesResponse response =
                new ReadMedicalResourcesResponse(medicalResources, PAGE_TOKEN);

        assertThat(response.getMedicalResources()).isEqualTo(medicalResources);
        assertThat(response.getNextPageToken()).isEqualTo(PAGE_TOKEN);
    }

    @Test
    public void testReadMedicalResourcesResponse_equals() {
        List<MedicalResource> medicalResources = List.of(getMedicalResource());
        ReadMedicalResourcesResponse response1 =
                new ReadMedicalResourcesResponse(medicalResources, PAGE_TOKEN);
        ReadMedicalResourcesResponse response2 =
                new ReadMedicalResourcesResponse(medicalResources, PAGE_TOKEN);

        assertThat(response1.equals(response2)).isTrue();
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    public void testReadMedicalResourcesResponse_equals_comparesAllValues() {
        List<MedicalResource> medicalResources = List.of(getMedicalResource());
        ReadMedicalResourcesResponse response =
                new ReadMedicalResourcesResponse(medicalResources, PAGE_TOKEN);
        ReadMedicalResourcesResponse responseDifferentList =
                new ReadMedicalResourcesResponse(
                        List.of(
                                getMedicalResourceBuilder()
                                        .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                                        .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                                        .setFhirResource(getFhirResourceAllergy())
                                        .build()),
                        PAGE_TOKEN);
        ReadMedicalResourcesResponse responseDifferentPageToken =
                new ReadMedicalResourcesResponse(medicalResources, null);

        assertThat(responseDifferentList.equals(response)).isFalse();
        assertThat(responseDifferentPageToken.equals(response)).isFalse();
        assertThat(responseDifferentList.hashCode()).isNotEqualTo(response.hashCode());
        assertThat(responseDifferentPageToken.hashCode()).isNotEqualTo(response.hashCode());
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        List<MedicalResource> medicalResources =
                List.of(
                        getMedicalResource(),
                        getMedicalResourceBuilder()
                                .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                                .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                                .setFhirResource(getFhirResourceAllergy())
                                .build());
        ReadMedicalResourcesResponse original =
                new ReadMedicalResourcesResponse(medicalResources, PAGE_TOKEN);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ReadMedicalResourcesResponse restored =
                ReadMedicalResourcesResponse.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
