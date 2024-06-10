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

package com.android.healthconnect.controller.exportimport.api

import android.health.connect.HealthConnectManager
import android.health.connect.exportimport.ScheduledExportStatus
import androidx.core.os.asOutcomeReceiver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadScheduledExportStatusUseCase
@Inject
constructor(
    private val healthDataExportManager: HealthDataExportManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ILoadScheduledExportStatusUseCase {

    companion object {
        private const val TAG = "LoadScheduledExportStatusUseCase"
    }

    suspend fun execute(): ScheduledExportUiState {
        val scheduledExportStatus: ScheduledExportStatus =
            suspendCancellableCoroutine { continuation ->
                healthDataExportManager.getScheduledExportStatus(
                    Runnable::run, continuation.asOutcomeReceiver())
            }
        val dataExportError: ScheduledExportUiState.DataExportError =
            when (scheduledExportStatus.dataExportError) {
                HealthConnectManager.DATA_EXPORT_ERROR_UNKNOWN ->
                    ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_UNKNOWN
                HealthConnectManager.DATA_EXPORT_ERROR_NONE ->
                    ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE
                HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS ->
                    ScheduledExportUiState.DataExportError.DATA_EXPORT_LOST_FILE_ACCESS
                else -> {
                    ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_UNKNOWN
                }
            }
        return ScheduledExportUiState(
            scheduledExportStatus.lastSuccessfulExportTime,
            dataExportError,
            scheduledExportStatus.periodInDays)
    }

    override suspend operator fun invoke(): ExportUseCaseResult<ScheduledExportUiState> =
        withContext(dispatcher) {
            try {
                ExportUseCaseResult.Success(execute())
            } catch (exception: Exception) {
                ExportUseCaseResult.Failed(exception)
            }
        }
}

interface ILoadScheduledExportStatusUseCase {
    /** Returns the stored scheduled export status. */
    suspend fun invoke(): ExportUseCaseResult<ScheduledExportUiState>
}
