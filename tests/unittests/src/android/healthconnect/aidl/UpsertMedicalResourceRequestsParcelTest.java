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

import static android.healthconnect.cts.phr.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_VERSION_R4;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.aidl.UpsertMedicalResourceRequestsParcel;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.healthfitness.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
public class UpsertMedicalResourceRequestsParcelTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testUpsertMedicalResourceRequestsParcel_successfulCreateAndGet() {
        UpsertMedicalResourceRequest request1 =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, FHIR_DATA_ALLERGY)
                        .build();
        UpsertMedicalResourceRequest request2 =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, FHIR_DATA_IMMUNIZATION)
                        .build();

        UpsertMedicalResourceRequestsParcel requestsParcel =
                new UpsertMedicalResourceRequestsParcel(List.of(request1, request2));

        assertThat(requestsParcel.getUpsertRequests()).containsExactly(request1, request2);
    }

    @Test
    public void testUpsertMedicalResourceRequestsParcel_nullRequests_throws() {
        assertThrows(
                NullPointerException.class, () -> new UpsertMedicalResourceRequestsParcel(null));
    }

    @Test
    public void
            testUpsertMedicalResourceRequestsParcel_writeToParcelThenRestore_propertiesIdentical() {
        UpsertMedicalResourceRequest request1 =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, FHIR_DATA_ALLERGY)
                        .build();
        UpsertMedicalResourceRequest request2 =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, FHIR_DATA_IMMUNIZATION)
                        .build();
        UpsertMedicalResourceRequestsParcel original =
                new UpsertMedicalResourceRequestsParcel(List.of(request1, request2));

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        UpsertMedicalResourceRequestsParcel restoredParcel =
                UpsertMedicalResourceRequestsParcel.CREATOR.createFromParcel(parcel);

        assertThat(restoredParcel.getUpsertRequests()).containsExactly(request1, request2);
        parcel.recycle();
    }
}
