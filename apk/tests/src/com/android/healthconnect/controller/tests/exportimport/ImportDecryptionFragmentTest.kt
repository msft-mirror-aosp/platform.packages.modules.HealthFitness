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
import android.os.Bundle
import androidx.navigation.testing.TestNavHostController
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.Espresso.onView
import com.android.healthconnect.controller.R
import androidx.test.espresso.assertion.ViewAssertions.matches
import com.android.healthconnect.controller.exportimport.ImportDecryptionFragment
import com.android.healthconnect.controller.tests.utils.launchFragment

@HiltAndroidTest
class ImportDecryptionFragmentTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @Test
    fun importDecryptionFragment_isDisplayedCorrectly() {
        launchFragment<ImportDecryptionFragment>(Bundle())

        onView(withId(R.id.page_header_icon))
                .check(matches(isDisplayed()))
        onView(withText("Type password")).check(matches(isDisplayed()))
        onView(withId(R.id.import_decryption_description)).check(matches(isDisplayed()))
        onView(withId(R.id.import_password)).check(matches(isDisplayed()))
        onView(withText("Forgotten password?")).check(matches(isDisplayed()))
        onView(withId(R.id.import_decryption_footer)).check(matches(isDisplayed()))
        onView(withId(R.id.import_decryption_footer_info_icon)).check(matches(isDisplayed()))
        onView(withText("Cancel")).check(matches(isDisplayed()))
        onView(withText("Next")).check(matches(isDisplayed()))
    }

    @Test
    fun importDecryptionFragment_cancelButton_isClickable() {
        launchFragment<ImportDecryptionFragment>(Bundle())

        onView(withId(R.id.export_import_cancel_button)).check(matches(isClickable()))
    }

    @Test
    fun importDecryptionFragment_nextButton_isClickable() {
        launchFragment<ImportDecryptionFragment>(Bundle())

        onView(withId(R.id.export_import_next_button)).check(matches(isClickable()))
    }
}