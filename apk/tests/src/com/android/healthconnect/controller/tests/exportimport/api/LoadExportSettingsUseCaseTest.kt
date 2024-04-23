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

package com.android.healthconnect.controller.tests.exportimport.api

import android.health.connect.HealthConnectException
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.ExportUseCaseResult
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.exportimport.api.LoadExportSettingsUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class LoadExportSettingsUseCaseTest {

    private lateinit var useCase: LoadExportSettingsUseCase
    private val healthDataExportManager: HealthDataExportManager =
        mock(HealthDataExportManager::class.java)

    @Before
    fun setup() {
        useCase = LoadExportSettingsUseCase(healthDataExportManager)
    }

    @Test
    fun invoke_callsHealthDataExportManager() = runTest {
        doAnswer { _ -> 1 }.`when`(healthDataExportManager).getScheduledExportPeriodInDays()
        val result = useCase.invoke()

        verify(healthDataExportManager).getScheduledExportPeriodInDays()
        assertThat(result is ExportUseCaseResult.Success).isTrue()
        assertThat((result as ExportUseCaseResult.Success).data)
            .isEqualTo(ExportFrequency.EXPORT_FREQUENCY_DAILY)
    }

    @Test
    fun invoke_callsHealthDataExportManager_returnsFailure() = runTest {
        doAnswer { throw HealthConnectException(HealthConnectException.ERROR_UNKNOWN) }
            .`when`(healthDataExportManager)
            .getScheduledExportPeriodInDays()

        val result = useCase.invoke()

        assertThat(result is ExportUseCaseResult.Failed).isTrue()
        assertThat((result as ExportUseCaseResult.Failed).exception is HealthConnectException)
            .isTrue()
        assertThat((result.exception as HealthConnectException).errorCode)
            .isEqualTo(HealthConnectException.ERROR_UNKNOWN)
    }
}
