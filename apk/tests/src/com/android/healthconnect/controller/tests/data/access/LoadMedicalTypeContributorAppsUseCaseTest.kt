/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.data.access

import android.content.Context
import android.health.connect.HealthConnectManager
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.access.LoadMedicalTypeContributorAppsUseCase
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@HiltAndroidTest
class LoadMedicalTypeContributorAppsUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)
    private lateinit var loadMedicalTypeContributorAppsUseCase:
            LoadMedicalTypeContributorAppsUseCase

    @Inject lateinit var appInfoReader: AppInfoReader

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        hiltRule.inject()
        loadMedicalTypeContributorAppsUseCase =
            LoadMedicalTypeContributorAppsUseCase(
                appInfoReader, healthConnectManager, Dispatchers.Main)
    }

    @Test
    fun immunization_returnsEmptyMap() = runTest {
        val result = loadMedicalTypeContributorAppsUseCase.invoke(MedicalPermissionType.IMMUNIZATION)
        val expected = listOf<AppMetadata>()
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun allMedicalData_returnsEmptyMap() = runTest {
        val result = loadMedicalTypeContributorAppsUseCase.invoke(MedicalPermissionType.ALL_MEDICAL_DATA)
        val expected = listOf<AppMetadata>()
        assertThat(result).isEqualTo(expected)
    }
}
