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
import android.health.connect.HealthConnectManager
import android.health.connect.exportimport.ScheduledExportSettings
import android.health.connect.exportimport.ScheduledExportStatus
import android.os.Bundle
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
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.service.HealthDataExportManagerModule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.di.FakeHealthDataExportManager
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(HealthDataExportManagerModule::class)
class BackupAndRestoreSettingsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val healthDataExportManager: HealthDataExportManager = FakeHealthDataExportManager()

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)

        (healthDataExportManager as FakeHealthDataExportManager).setScheduledExportStatus(
            ScheduledExportStatus(
                null,
                HealthConnectManager.DATA_EXPORT_ERROR_NONE,
                ExportFrequency.EXPORT_FREQUENCY_NEVER.periodInDays))
    }

    @After
    fun tearDown() {
        (healthDataExportManager as FakeHealthDataExportManager).reset()
    }

    @Test
    fun backupAndRestoreSettingsFragmentInit_showsFragmentCorrectly() {
        (healthDataExportManager as FakeHealthDataExportManager).setScheduledExportStatus(
            ScheduledExportStatus(
                NOW,
                HealthConnectManager.DATA_EXPORT_ERROR_NONE,
                ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays))

        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays))

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
        (healthDataExportManager as FakeHealthDataExportManager).setScheduledExportStatus(
            ScheduledExportStatus(
                null,
                HealthConnectManager.DATA_EXPORT_ERROR_NONE,
                ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays))

        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays))
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Last export: October 20, 2022")).check(doesNotExist())
    }

    @Test
    fun backupAndRestoreSettingsFragment_clicksScheduledExportWhenItIsOff_navigatesToExportSetupActivity() {
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_NEVER.periodInDays))
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
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays))
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
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_NEVER.periodInDays))
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Off")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsDaily_showsCorrectSummary() {
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays))
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Daily")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsWeekly_showsCorrectSummary() {
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_WEEKLY.periodInDays))
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Weekly")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsMonthly_showsCorrectSummary() {
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_MONTHLY.periodInDays))
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Monthly")).check(matches(isDisplayed()))
    }
}
