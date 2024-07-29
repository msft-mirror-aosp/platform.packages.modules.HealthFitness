/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 *
 */
package com.android.healthconnect.controller.data.appdata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for the [AppDataFragment] . */
@HiltViewModel
class AppDataViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val loadAppDataUseCase: AppDataUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "AppDataViewModel"
    }

    private val _appFitnessData = MutableLiveData<AppDataState>()
    private val _appMedicalData = MutableLiveData<AppDataState>()

    /** Provides a list of [PermissionTypesPerCategory]s of [FitnessPermissionType]s. */
    val appFitnessData: LiveData<AppDataState>
        get() = _appFitnessData

    /** Provides a list of [PermissionTypesPerCategory]s of [MedicalPermissionType]s. */
    val appMedicalData: LiveData<AppDataState>
        get() = _appMedicalData

    /**
     * Provides a list of all [PermissionTypesPerCategory]s to be displayed in [AppDataFragment].
     */
    val fitnessAndMedicalData: MediatorLiveData<AppDataState> =
        MediatorLiveData<AppDataState>().apply {
            value = AppDataState.Loading
            addSource(_appFitnessData) {
                postValue(getCombinedAppData(appFitnessData, appMedicalData))
            }
            addSource(_appMedicalData) {
                postValue(getCombinedAppData(appFitnessData, appMedicalData))
            }
        }

    private val _appInfo = MutableLiveData<AppMetadata>()

    val appInfo: LiveData<AppMetadata>
        get() = _appInfo

    fun loadAppData(packageName: String) {
        _appFitnessData.postValue(AppDataState.Loading)
        _appMedicalData.postValue(AppDataState.Loading)

        viewModelScope.launch {
            when (val result = loadAppDataUseCase.loadFitnessAppData(packageName)) {
                is UseCaseResults.Success -> {
                    _appFitnessData.postValue(AppDataState.WithData(result.data))
                }
                is UseCaseResults.Failed -> {
                    _appFitnessData.postValue(AppDataState.Error)
                }
            }
        }
        viewModelScope.launch {
            when (val result = loadAppDataUseCase.loadMedicalAppData(packageName)) {
                is UseCaseResults.Success -> {
                    _appMedicalData.postValue(AppDataState.WithData(result.data))
                }
                is UseCaseResults.Failed -> {
                    _appMedicalData.postValue(AppDataState.Error)
                }
            }
        }
    }

    private fun getCombinedAppData(
        appFitnessData: LiveData<AppDataState>,
        appMedicalData: LiveData<AppDataState>
    ): AppDataState {
        val fitnessData = appFitnessData.value ?: AppDataState.Loading
        val medicalData = appMedicalData.value ?: AppDataState.Loading

        if (fitnessData is AppDataState.WithData && medicalData is AppDataState.WithData) {
            val combinedData = fitnessData.dataMap + medicalData.dataMap
            return AppDataState.WithData(combinedData)
        }
        if (fitnessData is AppDataState.WithData) {
            return fitnessData
        }
        if (medicalData is AppDataState.WithData) {
            return medicalData
        }
        if (fitnessData is AppDataState.Error && medicalData is AppDataState.Error) {
            return AppDataState.Error
        }
        return AppDataState.Loading
    }

    fun loadAppInfo(packageName: String) {
        viewModelScope.launch { _appInfo.postValue(appInfoReader.getAppMetadata(packageName)) }
    }

    sealed class AppDataState {
        object Loading : AppDataState()

        object Error : AppDataState()

        data class WithData(val dataMap: List<PermissionTypesPerCategory>) : AppDataState()
    }
}
