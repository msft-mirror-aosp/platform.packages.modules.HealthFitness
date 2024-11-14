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
 *
 *
 */
package com.android.healthconnect.controller.permissions.connectedapps.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.Text
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.request.wear.elements.Chip
import com.android.healthconnect.controller.permissions.request.wear.elements.ScrollableScreen

/** Wear Settings Permissions Screen to allow/disallow a single data type permission for an app. */
@Composable
fun ControlSingleDataTypeForSingleAppScreen(
    viewModel: WearConnectedAppsViewModel,
    permissionStr: String,
    dataTypeStr: String,
    packageName: String,
    onAdditionalPermissionClick: (String) -> Unit,
) {
    val healthPermission = fromPermissionString(permissionStr)

    // Get app metadata. PackageName is passed from allowed/denied apps page and must be in the
    // connectedApps list, thus it's safe to have nonnull!! assert.
    val appMetadata by viewModel.getAppMetadataByPackageName(packageName).collectAsState()

    val dataTypeToAllowedApps by viewModel.dataTypeToAllowedApps.collectAsState()
    // Whether this data type permission is allowed (foreground).
    var allowed by remember { mutableStateOf(true) }
    allowed = dataTypeToAllowedApps[healthPermission]?.any { it.packageName == packageName } == true

    // Background permission status.
    val backgroundReadStatus by viewModel.appToBackgroundReadStatus.collectAsState()
    val isBackgroundPermissionRequested = appMetadata!! in backgroundReadStatus

    ScrollableScreen(showTimeText = false, title = appMetadata!!.appName) {
        // Data type text.
        item { Row(horizontalArrangement = Arrangement.Start) { Text(dataTypeStr) } }

        // "Allowed" radio button.
        item {
            RadioButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                selected = allowed,
                onSelect = {
                    viewModel.updatePermission(healthPermission, appMetadata!!, grant = true)
                    allowed = true
                },
                label = { Text(stringResource(R.string.allowed)) },
            )
        }

        // "Don't allow" radio button.
        item {
            RadioButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                selected = !allowed,
                onSelect = {
                    viewModel.updatePermission(healthPermission, appMetadata!!, grant = false)
                    allowed = false
                },
                label = { Text(stringResource(R.string.request_permissions_dont_allow)) },
            )
        }

        // Button to allow/disallow background permission.
        item {
            Chip(
                label = stringResource(R.string.additional_access_label),
                labelMaxLines = 3,
                onClick = { onAdditionalPermissionClick(packageName) },
                enabled = isBackgroundPermissionRequested,
            )
        }

        // Allow mode text.
        item {
            val message =
                if (backgroundReadStatus[appMetadata!!] == true) {
                    stringResource(R.string.view_permissions_all_the_time)
                } else {
                    stringResource(R.string.view_permissions_while_in_use)
                }
            Row(horizontalArrangement = Arrangement.Start) {
                Text(stringResource(R.string.current_allow_mode, appMetadata!!.appName, message))
            }
        }
    }
}
