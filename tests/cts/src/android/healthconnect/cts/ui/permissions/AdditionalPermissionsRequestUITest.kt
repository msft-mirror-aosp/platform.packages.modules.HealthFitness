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

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.health.connect.HealthConnectManager
import android.health.connect.HealthPermissions.*
import android.healthconnect.cts.lib.ActivityLauncher.launchRequestPermissionActivity
import android.healthconnect.cts.lib.UiTestUtils.TEST_APP_PACKAGE_NAME
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import android.healthconnect.cts.lib.UiTestUtils.grantPermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.revokePermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.waitDisplayed
import android.healthconnect.cts.ui.HealthConnectBaseTest
import android.healthconnect.cts.utils.ProxyActivity
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.UiAutomatorUtils2.getUiDevice
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AdditionalPermissionsRequestUITest : HealthConnectBaseTest() {

    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @get:Rule val freezeRotationRule = FreezeRotationRule()

    @Before
    fun setup() {
        revokePermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_HISTORY)
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_IN_BACKGROUND)
        with(getUiDevice()) { executeShellCommand("settings put system font_scale 0.85") }
    }

    @After
    fun tearDown() {
        revokePermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_HISTORY)
        revokePermissionViaPackageManager(
            context, TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_IN_BACKGROUND)
        revokePermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
    }

    @Test
    fun requestAdditionalPermissions_showsBothAdditionalPermissions_grantsBoth() {
        val permissions = listOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME, permissions = permissions) {
                waitDisplayed(By.text("Allow additional access for Health Connect cts test app?"))
                clickOnText("Access past data")
                clickOnText("Access data in the background")
                clickOnText("Allow")

                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_HISTORY)
                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_IN_BACKGROUND)
            }
    }

    @Test
    fun requestAdditionalPermissions_showsBothAdditionalPermissions_deniesBoth() {
        val permissions = listOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME, permissions = permissions) {
                waitDisplayed(By.text("Allow additional access for Health Connect cts test app?"))
                clickOnText("Access past data")
                clickOnText("Access data in the background")
                clickOnText("Don't allow")

                assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_HISTORY)
                assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_IN_BACKGROUND)
            }
    }

    @Test
    fun requestAdditionalPermissions_showsBothAdditionalPermissions_grantsOnlySelected() {
        val permissions = listOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME, permissions = permissions) {
                waitDisplayed(By.text("Allow additional access for Health Connect cts test app?"))
                clickOnText("Access past data")
                clickOnText("Allow")

                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_HISTORY)
                assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_IN_BACKGROUND)
            }
    }

    @Test
    fun requestAdditionalPermissions_showsOnlyNotGrantedPermissions() {
        val permissions = listOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        grantPermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_HISTORY)
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME, permissions = permissions) {
                waitDisplayed(
                    By.text("Allow Health Connect cts test app to access data in the background?"))
                clickOnText("Allow")

                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_HISTORY)
                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_IN_BACKGROUND)
            }
    }

    @Test
    fun requestAdditionalPermissions_noReadPermissions_returnsResultCanceled() {
        revokePermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        val permissions = listOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)

        val intent =
            Intent(HealthConnectManager.ACTION_REQUEST_HEALTH_PERMISSIONS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, permissions.toTypedArray())
                putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME)
            }

        SystemUtil.runWithShellPermissionIdentity(
            {
                val result = ProxyActivity.launchActivityForResult(intent)
                Truth.assertThat(result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
            },
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS)
    }

    @Test
    fun requestCombinedPermissions_showsDataTypeAndAdditionalPermissions() {
        revokePermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, READ_HEIGHT)
        revokePermissionViaPackageManager(context, TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)
        val permissions =
            listOf(
                READ_HEIGHT,
                WRITE_BODY_FAT,
                READ_HEALTH_DATA_HISTORY,
                READ_HEALTH_DATA_IN_BACKGROUND)
        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME, permissions = permissions) {
                waitDisplayed(
                    By.text("Allow Health Connect cts test app to access Health Connect?"))
                clickOnText("Height")
                clickOnText("Allow")

                waitDisplayed(By.text("Allow additional access for Health Connect cts test app?"))
                clickOnText("Access past data")
                clickOnText("Allow")

                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEIGHT)
                assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, WRITE_BODY_FAT)
                assertPermGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_HISTORY)
                assertPermNotGrantedForApp(TEST_APP_PACKAGE_NAME, READ_HEALTH_DATA_IN_BACKGROUND)
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
