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
package android.healthconnect.cts.ui.permissions

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import android.healthconnect.cts.lib.ActivityLauncher.launchRequestPermissionActivity
import android.healthconnect.cts.lib.UiTestUtils.TEST_APP_2_PACKAGE_NAME
import android.healthconnect.cts.lib.UiTestUtils.findText
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.grantPermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.revokePermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.scrollDownTo
import android.healthconnect.cts.lib.UiTestUtils.verifyTextNotFound
import android.healthconnect.cts.ui.HealthConnectBaseTest
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.uiautomator.By
import com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD
import com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test

class MedicalPermissionsRequestUITest : HealthConnectBaseTest() {

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    // TODO
    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun requestMedicalWrite_allow_grantsPermission() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.WRITE_MEDICAL_DATA,
        )
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_2_PACKAGE_NAME,
            permissions = listOf(HealthPermissions.WRITE_MEDICAL_DATA),
        ) {
            findText("Allow Health Connect cts test app 2 to access your health records?")
            findText("Data to share")

            findTextAndClick("Allow")
            assertPermGrantedForApp(TEST_APP_2_PACKAGE_NAME, HealthPermissions.WRITE_MEDICAL_DATA)
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun requestMedicalWrite_dontAllow_doesNotGrantPermission() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.WRITE_MEDICAL_DATA,
        )
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_2_PACKAGE_NAME,
            permissions = listOf(HealthPermissions.WRITE_MEDICAL_DATA),
        ) {
            findText("Allow Health Connect cts test app 2 to access your health records?")
            findText("Data to share")

            findTextAndClick("Don't allow")
            assertPermNotGrantedForApp(
                TEST_APP_2_PACKAGE_NAME,
                HealthPermissions.WRITE_MEDICAL_DATA,
            )
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun requestMedicalReadAndWrite_showsRequestedPermissions() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.WRITE_MEDICAL_DATA,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
        )
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_2_PACKAGE_NAME,
            permissions =
                listOf(
                    HealthPermissions.WRITE_MEDICAL_DATA,
                    HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
                    HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
                ),
        ) {
            findText("Allow Health Connect cts test app 2 to access your health records?")
            scrollDownTo(By.text("Allergies"))
            findText("Allergies")

            scrollDownTo(By.text("Conditions"))
            findText("Conditions")

            scrollDownTo(By.text("All health records"))
            findText("All health records")
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun requestMedicalReadAndWrite_doesNotShowGrantedPermissions() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.WRITE_MEDICAL_DATA,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
        )
        grantPermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
        )
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_2_PACKAGE_NAME,
            permissions =
                listOf(
                    HealthPermissions.WRITE_MEDICAL_DATA,
                    HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
                    HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
                ),
        ) {
            findText("Allow Health Connect cts test app 2 to access your health records?")
            scrollDownTo(By.text("Allergies"))
            findText("Allergies")

            verifyTextNotFound("Conditions")

            scrollDownTo(By.text("All health records"))
            findText("All health records")
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun requestMedicalReadAndWrite_grantsOnlyRequestedPermissions() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.WRITE_MEDICAL_DATA,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
        )
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_2_PACKAGE_NAME,
            permissions =
                listOf(
                    HealthPermissions.WRITE_MEDICAL_DATA,
                    HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
                    HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
                ),
        ) {
            findText("Allow Health Connect cts test app 2 to access your health records?")
            scrollDownTo(By.text("Allergies"))
            findTextAndClick("Allergies")

            scrollDownTo(By.text("Conditions"))
            findText("Conditions")

            scrollDownTo(By.text("All health records"))
            findTextAndClick("All health records")

            findTextAndClick("Allow")

            assertPermGrantedForApp(TEST_APP_2_PACKAGE_NAME, HealthPermissions.WRITE_MEDICAL_DATA)
            assertPermGrantedForApp(
                TEST_APP_2_PACKAGE_NAME,
                HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
            )
            assertPermNotGrantedForApp(
                TEST_APP_2_PACKAGE_NAME,
                HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
            )
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun requestMedicalReadAndWrite_allowAll_grantsAllRequestedPermissions() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.WRITE_MEDICAL_DATA,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
        )
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_2_PACKAGE_NAME,
            permissions =
                listOf(
                    HealthPermissions.WRITE_MEDICAL_DATA,
                    HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
                    HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
                ),
        ) {
            findText("Allow Health Connect cts test app 2 to access your health records?")
            scrollDownTo(By.text("Allow all"))
            findTextAndClick("Allow all")

            findTextAndClick("Allow")

            assertPermGrantedForApp(TEST_APP_2_PACKAGE_NAME, HealthPermissions.WRITE_MEDICAL_DATA)
            assertPermGrantedForApp(
                TEST_APP_2_PACKAGE_NAME,
                HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
            )
            assertPermGrantedForApp(
                TEST_APP_2_PACKAGE_NAME,
                HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
            )
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun requestMedicalReadAndWrite_dontAllow_doesNotGrantPermissions() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.WRITE_MEDICAL_DATA,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_2_PACKAGE_NAME,
            HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
        )
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_2_PACKAGE_NAME,
            permissions =
                listOf(
                    HealthPermissions.WRITE_MEDICAL_DATA,
                    HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
                    HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
                ),
        ) {
            findText("Allow Health Connect cts test app 2 to access your health records?")

            findTextAndClick("Don't allow")

            assertPermNotGrantedForApp(
                TEST_APP_2_PACKAGE_NAME,
                HealthPermissions.WRITE_MEDICAL_DATA,
            )
            assertPermNotGrantedForApp(
                TEST_APP_2_PACKAGE_NAME,
                HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES,
            )
            assertPermNotGrantedForApp(
                TEST_APP_2_PACKAGE_NAME,
                HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
            )
        }
    }

    @Throws(Exception::class)
    private fun assertPermGrantedForApp(packageName: String, permName: String) {
        Truth.assertThat(context.packageManager.checkPermission(permName, packageName))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    @Throws(Exception::class)
    private fun assertPermNotGrantedForApp(packageName: String, permName: String) {
        Truth.assertThat(context.packageManager.checkPermission(permName, packageName))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
    }
}
