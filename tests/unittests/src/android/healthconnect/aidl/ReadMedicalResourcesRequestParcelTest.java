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
package android.healthconnect.aidl;

import static android.health.connect.Constants.MAXIMUM_PAGE_SIZE;
import static android.health.connect.Constants.MINIMUM_PAGE_SIZE;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.PAGE_TOKEN;
import static android.healthconnect.cts.utils.TestUtils.setFieldValueUsingReflection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.ReadMedicalResourcesInitialRequest;
import android.health.connect.ReadMedicalResourcesPageRequest;
import android.health.connect.aidl.ReadMedicalResourcesRequestParcel;
import android.os.Parcel;

import org.junit.Test;

import java.util.Set;

public class ReadMedicalResourcesRequestParcelTest {

    @Test
    public void testWriteInitialRequestToParcelThenRestore_propertiesAreIdentical() {
        ReadMedicalResourcesInitialRequest original =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(DATA_SOURCE_ID)
                        .addDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .setPageSize(100)
                        .build();

        Parcel parcel = Parcel.obtain();
        original.toParcel().writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ReadMedicalResourcesRequestParcel restoredParcel =
                ReadMedicalResourcesRequestParcel.CREATOR.createFromParcel(parcel);

        assertThat(restoredParcel.getMedicalResourceType())
                .isEqualTo(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS);
        assertThat(restoredParcel.getDataSourceIds())
                .containsExactly(DATA_SOURCE_ID, DIFFERENT_DATA_SOURCE_ID);
        assertThat(restoredParcel.getPageToken()).isNull();
        assertThat(restoredParcel.getPageSize()).isEqualTo(100);
        parcel.recycle();
    }

    @Test
    public void testWritePageRequestToParcelThenRestore_propertiesAreIdentical() {
        ReadMedicalResourcesPageRequest original =
                new ReadMedicalResourcesPageRequest.Builder(PAGE_TOKEN).setPageSize(100).build();

        Parcel parcel = Parcel.obtain();
        original.toParcel().writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ReadMedicalResourcesRequestParcel restoredParcel =
                ReadMedicalResourcesRequestParcel.CREATOR.createFromParcel(parcel);

        assertThat(restoredParcel.getMedicalResourceType()).isEqualTo(0);
        assertThat(restoredParcel.getDataSourceIds()).isEmpty();
        assertThat(restoredParcel.getPageToken()).isEqualTo(PAGE_TOKEN);
        assertThat(restoredParcel.getPageSize()).isEqualTo(100);
    }

    @Test
    public void testRestoreInvalidPageRequestFromParcel_nullPageToken_expectException()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesPageRequest original =
                new ReadMedicalResourcesPageRequest.Builder(PAGE_TOKEN).setPageSize(100).build();
        setFieldValueUsingReflection(original, "mPageToken", null);

        Parcel parcel = Parcel.obtain();
        original.toParcel().writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> ReadMedicalResourcesRequestParcel.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testRestoreInvalidMedicalResourceTypeFromParcel_expectException()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesRequestParcel original =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(DATA_SOURCE_ID)
                        .addDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .setPageSize(100)
                        .build()
                        .toParcel();
        setFieldValueUsingReflection(original, "mMedicalResourceType", -1);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> ReadMedicalResourcesRequestParcel.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testRestoreInvalidDataSourceIdFromParcel_expectException()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesRequestParcel original =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(DATA_SOURCE_ID)
                        .addDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .setPageSize(100)
                        .build()
                        .toParcel();
        setFieldValueUsingReflection(original, "mDataSourceIds", Set.of("1"));

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> ReadMedicalResourcesRequestParcel.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testRestoreTooSmallPageSizeFromParcel_expectException()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesRequestParcel original =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(DATA_SOURCE_ID)
                        .addDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .setPageSize(100)
                        .build()
                        .toParcel();
        setFieldValueUsingReflection(original, "mPageSize", MINIMUM_PAGE_SIZE - 1);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> ReadMedicalResourcesRequestParcel.CREATOR.createFromParcel(parcel));
    }

    @Test
    public void testRestoreTooLargePageSizeFromParcel_expectException()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesRequestParcel original =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(DATA_SOURCE_ID)
                        .addDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .setPageSize(100)
                        .build()
                        .toParcel();
        setFieldValueUsingReflection(original, "mPageSize", MAXIMUM_PAGE_SIZE + 1);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        assertThrows(
                IllegalArgumentException.class,
                () -> ReadMedicalResourcesRequestParcel.CREATOR.createFromParcel(parcel));
    }
}
