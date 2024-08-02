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

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Context
import android.content.Intent
import android.health.connect.exportimport.ImportStatus.*
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.backuprestore.BackupAndRestoreSettingsFragment
import com.android.healthconnect.controller.exportimport.ImportFlowActivity
import com.android.healthconnect.controller.exportimport.api.DocumentProviders
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.ExportSettings
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import com.android.healthconnect.controller.exportimport.api.ExportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.ImportFlowViewModel
import com.android.healthconnect.controller.exportimport.api.ImportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.ImportUiState
import com.android.healthconnect.controller.exportimport.api.ImportUiStatus
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiState
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiStatus
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.logging.BackupAndRestoreElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class BackupAndRestoreSettingsFragmentTest {

    companion object {
        private const val TEST_EXPORT_PERIOD_IN_DAYS = 1
        private const val TEST_LAST_EXPORT_APP_NAME = "Drive"
        private const val TEST_LAST_EXPORT_FILE_NAME = "healthconnect.zip"
        private const val TEST_LAST_IMPORT_URI = "content://com.android.documents.testFile"
        private const val IMPORT_FILE_URI_KEY = "selectedUri"
        private val TEST_LAST_IMPORT_COMPLETION_TIME = Instant.parse("2022-09-20T07:06:05.432Z")
    }

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    // TODO: b/348591669 - Replace the mock with a fake and investigate the UI tests.
    @BindValue
    val exportSettingsViewModel: ExportSettingsViewModel =
        Mockito.mock(ExportSettingsViewModel::class.java)

    @BindValue
    val exportStatusViewModel: ExportStatusViewModel =
        Mockito.mock(ExportStatusViewModel::class.java)

    @BindValue
    val importStatusViewModel: ImportStatusViewModel =
        Mockito.mock(ImportStatusViewModel::class.java)

    @BindValue
    val importFlowViewModel: ImportFlowViewModel = Mockito.mock(ImportFlowViewModel::class.java)

    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    private var previousDefaultTimeZone: TimeZone? = null
    private var previousLocale: Locale? = null

    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        previousDefaultTimeZone = TimeZone.getDefault()
        previousLocale = Locale.getDefault()

        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)

        Intents.init()

        whenever(importStatusViewModel.storedImportStatus).then {
            MutableLiveData(
                ImportUiStatus.WithData(
                    ImportUiState(
                        dataImportError = ImportUiState.DataImportError.DATA_IMPORT_ERROR_NONE,
                        isImportOngoing = false)))
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        periodInDays = 0,
                        lastExportFileName = TEST_LAST_EXPORT_FILE_NAME,
                        lastExportAppName = TEST_LAST_EXPORT_APP_NAME)))
        }
        whenever(importFlowViewModel.lastImportCompletionInstant).then {
            MutableLiveData(TEST_LAST_IMPORT_COMPLETION_TIME)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        reset(healthConnectLogger)
        Intents.release()

        TimeZone.setDefault(previousDefaultTimeZone)
        previousLocale?.let { locale -> Locale.setDefault(locale) }
    }

    @Test
    fun backupAndRestoreSettingsFragmentInit_showsFragmentCorrectly() {
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        NOW,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        TEST_EXPORT_PERIOD_IN_DAYS,
                        TEST_LAST_EXPORT_FILE_NAME,
                        TEST_LAST_EXPORT_APP_NAME)))
        }
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_WEEKLY))
        }

        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Scheduled export")).check(matches(isDisplayed()))
        onView(withText("Import data")).check(matches(isDisplayed()))
        onView(withText("Restore data from a previously exported file"))
            .check(matches(isDisplayed()))
        onView(withText("Last export: Oct 20, 7:06 AM")).check(matches(isDisplayed()))
        onView(withText("Export lets you save your data so you can transfer it to a new phone"))
            .check(matches(isDisplayed()))
        onView(withText("About backup and restore")).check(matches(isDisplayed()))
        onView(withText("Drive • healthconnect.zip")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_impressionsLogged() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_DAILY))
        }

        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(BackupAndRestoreElement.SCHEDULED_EXPORT_BUTTON)
        verify(healthConnectLogger).logImpression(BackupAndRestoreElement.RESTORE_DATA_BUTTON)
    }

    @Test
    fun backupAndRestoreSettingsFragment_withNoLastSuccessfulDate_doesNotShowLastExportTime() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_WEEKLY))
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        lastSuccessfulExportTime = null,
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        periodInDays = TEST_EXPORT_PERIOD_IN_DAYS)))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Last export: Oct 20, 7:06 AM")).check(doesNotExist())
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenOnlyAppNameIsAvailable_showsAppName() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_WEEKLY))
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        lastSuccessfulExportTime = NOW,
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        periodInDays = TEST_EXPORT_PERIOD_IN_DAYS,
                        lastExportAppName = TEST_LAST_EXPORT_APP_NAME)))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Drive")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenOnlyFileNameIsAvailable_showsFileName() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_WEEKLY))
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        lastSuccessfulExportTime = NOW,
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        periodInDays = TEST_EXPORT_PERIOD_IN_DAYS,
                        lastExportFileName = TEST_LAST_EXPORT_FILE_NAME)))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("healthconnect.zip")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenImportStarted_importPreferenceDisabled() = runTest {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        whenever(exportSettingsViewModel.documentProviders).then {
            MutableLiveData(DocumentProviders.WithData(listOf()))
        }

        val expectedResult =
            ActivityResult(
                Activity.RESULT_OK, Intent().putExtra(IMPORT_FILE_URI_KEY, TEST_LAST_IMPORT_URI))
        intending(hasComponent(ImportFlowActivity::class.java.name)).respondWith(expectedResult)

        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Import data")).check(matches(isEnabled()))
        onView(withText("Import data")).perform(click())
        onView(withText("Import data")).check(matches(not(isEnabled())))

        intended(hasComponent(ImportFlowActivity::class.java.name))
        verify(importFlowViewModel).triggerImportOfSelectedFile(Uri.parse(TEST_LAST_IMPORT_URI))
    }

    @Test
    fun backupAndRestoreSettingsFragment_clicksImportData_navigatesToImportFlowActivity() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        whenever(exportSettingsViewModel.documentProviders).then {
            MutableLiveData(DocumentProviders.WithData(listOf()))
        }

        val expectedResult =
            ActivityResult(
                Activity.RESULT_OK, Intent().putExtra(IMPORT_FILE_URI_KEY, TEST_LAST_IMPORT_URI))
        intending(hasComponent(ImportFlowActivity::class.java.name)).respondWith(expectedResult)

        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Import data")).check(matches(isDisplayed()))
        onView(withText("Import data")).check(matches(isEnabled()))
        onView(withText("Import data")).perform(click())

        intended(hasComponent(ImportFlowActivity::class.java.name))

        verify(healthConnectLogger).logInteraction(BackupAndRestoreElement.RESTORE_DATA_BUTTON)
    }

    @Test
    fun backupAndRestoreSettingsFragment_clicksScheduledExportWhenItIsOff_navigatesToExportSetupActivity() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.backupAndRestoreSettingsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Scheduled export")).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.exportSetupActivity)
        verify(healthConnectLogger).logInteraction(BackupAndRestoreElement.SCHEDULED_EXPORT_BUTTON)
    }

    @Test
    fun backupAndRestoreSettingsFragment_clicksScheduledExportWhenItIsOn_navigatesToScheduledExportFragment() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_DAILY))
        }
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
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Off")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsDaily_showsCorrectSummary() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_DAILY))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Daily")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsWeekly_showsCorrectSummary() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_WEEKLY))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Weekly")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenExportFrequencyIsMonthly_showsCorrectSummary() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_MONTHLY))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("On • Monthly")).check(matches(isDisplayed()))
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenImportErrorIsWrongFile_showsImportErrorBanner() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        whenever(importStatusViewModel.storedImportStatus).then {
            MutableLiveData(
                ImportUiStatus.WithData(
                    ImportUiState(
                        dataImportError =
                            ImportUiState.DataImportError.DATA_IMPORT_ERROR_WRONG_FILE,
                        isImportOngoing = false,
                    )))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Choose file")).check(matches(isDisplayed()))
        onView(withText("Couldn't restore data")).check(matches(isDisplayed()))
        onView(
                withText(
                    "The file you selected isn't compatible for restore. Make sure to select the correct exported file."))
            .check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(BackupAndRestoreElement.IMPORT_WRONG_FILE_ERROR_BANNER)
        verify(healthConnectLogger)
            .logImpression(BackupAndRestoreElement.IMPORT_WRONG_FILE_ERROR_BANNER_BUTTON)
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenImportErrorIsVersionMismatch_showsImportErrorBanner() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        whenever(importStatusViewModel.storedImportStatus).then {
            MutableLiveData(
                ImportUiStatus.WithData(
                    ImportUiState(
                        ImportUiState.DataImportError.DATA_IMPORT_ERROR_VERSION_MISMATCH,
                        /** isImportOngoing= */
                        false,
                    )))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Update now")).check(matches(isDisplayed()))
        onView(withText("Couldn't restore data")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Update your system so that Health\u00A0Connect can restore your data, then try again."))
            .check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(BackupAndRestoreElement.IMPORT_VERSION_MISMATCH_ERROR_BANNER)
        verify(healthConnectLogger)
            .logImpression(BackupAndRestoreElement.IMPORT_VERSION_MISMATCH_ERROR_BANNER_BUTTON)
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenImportErrorIsUnknown_showsImportErrorBanner() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        whenever(importStatusViewModel.storedImportStatus).then {
            MutableLiveData(
                ImportUiStatus.WithData(
                    ImportUiState(
                        ImportUiState.DataImportError.DATA_IMPORT_ERROR_UNKNOWN,
                        /** isImportOngoing= */
                        false,
                    )))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Try again")).check(matches(isDisplayed()))
        onView(withText("Couldn't restore data")).check(matches(isDisplayed()))
        onView(withText("There was a problem with restoring data from your export."))
            .check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(BackupAndRestoreElement.IMPORT_GENERAL_ERROR_BANNER)
        verify(healthConnectLogger)
            .logImpression(BackupAndRestoreElement.IMPORT_GENERAL_ERROR_BANNER_BUTTON)
    }

    @Test
    fun backupAndRestoreSettingsFragment_whenImportErrorIsNone_doesNotShowImportErrorBanner() {
        whenever(exportSettingsViewModel.storedExportSettings).then {
            MutableLiveData(ExportSettings.WithData(ExportFrequency.EXPORT_FREQUENCY_NEVER))
        }
        whenever(importStatusViewModel.storedImportStatus).then {
            MutableLiveData(
                ImportUiStatus.WithData(
                    ImportUiState(
                        dataImportError = ImportUiState.DataImportError.DATA_IMPORT_ERROR_NONE,
                        isImportOngoing = false)))
        }
        launchFragment<BackupAndRestoreSettingsFragment>(Bundle())

        onView(withText("Couldn't restore data")).check(doesNotExist())
    }
}
