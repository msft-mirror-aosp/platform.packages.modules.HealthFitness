/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.healthconnect.controller.utils

import android.content.pm.PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED
import android.content.Intent
import android.health.connect.HealthPermissions
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthfitness.flags.Flags
import javax.inject.Inject
import javax.inject.Singleton

/** Utils for permission related operations. */
@Singleton
class PermissionUtils @Inject constructor(
    private val healthPermissionReader: HealthPermissionReader,
    private val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase) {

    /**
     * Returns whether the app is considered a "split-permission" app (i.e. an
     * app that is only using health permissions as a result of a
     * split-permission auto-migration of the legacy body-sensor permission).
     */
    public fun isBodySensorSplitPermissionApp(packageName: String): Boolean {
        if (!Flags.replaceBodySensorPermissionEnabled()) {
            return false
        }

        val declaredPermissions =
            healthPermissionReader.getDeclaredHealthPermissions(packageName)
        // Split permission only applies to READ_HEART_RATE.
        if (!declaredPermissions.contains(HealthPermissions.READ_HEART_RATE)) {
            return false
        }

        // If there are other health permissions (other than READ_HEALTH_DATA_IN_BACKGROUND)
        // don't consider this a pure split-permission request.
        if (declaredPermissions.size > 2) {
            return false
        }

        val declaresBackgroundPermission =
            declaredPermissions.contains(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)
        // If there are two health permissions declared, make sure the other is
        // READ_HEALTH_DATA_IN_BACKGROUND.
        if (declaredPermissions.size == 2 && !declaresBackgroundPermission) {
            return false
        }

        val permissionsToCheck =
            if (declaresBackgroundPermission) {
                listOf(
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                )
            } else {
                listOf(HealthPermissions.READ_HEART_RATE)
            }

        // Check the READ_HEART_RATE permission flag to see if it's a split-permission.
        val permissionToFlags =
            getHealthPermissionsFlagsUseCase(packageName, permissionsToCheck)

        if (declaresBackgroundPermission) {
            // READ_HEALTH_DATA_IN_BACKGROUND is not due to split-permission.
            if (
                permissionToFlags.get(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)?.let { flags
                    ->
                    (flags and FLAG_PERMISSION_REVOKE_WHEN_REQUESTED) == 0
                } ?: true
            ) {
                return false
            }
        }

        // READ_HEART_RATE is not due to split-permission.
        return permissionToFlags.get(HealthPermissions.READ_HEART_RATE)?.let { flags ->
            (flags and FLAG_PERMISSION_REVOKE_WHEN_REQUESTED) != 0
        } ?: false
    }
}
