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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.request.wear.elements.Chip
import com.android.healthconnect.controller.permissions.request.wear.elements.ScrollableScreen

/** Wear Settings Permissions Screen to see allowed/disallowed status for one app. */
@Composable
fun PerDataTypeScreen(
    viewModel: WearConnectedAppsViewModel,
    permissionStr: String,
    dataTypeStr: String,
) {
    val healthPermission = fromPermissionString(permissionStr)
    ScrollableScreen(showTimeText = false, title = dataTypeStr) {
        // Allowed apps.
        item { AllowedAppsList(viewModel, healthPermission) }

        // Notes on what this permission is about.
        item {
            Row(horizontalArrangement = Arrangement.Start) {
                Text(stringResource(R.string.access_sensor_note, dataTypeStr))
            }
        }

        // Not allowed apps.
        item { DeniedAppsList(viewModel, healthPermission) }
    }
}

@Composable
fun AllowedAppsList(viewModel: WearConnectedAppsViewModel, healthPermission: HealthPermission) {
    val dataTypeToAllowedApps by viewModel.dataTypeToAllowedApps.collectAsState()
    val allowedApps = dataTypeToAllowedApps[healthPermission]
    if (allowedApps?.isNotEmpty() == true) {
        val nApps = allowedApps.size
        Column {
            // Allowed text.
            Text(stringResource(R.string.allowed))

            // A chip for each allowed app for this data type.
            allowedApps.forEach { app ->
                Chip(
                    label = app.appName,
                    labelMaxLines = 3,
                    onClick = {}, // TODO: navigate
                    icon = app.icon,
                    modifier = Modifier.padding(4.dp),
                )
            }

            // Remove access for all apps button.
            Chip(
                label = stringResource(R.string.disconnect_all_apps),
                labelMaxLines = 3,
                onClick = {}, // TODO: navigate
                icon = R.drawable.ic_remove_access_for_all_apps,
            )
        }
    }
}

@Composable
fun DeniedAppsList(viewModel: WearConnectedAppsViewModel, healthPermission: HealthPermission) {
    val dataTypeToDeniedApps by viewModel.dataTypeToDeniedApps.collectAsState()
    val deniedApps = dataTypeToDeniedApps[healthPermission]
    if (deniedApps?.isNotEmpty() == true) {
        val nApps = deniedApps.size
        Column {
            // Not allowed text.
            Text(stringResource(R.string.not_allowed))

            // A chip for each denied app for this data type.
            deniedApps.forEach { app ->
                Chip(
                    label = app.appName,
                    labelMaxLines = 3,
                    onClick = {}, // TODO: navigate
                    icon = app.icon,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}
