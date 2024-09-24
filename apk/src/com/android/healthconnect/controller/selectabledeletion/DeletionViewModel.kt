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
package com.android.healthconnect.controller.selectabledeletion

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.selectabledeletion.DeletionType.DeleteEntries
import com.android.healthconnect.controller.selectabledeletion.DeletionType.DeleteHealthPermissionTypes
import com.android.healthconnect.controller.selectabledeletion.api.DeleteEntriesUseCase
import com.android.healthconnect.controller.selectabledeletion.api.DeletePermissionTypesFromAppUseCase
import com.android.healthconnect.controller.selectabledeletion.api.DeletePermissionTypesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DeletionViewModel
@Inject
constructor(
    private val deletePermissionTypesUseCase: DeletePermissionTypesUseCase,
    private val deleteEntriesUseCase: DeleteEntriesUseCase,
    private val deletesPermissionTypesFromAppUseCase: DeletePermissionTypesFromAppUseCase,
) : ViewModel() {

    companion object {
        private const val TAG = "DeletionViewModel"
    }

    private lateinit var deletionType: DeletionType

    private var _permissionTypesReloadNeeded = MutableLiveData(false)

    private var _appPermissionTypesReloadNeeded = MutableLiveData(false)

    private var _entriesReloadNeeded = MutableLiveData(false)

    private var _appEntriesReloadNeeded = MutableLiveData(false)

    private var _deletionProgress = MutableLiveData(DeletionProgress.NOT_STARTED)

    val deletionProgress: LiveData<DeletionProgress>
        get() = _deletionProgress

    val permissionTypesReloadNeeded: LiveData<Boolean>
        get() = _permissionTypesReloadNeeded

    val entriesReloadNeeded: LiveData<Boolean>
        get() = _entriesReloadNeeded

    val appPermissionTypesReloadNeeded: LiveData<Boolean>
        get() = _appPermissionTypesReloadNeeded

    val appEntriesReloadNeeded: LiveData<Boolean>
        get() = _appEntriesReloadNeeded

    fun delete() {
        viewModelScope.launch {
            _deletionProgress.value = (DeletionProgress.STARTED)

            try {
                _deletionProgress.value = (DeletionProgress.PROGRESS_INDICATOR_CAN_START)

                when (deletionType) {
                    is DeleteHealthPermissionTypes -> {
                        deletePermissionTypesUseCase.invoke(
                            deletionType as DeleteHealthPermissionTypes
                        )
                        _permissionTypesReloadNeeded.postValue(true)
                    }
                    is DeleteEntries -> {
                        deleteEntriesUseCase.invoke(deletionType as DeleteEntries)
                        _entriesReloadNeeded.postValue(true)
                    }
                    is DeletionType.DeleteHealthPermissionTypesFromApp -> {
                        deletesPermissionTypesFromAppUseCase.invoke(
                            deletionType as DeletionType.DeleteHealthPermissionTypesFromApp
                        )
                        _appPermissionTypesReloadNeeded.postValue(true)
                    }
                    is DeletionType.DeleteEntriesFromApp -> {
                        deleteEntriesUseCase.invoke(
                            (deletionType as DeletionType.DeleteEntriesFromApp).toDeleteEntries()
                        )
                        _appEntriesReloadNeeded.postValue(true)
                    }
                    else -> {
                        // do nothing
                    }
                }

                _deletionProgress.value = (DeletionProgress.COMPLETED)
            } catch (error: Exception) {
                Log.e(TAG, "Failed to delete data", error)

                _deletionProgress.value = (DeletionProgress.FAILED)
            } finally {
                _deletionProgress.value = (DeletionProgress.PROGRESS_INDICATOR_CAN_END)
            }
        }
    }

    fun resetPermissionTypesReloadNeeded() {
        _permissionTypesReloadNeeded.postValue(false)
    }

    fun resetEntriesReloadNeeded() {
        _entriesReloadNeeded.postValue(false)
    }

    fun setDeletionType(deletionType: DeletionType) {
        this.deletionType = deletionType
    }

    fun resetAppPermissionTypesReloadNeeded() {
        _appPermissionTypesReloadNeeded.postValue(false)
    }

    fun resetAppEntriesReloadNeeded() {
        _appEntriesReloadNeeded.postValue(false)
    }

    fun getDeletionType(): DeletionType {
        return deletionType
    }

    enum class DeletionProgress {
        NOT_STARTED,
        STARTED,
        PROGRESS_INDICATOR_CAN_START,
        PROGRESS_INDICATOR_CAN_END,
        COMPLETED,
        FAILED,
    }
}
