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

import android.app.Activity.RESULT_OK
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.health.connect.exportimport.ExportImportDocumentProvider
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.ExportDestinationFragment
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.service.HealthDataExportManagerModule
import com.android.healthconnect.controller.tests.utils.di.FakeHealthDataExportManager
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.times

@HiltAndroidTest
@UninstallModules(HealthDataExportManagerModule::class)
class ExportDestinationFragmentTest {
    companion object {
        private val TEST_DOCUMENT_PROVIDER_1_TITLE = "Document provider 1"
        private val TEST_DOCUMENT_PROVIDER_1_AUTHORITY = "documentprovider1.com"
        private val TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE = 1
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY = "Account 1"
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider1.documents/root/account1")
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_1_DOCUMENT_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider1.documents/root/account1/document")
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY = "Account 2"
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider1.documents/root/account2")
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_2_DOCUMENT_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider1.documents/root/account2/document")

        private val TEST_DOCUMENT_PROVIDER_2_TITLE = "Document provider 2"
        private val TEST_DOCUMENT_PROVIDER_2_AUTHORITY = "documentprovider2.com"
        private val TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE = 2
        private val TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY = "Account"
        private val TEST_DOCUMENT_PROVIDER_2_ROOT_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider2.documents/root/account")

        private val EXTERNAL_STORAGE_DOCUMENT_URI =
            Uri.parse("content://com.android.externalstorage.documents/document")
        private val DOWNLOADS_DOCUMENT_URI =
            Uri.parse("content://com.android.providers.downloads.documents/document")
    }

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val healthDataExportManager: HealthDataExportManager = FakeHealthDataExportManager()

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
        Intents.init()
    }

    @After
    fun tearDown() {
        (healthDataExportManager as FakeHealthDataExportManager).reset()
        Intents.release()
    }

    @Test
    fun exportDestinationFragment_isDisplayedCorrectly() {
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withId(R.id.export_destination_header_upload_icon)).check(matches(isDisplayed()))
        onView(withText("Choose where to export")).check(matches(isDisplayed()))

        onView(withText("Back")).check(matches(isDisplayed()))
        onView(withText("Next")).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_clicksBackButton_navigatesBackToFrequencyFragment() {
        launchFragment<ExportDestinationFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.export_nav_graph)
            navHostController.setCurrentDestination(R.id.exportDestinationFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withId(R.id.export_import_cancel_button)).check(matches(isClickable()))
        onView(withId(R.id.export_import_cancel_button)).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.exportFrequencyFragment)
    }

    @Test
    fun exportDestinationFragment_nextButton_notEnabled() {
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withId(R.id.export_import_next_button)).check(matches(isNotEnabled()))
    }

    @Test
    fun exportDestinationFragment_showsDocumentProviders() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(matches(isDisplayed()))
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY), isDisplayed()))))
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(matches(isDisplayed()))
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withText(TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY), isDisplayed()))))
    }

    @Test
    fun exportDestinationFragment_showsDocumentProviders_notChecked() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isNotChecked()))))
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isNotChecked()))))
    }

    @Test
    fun exportDestinationFragment_documentProviderClicked_documentProviderIsChecked() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isChecked()))))
    }

    @Test
    fun exportDestinationFragment_secondDocumentProviderClicked_otherDocumentProviderIsNotChecked() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE)).perform(click())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isNotChecked()))))
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isChecked()))))
    }

    @Test
    fun exportDestinationFragment_documentProviderClicked_nextButtonIsEnabled() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())

        onView(withId(R.id.export_import_next_button)).check(matches(isEnabled()))
    }

    @Test
    fun exportDestinationFragment_nextButtonClicked_startsDocumentsUi() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI))
    }

    @Test
    fun exportDestinationFragment_chooseFile_updatesUri() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(
                ActivityResult(
                    RESULT_OK, Intent().setData(TEST_DOCUMENT_PROVIDER_1_ROOT_1_DOCUMENT_URI)))

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        assertThat(healthDataExportManager.getExportUri())
            .isEqualTo(TEST_DOCUMENT_PROVIDER_1_ROOT_1_DOCUMENT_URI)
    }

    @Test
    fun exportDestinationFragment_chooseExternalStorageFile_doesNotUpdateUri() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(ActivityResult(RESULT_OK, Intent().setData(EXTERNAL_STORAGE_DOCUMENT_URI)))

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        assertThat(healthDataExportManager.getExportUri()).isEqualTo(null)
    }

    @Test
    fun exportDestinationFragment_chooseDownloadsFile_doesNotUpdateUri() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(ActivityResult(RESULT_OK, Intent().setData(DOWNLOADS_DOCUMENT_URI)))

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        assertThat(healthDataExportManager.getExportUri()).isEqualTo(null)
    }

    @Test
    fun exportDestinationFragment_multipleAccounts_doesNotShowSummary() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_summary), not(isDisplayed())))))
    }

    @Test
    fun exportDestinationFragment_multipleAccountsClicked_showsAccountPicker() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())

        onView(withText("Choose an account")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Done")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_multipleAccountsClickedAndAccountChosen_updatesSummary() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .perform(click())
        onView(withText("Done")).inRoot(isDialog()).perform(click())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY), isDisplayed()))))
    }

    @Test
    fun exportDestinationFragment_multipleAccountsClickedAndAccountChosen_nextButtonClicked_startsDocumentsUi() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY))
        (healthDataExportManager as FakeHealthDataExportManager).setExportImportDocumentProviders(
            documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .perform(click())
        onView(withText("Done")).inRoot(isDialog()).perform(click())
        onView(withText("Next")).perform(click())

        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI))
    }

    private fun documentProviderWithTitle(title: String): Matcher<View>? =
        allOf(withId(R.id.item_document_provider), hasDescendant(withText(title)))
}
