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
package com.android.healthconnect.controller.permissions.request.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.expandableButton
import androidx.wear.compose.foundation.expandableItems
import androidx.wear.compose.foundation.rememberExpandableState
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.FitnessPermissionStrings
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.request.RequestPermissionViewModel
import com.android.healthconnect.controller.permissions.request.wear.elements.Chip
import com.android.healthconnect.controller.permissions.request.wear.elements.ScrollableScreen
import com.android.healthconnect.controller.shared.app.AppMetadata

/**
 * Wear Grant Permissions Screen. This screen includes: grant single health permission, grant
 * multiple health permission, grant background health permission.
 *
 * TODO: b/364643447 - Grant background health permission.
 * TODO: b/376514553 - Write tests for Wear UI.
 */
@Composable
fun WearGrantPermissionsScreen(viewModel: RequestPermissionViewModel, onButtonClicked: () -> Unit) {
  val appMetadata: State<AppMetadata?> = viewModel.appMetadata.observeAsState(null)
  val appName = appMetadata.value?.appName ?: ""
  val fitnessPermissions = viewModel.fitnessPermissionsList.observeAsState(emptyList())
  val dataTypes =
    fitnessPermissions.value.map { permission ->
      stringResource(
        FitnessPermissionStrings.fromPermissionType(permission.fitnessPermissionType).uppercaseLabel
      )
    }

  if (dataTypes.size > 1) {
    GrantMultipleFitnessPermissions(
      fitnessPermissions,
      appName,
      dataTypes,
      onButtonClicked,
      viewModel,
    )
  } else if (dataTypes.size == 1) {
    GrantSingleFitnessPermission(appName, dataTypes[0], onButtonClicked, viewModel)
  }
}

@Composable
fun GrantMultipleFitnessPermissions(
  fitnessPermissions: State<List<FitnessPermission>>,
  appName: String,
  dataTypes: List<String>,
  onButtonClicked: () -> Unit,
  viewModel: RequestPermissionViewModel,
) {
  val res = LocalContext.current.resources
  // Represents whether user has toggled-on a granular data type permission, by default toggled.
  val checkedStates =
    remember(fitnessPermissions.value) { // Recalculate when fitness permissions change.
      mutableStateListOf(*(fitnessPermissions.value).map { true }.toTypedArray())
    }
  val expandableState = rememberExpandableState()

  ScrollableScreen(
    showTimeText = false,
    title = res.getString(R.string.wear_allow_app_access_fitness_and_wellness_data, appName),
    subtitle =
    res.getString(
      R.string.wear_request_multiple_data_type_permissions,
      appName,
      dataTypes.joinToString(", "),
    ),
  ) {
    // Granular health data types. By default hidden, will show up once user clicks expand button.
    expandableItems(expandableState, dataTypes.size) { index ->
      val dataType = dataTypes[index]
      val isChecked = checkedStates[index]
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        SwitchButton(
          label = { Text(dataType, maxLines = 3, overflow = TextOverflow.Ellipsis) },
          checked = isChecked,
          onCheckedChange = { newCheckedValue ->
            checkedStates[index] = newCheckedValue
            viewModel.updateHealthPermission(
              fitnessPermissions.value[index],
              newCheckedValue as Boolean,
            )
          },
          enabled = true,
        )
      }
    }
    // Buttons.
    // Allow all / Allow selected button.
    item {
      Chip(
        label =
        if (expandableState.expanded) {
            res.getString(R.string.request_permissions_allow_selected)
        } else {
            res.getString(R.string.request_permissions_allow_all)
        },
        onClick = {
          if (!expandableState.expanded) {
            // User hasn't toggle any chip, allow all.
            viewModel.updateFitnessPermissions(true)
          }
          onButtonClicked()
        },
        modifier = Modifier.fillMaxWidth(),
        labelMaxLines = Integer.MAX_VALUE,
      )
    }
    // Deny all button.
    item {
      Chip(
        label = res.getString(R.string.request_permissions_deny_all),
        onClick = {
          checkedStates.fill(false)
          viewModel.updateFitnessPermissions(false)
          onButtonClicked()
        },
        modifier = Modifier.fillMaxWidth(),
        labelMaxLines = Integer.MAX_VALUE,
      )
    }
    // Expand granular control button. User clicks this to control each data type individually.
    expandableButton(expandableState) {
      CompactChip(
        label = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(R.drawable.ic_expand_more_24),
              contentDescription = "Expand more",
            )
          }
        },
        onClick = {
          expandableState.expanded = !expandableState.expanded
          // By default, all the data types are selected when user clicks expand button.
          viewModel.updateFitnessPermissions(true)
        },
        border = ChipDefaults.chipBorder(),
        colors = ChipDefaults.chipColors(backgroundColor = Color.Black, contentColor = Color.White),
        contentPadding = PaddingValues(0.dp), // Remove Chip's default contentPadding
      )
    }
  }
}

@Composable
fun GrantSingleFitnessPermission(
  appName: String,
  dataType: String,
  onButtonClicked: () -> Unit,
  viewModel: RequestPermissionViewModel,
) {
  val res = LocalContext.current.resources
  ScrollableScreen(
    showTimeText = false,
    title = res.getString(R.string.wear_request_single_data_type_permission, appName, dataType),
  ) {
    // Allow button.
    item {
      Chip(
        label = res.getString(R.string.request_permissions_allow),
        onClick = {
          viewModel.updateFitnessPermissions(true)
          onButtonClicked()
        },
        modifier = Modifier.fillMaxWidth(),
        labelMaxLines = Integer.MAX_VALUE,
      )
    }
    // Deny button.
    item {
      Chip(
        label = res.getString(R.string.request_permissions_dont_allow),
        onClick = {
          viewModel.updateFitnessPermissions(false)
          onButtonClicked()
        },
        modifier = Modifier.fillMaxWidth(),
        labelMaxLines = Integer.MAX_VALUE,
      )
    }
  }
}
