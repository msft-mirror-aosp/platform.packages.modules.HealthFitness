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
package android.healthconnect.cts.ui.newia

import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.MenstruationPeriodRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.StepsRecord
import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.RecordFactory.newEmptyMetadata
import android.healthconnect.cts.lib.TestAppProxy
import android.healthconnect.cts.lib.UiTestUtils.findObjectAndClick
import android.healthconnect.cts.lib.UiTestUtils.findText
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.scrollDownTo
import android.healthconnect.cts.lib.UiTestUtils.scrollToEnd
import android.healthconnect.cts.lib.UiTestUtils.scrollUpTo
import android.healthconnect.cts.lib.UiTestUtils.verifyObjectNotFound
import android.healthconnect.cts.lib.UiTestUtils.verifyTextNotFound
import android.healthconnect.cts.ui.HealthConnectBaseTest
import android.healthconnect.cts.utils.TestUtils
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.uiautomator.By
import com.android.healthfitness.flags.Flags.FLAG_NEW_INFORMATION_ARCHITECTURE
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** CTS test for Health Connect App Entries fragment in the new IA. */
@RequiresFlagsEnabled(FLAG_NEW_INFORMATION_ARCHITECTURE)
class AppEntriesFragmentTest : HealthConnectBaseTest() {
    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun setup() {
        TestUtils.deleteAllStagedRemoteData()
        insertData()
    }

    @After
    fun tearDown() {
        TestUtils.deleteAllStagedRemoteData()
    }

    companion object {
        private val NOW = Instant.now()
        private val TEST_WRITER_APP =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.readWritePerms.A")
    }

    @Test
    fun appEntriesScreen_displaysCorrectly() {
        context.launchMainActivity {
            scrollDownTo(By.text("App permissions"))
            findTextAndClick("App permissions")
            findTextAndClick("CtsHealthConnectTestAppAWithNormalReadWritePermission")

            scrollToEnd()
            findTextAndClick("See app data")

            findText("CtsHealthConnectTestAppAWithNormalReadWritePermission")

            findText("Activity")
            findTextAndClick("Steps")

            findText("CtsHealthConnectTestAppAWithNormalReadWritePermission")
            verifyTextNotFound("Entries")
            verifyTextNotFound("Access")
            findText("10 steps")

            findTextAndClick("Today")
            findText("Day")
            findText("Week")
            findText("Month")
        }
    }

    @Test
    fun appEntriesScreen_deletesAllData() {
        context.launchMainActivity {
            scrollDownTo(By.text("App permissions"))
            findTextAndClick("App permissions")
            findTextAndClick("CtsHealthConnectTestAppAWithNormalReadWritePermission")

            scrollToEnd()
            findTextAndClick("See app data")

            findText("CtsHealthConnectTestAppAWithNormalReadWritePermission")
            verifyObjectNotFound(By.text("Select all"))
            findObjectAndClick(By.desc("Enter deletion"))
            scrollUpTo(By.text("Select all"))
            findTextAndClick("Select all")
            findObjectAndClick(By.desc("Delete data"))
            findTextAndClick("Delete")
            findTextAndClick("Done")
            findText("No data")
        }
    }

    private fun insertData() {
        TEST_WRITER_APP.insertRecords(
            mutableListOf(
                StepsRecord.Builder(newEmptyMetadata(), NOW, NOW.plusSeconds(2), 10).build(),
                HeartRateRecord.Builder(
                        newEmptyMetadata(),
                        NOW,
                        NOW.plusSeconds(10),
                        listOf(HeartRateRecord.HeartRateSample(140, NOW)),
                    )
                    .build(),
                MenstruationPeriodRecord.Builder(newEmptyMetadata(), NOW, NOW.plusSeconds(10))
                    .build(),
                SleepSessionRecord.Builder(newEmptyMetadata(), NOW, NOW.plusSeconds(1000)).build(),
            )
                as List<Record>?
        )
    }
}
