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
import androidx.fragment.app.commitNow
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.Constants.SHOW_MANAGE_APP_SECTION
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.fromHealthPermissionType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.HealthMainSwitchPreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.HealthSwitchPreference
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.dismissLoadingDialog
import com.android.healthconnect.controller.utils.logging.AppAccessElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.pref
import com.android.healthconnect.controller.utils.showLoadingDialog
import com.android.settingslib.widget.AppHeaderPreference
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Fragment for an app that has both fitness and medical permissions.
 */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class CombinedPermissionsFragment : Hilt_CombinedPermissionsFragment() {

    @Inject
    lateinit var logger: HealthConnectLogger
    @Inject
    lateinit var healthPermissionReader: HealthPermissionReader

    companion object {
        private const val PERMISSION_HEADER = "manage_app_permission_header"
        private const val MANAGE_PERMISSIONS_PREFERENCE_KEY = "manage_permissions"
        private const val MANAGE_APP_PREFERENCE_KEY = "manage_app"
        private const val FOOTER_KEY = "connected_app_footer"
        private const val KEY_ADDITIONAL_ACCESS = "additional_access"
        private const val PARAGRAPH_SEPARATOR = "\n\n"
    }

    init {
        // TODO(b/342159144): Update visual elements.
        this.setPageName(PageName.UNKNOWN_PAGE)
    }

    private var packageName = ""
    private var appName = ""
    private val appPermissionViewModel: AppPermissionViewModel by activityViewModels()
    private val additionalAccessViewModel: AdditionalAccessViewModel by activityViewModels()
    private val header: AppHeaderPreference by pref(PERMISSION_HEADER)
    private val managePermissionsCategory: PreferenceGroup by pref(MANAGE_PERMISSIONS_PREFERENCE_KEY)
    private val manageAppCategory: PreferenceGroup by pref(MANAGE_APP_PREFERENCE_KEY)
    private val connectedAppFooter: FooterPreference by pref(FOOTER_KEY)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.combined_permissions_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (requireArguments().containsKey(EXTRA_PACKAGE_NAME) &&
            requireArguments().getString(EXTRA_PACKAGE_NAME) != null) {
            packageName = requireArguments().getString(EXTRA_PACKAGE_NAME)!!
        }
        if (requireArguments().containsKey(EXTRA_APP_NAME) &&
            requireArguments().getString(EXTRA_APP_NAME) != null) {
            appName = requireArguments().getString(EXTRA_APP_NAME)!!
        }
        setupHeader()
        setupManagePermissionsPreferenceCategory()
        setupManageAppPreferenceCategory()
        setupFooter()
    }

    private fun setupHeader() {
        appPermissionViewModel.appInfo.observe(viewLifecycleOwner) { appMetadata ->
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
                it.title = getString(R.string.manage_fitness_permissions)
                it.setOnPreferenceClickListener {
                    findNavController()
                        .navigate(
                            R.id.action_combinedPermissions_to_fitnessApp,
                            bundleOf(
                                EXTRA_PACKAGE_NAME to packageName, EXTRA_APP_NAME to appName, SHOW_MANAGE_APP_SECTION to false))
                    true
                }
            })

        managePermissionsCategory.addPreference(
            HealthPreference(requireContext()).also {
                it.title = getString(R.string.manage_medical_permissions)
                it.setOnPreferenceClickListener {
                    findNavController()
                        .navigate(
                            R.id.action_combinedPermissions_to_medicalApp,
                            bundleOf(
                                EXTRA_PACKAGE_NAME to packageName, EXTRA_APP_NAME to appName, SHOW_MANAGE_APP_SECTION to false))
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
                            findNavController()
                                .navigate(
                                    R.id.action_combinedPermissions_to_additionalAccess,
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

    private fun setupManageAppPreferenceCategory() {
        manageAppCategory.removeAll()
        manageAppCategory.addPreference(
            HealthPreference(requireContext()).also {
                it.title = getString(R.string.see_app_data)
                it.setOnPreferenceClickListener {
                    findNavController()
                        .navigate(
                            R.id.action_combinedPermissions_to_appData,
                            bundleOf(
                                EXTRA_PACKAGE_NAME to packageName, EXTRA_APP_NAME to appName))
                    true
                }
            })
    }

    private fun setupFooter() {
        val title =
            getString(R.string.other_android_permissions) +
                    PARAGRAPH_SEPARATOR +
                    getString(R.string.manage_permissions_rationale, appName)
        val contentDescription =
            getString(R.string.other_android_permissions_content_description) +
                    PARAGRAPH_SEPARATOR +
                    getString(R.string.manage_permissions_rationale, appName)
        connectedAppFooter.title = title
        connectedAppFooter.setContentDescription(contentDescription)
        if (healthPermissionReader.isRationaleIntentDeclared(packageName)) {
            connectedAppFooter.setLearnMoreText(getString(R.string.manage_permissions_learn_more))
            logger.logImpression(AppAccessElement.PRIVACY_POLICY_LINK)
            connectedAppFooter.setLearnMoreAction {
                logger.logInteraction(AppAccessElement.PRIVACY_POLICY_LINK)
                val startRationaleIntent =
                    healthPermissionReader.getApplicationRationaleIntent(packageName)
                startActivity(startRationaleIntent)
            }
        }
    }
}
