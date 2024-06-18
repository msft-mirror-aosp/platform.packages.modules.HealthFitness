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
import androidx.core.os.asOutcomeReceiver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class QueryDocumentProvidersUseCase
@Inject
constructor(
    private val healthDataExportManager: HealthDataExportManager,
) : IQueryDocumentProvidersUseCase {
    companion object {
        private const val TAG = "QueryDocumentProvidersUseCase"
    }

    /** Returns the available document providers. */
    override suspend operator fun invoke(): ExportImportUseCaseResult<List<DocumentProvider>> =
        withContext(Dispatchers.IO) {
            try {
                val documentProviders: List<DocumentProvider> =
                    suspendCancellableCoroutine { continuation ->
                            healthDataExportManager.queryDocumentProviders(
                                Runnable::run, continuation.asOutcomeReceiver())
                        }
                        .groupBy({
                            DocumentProviderInfo(it.title, it.authority, it.iconResource)
                        }) {
                            DocumentProviderRoot(it.summary, it.rootUri)
                        }
                        .map { DocumentProvider(it.key, sortDocumentProviderRoots(it.value)) }
                        .stream()
                        .sorted { provider1, provider2 ->
                            provider1.info.title.compareTo(provider2.info.title)
                        }
                        .toList()
                ExportImportUseCaseResult.Success(documentProviders)
            } catch (ex: HealthConnectException) {
                Log.e(TAG, "Query document providers error: ", ex)
                ExportImportUseCaseResult.Failed(ex)
            }
        }

    private fun sortDocumentProviderRoots(
        roots: List<DocumentProviderRoot>
    ): List<DocumentProviderRoot> {
        return roots
            .stream()
            .sorted { root1, root2 -> root1.summary.compareTo(root2.summary) }
            .toList()
    }
}

interface IQueryDocumentProvidersUseCase {
    /** Returns the available document providers. */
    suspend fun invoke(): ExportImportUseCaseResult<List<DocumentProvider>>
}
