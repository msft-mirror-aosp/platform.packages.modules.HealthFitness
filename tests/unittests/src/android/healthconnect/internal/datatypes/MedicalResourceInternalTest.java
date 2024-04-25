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

package android.healthconnect.internal.datatypes;

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.MedicalResource;
import android.health.connect.internal.datatypes.MedicalResourceInternal;
import android.os.Parcel;

import org.junit.Test;

public class MedicalResourceInternalTest {
    private static final String MEDICAL_RESOURCE_ID = "medical_resource_id";
    private static final String DATA_SOURCE_ID = "data_source_id";
    private static final String DISPLAY_NAME = "display name";

    @Test
    public void testMedicalResourceInternal_writeToParcelThenRestore_objectsAreIdentical() {
        MedicalResourceInternal original = buildMedicalResourceInternal();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel);
        parcel.setDataPosition(0);
        MedicalResourceInternal restored = MedicalResourceInternal.readFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }

    @Test
    public void testMedicalResourceInternal_convertToExternalAndBack_objectsAreIdentical() {
        MedicalResourceInternal original = buildMedicalResourceInternal();

        MedicalResource external = original.toExternalResource();
        MedicalResourceInternal restored = MedicalResourceInternal.fromExternalResource(external);

        assertThat(restored).isEqualTo(original);
    }

    private static MedicalResourceInternal buildMedicalResourceInternal() {
        return new MedicalResourceInternal()
                .setType(MEDICAL_RESOURCE_TYPE_UNKNOWN)
                .setId(MEDICAL_RESOURCE_ID)
                .setDataSourceId(DATA_SOURCE_ID)
                .setDisplayName(DISPLAY_NAME);
    }
}
