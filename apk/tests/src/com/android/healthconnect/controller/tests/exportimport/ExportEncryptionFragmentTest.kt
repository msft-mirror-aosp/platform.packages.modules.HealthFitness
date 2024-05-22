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

import android.content.Context
import android.health.connect.exportimport.ScheduledExportSettings.*
import android.os.Bundle
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.ExportEncryptionFragment
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.service.HealthDataExportManagerModule
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any

@HiltAndroidTest
@UninstallModules(HealthDataExportManagerModule::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ExportEncryptionFragmentTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @BindValue
    val healthDataExportManager: HealthDataExportManager =
        Mockito.mock(HealthDataExportManager::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @Test
    fun exportEncryptionFragment_isDisplayedCorrectly() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.export_encryption_header_lock_icon)).check(matches(isDisplayed()))
        onView(withText("Turn on encryption")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Data needs to be encrypted before it is saved. No one, not even Google or Health\u00A0Connect, will be able to access it."))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "Create a password to protect your encrypted data. You will need this password to import and restore your data."))
            .check(matches(isDisplayed()))

        onView(withId(R.id.export_password)).check(matches(withHint("Password")))
        onView(withId(R.id.export_repeat_password)).check(matches(withHint("Repeat password")))

        onView(
                withText(
                    "If you forget your password and lose your phone, Health\u00A0Connect cannot help you recover your saved data."))
            .check(matches(isDisplayed()))

        onView(withText("Back")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Next")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun exportEncryptionFragment_nextButton_disabledByDefault() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(not(isEnabled())))
    }

    @Test
    fun exportEncryptionFragment_noUserInput_errorIsNotShown() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.password_mismatch_error)).check(matches(not(isDisplayed())))
    }

    @Test
    fun exportEncryptionFragment_whenPasswordsMatch_nextButtonIsEnabledAndNoErrorIsNotShown() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.export_next_button)).check(matches(not(isEnabled())))
        // Enabled when passwords match.
        onView(withId(R.id.export_password)).perform(scrollTo(), typeText("1password"))
        onView(withId(R.id.export_repeat_password)).perform(scrollTo(), typeText("1password"))

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(isEnabled()))
        onView(withId(R.id.password_mismatch_error)).check(matches(not(isDisplayed())))
    }

    @Test
    fun exportEncryptionFragment_addMoreTextToPassword_nextButtonIsDisabledAndErrorIsShown() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.export_next_button)).check(matches(not(isEnabled())))

        // Enabled when passwords match.
        onView(withId(R.id.export_password)).perform(scrollTo(), typeText("1password"))
        onView(withId(R.id.export_repeat_password)).perform(scrollTo(), typeText("1password"))
        onView(withId(R.id.export_next_button)).check(matches(isEnabled()))
        // Add more text to the password field.
        onView(withId(R.id.export_password)).perform(scrollTo(), typeText("+123"))

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withId(R.id.password_mismatch_error)).check(matches(isDisplayed()))
    }

    @Test
    fun exportEncryptionFragment_addMoreTextToRepeatedPassword_nextButtonIsDisabledAndErrorIsShown() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.export_next_button)).check(matches(not(isEnabled())))

        // Enabled when passwords match.
        onView(withId(R.id.export_password)).perform(scrollTo(), typeText("1password"))
        onView(withId(R.id.export_repeat_password)).perform(scrollTo(), typeText("1password"))
        onView(withId(R.id.export_next_button)).check(matches(isEnabled()))
        // Add more text to the repeated password field.
        onView(withId(R.id.export_repeat_password)).perform(scrollTo(), typeText("+123"))

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withId(R.id.password_mismatch_error)).check(matches(isDisplayed()))
    }

    @Test
    fun exportEncryptionFragment_mismatchedPassword_nextButtonIsDisabledAndErrorIsShown() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        // Enters the repeated password first.
        onView(withId(R.id.export_repeat_password)).perform(scrollTo(), typeText("2password"))
        onView(withId(R.id.export_password)).perform(scrollTo(), typeText("1password"))

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withId(R.id.password_mismatch_error)).check(matches(isDisplayed()))
    }

    @Test
    fun exportEncryptionFragment_mismatchedRepeatedPassword_nextButtonIsDisabledAndErrorIsShown() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        // Enters the password first.
        onView(withId(R.id.export_password)).perform(scrollTo(), typeText("1password"))
        onView(withId(R.id.export_repeat_password)).perform(scrollTo(), typeText("2password"))

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withId(R.id.password_mismatch_error)).check(matches(isDisplayed()))
    }

    @Test
    fun exportEncryptionFragment_onlyPasswordIsEntered_nextButtonIsDisabledButErrorIsNotShown() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.export_password)).perform(scrollTo(), typeText("1password"))

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withId(R.id.password_mismatch_error)).check(matches(not(isDisplayed())))
    }

    @Test
    fun exportEncryptionFragment_onlyRepeatedPasswordIsEntered_nextButtonIsDisabledButErrorIsNotShown() {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.export_repeat_password)).perform(scrollTo(), typeText("1password"))

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(not(isEnabled())))
        onView(withId(R.id.password_mismatch_error)).check(matches(not(isDisplayed())))
    }

    @Test
    @Ignore(
        "TODO: b/325917283 - this test fails on U image as it requires conscrypt change which will be added to U in M-08")
    fun exportEncryptionFragment_clicksNextButton_updatesEncryption() = runTest {
        launchFragment<ExportEncryptionFragment>(Bundle())

        onView(withId(R.id.export_password)).perform(scrollTo(), typeText("1password"))
        onView(withId(R.id.export_repeat_password)).perform(scrollTo(), typeText("1password"))

        onView(withId(R.id.export_next_button)).perform(scrollTo()).check(matches(isEnabled()))
        onView(withId(R.id.export_next_button)).perform(click())

        advanceUntilIdle()
        Mockito.verify(healthDataExportManager).configureScheduledExport(any())
    }

    @Test
    fun exportEncryptionFragment_clicksBackButton_navigatesBackToDestinationFragment() {
        launchFragment<ExportEncryptionFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.export_nav_graph)
            navHostController.setCurrentDestination(R.id.exportEncryptionFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withId(R.id.export_back_button)).perform(scrollTo()).check(matches(isClickable()))
        onView(withId(R.id.export_back_button)).perform(click())

        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.exportDestinationFragment)
    }
}
