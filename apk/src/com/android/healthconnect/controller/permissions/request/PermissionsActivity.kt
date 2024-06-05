/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.healthconnect.controller.permissions.request

import android.app.Activity.*
import android.content.Intent
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.pm.PackageManager
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS
import android.os.Bundle
import android.util.Log
import android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.maybeShowWhatsNewDialog
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.showDataRestoreInProgressDialog
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.showMigrationInProgressDialog
import com.android.healthconnect.controller.migration.MigrationActivity.Companion.showMigrationPendingDialog
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.onboarding.OnboardingActivity
import com.android.healthconnect.controller.onboarding.OnboardingActivity.Companion.shouldRedirectToOnboardingActivity
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.activity.EmbeddingUtils.maybeRedirectIntoTwoPaneSettings
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Permissions activity for Health Connect. */
@AndroidEntryPoint(FragmentActivity::class)
class PermissionsActivity : Hilt_PermissionsActivity() {

    companion object {
        private const val TAG = "PermissionsActivity"
    }

    @Inject lateinit var logger: HealthConnectLogger

    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    @Inject lateinit var deviceInfoUtils: DeviceInfoUtils

    @Inject lateinit var featureUtils: FeatureUtils

    private val requestPermissionsViewModel: RequestPermissionViewModel by viewModels()

    private val migrationViewModel: MigrationViewModel by viewModels()

    private val openOnboardingActivity =
        registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_CANCELED) {
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // This flag ensures a non system app cannot show an overlay on Health Connect. b/313425281
        window.addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS)

        // Handles unsupported devices and user profiles.
        if (!deviceInfoUtils.isHealthConnectAvailable(this)) {
            Log.e(TAG, "Health connect is not available for this user or hardware, finishing!")
            finish()
            return
        }

        if (!intent.hasExtra(EXTRA_PACKAGE_NAME)) {
            Log.e(TAG, "Invalid Intent Extras, finishing")
            finish()
            return
        }

        if (maybeRedirectIntoTwoPaneSettings(this)) {
            return
        }

        setContentView(R.layout.activity_permissions)

        if (savedInstanceState == null && shouldRedirectToOnboardingActivity(this)) {
            openOnboardingActivity.launch(OnboardingActivity.createIntent(this))
        }

        val rationaleIntentDeclared =
            healthPermissionReader.isRationaleIntentDeclared(getPackageNameExtra())
        if (!rationaleIntentDeclared) {
            Log.e(TAG, "App should support rationale intent, finishing!")
            finish()
        }

        requestPermissionsViewModel.init(getPackageNameExtra(), getPermissionStrings())
        if (requestPermissionsViewModel.isAnyPermissionUserFixed(
            getPackageNameExtra(), getPermissionStrings())) {
            Log.e(TAG, "App has at least one USER_FIXED permission, finishing!")
            requestPermissionsViewModel.requestHealthPermissions(getPackageNameExtra())
            handlePermissionResults()
        }

        requestPermissionsViewModel.healthPermissionsList.observe(this) { allPermissions ->
            val medicalPermissions =
                allPermissions.filterIsInstance<HealthPermission.MedicalPermission>()
            val dataTypePermissions =
                allPermissions.filterIsInstance<HealthPermission.DataTypePermission>()
            val additionalPermissions =
                allPermissions.filterIsInstance<HealthPermission.AdditionalPermission>()
            val noMedicalRequest = medicalPermissions.isEmpty()
            val noDataTypeRequest = dataTypePermissions.isEmpty()
            val noAdditionalRequest = additionalPermissions.isEmpty()

            // Case 1 - no permissions
            if (noMedicalRequest && noDataTypeRequest && noAdditionalRequest) {
                requestPermissionsViewModel.requestHealthPermissions(getPackageNameExtra())
                handlePermissionResults()
            }

            // Case 2 - just medical permissions
            else if (noDataTypeRequest && noAdditionalRequest) {
                showFragment(MedicalPermissionsFragment())
            }

            // Case 3 - just data type permissions
            else if (noMedicalRequest && noAdditionalRequest) {
                showFragment(DataTypePermissionsFragment())
            }

            // Case 4 - just additional permissions
            else if (noMedicalRequest && noDataTypeRequest) {
                if (!requestPermissionsViewModel.isAnyReadPermissionGranted()) {
                    Log.e(
                        TAG,
                        "No data type read permissions are granted, cannot request additional permissions.")
                    handlePermissionResults(RESULT_CANCELED)
                }

                // Show only additional access request
                requestPermissionsViewModel.setDataTypePermissionRequestConcluded(true)
                showFragment(AdditionalPermissionsFragment())
            }

            // Case 5 - medical and data type
            else if (noAdditionalRequest) {
                if (!requestPermissionsViewModel.isMedicalPermissionRequestConcluded()) {
                    showFragment(MedicalPermissionsFragment())
                } else {
                    showFragment(DataTypePermissionsFragment())
                }
            }

            // Case 6 - medical and additional
            else if (noDataTypeRequest) {
                if (!requestPermissionsViewModel.isMedicalPermissionRequestConcluded()) {
                    showFragment(MedicalPermissionsFragment())
                } else {
                    showFragment(AdditionalPermissionsFragment())
                }
            }

            // Case 7 - data type and additional
            else if (noMedicalRequest) {
                if (!requestPermissionsViewModel.isDataTypePermissionRequestConcluded()) {
                    showFragment(DataTypePermissionsFragment())
                } else {
                    showFragment(AdditionalPermissionsFragment())
                }
            }

            // Case 8 - all three combined
            else {
                if (!requestPermissionsViewModel.isMedicalPermissionRequestConcluded()) {
                    showFragment(MedicalPermissionsFragment())
                } else if (!requestPermissionsViewModel.isDataTypePermissionRequestConcluded()) {
                    showFragment(DataTypePermissionsFragment())
                } else {
                    // After configuration change
                    showFragment(AdditionalPermissionsFragment())
                }
            }
        }

        migrationViewModel.migrationState.observe(this) { migrationState ->
            when (migrationState) {
                is MigrationViewModel.MigrationFragmentState.WithData -> {
                    maybeShowMigrationDialog(migrationState.migrationRestoreState)
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun maybeShowMigrationDialog(migrationRestoreState: MigrationRestoreState) {
        val (migrationUiState, dataRestoreUiState, dataErrorState) = migrationRestoreState

        if (dataRestoreUiState == DataRestoreUiState.IN_PROGRESS) {
            showDataRestoreInProgressDialog(this) { _, _ -> finish() }
        } else if (migrationUiState == MigrationUiState.IN_PROGRESS) {
            showMigrationInProgressDialog(
                this,
                getString(
                    R.string.migration_in_progress_permissions_dialog_content,
                    requestPermissionsViewModel.appMetadata.value?.appName)) { _, _ ->
                    finish()
                }
        } else if (migrationUiState in
            listOf(
                MigrationUiState.ALLOWED_PAUSED,
                MigrationUiState.ALLOWED_NOT_STARTED,
                MigrationUiState.MODULE_UPGRADE_REQUIRED,
                MigrationUiState.APP_UPGRADE_REQUIRED)) {
            showMigrationPendingDialog(
                this,
                getString(
                    R.string.migration_pending_permissions_dialog_content,
                    requestPermissionsViewModel.appMetadata.value?.appName),
                null,
            ) { _, _ ->
                if (requestPermissionsViewModel.isDataTypePermissionRequestConcluded()) {
                    requestPermissionsViewModel.updateAdditionalPermissions(false)
                    requestPermissionsViewModel.requestAdditionalPermissions(getPackageNameExtra())
                } else {
                    requestPermissionsViewModel.updateDataTypePermissions(false)
                    requestPermissionsViewModel.requestDataTypePermissions(getPackageNameExtra())
                }

                handlePermissionResults()
                finish()
            }
        } else if (migrationUiState == MigrationUiState.COMPLETE) {
            maybeShowWhatsNewDialog(this)
        }
    }

    private fun handlePermissionResults(resultCode: Int = RESULT_OK) {
        val results = requestPermissionsViewModel.getPermissionGrants()
        val grants = mutableListOf<Int>()
        val permissionStrings = mutableListOf<String>()

        for ((permission, state) in results) {
            if (state == PermissionState.GRANTED) {
                grants.add(PackageManager.PERMISSION_GRANTED)
            } else {
                grants.add(PackageManager.PERMISSION_DENIED)
            }

            permissionStrings.add(permission.toString())
        }

        val result = Intent()
        result.putExtra(EXTRA_REQUEST_PERMISSIONS_NAMES, permissionStrings.toTypedArray())
        result.putExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS, grants.toIntArray())
        setResult(resultCode, result)
        finish()
    }

    private fun getPermissionStrings(): Array<out String> {
        return intent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES).orEmpty()
    }

    private fun getPackageNameExtra(): String {
        return intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.permission_content, fragment)
            .commit()
    }
}
