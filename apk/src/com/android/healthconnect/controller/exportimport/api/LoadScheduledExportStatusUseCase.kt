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

    suspend fun execute(): ScheduledExportStatus {
        val scheduledExportStatus: ScheduledExportStatus =
            suspendCancellableCoroutine { continuation ->
                healthDataExportManager.getScheduledExportStatus(
                    Runnable::run, continuation.asOutcomeReceiver())
            }
        return scheduledExportStatus
    }

    override suspend operator fun invoke(): ExportUseCaseResult<ScheduledExportStatus> =
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
    suspend fun invoke(): ExportUseCaseResult<ScheduledExportStatus>
}
