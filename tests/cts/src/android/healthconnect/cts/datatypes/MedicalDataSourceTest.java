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

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.MedicalDataSource;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MedicalDataSourceTest {
    private static final String MEDICAL_DATA_SOURCE_ID = "medical_data_source_id";
    private static final String PACKAGE_NAME = "package_name";
    private static final String FHIR_BASE_URI = "fhir_base_uri";
    private static final String DISPLAY_NAME = "display_name";

    @Test
    public void testMedicalDataSourceBuilder_constructor() {
        MedicalDataSource dataSource =
                new MedicalDataSource.Builder(
                                MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME)
                        .build();

        assertThat(dataSource.getId()).isEqualTo(MEDICAL_DATA_SOURCE_ID);
        assertThat(dataSource.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(dataSource.getFhirBaseUri()).isEqualTo(FHIR_BASE_URI);
        assertThat(dataSource.getDisplayName()).isEqualTo(DISPLAY_NAME);
    }

    @Test
    public void testMedicalDataSourceBuilder_setAllFields() {
        MedicalDataSource dataSource =
                new MedicalDataSource.Builder("", "", "", "")
                        .setId(MEDICAL_DATA_SOURCE_ID)
                        .setPackageName(PACKAGE_NAME)
                        .setFhirBaseUri(FHIR_BASE_URI)
                        .setDisplayName(DISPLAY_NAME)
                        .build();

        assertThat(dataSource.getId()).isEqualTo(MEDICAL_DATA_SOURCE_ID);
        assertThat(dataSource.getPackageName()).isEqualTo(PACKAGE_NAME);
        assertThat(dataSource.getFhirBaseUri()).isEqualTo(FHIR_BASE_URI);
        assertThat(dataSource.getDisplayName()).isEqualTo(DISPLAY_NAME);
    }

    @Test
    public void testMedicalDataSourceBuilder_fromExistingBuilder() {
        MedicalDataSource.Builder original =
                new MedicalDataSource.Builder(
                        MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME);

        MedicalDataSource dataSource = new MedicalDataSource.Builder(original).build();

        assertThat(dataSource).isEqualTo(original.build());
    }

    @Test
    public void testMedicalDataSourceBuilder_fromExistingInstance() {
        MedicalDataSource original =
                new MedicalDataSource.Builder(
                                MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME)
                        .build();

        MedicalDataSource dataSource = new MedicalDataSource.Builder(original).build();

        assertThat(dataSource).isEqualTo(original);
    }

    @Test
    public void testMedicalDataSource_toString() {
        MedicalDataSource dataSource =
                new MedicalDataSource.Builder(
                                MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME)
                        .build();
        String expectedPropertiesString =
                String.format(
                        "id=%s,packageName=%s,fhirBaseUri=%s,displayName=%s",
                        MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME);

        assertThat(dataSource.toString())
                .isEqualTo(String.format("MedicalDataSource{%s}", expectedPropertiesString));
    }

    @Test
    public void testMedicalDataSource_equals() {
        MedicalDataSource dataSource1 =
                new MedicalDataSource.Builder(
                                MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME)
                        .build();
        MedicalDataSource dataSource2 =
                new MedicalDataSource.Builder(
                                MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME)
                        .build();

        assertThat(dataSource1.equals(dataSource2)).isTrue();
        assertThat(dataSource1.hashCode()).isEqualTo(dataSource2.hashCode());
    }

    @Test
    public void testMedicalDataSource_equals_comparesAllValues() {
        MedicalDataSource dataSource =
                new MedicalDataSource.Builder(
                                MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME)
                        .build();
        MedicalDataSource dataSourceDifferentBaseUri =
                new MedicalDataSource.Builder(dataSource)
                        .setFhirBaseUri("different_base_uri")
                        .build();
        MedicalDataSource dataSourceDifferentDisplayName =
                new MedicalDataSource.Builder(dataSource)
                        .setFhirBaseUri("different_display_name")
                        .build();
        MedicalDataSource dataSourceDifferentId =
                new MedicalDataSource.Builder(dataSource).setFhirBaseUri("different_id").build();
        MedicalDataSource dataSourceDifferentPackageName =
                new MedicalDataSource.Builder(dataSource)
                        .setFhirBaseUri("different_package_name")
                        .build();

        assertThat(dataSourceDifferentBaseUri.equals(dataSource)).isFalse();
        assertThat(dataSourceDifferentDisplayName.equals(dataSource)).isFalse();
        assertThat(dataSourceDifferentId.equals(dataSource)).isFalse();
        assertThat(dataSourceDifferentPackageName.equals(dataSource)).isFalse();
        assertThat(dataSourceDifferentBaseUri.hashCode()).isNotEqualTo(dataSource.hashCode());
        assertThat(dataSourceDifferentDisplayName.hashCode()).isNotEqualTo(dataSource.hashCode());
        assertThat(dataSourceDifferentId.hashCode()).isNotEqualTo(dataSource.hashCode());
        assertThat(dataSourceDifferentPackageName.hashCode()).isNotEqualTo(dataSource.hashCode());
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        MedicalDataSource original =
                new MedicalDataSource.Builder(
                                MEDICAL_DATA_SOURCE_ID, PACKAGE_NAME, FHIR_BASE_URI, DISPLAY_NAME)
                        .build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MedicalDataSource restored = MedicalDataSource.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
