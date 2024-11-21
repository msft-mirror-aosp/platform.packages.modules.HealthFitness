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
package com.android.healthconnect.controller.data.alldata

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.data.appdata.AppDataUseCase
import com.android.healthconnect.controller.data.appdata.PermissionTypesPerCategory
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.MEDICAL
import com.android.healthconnect.controller.selectabledeletion.DeletionDataViewModel
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for the [AllDataFragment] . */
@HiltViewModel
class AllDataViewModel @Inject constructor(private val loadAppDataUseCase: AppDataUseCase) :
    DeletionDataViewModel() {

    companion object {
        private const val TAG = "AllDataViewModel"
    }

    private val _allData = MutableLiveData<AllDataState>()

    /** Provides a list of [PermissionTypesPerCategory]s to be displayed in [AllDataFragment]. */
    val allData: LiveData<AllDataState>
        get() = _allData

    private val _isAnyMedicalData = MutableLiveData(false)

    /** Provides whether there is any medical data stored in HC. */
    val isAnyMedicalData: LiveData<Boolean>
        get() = _isAnyMedicalData

    fun loadAllFitnessData() {
        _allData.postValue(AllDataState.Loading)
        viewModelScope.launch {
            when (val result = loadAppDataUseCase.loadAllFitnessData()) {
                is UseCaseResults.Success -> {
                    _allData.postValue(AllDataState.WithData(result.data))
                    numOfPermissionTypes = result.data.sumOf { it.data.size }
                }
                is UseCaseResults.Failed -> {
                    _allData.postValue(AllDataState.Error)
                }
            }
        }
    }

    fun loadAllMedicalData() {
        _allData.postValue(AllDataState.Loading)
        _isAnyMedicalData.postValue(false)
        viewModelScope.launch {
            when (val result = loadAppDataUseCase.loadAllMedicalData()) {
                is UseCaseResults.Success -> {
                    _allData.postValue(AllDataState.WithData(result.data))
                    numOfPermissionTypes = result.data.sumOf { it.data.size }
                    _isAnyMedicalData.postValue(isAnyMedicalData(result.data))
                }
                is UseCaseResults.Failed -> {
                    _allData.postValue(AllDataState.Error)
                    _isAnyMedicalData.postValue(false)
                }
            }
        }
    }

    private fun isAnyMedicalData(
        permissionTypesPerCategory: List<PermissionTypesPerCategory>
    ): Boolean {
        return permissionTypesPerCategory
            .filter { it.category == MEDICAL }
            .flatMap { it.data }
            .isNotEmpty()
    }

    sealed class AllDataState {
        data object Loading : AllDataState()

        data object Error : AllDataState()

        data class WithData(val dataMap: List<PermissionTypesPerCategory>) : AllDataState()
    }
}
