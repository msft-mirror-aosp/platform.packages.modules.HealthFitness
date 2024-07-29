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

import static android.health.connect.datatypes.FhirVersion.parseFhirVersion;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4B;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.UpsertMedicalResourceRequest;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.healthfitness.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
public class UpsertMedicalResourceRequestTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testUpsertMedicalResourceRequest_successfulCreate() {
        UpsertMedicalResourceRequest upsertMedicalResourceRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                parseFhirVersion(FHIR_VERSION_R4),
                                FHIR_DATA_ALLERGY)
                        .build();

        assertThat(upsertMedicalResourceRequest.getDataSourceId()).isEqualTo(DATA_SOURCE_ID);
        assertThat(upsertMedicalResourceRequest.getFhirVersion())
                .isEqualTo(parseFhirVersion(FHIR_VERSION_R4));
        assertThat(upsertMedicalResourceRequest.getData()).isEqualTo(FHIR_DATA_ALLERGY);
    }

    @Test
    public void testUpsertMedicalResourceRequest_fromExistingBuilder() {
        UpsertMedicalResourceRequest.Builder builder =
                new UpsertMedicalResourceRequest.Builder(
                        DATA_SOURCE_ID, parseFhirVersion(FHIR_VERSION_R4), FHIR_DATA_ALLERGY);
        UpsertMedicalResourceRequest copy =
                new UpsertMedicalResourceRequest.Builder(builder).build();

        assertThat(copy).isEqualTo(builder.build());
    }

    @Test
    public void testUpsertMedicalResourceRequest_fromExistingRequest() {
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                parseFhirVersion(FHIR_VERSION_R4),
                                FHIR_DATA_ALLERGY)
                        .build();
        UpsertMedicalResourceRequest copy =
                new UpsertMedicalResourceRequest.Builder(request).build();

        assertThat(copy).isEqualTo(request);
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                parseFhirVersion(FHIR_VERSION_R4),
                                FHIR_DATA_ALLERGY)
                        .build();

        Parcel parcel = Parcel.obtain();
        request.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        UpsertMedicalResourceRequest copy =
                UpsertMedicalResourceRequest.CREATOR.createFromParcel(parcel);

        assertThat(copy).isEqualTo(request);
        parcel.recycle();
    }

    @Test
    public void testUpsertMedicalResourceRequest_equals() {
        UpsertMedicalResourceRequest upsertMedicalResourceRequest1 =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                parseFhirVersion(FHIR_VERSION_R4),
                                FHIR_DATA_ALLERGY)
                        .build();
        UpsertMedicalResourceRequest upsertMedicalResourceRequest2 =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                parseFhirVersion(FHIR_VERSION_R4),
                                FHIR_DATA_ALLERGY)
                        .build();

        assertThat(upsertMedicalResourceRequest1.equals(upsertMedicalResourceRequest2)).isTrue();
        assertThat(upsertMedicalResourceRequest1.hashCode())
                .isEqualTo(upsertMedicalResourceRequest2.hashCode());
    }

    @Test
    public void testUpsertMedicalDataSource_equals_comparesAllValues() {
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                parseFhirVersion(FHIR_VERSION_R4),
                                FHIR_DATA_IMMUNIZATION)
                        .build();
        UpsertMedicalResourceRequest requestDifferentId =
                new UpsertMedicalResourceRequest.Builder(request)
                        .setDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .build();
        UpsertMedicalResourceRequest requestDifferentFhirVersion =
                new UpsertMedicalResourceRequest.Builder(request)
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4B))
                        .build();
        UpsertMedicalResourceRequest requestDifferentData =
                new UpsertMedicalResourceRequest.Builder(request)
                        .setData(FHIR_DATA_ALLERGY)
                        .build();

        assertThat(requestDifferentId.equals(request)).isFalse();
        assertThat(requestDifferentFhirVersion.equals(request)).isFalse();
        assertThat(requestDifferentData.equals(request)).isFalse();
        assertThat(requestDifferentId.hashCode()).isNotEqualTo(request.hashCode());
        assertThat(requestDifferentFhirVersion.hashCode()).isNotEqualTo(request.hashCode());
        assertThat(requestDifferentData.hashCode()).isNotEqualTo(request.hashCode());
    }

    @Test
    public void testRequestBuilder_nullData_throws() {
        assertThrows(
                NullPointerException.class,
                () -> new UpsertMedicalResourceRequest.Builder(null, null, null));
    }
}
