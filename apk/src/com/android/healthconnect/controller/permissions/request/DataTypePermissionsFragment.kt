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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton.OnCheckedChangeListener
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.DataTypePermissionStrings
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.HealthMainSwitchPreference
import com.android.healthconnect.controller.shared.preference.HealthSwitchPreference
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(PermissionsFragment::class)
class DataTypePermissionsFragment : Hilt_DataTypePermissionsFragment() {

    companion object {
        private const val ALLOW_ALL_PREFERENCE = "allow_all_preference"
        private const val READ_CATEGORY = "read_permission_category"
        private const val WRITE_CATEGORY = "write_permission_category"
        private const val HEADER = "request_permissions_header"
    }

    private val pageName = PageName.REQUEST_PERMISSIONS_PAGE
    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: RequestPermissionViewModel by activityViewModels()
    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    private val header: RequestPermissionHeaderPreference? by lazy {
        preferenceScreen.findPreference(HEADER)
    }

    private val allowAllPreference: HealthMainSwitchPreference? by lazy {
        preferenceScreen.findPreference(ALLOW_ALL_PREFERENCE)
    }

    private val readPermissionCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(READ_CATEGORY)
    }

    private val writePermissionCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(WRITE_CATEGORY)
    }

    private val onSwitchChangeListener = OnCheckedChangeListener { _, grant ->
        readPermissionCategory?.children?.forEach { preference ->
            (preference as TwoStatePreference).isChecked = grant
        }
        writePermissionCategory?.children?.forEach { preference ->
            (preference as TwoStatePreference).isChecked = grant
        }
        viewModel.updateDataTypePermissions(grant)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.setPageId(pageName)
    }

    override fun onResume() {
        super.onResume()
        logger.setPageId(pageName)
        logger.logPageImpression()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logger.setPageId(pageName)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.permissions_screen, rootKey)
        allowAllPreference?.logNameActive = PermissionsElement.ALLOW_ALL_SWITCH
        allowAllPreference?.logNameInactive = PermissionsElement.ALLOW_ALL_SWITCH
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.appMetadata.observe(viewLifecycleOwner) { app ->
            logger.logImpression(PermissionsElement.APP_RATIONALE_LINK)
            header?.bind(app.appName, viewModel.isHistoryAccessGranted()) {
                val startRationaleIntent =
                    healthPermissionReader.getApplicationRationaleIntent(app.packageName)
                logger.logInteraction(PermissionsElement.APP_RATIONALE_LINK)
                startActivity(startRationaleIntent)
            }
            readPermissionCategory?.title =
                getString(R.string.read_permission_category, app.appName)
            writePermissionCategory?.title =
                getString(R.string.write_permission_category, app.appName)
        }
        viewModel.healthPermissionsList.observe(viewLifecycleOwner) { allPermissions ->
            val dataTypePermissions =
                allPermissions.filterIsInstance<HealthPermission.DataTypePermission>()
            val additionalPermissions =
                allPermissions.filterIsInstance<HealthPermission.AdditionalPermission>()

            updateDataList(dataTypePermissions)
            setupAllowAll()

            setupAllowButton(additionalPermissions.isNotEmpty())
            setupDontAllowButton()
        }
    }

    private fun setupAllowButton(isCombinedPermissionRequest: Boolean) {
        logger.logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        if (!viewModel.isDataTypePermissionRequestConcluded()) {
            viewModel.grantedDataTypePermissions.observe(viewLifecycleOwner) { grantedPermissions ->
                getAllowButton().isEnabled = grantedPermissions.isNotEmpty()
            }
        }

        if (isCombinedPermissionRequest) {
            getAllowButton().setOnClickListener {
                viewModel.setDataTypePermissionRequestConcluded(true)
                // When data type permissions are concluded we need to
                // grant/revoke only the data type permissions, to trigger the
                // access date. We can't request all at once because we might accidentally
                // set the additional permissions USER_FIXED
                viewModel.requestDataTypePermissions(getPackageNameExtra())
                logger.logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
                // navigate to additional permissions
                requireActivity()
                    .supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.permission_content, AdditionalPermissionsFragment())
                    .commit()
            }
        } else {
            // Just health permissions
            getAllowButton().setOnClickListener {
                logger.logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
                viewModel.requestDataTypePermissions(getPackageNameExtra())
                this.handlePermissionResults(viewModel.getPermissionGrants())
            }
        }
    }

    private fun setupDontAllowButton() {
        logger.logImpression(PermissionsElement.CANCEL_PERMISSIONS_BUTTON)

        getDontAllowButton().setOnClickListener {
            logger.logInteraction(PermissionsElement.CANCEL_PERMISSIONS_BUTTON)
            viewModel.updateDataTypePermissions(false)
            viewModel.requestDataTypePermissions(getPackageNameExtra())
            handlePermissionResults(viewModel.getPermissionGrants())
        }
    }

    private fun setupAllowAll() {
        viewModel.allDataTypePermissionsGranted.observe(viewLifecycleOwner) { allPermissionsGranted
            ->
            // does not trigger removing/enabling all permissions
            allowAllPreference?.removeOnSwitchChangeListener(onSwitchChangeListener)
            allowAllPreference?.isChecked = allPermissionsGranted
            allowAllPreference?.addOnSwitchChangeListener(onSwitchChangeListener)
        }
        allowAllPreference?.addOnSwitchChangeListener(onSwitchChangeListener)
    }

    private fun updateDataList(permissionsList: List<HealthPermission.DataTypePermission>) {
        readPermissionCategory?.removeAll()
        writePermissionCategory?.removeAll()

        permissionsList
            .sortedBy {
                requireContext()
                    .getString(
                        DataTypePermissionStrings.fromPermissionType(it.healthPermissionType)
                            .uppercaseLabel)
            }
            .forEach { permission ->
                val value = viewModel.isPermissionLocallyGranted(permission)
                if (PermissionsAccessType.READ == permission.permissionsAccessType) {
                    readPermissionCategory?.addPreference(
                        getPermissionPreference(value, permission))
                } else if (PermissionsAccessType.WRITE == permission.permissionsAccessType) {
                    writePermissionCategory?.addPreference(
                        getPermissionPreference(value, permission))
                }
            }

        readPermissionCategory?.apply { isVisible = (preferenceCount != 0) }
        writePermissionCategory?.apply { isVisible = (preferenceCount != 0) }
    }

    private fun getPermissionPreference(
        defaultValue: Boolean,
        permission: HealthPermission.DataTypePermission
    ): Preference {
        return HealthSwitchPreference(requireContext()).also {
            val healthCategory =
                HealthDataCategoryExtensions.fromHealthPermissionType(
                    permission.healthPermissionType)
            it.icon = healthCategory.icon(requireContext())
            it.setDefaultValue(defaultValue)
            it.setTitle(
                DataTypePermissionStrings.fromPermissionType(permission.healthPermissionType)
                    .uppercaseLabel)
            it.logNameActive = PermissionsElement.PERMISSION_SWITCH
            it.logNameInactive = PermissionsElement.PERMISSION_SWITCH
            it.setOnPreferenceChangeListener { _, newValue ->
                viewModel.updateHealthPermission(permission, newValue as Boolean)
                true
            }
        }
    }
}
