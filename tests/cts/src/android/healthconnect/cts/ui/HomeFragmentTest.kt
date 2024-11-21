/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.health.connect.datatypes.StepsRecord
import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.TestAppProxy
import android.healthconnect.cts.lib.UiTestUtils.findObject
import android.healthconnect.cts.lib.UiTestUtils.findText
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.scrollDownTo
import android.healthconnect.cts.lib.UiTestUtils.scrollToEnd
import android.healthconnect.cts.lib.UiTestUtils.verifyTextNotFound
import android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION
import android.healthconnect.cts.phr.utils.PhrDataFactory.getCreateMedicalDataSourceRequest
import android.healthconnect.cts.utils.DataFactory.getEmptyMetadata
import android.healthconnect.cts.utils.TestUtils
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.uiautomator.By
import com.android.healthfitness.flags.Flags.FLAG_NEW_INFORMATION_ARCHITECTURE
import com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD
import com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE
import com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

/** CTS test for HealthConnect Home screen. */
class HomeFragmentTest : HealthConnectBaseTest() {

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    companion object {

        private val APP_A_WITH_READ_WRITE_PERMS: TestAppProxy =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.readWritePerms.A")

        @JvmStatic
        @BeforeClass
        fun setup() {
            if (!TestUtils.isHealthConnectFullySupported()) {
                return
            }

            TestUtils.deleteAllStagedRemoteData()
            TestUtils.deleteAllMedicalData()

            val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
            APP_A_WITH_READ_WRITE_PERMS.insertRecords(
                StepsRecord.Builder(getEmptyMetadata(), now.minusSeconds(30), now, 43).build()
            )
        }

        @JvmStatic
        @AfterClass
        fun teardown() {
            if (!TestUtils.isHealthConnectFullySupported()) {
                return
            }
            TestUtils.deleteAllStagedRemoteData()
            TestUtils.deleteAllMedicalData()
        }
    }

    @Test
    fun homeFragment_opensAppPermissions() {
        context.launchMainActivity {
            scrollDownTo(By.text("App permissions"))
            findTextAndClick("App permissions")

            findText("Allowed access")
            scrollDownTo(By.text("Not allowed access"))
            findText("Not allowed access")
        }
    }

    @Test
    @RequiresFlagsDisabled(FLAG_NEW_INFORMATION_ARCHITECTURE)
    fun homeFragment_oldIa_opensDataManagement() {
        context.launchMainActivity {
            scrollDownTo(By.text("Data and access"))
            findTextAndClick("Data and access")

            findText("Browse data")
            scrollToEnd()
            findText("Manage data")

            findText("Delete all data")
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_INFORMATION_ARCHITECTURE)
    fun homeFragment_newIa_opensDataManagement() {
        context.launchMainActivity {
            scrollDownTo(By.text("Data and access"))
            findTextAndClick("Data and access")

            findText("Activity")
            findText("Steps")
        }
    }

    @Test
    fun homeFragment_opensManageData() {
        context.launchMainActivity {
            scrollToEnd()
            findTextAndClick("Manage data")

            findText("Auto-delete")
            findText("Data sources and priority")
            findText("Set units")
        }
    }

    @Test
    fun homeFragment_recentAccessShownOnHomeScreen() {
        context.launchMainActivity {
            findObject(By.textContains("CtsHealthConnectTest"))
            findObject(By.text("See all recent access"))
        }
    }

    @Test
    fun homeFragment_navigatesToRecentAccess() {
        context.launchMainActivity {
            findTextAndClick("See all recent access")

            findText("Today")
            findObject(By.textContains("CtsHealthConnectTest"))
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun homeFragment_withMedicalData_opensBrowseMedicalRecords() {
        val dataSource =
            APP_A_WITH_READ_WRITE_PERMS.createMedicalDataSource(getCreateMedicalDataSourceRequest())
        APP_A_WITH_READ_WRITE_PERMS.upsertMedicalResource(dataSource.id, FHIR_DATA_IMMUNIZATION)
        context.launchMainActivity {
            scrollToEnd()
            findTextAndClick("Browse health records")
            findText("Vaccines")
        }
    }

    @Test
    @RequiresFlagsDisabled(FLAG_PERSONAL_HEALTH_RECORD)
    fun homeFragment_withMedicalData_flagOff_hidesBrowseMedicalRecords() {
        context.launchMainActivity {
            scrollToEnd()
            verifyTextNotFound("Browse health records")
        }
    }
}
