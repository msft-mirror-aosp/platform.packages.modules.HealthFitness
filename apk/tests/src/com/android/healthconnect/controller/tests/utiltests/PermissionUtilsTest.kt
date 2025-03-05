/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.healthconnect.controller.tests.utiltests

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.utils.PermissionUtils
import com.android.healthfitness.flags.Flags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@HiltAndroidTest
class PermissionUtilsTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private val healthPermissionReader: HealthPermissionReader = mock()
    private val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase = mock()

    lateinit var permissionUtils: PermissionUtils

    @Before
    fun setup() {
        hiltRule.inject()

        permissionUtils = PermissionUtils(healthPermissionReader,
            getHealthPermissionsFlagsUseCase)
    }

    @Test
    @DisableFlags(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    fun isBodySensorSplitPermissionApp_flagDisabled_returnsFalse() {
        assertThat(permissionUtils.isBodySensorSplitPermissionApp(
            TEST_APP_PACKAGE_NAME)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    fun isBodySensorSplitPermissionApp_hrPermissionMissing_returnsFalse() {
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf())

        assertThat(permissionUtils.isBodySensorSplitPermissionApp(
            TEST_APP_PACKAGE_NAME)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    fun isBodySensorSplitPermissionApp_multipleHealthPermissionsRequested_returnsFalse() {
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(HealthPermissions.READ_HEART_RATE, HealthPermissions.READ_STEPS))

        assertThat(permissionUtils.isBodySensorSplitPermissionApp(
            TEST_APP_PACKAGE_NAME)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    fun isBodySensorSplitPermissionApp_hrNotFromSplitPermission_returnsFalse() {
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(HealthPermissions.READ_HEART_RATE))
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any()))
            .thenReturn(mapOf(HealthPermissions.READ_HEART_RATE to PackageManager.FLAG_PERMISSION_USER_SET))

        assertThat(permissionUtils.isBodySensorSplitPermissionApp(
            TEST_APP_PACKAGE_NAME)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    fun isBodySensorSplitPermissionApp_hrFromSplitPermission_returnsTrue() {
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(HealthPermissions.READ_HEART_RATE))
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).thenReturn(
            mapOf(HealthPermissions.READ_HEART_RATE to PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED))

        assertThat(permissionUtils.isBodySensorSplitPermissionApp(
            TEST_APP_PACKAGE_NAME)).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    fun isBodySensorSplitPermissionApp_multipleHealthPermissionsIncludingBackground_returnsFalse() {
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(
                HealthPermissions.READ_HEART_RATE, HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                HealthPermissions.READ_STEPS))

        assertThat(permissionUtils.isBodySensorSplitPermissionApp(
            TEST_APP_PACKAGE_NAME)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    fun isBodySensorSplitPermissionApp_backgroundNotFromSplitPermission_returnsFalse() {
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(
                HealthPermissions.READ_HEART_RATE, HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND))
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).thenReturn(
            mapOf(
                HealthPermissions.READ_HEART_RATE to PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND to PackageManager.FLAG_PERMISSION_USER_SET))

        assertThat(permissionUtils.isBodySensorSplitPermissionApp(
            TEST_APP_PACKAGE_NAME)).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    fun isBodySensorSplitPermissionApp_hrAndBackgroundFromSplitPermission_returnsTrue() {
        whenever(healthPermissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(
                HealthPermissions.READ_HEART_RATE, HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND))
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).thenReturn(
            mapOf(
                HealthPermissions.READ_HEART_RATE to PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND to PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED))

        assertThat(permissionUtils.isBodySensorSplitPermissionApp(
            TEST_APP_PACKAGE_NAME)).isTrue()
    }
}
