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

import android.Manifest
import android.app.Activity.RESULT_OK
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.health.connect.exportimport.ExportImportDocumentProvider
import android.health.connect.exportimport.ScheduledExportStatus
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.DocumentsContract
import android.view.View
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onIdle
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
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.HealthDataExportManager
import com.android.healthconnect.controller.service.HealthDataExportManagerModule
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.di.FakeDeviceInfoUtils
import com.android.healthconnect.controller.tests.utils.di.FakeHealthDataExportManager
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.DeviceInfoUtilsModule
import com.android.healthconnect.controller.utils.logging.ExportDestinationElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthfitness.flags.Flags
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@HiltAndroidTest
@UninstallModules(DeviceInfoUtilsModule::class, HealthDataExportManagerModule::class)
class ExportDestinationFragmentTest {
    companion object {
        private const val TEST_DOCUMENT_PROVIDER_1_TITLE = "Document provider 1"
        private const val TEST_DOCUMENT_PROVIDER_1_AUTHORITY = "documentprovider1.com"
        private const val TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE = 1
        private const val TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY = "Account 1"
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider1.documents/root/account1"
            )
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_1_DOCUMENT_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider1.documents/root/account1/document"
            )
        private const val TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY = "Account 2"
        private val TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider1.documents/root/account2"
            )

        private const val TEST_DOCUMENT_PROVIDER_2_TITLE = "Document provider 2"
        private const val TEST_DOCUMENT_PROVIDER_2_AUTHORITY = "documentprovider2.com"
        private const val TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE = 2
        private const val TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY = "Account"
        private val TEST_DOCUMENT_PROVIDER_2_ROOT_URI =
            Uri.parse(
                "content://android.healthconnect.tests.documentprovider2.documents/root/account"
            )

        private val EXTERNAL_STORAGE_DOCUMENT_URI =
            Uri.parse("content://com.android.externalstorage.documents/document")
        private val DOWNLOADS_DOCUMENT_URI =
            Uri.parse("content://com.android.providers.downloads.documents/document")

        private const val DEFAULT_FILE_NAME = "Health Connect.zip"
    }

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val setFlagsRule = SetFlagsRule()

    @BindValue val healthDataExportManager: HealthDataExportManager = FakeHealthDataExportManager()
    private val fakeHealthDataExportManager = healthDataExportManager as FakeHealthDataExportManager
    @BindValue val deviceInfoUtils: DeviceInfoUtils = FakeDeviceInfoUtils()
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()
    @BindValue val timeSource = TestTimeSource

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        // Required for aconfig flag reading for tests run on pre V devices
        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG)
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
        reset(healthConnectLogger)
        fakeHealthDataExportManager.reset()
    }

    @Test
    fun exportDestinationFragment_isDisplayedCorrectly() {
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withId(R.id.export_destination_header_upload_icon)).check(matches(isDisplayed()))
        onView(withText("Choose where to save scheduled export")).check(matches(isDisplayed()))

        onView(withText("Back")).check(matches(isDisplayed()))
        onView(withText("Next")).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_impressionsLogged() {
        launchFragment<ExportDestinationFragment>(Bundle())

        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger)
            .logImpression(ExportDestinationElement.EXPORT_DESTINATION_BACK_BUTTON)
        verify(healthConnectLogger)
            .logImpression(ExportDestinationElement.EXPORT_DESTINATION_NEXT_BUTTON)
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
        verify(healthConnectLogger)
            .logInteraction(ExportDestinationElement.EXPORT_DESTINATION_BACK_BUTTON)
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(matches(isDisplayed()))
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY), isDisplayed())
                    )
                )
            )
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(matches(isDisplayed()))
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withText(TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY), isDisplayed())
                    )
                )
            )
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isNotChecked())
                    )
                )
            )
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isNotChecked())
                    )
                )
            )
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isChecked())
                    )
                )
            )
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE)).perform(click())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isNotChecked())
                    )
                )
            )
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isChecked())
                    )
                )
            )
    }

    @Test
    fun exportDestinationFragment_switchBackToPreviousSelectedDocumentProvider_previousSelectedAccountIsChecked() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        // Selects the second account for provider 1.
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .perform(click())
        onView(withText("Done")).inRoot(isDialog()).perform(click())
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY), isDisplayed())
                    )
                )
            )
        // Selects the provider 2.
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE)).perform(click())
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_2_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isChecked())
                    )
                )
            )
        // Switches back to provider 1.
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())

        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .check(matches(isChecked()))
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI))
        intended(hasExtra(Intent.EXTRA_TITLE, DEFAULT_FILE_NAME))
        verify(healthConnectLogger)
            .logInteraction(ExportDestinationElement.EXPORT_DESTINATION_NEXT_BUTTON)
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(
                ActivityResult(
                    RESULT_OK,
                    Intent().setData(TEST_DOCUMENT_PROVIDER_1_ROOT_1_DOCUMENT_URI),
                )
            )

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        assertThat(fakeHealthDataExportManager.getExportUri())
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(ActivityResult(RESULT_OK, Intent().setData(EXTERNAL_STORAGE_DOCUMENT_URI)))

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        assertThat(fakeHealthDataExportManager.getExportUri()).isNull()
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())
        intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(ActivityResult(RESULT_OK, Intent().setData(DOWNLOADS_DOCUMENT_URI)))

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        assertThat(fakeHealthDataExportManager.getExportUri()).isNull()
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_summary), not(isDisplayed()))
                    )
                )
            )
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
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
                        allOf(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY), isDisplayed())
                    )
                )
            )
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
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
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
        intended(hasExtra(Intent.EXTRA_TITLE, DEFAULT_FILE_NAME))
    }

    @Test
    fun exportDestinationFragment_noProviders_nextButtonNotEnabled() {
        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withId(R.id.export_import_next_button)).check(matches(isNotEnabled()))
    }

    @Test
    fun exportDestinationFragment_noProvidersAndPlayStoreNotAvailable_showsNoAppsText() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_no_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_noProvidersAndPlayStoreNotAvailable_doesNotShowPlayStoreButton() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_go_to_play_store_text))
            .check(matches(not(isDisplayed())))
    }

    @Test
    fun exportDestinationFragment_noProvidersAndPlayStoreAvailable_showsNoAppsText() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_no_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_noProvidersAndPlayStoreAvailable_showsPlayStoreButton() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_go_to_play_store_text)).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_noProvidersAndPlayStoreAvailable_goToPlayStoreClicked_launchesPlayStore() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        fakeHealthDataExportManager.setExportImportDocumentProviders(listOf())
        launchFragment<ExportDestinationFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.export_nav_graph)
            navHostController.setCurrentDestination(R.id.exportDestinationFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withId(R.id.export_import_go_to_play_store)).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.play_store_activity)
    }

    @Test
    fun exportDestinationFragment_singleProvider_hasRadioButtonChecked() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)

        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withId(R.id.item_document_provider_radio_button)).check(matches(isDisplayed()))
        onView(withId(R.id.item_document_provider_radio_button)).check(matches(isChecked()))
    }

    @Test
    fun exportDestinationFragment_singleProviderSingleAccount_showsAccountSummary() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY)).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_singleProviderSingleAccount_showsAccountSummary_nextButtonClicked_startsDocumentsUi() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText("Next")).perform(click())

        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI))
        intended(hasExtra(Intent.EXTRA_TITLE, DEFAULT_FILE_NAME))
    }

    @Test
    fun exportDestinationFragment_singleProviderMultipleAccounts_showsTapToSelectAccount() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_tap_to_choose_account)).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_singleProviderMultipleAccountsClicked_showsAccountPicker() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
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
    fun exportDestinationFragment_singleProviderMultipleAccountsClickedAndAccountChosen_updatesSummary() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
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
                        allOf(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY), isDisplayed())
                    )
                )
            )
    }

    @Test
    fun exportDestinationFragment_singleProviderMultipleAccountsClickedAndAccountChosen_nextButtonClicked_startsDocumentsUi() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
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
        intended(hasExtra(Intent.EXTRA_TITLE, DEFAULT_FILE_NAME))
    }

    @Test
    fun exportDestinationFragment_singleProviderAndPlayStoreNotAvailable_showsInstallAppsText() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_install_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_singleProviderAndPlayStoreNotAvailable_doesNotShowPlayStoreButton() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_go_to_play_store_text))
            .check(matches(not(isDisplayed())))
    }

    @Test
    fun exportDestinationFragment_singleProviderAndPlayStoreAvailable_showsInstallAppsText() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_install_apps_text)).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_singleProviderAndPlayStoreAvailable_showsPlayStoreButton() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(withText(R.string.export_import_go_to_play_store_text)).check(matches(isDisplayed()))
    }

    @Test
    fun exportDestinationFragment_singleProviderAndPlayStoreAvailable_goToPlayStoreClicked_launchesPlayStore() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        launchFragment<ExportDestinationFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.export_nav_graph)
            navHostController.setCurrentDestination(R.id.exportDestinationFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withId(R.id.export_import_go_to_play_store)).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.play_store_activity)
    }

    @Test
    fun exportDestinationFragment_orientationChanged_keepsSelection() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        val scenario = launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .perform(click())
        onView(withText("Done")).inRoot(isDialog()).perform(click())

        scenario.recreate()
        onIdle()

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withId(R.id.item_document_provider_radio_button), isChecked())
                    )
                )
            )
        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE))
            .check(
                matches(
                    hasDescendant(
                        allOf(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY), isDisplayed())
                    )
                )
            )
    }

    @Test
    fun exportDestinationFragment_orientationChanged_nextButtonClicked_startsDocumentsUi() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                ),
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_2_TITLE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_2_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_2_ROOT_URI,
                    TEST_DOCUMENT_PROVIDER_2_AUTHORITY,
                ),
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        val scenario = launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withText(TEST_DOCUMENT_PROVIDER_1_ROOT_2_SUMMARY))
            .inRoot(isDialog())
            .perform(click())
        onView(withText("Done")).inRoot(isDialog()).perform(click())

        scenario.recreate()
        onIdle()
        onView(withText("Next")).perform(click())

        intended(hasAction(Intent.ACTION_CREATE_DOCUMENT))
        intended(hasType("application/zip"))
        intended(hasExtra(DocumentsContract.EXTRA_INITIAL_URI, TEST_DOCUMENT_PROVIDER_1_ROOT_2_URI))
        intended(hasExtra(Intent.EXTRA_TITLE, DEFAULT_FILE_NAME))
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT_FAST_FOLLOW)
    @DisableFlags(Flags.FLAG_EXPORT_IMPORT_NICE_TO_HAVE)
    // TODO(b/393617190) Remove test when export_import_nice_to_have is enabled and removed
    fun exportDestinationFragment_nextExportSequentialNumberPresent_showsDefaultNameWithNumber() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        fakeHealthDataExportManager.setScheduledExportStatus(
            ScheduledExportStatus.Builder()
                .setDataExportError(ScheduledExportStatus.DATA_EXPORT_ERROR_NONE)
                .setPeriodInDays(ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays)
                .setNextExportSequentialNumber(42)
                .build()
        )
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        intended(hasExtra(Intent.EXTRA_TITLE, "Health Connect (42).zip"))
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT_FAST_FOLLOW, Flags.FLAG_EXPORT_IMPORT_NICE_TO_HAVE)
    fun exportDestinationFragment_showsFileNameWithManufacturerAndModel() {
        val documentProviders =
            listOf(
                ExportImportDocumentProvider(
                    TEST_DOCUMENT_PROVIDER_1_TITLE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_SUMMARY,
                    TEST_DOCUMENT_PROVIDER_1_ICON_RESOURCE,
                    TEST_DOCUMENT_PROVIDER_1_ROOT_1_URI,
                    TEST_DOCUMENT_PROVIDER_1_AUTHORITY,
                )
            )
        fakeHealthDataExportManager.setExportImportDocumentProviders(documentProviders)
        fakeHealthDataExportManager.setScheduledExportStatus(
            ScheduledExportStatus.Builder()
                .setDataExportError(ScheduledExportStatus.DATA_EXPORT_ERROR_NONE)
                .setPeriodInDays(ExportFrequency.EXPORT_FREQUENCY_DAILY.periodInDays)
                .setNextExportSequentialNumber(42)
                .build()
        )
        launchFragment<ExportDestinationFragment>(Bundle())

        onView(documentProviderWithTitle(TEST_DOCUMENT_PROVIDER_1_TITLE)).perform(click())
        onView(withId(R.id.export_import_next_button)).perform(click())

        val brand: String = Build.BRAND
        val model: String = Build.MODEL

        intended(hasExtra(Intent.EXTRA_TITLE, "$brand $model Health Connect (42).zip"
        ))
    }

    private fun documentProviderWithTitle(title: String): Matcher<View>? =
        allOf(withId(R.id.item_document_provider), hasDescendant(withText(title)))
}
