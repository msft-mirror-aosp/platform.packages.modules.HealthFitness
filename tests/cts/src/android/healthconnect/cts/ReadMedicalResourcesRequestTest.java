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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.healthconnect.cts.utils.DataFactory.DEFAULT_LONG;
import static android.healthconnect.cts.utils.DataFactory.DEFAULT_PAGE_SIZE;
import static android.healthconnect.cts.utils.DataFactory.MAXIMUM_PAGE_SIZE;
import static android.healthconnect.cts.utils.DataFactory.MINIMUM_PAGE_SIZE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.ReadMedicalResourcesRequest;
import android.os.Parcel;

import org.junit.Test;

public class ReadMedicalResourcesRequestTest {

    @Test
    public void testRequestBuilder_requiredFieldsOnly() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();

        assertThat(request.getMedicalResourceType()).isEqualTo(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        assertThat(request.getPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
        assertThat(request.getPageToken()).isEqualTo(DEFAULT_LONG);
    }

    @Test
    public void testRequestBuilder_setAllFields() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                        .setMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(100)
                        .setPageToken(1L)
                        .build();

        assertThat(request.getMedicalResourceType()).isEqualTo(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        assertThat(request.getPageSize()).isEqualTo(100);
        assertThat(request.getPageToken()).isEqualTo(1L);
    }

    @Test
    public void testRequestBuilder_fromExistingBuilder() {
        ReadMedicalResourcesRequest.Builder original =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(original).build();

        assertThat(request).isEqualTo(original.build());
    }

    @Test
    public void testRequestBuilder_fromExistingInstance() {
        ReadMedicalResourcesRequest original =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(original).build();

        assertThat(request).isEqualTo(original);
    }

    @Test
    public void testRequestBuilder_invalidMedicalResourceType_throws() {
        assertThrows(
                IllegalArgumentException.class, () -> new ReadMedicalResourcesRequest.Builder(-1));
    }

    @Test
    public void testRequestBuilder_lowerThanMinPageSize_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                                .setPageSize(MAXIMUM_PAGE_SIZE + 1));
    }

    @Test
    public void testRequestBuilder_exceedsMaxPageSize_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                                .setPageSize(MINIMUM_PAGE_SIZE - 1));
    }

    @Test
    public void testMedicalResource_toString() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(100)
                        .setPageToken(1L)
                        .build();
        String expectedPropertiesString =
                String.format(
                        "medicalResourceType=%d,pageSize=%d,pageToken=%d",
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, 100, 1L);

        assertThat(request.toString())
                .isEqualTo(
                        String.format("ReadMedicalResourcesRequest{%s}", expectedPropertiesString));
    }

    @Test
    public void testRequest_equals() {
        ReadMedicalResourcesRequest request1 =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(100)
                        .setPageToken(1L)
                        .build();
        ReadMedicalResourcesRequest request2 =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(100)
                        .setPageToken(1L)
                        .build();

        assertThat(request1.equals(request2)).isTrue();
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    public void testRequest_equals_comparesAllValues() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        ReadMedicalResourcesRequest requestDifferentType =
                new ReadMedicalResourcesRequest.Builder(request)
                        .setMedicalResourceType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                        .build();
        ReadMedicalResourcesRequest requestDifferentPageSize =
                new ReadMedicalResourcesRequest.Builder(request).setPageSize(100).build();
        ReadMedicalResourcesRequest requestDifferentPageTokens =
                new ReadMedicalResourcesRequest.Builder(request).setPageToken(1L).build();

        assertThat(requestDifferentType.equals(request)).isFalse();
        assertThat(requestDifferentPageSize.equals(request)).isFalse();
        assertThat(requestDifferentPageTokens.equals(request)).isFalse();
        assertThat(requestDifferentType.hashCode()).isNotEqualTo(request.hashCode());
        assertThat(requestDifferentPageSize.hashCode()).isNotEqualTo(request.hashCode());
        assertThat(requestDifferentPageTokens.hashCode()).isNotEqualTo(request.hashCode());
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        ReadMedicalResourcesRequest original =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ReadMedicalResourcesRequest restored =
                ReadMedicalResourcesRequest.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
