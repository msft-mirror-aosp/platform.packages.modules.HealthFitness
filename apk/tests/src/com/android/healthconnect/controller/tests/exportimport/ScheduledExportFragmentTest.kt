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

import android.health.connect.exportimport.ScheduledExportSettings
import android.os.Bundle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.exportimport.ScheduledExportFragment
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.service.HealthDataExportManagerModule
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@HiltAndroidTest
@UninstallModules(HealthDataExportManagerModule::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ScheduledExportFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @BindValue
    val healthDataExportManager: HealthDataExportManager =
        Mockito.mock(HealthDataExportManager::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
        whenever(healthDataExportManager.getScheduledExportPeriodInDays()).then {
            ExportFrequency.EXPORT_FREQUENCY_WEEKLY.periodInDays
        }
    }

    @Test
    fun scheduledExportFragment_isDisplayedCorrectly() {
        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withText("On")).check(matches(isDisplayed()))
    }

    @Test
    fun scheduledExportFragment_turnsOffControl_offIsDisplayed() = runTest {
        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withText("On")).perform(click())

        onView(withText("Off")).check(matches(isDisplayed()))
        advanceUntilIdle()
        Mockito.verify(healthDataExportManager)
            .configureScheduledExport(
                ScheduledExportSettings.withPeriodInDays(
                    ExportFrequency.EXPORT_FREQUENCY_NEVER.periodInDays))
    }

    @Test
    fun scheduledExportFragment_turnsOffControlAndOnAgain_exportFrequencyNotChanged() = runTest {
        launchFragment<ScheduledExportFragment>(Bundle())

        onView(withText("On")).perform(click())
        onView(withText("Off")).check(matches(isDisplayed()))
        Mockito.verify(healthDataExportManager)
            .configureScheduledExport(
                ScheduledExportSettings.withPeriodInDays(
                    ExportFrequency.EXPORT_FREQUENCY_NEVER.periodInDays))
        onView(withText("Off")).perform(click())

        advanceUntilIdle()
        Mockito.verify(healthDataExportManager)
            .configureScheduledExport(
                ScheduledExportSettings.withPeriodInDays(
                    ExportFrequency.EXPORT_FREQUENCY_WEEKLY.periodInDays))
    }
}
