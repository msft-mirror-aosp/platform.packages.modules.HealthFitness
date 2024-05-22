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
import android.health.connect.HealthConnectManager
import android.health.connect.exportimport.ScheduledExportStatus
import android.os.OutcomeReceiver
import com.android.healthconnect.controller.exportimport.api.ExportUseCaseResult
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.exportimport.api.LoadScheduledExportStatusUseCase
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer

class LoadScheduledExportStatusUseCaseTest {

    private lateinit var useCase: LoadScheduledExportStatusUseCase
    private val healthDataExportManager: HealthDataExportManager =
        Mockito.mock(HealthDataExportManager::class.java)

    @Before
    fun setup() {
        useCase = LoadScheduledExportStatusUseCase(healthDataExportManager)
    }

    @Test
    fun invoke_callsHealthDataExportManagerSuccessfully() = runTest {
        val scheduledExportStatus =
            ScheduledExportStatus(
                Instant.ofEpochMilli(100), HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS, 7)
        doAnswer(prepareAnswer(scheduledExportStatus))
            .`when`(healthDataExportManager)
            .getScheduledExportStatus(any(), any())

        val result = useCase.invoke()

        assertThat(result is ExportUseCaseResult.Success).isTrue()
        val exportStatus = (result as ExportUseCaseResult.Success).data
        assertThat(exportStatus.lastSuccessfulExportTime).isEqualTo(Instant.ofEpochMilli(100))
        assertThat(exportStatus.dataExportError)
            .isEqualTo(HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS)
        assertThat(exportStatus.periodInDays).isEqualTo(7)
    }

    @Test
    fun invoke_callsHealthDataExportManager_returnsFailure() = runTest {
        doAnswer { throw HealthConnectException(HealthConnectException.ERROR_UNKNOWN) }
            .`when`(healthDataExportManager)
            .getScheduledExportStatus(any(), any())

        val result = useCase.invoke()

        assertThat(result is ExportUseCaseResult.Failed).isTrue()
        assertThat((result as ExportUseCaseResult.Failed).exception is HealthConnectException)
            .isTrue()
        assertThat((result.exception as HealthConnectException).errorCode)
            .isEqualTo(HealthConnectException.ERROR_UNKNOWN)
    }

    private fun prepareAnswer(
        scheduledExportStatus: ScheduledExportStatus
    ): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[1] as OutcomeReceiver<ScheduledExportStatus, *>
            receiver.onResult(scheduledExportStatus)
            null
        }
        return answer
    }
}
