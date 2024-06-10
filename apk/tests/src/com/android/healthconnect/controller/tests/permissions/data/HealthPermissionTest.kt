/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.permissions.data

import android.health.connect.HealthPermissions
import com.android.healthconnect.controller.permissions.data.HealthPermission.AdditionalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.MedicalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.ACTIVE_CALORIES_BURNED
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.BLOOD_GLUCOSE
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HealthPermissionTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun fromPermission_returnsCorrectReadFitnessPermission() {
        assertThat(fromPermissionString("android.permission.health.READ_ACTIVE_CALORIES_BURNED"))
            .isEqualTo(FitnessPermission(ACTIVE_CALORIES_BURNED, PermissionsAccessType.READ))
    }

    @Test
    fun fromPermission_returnsCorrectWriteFitnessPermission() {
        assertThat(fromPermissionString("android.permission.health.WRITE_BLOOD_GLUCOSE"))
            .isEqualTo(FitnessPermission(BLOOD_GLUCOSE, PermissionsAccessType.WRITE))
    }

    @Test
    fun fromPermissionString_canParseAllFitnessPermissions() {
        val allPermissions =
            healthPermissionReader.getHealthPermissions().filterNot { perm ->
                healthPermissionReader.isAdditionalPermission(perm) ||
                    healthPermissionReader.isMedicalPermission(perm)
            }
        for (permissionString in allPermissions) {
            assertThat(fromPermissionString(permissionString).toString())
                .isEqualTo(permissionString)
        }
    }

    @Test
    fun fromPermissionString_canParseAllMedicalPermissions() {
        val medicalPermissions =
            healthPermissionReader.getHealthPermissions().filter { perm ->
                healthPermissionReader.isMedicalPermission(perm)
            }
        for (permissionString in medicalPermissions) {
            assertThat(fromPermissionString(permissionString).toString())
                .isEqualTo(permissionString)
        }
    }

    @Test
    fun fromPermissionString_returnsCorrectAdditionalPermission() {
        assertThat(fromPermissionString("android.permission.health.READ_HEALTH_DATA_HISTORY"))
            .isEqualTo(AdditionalPermission(HealthPermissions.READ_HEALTH_DATA_HISTORY))

        assertThat(fromPermissionString("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"))
            .isEqualTo(AdditionalPermission(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND))
    }

    @Test
    fun fromPermissionString_returnsCorrectMedicalPermission() {
        assertThat(fromPermissionString("android.permission.health.WRITE_MEDICAL_RESOURCES"))
            .isEqualTo(MedicalPermission(MedicalPermissionType.ALL_MEDICAL_DATA))

        assertThat(
                fromPermissionString(
                    "android.permission.health.READ_MEDICAL_RESOURCES_IMMUNIZATION"))
            .isEqualTo(MedicalPermission(MedicalPermissionType.IMMUNIZATION))
    }

    @Test
    fun fromPermissionString_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            fromPermissionString("Unsupported_permission")
        }
    }
}
