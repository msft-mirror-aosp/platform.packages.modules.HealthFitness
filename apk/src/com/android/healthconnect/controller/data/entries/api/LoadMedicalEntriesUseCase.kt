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
package com.android.healthconnect.controller.data.entries.api

import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/** Use case to load medical data type entries. */
@Singleton
class LoadMedicalEntriesUseCase
@Inject
constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : BaseUseCase<LoadMedicalEntriesInput, List<FormattedEntry>>(dispatcher), ILoadMedicalEntriesUseCase {

    override suspend fun execute(input: LoadMedicalEntriesInput): List<FormattedEntry> {
        return listOf<FormattedEntry.FormattedMedicalDataEntry>()
    }
}

data class LoadMedicalEntriesInput(
    val permissionType: MedicalPermissionType,
    val packageName: String?,
    val showDataOrigin: Boolean
)

interface ILoadMedicalEntriesUseCase {
    suspend fun invoke(input: LoadMedicalEntriesInput): UseCaseResults<List<FormattedEntry>>

    suspend fun execute(input: LoadMedicalEntriesInput): List<FormattedEntry>
}
