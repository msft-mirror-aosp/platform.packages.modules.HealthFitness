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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.FitnessPermissionStrings
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.request.wear.elements.Chip
import com.android.healthconnect.controller.permissions.request.wear.elements.ScrollableScreen

/** Wear Settings Permissions Screen to see allowed/disallowed status for all apps. */
@Composable
fun AllDataTypesScreen(viewModel: WearConnectedAppsViewModel, onClick: (String, String) -> Unit) {
    val connectedApps by viewModel.connectedApps.collectAsState()
    val dataTypeToAllowedApps by viewModel.dataTypeToAllowedApps.collectAsState()
    val dataTypeToDeniedApps by viewModel.dataTypeToDeniedApps.collectAsState()
    val systemHealthPermissions by viewModel.systemHealthPermissions.collectAsState()
    val nTotalApps = connectedApps.size

    ScrollableScreen(showTimeText = false, title = stringResource(R.string.fitness_and_wellness)) {
        item {
            Row(horizontalArrangement = Arrangement.Start) {
                Text(stringResource(R.string.vitals_category_uppercase))
            }
        }

        // Granular data type and the number of apps allowed.
        items(systemHealthPermissions.size) { index ->
            val healthPermission = systemHealthPermissions[index]
            val strDataType =
                stringResource(
                    FitnessPermissionStrings.fromPermissionType(
                            (healthPermission as HealthPermission.FitnessPermission)
                                .fitnessPermissionType
                        )
                        .uppercaseLabel
                )
            val nAllowedApps = dataTypeToAllowedApps[healthPermission]?.size ?: 0
            val nDeniedApps = dataTypeToDeniedApps[healthPermission]?.size ?: 0
            val nRequestedApps = nAllowedApps + nDeniedApps
            val enabled = (nRequestedApps == nTotalApps) && (nTotalApps != 0)
            val message =
                if (enabled) {
                    stringResource(R.string.allowed_apps_count, nAllowedApps, nRequestedApps)
                } else {
                    stringResource(R.string.no_apps_requesting)
                }
            Chip(
                label = strDataType,
                labelMaxLines = 3,
                secondaryLabel = message,
                secondaryLabelMaxLines = 3,
                onClick = { onClick(healthPermission.toString(), strDataType) },
                enabled = enabled,
            )
        }
    }
}
