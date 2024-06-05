/**
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.dataentries

import android.content.Context
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedAggregation
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedDataEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.PlannedExerciseSessionEntry
import com.android.healthconnect.controller.dataentries.DataEntriesFragment
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel.DataEntriesFragmentState.Empty
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel.DataEntriesFragmentState.Loading
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel.DataEntriesFragmentState.LoadingFailed
import com.android.healthconnect.controller.dataentries.DataEntriesFragmentViewModel.DataEntriesFragmentState.WithData
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.PLANNED_EXERCISE
import com.android.healthconnect.controller.permissions.data.HealthPermissionType.STEPS
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.withIndex
import com.android.healthconnect.controller.utils.logging.DataEntriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class DataEntriesFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: DataEntriesFragmentViewModel =
        Mockito.mock(DataEntriesFragmentViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        Mockito.`when`(viewModel.currentSelectedDate).thenReturn(MutableLiveData())
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    fun dataEntriesInit_showsDateNavigationPreference() {
        Mockito.`when`(viewModel.dataEntries).thenReturn(MutableLiveData(WithData(emptyList())))

        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to STEPS))

        onView(withId(R.id.selected_date)).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.DATA_ENTRIES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(DataEntriesElement.SELECT_DATE_BUTTON)
        verify(healthConnectLogger).logImpression(DataEntriesElement.NEXT_DAY_BUTTON)
        verify(healthConnectLogger).logImpression(DataEntriesElement.PREVIOUS_DAY_BUTTON)
    }

    @Test
    fun dataEntriesInit_noData_showsNoData() {
        Mockito.`when`(viewModel.dataEntries).thenReturn(MutableLiveData(Empty))

        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to STEPS))

        onView(withId(R.id.no_data_view)).check(matches(isDisplayed()))
    }

    @Test
    fun dataEntriesInit_error_showsNoData() {
        Mockito.`when`(viewModel.dataEntries).thenReturn(MutableLiveData(LoadingFailed))

        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to STEPS))

        onView(withId(R.id.error_view)).check(matches(isDisplayed()))
    }

    @Test
    fun dataEntriesInit_loading_showsLoading() {
        Mockito.`when`(viewModel.dataEntries).thenReturn(MutableLiveData(Loading))

        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to STEPS))

        onView(withId(R.id.loading)).check(matches(isDisplayed()))
    }

    @Test
    fun dataEntriesInit_withData_showsListOfEntries() {
        Mockito.`when`(viewModel.dataEntries)
            .thenReturn(MutableLiveData(WithData(FORMATTED_STEPS_LIST)))

        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to STEPS))

        onView(withText("127 steps")).check(matches(isDisplayed()))
        onView(withText("TEST_APP_NAME, TEST_APP_NAME_2")).check(matches(isDisplayed()))
        onView(withText("7:06 - 7:06 • TEST_APP_NAME")).check(matches(isDisplayed()))
        onView(withText("12 steps")).check(matches(isDisplayed()))
        onView(withText("8:06 - 8:06 • TEST_APP_NAME")).check(matches(isDisplayed()))
        onView(withText("15 steps")).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(DataEntriesElement.AGGREGATION_DATA_VIEW)
        verify(healthConnectLogger, times(2)).logImpression(DataEntriesElement.DATA_ENTRY_VIEW)
        verify(healthConnectLogger, times(2))
            .logImpression(DataEntriesElement.DATA_ENTRY_DELETE_BUTTON)
    }

    @Test
    fun dataEntriesInit_withPlannedExerciseData_showsListOfPlannedExerciseEntries() {
        Mockito.`when`(viewModel.dataEntries)
            .thenReturn(MutableLiveData(WithData(FORMATTED_PLANNED_EXERCISE_LIST)))

        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to PLANNED_EXERCISE))

        onView(withText("7:00 AM - 10:00 AM • TEST_APP_NAME")).check(matches(isDisplayed()))
        onView(withText("Running • Morning Run")).check(matches(isDisplayed()))
        onView(withText("4:00 PM - 5:00 PM • TEST_APP_NAME")).check(matches(isDisplayed()))
        onView(withText("Swimming • Freestyle Technique Bootcamp")).check(matches(isDisplayed()))
        verify(healthConnectLogger, times(2))
            .logImpression(DataEntriesElement.PLANNED_EXERCISE_SESSION_ENTRY_BUTTON)
        verify(healthConnectLogger, times(2))
            .logImpression(DataEntriesElement.DATA_ENTRY_DELETE_BUTTON)
    }

    @Test
    fun dataEntries_withData_showsDeleteAction() {
        Mockito.`when`(viewModel.dataEntries)
            .thenReturn(MutableLiveData(WithData(FORMATTED_STEPS_LIST)))

        launchFragment<DataEntriesFragment>(bundleOf(PERMISSION_TYPE_KEY to STEPS))

        onView(withIndex(withId(R.id.item_data_entry_delete), 0)).check(matches(isDisplayed()))
    }
}

private val FORMATTED_STEPS_LIST =
    listOf(
        FormattedDataEntry(
            uuid = "test_id",
            header = "7:06 - 7:06 • TEST_APP_NAME",
            headerA11y = "from 7:06 to 7:06 • TEST_APP_NAME",
            title = "12 steps",
            titleA11y = "12 steps",
            dataType = DataType.STEPS),
        FormattedDataEntry(
            uuid = "test_id",
            header = "8:06 - 8:06 • TEST_APP_NAME",
            headerA11y = "from 8:06 to 8:06 • TES   T_APP_NAME",
            title = "15 steps",
            titleA11y = "15 steps",
            dataType = DataType.STEPS),
        FormattedAggregation(
            aggregation = "127 steps",
            aggregationA11y = "127 steps",
            contributingApps = "TEST_APP_NAME, TEST_APP_NAME_2"))

private val FORMATTED_PLANNED_EXERCISE_LIST =
    listOf(
        PlannedExerciseSessionEntry(
            uuid = "test_id",
            header = "7:00 AM - 10:00 AM • TEST_APP_NAME",
            headerA11y = "from 7:00 AM to 10:00 AM • TEST_APP_NAME",
            title = "Running • Morning Run",
            titleA11y = "Running • Morning Run",
            notes = "Daily morning run.",
            dataType = DataType.PLANNED_EXERCISE),
        PlannedExerciseSessionEntry(
            uuid = "test_id",
            header = "4:00 PM - 5:00 PM • TEST_APP_NAME",
            headerA11y = "from 4:00 PM to 5:00 PM • TEST_APP_NAME",
            title = "Swimming • Freestyle Technique Bootcamp",
            titleA11y = "Swimming • Freestyle Technique Bootcamp",
            notes = "A weekly swimming training plan.",
            dataType = DataType.PLANNED_EXERCISE))
