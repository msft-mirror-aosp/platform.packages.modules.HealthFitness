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
package com.android.healthconnect.controller.permissions.app

import android.content.Intent.EXTRA_PACKAGE_NAME
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.maybeShowMigrationDialog
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.*
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.DisableExerciseRoutePermissionDialog
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel.RevokeAllState
import com.android.healthconnect.controller.permissions.data.FitnessPermissionStrings.Companion.fromPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.permissions.shared.DisconnectHealthPermissionsDialogFragment
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.fromFitnessPermissionType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.preference.HealthMainSwitchPreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.HealthSwitchPreference
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.dismissLoadingDialog
import com.android.healthconnect.controller.utils.logging.AppAccessElement.ADDITIONAL_ACCESS_BUTTON
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import com.android.healthconnect.controller.utils.pref
import com.android.healthconnect.controller.utils.showLoadingDialog
import com.android.settingslib.widget.AppHeaderPreference
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment to show granted/revoked [FitnessPermission]s for and app. It is used as an entry point
 * from PermissionController or from [SettingsCombinedPermissionsFragment].
 *
 * For apps that declares health connect permissions without the rational intent, we only show
 * granted permissions to allow the user to revoke this app permissions.
 */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class SettingsFitnessAppFragment : Hilt_SettingsFitnessAppFragment() {

    init {
        setPageName(PageName.MANAGE_PERMISSIONS_PAGE)
    }

    @Inject lateinit var healthPermissionReader: HealthPermissionReader
    @Inject lateinit var navigationUtils: NavigationUtils

    private lateinit var packageName: String
    private var appName: String = ""
    private var showManageAppSection = true

    private val viewModel: AppPermissionViewModel by activityViewModels()
    private val permissionMap: MutableMap<FitnessPermission, TwoStatePreference> = mutableMapOf()
    private val additionalAccessViewModel: AdditionalAccessViewModel by viewModels()
    private val migrationViewModel: MigrationViewModel by viewModels()
    private val allowAllPreference: HealthMainSwitchPreference by pref(ALLOW_ALL_PREFERENCE)
    private val readPermissionCategory: PreferenceGroup by pref(READ_CATEGORY)
    private val writePermissionCategory: PreferenceGroup by pref(WRITE_CATEGORY)
    private val manageAppCategory: PreferenceGroup by pref(MANAGE_APP_CATEGORY)
    private val header: AppHeaderPreference by pref(PERMISSION_HEADER)
    private val footer: FooterPreference by pref(FOOTER)
    private val dateFormatter by lazy { LocalDateTimeFormatter(requireContext()) }
    private val onSwitchChangeListener = OnCheckedChangeListener { switchView, isChecked ->
        if (isChecked) {
            val permissionsUpdated = viewModel.grantAllFitnessPermissions(packageName)
            if (!permissionsUpdated) {
                switchView.isChecked = false
                Toast.makeText(requireContext(), R.string.default_error, Toast.LENGTH_SHORT).show()
            }
        } else {
            showRevokeAllPermissions()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.settings_manage_app_permission_screen, rootKey)

        allowAllPreference.apply {
            logNameActive = PermissionsElement.ALLOW_ALL_SWITCH
            logNameInactive = PermissionsElement.ALLOW_ALL_SWITCH
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (
            requireArguments().containsKey(EXTRA_PACKAGE_NAME) &&
                requireArguments().getString(EXTRA_PACKAGE_NAME) != null
        ) {
            packageName = requireArguments().getString(EXTRA_PACKAGE_NAME)!!
        }
        if (requireArguments().containsKey(Constants.SHOW_MANAGE_APP_SECTION)) {
            showManageAppSection = requireArguments().getBoolean(Constants.SHOW_MANAGE_APP_SECTION)
        }

        viewModel.loadPermissionsForPackage(packageName)
        additionalAccessViewModel.loadAdditionalAccessPreferences(packageName)

        viewModel.fitnessPermissions.observe(viewLifecycleOwner) { permissions ->
            updatePermissions(permissions)
        }
        viewModel.grantedFitnessPermissions.observe(viewLifecycleOwner) { granted ->
            permissionMap.forEach { (healthPermission, switchPreference) ->
                switchPreference.isChecked = healthPermission in granted
            }
        }
        viewModel.lastReadPermissionDisconnected.observe(viewLifecycleOwner) { lastRead ->
            if (lastRead) {
                Toast.makeText(
                        requireContext(),
                        R.string.removed_additional_permissions_toast,
                        Toast.LENGTH_LONG,
                    )
                    .show()
                viewModel.markLastReadShown()
            }
        }

        viewModel.revokeAllHealthPermissionsState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RevokeAllState.Loading -> {
                    showLoadingDialog()
                }
                else -> {
                    dismissLoadingDialog()
                }
            }
        }

        migrationViewModel.migrationState.observe(viewLifecycleOwner) { migrationState ->
            when (migrationState) {
                is WithData -> {
                    maybeShowMigrationDialog(
                        migrationState.migrationRestoreState,
                        requireActivity(),
                        viewModel.appInfo.value?.appName!!,
                    )
                }
                else -> {
                    // do nothing
                }
            }
        }

        viewModel.showDisableExerciseRouteEvent.observe(viewLifecycleOwner) { event ->
            if (event.shouldShowDialog) {
                DisableExerciseRoutePermissionDialog.createDialog(packageName, event.appName)
                    .show(childFragmentManager, DISABLE_EXERCISE_ROUTE_DIALOG_TAG)
            }
        }

        childFragmentManager.setFragmentResultListener(
            DisconnectHealthPermissionsDialogFragment.DISCONNECT_CANCELED_EVENT,
            this,
        ) { _, _ ->
            allowAllPreference.isChecked = true
        }

        childFragmentManager.setFragmentResultListener(
            DisconnectHealthPermissionsDialogFragment.DISCONNECT_ALL_EVENT,
            this,
        ) { _, bundle ->
            if (!viewModel.revokeAllFitnessAndMaybeAdditionalPermissions(packageName)) {
                Toast.makeText(requireContext(), R.string.default_error, Toast.LENGTH_SHORT).show()
            }

            if (
                bundle.containsKey(DisconnectHealthPermissionsDialogFragment.KEY_DELETE_DATA) &&
                    bundle.getBoolean(DisconnectHealthPermissionsDialogFragment.KEY_DELETE_DATA)
            ) {
                viewModel.deleteAppData(packageName, appName)
            }
        }

        setupHeader()
        setupManageAppCategory()
    }

    private fun setupHeader() {
        viewModel.appInfo.observe(viewLifecycleOwner) { appMetadata ->
            packageName = appMetadata.packageName
            appName = appMetadata.appName
            setupAllowAllPreference()
            setupFooter(appMetadata.appName)
            header.apply {
                icon = appMetadata.icon
                title = appMetadata.appName
            }
        }
    }

    private fun setupFooter(appName: String) {
        if (viewModel.isPackageSupported(packageName)) {
            viewModel.atLeastOneFitnessPermissionGranted.observe(viewLifecycleOwner) {
                isAtLeastOneGranted ->
                updateFooter(isAtLeastOneGranted, appName)
            }
        } else {
            preferenceScreen.removePreferenceRecursively(FOOTER)
        }
    }

    private fun setupManageAppCategory() {
        if (!showManageAppSection) {
            manageAppCategory.isVisible = false
            return
        }
        additionalAccessViewModel.additionalAccessState.observe(viewLifecycleOwner) { state ->
            manageAppCategory.isVisible = state.isAvailable()
            manageAppCategory.removeAll()
            if (state.isAvailable()) {
                val additionalAccessPref =
                    HealthPreference(requireContext()).also {
                        it.key = KEY_ADDITIONAL_ACCESS
                        it.logName = ADDITIONAL_ACCESS_BUTTON
                        it.setTitle(R.string.additional_access_label)
                        it.setOnPreferenceClickListener { _ ->
                            val extras = bundleOf(EXTRA_PACKAGE_NAME to packageName)
                            navigationUtils.navigate(
                                fragment = this,
                                action = R.id.action_settingsFitnessApp_to_additionalAccessFragment,
                                bundle = extras,
                            )
                            true
                        }
                    }
                manageAppCategory.addPreference(additionalAccessPref)
            }
        }
    }

    private fun setupAllowAllPreference() {
        allowAllPreference.addOnSwitchChangeListener(onSwitchChangeListener)
        viewModel.allFitnessPermissionsGranted.observe(viewLifecycleOwner) { isAllGranted ->
            allowAllPreference.removeOnSwitchChangeListener(onSwitchChangeListener)
            allowAllPreference.isChecked = isAllGranted
            allowAllPreference.addOnSwitchChangeListener(onSwitchChangeListener)
        }
    }

    private fun showRevokeAllPermissions() {
        DisconnectHealthPermissionsDialogFragment(
                appName = appName,
                enableDeleteData = false,
                DisconnectHealthPermissionsDialogFragment.DisconnectType.FITNESS,
            )
            .show(childFragmentManager, DisconnectHealthPermissionsDialogFragment.TAG)
    }

    private fun updatePermissions(permissions: List<FitnessPermission>) {
        readPermissionCategory.removeAll()
        writePermissionCategory.removeAll()

        permissionMap.clear()

        permissions
            .sortedBy {
                requireContext()
                    .getString(fromPermissionType(it.fitnessPermissionType).uppercaseLabel)
            }
            .forEach { permission ->
                val category =
                    if (permission.permissionsAccessType == PermissionsAccessType.READ) {
                        readPermissionCategory
                    } else {
                        writePermissionCategory
                    }
                val switchPreference =
                    HealthSwitchPreference(requireContext()).also {
                        val healthCategory =
                            fromFitnessPermissionType(permission.fitnessPermissionType)
                        it.icon = healthCategory.icon(requireContext())
                        it.setTitle(
                            fromPermissionType(permission.fitnessPermissionType).uppercaseLabel
                        )
                        it.logNameActive = PermissionsElement.PERMISSION_SWITCH
                        it.logNameInactive = PermissionsElement.PERMISSION_SWITCH
                        it.setOnPreferenceChangeListener { _, newValue ->
                            val checked = newValue as Boolean
                            val permissionUpdated =
                                viewModel.updatePermission(packageName, permission, checked)
                            if (!permissionUpdated) {
                                Toast.makeText(
                                        requireContext(),
                                        R.string.default_error,
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                            permissionUpdated
                        }
                    }
                permissionMap[permission] = switchPreference
                category.addPreference(switchPreference)
            }

        // Hide category if it contains no permissions
        readPermissionCategory.apply { isVisible = (preferenceCount != 0) }
        writePermissionCategory.apply { isVisible = (preferenceCount != 0) }
    }

    private fun updateFooter(isAtLeastOneGranted: Boolean, appName: String) {
        var title = getString(R.string.manage_permissions_rationale, appName)

        val isHistoryReadAvailable =
            additionalAccessViewModel.additionalAccessState.value?.historyReadUIState?.isDeclared
                ?: false
        // Do not show the access date here if history read is available
        if (isAtLeastOneGranted && !isHistoryReadAvailable) {
            val dataAccessDate = viewModel.loadAccessDate(packageName)
            dataAccessDate?.let {
                val formattedDate = dateFormatter.formatLongDate(dataAccessDate)
                title =
                    getString(R.string.manage_permissions_time_frame, appName, formattedDate) +
                        PARAGRAPH_SEPARATOR +
                        title
            }
        }

        footer.title = title
        if (healthPermissionReader.isRationaleIntentDeclared(packageName)) {
            footer.setLearnMoreText(getString(R.string.manage_permissions_learn_more))
            footer.setLearnMoreAction {
                val startRationaleIntent =
                    healthPermissionReader.getApplicationRationaleIntent(packageName)
                startActivity(startRationaleIntent)
            }
        }
    }

    companion object {
        private const val ALLOW_ALL_PREFERENCE = "allow_all_preference"
        private const val READ_CATEGORY = "read_permission_category"
        private const val WRITE_CATEGORY = "write_permission_category"
        private const val PERMISSION_HEADER = "manage_app_permission_header"
        private const val MANAGE_APP_CATEGORY = "manage_app_category"
        private const val KEY_ADDITIONAL_ACCESS = "additional_access"
        private const val DISABLE_EXERCISE_ROUTE_DIALOG_TAG = "disable_exercise_route_dialog"
        private const val FOOTER = "manage_app_permission_footer"
        private const val PARAGRAPH_SEPARATOR = "\n\n"
    }
}
