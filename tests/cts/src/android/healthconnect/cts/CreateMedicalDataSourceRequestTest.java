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

import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequestBuilder;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.CreateMedicalDataSourceRequest;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CreateMedicalDataSourceRequestTest {

    @Test
    public void testCreateMedicalDataSourceRequestBuilder_constructor() {
        CreateMedicalDataSourceRequest request =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();

        assertThat(request.getFhirBaseUri()).isEqualTo(DATA_SOURCE_FHIR_BASE_URI);
        assertThat(request.getDisplayName()).isEqualTo(DATA_SOURCE_DISPLAY_NAME);
    }

    @Test
    public void testCreateMedicalDataSourceRequestBuilder_setAllFields() {
        CreateMedicalDataSourceRequest request =
                new CreateMedicalDataSourceRequest.Builder("", "")
                        .setFhirBaseUri(DATA_SOURCE_FHIR_BASE_URI)
                        .setDisplayName(DATA_SOURCE_DISPLAY_NAME)
                        .build();

        assertThat(request.getFhirBaseUri()).isEqualTo(DATA_SOURCE_FHIR_BASE_URI);
        assertThat(request.getDisplayName()).isEqualTo(DATA_SOURCE_DISPLAY_NAME);
    }

    @Test
    public void testCreateMedicalDataSourceRequestBuilder_fromExistingBuilder() {
        CreateMedicalDataSourceRequest.Builder original =
                getCreateMedicalDataSourceRequestBuilder();

        CreateMedicalDataSourceRequest request =
                new CreateMedicalDataSourceRequest.Builder(original).build();

        assertThat(request).isEqualTo(original.build());
    }

    @Test
    public void testCreateMedicalDataSourceRequestBuilder_fromExistingInstance() {
        CreateMedicalDataSourceRequest original = getCreateMedicalDataSourceRequest();

        CreateMedicalDataSourceRequest request =
                new CreateMedicalDataSourceRequest.Builder(original).build();

        assertThat(request).isEqualTo(original);
    }

    @Test
    public void testCreateMedicalDataSourceRequest_toString() {
        CreateMedicalDataSourceRequest request =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        String expectedPropertiesString =
                String.format(
                        "fhirBaseUri=%s,displayName=%s",
                        DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME);

        assertThat(request.toString())
                .isEqualTo(
                        String.format(
                                "CreateMedicalDataSourceRequest{%s}", expectedPropertiesString));
    }

    @Test
    public void testCreateMedicalDataSourceRequest_equals() {
        CreateMedicalDataSourceRequest request1 = getCreateMedicalDataSourceRequest();
        CreateMedicalDataSourceRequest request2 = getCreateMedicalDataSourceRequest();

        assertThat(request1.equals(request2)).isTrue();
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    public void testCreateMedicalDataSourceRequest_equals_comparesAllValues() {
        CreateMedicalDataSourceRequest request = getCreateMedicalDataSourceRequest();
        CreateMedicalDataSourceRequest requestDifferentBaseUri =
                new CreateMedicalDataSourceRequest.Builder(request)
                        .setFhirBaseUri(DIFFERENT_DATA_SOURCE_BASE_URI)
                        .build();
        CreateMedicalDataSourceRequest requestDifferentDisplayName =
                new CreateMedicalDataSourceRequest.Builder(request)
                        .setDisplayName(DIFFERENT_DATA_SOURCE_DISPLAY_NAME)
                        .build();

        assertThat(requestDifferentBaseUri.equals(request)).isFalse();
        assertThat(requestDifferentDisplayName.equals(request)).isFalse();
        assertThat(requestDifferentBaseUri.hashCode()).isNotEqualTo(request.hashCode());
        assertThat(requestDifferentDisplayName.hashCode()).isNotEqualTo(request.hashCode());
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        CreateMedicalDataSourceRequest original = getCreateMedicalDataSourceRequest();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CreateMedicalDataSourceRequest restored =
                CreateMedicalDataSourceRequest.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
