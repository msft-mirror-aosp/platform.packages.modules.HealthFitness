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

import android.health.connect.exportimport.ImportStatus
import android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_NONE
import android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_UNKNOWN
import android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_VERSION_MISMATCH
import android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_WRONG_FILE
import androidx.core.os.asOutcomeReceiver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class LoadImportStatusUseCase
@Inject
constructor(
    private val healthDataImportManager: HealthDataImportManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : ILoadImportStatusUseCase {
    suspend fun execute(): ImportUiState {
        val importStatus: ImportStatus = suspendCancellableCoroutine { continuation ->
            healthDataImportManager.getImportStatus(Runnable::run, continuation.asOutcomeReceiver())
        }
        val dataImportError: ImportUiState.DataImportError =
            when (importStatus.dataImportError) {
                DATA_IMPORT_ERROR_UNKNOWN -> ImportUiState.DataImportError.DATA_IMPORT_ERROR_UNKNOWN
                DATA_IMPORT_ERROR_NONE -> ImportUiState.DataImportError.DATA_IMPORT_ERROR_NONE
                DATA_IMPORT_ERROR_WRONG_FILE ->
                    ImportUiState.DataImportError.DATA_IMPORT_ERROR_WRONG_FILE
                DATA_IMPORT_ERROR_VERSION_MISMATCH ->
                    ImportUiState.DataImportError.DATA_IMPORT_ERROR_VERSION_MISMATCH
                else -> {
                    ImportUiState.DataImportError.DATA_IMPORT_ERROR_UNKNOWN
                }
            }
        return ImportUiState(dataImportError, importStatus.isImportOngoing)
    }

    override suspend fun invoke(): ExportImportUseCaseResult<ImportUiState> =
        withContext(dispatcher) {
            try {
                ExportImportUseCaseResult.Success(execute())
            } catch (exception: Exception) {
                ExportImportUseCaseResult.Failed(exception)
            }
        }
}

interface ILoadImportStatusUseCase {
    /** Returns the stored import status. */
    suspend fun invoke(): ExportImportUseCaseResult<ImportUiState>
}
