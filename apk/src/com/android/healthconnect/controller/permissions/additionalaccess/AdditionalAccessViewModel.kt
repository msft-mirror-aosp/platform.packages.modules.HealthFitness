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

import android.health.connect.HealthPermissions
import android.health.connect.HealthPermissions.READ_EXERCISE
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ALWAYS_ALLOW
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ASK_EVERY_TIME
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.NEVER_ALLOW
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.NOT_DECLARED
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.SetHealthPermissionsUserFixedFlagValueUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
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
    private val appInfoReader: AppInfoReader,
    private val loadExerciseRoutePermissionUseCase: LoadExerciseRoutePermissionUseCase,
    private val grantHealthPermissionUseCase: GrantHealthPermissionUseCase,
    private val revokeHealthPermissionUseCase: RevokeHealthPermissionUseCase,
    private val setHealthPermissionsUserFixedFlagValueUseCase:
        SetHealthPermissionsUserFixedFlagValueUseCase,
    private val getAdditionalPermissionUseCase: GetAdditionalPermissionUseCase,
    private val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase,
    private val loadAccessDateUseCase: LoadAccessDateUseCase
) : ViewModel() {

    private val _additionalAccessState = MutableLiveData<State>()
    val additionalAccessState: LiveData<State>
        get() = _additionalAccessState

    private val _showEnableExerciseEvent = MutableLiveData(false)
    private val _appInfo = MutableLiveData<AppMetadata>()

    val showEnableExerciseEvent =
        MediatorLiveData(EnableExerciseDialogEvent()).apply {
            addSource(_showEnableExerciseEvent) {
                postValue(
                    EnableExerciseDialogEvent(
                        shouldShowDialog = _showEnableExerciseEvent.value ?: false,
                        appName = _appInfo.value?.appName ?: ""))
            }
            addSource(_appInfo) {
                postValue(
                    EnableExerciseDialogEvent(
                        shouldShowDialog = _showEnableExerciseEvent.value ?: false,
                        appName = _appInfo.value?.appName ?: ""))
            }
        }

    fun loadAccessDate(packageName: String) = loadAccessDateUseCase.invoke(packageName)

    /** Loads available additional access preferences. */
    fun loadAdditionalAccessPreferences(packageName: String) {
        viewModelScope.launch {
            _appInfo.postValue(appInfoReader.getAppMetadata(packageName))
            var newState = State()
            if (featureUtils.isExerciseRouteReadAllEnabled()) {
                newState =
                    when (val result = loadExerciseRoutePermissionUseCase(packageName)) {
                        is UseCaseResults.Success -> {
                            newState.copy(
                                exerciseRoutePermissionUIState =
                                    result.data.exerciseRoutePermissionState,
                                exercisePermissionUIState = result.data.exercisePermissionState)
                        }
                        else -> {
                            newState.copy(
                                exerciseRoutePermissionUIState = NOT_DECLARED,
                                exercisePermissionUIState = NOT_DECLARED)
                        }
                    }
            }

            val additionalPermissions = getAdditionalPermissionUseCase(packageName)
            val grantedPermissions = getGrantedHealthPermissionsUseCase.invoke(packageName)
            val isAnyReadPermissionGranted =
                grantedPermissions.any { permission -> isDataTypeReadPermission(permission) }
            if (featureUtils.isBackgroundReadEnabled() &&
                additionalPermissions.contains(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)) {
                newState =
                    newState.copy(
                        backgroundReadUIState =
                            AdditionalPermissionState(
                                isDeclared = true,
                                isGranted =
                                    grantedPermissions.contains(
                                        HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND),
                                isEnabled = isAnyReadPermissionGranted))
            }
            if (featureUtils.isHistoryReadEnabled() &&
                additionalPermissions.contains(HealthPermissions.READ_HEALTH_DATA_HISTORY)) {
                newState =
                    newState.copy(
                        historyReadUIState =
                            AdditionalPermissionState(
                                isDeclared = true,
                                isGranted =
                                    grantedPermissions.contains(
                                        HealthPermissions.READ_HEALTH_DATA_HISTORY),
                                isEnabled = isAnyReadPermissionGranted))
            }

            _additionalAccessState.postValue(newState)
        }
    }

    private fun isDataTypeReadPermission(permission: String): Boolean {
        val healthPermission = HealthPermission.fromPermissionString(permission)
        return ((healthPermission is HealthPermission.DataTypePermission) &&
            healthPermission.permissionsAccessType == PermissionsAccessType.READ)
    }

    /** Updates exercise route permission state and refreshes the screen state. */
    fun updateExerciseRouteState(packageName: String, exerciseRouteNewState: PermissionUiState) {
        val screenState = _additionalAccessState.value
        if (screenState == null ||
            screenState.exerciseRoutePermissionUIState == exerciseRouteNewState)
            return
        when (exerciseRouteNewState) {
            ALWAYS_ALLOW -> {
                // apps who are granted [READ_EXERCISE_ROUTES] should also be granted
                // [READ_EXERCISE]
                if (canEnableExercisePermission(screenState)) {
                    _showEnableExerciseEvent.postValue(true)
                } else {
                    grantHealthPermissionUseCase(packageName, READ_EXERCISE_ROUTES)
                }
            }
            ASK_EVERY_TIME -> {
                if (screenState.exerciseRoutePermissionUIState == ALWAYS_ALLOW) {
                    revokeHealthPermissionUseCase(packageName, READ_EXERCISE_ROUTES)
                } else if (screenState.exerciseRoutePermissionUIState == NEVER_ALLOW) {
                    setHealthPermissionsUserFixedFlagValueUseCase(
                        packageName, listOf(READ_EXERCISE_ROUTES), false)
                }
            }
            else -> {
                if (screenState.exerciseRoutePermissionUIState == ALWAYS_ALLOW) {
                    revokeHealthPermissionUseCase(packageName, READ_EXERCISE_ROUTES)
                }
                setHealthPermissionsUserFixedFlagValueUseCase(
                    packageName, listOf(READ_EXERCISE_ROUTES), true)
            }
        }
        // refresh the ui
        loadAdditionalAccessPreferences(packageName)
    }

    private fun canEnableExercisePermission(screenState: State): Boolean {
        return screenState.exercisePermissionUIState == ASK_EVERY_TIME ||
            screenState.exercisePermissionUIState == NEVER_ALLOW
    }

    fun enableExercisePermission(packageName: String) {
        grantHealthPermissionUseCase(packageName, READ_EXERCISE_ROUTES)
        grantHealthPermissionUseCase(packageName, READ_EXERCISE)
    }

    fun hideExercisePermissionRequestDialog() {
        _showEnableExerciseEvent.postValue(false)
    }

    fun updatePermission(packageName: String, permission: String, grant: Boolean) {
        if (grant) {
            grantHealthPermissionUseCase.invoke(packageName, permission)
        } else {
            revokeHealthPermissionUseCase.invoke(packageName, permission)
        }
    }

    /** Holds [AdditionalAccessFragment] UI state. */
    data class State(
        val exerciseRoutePermissionUIState: PermissionUiState = NOT_DECLARED,
        val exercisePermissionUIState: PermissionUiState = NOT_DECLARED,
        val backgroundReadUIState: AdditionalPermissionState = AdditionalPermissionState(),
        val historyReadUIState: AdditionalPermissionState = AdditionalPermissionState()
    ) {

        /**
         * Checks if Additional access screen state is valid and will have options to show in the
         * screen.
         *
         * Used by [SettingsManageAppPermissionsFragment] to decide to show additional access entry
         * point.
         */
        fun isValid(): Boolean {
            return (exerciseRoutePermissionUIState != NOT_DECLARED &&
                exercisePermissionUIState != NOT_DECLARED) ||
                backgroundReadUIState.isDeclared ||
                historyReadUIState.isDeclared
        }

        fun showFooter(): Boolean {
            return isAdditionalPermissionDisabled(backgroundReadUIState) ||
                isAdditionalPermissionDisabled(historyReadUIState)
        }

        fun isAdditionalPermissionDisabled(
            additionalPermissionState: AdditionalPermissionState
        ): Boolean {
            return additionalPermissionState.isDeclared && !additionalPermissionState.isEnabled
        }
    }

    data class AdditionalPermissionState(
        val isDeclared: Boolean = false,
        val isEnabled: Boolean = false,
        val isGranted: Boolean = false
    )

    data class EnableExerciseDialogEvent(
        val shouldShowDialog: Boolean = false,
        val appName: String = ""
    )
}
