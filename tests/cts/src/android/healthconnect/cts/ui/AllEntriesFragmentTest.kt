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

import android.health.connect.datatypes.StepsRecord
import android.healthconnect.cts.lib.ActivityLauncher.launchDataActivity
import android.healthconnect.cts.lib.RecordFactory.newEmptyMetadata
import android.healthconnect.cts.lib.UiTestUtils.findText
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.verifyTextNotFound
import android.healthconnect.cts.utils.TestUtils
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.healthfitness.flags.Flags.FLAG_NEW_INFORMATION_ARCHITECTURE
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** CTS test for Health Connect All Entries fragment in the new IA. */
@RequiresFlagsEnabled(FLAG_NEW_INFORMATION_ARCHITECTURE)
class AllEntriesFragmentTest : HealthConnectBaseTest() {

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
        private val TODAY =
            LocalDate.now(ZoneId.systemDefault()).atTime(0, 0).atZone(ZoneId.systemDefault())
    }

    @Test
    fun allEntries_dayView_showsDataOnlyFromDay() {
        context.launchDataActivity {
            findText("Activity")
            findTextAndClick("Steps")
            findText("Entries")
            findText("Access")

            findText("Today")
            findText("10 steps")
            verifyTextNotFound("20 steps")
        }
    }

    @Test
    fun allEntries_navigationView_showsDayWeekMonth() {
        context.launchDataActivity {
            findText("Activity")
            findTextAndClick("Steps")
            findText("Entries")
            findText("Access")

            findTextAndClick("Today")
            findText("Day")
            findText("Week")
            findText("Month")
        }
    }

    @Test
    fun allEntries_clickOnAccessTab_navigatesToAccessScreen() {
        context.launchDataActivity {
            findText("Activity")
            findTextAndClick("Steps")
            findText("Entries")

            findTextAndClick("Access")
            findText("Can read steps")
            findText("Can write steps")
        }
    }

    // TODO (b/360887258) Deletion tests

    private fun insertData() {
        TestUtils.insertRecords(
            mutableListOf(
                StepsRecord.Builder(
                        newEmptyMetadata(),
                        TODAY.toInstant(),
                        TODAY.plusMinutes(2).toInstant(),
                        10,
                    )
                    .build(),
                StepsRecord.Builder(
                        newEmptyMetadata(),
                        TODAY.minusDays(1).toInstant(),
                        TODAY.minusDays(1).plusMinutes(1).toInstant(),
                        20,
                    )
                    .build(),
            )
        )
    }
}
