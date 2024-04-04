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

import android.health.connect.TimeInstantRangeFilter
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.StepsRecord
import android.healthconnect.cts.lib.ActivityLauncher.launchDataActivity
import android.healthconnect.cts.lib.TestAppProxy
import android.healthconnect.cts.lib.UiTestUtils
import android.healthconnect.cts.lib.UiTestUtils.clickOnContentDescription
import android.healthconnect.cts.lib.UiTestUtils.clickOnDescContains
import android.healthconnect.cts.lib.UiTestUtils.clickOnText
import android.healthconnect.cts.lib.UiTestUtils.waitDisplayed
import android.healthconnect.cts.utils.RevokedHealthPermissionRule
import android.healthconnect.cts.utils.TestUtils
import android.healthconnect.cts.utils.TestUtils.insertRecords
import android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords
import androidx.test.uiautomator.By
import java.time.Instant
import java.time.Period.ofDays
import java.time.ZoneId
import org.junit.AfterClass
import org.junit.Rule
import org.junit.Test

/** CTS test for HealthConnect Data entries screen. */
class DataEntriesFragmentTest : HealthConnectBaseTest() {

    companion object {
        private const val TAG = "DataEntriesFragmentTest"

        val APP_A =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.readWritePerms.A")

        val APP_B =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.readWritePerms.B")

        @JvmStatic
        @AfterClass
        fun tearDown() {
            if (!TestUtils.isHardwareSupported()) {
                return
            }
            verifyDeleteRecords(
                StepsRecord::class.java,
                TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.now())
                    .build())
            verifyDeleteRecords(
                DistanceRecord::class.java,
                TimeInstantRangeFilter.Builder()
                    .setStartTime(Instant.EPOCH)
                    .setEndTime(Instant.now())
                    .build())
        }
    }

    @JvmField
    @Rule
    val revokedWriteDistanceARule =
        RevokedHealthPermissionRule(
            APP_A.getPackageName(), "android.permission.health.WRITE_DISTANCE")

    @JvmField
    @Rule
    val revokedWriteDistanceBRule =
        RevokedHealthPermissionRule(
            APP_B.getPackageName(), "android.permission.health.WRITE_DISTANCE")

    @JvmField
    @Rule
    val revokedReadDistanceARule =
        RevokedHealthPermissionRule(
            APP_A.getPackageName(), "android.permission.health.READ_DISTANCE")

    @JvmField
    @Rule
    val revokedReadDistanceBRule =
        RevokedHealthPermissionRule(
            APP_B.getPackageName(), "android.permission.health.READ_DISTANCE")

    @Test
    fun dataEntries_showsInsertedEntry() {
        insertRecords(listOf(UiTestUtils.distanceRecordFromTestApp()))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")
            clickOnText("See all entries")

            waitDisplayed(By.text("0.5 km"))
        }
    }

    @Test
    fun dataEntries_changeUnit_showsUpdatedUnit() {
        insertRecords(listOf(UiTestUtils.distanceRecordFromTestApp()))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")

            clickOnText("See all entries")
            clickOnContentDescription("More options")
            clickOnText("Set units")
            clickOnText("Distance")
            clickOnText("Kilometers")
            clickOnContentDescription("Navigate up")

            waitDisplayed(By.text("0.5 km"))
        }
    }

    @Test
    fun dataEntries_deletesData() {
        insertRecords(listOf(UiTestUtils.distanceRecordFromTestApp()))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")
            clickOnText("See all entries")

            // Delete entry
            clickOnDescContains("Delete data entry")
            clickOnText("Delete")
            clickOnText("Done")
        }
    }

    @Test
    fun dataEntries_changeDate_updatesSelectedDate() {
        insertRecords(listOf(UiTestUtils.distanceRecordFromTestApp()))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")
            clickOnText("See all entries")

            clickOnDescContains(Instant.now().atZone(ZoneId.systemDefault()).year.toString())
            clickOnText("1")
            clickOnText("OK")
        }
    }

    @Test
    fun dataEntries_navigateToYesterday() {
        insertRecords(listOf(UiTestUtils.distanceRecordFromTestApp(Instant.now().minus(ofDays(1)))))
        context.launchDataActivity {
            clickOnText("Activity")
            clickOnText("Distance")
            clickOnText("See all entries")

            clickOnContentDescription("Previous day")

            waitDisplayed(By.text("0.5 km"))
        }
    }
}
