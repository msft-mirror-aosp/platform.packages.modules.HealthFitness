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

import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_ID_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_ID_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_TYPE_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResourceId;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.MedicalResourceId;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_PERSONAL_HEALTH_RECORD)
public class MedicalResourceIdTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testMedicalResourceId_constructor() {
        MedicalResourceId medicalResourceId =
                new MedicalResourceId(
                        DATA_SOURCE_ID,
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);

        assertThat(medicalResourceId.getDataSourceId()).isEqualTo(DATA_SOURCE_ID);
        assertThat(medicalResourceId.getFhirResourceType())
                .isEqualTo(FHIR_RESOURCE_TYPE_IMMUNIZATION);
        assertThat(medicalResourceId.getFhirResourceId()).isEqualTo(FHIR_RESOURCE_ID_IMMUNIZATION);
    }

    @Test
    public void testMedicalResourceId_toString() {
        MedicalResourceId medicalResourceId =
                new MedicalResourceId(
                        DATA_SOURCE_ID,
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);
        String expectedPropertiesString =
                String.format(
                        "dataSourceId=%s,fhirResourceType=%s,fhirResourceId=%s",
                        DATA_SOURCE_ID,
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);

        assertThat(medicalResourceId.toString())
                .isEqualTo(String.format("MedicalResourceId{%s}", expectedPropertiesString));
    }

    @Test
    public void testMedicalResourceId_equals() {
        MedicalResourceId medicalResourceId1 = getMedicalResourceId();
        MedicalResourceId medicalResourceId2 = getMedicalResourceId();

        assertThat(medicalResourceId1.equals(medicalResourceId2)).isTrue();
        assertThat(medicalResourceId1.hashCode()).isEqualTo(medicalResourceId2.hashCode());
    }

    @Test
    public void testMedicalResourceId_equals_comparesAllValues() {
        MedicalResourceId medicalResourceId =
                new MedicalResourceId(
                        DATA_SOURCE_ID,
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);
        MedicalResourceId idWithDifferentDataSourceId =
                new MedicalResourceId(
                        DIFFERENT_DATA_SOURCE_ID,
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);
        MedicalResourceId idWithDifferentFhirResourceType =
                new MedicalResourceId(
                        DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_ALLERGY, FHIR_RESOURCE_ID_IMMUNIZATION);
        MedicalResourceId idWithDifferentFhirResourceId =
                new MedicalResourceId(
                        DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_IMMUNIZATION, FHIR_RESOURCE_ID_ALLERGY);

        assertThat(idWithDifferentDataSourceId.equals(medicalResourceId)).isFalse();
        assertThat(idWithDifferentFhirResourceType.equals(medicalResourceId)).isFalse();
        assertThat(idWithDifferentFhirResourceId.equals(medicalResourceId)).isFalse();
        assertThat(idWithDifferentDataSourceId.hashCode())
                .isNotEqualTo(medicalResourceId.hashCode());
        assertThat(idWithDifferentFhirResourceType.hashCode())
                .isNotEqualTo(medicalResourceId.hashCode());
        assertThat(idWithDifferentFhirResourceId.hashCode())
                .isNotEqualTo(medicalResourceId.hashCode());
    }

    @Test
    public void testWriteToParcelThenRestore_objectsAreIdentical() {
        MedicalResourceId original = getMedicalResourceId();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        MedicalResourceId restored = MedicalResourceId.CREATOR.createFromParcel(parcel);

        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }
}
