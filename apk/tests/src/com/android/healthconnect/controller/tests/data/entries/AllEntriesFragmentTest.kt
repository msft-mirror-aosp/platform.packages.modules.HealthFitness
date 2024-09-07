/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.data.entries

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.appdata.AppDataFragment.Companion.PERMISSION_TYPE_NAME_KEY
import com.android.healthconnect.controller.data.entries.AllEntriesFragment
import com.android.healthconnect.controller.data.entries.EntriesViewModel
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.Empty
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.Loading
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.LoadingFailed
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.With
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedDataEntry
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.STEPS
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.withIndex
import com.android.healthconnect.controller.utils.toInstantAtStartOfDay
import com.android.healthconnect.controller.utils.toLocalDate
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.junit.Ignore

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class AllEntriesFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val viewModel: EntriesViewModel = Mockito.mock(EntriesViewModel::class.java)

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        Mockito.`when`(viewModel.currentSelectedDate).thenReturn(MutableLiveData())
        Mockito.`when`(viewModel.period)
            .thenReturn(MutableLiveData(DateNavigationPeriod.PERIOD_DAY))
        Mockito.`when`(viewModel.appInfo)
            .thenReturn(
                MutableLiveData(
                    AppMetadata(
                        TEST_APP_PACKAGE_NAME,
                        TEST_APP_NAME,
                        context.getDrawable(R.drawable.health_connect_logo))))
        Mockito.`when`(viewModel.isDeletionState).thenReturn(MutableLiveData(false))
        Mockito.`when`(viewModel.setOfEntriesToBeDeleted).thenReturn(MutableLiveData())
    }

    @Test
    fun appEntriesInit_showsDateNavigationPreference() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(emptyList())))

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.date_picker_spinner)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_noData_showsNoData() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Empty))

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.no_data_view)).check(matches(isDisplayed()))
    }

    @Test
    fun appEntriesInit_error_showsErrorView() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(LoadingFailed))

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun appEntriesInit_loading_showsLoading() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Loading))

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.loading)).check(matches(isDisplayed()))
    }

    @Test
    fun appEntriesInit_withData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("12 steps")).check(matches(isDisplayed()))
        onView(withText("8:06 - 8:06")).check(matches(isDisplayed()))
        onView(withText("15 steps")).check(matches(isDisplayed()))
    }

    @Ignore("b/363994647")
    @Test
    fun appEntries_withData_onOrientationChange() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("12 steps")).check(matches(isDisplayed()))
        onView(withText("8:06 - 8:06")).check(matches(isDisplayed()))
        onView(withText("15 steps")).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        onIdle()
        onView(withText("7:06 - 7:06")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("12 steps")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("8:06 - 8:06")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("15 steps")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_noMedicalData_showsNoData() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Empty))

        val scenario =
            launchFragment<AllEntriesFragment>(
                bundleOf(PERMISSION_TYPE_NAME_KEY to MedicalPermissionType.IMMUNIZATION.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.no_data_view)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_medicalError_showsErrorView() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(LoadingFailed))

        val scenario =
            launchFragment<AllEntriesFragment>(
                bundleOf(PERMISSION_TYPE_NAME_KEY to MedicalPermissionType.IMMUNIZATION.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_medicalLoading_showsLoading() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Loading))

        val scenario =
            launchFragment<AllEntriesFragment>(
                bundleOf(PERMISSION_TYPE_NAME_KEY to MedicalPermissionType.IMMUNIZATION.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withId(R.id.loading)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_withMedicalData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries)
            .thenReturn(MutableLiveData(With(FORMATTED_IMMUNIZATION_LIST)))

        val scenario =
            launchFragment<AllEntriesFragment>(
                bundleOf(PERMISSION_TYPE_NAME_KEY to MedicalPermissionType.IMMUNIZATION.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("02 May 2023 • Health Connect Toolbox")).check(matches(isDisplayed()))
        onView(withText("12 Aug 2022 • Health Connect Toolbox")).check(matches(isDisplayed()))
        onView(withText("25 Sep 2021 • Health Connect Toolbox")).check(matches(isDisplayed()))
    }

    @Test
    fun allEntries_triggerDeletion_showsCheckboxes() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(true)
        }

        onView(withIndex(withId(R.id.item_checkbox_button), 0)).check(matches(isDisplayed()))
    }

    @Ignore("b/363994647")
    @Test
    fun allEntries_triggerDeletion_checkboxesRemainOnOrientationChange() = runTest{
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))
        Mockito.`when`(viewModel.isDeletionState).thenReturn(MutableLiveData(true))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(true)
        }

        onView(withIndex(withId(R.id.item_checkbox_button), 0)).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        advanceUntilIdle()

        onIdle()
        onView(withIndex(withId(R.id.item_checkbox_button), 0)).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    @Test
    fun allEntries_triggerDeletion_checkedItemsAddedToDeleteSet() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))

        val scenario =
                launchFragment<AllEntriesFragment>(
                        bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(true)
        }


        onView(withText("12 steps")).perform(click())
        onIdle()
        verify(viewModel).addToDeleteSet("test_id")
    }
}

private val FORMATTED_STEPS_LIST =
    listOf(
        FormattedDataEntry(
            uuid = "test_id",
            header = "7:06 - 7:06",
            headerA11y = "from 7:06 to 7:06",
            title = "12 steps",
            titleA11y = "12 steps",
            dataType = DataType.STEPS),
        FormattedDataEntry(
            uuid = "test_id_2",
            header = "8:06 - 8:06",
            headerA11y = "from 8:06 to 8:06",
            title = "15 steps",
            titleA11y = "15 steps",
            dataType = DataType.STEPS))

private val FORMATTED_IMMUNIZATION_LIST =
    listOf(
        FormattedEntry.FormattedMedicalDataEntry(
            header = "02 May 2023 • Health Connect Toolbox",
            headerA11y = "02 May 2023 • Health Connect Toolbox",
            title = "Covid vaccine",
            titleA11y = "important vaccination",
            time = Instant.now().toLocalDate().minusMonths(4).toInstantAtStartOfDay()),
        FormattedEntry.FormattedMedicalDataEntry(
            header = "12 Aug 2022 • Health Connect Toolbox",
            headerA11y = "12 Aug 2022 • Health Connect Toolbox",
            title = "Covid vaccine",
            titleA11y = "important vaccination",
            time = Instant.now().toLocalDate().minusMonths(2).minusDays(4).toInstantAtStartOfDay()),
        FormattedEntry.FormattedMedicalDataEntry(
            header = "25 Sep 2021 • Health Connect Toolbox",
            headerA11y = "25 Sep 2021 • Health Connect Toolbox",
            title = "Covid vaccine",
            titleA11y = "important vaccination",
            time = Instant.now().toLocalDate().minusMonths(1).plusDays(5).toInstantAtStartOfDay()))
