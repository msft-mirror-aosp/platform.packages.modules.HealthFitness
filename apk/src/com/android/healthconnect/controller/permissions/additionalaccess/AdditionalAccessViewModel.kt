/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.permissions.additionalaccess

import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState.DECLARED
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState.GRANTED
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState.NOT_DECLARED
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState.REVOKED
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.MakeHealthPermissionsRequestableUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.utils.FeatureUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for [AdditionalAccessFragment]. */
@HiltViewModel
class AdditionalAccessViewModel
@Inject
constructor(
    private val featureUtils: FeatureUtils,
    private val loadExerciseRoutePermissionUseCase: LoadExerciseRoutePermissionUseCase,
    private val grantHealthPermissionUseCase: GrantHealthPermissionUseCase,
    private val revokeHealthPermissionUseCase: RevokeHealthPermissionUseCase,
    private val makeHealthPermissionsRequestableUseCase: MakeHealthPermissionsRequestableUseCase
) : ViewModel() {

    private val _additionalAccessState = MutableLiveData(State())
    val additionalAccessState: LiveData<State>
        get() = _additionalAccessState

    /** Loads available additional access preferences. */
    fun loadAdditionalAccessPreferences(packageName: String) {
        viewModelScope.launch {
            var newState = State()
            if (featureUtils.isExerciseRouteReadAllEnabled()) {
                newState =
                    when (val result = loadExerciseRoutePermissionUseCase(packageName)) {
                        is UseCaseResults.Success -> {
                            newState.copy(exerciseRouteState = result.data)
                        }
                        else -> {
                            newState.copy(exerciseRouteState = NOT_DECLARED)
                        }
                    }
            }
            _additionalAccessState.postValue(newState)
        }
    }

    /** Updates exercise route permission state and refreshes the screen state. */
    fun updateExerciseRouteState(packageName: String, newState: ExerciseRouteState) {
        val currentState = _additionalAccessState.value?.exerciseRouteState
        if (currentState == newState) return
        when (newState) {
            GRANTED -> grantHealthPermissionUseCase(packageName, READ_EXERCISE_ROUTES)
            DECLARED -> {
                if (currentState == GRANTED) {
                    revokeHealthPermissionUseCase(packageName, READ_EXERCISE_ROUTES)
                } else if (currentState == REVOKED) {
                    makeHealthPermissionsRequestableUseCase(
                        packageName, listOf(READ_EXERCISE_ROUTES))
                }
            }
            else -> {
                // TODO(b/325252727) Remove this check after adding an api to set permission flags.
                if (currentState == GRANTED) {
                    revokeHealthPermissionUseCase(packageName, READ_EXERCISE_ROUTES)
                }
                revokeHealthPermissionUseCase(packageName, READ_EXERCISE_ROUTES)
            }
        }
        // refresh the ui
        loadAdditionalAccessPreferences(packageName)
    }

    /** Holds [AdditionalAccessFragment] UI state. */
    data class State(val exerciseRouteState: ExerciseRouteState = NOT_DECLARED) {

        /**
         * Checks if Additional access screen state is valid and will have options to show in the
         * screen.
         *
         * Used by [SettingsManageAppPermissionsFragment] to decide to show additional access entry
         * point.
         */
        fun isValid(): Boolean {
            return exerciseRouteState != NOT_DECLARED
        }
    }
}
