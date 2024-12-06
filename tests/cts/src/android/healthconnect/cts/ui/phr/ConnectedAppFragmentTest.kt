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
package android.healthconnect.cts.ui.phr

import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.UiTestUtils.findText
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.scrollDownTo
import android.healthconnect.cts.lib.UiTestUtils.scrollDownToAndClick
import android.healthconnect.cts.ui.HealthConnectBaseTest
import android.healthconnect.cts.utils.TestUtils
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.uiautomator.By
import com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD
import com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
class ConnectedAppFragmentTest : HealthConnectBaseTest() {
    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setup() {
        TestUtils.deleteAllStagedRemoteData()
    }

    @Test
    fun appWithMedicalAndFitnessPermissions_showsCombinedPermssionsScreen() {
        context.launchMainActivity {
            scrollDownTo(By.text("App permissions"))
            findTextAndClick("App permissions")
            scrollDownTo(By.text("Health Connect cts test app 2"))
            findTextAndClick("Health Connect cts test app 2")

            findText("Health Connect cts test app 2")
            findText("Permissions")
            scrollDownTo(By.text("Fitness and wellness"))
            findText("Fitness and wellness")
            scrollDownTo(By.text("Health records"))
            findText("Health records")
            scrollDownTo(By.text("Additional access"))
            findText("Additional access")

            scrollDownTo(By.text("Manage app"))
            findText("Manage app")
            scrollDownTo(By.text("See app data"))
            findText("See app data")
            scrollDownTo(By.text("Remove access for this app"))
            findText("Remove access for this app")
        }
    }

    @Test
    fun appWithFitnessPermissionsOnly_showsFitnessPermissionsScreen() {
        context.launchMainActivity {
            scrollDownToAndClick(By.text("App permissions"))
            scrollDownToAndClick(By.text("CtsHealthConnectTestAppBWithNormalReadWritePermission"))

            scrollDownTo(By.text("Allowed to read"))
            scrollDownTo(By.text("Allowed to write"))

            scrollDownTo(By.text("See app data"))
            findText("See app data")
        }
    }
}
