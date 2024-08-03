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
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.maybeShowMigrationDialog
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.*
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.MedicalPermission
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.shared.Constants.SHOW_MANAGE_APP_SECTION
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.logging.AppAccessElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.pref
import com.android.settingslib.widget.AppHeaderPreference
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment to show granted/revoked health permissions for and apps that declare both
 * [FitnessPermission]s and[MedicalPermission]s. It is used as an entry point from
 * PermissionController.
 *
 * For apps that declares health connect permissions without the rational intent, we only show
 * granted permissions to allow the user to revoke this app permissions.
 */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class SettingsCombinedPermissionsFragment : Hilt_SettingsCombinedPermissionsFragment() {

    init {
        this.setPageName(PageName.MANAGE_PERMISSIONS_PAGE)
    }

    @Inject lateinit var healthPermissionReader: HealthPermissionReader
    @Inject lateinit var navigationUtils: NavigationUtils

    private lateinit var packageName: String
    private var appName: String = ""

    private val viewModel: AppPermissionViewModel by activityViewModels()
    private val additionalAccessViewModel: AdditionalAccessViewModel by viewModels()
    private val migrationViewModel: MigrationViewModel by viewModels()
    private val managePermissionsCategory: PreferenceGroup by pref(MANAGE_PERMISSIONS_CATEGORY)
    private val header: AppHeaderPreference by pref(PERMISSION_HEADER)
    private val footer: FooterPreference by pref(FOOTER)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.settings_combined_permissions_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (requireArguments().containsKey(EXTRA_PACKAGE_NAME) &&
            requireArguments().getString(EXTRA_PACKAGE_NAME) != null) {
            packageName = requireArguments().getString(EXTRA_PACKAGE_NAME)!!
        }
        viewModel.loadPermissionsForPackage(packageName)

        migrationViewModel.migrationState.observe(viewLifecycleOwner) { migrationState ->
            when (migrationState) {
                is WithData -> {
                    maybeShowMigrationDialog(
                        migrationState.migrationRestoreState,
                        requireActivity(),
                        viewModel.appInfo.value?.appName!!)
                }
                else -> {
                    // do nothing
                }
            }
        }
        setupHeader()
        setupManagePermissionsPreferenceCategory()
    }

    private fun setupHeader() {
        viewModel.appInfo.observe(viewLifecycleOwner) { appMetadata ->
            setupFooter(appMetadata.appName)
            header.apply {
                icon = appMetadata.icon
                title = appMetadata.appName
            }
        }
    }

    private fun setupManagePermissionsPreferenceCategory() {
        managePermissionsCategory.removeAll()

        managePermissionsCategory.addPreference(
            HealthPreference(requireContext()).also {
                it.title = getString(R.string.fitness_permissions)
                it.summary = getString(R.string.fitness_permissions_summary)
                it.setOnPreferenceClickListener {
                    navigationUtils.navigate(
                        this,
                        R.id.action_settingsCombinedPermissions_to_FitnessAppFragment,
                        bundleOf(
                            EXTRA_PACKAGE_NAME to packageName,
                            Constants.EXTRA_APP_NAME to appName,
                            SHOW_MANAGE_APP_SECTION to false))
                    true
                }
            })

        managePermissionsCategory.addPreference(
            HealthPreference(requireContext()).also {
                it.title = getString(R.string.medical_permissions)
                it.summary = getString(R.string.medical_permissions_summary)
                it.setOnPreferenceClickListener {
                    navigationUtils.navigate(
                        this,
                        R.id.action_settingsCombinedPermissions_to_MedicalAppFragment,
                        bundleOf(
                            EXTRA_PACKAGE_NAME to packageName,
                            Constants.EXTRA_APP_NAME to appName,
                            SHOW_MANAGE_APP_SECTION to false))
                    true
                }
            })

        additionalAccessViewModel.loadAdditionalAccessPreferences(packageName)
        additionalAccessViewModel.additionalAccessState.observe(viewLifecycleOwner) { state ->
            if (state.isValid() && shouldAddAdditionalAccessPref()) {
                val additionalAccessPref =
                    HealthPreference(requireContext()).also {
                        it.key = KEY_ADDITIONAL_ACCESS
                        it.logName = AppAccessElement.ADDITIONAL_ACCESS_BUTTON
                        it.setTitle(R.string.additional_access_label)
                        it.setOnPreferenceClickListener { _ ->
                            val extras = bundleOf(EXTRA_PACKAGE_NAME to packageName)
                            navigationUtils.navigate(
                                this,
                                R.id.action_settingsCombinedPermissions_to_additionalAccessFragment,
                                extras)
                            true
                        }
                    }
                managePermissionsCategory.addPreference(additionalAccessPref)
            }
            managePermissionsCategory.children.find { it.key == KEY_ADDITIONAL_ACCESS }?.isVisible =
                state.isValid()
        }
    }

    private fun shouldAddAdditionalAccessPref(): Boolean {
        return managePermissionsCategory.children.none { it.key == KEY_ADDITIONAL_ACCESS }
    }

    private fun setupFooter(appName: String) {
        if (viewModel.isPackageSupported(packageName)) {
            updateFooter(appName)
        } else {
            preferenceScreen.removePreferenceRecursively(FOOTER)
        }
    }

    private fun updateFooter(appName: String) {
        footer.title = getString(R.string.manage_permissions_rationale, appName)
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
        private const val PERMISSION_HEADER = "manage_app_permission_header"
        private const val MANAGE_PERMISSIONS_CATEGORY = "manage_permissions"
        private const val FOOTER = "manage_app_permission_footer"
        private const val KEY_ADDITIONAL_ACCESS = "key_additional_access"
    }
}
