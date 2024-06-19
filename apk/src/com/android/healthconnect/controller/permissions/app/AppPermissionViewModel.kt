/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.permissions.app

import android.health.connect.HealthPermissions.READ_EXERCISE
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import android.health.connect.TimeInstantRangeFilter
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.api.DeleteAppDataUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.ILoadExerciseRoutePermissionUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ALWAYS_ALLOW
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.IGetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.permissions.api.RevokeAllHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.data.HealthPermission.MedicalPermission
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.utils.FeatureUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** View model for {@link ConnectedAppFragment} and {SettingsManageAppPermissionsFragment} . */
@HiltViewModel
class AppPermissionViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val loadAppPermissionsStatusUseCase: LoadAppPermissionsStatusUseCase,
    private val grantPermissionsStatusUseCase: GrantHealthPermissionUseCase,
    private val revokePermissionsStatusUseCase: RevokeHealthPermissionUseCase,
    private val revokeAllHealthPermissionsUseCase: RevokeAllHealthPermissionsUseCase,
    private val deleteAppDataUseCase: DeleteAppDataUseCase,
    private val loadAccessDateUseCase: LoadAccessDateUseCase,
    private val loadGrantedHealthPermissionsUseCase: IGetGrantedHealthPermissionsUseCase,
    private val loadExerciseRoutePermissionUseCase: ILoadExerciseRoutePermissionUseCase,
    private val healthPermissionReader: HealthPermissionReader,
    private val featureUtils: FeatureUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    companion object {
        private const val TAG = "AppPermissionViewModel"
    }

    private val _fitnessPermissions = MutableLiveData<List<FitnessPermission>>(emptyList())
    val fitnessPermissions: LiveData<List<FitnessPermission>>
        get() = _fitnessPermissions

    private val _grantedFitnessPermissions = MutableLiveData<Set<FitnessPermission>>(emptySet())
    val grantedFitnessPermissions: LiveData<Set<FitnessPermission>>
        get() = _grantedFitnessPermissions

    val allFitnessPermissionsGranted =
        MediatorLiveData(false).apply {
            addSource(_fitnessPermissions) {
                postValue(
                    isAllFitnessPermissionsGranted(fitnessPermissions, grantedFitnessPermissions))
            }
            addSource(_grantedFitnessPermissions) {
                postValue(
                    isAllFitnessPermissionsGranted(fitnessPermissions, grantedFitnessPermissions))
            }
        }

    val atLeastOneFitnessPermissionGranted =
        MediatorLiveData(false).apply {
            addSource(_grantedFitnessPermissions) { grantedPermissions ->
                postValue(grantedPermissions.isNotEmpty())
            }
        }

    private val _medicalPermissions = MutableLiveData<List<MedicalPermission>>(emptyList())
    val medicalPermissions: LiveData<List<MedicalPermission>>
        get() = _medicalPermissions

    private val _grantedMedicalPermissions = MutableLiveData<Set<MedicalPermission>>(emptySet())
    val grantedMedicalPermissions: LiveData<Set<MedicalPermission>>
        get() = _grantedMedicalPermissions

    val allMedicalPermissionsGranted =
        MediatorLiveData(false).apply {
            addSource(_fitnessPermissions) {
                postValue(
                    isAllMedicalPermissionsGranted(medicalPermissions, grantedMedicalPermissions))
            }
            addSource(_grantedFitnessPermissions) {
                postValue(
                    isAllMedicalPermissionsGranted(medicalPermissions, grantedMedicalPermissions))
            }
        }

    val atLeastOneMedicalPermissionGranted =
        MediatorLiveData(false).apply {
            addSource(_grantedMedicalPermissions) { grantedPermissions ->
                postValue(grantedPermissions.isNotEmpty())
            }
        }

    private val _appInfo = MutableLiveData<AppMetadata>()
    val appInfo: LiveData<AppMetadata>
        get() = _appInfo

    private val _revokeAllHealthPermissionsState =
        MutableLiveData<RevokeAllState>(RevokeAllState.NotStarted)
    val revokeAllHealthPermissionsState: LiveData<RevokeAllState>
        get() = _revokeAllHealthPermissionsState

    private var healthPermissionsList: List<HealthPermissionStatus> = listOf()

    /**
     * Flag to prevent {@link SettingManageAppPermissionsFragment} from reloading the granted
     * permissions on orientation change
     */
    private var shouldLoadGrantedPermissions = true

    private val _showDisableExerciseRouteEvent = MutableLiveData(false)
    val showDisableExerciseRouteEvent =
        MediatorLiveData(DisableExerciseRouteDialogEvent()).apply {
            addSource(_showDisableExerciseRouteEvent) {
                postValue(
                    DisableExerciseRouteDialogEvent(
                        shouldShowDialog = _showDisableExerciseRouteEvent.value ?: false,
                        appName = _appInfo.value?.appName ?: ""))
            }
            addSource(_appInfo) {
                postValue(
                    DisableExerciseRouteDialogEvent(
                        shouldShowDialog = _showDisableExerciseRouteEvent.value ?: false,
                        appName = _appInfo.value?.appName ?: ""))
            }
        }

    private val _lastReadPermissionDisconnected = MutableLiveData(false)
    val lastReadPermissionDisconnected: LiveData<Boolean>
        get() = _lastReadPermissionDisconnected

    private var grantedAdditionalPermissions: List<String> = emptyList()

    fun loadPermissionsForPackage(packageName: String) {
        // clear app permissions
        _fitnessPermissions.postValue(emptyList())
        _grantedFitnessPermissions.postValue(emptySet())
        _medicalPermissions.postValue(emptyList())
        _grantedMedicalPermissions.postValue(emptySet())

        viewModelScope.launch { _appInfo.postValue(appInfoReader.getAppMetadata(packageName)) }
        if (isPackageSupported(packageName)) {
            loadAllPermissions(packageName)
        } else {
            // we only load granted permissions for not supported apps to allow users to revoke
            // these permissions.
            loadGrantedPermissionsForPackage(packageName)
        }
    }

    private fun loadAllPermissions(packageName: String) {
        viewModelScope.launch {
            healthPermissionsList = loadAppPermissionsStatusUseCase.invoke(packageName)
            _fitnessPermissions.postValue(
                healthPermissionsList
                    .map { it.healthPermission }
                    .filterIsInstance<FitnessPermission>())
            _grantedFitnessPermissions.postValue(
                healthPermissionsList
                    .filter { it.isGranted }
                    .map { it.healthPermission }
                    .filterIsInstance<FitnessPermission>()
                    .toSet())
            _medicalPermissions.postValue(
                healthPermissionsList
                    .map { it.healthPermission }
                    .filterIsInstance<MedicalPermission>())
            _grantedMedicalPermissions.postValue(
                healthPermissionsList
                    .filter { it.isGranted }
                    .map { it.healthPermission }
                    .filterIsInstance<MedicalPermission>()
                    .toSet())
            grantedAdditionalPermissions =
                healthPermissionsList
                    .filter { it.isGranted }
                    .map { it.healthPermission }
                    .filterIsInstance<HealthPermission.AdditionalPermission>()
                    .map { it.additionalPermission }
        }
    }

    private fun loadGrantedPermissionsForPackage(packageName: String) {
        // Only reload the status the first time this method is called
        if (shouldLoadGrantedPermissions) {
            viewModelScope.launch {
                val grantedPermissions =
                    loadAppPermissionsStatusUseCase.invoke(packageName).filter { it.isGranted }
                healthPermissionsList = grantedPermissions

                // Only show app permissions that are granted
                _fitnessPermissions.postValue(
                    grantedPermissions
                        .map { it.healthPermission }
                        .filterIsInstance<FitnessPermission>())
                _grantedFitnessPermissions.postValue(
                    grantedPermissions
                        .map { it.healthPermission }
                        .filterIsInstance<FitnessPermission>()
                        .toSet())
                _medicalPermissions.postValue(
                    grantedPermissions
                        .map { it.healthPermission }
                        .filterIsInstance<MedicalPermission>())
                _grantedMedicalPermissions.postValue(
                    grantedPermissions
                        .map { it.healthPermission }
                        .filterIsInstance<MedicalPermission>()
                        .toSet())
            }
            shouldLoadGrantedPermissions = false
        }
    }

    fun loadAccessDate(packageName: String): Instant? {
        return loadAccessDateUseCase.invoke(packageName)
    }

    fun updatePermission(
        packageName: String,
        fitnessPermission: FitnessPermission,
        grant: Boolean
    ): Boolean {
        try {
            if (grant) {
                grantPermission(packageName, fitnessPermission)
            } else {
                if (shouldDisplayExerciseRouteDialog(packageName, fitnessPermission)) {
                    _showDisableExerciseRouteEvent.postValue(true)
                } else {
                    revokePermission(fitnessPermission, packageName)
                }
            }

            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update fitness permission!", ex)
        }
        return false
    }

    fun updatePermission(
        packageName: String,
        medicalPermission: MedicalPermission,
        grant: Boolean
    ): Boolean {
        try {
            if (grant) {
                grantPermission(packageName, medicalPermission)
            } else {
                revokePermission(medicalPermission, packageName)
            }

            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update medical permission!", ex)
        }
        return false
    }

    private fun grantPermission(packageName: String, fitnessPermission: FitnessPermission) {
        val grantedPermissions = _grantedFitnessPermissions.value.orEmpty().toMutableSet()
        grantPermissionsStatusUseCase.invoke(packageName, fitnessPermission.toString())
        grantedPermissions.add(fitnessPermission)
        _grantedFitnessPermissions.postValue(grantedPermissions)
    }

    private fun grantPermission(packageName: String, medicalPermission: MedicalPermission) {
        val grantedPermissions = _grantedMedicalPermissions.value.orEmpty().toMutableSet()
        grantPermissionsStatusUseCase.invoke(packageName, medicalPermission.toString())
        grantedPermissions.add(medicalPermission)
        _grantedMedicalPermissions.postValue(grantedPermissions)
    }

    private fun revokePermission(fitnessPermission: FitnessPermission, packageName: String) {
        val grantedPermissions = _grantedFitnessPermissions.value.orEmpty().toMutableSet()
        val readPermissionsBeforeDisconnect =
            grantedPermissions.count { permission ->
                permission.permissionsAccessType == PermissionsAccessType.READ
            }
        grantedPermissions.remove(fitnessPermission)
        val readPermissionsAfterDisconnect =
            grantedPermissions.count { permission ->
                permission.permissionsAccessType == PermissionsAccessType.READ
            }
        _grantedFitnessPermissions.postValue(grantedPermissions)

        val lastReadPermissionRevoked =
            grantedAdditionalPermissions.isNotEmpty() &&
                (readPermissionsBeforeDisconnect > readPermissionsAfterDisconnect) &&
                readPermissionsAfterDisconnect == 0

        if (lastReadPermissionRevoked) {
            grantedAdditionalPermissions.forEach { permission ->
                revokePermissionsStatusUseCase.invoke(packageName, permission)
            }
        }

        _lastReadPermissionDisconnected.postValue(lastReadPermissionRevoked)
        revokePermissionsStatusUseCase.invoke(packageName, fitnessPermission.toString())
    }

    private fun revokePermission(medicalPermission: MedicalPermission, packageName: String) {
        val grantedPermissions = _grantedMedicalPermissions.value.orEmpty().toMutableSet()
        grantedPermissions.remove(medicalPermission)
        _grantedMedicalPermissions.postValue(grantedPermissions)
        revokePermissionsStatusUseCase.invoke(packageName, medicalPermission.toString())
    }

    fun markLastReadShown() {
        _lastReadPermissionDisconnected.postValue(false)
    }

    private fun shouldDisplayExerciseRouteDialog(
        packageName: String,
        fitnessPermission: FitnessPermission
    ): Boolean {
        if (!featureUtils.isExerciseRouteReadAllEnabled() ||
            fitnessPermission.toString() != READ_EXERCISE) {
            return false
        }

        return isExerciseRoutePermissionAlwaysAllow(packageName)
    }

    fun grantAllFitnessPermissions(packageName: String): Boolean {
        try {
            _fitnessPermissions.value?.forEach {
                grantPermissionsStatusUseCase.invoke(packageName, it.toString())
            }
            val grantedFitnessPermissions =
                _grantedFitnessPermissions.value.orEmpty().toMutableSet()
            grantedFitnessPermissions.addAll(_fitnessPermissions.value.orEmpty())
            _grantedFitnessPermissions.postValue(grantedFitnessPermissions)
            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update fitness permissions!", ex)
        }
        return false
    }

    fun grantAllMedicalPermissions(packageName: String): Boolean {
        try {
            _medicalPermissions.value?.forEach {
                grantPermissionsStatusUseCase.invoke(packageName, it.toString())
            }
            val grantedMedicalPermissions =
                _grantedMedicalPermissions.value.orEmpty().toMutableSet()
            grantedMedicalPermissions.addAll(_medicalPermissions.value.orEmpty())
            _grantedMedicalPermissions.postValue(grantedMedicalPermissions)
            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update medical permissions!", ex)
        }
        return false
    }

    fun disableExerciseRoutePermission(packageName: String) {
        revokePermission(fromPermissionString(READ_EXERCISE), packageName)
        // the revokePermission call will automatically revoke all additional permissions
        // including Exercise Routes if the READ_EXERCISE permission is the last READ permission
        if (isExerciseRoutePermissionAlwaysAllow(packageName)) {
            revokePermissionsStatusUseCase(packageName, READ_EXERCISE_ROUTES)
        }
    }

    private fun isExerciseRoutePermissionAlwaysAllow(packageName: String): Boolean = runBlocking {
        when (val exerciseRouteState = loadExerciseRoutePermissionUseCase(packageName)) {
            is UseCaseResults.Success -> {
                exerciseRouteState.data.exerciseRoutePermissionState == ALWAYS_ALLOW
            }
            else -> false
        }
    }

    // TODO(b/343142873): Update the behavior.
    fun revokeAllHealthPermissions(packageName: String): Boolean {
        // TODO (b/325729045) if there is an error within the coroutine scope
        // it will not be caught by this statement in tests. Consider using LiveData instead
        try {
            viewModelScope.launch(ioDispatcher) {
                _revokeAllHealthPermissionsState.postValue(RevokeAllState.Loading)
                revokeAllHealthPermissionsUseCase.invoke(packageName)
                if (isPackageSupported(packageName)) {
                    loadPermissionsForPackage(packageName)
                }
                _revokeAllHealthPermissionsState.postValue(RevokeAllState.Updated)
                _grantedFitnessPermissions.postValue(emptySet())
                _grantedMedicalPermissions.postValue(emptySet())
                grantedAdditionalPermissions
            }
            return true
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to update permissions!", ex)
        }
        return false
    }

    fun deleteAppData(packageName: String, appName: String) {
        viewModelScope.launch {
            val appData = DeletionType.DeletionTypeAppData(packageName, appName)
            val timeRangeFilter =
                TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.ofEpochMilli(Long.MAX_VALUE))
                    .build()
            deleteAppDataUseCase.invoke(appData, timeRangeFilter)
        }
    }

    fun shouldNavigateToAppPermissionsFragment(packageName: String): Boolean {
        return isPackageSupported(packageName) || hasGrantedPermissions(packageName)
    }

    private fun hasGrantedPermissions(packageName: String): Boolean {
        return loadGrantedHealthPermissionsUseCase(packageName)
            .map { permission -> fromPermissionString(permission) }
            .isNotEmpty()
    }

    private fun isAllFitnessPermissionsGranted(
        permissionsListLiveData: LiveData<List<FitnessPermission>>,
        grantedPermissionsLiveData: LiveData<Set<FitnessPermission>>
    ): Boolean {
        val permissionsList = permissionsListLiveData.value.orEmpty()
        val grantedPermissions = grantedPermissionsLiveData.value.orEmpty()
        return if (permissionsList.isEmpty() || grantedPermissions.isEmpty()) {
            false
        } else {
            permissionsList.size == grantedPermissions.size
        }
    }

    private fun isAllMedicalPermissionsGranted(
        permissionsListLiveData: LiveData<List<MedicalPermission>>,
        grantedPermissionsLiveData: LiveData<Set<MedicalPermission>>
    ): Boolean {
        val permissionsList = permissionsListLiveData.value.orEmpty()
        val grantedPermissions = grantedPermissionsLiveData.value.orEmpty()
        return if (permissionsList.isEmpty() || grantedPermissions.isEmpty()) {
            false
        } else {
            permissionsList.size == grantedPermissions.size
        }
    }

    /** Returns True if the packageName declares the Rationale intent, False otherwise */
    fun isPackageSupported(packageName: String): Boolean {
        return healthPermissionReader.isRationaleIntentDeclared(packageName)
    }

    fun hideExerciseRoutePermissionDialog() {
        _showDisableExerciseRouteEvent.postValue(false)
    }

    sealed class RevokeAllState {
        object NotStarted : RevokeAllState()

        object Loading : RevokeAllState()

        object Updated : RevokeAllState()
    }

    data class DisableExerciseRouteDialogEvent(
        val shouldShowDialog: Boolean = false,
        val appName: String = ""
    )
}
