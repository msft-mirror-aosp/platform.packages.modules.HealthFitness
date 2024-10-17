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
import android.health.connect.datatypes.HeightRecord
import android.health.connect.datatypes.HydrationRecord
import android.health.connect.datatypes.OvulationTestRecord
import android.health.connect.datatypes.OvulationTestRecord.OvulationTestResult
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.units.Length
import android.health.connect.datatypes.units.Volume
import android.healthconnect.cts.lib.ActivityLauncher.launchDataActivity
import android.healthconnect.cts.lib.RecordFactory.newEmptyMetadata
import android.healthconnect.cts.lib.UiTestUtils.findObjectAndClick
import android.healthconnect.cts.lib.UiTestUtils.findText
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.scrollDownTo
import android.healthconnect.cts.lib.UiTestUtils.scrollUpTo
import android.healthconnect.cts.lib.UiTestUtils.verifyObjectNotFound
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

/** CTS test for Health Connect All Data fragment in the new IA. */
@RequiresFlagsEnabled(FLAG_NEW_INFORMATION_ARCHITECTURE)
class AllDataFragmentTest : HealthConnectBaseTest() {

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
        private val NOW: Instant = Instant.parse("2024-01-20T07:06:05.432Z")
    }

    @Test
    fun allDataFragment_showsAllAvailableDataTypes() {
        context.launchDataActivity {
            findText("Activity")
            findText("Steps")
            scrollDownTo(By.text("Body measurements"))
            findText("Body measurements")
            findText("Height")
            scrollDownTo(By.text("Cycle tracking"))
            findText("Cycle tracking")
            findText("Ovulation test")
            scrollDownTo(By.text("Sleep"))
            findText("Sleep")
            scrollDownTo(By.text("Vitals"))
            findText("Vitals")
            findText("Heart rate")
        }
    }

    @Test
    fun allDataFragment_clickOnDataSourcesIcon_navigatesToDataSources() {
        context.launchDataActivity {
            findObjectAndClick(By.desc("Data sources and priority"))
            findText("App sources")
        }
    }

    @Test
    fun allDataFragment_deletesAllData() {
        context.launchDataActivity {
            findText("Activity")
            findText("Steps")
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

    @Test
    fun allDataFragment_clickOnPermissionType_navigatesToEntriesAndAccess() {
        context.launchDataActivity {
            findText("Activity")
            findTextAndClick("Steps")

            findText("Entries")
            findText("Access")
        }
    }

    private fun insertData() {
        TestUtils.insertRecords(
            mutableListOf(
                StepsRecord.Builder(newEmptyMetadata(), NOW, NOW.plusSeconds(2), 10).build(),
                HeightRecord.Builder(newEmptyMetadata(), NOW, Length.fromMeters(1.75)).build(),
                HeartRateRecord.Builder(
                        newEmptyMetadata(),
                        NOW,
                        NOW.plusSeconds(10),
                        listOf(HeartRateRecord.HeartRateSample(140, NOW)),
                    )
                    .build(),
                HydrationRecord.Builder(
                        newEmptyMetadata(),
                        NOW,
                        NOW.plusSeconds(100),
                        Volume.fromLiters(0.5),
                    )
                    .build(),
                OvulationTestRecord.Builder(
                        newEmptyMetadata(),
                        NOW,
                        OvulationTestResult.RESULT_INCONCLUSIVE,
                    )
                    .build(),
                SleepSessionRecord.Builder(newEmptyMetadata(), NOW, NOW.plusSeconds(1000)).build(),
            )
        )
    }
}
