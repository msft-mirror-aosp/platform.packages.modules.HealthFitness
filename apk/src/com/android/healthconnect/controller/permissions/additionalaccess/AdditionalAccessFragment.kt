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

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.health.connect.HealthPermissions
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.activityViewModels
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ALWAYS_ALLOW
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.ASK_EVERY_TIME
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState.NOT_DECLARED
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.permissions.data.AdditionalPermissionStrings
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.HealthSwitchPreference
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement.EXERCISE_ROUTES_BUTTON
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.pref
import com.android.settingslib.widget.AppHeaderPreference
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment that contains additional app permission access. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class AdditionalAccessFragment : Hilt_AdditionalAccessFragment() {

    @Inject lateinit var navigationUtils: NavigationUtils

    private val permissionsViewModel: AppPermissionViewModel by activityViewModels()
    private val viewModel: AdditionalAccessViewModel by activityViewModels()

    private val header: AppHeaderPreference by pref(PREF_APP_HEADER)
    private val exerciseRoutePref: HealthPreference by pref(KEY_EXERCISE_ROUTES_PERMISSION)
    private val historicReadPref: HealthSwitchPreference by pref(KEY_HISTORY_READ_PERMISSION)
    private val backgroundReadPref: HealthSwitchPreference by pref(KEY_BACKGROUND_READ_PERMISSION)
    private val footerPref: FooterPreference by pref(KEY_FOOTER)

    private val dateFormatter by lazy { LocalDateTimeFormatter(requireContext()) }

    lateinit var packageName: String

    init {
        setPageName(PageName.ADDITIONAL_ACCESS_PAGE)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.additional_access_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val packageNameExtra = requireArguments().getString(EXTRA_PACKAGE_NAME)
        if (packageNameExtra.isNullOrEmpty()) {
            Log.e(TAG, "AdditionalAccessFragment is missing $EXTRA_PACKAGE_NAME intent!")
            requireActivity().finish()
            return
        }
        packageName = packageNameExtra

        viewModel.additionalAccessState.observe(viewLifecycleOwner) { state ->
            setupAdditionalPrefs(state)
        }

        viewModel.showEnableExerciseEvent.observe(viewLifecycleOwner) { state ->
            if (state.shouldShowDialog) {
                EnableExercisePermissionDialog.createDialog(packageName, state.appName)
                    .show(childFragmentManager, ENABLE_EXERCISE_DIALOG_TAG)
            }
        }

        permissionsViewModel.appInfo.observe(viewLifecycleOwner) { appMetaData ->
            header.apply {
                icon = appMetaData.icon
                title = appMetaData.appName
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAdditionalAccessPreferences(packageName)
    }

    private fun setupExerciseRoutePref(state: PermissionUiState) {
        exerciseRoutePref.isVisible = state != NOT_DECLARED
        if (state == NOT_DECLARED) {
            return
        }
        exerciseRoutePref.apply {
            logName = EXERCISE_ROUTES_BUTTON
            exerciseRoutePref.setSummary(
                when (state) {
                    ASK_EVERY_TIME -> R.string.route_permissions_ask
                    ALWAYS_ALLOW -> R.string.route_permissions_always_allow
                    else -> R.string.route_permissions_deny
                })
            exerciseRoutePref.setOnPreferenceClickListener {
                val dialog = ExerciseRoutesPermissionDialogFragment.createDialog(packageName)
                dialog.show(childFragmentManager, EXERCISE_ROUTES_DIALOG_TAG)
                true
            }
        }
    }

    private fun maybeShowFooter(state: AdditionalAccessViewModel.State) {
        val shouldShow = state.showFooter()

        if (!shouldShow) {
            footerPref.isVisible = false
            return
        }

        val title =
            if (state.isAdditionalPermissionDisabled(state.historyReadUIState) &&
                state.isAdditionalPermissionDisabled(state.backgroundReadUIState)) {
                R.string.additional_access_combined_footer
            } else if (state.isAdditionalPermissionDisabled(state.backgroundReadUIState)) {
                R.string.additional_access_background_footer
            } else {
                R.string.additional_access_history_footer
            }

        footerPref.title = getString(title)
        footerPref.isVisible = true
    }

    private fun setupAdditionalPrefs(state: AdditionalAccessViewModel.State) {
        setupExerciseRoutePref(state.exerciseRoutePermissionUIState)
        maybeShowFooter(state)

        if (state.historyReadUIState.isDeclared) {
            val permStrings =
                AdditionalPermissionStrings.fromAdditionalPermission(
                    HealthPermission.AdditionalPermission.READ_HEALTH_DATA_HISTORY)

            val dataAccessDate = viewModel.loadAccessDate(packageName)
            val summary =
                if (dataAccessDate != null) {
                    val formattedDate = dateFormatter.formatLongDate(dataAccessDate)
                    getString(permStrings.permissionDescription, formattedDate)
                } else {
                    getString(permStrings.permissionDescriptionFallback)
                }

            historicReadPref.isVisible = true
            historicReadPref.isChecked = state.historyReadUIState.isGranted
            historicReadPref.isEnabled = state.historyReadUIState.isEnabled
            historicReadPref.summary = summary
            historicReadPref.setOnPreferenceChangeListener { _, newValue ->
                viewModel.updatePermission(
                    packageName, HealthPermissions.READ_HEALTH_DATA_HISTORY, newValue as Boolean)
                true
            }
        }

        if (state.backgroundReadUIState.isDeclared) {
            backgroundReadPref.isVisible = true
            backgroundReadPref.isChecked = state.backgroundReadUIState.isGranted
            backgroundReadPref.isEnabled = state.backgroundReadUIState.isEnabled
            backgroundReadPref.setOnPreferenceChangeListener { _, newValue ->
                viewModel.updatePermission(
                    packageName,
                    HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                    newValue as Boolean)
                true
            }
        }
    }

    companion object {
        private const val TAG = "AdditionalAccessFragmen"
        private const val PREF_APP_HEADER = "manage_app_permission_header"
        private const val KEY_EXERCISE_ROUTES_PERMISSION = "key_exercise_routes_permission"
        private const val EXERCISE_ROUTES_DIALOG_TAG = "ExerciseRoutesPermissionDialogFragment"
        private const val ENABLE_EXERCISE_DIALOG_TAG = "EnableExercisePermissionDialog"
        private const val KEY_BACKGROUND_READ_PERMISSION = "key_background_read"
        private const val KEY_HISTORY_READ_PERMISSION = "key_history_read"
        private const val KEY_FOOTER = "key_additional_access_footer"
    }
}
