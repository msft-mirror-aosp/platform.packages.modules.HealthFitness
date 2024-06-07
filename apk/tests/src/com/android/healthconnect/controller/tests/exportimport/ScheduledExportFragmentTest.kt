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

package com.android.healthconnect.controller.tests.exportimport

import android.health.connect.HealthConnectManager
import android.health.connect.exportimport.ScheduledExportSettings
import android.health.connect.exportimport.ScheduledExportStatus
import android.os.Bundle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.ScheduledExportFragment
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.service.HealthDataExportManagerModule
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.di.FakeHealthDataExportManager
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
@UninstallModules(HealthDataExportManagerModule::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ScheduledExportFragmentTest {
    companion object {
        private const val TEST_EXPORT_PERIOD_IN_DAYS = 1
    }

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @BindValue val healthDataExportManager: HealthDataExportManager = FakeHealthDataExportManager()

    @Before
    fun setup() {
        hiltRule.inject()
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_WEEKLY.periodInDays))
        val scheduledExportStatus =
            ScheduledExportStatus(null, HealthConnectManager.DATA_EXPORT_ERROR_NONE, 0)
        (healthDataExportManager as FakeHealthDataExportManager).setScheduledExportStatus(
            scheduledExportStatus)
    }

    @After
    fun tearDown() {
        (healthDataExportManager as FakeHealthDataExportManager).reset()
    }

    @Test
    fun scheduledExportFragment_isDisplayedCorrectly() {
        val scheduledExportStatus =
            ScheduledExportStatus(
                NOW, HealthConnectManager.DATA_EXPORT_ERROR_NONE, TEST_EXPORT_PERIOD_IN_DAYS)
        (healthDataExportManager as FakeHealthDataExportManager).setScheduledExportStatus(
            scheduledExportStatus)

        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withText("On")).check(matches(isDisplayed()))
        onView(withText("Choose frequency")).check(matches(isDisplayed()))
        onView(withText("Daily")).check(matches(isDisplayed()))
        onView(withText("Weekly")).check(matches(isDisplayed()))
        onView(withText("Monthly")).check(matches(isDisplayed()))
        onView(withText("Next export: October 21, 2022")).check(matches(isDisplayed()))
    }

    @Test
    fun scheduledExportFragment_whenLastSuccessfulExportDateIsNull_doesNotShowNextExportStatus() {
        val scheduledExportStatus =
            ScheduledExportStatus(
                null, HealthConnectManager.DATA_EXPORT_ERROR_NONE, TEST_EXPORT_PERIOD_IN_DAYS)
        (healthDataExportManager as FakeHealthDataExportManager).setScheduledExportStatus(
            scheduledExportStatus)

        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withText("Next export: October 21, 2022")).check(doesNotExist())
    }

    @Test
    fun scheduledExportFragment_dailyExport_checkedButtonMatchesExportFrequency() {
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays))

        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withId(R.id.radio_button_daily)).check(matches(isChecked()))
    }

    @Test
    fun scheduledExportFragment_weeklyExport_checkedButtonMatchesExportFrequency() {
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_WEEKLY.periodInDays))

        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withId(R.id.radio_button_weekly)).check(matches(isChecked()))
    }

    @Test
    fun scheduledExportFragment_monthlyExport_checkedButtonMatchesExportFrequency() {
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_MONTHLY.periodInDays))

        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withId(R.id.radio_button_monthly)).check(matches(isChecked()))
    }

    @Test
    fun scheduledExportFragment_turnsOffControl_offIsDisplayed() = runTest {
        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withText("On")).perform(click())

        onView(withText("Off")).check(matches(isDisplayed()))
        advanceUntilIdle()
        assertThat(healthDataExportManager.getScheduledExportPeriodInDays())
            .isEqualTo(ExportFrequency.EXPORT_FREQUENCY_NEVER.periodInDays)
    }

    @Test
    fun scheduledExportFragment_turnsOffControl_exportFrequencySectionDoesNotExist() {
        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withText("On")).perform(click())

        onView(withText("Off")).check(matches(isDisplayed()))
        onView(withText("Choose frequency")).check(doesNotExist())
        onView(withText("Daily")).check(doesNotExist())
        onView(withText("Weekly")).check(doesNotExist())
        onView(withText("Monthly")).check(doesNotExist())
    }

    @Test
    fun scheduledExportFragment_turnsOffControlAndOnAgain_exportFrequencyNotChanged() = runTest {
        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withText("On")).perform(click())
        onView(withText("Off")).check(matches(isDisplayed()))
        advanceUntilIdle()
        assertThat(healthDataExportManager.getScheduledExportPeriodInDays())
            .isEqualTo(ExportFrequency.EXPORT_FREQUENCY_NEVER.periodInDays)
        onView(withText("Off")).perform(click())

        advanceUntilIdle()
        assertThat(healthDataExportManager.getScheduledExportPeriodInDays())
            .isEqualTo(ExportFrequency.EXPORT_FREQUENCY_WEEKLY.periodInDays)
    }

    @Test
    fun scheduledExportFragment_selectsAnotherFrequency_updatesExportFrequency() = runTest {
        healthDataExportManager.configureScheduledExport(
            ScheduledExportSettings.withPeriodInDays(
                ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays))
        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withId(R.id.radio_button_daily)).check(matches(isChecked()))
        onView(withId(R.id.radio_button_monthly)).perform(click())
        advanceUntilIdle()
        assertThat(healthDataExportManager.getScheduledExportPeriodInDays())
            .isEqualTo(ExportFrequency.EXPORT_FREQUENCY_MONTHLY.periodInDays)
    }
}
