/**
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.healthconnect.controller.permissiontypes

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissiontypes.api.FilterPermissionTypesUseCase
import com.android.healthconnect.controller.permissiontypes.api.LoadContributingAppsUseCase
import com.android.healthconnect.controller.permissiontypes.api.LoadPermissionTypesUseCase
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.app.AppMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
class HealthPermissionTypesViewModel
@Inject
constructor(
    private val loadPermissionTypesUseCase: LoadPermissionTypesUseCase,
    private val loadContributingAppsUseCase: LoadContributingAppsUseCase,
    private val filterPermissionTypesUseCase: FilterPermissionTypesUseCase
) : ViewModel() {

    private val _permissionTypesData = MutableLiveData<PermissionTypesState>()
    private val _appsWithData = MutableLiveData<AppsWithDataFragmentState>()
    private val _selectedAppFilter = MutableLiveData("All apps")

    /** Provides a list of [FitnessPermissionType]s displayed in [HealthPermissionTypesFragment]. */
    val permissionTypesData: LiveData<PermissionTypesState>
        get() = _permissionTypesData

    /** Provides a list of apps with data in [HealthPermissionTypesFragment]. */
    val appsWithData: LiveData<AppsWithDataFragmentState>
        get() = _appsWithData

    /** Stores currently selected app filter. */
    val selectedAppFilter: LiveData<String>
        get() = _selectedAppFilter

    fun setAppFilter(selectedAppFilter: String) {
        _selectedAppFilter.postValue(selectedAppFilter)
    }

    fun loadData(category: @HealthDataCategoryInt Int) {
        _permissionTypesData.postValue(PermissionTypesState.Loading)

        viewModelScope.launch {
            val permissionTypes = loadPermissionTypesUseCase.invoke(category)
            _permissionTypesData.postValue(PermissionTypesState.WithData(permissionTypes))
        }
    }

    fun loadAppsWithData(category: @HealthDataCategoryInt Int) {
        _appsWithData.postValue(AppsWithDataFragmentState.Loading)
        viewModelScope.launch {
            val appsWithHealthPermissions = loadContributingAppsUseCase.invoke(category)
            _appsWithData.postValue(AppsWithDataFragmentState.WithData(appsWithHealthPermissions))
        }
    }

    fun filterPermissionTypes(
        category: @HealthDataCategoryInt Int,
        selectedAppPackageName: String
    ) {
        _permissionTypesData.postValue(PermissionTypesState.Loading)
        viewModelScope.launch {
            val permissionTypes =
                filterPermissionTypesUseCase.invoke(category, selectedAppPackageName)
            if (permissionTypes.isNotEmpty()) {
                _permissionTypesData.postValue(PermissionTypesState.WithData(permissionTypes))
            } else {
                val allPermissionTypes = loadPermissionTypesUseCase.invoke(category)
                _permissionTypesData.postValue(PermissionTypesState.WithData(allPermissionTypes))
            }
        }
    }

    sealed class PermissionTypesState {
        object Loading : PermissionTypesState()

        data class WithData(val permissionTypes: List<HealthPermissionType>) :
            PermissionTypesState()
    }

    sealed class AppsWithDataFragmentState {
        object Loading : AppsWithDataFragmentState()

        data class WithData(val appsWithData: List<AppMetadata>) : AppsWithDataFragmentState()
    }
}
