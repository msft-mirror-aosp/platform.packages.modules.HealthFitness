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
 */
package com.android.healthconnect.controller.permissions.request

import android.health.connect.HealthPermissions
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceCategory
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.AdditionalPermissionStrings
import com.android.healthconnect.controller.permissions.data.HealthPermission.AdditionalPermission
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.preference.AdditionalPermissionHeaderPreference
import com.android.healthconnect.controller.shared.preference.HealthSwitchPreference
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment that can show combined or single additional permission request screens. */
@AndroidEntryPoint(PermissionsFragment::class)
class AdditionalPermissionsFragment : Hilt_AdditionalPermissionsFragment() {

    // TODO (b/331098724) Add telemetry for this screen
    @Inject lateinit var logger: HealthConnectLogger

    companion object {
        private const val HEADER = "request_additional_permissions_header"
        private const val CATEGORY = "additional_permissions_category"
    }

    private val viewModel: RequestPermissionViewModel by activityViewModels()

    private val header: AdditionalPermissionHeaderPreference? by lazy {
        preferenceScreen.findPreference(HEADER)
    }

    private val category: PreferenceCategory? by lazy { preferenceScreen.findPreference(CATEGORY) }
    private val dateFormatter by lazy { LocalDateTimeFormatter(requireContext()) }

    @Inject lateinit var featureUtils: FeatureUtils

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.additional_permissions_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.additionalPermissionsInfo.observe(viewLifecycleOwner) { info ->
            val (additionalPermissions, appMetadata) = info

            if (additionalPermissions.isNullOrEmpty()) {
                return@observe
            }

            if (additionalPermissions.size > 1) {
                showCombinedAdditionalPermissions(additionalPermissions, appMetadata!!)
            } else {
                showSingleAdditionalPermission(additionalPermissions[0], appMetadata!!)
            }

            setupAllowButton(additionalPermissions)
            setupDontAllowButton()
        }
    }

    private fun setupAllowButton(permissionList: List<AdditionalPermission>) {
        logger.logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        if (viewModel.isDataTypePermissionRequestConcluded()) {
            // if requested additional permissions == 1 then allow by default
            if (permissionList.size == 1) {
                getAllowButton().isEnabled = true
            } else {
                viewModel.grantedAdditionalPermissions.observe(viewLifecycleOwner) {
                    grantedPermissions ->
                    getAllowButton().isEnabled = grantedPermissions.isNotEmpty()
                }
            }
        }

        getAllowButton().setOnClickListener {
            logger.logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
            viewModel.requestAdditionalPermissions(getPackageNameExtra())
            handlePermissionResults(viewModel.getPermissionGrants())
        }
    }

    private fun setupDontAllowButton() {
        logger.logImpression(PermissionsElement.CANCEL_PERMISSIONS_BUTTON)

        getDontAllowButton().setOnClickListener {
            logger.logInteraction(PermissionsElement.CANCEL_PERMISSIONS_BUTTON)
            viewModel.updateAdditionalPermissions(false)
            viewModel.requestAdditionalPermissions(this.getPackageNameExtra())
            handlePermissionResults(viewModel.getPermissionGrants())
        }
    }

    private fun showCombinedAdditionalPermissions(
        additionalPermissions: List<AdditionalPermission>,
        appMetadata: AppMetadata
    ) {
        header?.bind(
            titleText = R.string.request_additional_permissions_header_title,
            appName = appMetadata.appName,
            summaryText =
                getString(R.string.request_additional_permissions_description, appMetadata.appName))

        category?.removeAll()
        category?.isVisible = true

        additionalPermissions
            .sortedBy { it.additionalPermission }
            .forEach { additionalPermission ->
                category?.addPreference(
                    getAdditionalPermissionPreference(additionalPermission, appMetadata))
            }
    }

    private fun getAdditionalPermissionPreference(
        additionalPermission: AdditionalPermission,
        appMetadata: AppMetadata
    ): HealthSwitchPreference {
        val value = viewModel.isPermissionLocallyGranted(additionalPermission)

        val summary =
            if (additionalPermission.isHistoryReadPermission()) {
                getHistoryReadPermissionPreferenceSummary(appMetadata)
            } else {
                getString(
                    AdditionalPermissionStrings.fromAdditionalPermission(additionalPermission)
                        .permissionDescription)
            }
        return HealthSwitchPreference(requireContext()).also { switchPreference ->
            switchPreference.setDefaultValue(value)
            switchPreference.title =
                getString(
                    AdditionalPermissionStrings.fromAdditionalPermission(additionalPermission)
                        .permissionTitle)
            switchPreference.summary = summary

            switchPreference.setOnPreferenceChangeListener { _, newValue ->
                viewModel.updateHealthPermission(additionalPermission, newValue as Boolean)
                true
            }
        }
    }

    private fun getHistoryReadPermissionPreferenceSummary(appMetadata: AppMetadata): String {
        val additionalPermission = AdditionalPermission(HealthPermissions.READ_HEALTH_DATA_HISTORY)
        val dataAccessDate = viewModel.loadAccessDate(appMetadata.packageName)
        return if (dataAccessDate != null) {
            val formattedDate = dateFormatter.formatLongDate(dataAccessDate)
            getString(
                AdditionalPermissionStrings.fromAdditionalPermission(additionalPermission)
                    .permissionDescription,
                formattedDate)
        } else {
            getString(
                AdditionalPermissionStrings.fromAdditionalPermission(additionalPermission)
                    .permissionDescriptionFallback)
        }
    }

    private fun getHistoryReadPermissionRequestText(appMetadata: AppMetadata): String {
        val additionalPermission = AdditionalPermission(HealthPermissions.READ_HEALTH_DATA_HISTORY)
        val dataAccessDate = viewModel.loadAccessDate(appMetadata.packageName)
        return if (dataAccessDate != null) {
            val formattedDate = dateFormatter.formatLongDate(dataAccessDate)
            getString(
                AdditionalPermissionStrings.fromAdditionalPermission(additionalPermission)
                    .requestDescription,
                formattedDate)
        } else {
            getString(
                AdditionalPermissionStrings.fromAdditionalPermission(additionalPermission)
                    .requestDescriptionFallback)
        }
    }

    private fun showSingleAdditionalPermission(
        additionalPermission: AdditionalPermission,
        appMetadata: AppMetadata
    ) {
        val additionalPermissionStrings =
            AdditionalPermissionStrings.fromAdditionalPermission(additionalPermission)

        if (additionalPermission.isHistoryReadPermission()) {
            header?.bind(
                titleText = additionalPermissionStrings.requestTitle,
                appName = appMetadata.appName,
                summaryText = getHistoryReadPermissionRequestText(appMetadata))
        } else {
            header?.bind(
                titleText = additionalPermissionStrings.requestTitle,
                appName = appMetadata.appName,
                summaryText = getString(additionalPermissionStrings.requestDescription))
        }
    }
}
