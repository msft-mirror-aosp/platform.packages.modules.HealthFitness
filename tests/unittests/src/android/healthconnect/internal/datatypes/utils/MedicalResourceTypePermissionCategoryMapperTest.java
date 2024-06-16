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

package android.healthconnect.internal.datatypes.utils;

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.MedicalPermissionCategory;
import android.health.connect.internal.datatypes.utils.MedicalResourceTypePermissionCategoryMapper;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;

public class MedicalResourceTypePermissionCategoryMapperTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalPermissionCategoryForResourceType_immunizationType_returnsCategory() {
        int category =
                MedicalResourceTypePermissionCategoryMapper.getMedicalPermissionCategory(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION);

        assertThat(category).isEqualTo(MedicalPermissionCategory.IMMUNIZATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalPermissionCategoryForResourceType_unknownType_throws() {
        MedicalResourceTypePermissionCategoryMapper.getMedicalPermissionCategory(
                MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }
}
