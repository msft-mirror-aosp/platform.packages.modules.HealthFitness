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

import android.health.connect.HealthConnectException
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LoadExportSettingsUseCase
@Inject
constructor(
    private val healthDataExportManager: HealthDataExportManager,
) : ILoadExportSettingsUseCase {
    companion object {
        private const val TAG = "LoadExportSettingsUseCase"
    }

    /** Returns the stored export settings. */
    override suspend operator fun invoke(): ExportUseCaseResult<ExportFrequency> =
        withContext(Dispatchers.IO) {
            try {
                val periodInDays = healthDataExportManager.getScheduledExportPeriodInDays()
                val frequency = fromPeriodInDays(periodInDays)
                ExportUseCaseResult.Success(frequency)
            } catch (ex: HealthConnectException) {
                Log.e(TAG, "Load export settings error: ", ex)
                ExportUseCaseResult.Failed(ex)
            }
        }
}

interface ILoadExportSettingsUseCase {
    /** Returns the stored export settings. */
    suspend fun invoke(): ExportUseCaseResult<ExportFrequency>
}
