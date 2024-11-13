/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.shared

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.PackageManager.ResolveInfoFlags
import android.health.connect.HealthConnectManager
import android.health.connect.HealthPermissions
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.AdditionalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.isAdditionalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.isFitnessReadPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.isMedicalReadPermission
import com.android.healthconnect.controller.shared.app.AppPermissionsType
import com.android.healthfitness.flags.AconfigFlagHelper
import com.android.healthfitness.flags.AconfigFlagHelper.isPersonalHealthRecordEnabled
import com.google.common.annotations.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Class that reads permissions declared by Health Connect clients as a string array in their XML
 * resources. See android.health.connect.HealthPermissions
 */
@Singleton
class HealthPermissionReader @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val HEALTH_PERMISSION_GROUP = "android.permission-group.HEALTH"
        private const val RESOLVE_INFO_FLAG: Long = PackageManager.MATCH_ALL.toLong()
        private const val PACKAGE_INFO_PERMISSIONS_FLAG: Long =
            PackageManager.GET_PERMISSIONS.toLong()

        private val medicalPermissions =
            setOf(
                HealthPermissions.WRITE_MEDICAL_DATA,
                HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
                HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
                HealthPermissions.READ_MEDICAL_DATA_LABORATORY_RESULTS,
                HealthPermissions.READ_MEDICAL_DATA_MEDICATIONS,
                HealthPermissions.READ_MEDICAL_DATA_PERSONAL_DETAILS,
                HealthPermissions.READ_MEDICAL_DATA_PRACTITIONER_DETAILS,
                HealthPermissions.READ_MEDICAL_DATA_PREGNANCY,
                HealthPermissions.READ_MEDICAL_DATA_PROCEDURES,
                HealthPermissions.READ_MEDICAL_DATA_SOCIAL_HISTORY,
                HealthPermissions.READ_MEDICAL_DATA_VACCINES,
                HealthPermissions.READ_MEDICAL_DATA_VISITS,
                HealthPermissions.READ_MEDICAL_DATA_VITAL_SIGNS,
            )
    }

    /**
     * Returns a list of app packageNames that have declared at least one health permission
     * (additional or data type).
     */
    fun getAppsWithHealthPermissions(): List<String> {
        return try {
            appsWithDeclaredIntent().filter { getValidHealthPermissions(it).isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAppsWithFitnessPermissions(): List<String> {
        return try {
            appsWithDeclaredIntent().filter {
                getValidHealthPermissions(it)
                    .filterIsInstance<HealthPermission.FitnessPermission>()
                    .isNotEmpty()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAppsWithMedicalPermissions(): List<String> {
        return try {
            appsWithDeclaredIntent().filter {
                getValidHealthPermissions(it)
                    .filterIsInstance<HealthPermission.MedicalPermission>()
                    .isNotEmpty()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun appsWithDeclaredIntent(): List<String> {
        return context.packageManager
            .queryIntentActivities(getRationaleIntent(), ResolveInfoFlags.of(RESOLVE_INFO_FLAG))
            .map { it.activityInfo.packageName }
            .distinct()
    }

    /**
     * Identifies apps that have the old permissions declared - they need to update before
     * continuing to sync with Health Connect.
     */
    fun getAppsWithOldHealthPermissions(): List<String> {
        return try {
            val oldPermissionsRationale = "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE"
            val oldPermissionsMetaDataKey = "health_permissions"
            val intent = Intent(oldPermissionsRationale)
            val resolveInfoList =
                context.packageManager
                    .queryIntentActivities(intent, PackageManager.GET_META_DATA)
                    .filter { resolveInfo -> resolveInfo.activityInfo != null }
                    .filter { resolveInfo -> resolveInfo.activityInfo.metaData != null }
                    .filter { resolveInfo ->
                        resolveInfo.activityInfo.metaData.getInt(oldPermissionsMetaDataKey) != -1
                    }

            resolveInfoList.map { it.activityInfo.packageName }.distinct()
        } catch (e: NameNotFoundException) {
            emptyList()
        }
    }

    /**
     * Returns a list of health permissions declared by an app that can be rendered in our UI. This
     * also filters out invalid additional permissions.
     */
    fun getValidHealthPermissions(packageName: String): List<HealthPermission> {
        return try {
            val permissions = getDeclaredHealthPermissions(packageName)
            val declaredPermissions =
                permissions.mapNotNull { permission -> parsePermission(permission) }
            if (isPersonalHealthRecordEnabled()) {
                maybeFilterOutAdditionalIfNotValid(declaredPermissions)
            } else {
                declaredPermissions
            }
        } catch (e: NameNotFoundException) {
            emptyList()
        }
    }

    /**
     * Filers out invalid additional permissions. READ_HEALTH_DATA_HISTORY is valid if at least one
     * FITNESS READ permission is declared. READ_HEALTH_DATA_IN_BACKGROUND is valid if at least one
     * HEALTH READ permission is declared.
     */
    @VisibleForTesting
    fun maybeFilterOutAdditionalIfNotValid(
        declaredPermissions: List<HealthPermission>
    ): List<HealthPermission> {
        val historyReadDeclared =
            declaredPermissions.filterIsInstance<AdditionalPermission>().any {
                it == AdditionalPermission.READ_HEALTH_DATA_HISTORY
            }
        val backgroundReadDeclared =
            declaredPermissions.filterIsInstance<AdditionalPermission>().any {
                it == AdditionalPermission.READ_HEALTH_DATA_IN_BACKGROUND
            }
        val atLeastOneFitnessReadDeclared = declaredPermissions.any { isFitnessReadPermission(it) }
        val atLeastOneMedicalReadDeclared = declaredPermissions.any { isMedicalReadPermission(it) }
        val atLeastOneHealthReadDeclared =
            atLeastOneFitnessReadDeclared || atLeastOneMedicalReadDeclared

        var result = declaredPermissions.toMutableList()
        if (historyReadDeclared && !atLeastOneFitnessReadDeclared) {
            result =
                result
                    .filterNot { it == AdditionalPermission.READ_HEALTH_DATA_HISTORY }
                    .toMutableList()
        }
        if (backgroundReadDeclared && !atLeastOneHealthReadDeclared) {
            result =
                result
                    .filterNot { it == AdditionalPermission.READ_HEALTH_DATA_IN_BACKGROUND }
                    .toMutableList()
        }
        return result.toList()
    }

    /** Returns a list of health permissions that are declared by an app. */
    fun getDeclaredHealthPermissions(packageName: String): List<String> {
        return try {
            val appInfo =
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageInfoFlags.of(PACKAGE_INFO_PERMISSIONS_FLAG),
                )
            val healthPermissions = getHealthPermissions()
            appInfo.requestedPermissions?.filter { it in healthPermissions }.orEmpty()
        } catch (e: NameNotFoundException) {
            emptyList()
        }
    }

    fun getAppPermissionsType(packageName: String): AppPermissionsType {
        val permissions = getValidHealthPermissions(packageName)
        val hasAtLeastOneFitnessPermission =
            permissions.firstOrNull { it is HealthPermission.FitnessPermission } != null
        val hasAtLeastOneMedicalPermission =
            permissions.firstOrNull { it is HealthPermission.MedicalPermission } != null

        return if (hasAtLeastOneFitnessPermission && hasAtLeastOneMedicalPermission) {
            AppPermissionsType.COMBINED_PERMISSIONS
        } else if (hasAtLeastOneFitnessPermission) {
            AppPermissionsType.FITNESS_PERMISSIONS_ONLY
        } else if (hasAtLeastOneMedicalPermission) {
            AppPermissionsType.MEDICAL_PERMISSIONS_ONLY
        } else {
            // All Fitness, Medical and Combined screens handle the empty state so any of those can
            // be returned here.
            AppPermissionsType.FITNESS_PERMISSIONS_ONLY
        }
    }

    /**
     * When PHR flag is on, returns valid additional permissions that we can display in our UI. An
     * additional permission is valid if the correct read permissions are declared.
     *
     * When PHR flag is off, returns additional permissions that are declared.
     */
    fun getAdditionalPermissions(packageName: String): List<String> {
        return if (isPersonalHealthRecordEnabled()) {
            getValidHealthPermissions(packageName)
                .map { it.toString() }
                .filter { perm -> isAdditionalPermission(perm) && !shouldHidePermission(perm) }
        } else {
            getDeclaredHealthPermissions(packageName).filter { perm ->
                isAdditionalPermission(perm) && !shouldHidePermission(perm)
            }
        }
    }

    fun isRationaleIntentDeclared(packageName: String): Boolean {
        val intent = getRationaleIntent(packageName)
        val resolvedInfo =
            context.packageManager.queryIntentActivities(
                intent,
                ResolveInfoFlags.of(RESOLVE_INFO_FLAG),
            )
        return resolvedInfo.any { info -> info.activityInfo.packageName == packageName }
    }

    fun getApplicationRationaleIntent(packageName: String): Intent {
        val intent = getRationaleIntent(packageName)
        val resolvedInfo =
            context.packageManager.queryIntentActivities(
                intent,
                ResolveInfoFlags.of(RESOLVE_INFO_FLAG),
            )
        resolvedInfo.forEach { info -> intent.setClassName(packageName, info.activityInfo.name) }
        return intent
    }

    private fun parsePermission(permission: String): HealthPermission? {
        return try {
            HealthPermission.fromPermissionString(permission)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Returns a list of all health permissions in the HEALTH permission group. */
    @VisibleForTesting
    fun getHealthPermissions(): List<String> {
        val permissions =
            context.packageManager.queryPermissionsByGroup(HEALTH_PERMISSION_GROUP, 0).map {
                permissionInfo ->
                permissionInfo.name
            }
        return permissions.filterNot { permission -> shouldHidePermission(permission) }
    }

    /** Returns a list of all system health permissions in the HEALTH permission group. */
    fun getSystemHealthPermissions(): List<String> {
        val permissions =
            context.packageManager
                .queryPermissionsByGroup(HEALTH_PERMISSION_GROUP, 0)
                .map { permissionInfo -> permissionInfo.name }
                .filter { permissionName ->
                    !AppOpsManager.permissionToOp(permissionName)
                        .equals(AppOpsManager.OPSTR_READ_WRITE_HEALTH_DATA)
                }
        return permissions
    }

    fun shouldHidePermission(permission: String): Boolean {
        return when (permission) {
            in medicalPermissions -> !isPersonalHealthRecordEnabled()
            HealthPermissions.READ_ACTIVITY_INTENSITY,
            HealthPermissions.WRITE_ACTIVITY_INTENSITY ->
                !AconfigFlagHelper.isActivityIntensityEnabled()
            else -> false
        }
    }

    private fun getRationaleIntent(packageName: String? = null): Intent {
        val intent =
            Intent(Intent.ACTION_VIEW_PERMISSION_USAGE).apply {
                addCategory(HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS)
                if (packageName != null) {
                    setPackage(packageName)
                }
            }
        return intent
    }
}
