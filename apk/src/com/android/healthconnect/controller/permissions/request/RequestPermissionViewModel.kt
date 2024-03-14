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

package com.android.healthconnect.controller.permissions.request

import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** View model for {@link PermissionsFragment} . */
@HiltViewModel
class RequestPermissionViewModel
@Inject
constructor(
    private val appInfoReader: AppInfoReader,
    private val grantHealthPermissionUseCase: GrantHealthPermissionUseCase,
    private val revokeHealthPermissionUseCase: RevokeHealthPermissionUseCase,
    private val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase,
    private val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase,
    private val healthPermissionReader: HealthPermissionReader
) : ViewModel() {

    companion object {
        private const val TAG = "RequestPermissionViewMo"
    }

    private val _appMetaData = MutableLiveData<AppMetadata>()
    val appMetadata: LiveData<AppMetadata>
        get() = _appMetaData

    private val _permissionsList = MutableLiveData<List<HealthPermission>>()
    val permissionsList: LiveData<List<HealthPermission>>
        get() = _permissionsList

    private val _grantedPermissions = MutableLiveData<Set<HealthPermission>>(emptySet())
    val grantedPermissions: LiveData<Set<HealthPermission>>
        get() = _grantedPermissions

    private val _allPermissionsGranted =
        MediatorLiveData(false).apply {
            addSource(_permissionsList) {
                postValue(isAllPermissionsGranted(permissionsList, grantedPermissions))
            }
            addSource(_grantedPermissions) {
                postValue(isAllPermissionsGranted(permissionsList, grantedPermissions))
            }
        }
    val allPermissionsGranted: LiveData<Boolean>
        get() = _allPermissionsGranted

    /** Retains the originally requested permissions and their state. */
    private var requestedPermissions: MutableMap<HealthPermission, PermissionState> = mutableMapOf()

    private fun isAllPermissionsGranted(
        permissionsListLiveData: LiveData<List<HealthPermission>>,
        grantedPermissionsLiveData: LiveData<Set<HealthPermission>>
    ): Boolean {
        val permissionsList = permissionsListLiveData.value.orEmpty()
        val grantedPermissions = grantedPermissionsLiveData.value.orEmpty()
        return if (permissionsList.isEmpty() || grantedPermissions.isEmpty()) {
            false
        } else {
            permissionsList.size == grantedPermissions.size
        }
    }

    fun init(packageName: String, permissions: Array<out String>) {
        loadAppInfo(packageName)
        loadPermissions(packageName, permissions)
    }

    /** Whether the user has enabled this permission in the Permission Request screen. */
    fun isPermissionLocallyGranted(permission: HealthPermission): Boolean {
        return _grantedPermissions.value.orEmpty().contains(permission)
    }

    /** Returns true if any of the requested permissions is USER_FIXED, false otherwise. */
    fun isAnyPermissionUserFixed(packageName: String, permissions: Array<out String>): Boolean {
        return getHealthPermissionsFlagsUseCase.invoke(packageName, permissions.toList()).any {
            (_, flags) ->
            flags.and(PackageManager.FLAG_PERMISSION_USER_FIXED) != 0
        }
    }

    private fun loadPermissions(packageName: String, permissions: Array<out String>) {
        val grantedPermissions = getGrantedHealthPermissionsUseCase.invoke(packageName)

        val filteredPermissions =
            permissions
                // Filter invalid health permissions
                .mapNotNull { permissionString ->
                    try {
                        HealthPermission.fromPermissionString(permissionString)
                    } catch (exception: IllegalArgumentException) {
                        Log.e(TAG, "Unrecognized health exception!", exception)
                        null
                    }
                }
                // Add the requested permissions and their states to requestedPermissions
                .onEach { permission -> addToRequestedPermissions(grantedPermissions, permission) }
                // Finally, filter out the granted permissions
                .filter { permission -> !grantedPermissions.contains(permission.toString()) }
                // TODO (b/295490462) Additional permissions will be requested separately
                .filter { permission ->
                    !healthPermissionReader.isAdditionalPermission(permission.toString())
                }

        _permissionsList.value = filteredPermissions
    }

    // Adds a permission to the requested permissions map with its original granted state
    private fun addToRequestedPermissions(
        grantedPermissions: List<String>,
        permission: HealthPermission
    ) {
        val isPermissionGranted = grantedPermissions.contains(permission.toString())
        if (isPermissionGranted) {
            requestedPermissions[permission] = PermissionState.GRANTED
        } else {
            requestedPermissions[permission] = PermissionState.NOT_GRANTED
        }
    }

    fun updatePermission(permission: HealthPermission, grant: Boolean) {
        val updatedGrantedPermissions = _grantedPermissions.value.orEmpty().toMutableSet()
        if (grant) {
            updatedGrantedPermissions.add(permission)
        } else {
            updatedGrantedPermissions.remove(permission)
        }
        _grantedPermissions.postValue(updatedGrantedPermissions)
    }

    fun updatePermissions(grant: Boolean) {
        if (grant) {
            _grantedPermissions.setValue(_permissionsList.value.orEmpty().toSet())
        } else {
            _grantedPermissions.setValue(emptySet())
        }
    }

    private fun loadAppInfo(packageName: String) {
        viewModelScope.launch { _appMetaData.postValue(appInfoReader.getAppMetadata(packageName)) }
    }

    fun request(packageName: String): MutableMap<HealthPermission, PermissionState> {
        val grants: MutableMap<HealthPermission, PermissionState> = mutableMapOf()
        requestedPermissions.forEach { (permission, permissionState) ->
            val granted =
                isPermissionLocallyGranted(permission) || permissionState == PermissionState.GRANTED
            try {
                if (granted) {
                    grantHealthPermissionUseCase.invoke(packageName, permission.toString())
                    grants[permission] = PermissionState.GRANTED
                } else {
                    revokeHealthPermissionUseCase.invoke(packageName, permission.toString())
                    grants[permission] = PermissionState.NOT_GRANTED
                }
            } catch (e: SecurityException) {
                grants[permission] = PermissionState.NOT_GRANTED
            } catch (e: Exception) {
                grants[permission] = PermissionState.ERROR
            }
        }
        return grants
    }
}
