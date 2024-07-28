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

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.GetMedicalDataSourcesRequest;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
@RunWith(AndroidJUnit4.class)
public class GetMedicalDataSourcesRequestTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testBuilder_constructor() {
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();

        assertThat(request.getPackageNames()).isEmpty();
    }

    @Test
    public void testBuilder_addPackageNames() {
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName("com.foo")
                        .addPackageName("com.bar")
                        .build();

        assertThat(request.getPackageNames()).containsExactly("com.foo", "com.bar");
    }

    @Test
    public void testRequest_equalsAndHashcode() {
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName("com.foo")
                        .addPackageName("com.bar")
                        .build();
        GetMedicalDataSourcesRequest requestSame =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName("com.bar")
                        .addPackageName("com.foo")
                        .build();
        GetMedicalDataSourcesRequest requestDifferent =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName("com.bar")
                        .addPackageName("com.foo")
                        .addPackageName("com.baz")
                        .build();

        assertThat(request).isEqualTo(requestSame);
        assertThat(request.hashCode()).isEqualTo(requestSame.hashCode());
        assertThat(request).isNotEqualTo(requestDifferent);
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        GetMedicalDataSourcesRequest original =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName("com.foo")
                        .addPackageName("com.bar")
                        .build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        GetMedicalDataSourcesRequest restored =
                GetMedicalDataSourcesRequest.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }

    @Test
    public void testRequest_toString() {
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName("com.foo")
                        .addPackageName("com.bar")
                        .build();
        String expectedPropertiesStringOrder1 = "packageNames={com.foo, com.bar}";
        String expectedPropertiesStringOrder2 = "packageNames={com.bar, com.foo}";

        String formatString = "GetMedicalDataSourcesRequest{%s}";
        assertThat(request.toString())
                .isAnyOf(
                        String.format(formatString, expectedPropertiesStringOrder1),
                        String.format(formatString, expectedPropertiesStringOrder2));
    }
}
