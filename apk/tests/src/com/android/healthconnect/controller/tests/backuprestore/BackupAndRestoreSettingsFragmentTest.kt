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

package com.android.healthconnect.controller.tests.backuprestore

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.backuprestore.BackupAndRestoreSettingsFragment
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.ExportSettings
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import com.android.healthconnect.controller.exportimport.api.ExportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiState
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiStatus
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@HiltAndroidTest
class BackupAndRestoreSettingsFragmentTest {

    companion object {
        private const val TEST_EXPORT_PERIOD_IN_DAYS = 1
    }

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val exportSettingsViewModel: ExportSettingsViewModel =
        Mockito.mock(ExportSettingsViewModel::class.java)

    @BindValue
    val exportStatusViewModel: ExportStatusViewModel =
        Mockito.mock(ExportStatusViewModel::class.java)

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)

        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        null,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        /** periodInDays= */
                        0)))
        }
    }

    @Test
    fun backupAndRestoreSettingsFragmentInit_showsFragmentCorrectly() {
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        NOW,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        TEST_EXPORT_PERIOD_IN_DAYS)))
        }
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_WEEKLY))
        }

        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Export and import")).check(matches(isDisplayed()))

        onView(withText("Scheduled export")).check(matches(isDisplayed()))

        onView(withText("Import data")).check(matches(isDisplayed()))
        onView(withText("Restore data from a previously exported file"))
            .check(matches(isDisplayed()))

        onView(withText("Last export: October 20, 2022")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_withNoLastSuccessfulDate_doesNotShowLastExportTime() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_WEEKLY))
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        null,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        TEST_EXPORT_PERIOD_IN_DAYS)))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Last export: October 20, 2022")).check(doesNotExist())
    }

    @Test
    fun backupAndRestoreSettingsFragment_clicksScheduledExportWhenItIsOff_navigatesToExportSetupActivity() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.backupAndRestoreSettingsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Scheduled export")).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.exportSetupActivity)
    }

    @Test
    fun backupAndRestoreSettingsFragment_clicksScheduledExportWhenItIsOn_navigatesToScheduledExportFragment() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_DAILY))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.backupAndRestoreSettingsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Scheduled export")).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.scheduledExportFragment)
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsNever_showsCorrectSummary() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Off")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsDaily_showsCorrectSummary() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_DAILY))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Daily")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsWeekly_showsCorrectSummary() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_WEEKLY))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Weekly")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsMonthly_showsCorrectSummary() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_MONTHLY))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Monthly")).check(matches(isDisplayed()))
    }
}
