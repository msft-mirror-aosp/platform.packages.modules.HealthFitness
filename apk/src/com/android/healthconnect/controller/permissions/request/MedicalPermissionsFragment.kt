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
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.MedicalPermissionStrings
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.HealthMainSwitchPreference
import com.android.healthconnect.controller.shared.preference.HealthSwitchPreference
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(PermissionsFragment::class)
class MedicalPermissionsFragment : Hilt_MedicalPermissionsFragment() {

    companion object {
        private const val ALLOW_ALL_PREFERENCE = "allow_all_preference"
        private const val READ_CATEGORY = "read_permission_category"
        private const val WRITE_CATEGORY = "write_permission_category"
        private const val HEADER = "request_permissions_header"
    }

    // TODO(b/342159144): Update page name.
    private val pageName = PageName.UNKNOWN_PAGE
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
        viewModel.updateMedicalPermissions(grant)
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
        // TODO(b/342159144): Update visual elements.
        allowAllPreference?.logNameActive = ErrorPageElement.UNKNOWN_ELEMENT
        allowAllPreference?.logNameInactive = ErrorPageElement.UNKNOWN_ELEMENT
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.appMetadata.observe(viewLifecycleOwner) { app ->
            logger.logImpression(PermissionsElement.APP_RATIONALE_LINK)
            header?.bind(app.appName, /* historyAccessGranted= */ true) {
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
            val medicalPermissions =
                allPermissions.filterIsInstance<HealthPermission.MedicalPermission>()
            val fitnessPermissions =
                allPermissions.filterIsInstance<HealthPermission.FitnessPermission>()
            val additionalPermissions =
                allPermissions.filterIsInstance<HealthPermission.AdditionalPermission>()

            updateDataList(medicalPermissions)
            setupAllowAll()

            setupAllowButton(fitnessPermissions.isNotEmpty(), additionalPermissions.isNotEmpty())
            setupDontAllowButton()
        }
    }

    private fun setupAllowButton(isDataTypeNotEmpty: Boolean, isAdditionalNotEmpty: Boolean) {
        // TODO(b/342159144): Update visual element.
        logger.logImpression(ErrorPageElement.UNKNOWN_ELEMENT)

        if (!viewModel.isMedicalPermissionRequestConcluded()) {
            viewModel.grantedMedicalPermissions.observe(viewLifecycleOwner) { grantedPermissions ->
                getAllowButton().isEnabled = grantedPermissions.isNotEmpty()
            }
        }

        if (isDataTypeNotEmpty || isAdditionalNotEmpty) {
            getAllowButton().setOnClickListener {
                viewModel.setMedicalPermissionRequestConcluded(true)
                // When medical permissions are concluded we need to
                // grant/revoke only the medical permissions, to trigger the
                // access date. We can't request all at once because we might accidentally
                // set the data type and additional permissions USER_FIXED
                viewModel.requestMedicalPermissions(getPackageNameExtra())
                // TODO(b/342159144): Update visual element.
                logger.logInteraction(ErrorPageElement.UNKNOWN_ELEMENT)
                // navigate to the next permission screen
                if (isDataTypeNotEmpty) {
                    requireActivity()
                        .supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.permission_content, FitnessPermissionsFragment())
                        .commit()
                } else {
                    requireActivity()
                        .supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.permission_content, AdditionalPermissionsFragment())
                        .commit()
                }
            }
        } else {
            // Just medical permissions
            getAllowButton().setOnClickListener {
                // TODO(b/342159144): Update visual element.
                logger.logInteraction(ErrorPageElement.UNKNOWN_ELEMENT)
                viewModel.requestMedicalPermissions(getPackageNameExtra())
                this.handlePermissionResults(viewModel.getPermissionGrants())
            }
        }
    }

    private fun setupDontAllowButton() {
        // TODO(b/342159144): Update visual element.
        logger.logImpression(ErrorPageElement.UNKNOWN_ELEMENT)

        getDontAllowButton().setOnClickListener {
            // TODO(b/342159144): Update visual element.
            logger.logInteraction(ErrorPageElement.UNKNOWN_ELEMENT)
            viewModel.updateMedicalPermissions(false)
            viewModel.requestMedicalPermissions(getPackageNameExtra())
            handlePermissionResults(viewModel.getPermissionGrants())
        }
    }

    private fun setupAllowAll() {
        viewModel.allMedicalPermissionsGranted.observe(viewLifecycleOwner) { allPermissionsGranted
            ->
            // does not trigger removing/enabling all permissions
            allowAllPreference?.removeOnSwitchChangeListener(onSwitchChangeListener)
            allowAllPreference?.isChecked = allPermissionsGranted
            allowAllPreference?.addOnSwitchChangeListener(onSwitchChangeListener)
        }
        allowAllPreference?.addOnSwitchChangeListener(onSwitchChangeListener)
    }

    private fun updateDataList(permissionsList: List<HealthPermission.MedicalPermission>) {
        readPermissionCategory?.removeAll()
        writePermissionCategory?.removeAll()

        permissionsList
            .sortedBy {
                requireContext()
                    .getString(
                        MedicalPermissionStrings.fromPermissionType(it.medicalPermissionType)
                            .uppercaseLabel)
            }
            .forEach { permission ->
                val value = viewModel.isPermissionLocallyGranted(permission)
                if (permission.medicalPermissionType == MedicalPermissionType.ALL_MEDICAL_DATA) {
                    writePermissionCategory?.addPreference(
                        getPermissionPreference(value, permission))
                } else {
                    readPermissionCategory?.addPreference(
                        getPermissionPreference(value, permission))
                }
            }

        readPermissionCategory?.apply { isVisible = (preferenceCount != 0) }
        writePermissionCategory?.apply { isVisible = (preferenceCount != 0) }
    }

    private fun getPermissionPreference(
        defaultValue: Boolean,
        permission: HealthPermission.MedicalPermission
    ): Preference {
        return HealthSwitchPreference(requireContext()).also {
            // TODO(b/342156345): Add icons.
            it.setDefaultValue(defaultValue)
            it.setTitle(
                MedicalPermissionStrings.fromPermissionType(permission.medicalPermissionType)
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
