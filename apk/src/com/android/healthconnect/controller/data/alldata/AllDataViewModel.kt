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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.data.appdata.AppDataUseCase
import com.android.healthconnect.controller.data.appdata.PermissionTypesPerCategory
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for the [AllDataFragment] . */
@HiltViewModel
class AllDataViewModel
@Inject
constructor(
    private val loadAppDataUseCase: AppDataUseCase,
) : ViewModel() {

    companion object {
        private const val TAG = "AllDataViewModel"
    }

    private val _allData = MutableLiveData<AllDataState>()

    private var setOfPermissionTypesToBeDeleted: MutableSet<FitnessPermissionType> = mutableSetOf()

    private var isDeletionState: Boolean = false

    /** Provides a list of [PermissionTypesPerCategory]s to be displayed in [AllDataFragment]. */
    val allData: LiveData<AllDataState>
        get() = _allData

    fun loadAllFitnessData() {
        _allData.postValue(AllDataState.Loading)
        viewModelScope.launch {
            when (val result = loadAppDataUseCase.loadAllFitnessData()) {
                is UseCaseResults.Success -> {
                    _allData.postValue(AllDataState.WithData(result.data))
                }
                is UseCaseResults.Failed -> {
                    _allData.postValue(AllDataState.Error)
                }
            }
        }
    }

    fun resetDeleteSet() {
        setOfPermissionTypesToBeDeleted.clear()
    }

    fun addToDeleteSet(permissionType: FitnessPermissionType) {
        setOfPermissionTypesToBeDeleted.add(permissionType)
    }

    fun removeFromDeleteSet(permissionType: FitnessPermissionType) {
        setOfPermissionTypesToBeDeleted.remove(permissionType)
    }

    fun getDeleteSet(): Set<FitnessPermissionType> {
        return setOfPermissionTypesToBeDeleted.toSet()
    }

    fun setDeletionState(boolean: Boolean) {
        isDeletionState = boolean
        if (!isDeletionState) {
            setOfPermissionTypesToBeDeleted.clear()
        }
    }

    fun getDeletionState(): Boolean {
        return isDeletionState
    }

    sealed class AllDataState {
        object Loading : AllDataState()

        object Error : AllDataState()

        data class WithData(val dataMap: List<PermissionTypesPerCategory>) : AllDataState()
    }
}
