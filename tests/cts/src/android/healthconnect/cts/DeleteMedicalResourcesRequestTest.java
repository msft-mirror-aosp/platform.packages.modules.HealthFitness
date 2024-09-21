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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.healthconnect.cts.utils.TestUtils.setFieldValueUsingReflection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.DeleteMedicalResourcesRequest;
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

import java.util.Collections;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
public class DeleteMedicalResourcesRequestTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testRequestBuilder_noDatasources_throws() {
        DeleteMedicalResourcesRequest.Builder request = new DeleteMedicalResourcesRequest.Builder();

        assertThrows(IllegalArgumentException.class, request::build);
    }

    @Test
    public void testRequestBuilder_invalidMedicalResourceType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DeleteMedicalResourcesRequest.Builder().addMedicalResourceType(-1));
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
    public void testRequestBuilder_oneFhirTypeOk_ok() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();

        assertThat(request.getMedicalResourceTypes())
                .containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    public void testRequestBuilder_multipleResourceTypes_ok() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .addMedicalResourceType(MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN)
                        .build();

        assertThat(request.getMedicalResourceTypes())
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                        MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    public void testRequestBuilder_multipleResourceTypesAndDataSources_ok() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .addMedicalResourceType(MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN)
                        .addDataSourceId("foo")
                        .addDataSourceId("bar")
                        .build();

        assertThat(request.getDataSourceIds()).containsExactly("foo", "bar");

        assertThat(request.getMedicalResourceTypes())
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                        MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN);
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
    public void testRequestBuilder_fromExistingBuilderResourceType() {
        DeleteMedicalResourcesRequest.Builder original =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder(original).build();

        assertThat(request).isEqualTo(original.build());
    }

    @Test
    public void testRequestBuilder_fromExistingBuilderClearDataSources() {
        DeleteMedicalResourcesRequest.Builder original =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        DeleteMedicalResourcesRequest.Builder copy =
                new DeleteMedicalResourcesRequest.Builder(original);
        original.addDataSourceId("bar");

        copy.clearDataSourceIds();

        assertThat(original.build().getDataSourceIds()).containsExactly("foo", "bar");
        assertThat(copy.build().getDataSourceIds()).isEmpty();
    }

    @Test
    public void testRequestBuilder_fromExistingBuilderClearMedicalResourceTypes() {
        DeleteMedicalResourcesRequest.Builder original =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        DeleteMedicalResourcesRequest.Builder copy =
                new DeleteMedicalResourcesRequest.Builder(original);
        original.addMedicalResourceType(MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN);

        copy.clearMedicalResourceTypes();

        assertThat(original.build().getMedicalResourceTypes())
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                        MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN);
        assertThat(copy.build().getMedicalResourceTypes()).isEmpty();
    }

    @Test
    public void testRequest_equalsSameIdOnly() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();
        DeleteMedicalResourcesRequest same =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();

        assertThat(request.equals(same)).isTrue();
        assertThat(request.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    public void testRequest_equalsSameResourceOnly() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();
        DeleteMedicalResourcesRequest same =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();

        assertThat(request.equals(same)).isTrue();
        assertThat(request.hashCode()).isEqualTo(same.hashCode());
    }

    @Test
    public void testRequest_equalsSameResourceAndId() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .addDataSourceId("foo")
                        .build();
        DeleteMedicalResourcesRequest same =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .addDataSourceId("foo")
                        .build();

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
    public void testRequest_equalsDifferByResource() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();
        DeleteMedicalResourcesRequest different =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();

        assertThat(request.equals(different)).isFalse();
    }

    @Test
    public void testRequest_equalsDifferById() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();
        DeleteMedicalResourcesRequest different =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();

        assertThat(request.equals(different)).isFalse();
    }

    @Test
    public void testToString_idOnly() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("foo").build();
        String expectedPropertiesString = "dataSourceIds=[foo],medicalResourceTypes=[]";

        assertThat(request.toString())
                .isEqualTo(
                        String.format(
                                "DeleteMedicalResourcesRequest{%s}", expectedPropertiesString));
    }

    @Test
    public void testToString_resourceAndId() {
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId("foo")
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();
        String expectedPropertiesString = "dataSourceIds=[foo],medicalResourceTypes=[1]";

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
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .addMedicalResourceType(MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN)
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
    public void testWriteToParcelThenRestore_justIds_objectsAreIdentical() {
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
    public void testWriteToParcelThenRestore_justResources_objectsAreIdentical() {
        DeleteMedicalResourcesRequest original =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .addMedicalResourceType(MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN)
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
        parcel.writeIntArray(new int[0]);
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

    @Test
    public void testRestoreInvalidMedicalResourceTypesFromParcel_expectException()
            throws NoSuchFieldException, IllegalAccessException {
        DeleteMedicalResourcesRequest original =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();
        setFieldValueUsingReflection(original, "mMedicalResourceTypes", Set.of(-1));

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> DeleteMedicalResourcesRequest.CREATOR.createFromParcel(parcel));
    }
}
