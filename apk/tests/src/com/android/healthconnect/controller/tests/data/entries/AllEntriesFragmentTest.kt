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
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedAggregation
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.EXERCISE
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.HEART_RATE
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.PLANNED_EXERCISE
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.SLEEP
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType.STEPS
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_RESOURCE_IMMUNIZATION
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_RESOURCE_IMMUNIZATION_2
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_RESOURCE_IMMUNIZATION_3
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.withIndex
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify

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
                        context.getDrawable(R.drawable.health_connect_logo),
                    )
                )
            )
        Mockito.`when`(viewModel.screenState)
            .thenReturn(MutableLiveData(EntriesViewModel.EntriesDeletionScreenState.VIEW))
        Mockito.`when`(viewModel.setOfEntriesToBeDeleted).thenReturn(MutableLiveData())
        Mockito.`when`(viewModel.allEntriesSelected).thenReturn(MutableLiveData(false))
    }

    @Test
    fun appEntriesInit_showsDateNavigationPreference() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn((FORMATTED_STEPS_LIST.toMutableList()))

        launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))

        onView(withId(R.id.date_picker_spinner)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_noData_showsNoData() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Empty))

        launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))

        onView(withId(R.id.no_data_view)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_error_showsErrorView() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(LoadingFailed))

        launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))

        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_loading_showsLoading() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Loading))

        launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))

        onView(withId(R.id.loading)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_withSleepData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_SLEEP_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_SLEEP_LIST.toMutableList())

        launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to SLEEP.name))

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("7 hours")).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_withHeartRateData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries)
            .thenReturn(MutableLiveData(With(FORMATTED_HEART_RATE_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_HEART_RATE_LIST.toMutableList())

        launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to HEART_RATE.name))

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("128 - 140 bpm")).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_withExerciseData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries)
            .thenReturn(MutableLiveData(With(FORMATTED_EXERCISE_SESSION_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_EXERCISE_SESSION_LIST.toMutableList())

        launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to EXERCISE.name))

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("Biking")).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_withPlannedExerciseData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries)
            .thenReturn(MutableLiveData(With(FORMATTED_PLANNED_EXERCISE_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_PLANNED_EXERCISE_LIST.toMutableList())

        launchFragment<AllEntriesFragment>(
            bundleOf(PERMISSION_TYPE_NAME_KEY to PLANNED_EXERCISE.name)
        )

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("Workout")).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_withStepsData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_PLANNED_EXERCISE_LIST.toMutableList())

        launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))

        onView(withText("7:06 - 7:06")).check(matches(isDisplayed()))
        onView(withText("12 steps")).check(matches(isDisplayed()))
        onView(withText("8:06 - 8:06")).check(matches(isDisplayed()))
        onView(withText("15 steps")).check(matches(isDisplayed()))
    }

    @Ignore("b/363994647")
    @Test
    fun appEntries_withData_onOrientationChange() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_STEPS_LIST.toMutableList())

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))

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

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    @Test
    fun allEntriesInit_noMedicalData_showsNoData() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Empty))

        launchFragment<AllEntriesFragment>(
            bundleOf(PERMISSION_TYPE_NAME_KEY to MedicalPermissionType.IMMUNIZATION.name)
        )

        onView(withId(R.id.no_data_view)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_medicalError_showsErrorView() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(LoadingFailed))

        launchFragment<AllEntriesFragment>(
            bundleOf(PERMISSION_TYPE_NAME_KEY to MedicalPermissionType.IMMUNIZATION.name)
        )

        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_medicalLoading_showsLoading() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(Loading))

        launchFragment<AllEntriesFragment>(
            bundleOf(PERMISSION_TYPE_NAME_KEY to MedicalPermissionType.IMMUNIZATION.name)
        )

        onView(withId(R.id.loading)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntriesInit_withMedicalData_showsListOfEntries() {
        Mockito.`when`(viewModel.entries)
            .thenReturn(MutableLiveData(With(FORMATTED_IMMUNIZATION_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_IMMUNIZATION_LIST.toMutableList())

        launchFragment<AllEntriesFragment>(
            bundleOf(PERMISSION_TYPE_NAME_KEY to MedicalPermissionType.IMMUNIZATION.name)
        )

        onView(withText("02 May 2023 • Health Connect Toolbox")).check(matches(isDisplayed()))
        onView(withText("12 Aug 2022 • Health Connect Toolbox")).check(matches(isDisplayed()))
        onView(withText("25 Sep 2021 • Health Connect Toolbox")).check(matches(isDisplayed()))
    }

    @Test
    fun allEntries_triggerDeletion_showsCheckboxes() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_STEPS_LIST.toMutableList())

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(
                EntriesViewModel.EntriesDeletionScreenState.DELETE
            )
        }

        onView(withIndex(withId(R.id.item_checkbox_button), 1)).check(matches(isDisplayed()))
    }

    @Ignore("b/363994647")
    @Test
    fun allEntries_triggerDeletion_checkboxesRemainOnOrientationChange() = runTest {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST)))
        Mockito.`when`(viewModel.screenState)
            .thenReturn(MutableLiveData(EntriesViewModel.EntriesDeletionScreenState.DELETE))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_STEPS_LIST.toMutableList())

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(
                EntriesViewModel.EntriesDeletionScreenState.DELETE
            )
        }

        onView(withIndex(withId(R.id.item_checkbox_button), 1)).check(matches(isDisplayed()))

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
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(
            FORMATTED_STEPS_LIST_WITH_AGGREGATION)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_STEPS_LIST_WITH_AGGREGATION.toMutableList())

        val scenario =
            launchFragment<AllEntriesFragment>(bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(
                EntriesViewModel.EntriesDeletionScreenState.DELETE
            )
        }

        onView(withText("12 steps")).perform(click())
        onIdle()
        verify(viewModel).addToDeleteSet("test_id")
    }

    @Test
    fun allEntries_triggerDeletion_displaysSelectAll() {
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST_WITH_AGGREGATION)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_STEPS_LIST_WITH_AGGREGATION.toMutableList())

        val scenario =
            launchFragment<AllEntriesFragment>(
                bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(
                EntriesViewModel.EntriesDeletionScreenState.DELETE
            )
        }

        onIdle()
        onView(withIndex(withText("Select all"), 0)).check(matches(isDisplayed()))
    }

    @Test
    fun allEntries_selectAllChecked_allEntriesChecked(){
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST_WITH_AGGREGATION)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_STEPS_LIST_WITH_AGGREGATION.toMutableList())

        val scenario =
            launchFragment<AllEntriesFragment>(
                bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(
                EntriesViewModel.EntriesDeletionScreenState.DELETE
            )
        }

        onView(withText("Select all")).perform(click())
        onIdle()
        verify(viewModel).addToDeleteSet("test_id")
        verify(viewModel).addToDeleteSet("test_id_2")

    }

    @Test
    fun allEntries_selectAllUnchecked_allEntriesUnchecked(){
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST_WITH_AGGREGATION)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_STEPS_LIST_WITH_AGGREGATION.toMutableList())
        Mockito.`when`(viewModel.setOfEntriesToBeDeleted).thenReturn(MutableLiveData(setOf("test_id", "test_id_2")))
        Mockito.`when`(viewModel.allEntriesSelected).thenReturn(MutableLiveData(true))

        val scenario =
            launchFragment<AllEntriesFragment>(
                bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllEntriesFragment).triggerDeletionState(
                EntriesViewModel.EntriesDeletionScreenState.DELETE
            )
        }

        onView(withText("Select all")).perform(click())
        onIdle()
        verify(viewModel).removeFromDeleteSet("test_id")
        verify(viewModel).removeFromDeleteSet("test_id_2")
    }

    @Ignore("Test failing b/367274683")
    @Test
    fun allEntries_selectAllRemains_onOrientationChange() = runTest{
        Mockito.`when`(viewModel.screenState).thenReturn(MutableLiveData(EntriesViewModel.EntriesDeletionScreenState.DELETE))
        Mockito.`when`(viewModel.entries).thenReturn(MutableLiveData(With(FORMATTED_STEPS_LIST_WITH_AGGREGATION)))
        Mockito.`when`(viewModel.getEntriesList()).thenReturn(FORMATTED_STEPS_LIST_WITH_AGGREGATION.toMutableList())

        val scenario =
            launchFragment<AllEntriesFragment>(
                bundleOf(PERMISSION_TYPE_NAME_KEY to STEPS.name))

        onView(withIndex(withText("Select all"), 0)).check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        advanceUntilIdle()

        onIdle()
        onView(withIndex(withText("Select all"), 0))
            .perform(scrollTo())
            .check(matches(isDisplayed()))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
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
            dataType = DataType.STEPS,
        ),
        FormattedDataEntry(
            uuid = "test_id_2",
            header = "8:06 - 8:06",
            headerA11y = "from 8:06 to 8:06",
            title = "15 steps",
            titleA11y = "15 steps",
            dataType = DataType.STEPS,
        ),
    )

private val FORMATTED_SLEEP_LIST =
    listOf(
        FormattedEntry.SleepSessionEntry(
            uuid = "test_id",
            header = "7:06 - 7:06",
            headerA11y = "from 7:06 to 7:06",
            title = "7 hours",
            titleA11y = "7 hours",
            dataType = DataType.SLEEP,
            notes = "",
        )
    )
private val FORMATTED_HEART_RATE_LIST =
    listOf(
        FormattedEntry.SeriesDataEntry(
            uuid = "test_id",
            header = "7:06 - 7:06",
            headerA11y = "from 7:06 to 7:06",
            title = "128 - 140 bpm",
            titleA11y = "128 - 140 bpm",
            dataType = DataType.HEART_RATE,
        )
    )
private val FORMATTED_PLANNED_EXERCISE_LIST =
    listOf(
        FormattedEntry.PlannedExerciseSessionEntry(
            uuid = "test_id",
            header = "7:06 - 7:06",
            headerA11y = "from 7:06 to 7:06",
            title = "Workout",
            titleA11y = "Workout",
            dataType = DataType.PLANNED_EXERCISE,
            notes = "",
        )
    )
private val FORMATTED_EXERCISE_SESSION_LIST =
    listOf(
        FormattedEntry.ExerciseSessionEntry(
            uuid = "test_id",
            header = "7:06 - 7:06",
            headerA11y = "from 7:06 to 7:06",
            title = "Biking",
            titleA11y = "Biking",
            dataType = DataType.EXERCISE,
            notes = "",
        )
    )

private val FORMATTED_IMMUNIZATION_LIST =
    listOf(
        FormattedEntry.FormattedMedicalDataEntry(
            header = "02 May 2023 • Health Connect Toolbox",
            headerA11y = "02 May 2023 • Health Connect Toolbox",
            title = "Covid vaccine",
            titleA11y = "important vaccination",
            medicalResourceId = TEST_MEDICAL_RESOURCE_IMMUNIZATION.id,
        ),
        FormattedEntry.FormattedMedicalDataEntry(
            header = "12 Aug 2022 • Health Connect Toolbox",
            headerA11y = "12 Aug 2022 • Health Connect Toolbox",
            title = "Covid vaccine",
            titleA11y = "important vaccination",
            medicalResourceId = TEST_MEDICAL_RESOURCE_IMMUNIZATION_2.id,
        ),
        FormattedEntry.FormattedMedicalDataEntry(
            header = "25 Sep 2021 • Health Connect Toolbox",
            headerA11y = "25 Sep 2021 • Health Connect Toolbox",
            title = "Covid vaccine",
            titleA11y = "important vaccination",
            medicalResourceId = TEST_MEDICAL_RESOURCE_IMMUNIZATION_3.id,
        ),
    )

private val FORMATTED_STEPS_LIST_WITH_AGGREGATION =
    listOf(
        FormattedAggregation(
            aggregation = "27",
            aggregationA11y = "27",
            contributingApps = TEST_APP_NAME
        ),
        FormattedDataEntry(
            uuid = "test_id",
            header = "7:06 - 7:06",
            headerA11y = "from 7:06 to 7:06",
            title = "12 steps",
            titleA11y = "12 steps",
            dataType = DataType.STEPS,
        ),
        FormattedDataEntry(
            uuid = "test_id_2",
            header = "8:06 - 8:06",
            headerA11y = "from 8:06 to 8:06",
            title = "15 steps",
            titleA11y = "15 steps",
            dataType = DataType.STEPS,
        ),
    )