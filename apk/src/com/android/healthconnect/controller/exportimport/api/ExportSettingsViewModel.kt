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
    private val queryDocumentProvidersUseCase: IQueryDocumentProvidersUseCase
) : ViewModel() {
    private val _storedExportSettings = MutableLiveData<ExportSettings>()
    private val _selectedExportFrequency = MutableLiveData<ExportFrequency>()
    private val _previousExportFrequency = MutableLiveData<ExportFrequency?>()
    private val _documentProviders = MutableLiveData<DocumentProviders>()

    /** Holds the export settings that is stored in the Health Connect service. */
    val storedExportSettings: LiveData<ExportSettings>
        get() = _storedExportSettings

    /** Holds the previous export frequency that is stored. */
    val previousExportFrequency: LiveData<ExportFrequency?>
        get() = _previousExportFrequency

    /** Holds the user selected export frequency. */
    val selectedExportFrequency: LiveData<ExportFrequency?>
        get() = _selectedExportFrequency

    /** Holds the supported document providers. */
    val documentProviders: LiveData<DocumentProviders>
        get() = _documentProviders

    init {
        loadExportSettings()
        loadDocumentProviders()
        _selectedExportFrequency.value = ExportFrequency.EXPORT_FREQUENCY_NEVER
    }

    /** Triggers a load of export settings. */
    fun loadExportSettings() {
        _storedExportSettings.postValue(ExportSettings.Loading)
        viewModelScope.launch {
            when (val result = loadExportSettingsUseCase.invoke()) {
                is ExportImportUseCaseResult.Success -> {
                    _storedExportSettings.postValue(ExportSettings.WithData(result.data))
                }
                is ExportImportUseCaseResult.Failed -> {
                    _storedExportSettings.postValue(ExportSettings.LoadingFailed)
                }
            }
        }
    }

    /** Triggers a query of the document providers. */
    fun loadDocumentProviders() {
        _documentProviders.postValue(DocumentProviders.Loading)
        viewModelScope.launch {
            when (val result = queryDocumentProvidersUseCase.invoke()) {
                is ExportImportUseCaseResult.Success -> {
                    _documentProviders.postValue(DocumentProviders.WithData(result.data))
                }
                is ExportImportUseCaseResult.Failed -> {
                    _documentProviders.postValue(DocumentProviders.LoadingFailed)
                }
            }
        }
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

    /**
     * Updates the uri and the selected frequency to write to in scheduled exports of Health Connect
     * data.
     */
    fun updateExportUriWithSelectedFrequency(uri: Uri) {
        val settings =
            ScheduledExportSettings.withUriAndPeriodInDays(
                uri,
                _selectedExportFrequency.value?.periodInDays
                    ?: ExportFrequency.EXPORT_FREQUENCY_NEVER.periodInDays)
        updateExportSettings(settings)
    }

    /** Updates the frequency of scheduled exports of Health Connect data. */
    fun updateExportFrequency(frequency: ExportFrequency) {
        val settings = ScheduledExportSettings.withPeriodInDays(frequency.periodInDays)
        updateExportSettings(settings)
    }

    /** Updates the stored frequency of scheduled exports of Health Connect data. */
    fun updateSelectedFrequency(frequency: ExportFrequency) {
        _selectedExportFrequency.value = frequency
    }

    private fun updateExportSettings(settings: ScheduledExportSettings) {
        viewModelScope.launch {
            when (updateExportSettingsUseCase.invoke(settings)) {
                is ExportImportUseCaseResult.Success -> {
                    if (settings.periodInDays != DEFAULT_INT) {
                        val frequency = fromPeriodInDays(settings.periodInDays)
                        _storedExportSettings.postValue(ExportSettings.WithData(frequency))
                    }
                }
                is ExportImportUseCaseResult.Failed -> {
                    _storedExportSettings.postValue(ExportSettings.LoadingFailed)
                }
            }
        }
    }
}
