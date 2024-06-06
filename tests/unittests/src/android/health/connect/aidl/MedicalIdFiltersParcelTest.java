/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.health.connect.aidl;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.MedicalIdFilter;
import android.os.Parcel;

import org.junit.Test;

import java.util.List;

public class MedicalIdFiltersParcelTest {
    @Test
    public void testCreateFromParcel_correctResult() {
        MedicalIdFiltersParcel medicalIdFiltersParcelOriginal =
                new MedicalIdFiltersParcel(
                        List.of(MedicalIdFilter.fromId("123"), MedicalIdFilter.fromId("456")));
        Parcel parcel = Parcel.obtain();
        medicalIdFiltersParcelOriginal.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        MedicalIdFiltersParcel medicalIdFiltersParcelResult =
                MedicalIdFiltersParcel.CREATOR.createFromParcel(parcel);

        assertThat(medicalIdFiltersParcelResult.getMedicalIdFilters().size()).isEqualTo(2);
        // TODO(b/340227835): assert for object equality once we can make changes to
        // MedicalIdFilter.
        assertThat(medicalIdFiltersParcelResult.getMedicalIdFilters().get(0).getId())
                .isEqualTo(medicalIdFiltersParcelOriginal.getMedicalIdFilters().get(0).getId());
        assertThat(medicalIdFiltersParcelResult.getMedicalIdFilters().get(1).getId())
                .isEqualTo(medicalIdFiltersParcelOriginal.getMedicalIdFilters().get(1).getId());

        parcel.recycle();
    }
}
