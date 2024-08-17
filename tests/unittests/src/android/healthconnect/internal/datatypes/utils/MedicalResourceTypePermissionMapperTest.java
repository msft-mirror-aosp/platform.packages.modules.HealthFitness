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

import static org.junit.Assert.assertThrows;

import android.health.connect.HealthPermissions;
import android.health.connect.internal.datatypes.utils.MedicalResourceTypePermissionMapper;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

public class MedicalResourceTypePermissionMapperTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalReadPermissionForResourceType_immunizationType_returns() {
        String readPermission =
                MedicalResourceTypePermissionMapper.getMedicalReadPermission(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION);

        assertThat(readPermission).isEqualTo(HealthPermissions.READ_MEDICAL_DATA_IMMUNIZATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalReadPermissionForResourceType_unknownType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MedicalResourceTypePermissionMapper.getMedicalReadPermission(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN));
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalResourceTypeForReadPermission_immunizationType_returns() {
        int medicalResourceType =
                MedicalResourceTypePermissionMapper.getMedicalResourceType(
                        HealthPermissions.READ_MEDICAL_DATA_IMMUNIZATION);

        assertThat(medicalResourceType).isEqualTo(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalResourceTypeForReadPermission_coversAllPermissions() {
        Set<String> medicalReadPermissions =
                HealthPermissions.getAllMedicalPermissions().stream()
                        .filter(
                                permissionString ->
                                        !permissionString.equals(
                                                HealthPermissions.WRITE_MEDICAL_DATA))
                        .collect(Collectors.toSet());
        Set<Integer> medicalResourceTypes =
                medicalReadPermissions.stream()
                        .map(MedicalResourceTypePermissionMapper::getMedicalResourceType)
                        .collect(Collectors.toSet());

        assertThat(medicalResourceTypes.size()).isEqualTo(medicalReadPermissions.size());
        assertThat(medicalResourceTypes.size()).isEqualTo(1);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalResourceTypeForReadPermission_fitnessDataType_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MedicalResourceTypePermissionMapper.getMedicalResourceType(
                                HealthPermissions.READ_STEPS));
    }
}
