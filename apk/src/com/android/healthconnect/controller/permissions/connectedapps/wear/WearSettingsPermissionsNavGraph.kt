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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Wear Settings Permissions navigation graph.
 *
 * TODO: b/364643019 - Per data type screen, Remove one health permission for all apps screen,
 *   Control single health permission for one app screen, Control background health read permission
 *   screen.
 */
@Composable
fun WearSettingsPermissionsNavGraph() {
    val viewModel = hiltViewModel<WearConnectedAppsViewModel>()
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = PermissionManagerScreen.Vitals.name) {
        composable(route = PermissionManagerScreen.Vitals.name) { AllDataTypesScreen(viewModel) }
    }
}

enum class PermissionManagerScreen() {
    Vitals
}
