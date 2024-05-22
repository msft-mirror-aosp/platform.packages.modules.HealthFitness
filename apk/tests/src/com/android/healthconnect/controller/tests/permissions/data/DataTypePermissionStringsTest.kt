/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.permissions.data

import com.android.healthconnect.controller.permissions.data.DataTypePermissionStrings
import com.android.healthconnect.controller.permissions.data.HealthPermission.DataTypePermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class DataTypePermissionStringsTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun allHealthPermissionTypesHaveStrings() {
        for (type in HealthPermissionType.values()) {
            assertThat(DataTypePermissionStrings.fromPermissionType(type)).isNotNull()
        }
    }

    @Test
    fun allDataTypePermissionsHaveStrings() {
        val allPermissions =
            healthPermissionReader.getHealthPermissions().filterNot { perm ->
                healthPermissionReader.isAdditionalPermission(perm) || healthPermissionReader.isMedicalPermission(perm)
            }
        for (permission in allPermissions) {
            val type = DataTypePermission.fromPermissionString(permission).healthPermissionType
            assertThat(DataTypePermissionStrings.fromPermissionType(type)).isNotNull()
        }
    }
}
