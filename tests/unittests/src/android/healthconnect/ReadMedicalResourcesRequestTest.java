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

package android.healthconnect;

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.Constants.MAXIMUM_PAGE_SIZE;
import static android.health.connect.Constants.MINIMUM_PAGE_SIZE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.ReadMedicalResourcesRequest;
import android.os.Parcel;

import org.junit.Test;

public class ReadMedicalResourcesRequestTest {

    @Test
    public void testBuilder_requiredFieldsOnly() {
        ReadMedicalResourcesRequest request = new ReadMedicalResourcesRequest.Builder().build();

        assertThat(request.getPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
        assertThat(request.getPageToken()).isEqualTo(DEFAULT_LONG);
    }

    @Test
    public void testBuilder_fromExistingBuilder() {
        ReadMedicalResourcesRequest.Builder original =
                new ReadMedicalResourcesRequest.Builder().setPageSize(100).setPageToken(1L);
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(original).build();

        assertThat(request).isEqualTo(original.build());
    }

    @Test
    public void testBuilder_fromExistingInstance() {
        ReadMedicalResourcesRequest original =
                new ReadMedicalResourcesRequest.Builder().setPageSize(100).setPageToken(1L).build();
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(original).build();

        assertThat(request).isEqualTo(original);
    }

    @Test
    public void testBuilder_lowerThanMinPageSize_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReadMedicalResourcesRequest.Builder().setPageSize(MAXIMUM_PAGE_SIZE + 1));
    }

    @Test
    public void testBuilder_exceedsMaxPageSize_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReadMedicalResourcesRequest.Builder().setPageSize(MINIMUM_PAGE_SIZE - 1));
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        ReadMedicalResourcesRequest original = new ReadMedicalResourcesRequest.Builder().build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ReadMedicalResourcesRequest restored =
                ReadMedicalResourcesRequest.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
