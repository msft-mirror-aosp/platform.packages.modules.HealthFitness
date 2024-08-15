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
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalDataSource;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.MedicalResourceTypeInfoResponse;
import android.health.connect.datatypes.MedicalDataSource;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
public class MedicalResourceTypeInfoResponseTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testConstructor_EmptyContributingDataSources() {
        MedicalResourceTypeInfoResponse response =
                new MedicalResourceTypeInfoResponse(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, Set.of());

        assertThat(response.getMedicalResourceType()).isEqualTo(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        assertThat(response.getContributingDataSources()).isEmpty();
    }

    @Test
    public void testConstructor_withContributingDataSources() {
        Set<MedicalDataSource> dataSources = Set.of(getMedicalDataSource());
        MedicalResourceTypeInfoResponse response =
                new MedicalResourceTypeInfoResponse(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, dataSources);

        assertThat(response.getMedicalResourceType()).isEqualTo(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        assertThat(response.getContributingDataSources()).isEqualTo(dataSources);
    }

    @Test
    public void testConstructor_invalidMedicalResourceType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MedicalResourceTypeInfoResponse(1000, Set.of()));
    }

    @Test
    public void testEquals() {
        Set<MedicalDataSource> dataSources = Set.of(getMedicalDataSource());
        MedicalResourceTypeInfoResponse response1 =
                new MedicalResourceTypeInfoResponse(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, dataSources);
        MedicalResourceTypeInfoResponse response2 =
                new MedicalResourceTypeInfoResponse(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, dataSources);

        assertThat(response1.equals(response2)).isTrue();
        assertThat(response1.hashCode()).isEqualTo(response2.hashCode());
    }

    @Test
    public void testEquals_comparesAllValues() {
        Set<MedicalDataSource> dataSources = Set.of(getMedicalDataSource());
        MedicalResourceTypeInfoResponse response =
                new MedicalResourceTypeInfoResponse(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, dataSources);
        MedicalResourceTypeInfoResponse responseDifferentMedicalResourceType =
                new MedicalResourceTypeInfoResponse(MEDICAL_RESOURCE_TYPE_UNKNOWN, dataSources);
        MedicalResourceTypeInfoResponse responseDifferentDataSources =
                new MedicalResourceTypeInfoResponse(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, Set.of());

        assertThat(responseDifferentMedicalResourceType.equals(response)).isFalse();
        assertThat(responseDifferentDataSources.equals(response)).isFalse();
        assertThat(responseDifferentMedicalResourceType.hashCode())
                .isNotEqualTo(response.hashCode());
        assertThat(responseDifferentDataSources.hashCode()).isNotEqualTo(response.hashCode());
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        Set<MedicalDataSource> dataSources = Set.of(getMedicalDataSource());
        MedicalResourceTypeInfoResponse original =
                new MedicalResourceTypeInfoResponse(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, dataSources);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MedicalResourceTypeInfoResponse restored =
                MedicalResourceTypeInfoResponse.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
