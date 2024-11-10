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
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/** View model for the [AppDataFragment] . */
@HiltViewModel
class AppDataViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val loadAllDataUseCase: AllDataUseCase,
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

    private val _setOfPermissionTypesToBeDeleted = MutableLiveData<Set<HealthPermissionType>>()

    val setOfPermissionTypesToBeDeleted: LiveData<Set<HealthPermissionType>>
        get() = _setOfPermissionTypesToBeDeleted

    private var appDataDeletionScreenState: AppDataDeletionScreenState =
        AppDataDeletionScreenState.VIEW

    private var numOfPermissionTypes: Int = 0

    private val _allPermissionTypesSelected = MutableLiveData<Boolean>()

    val allPermissionTypesSelected: LiveData<Boolean>
        get() = _allPermissionTypesSelected

    fun loadAppData(packageName: String) {
        _appFitnessData.postValue(AppDataState.Loading)
        _appMedicalData.postValue(AppDataState.Loading)
        numOfPermissionTypes = 0
        viewModelScope.launch {
            val fitnessData = async { loadAllDataUseCase.loadFitnessAppData(packageName) }
            val medicalData = async { loadAllDataUseCase.loadMedicalAppData(packageName) }

            handleResult(fitnessData.await(), _appFitnessData)
            handleResult(medicalData.await(), _appMedicalData)
        }
    }

    private fun handleResult(
        result: UseCaseResults<List<PermissionTypesPerCategory>>,
        liveData: MutableLiveData<AppDataState>,
    ) {
        when (result) {
            is UseCaseResults.Success -> {
                liveData.postValue(AppDataState.WithData(result.data))
                numOfPermissionTypes += result.data.sumOf { it.data.size }
            }
            is UseCaseResults.Failed -> liveData.postValue(AppDataState.Error)
        }
    }

    private fun getCombinedAppData(
        appFitnessData: LiveData<AppDataState>,
        appMedicalData: LiveData<AppDataState>,
    ): AppDataState {
        val fitnessData = appFitnessData.value ?: AppDataState.Loading
        val medicalData = appMedicalData.value ?: AppDataState.Loading

        return when {
            fitnessData is AppDataState.WithData && medicalData is AppDataState.WithData ->
                AppDataState.WithData(fitnessData.dataMap + medicalData.dataMap)
            fitnessData is AppDataState.WithData -> fitnessData
            medicalData is AppDataState.WithData -> medicalData
            fitnessData is AppDataState.Error && medicalData is AppDataState.Error ->
                AppDataState.Error
            else -> AppDataState.Loading
        }
    }

    fun loadAppInfo(packageName: String) {
        viewModelScope.launch { _appInfo.postValue(appInfoReader.getAppMetadata(packageName)) }
    }

    fun resetDeletionSet() {
        _setOfPermissionTypesToBeDeleted.value = emptySet()
    }

    fun addToDeletionSet(permissionType: HealthPermissionType) {
        val deleteSet = _setOfPermissionTypesToBeDeleted.value.orEmpty().toMutableSet()
        deleteSet.add(permissionType)
        _setOfPermissionTypesToBeDeleted.value = deleteSet.toSet()
        if (numOfPermissionTypes == deleteSet.size) {
            _allPermissionTypesSelected.postValue(true)
        }
    }

    fun removeFromDeletionSet(permissionType: HealthPermissionType) {
        val deleteSet = _setOfPermissionTypesToBeDeleted.value.orEmpty().toMutableSet()
        deleteSet.remove(permissionType)
        _setOfPermissionTypesToBeDeleted.value = deleteSet.toSet()
        if (numOfPermissionTypes != deleteSet.size) {
            _allPermissionTypesSelected.postValue(false)
        }
    }

    fun setDeletionState(appDataDeletionScreenState: AppDataDeletionScreenState) {
        this.appDataDeletionScreenState = appDataDeletionScreenState
        if (this.appDataDeletionScreenState == AppDataDeletionScreenState.VIEW) {
            resetDeletionSet()
        }
    }

    fun getDeletionState(): AppDataDeletionScreenState {
        return appDataDeletionScreenState
    }

    fun getNumOfPermissionTypes(): Int = numOfPermissionTypes

    enum class AppDataDeletionScreenState {
        VIEW,
        DELETE,
    }

    sealed class AppDataState {
        object Loading : AppDataState()

        object Error : AppDataState()

        data class WithData(val dataMap: List<PermissionTypesPerCategory>) : AppDataState()
    }
}
