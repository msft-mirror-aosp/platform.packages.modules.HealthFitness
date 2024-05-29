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

import android.health.connect.Constants.DEFAULT_INT
import android.health.connect.exportimport.ScheduledExportSettings
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for Export settings fragments. */
@HiltViewModel
class ExportSettingsViewModel
@Inject
constructor(
    private val loadExportSettingsUseCase: ILoadExportSettingsUseCase,
    private val updateExportSettingsUseCase: IUpdateExportSettingsUseCase,
    private val loadScheduledExportStatusUseCase: ILoadScheduledExportStatusUseCase,
    private val queryDocumentProvidersUseCase: IQueryDocumentProvidersUseCase
) : ViewModel() {
    private val _storedExportSettings = MutableLiveData<ExportSettings>()
    private val _previousExportFrequency = MutableLiveData<ExportFrequency?>()
    private val _storedScheduledExportStatus = MutableLiveData<ScheduledExportUiStatus>()
    private val _documentProviders = MutableLiveData<DocumentProviders>()

    /** Holds the export settings that is stored in the Health Connect service. */
    val storedExportSettings: LiveData<ExportSettings>
        get() = _storedExportSettings

    /** Holds the previous export frequency that is stored. */
    val previousExportFrequency: LiveData<ExportFrequency?>
        get() = _previousExportFrequency

    /** Holds the export status that is stored in the Health Connect service. */
    val storedScheduledExportStatus: LiveData<ScheduledExportUiStatus>
        get() = _storedScheduledExportStatus

    /** Holds the supported document providers. */
    val documentProviders: LiveData<DocumentProviders>
        get() = _documentProviders

    init {
        loadExportSettings()
        loadScheduledExportStatus()
        loadDocumentProviders()
    }

    /** Triggers a load of export settings. */
    fun loadExportSettings() {
        _storedExportSettings.postValue(ExportSettings.Loading)
        viewModelScope.launch {
            when (val result = loadExportSettingsUseCase.invoke()) {
                is ExportUseCaseResult.Success -> {
                    _storedExportSettings.postValue(ExportSettings.WithData(result.data))
                }
                is ExportUseCaseResult.Failed -> {
                    _storedExportSettings.postValue(ExportSettings.LoadingFailed)
                }
            }
        }
    }

    /** Triggers a load of scheduled export status. */
    fun loadScheduledExportStatus() {
        _storedScheduledExportStatus.postValue(ScheduledExportUiStatus.Loading)
        viewModelScope.launch {
            when (val result = loadScheduledExportStatusUseCase.invoke()) {
                is ExportUseCaseResult.Success -> {
                    _storedScheduledExportStatus.postValue(
                        ScheduledExportUiStatus.WithData(result.data))
                }
                is ExportUseCaseResult.Failed -> {
                    _storedScheduledExportStatus.postValue(ScheduledExportUiStatus.LoadingFailed)
                }
            }
        }
    }

    /** Triggers a query of the document providers. */
    fun loadDocumentProviders() {
        _documentProviders.postValue(DocumentProviders.Loading)
        viewModelScope.launch {
            when (val result = queryDocumentProvidersUseCase.invoke()) {
                is ExportUseCaseResult.Success -> {
                    _documentProviders.postValue(DocumentProviders.WithData(result.data))
                }
                is ExportUseCaseResult.Failed -> {
                    _documentProviders.postValue(DocumentProviders.LoadingFailed)
                }
            }
        }
    }

    /**
     * Updates the secret key and salt used for encrypting data in scheduled exports of Health
     * Connect data.
     */
    fun updateExportSecretKey(secretKey: ByteArray, salt: ByteArray) {
        val settings = ScheduledExportSettings.withSecretKey(secretKey, salt)
        updateExportSettings(settings)
    }

    /** Updates the previous frequency of scheduled exports of Health Connect data. */
    fun updatePreviousExportFrequency(frequency: ExportFrequency) {
        if (frequency != ExportFrequency.EXPORT_FREQUENCY_NEVER) {
            _previousExportFrequency.value = frequency
        }
    }

    /** Updates the uri to write to in scheduled exports of Health Connect data. */
    fun updateExportUri(uri: Uri) {
        val settings = ScheduledExportSettings.withUri(uri)
        updateExportSettings(settings)
    }

    /** Updates the frequency of scheduled exports of Health Connect data. */
    fun updateExportFrequency(frequency: ExportFrequency) {
        val settings = ScheduledExportSettings.withPeriodInDays(frequency.periodInDays)
        updateExportSettings(settings)
    }

    private fun updateExportSettings(settings: ScheduledExportSettings) {
        viewModelScope.launch {
            when (updateExportSettingsUseCase.invoke(settings)) {
                is ExportUseCaseResult.Success -> {
                    if (settings.periodInDays != DEFAULT_INT) {
                        val frequency = fromPeriodInDays(settings.periodInDays)
                        _storedExportSettings.postValue(ExportSettings.WithData(frequency))
                    }
                }
                is ExportUseCaseResult.Failed -> {
                    _storedExportSettings.postValue(ExportSettings.LoadingFailed)
                }
            }
        }
    }
}
