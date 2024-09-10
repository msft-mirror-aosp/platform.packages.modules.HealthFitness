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

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.ExerciseSessionType
import android.health.connect.datatypes.MindfulnessSessionRecord
import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.ActivityLauncher.launchRequestPermissionActivity
import android.healthconnect.cts.lib.RecordFactory.newEmptyMetadata
import android.healthconnect.cts.lib.UiTestUtils.TEST_APP_PACKAGE_NAME
import android.healthconnect.cts.lib.UiTestUtils.findObject
import android.healthconnect.cts.lib.UiTestUtils.findObjectAndClick
import android.healthconnect.cts.lib.UiTestUtils.findText
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.navigateUp
import android.healthconnect.cts.lib.UiTestUtils.revokePermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.scrollDownTo
import android.healthconnect.cts.lib.UiTestUtils.scrollToEnd
import android.healthconnect.cts.lib.UiTestUtils.verifyObjectNotFound
import android.healthconnect.cts.lib.UiTestUtils.verifyTextNotFound
import android.healthconnect.cts.lib.UiTestUtils.waitDisplayed
import android.healthconnect.cts.lib.UiTestUtils.waitForObjectNotFound
import android.healthconnect.cts.utils.AssumptionCheckerRule
import android.healthconnect.cts.utils.TestUtils
import android.healthconnect.cts.utils.TestUtils.readAllRecords
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.uiautomator.By
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.healthfitness.flags.Flags
import com.google.common.truth.Correspondence.transforming
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MindfulnessTest : HealthConnectBaseTest() {

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @get:Rule val freezeRotationRule = FreezeRotationRule()

    @get:Rule
    var mSupportedHardwareRule =
        AssumptionCheckerRule(
            { TestUtils.isHardwareSupported() },
            "Tests should run on supported hardware only.",
        )

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
        private val YESTERDAY_11AM =
            LocalDate.now(ZoneId.systemDefault())
                .minusDays(1)
                .atTime(11, 0)
                .atZone(ZoneId.systemDefault())
    }

    @RequiresFlagsEnabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun dataAndAccess_seeAllEntries_flagEnabled_showsMindfulness() {
        context.launchMainActivity {
            findTextAndClick("Data and access")
            findText("Wellness")
            findTextAndClick("See all categories")

            scrollToEnd()
            findTextAndClick("Wellness")
            findText("Delete wellness data")
            findTextAndClick("Mindfulness")
            scrollToEnd()
            findTextAndClick("See all entries")

            findText("No data")
            findObjectAndClick(By.desc("Previous day"))
            waitForObjectNotFound(By.text("No data"), timeout = Duration.ofSeconds(1))

            findText("11:00 AM - 11:15 AM • ${context.packageName}")
            findText("foo-notes")
            // Make sure that clicking on a Mindfulness entry does not open the details screen.
            findTextAndClick("foo-title, Meditation")

            findText("29m, Unknown type")
            findObject(
                By.desc("Delete data entry from 12:00 PM to 12:29 PM • ${context.packageName}")
            )
            findObject(By.desc("Previous day"))

            // Delete a specific Mindfulness session.
            findObjectAndClick(
                By.descStartsWith(
                    "Delete data entry from 11:00 AM to 11:15 AM • ${context.packageName}"
                )
            )
            findTextAndClick("Delete")
            findTextAndClick("Done")
            findText("29m, Unknown type")
            verifyObjectNotFound(By.text("foo-title, Meditation"))

            assertThat(readAllRecords(MindfulnessSessionRecord::class.java))
                .comparingElementsUsing(
                    transforming(MindfulnessSessionRecord::getStartTime, "record start time")
                )
                .containsExactly(YESTERDAY_11AM.plusHours(1).toInstant())
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun dataAndAccess_deleteMindfulnessData() {
        context.launchMainActivity {
            findTextAndClick("Data and access")
            findTextAndClick("See all categories")
            scrollToEnd()
            findTextAndClick("Wellness")
            findText("Delete wellness data")
            findTextAndClick("Mindfulness")

            // Delete all Mindfulness data.
            scrollToEnd()
            findTextAndClick("Delete this data")
            findTextAndClick("Delete all data")
            findTextAndClick("Next")
            findTextAndClick("Delete")
            findTextAndClick("Done")
            assertThat(readAllRecords(MindfulnessSessionRecord::class.java)).isEmpty()
            assertThat(readAllRecords(ExerciseSessionRecord::class.java)).hasSize(1)

            // Go back to the Data and Access screen.
            navigateUp()
            navigateUp()

            // Make sure Wellness category is disabled.
            findObject(By.text("Activity").enabled(true))
            scrollToEnd()
            findObject(By.text("Wellness").enabled(false))
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun appPermissions_flagEnabled_showsMindfulness() {
        context.launchMainActivity {
            findTextAndClick("App permissions")
            findTextAndClick("Health Connect cts test app")
            scrollDownTo(By.text("Mindfulness"))
            findText("Mindfulness")
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun seeAllRecentAccess_flagEnabled_showsWellness() {
        context.launchMainActivity {
            findTextAndClick("See all recent access")
            findText("Write: Activity, Wellness")
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun requestPermission_flagEnabled_showsMindfulness() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_PACKAGE_NAME,
            HealthPermissions.READ_MINDFULNESS,
        )

        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME,
            permissions = listOf(HealthPermissions.READ_MINDFULNESS),
        ) {
            scrollToEnd()
            findTextAndClick("Mindfulness")
            findTextAndClick("Allow")

            assertThat(
                    context.packageManager.checkPermission(
                        HealthPermissions.READ_MINDFULNESS,
                        TEST_APP_PACKAGE_NAME,
                    )
                )
                .isEqualTo(PackageManager.PERMISSION_GRANTED)
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun dataAndAccess_seeAllCategories_flagDisabled_doesNotShowWellness() {
        context.launchMainActivity {
            findTextAndClick("Data and access")
            findObject(By.text("Activity").enabled(true), timeout = Duration.ofSeconds(2))
            verifyTextNotFound("Wellness")

            findTextAndClick("See all categories")
            scrollToEnd()
            verifyTextNotFound("Wellness")
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun appPermissions_flagDisabled_doesNotShowMindfulness() {
        context.launchMainActivity {
            scrollToEnd()
            findTextAndClick("App permissions")
            findTextAndClick("Health Connect cts test app")
            scrollDownTo(By.text("Steps"))
            findText("Height")
            findText("Steps")
            verifyTextNotFound("Mindfulness")
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun seeAllRecentAccess_flagDisabled_doesNotShowMindfulness() {
        context.launchMainActivity {
            findTextAndClick("See all recent access")
            findText("Write: Activity")
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_MINDFULNESS)
    @Test
    fun requestPermission_flagDisabled_doesNotShowMindfulness() {
        revokePermissionViaPackageManager(
            context,
            TEST_APP_PACKAGE_NAME,
            HealthPermissions.READ_MINDFULNESS,
        )
        revokePermissionViaPackageManager(
            context,
            TEST_APP_PACKAGE_NAME,
            HealthPermissions.READ_HEIGHT,
        )

        context.launchRequestPermissionActivity(
            packageName = TEST_APP_PACKAGE_NAME,
            permissions = listOf(HealthPermissions.READ_MINDFULNESS, HealthPermissions.READ_HEIGHT),
        ) {
            scrollToEnd()
            findText("Height")
            verifyTextNotFound("Mindfulness")
            findTextAndClick("Allow all")
            findTextAndClick("Allow")

            assertThat(
                    context.packageManager.checkPermission(
                        HealthPermissions.READ_MINDFULNESS,
                        TEST_APP_PACKAGE_NAME,
                    )
                )
                .isEqualTo(PackageManager.PERMISSION_DENIED)
            assertThat(
                    context.packageManager.checkPermission(
                        HealthPermissions.READ_HEIGHT,
                        TEST_APP_PACKAGE_NAME,
                    )
                )
                .isEqualTo(PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun insertData() {
        TestUtils.insertRecords(
            mutableListOf(
                MindfulnessSessionRecord.Builder(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.toInstant(),
                        YESTERDAY_11AM.plusMinutes(15).toInstant(),
                        MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
                    )
                    .setTitle("foo-title")
                    .setNotes("foo-notes")
                    .build(),
                MindfulnessSessionRecord.Builder(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.plusHours(1).toInstant(),
                        YESTERDAY_11AM.plusHours(1).plusMinutes(29).toInstant(),
                        MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_UNKNOWN,
                    )
                    .build(),
                ExerciseSessionRecord.Builder(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.plusHours(4).toInstant(),
                        YESTERDAY_11AM.plusHours(4).plusMinutes(13).toInstant(),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING,
                    )
                    .setTitle("Title")
                    .setNotes("Notes lorem ipsum blah blah blah")
                    .build(),
            )
        )
    }
}
