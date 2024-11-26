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

package android.healthconnect.cts.ui

import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.ActivityLauncher.launchRequestPermissionActivity
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.revokePermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.scrollDownTo
import android.healthconnect.cts.lib.UiTestUtils.scrollDownToAndClick
import android.healthconnect.cts.ui.BaseDataTypeTest.Companion.APP_WITH_READ_WRITE_PERMISSIONS
import android.healthconnect.cts.ui.BaseDataTypeTest.Companion.APP_WITH_READ_WRITE_PERMISSIONS_LABEL
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.uiautomator.By
import org.junit.Before
import org.junit.Rule
import org.junit.Test

abstract class BaseDataTypeDisabledTest : HealthConnectBaseTest() {

    abstract val permissions: List<String>
    abstract val anotherPermission: String
    abstract val anotherPermissionString: String

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setUp() {
        assertPermissionGranted(anotherPermission, APP_WITH_READ_WRITE_PERMISSIONS)

        buildList {
                addAll(permissions)
                add(anotherPermission)
            }
            .forEach {
                revokePermissionViaPackageManager(context, APP_WITH_READ_WRITE_PERMISSIONS, it)
                assertPermissionDenied(it, APP_WITH_READ_WRITE_PERMISSIONS)
            }
    }

    @Test
    fun requestPermissions_allowAll_permissionsNotGranted() {
        context.launchRequestPermissionActivity(
            packageName = APP_WITH_READ_WRITE_PERMISSIONS,
            permissions =
                buildList {
                    addAll(permissions)
                    add(anotherPermission)
                },
        ) {
            scrollDownTo(By.text(anotherPermissionString))
            findTextAndClick("Allow all")
            findTextAndClick("Allow")
        }

        permissions.forEach { assertPermissionDenied(it, APP_WITH_READ_WRITE_PERMISSIONS) }
        assertPermissionGranted(anotherPermission, APP_WITH_READ_WRITE_PERMISSIONS)
    }

    @Test
    fun appPermissions_allowAll_permissionsToGranted() {
        context.launchMainActivity {
            scrollDownToAndClick(By.text("App permissions"))
            findTextAndClick(APP_WITH_READ_WRITE_PERMISSIONS_LABEL)
            findTextAndClick("Fitness and wellness")
            findTextAndClick("Allow all")
        }

        permissions.forEach { assertPermissionDenied(it, APP_WITH_READ_WRITE_PERMISSIONS) }
        assertPermissionGranted(anotherPermission, APP_WITH_READ_WRITE_PERMISSIONS)
    }
}
