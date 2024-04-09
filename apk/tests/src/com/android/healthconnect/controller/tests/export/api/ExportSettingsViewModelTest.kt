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

package com.android.healthconnect.controller.tests.export.api

import android.health.connect.Constants.DEFAULT_INT
import android.net.Uri
import com.android.healthconnect.controller.export.api.ExportFrequency.EXPORT_FREQUENCY_DAILY
import com.android.healthconnect.controller.export.api.ExportFrequency.EXPORT_FREQUENCY_MONTHLY
import com.android.healthconnect.controller.export.api.ExportFrequency.EXPORT_FREQUENCY_WEEKLY
import com.android.healthconnect.controller.export.api.ExportSettings
import com.android.healthconnect.controller.export.api.ExportSettingsViewModel
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.di.FakeLoadExportSettingsUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeUpdateExportSettingsUseCase
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class ExportSettingsViewModelTest {
    companion object {
        private val TEST_SECRET_KEY = byteArrayOf(1, 2, 3, 4)
        private val TEST_SALT = byteArrayOf(5, 6, 7, 8)
        private val TEST_URI: Uri = Uri.parse("content://com.android.server.healthconnect/testuri")
    }

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ExportSettingsViewModel
    private val loadExportSettingsUseCase = FakeLoadExportSettingsUseCase()
    private val updateExportSettingsUseCase = FakeUpdateExportSettingsUseCase()

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel = ExportSettingsViewModel(loadExportSettingsUseCase, updateExportSettingsUseCase)
    }

    @After
    fun tearDown() {
        updateExportSettingsUseCase.reset()
        loadExportSettingsUseCase.reset()
        Dispatchers.resetMain()
    }

    @Test
    fun loadExportSettings() = runTest {
        val testObserver = TestObserver<ExportSettings>()
        viewModel.storedExportSettings.observeForever(testObserver)
        loadExportSettingsUseCase.updateExportFrequency(EXPORT_FREQUENCY_WEEKLY)

        viewModel.loadExportSettings()
        advanceUntilIdle()

        assertThat(testObserver.getLastValue())
            .isEqualTo(ExportSettings.WithData(EXPORT_FREQUENCY_WEEKLY))
    }

    @Test
    fun updateExportSecretKey() = runTest {
        viewModel.updateExportSecretKey(TEST_SECRET_KEY, TEST_SALT)
        advanceUntilIdle()

        assertThat(updateExportSettingsUseCase.mostRecentSettings.secretKey)
            .isEqualTo(TEST_SECRET_KEY)
        assertThat(updateExportSettingsUseCase.mostRecentSettings.salt).isEqualTo(TEST_SALT)
        assertThat(updateExportSettingsUseCase.mostRecentSettings.uri).isNull()
        assertThat(updateExportSettingsUseCase.mostRecentSettings.periodInDays)
            .isEqualTo(DEFAULT_INT)
    }

    @Test
    fun updateExportSecretKey_keepsExistingFrequencySetting() = runTest {
        val testObserver = TestObserver<ExportSettings>()
        viewModel.storedExportSettings.observeForever(testObserver)
        loadExportSettingsUseCase.updateExportFrequency(EXPORT_FREQUENCY_MONTHLY)
        viewModel.loadExportSettings()

        viewModel.updateExportSecretKey(TEST_SECRET_KEY, TEST_SALT)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue())
            .isEqualTo(ExportSettings.WithData(EXPORT_FREQUENCY_MONTHLY))
    }

    @Test
    fun updateExportUri() = runTest {
        viewModel.updateExportUri(TEST_URI)
        advanceUntilIdle()

        assertThat(updateExportSettingsUseCase.mostRecentSettings.secretKey).isNull()
        assertThat(updateExportSettingsUseCase.mostRecentSettings.salt).isNull()
        assertThat(updateExportSettingsUseCase.mostRecentSettings.uri).isEqualTo(TEST_URI)
        assertThat(updateExportSettingsUseCase.mostRecentSettings.periodInDays)
            .isEqualTo(DEFAULT_INT)
    }

    @Test
    fun updateExportUri_keepsExistingFrequencySetting() = runTest {
        val testObserver = TestObserver<ExportSettings>()
        viewModel.storedExportSettings.observeForever(testObserver)
        loadExportSettingsUseCase.updateExportFrequency(EXPORT_FREQUENCY_MONTHLY)
        viewModel.loadExportSettings()

        viewModel.updateExportUri(TEST_URI)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue())
            .isEqualTo(ExportSettings.WithData(EXPORT_FREQUENCY_MONTHLY))
    }

    @Test
    fun updateExportFrequency() = runTest {
        viewModel.updateExportFrequency(EXPORT_FREQUENCY_DAILY)
        advanceUntilIdle()

        assertThat(updateExportSettingsUseCase.mostRecentSettings.secretKey).isNull()
        assertThat(updateExportSettingsUseCase.mostRecentSettings.salt).isNull()
        assertThat(updateExportSettingsUseCase.mostRecentSettings.uri).isNull()
        assertThat(updateExportSettingsUseCase.mostRecentSettings.periodInDays)
            .isEqualTo(EXPORT_FREQUENCY_DAILY.periodInDays)
    }

    @Test
    fun updateExportFrequency_updatesStoredExportFrequency() = runTest {
        val testObserver = TestObserver<ExportSettings>()
        viewModel.storedExportSettings.observeForever(testObserver)
        loadExportSettingsUseCase.updateExportFrequency(EXPORT_FREQUENCY_MONTHLY)
        viewModel.loadExportSettings()

        viewModel.updateExportFrequency(EXPORT_FREQUENCY_DAILY)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue())
            .isEqualTo(ExportSettings.WithData(EXPORT_FREQUENCY_DAILY))
    }
}
