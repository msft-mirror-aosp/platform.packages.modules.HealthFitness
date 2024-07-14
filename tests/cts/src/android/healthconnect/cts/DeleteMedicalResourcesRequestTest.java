/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.DeleteMedicalResourcesRequest;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
public class DeleteMedicalResourcesRequestTest {

    @Test
    public void testRequestBuilder_noDatasources_throws() {
        DeleteMedicalResourcesRequest.Builder request = new DeleteMedicalResourcesRequest.Builder();

        assertThrows(IllegalArgumentException.class, request::build);
    }

    @Test
    public void testRequestBuilder_oneDatasource_ok() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();

        assertThat(request.getDataSourceIds()).containsExactly("foo");
    }

    @Test
    public void testRequestBuilder_multipleDatasource_ok() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addDataSourceId("bar")
                        .addDataSourceId("baz")
                        .build();

        assertThat(request.getDataSourceIds()).containsExactly("foo", "bar", "baz");
    }

    @Test
    public void testRequestBuilder_fromExistingBuilder() {
        DeleteMedicalResourcesRequest.Builder original =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo");
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder(original).build();

        assertThat(request).isEqualTo(original.build());
    }

    @Test
    public void testRequestBuilder_fromExistingBuilder_changeIndependently() {
        DeleteMedicalResourcesRequest.Builder original =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo");
        DeleteMedicalResourcesRequest.Builder copy =
                new DeleteMedicalResourcesRequest.Builder(original);
        original.addDataSourceId("bar");

        assertThat(original.build().getDataSourceIds()).containsExactly("foo", "bar");
        assertThat(copy.build().getDataSourceIds()).containsExactly("foo");
    }

    @Test
    public void testRequest_equalsSame() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();
        DeleteMedicalResourcesRequest same =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();

        assertThat(request.equals(same)).isTrue();
        assertThat(request.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    public void testRequest_equalsDifferentOrderSame() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("bar")
                        .addDataSourceId("foo")
                        .build();
        DeleteMedicalResourcesRequest same =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addDataSourceId("bar")
                        .build();

        assertThat(request.equals(same)).isTrue();
        assertThat(request.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    public void testRequest_equalsDifferent() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();
        DeleteMedicalResourcesRequest different =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("bar").build();

        assertThat(request.equals(different)).isFalse();
    }

    @Test
    public void testToString() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();
        String expectedPropertiesString = "dataSourceIds=[foo]";

        assertThat(request.toString())
                .isEqualTo(
                        String.format(
                                "DeleteMedicalResourcesRequest{%s}", expectedPropertiesString));
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        DeleteMedicalResourcesRequest original =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DeleteMedicalResourcesRequest restored =
                DeleteMedicalResourcesRequest.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }

    @Test
    public void testWriteToParcelThenRestore_multiple_objectsAreIdentical() {
        DeleteMedicalResourcesRequest original =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addDataSourceId("bar")
                        .addDataSourceId("baz")
                        .build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        DeleteMedicalResourcesRequest restored =
                DeleteMedicalResourcesRequest.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }

    @Test
    public void testReadFromEmptyParcel_illegalArgument() {
        Parcel parcel = Parcel.obtain();
        parcel.writeStringList(Collections.emptyList());
        parcel.setDataPosition(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> DeleteMedicalResourcesRequest.CREATOR.createFromParcel(parcel));
        parcel.recycle();
    }

    @Test
    public void testCreateFromParcel_null_throwsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> DeleteMedicalResourcesRequest.CREATOR.createFromParcel(null));
    }

    @Test
    public void testDescribeContents_noFlags() {
        DeleteMedicalResourcesRequest original =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();
        assertThat(original.describeContents()).isEqualTo(0);
    }
}
