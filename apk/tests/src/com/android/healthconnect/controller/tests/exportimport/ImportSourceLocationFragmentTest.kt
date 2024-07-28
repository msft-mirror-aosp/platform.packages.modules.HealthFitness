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
import com.android.healthconnect.controller.exportimport.ImportSourceLocationFragment
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.service.HealthDataExportManagerModule
import com.android.healthconnect.controller.tests.utils.di.FakeDeviceInfoUtils
import com.android.healthconnect.controller.tests.utils.di.FakeHealthDataExportManager
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.DeviceInfoUtilsModule
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.ImportSourceLocationElement
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.io.File
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@HiltAndroidTest
@UninstallModules(DeviceInfoUtilsModule::class, HealthDataExportManagerModule::class)
class ImportSourceLocationFragmentTest {
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
    val fakeHealthDataExportManager = healthDataExportManager as FakeHealthDataExportManager
    @BindValue val deviceInfoUtils: DeviceInfoUtils = FakeDeviceInfoUtils()
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
        Intents.init()

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
    }

    @After
    fun tearDown() {
        Intents.release()
        fakeHealthDataExportManager.reset()
        reset(healthConnectLogger)
    }

    @Test
    fun importSourceLocationFragment_isDisplayedCorrectly() {
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withId(R.id.page_header_icon)).check(matches(isDisplayed()))
        onView(withText("Import from")).check(matches(isDisplayed()))
        onView(withText("Cancel")).check(matches(isDisplayed()))
        onView(withText("Next")).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_impressionsLogged() {
        launchFragment<ImportSourceLocationFragment>(Bundle())

        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger)
            .logImpression(ImportSourceLocationElement.IMPORT_SOURCE_LOCATION_NEXT_BUTTON)
        verify(healthConnectLogger)
            .logImpression(ImportSourceLocationElement.IMPORT_SOURCE_LOCATION_CANCEL_BUTTON)
    }

    @Test
    fun importSourceLocationFragment_cancelButton_isClickable() {
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withId(R.id.export_import_cancel_button)).check(matches(isClickable()))
        onView(withId(R.id.export_import_cancel_button)).perform(click())
        verify(healthConnectLogger)
            .logInteraction(ImportSourceLocationElement.IMPORT_SOURCE_LOCATION_CANCEL_BUTTON)
    }

    @Test
    fun importSourceLocationFragment_nextButton_notEnabled() {
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withId(R.id.export_import_next_button)).check(matches(isNotEnabled()))
    }

    @Test
    fun importSourceLocationFragment_showsDocumentProviders() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

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
    fun importSourceLocationFragment_showsDocumentProviders_notChecked() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

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
    fun importSourceLocationFragment_documentProviderClicked_documentProviderIsChecked() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isChecked()))))
    }

    @Test
    fun importSourceLocationFragment_secondDocumentProviderClicked_otherDocumentProviderIsNotChecked() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

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
    fun importSourceLocationFragment_documentProviderClicked_nextButtonIsEnabled() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())

        onView(withId(R.id.export_import_next_button)).check(matches(isEnabled()))
    }

    @Test
    fun importSourceLocationFragment_nextButtonClicked_startsDocumentsUi() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI))
        verify(healthConnectLogger)
            .logInteraction(ImportSourceLocationElement.IMPORT_SOURCE_LOCATION_NEXT_BUTTON)
    }

    @Test
    fun importSourceLocationFragment_chooseFile_navigatesToImportConfirmationDialogFragment() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.import_nav_graph)
            navHostController.setCurrentDestination(R.id.importSourceLocationFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        val testFile = File.createTempFile("testFile", ".zip")

        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(ActivityResult(RESULT_OK, Intent().setData(Uri.fromFile(testFile))))

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        onView(withText("Import this file?")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(R.id.dialog_custom_message))
            .inRoot(isDialog())
            .check(matches(withText(startsWith(testFile.name))))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Import")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_chooseExternalStorageFile_doesNotNavigateToNewScreen() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.import_nav_graph)
            navHostController.setCurrentDestination(R.id.importSourceLocationFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(ActivityResult(RESULT_OK, Intent().setData(EXTERNAL_STORAGE_DOCUMENT_URI)))

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.importSourceLocationFragment)
    }

    @Test
    fun importSourceLocationFragment_chooseDownloadsFile_doesNotNavigateToNewScreen() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.import_nav_graph)
            navHostController.setCurrentDestination(R.id.importSourceLocationFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
        intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(ActivityResult(RESULT_OK, Intent().setData(DOWNLOADS_DOCUMENT_URI)))

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.importSourceLocationFragment)
    }

    @Test
    fun importSourceLocationFragment_multipleAccounts_doesNotShowSummary() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_summary), not(isDisplayed())))))
    }

    @Test
    fun importSourceLocationFragment_multipleAccountsClicked_showsAccountPicker() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

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
    fun importSourceLocationFragment_multipleAccountsClickedAndAccountChosen_updatesSummary() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

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
    fun importSourceLocationFragment_multipleAccountsClickedAndAccountChosen_nextButtonClicked_startsDocumentsUi() {
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
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .perform(click())
        onView(withText("Done")).inRoot(isDialog()).perform(click())
        onView(withText("Next")).perform(click())

        intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI))
    }

    @Test
    fun importSourceLocationFragment_noProviders_nextButtonNotEnabled() {
        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withId(R.id.export_import_next_button)).check(matches(isNotEnabled()))
    }

    @Test
    fun importSourceLocationFragment_noProvidersAndPlayStoreNotAvailable_showsInstallAppsText() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_install_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_noProvidersAndPlayStoreNotAvailable_doesNotShowPlayStoreButton() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_go_to_play_store_text))
            .check(matches(not(isDisplayed())))
    }

    @Test
    fun importSourceLocationFragment_noProvidersAndPlayStoreAvailable_showsInstallAppsText() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_install_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_noProvidersAndPlayStoreAvailable_showsPlayStoreButton() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_go_to_play_store_text)).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_noProvidersAndPlayStoreAvailable_goToPlayStoreClicked_launchesPlayStore() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ImportSourceLocationFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.import_nav_graph)
            navHostController.setCurrentDestination(R.id.importSourceLocationFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withId(R.id.export_import_go_to_play_store)).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.play_store_activity)
    }

    @Test
    fun importSourceLocationFragment_singleProvider_noRadioButton() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withId(R.id.item_document_provider_radio_button)).check(matches(not(isDisplayed())))
    }

    @Test
    fun importSourceLocationFragment_singleProviderSingleAccount_showsAccountSummary() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY)).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_singleProviderSingleAccount_nextButtonClicked_startsDocumentsUi() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText("Next")).perform(click())

        intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI))
    }

    @Test
    fun importSourceLocationFragment_singleProviderMultipleAccounts_showsTapToSelectAccount() {
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_tap_to_choose_account)).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_singleProviderMultipleAccountsClicked_showsAccountPicker() {
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

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
    fun importSourceLocationFragment_singleProviderMultipleAccountsClickedAndAccountChosen_updatesSummary() {
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

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
    fun importSourceLocationFragment_singleProviderMultipleAccountsClickedAndAccountChosen_nextButtonClicked_startsDocumentsUi() {
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .perform(click())
        onView(withText("Done")).inRoot(isDialog()).perform(click())
        onView(withText("Next")).perform(click())

        intended(hasAction(Intent.ACTION_OPEN_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI))
    }

    @Test
    fun importSourceLocationFragment_singleProviderAndPlayStoreNotAvailable_showsInstallAppsText() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_install_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_singleProviderAndPlayStoreNotAvailable_doesNotShowPlayStoreButton() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_go_to_play_store_text))
            .check(matches(not(isDisplayed())))
    }

    @Test
    fun importSourceLocationFragment_singleProviderAndPlayStoreAvailable_showsInstallAppsText() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_install_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_singleProviderAndPlayStoreAvailable_showsPlayStoreButton() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle())

        onView(withText(R.string.export_import_go_to_play_store_text)).check(matches(isDisplayed()))
    }

    @Test
    fun importSourceLocationFragment_singleProviderAndPlayStoreAvailable_goToPlayStoreClicked_launchesPlayStore() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY))
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ImportSourceLocationFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.import_nav_graph)
            navHostController.setCurrentDestination(R.id.importSourceLocationFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withId(R.id.export_import_go_to_play_store)).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.play_store_activity)
    }

    private fun documentProviderWithTitle(title: String): Matcher<View>? =
        allOf(withId(R.id.item_document_provider), hasDescendant(withText(title)))
}
