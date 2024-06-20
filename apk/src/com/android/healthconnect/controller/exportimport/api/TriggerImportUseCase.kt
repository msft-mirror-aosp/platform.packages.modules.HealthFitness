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
import android.net.Uri
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TriggerImportUseCase
@Inject
constructor(
    private val healthDataImportManager: HealthDataImportManager,
) : ITriggerImportUseCase {
    companion object {
        private const val TAG = "TriggerImportUseCase"
    }

    /** Triggers the process to import and restore the user-selected backup file. */
    override suspend operator fun invoke(
        fileToImportUri: Uri
    ): ExportImportUseCaseResult<ImportUiState> =
        withContext(Dispatchers.IO) {
            try {
                healthDataImportManager.runImport(fileToImportUri)
                ExportImportUseCaseResult.Success(
                    ImportUiState(ImportUiState.DataImportError.DATA_IMPORT_ERROR_NONE, false))
            } catch (ex: HealthConnectException) {
                Log.e(TAG, "Load export settings error: ", ex)
                ExportImportUseCaseResult.Failed(ex)
            }
        }
}

interface ITriggerImportUseCase {
    /** Triggers the process to import and restore the user-selected backup file. */
    suspend fun invoke(fileToImportUri: Uri): ExportImportUseCaseResult<ImportUiState>
}
