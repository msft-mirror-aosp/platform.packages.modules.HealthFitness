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

package com.android.healthconnect.controller.tests.export

import android.content.Context
import android.os.Bundle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.export.ExportFrequencyFragment
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ExportFrequencyFragmentTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @Test
    fun exportFrequencyFragment_isDisplayedCorrectly() {
        launchFragment<ExportFrequencyFragment>(Bundle())

        onView(withId(R.id.export_frequency_header_repeat_icon)).check(matches(isDisplayed()))

        onView(withText("Choose frequency")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Choose to save data once, or set it to save automatically at regular times."))
            .check(matches(isDisplayed()))

        onView(withText("Daily")).check(matches(isDisplayed()))
        onView(withText("Weekly")).check(matches(isDisplayed()))
        onView(withText("Monthly")).check(matches(isDisplayed()))

        onView(withText("Back")).check(matches(isDisplayed()))
        onView(withText("Next")).check(matches(isDisplayed()))
    }

    @Test
    fun exportFrequencyFragment_backButton_isClickable() {
        launchFragment<ExportFrequencyFragment>(Bundle())

        onView(withId(R.id.export_back_button)).check(matches(isClickable()))
    }

    @Test
    fun exportFrequencyFragment_clicksNextButton_navigatesToDestinationFragment() {
        launchFragment<ExportFrequencyFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.export_nav_graph)
            navHostController.setCurrentDestination(R.id.exportFrequencyFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withId(R.id.export_next_button)).check(matches(isClickable()))
        onView(withId(R.id.export_next_button)).perform(click())

        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.exportDestinationFragment)
    }
}
