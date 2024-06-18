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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for Export status. */
@HiltViewModel
class ExportStatusViewModel
@Inject
constructor(
    private val loadScheduledExportStatusUseCase: ILoadScheduledExportStatusUseCase,
) : ViewModel() {
    private val _storedScheduledExportStatus = MutableLiveData<ScheduledExportUiStatus>()

    /** Holds the export status that is stored in the Health Connect service. */
    val storedScheduledExportStatus: LiveData<ScheduledExportUiStatus>
        get() = _storedScheduledExportStatus

    init {
        loadScheduledExportStatus()
    }

    /** Triggers a load of scheduled export status. */
    fun loadScheduledExportStatus() {
        _storedScheduledExportStatus.postValue(ScheduledExportUiStatus.Loading)
        viewModelScope.launch {
            when (val result = loadScheduledExportStatusUseCase.invoke()) {
                is ExportImportUseCaseResult.Success -> {
                    _storedScheduledExportStatus.postValue(
                        ScheduledExportUiStatus.WithData(result.data))
                }
                is ExportImportUseCaseResult.Failed -> {
                    _storedScheduledExportStatus.postValue(ScheduledExportUiStatus.LoadingFailed)
                }
            }
        }
    }
}
