/**
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.controller.permissions.connectedapps.wear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.app.LoadAppPermissionsStatusUseCase
import com.android.healthconnect.controller.permissions.connectedapps.ILoadHealthPermissionApps
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.AdditionalPermission.Companion.READ_HEALTH_DATA_IN_BACKGROUND
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.collections.MutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class WearConnectedAppsViewModel
@Inject
constructor(
    private val loadHealthPermissionApps: ILoadHealthPermissionApps,
    private val loadAppPermissionsStatusUseCase: LoadAppPermissionsStatusUseCase,
    private val grantPermissionsStatusUseCase: GrantHealthPermissionUseCase,
    private val revokeHealthPermissionUseCase: RevokeHealthPermissionUseCase,
    private val healthPermissionReader: HealthPermissionReader,
) : ViewModel() {

    companion object {
        private const val TAG = "WearConnectedAppsViewModel"
    }

    /** A list of [AppMetadata] of all the apps that requests health permissions. */
    val connectedApps = MutableStateFlow<List<ConnectedAppMetadata>>(emptyList())

    /** Mapping from [HealthPermission] to a list of [AppMetadata] of the allowed apps. */
    val dataTypeToAllowedApps =
        MutableStateFlow<Map<HealthPermission, MutableList<AppMetadata>>>(emptyMap())

    /** Mapping from [HealthPermission] to a list of [AppMetadata] of the denied apps. */
    val dataTypeToDeniedApps =
        MutableStateFlow<Map<HealthPermission, MutableList<AppMetadata>>>(emptyMap())

    /**
     * Mapping from [AppMetadata] of the all connected apps to a boolean representing whether
     * background permission is granted.
     */
    val appToBackgroundReadStatus = MutableStateFlow<Map<AppMetadata, Boolean>>(emptyMap())

    /** A list of [HealthPermission] that are at system level (not restricted to HC-only). */
    val systemHealthPermissions = MutableStateFlow<List<HealthPermission>>(emptyList())

    init {
        loadConnectedApps()
    }

    fun loadConnectedApps() {
        viewModelScope.launch {
            connectedApps.value = loadHealthPermissionApps.invoke()

            // Load system health permissions and granular permission to allowed/denied apps maps.
            val sysHealthPermissions =
                healthPermissionReader.getSystemHealthPermissions().map { perm ->
                    fromPermissionString(perm)
                }
            systemHealthPermissions.value = sysHealthPermissions

            // Init dataTypeToAllowedApps and dataTypeToDeniedApps.
            // For each granular health permission, create a mapping of the allowed and denied apps.
            val allowedAppsMap = mutableMapOf<HealthPermission, MutableList<AppMetadata>>()
            val deniedAppsMap = mutableMapOf<HealthPermission, MutableList<AppMetadata>>()
            val backgroundReadPermissionStatus = mutableMapOf<AppMetadata, Boolean>()
            connectedApps.value.forEach { connectedAppMetadata ->
                val packageName = connectedAppMetadata.appMetadata.packageName
                val healthPermissionStatus = loadAppPermissionsStatusUseCase.invoke(packageName)
                healthPermissionStatus
                    .filter { sysHealthPermissions.contains(it.healthPermission) }
                    .forEach { status ->
                        val permission = status.healthPermission
                        val appList = if (status.isGranted) allowedAppsMap else deniedAppsMap
                        appList
                            .getOrPut(permission) { mutableListOf() }
                            .add(connectedAppMetadata.appMetadata)
                    }
                healthPermissionStatus
                    .firstOrNull { it.healthPermission == READ_HEALTH_DATA_IN_BACKGROUND }
                    ?.let {
                        backgroundReadPermissionStatus[connectedAppMetadata.appMetadata] =
                            it.isGranted
                    }
            }
            dataTypeToAllowedApps.value = allowedAppsMap
            dataTypeToDeniedApps.value = deniedAppsMap
            appToBackgroundReadStatus.value = backgroundReadPermissionStatus
        }
    }

    /** Grant or revoke a specific permission for an app. */
    fun updatePermission(permission: HealthPermission, appMetadata: AppMetadata, grant: Boolean) {
        if (grant) {
            grantPermissionsStatusUseCase.invoke(appMetadata.packageName, permission.toString())
        } else {
            revokeHealthPermissionUseCase.invoke(appMetadata.packageName, permission.toString())
        }

        val mapToAdd =
            if (grant) {
                dataTypeToAllowedApps
            } else {
                dataTypeToDeniedApps
            }
        val mapToRemove =
            if (grant) {
                dataTypeToDeniedApps
            } else {
                dataTypeToAllowedApps
            }
        mapToAdd.value =
            mapToAdd.value.toMutableMap().also {
                it[permission] =
                    (it[permission] ?: mutableListOf()).also { appsList ->
                        if (appsList.none { it.packageName == appMetadata.packageName }) {
                            appsList.add(appMetadata)
                        }
                    }
            }
        mapToRemove.value =
            mapToRemove.value.toMutableMap().also {
                it[permission] =
                    (it[permission] ?: mutableListOf()).also { appsList ->
                        appsList.removeIf { it.packageName == appMetadata.packageName }
                        if (appsList.isEmpty()) {
                            it.remove(permission)
                        }
                    }
            }
    }

    /** Removes all apps from accessing a specific fitness permission. */
    fun removeFitnessPermissionForAllApps(permission: HealthPermission) {
        val permissionStr = permission.toString()
        val deniedAppsMap = dataTypeToDeniedApps.value.toMutableMap()

        dataTypeToAllowedApps.value[permission]?.forEach { appMetadata ->
            revokeHealthPermissionUseCase.invoke(appMetadata.packageName, permissionStr)
            deniedAppsMap.getOrPut(permission) { mutableListOf() }.add(appMetadata)
        }

        dataTypeToAllowedApps.value = dataTypeToAllowedApps.value - permission
        dataTypeToDeniedApps.value = deniedAppsMap
    }

    fun getAppMetadataByPackageName(packageName: String): MutableStateFlow<AppMetadata?> =
        MutableStateFlow<AppMetadata?>(null).also { flow ->
            flow.value =
                connectedApps.value
                    .firstOrNull { it.appMetadata.packageName == packageName }
                    ?.appMetadata
        }
}
