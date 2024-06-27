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
import android.net.Uri
import androidx.core.os.bundleOf
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.ImportConfirmationDialogFragment
import com.android.healthconnect.controller.exportimport.api.HealthDataImportManager
import com.android.healthconnect.controller.service.HealthDataImportManagerModule
import com.android.healthconnect.controller.tests.utils.di.FakeHealthDataImportManager
import com.android.healthconnect.controller.tests.utils.launchDialog
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.ImportConfirmationDialogElement
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@UninstallModules(HealthDataImportManagerModule::class)
@HiltAndroidTest
class ImportConfirmationDialogFragmentTest {

    companion object {
        private const val FILE_NAME = "testFile"
    }

    private lateinit var importFile: File
    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val healthDataImportManager: HealthDataImportManager = FakeHealthDataImportManager()
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
        importFile = File.createTempFile("testFile", ".zip")
    }

    @After
    fun tearDown() {
        importFile.delete()
        reset(healthConnectLogger)
    }

    @Test
    fun importConfirmationDialogFragment_isDisplayedCorrectly() {
        launchDialog<ImportConfirmationDialogFragment>(
            bundleOf(
                ImportConfirmationDialogFragment.IMPORT_FILE_URI_KEY to
                    Uri.fromFile(importFile).toString()))

        onView(withText(R.string.import_confirmation_dialog_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withId(R.id.dialog_custom_message))
            .inRoot(isDialog())
            .check(matches(withText(startsWith(FILE_NAME))))

        val dialogCancelButton =
            onView(withText(R.string.import_confirmation_dialog_cancel_button)).inRoot(isDialog())
        dialogCancelButton.check(matches(isDisplayed()))
        dialogCancelButton.check(matches(isClickable()))

        val dialogImportButton =
            onView(withText(R.string.import_confirmation_dialog_import_button)).inRoot(isDialog())
        dialogImportButton.check(matches(isDisplayed()))
        dialogImportButton.check(matches(isClickable()))
    }

    @Test
    fun importConfirmationDialogFragment_importButtonClicked_runsImport() {
        val importFileUri: Uri = Uri.fromFile(importFile)
        launchDialog<ImportConfirmationDialogFragment>(
            bundleOf(
                ImportConfirmationDialogFragment.IMPORT_FILE_URI_KEY to importFileUri.toString()))

        val dialogImportButton =
            onView(withText(R.string.import_confirmation_dialog_import_button)).inRoot(isDialog())
        dialogImportButton.perform(click())

        assertThat((healthDataImportManager as FakeHealthDataImportManager).getImportFileUri())
            .isEqualTo(importFileUri)
        verify(healthConnectLogger)
            .logInteraction(ImportConfirmationDialogElement.IMPORT_CONFIRMATION_DONE_BUTTON)
    }

    @Test
    fun importConfirmationDialogFragment_cancelButtonClicked_interactionLogged() {
        val importFileUri: Uri = Uri.fromFile(importFile)
        launchDialog<ImportConfirmationDialogFragment>(
            bundleOf(
                ImportConfirmationDialogFragment.IMPORT_FILE_URI_KEY to importFileUri.toString()))

        val dialogCancelButton =
            onView(withText(R.string.import_confirmation_dialog_cancel_button)).inRoot(isDialog())
        dialogCancelButton.check(matches(isDisplayed()))
        dialogCancelButton.perform(click())

        verify(healthConnectLogger)
            .logInteraction(ImportConfirmationDialogElement.IMPORT_CONFIRMATION_CANCEL_BUTTON)
    }
}
