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
package com.android.healthconnect.controller.tests.deletion

import android.health.connect.HealthDataCategory
import android.health.connect.datatypes.StepsRecord
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.deletion.ChosenRange
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionConstants.START_INACTIVE_APP_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionFragment
import com.android.healthconnect.controller.deletion.DeletionParameters
import com.android.healthconnect.controller.deletion.DeletionState
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.DeletionViewModel
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

@HiltAndroidTest
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
class DeletionFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val viewModel: DeletionViewModel = Mockito.mock(DeletionViewModel::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
        whenever(viewModel.isInactiveApp).then { false }
        toggleAnimation(false)
    }

    @After
    fun tearDown() {
        toggleAnimation(true)
    }

    // Delete all data flow
    @Test
    fun deleteAllData_timeRangeDialog_showsCorrectText() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(DeletionParameters(deletionType = deletionTypeAllData))
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withText("Choose data to delete from Health Connect"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "This permanently deletes all data added to Health\u00A0Connect in the chosen" +
                        " time period"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 24 hours"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 7 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 30 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete all data"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Next")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deleteCategoryData_timeRangeDialog_showsCorrectText() {
        val deletionTypeCategory =
            DeletionType.DeletionTypeCategoryData(category = HealthDataCategory.ACTIVITY)

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(DeletionParameters(deletionType = deletionTypeCategory))
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeCategory),
                )
        }

        onView(withText("Choose data to delete from Health Connect"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "This permanently deletes activity data added to Health\u00A0Connect in the chosen" +
                        " time period"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 24 hours"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 7 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 30 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete all data"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Next")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeData_timeRangeDialog_showsCorrectText() {
        val deletionTypeFitnessPermissionType =
            DeletionType.DeletionTypeHealthPermissionTypeData(
                fitnessPermissionType = FitnessPermissionType.BLOOD_GLUCOSE
            )

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(DeletionParameters(deletionType = deletionTypeFitnessPermissionType))
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeFitnessPermissionType),
                )
        }

        onView(withText("Choose data to delete from Health Connect"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "This permanently deletes blood glucose data added to Health\u00A0Connect in the chosen" +
                        " time period"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 24 hours"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 7 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 30 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete all data"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Next")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deleteAppData_timeRangeDialog_showsCorrectText() {
        val deletionTypeAppData =
            DeletionType.DeletionTypeAppData(
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(DeletionParameters(deletionType = deletionTypeAppData))
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAppData),
                )
        }

        onView(withText("Choose data to delete from Health Connect"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "This permanently deletes $TEST_APP_NAME data added to Health\u00A0Connect in the chosen" +
                        " time period"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 24 hours"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 7 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 30 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete all data"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Next")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeFromApp_timeRangeDialog_showsCorrectText() {
        val deletionTypePermissionTypeFromApp =
            DeletionType.DeletionTypeHealthPermissionTypeFromApp(
                fitnessPermissionType = FitnessPermissionType.STEPS,
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(DeletionParameters(deletionType = deletionTypePermissionTypeFromApp))
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypePermissionTypeFromApp),
                )
        }

        onView(withText("Choose data to delete from Health Connect"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "This permanently deletes steps data added by $TEST_APP_NAME to Health\u00A0Connect in the chosen" +
                        " time period"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 24 hours"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 7 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 30 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete all data"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Next")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deleteAllData_confirmationDialogForOneDay_showsCorrectText() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_24_HOURS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_one_day))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from the last 24 hours?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteAllData_confirmationDialogForOneWeek_showsCorrectText() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_7_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_one_week))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from the last 7 days?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteAllData_confirmationDialogForOneMonth_showsCorrectText() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_30_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_one_month))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from the last 30 days?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteAllData_confirmationDialogForAllTime_showsCorrectText() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteCategoryData_confirmationDialogForOneDay_showsCorrectText() {
        val deletionTypeCategory =
            DeletionType.DeletionTypeCategoryData(category = HealthDataCategory.ACTIVITY)

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeCategory,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_24_HOURS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeCategory),
                )
        }

        onView(withId(R.id.radio_button_one_day))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete activity data from the last 24 hours?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteCategoryData_confirmationDialogForOneWeek_showsCorrectText() {
        val deletionTypeCategory =
            DeletionType.DeletionTypeCategoryData(category = HealthDataCategory.ACTIVITY)
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeCategory,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_7_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeCategory),
                )
        }

        onView(withId(R.id.radio_button_one_week))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete activity data from the last 7 days?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteCategoryData_confirmationDialogForOneMonth_showsCorrectText() {
        val deletionTypeCategory =
            DeletionType.DeletionTypeCategoryData(category = HealthDataCategory.ACTIVITY)

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeCategory,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_30_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeCategory),
                )
        }

        onView(withId(R.id.radio_button_one_month))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete activity data from the last 30 days?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteCategoryData_confirmationDialogForAllTime_showsCorrectText() {
        val deletionTypeCategory =
            DeletionType.DeletionTypeCategoryData(category = HealthDataCategory.ACTIVITY)
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeCategory,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeCategory),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete activity data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeData_confirmationDialogForOneDay_showsCorrectText() {
        val deletionTypeFitnessPermissionType =
            DeletionType.DeletionTypeHealthPermissionTypeData(
                fitnessPermissionType = FitnessPermissionType.BLOOD_GLUCOSE
            )

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeFitnessPermissionType,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_24_HOURS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeFitnessPermissionType),
                )
        }

        onView(withId(R.id.radio_button_one_day))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete blood glucose data from the last 24 hours?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeData_confirmationDialogForOneWeek_showsCorrectText() {
        val deletionTypeFitnessPermissionType =
            DeletionType.DeletionTypeHealthPermissionTypeData(
                fitnessPermissionType = FitnessPermissionType.BLOOD_GLUCOSE
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeFitnessPermissionType,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_7_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeFitnessPermissionType),
                )
        }

        onView(withId(R.id.radio_button_one_week))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete blood glucose data from the last 7 days?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeData_confirmationDialogForOneMonth_showsCorrectText() {
        val deletionTypeFitnessPermissionType =
            DeletionType.DeletionTypeHealthPermissionTypeData(
                fitnessPermissionType = FitnessPermissionType.BLOOD_GLUCOSE
            )

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeFitnessPermissionType,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_30_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeFitnessPermissionType),
                )
        }

        onView(withId(R.id.radio_button_one_month))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete blood glucose data from the last 30 days?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeData_confirmationDialogForAllTime_showsCorrectText() {
        val deletionTypeFitnessPermissionType =
            DeletionType.DeletionTypeHealthPermissionTypeData(
                fitnessPermissionType = FitnessPermissionType.BLOOD_GLUCOSE
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeFitnessPermissionType,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeFitnessPermissionType),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete blood glucose data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteAppData_confirmationDialogForOneDay_showsCorrectText() {
        val deletionTypeAppData =
            DeletionType.DeletionTypeAppData(
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAppData,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_24_HOURS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAppData),
                )
        }

        onView(withId(R.id.radio_button_one_day))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete $TEST_APP_NAME data from the last 24 hours?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteAppData_confirmationDialogForOneWeek_showsCorrectText() {
        val deletionTypeAppData =
            DeletionType.DeletionTypeAppData(
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAppData,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_7_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAppData),
                )
        }

        onView(withId(R.id.radio_button_one_week))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete $TEST_APP_NAME data from the last 7 days?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteAppData_confirmationDialogForOneMonth_showsCorrectText() {
        val deletionTypeAppData =
            DeletionType.DeletionTypeAppData(
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAppData,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_30_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAppData),
                )
        }

        onView(withId(R.id.radio_button_one_month))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete $TEST_APP_NAME data from the last 30 days?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteAppData_confirmationDialogForAllTime_showsCorrectText() {
        val deletionTypeAppData =
            DeletionType.DeletionTypeAppData(
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAppData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAppData),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete $TEST_APP_NAME data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeFromApp_confirmationDialogForOneDay_showsCorrectText() {
        val deletionTypePermissionTypeFromApp =
            DeletionType.DeletionTypeHealthPermissionTypeFromApp(
                fitnessPermissionType = FitnessPermissionType.STEPS,
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypePermissionTypeFromApp,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_24_HOURS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypePermissionTypeFromApp),
                )
        }

        onView(withId(R.id.radio_button_one_day))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(
                withText(
                    "Permanently delete steps data added by $TEST_APP_NAME from the last 24 hours?"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeFromApp_confirmationDialogForOneWeek_showsCorrectText() {
        val deletionTypePermissionTypeFromApp =
            DeletionType.DeletionTypeHealthPermissionTypeFromApp(
                fitnessPermissionType = FitnessPermissionType.STEPS,
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypePermissionTypeFromApp,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_7_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypePermissionTypeFromApp),
                )
        }

        onView(withId(R.id.radio_button_one_week))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(
                withText(
                    "Permanently delete steps data added by $TEST_APP_NAME from the last 7 days?"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeFromApp_confirmationDialogForOneMonth_showsCorrectText() {
        val deletionTypePermissionTypeFromApp =
            DeletionType.DeletionTypeHealthPermissionTypeFromApp(
                fitnessPermissionType = FitnessPermissionType.STEPS,
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypePermissionTypeFromApp,
                    chosenRange = ChosenRange.DELETE_RANGE_LAST_30_DAYS,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypePermissionTypeFromApp),
                )
        }

        onView(withId(R.id.radio_button_one_month))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(
                withText(
                    "Permanently delete steps data added by $TEST_APP_NAME from the last 30 days?"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deletePermissionTypeFromApp_confirmationDialogForAllTime_showsCorrectText() {
        val deletionTypePermissionTypeFromApp =
            DeletionType.DeletionTypeHealthPermissionTypeFromApp(
                fitnessPermissionType = FitnessPermissionType.STEPS,
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypePermissionTypeFromApp,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypePermissionTypeFromApp),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete steps data added by $TEST_APP_NAME from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteInActiveAppData_confirmationDialog_showsCorrectText() {
        val deletionTypeAppData =
            DeletionType.DeletionTypeAppData(
                packageName = TEST_APP_PACKAGE_NAME,
                appName = TEST_APP_NAME,
            )
        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAppData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }
        whenever(viewModel.isInactiveApp).then { true }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_INACTIVE_APP_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAppData),
                )
        }

        onView(withText("Permanently delete $TEST_APP_NAME data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText("Also remove all $TEST_APP_NAME permissions from Health\u00A0Connect"))
            .inRoot(isDialog())
            .check(doesNotExist())
    }

    @Test
    fun confirmationDialog_goBackButton_navigatesToTimeRangeDialog() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        whenever(viewModel.showTimeRangeDialogFragment).then { true }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText("Go back")).inRoot(isDialog()).perform(click())

        onView(withText("Choose data to delete from Health Connect"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "This permanently deletes all data added to Health\u00A0Connect in the chosen" +
                        " time period"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 24 hours"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 7 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete last 30 days"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Delete all data"))
            .inRoot(isDialog())
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Next")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deleteAllData_confirmationDialogForEntry_showsCorrectText() {
        val deletionEntry = DeletionType.DeleteDataEntry("test_id", StepsRecord::class, 0)

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(DeletionParameters(deletionType = deletionEntry))
        }

        whenever(viewModel.showTimeRangeDialogFragment).then { false }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(START_DELETION_EVENT, bundleOf(DELETION_TYPE to deletionEntry))
        }

        onView(withText("Permanently delete this entry from Health Connect?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText("Cancel")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Delete")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deleteAllData_confirmationDialog_cancelButton_exitsFlow() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        whenever(viewModel.showTimeRangeDialogFragment).then { false }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText("Cancel")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from all time?")).check(doesNotExist())
    }

    @Test
    fun deleteFragment_progressIndicatorStartedState_progressIndicatorShown() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionState = DeletionState.STATE_PROGRESS_INDICATOR_STARTED,
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText("Delete")).inRoot(isDialog()).perform(click())

        onView(withText("Deleting your data")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deleteFragment_progressIndicatorCanEndState_progressIndicatorDisappears() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionState = DeletionState.STATE_PROGRESS_INDICATOR_CAN_END,
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText("Delete")).inRoot(isDialog()).perform(click())

        onView(withText("Deleting your data")).check(doesNotExist())
    }

    @Test
    fun deleteFragment_deletionSuccessfulState_successMessageShown() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionState = DeletionState.STATE_DELETION_SUCCESSFUL,
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText("Delete")).inRoot(isDialog()).perform(click())

        onView(withText("Deleting your data")).inRoot(isDialog()).check(doesNotExist())
        onView(withText("Data deleted from Health Connect"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "If you want to completely delete the data from your connected apps, check each app where your data may be saved."
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("See connected apps")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Done")).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    @Test
    fun deleteFragment_deletionFailedState_failureMessageShown() {
        val deletionTypeAllData = DeletionType.DeletionTypeAllData()

        whenever(viewModel.deletionParameters).then {
            MutableLiveData(
                DeletionParameters(
                    deletionState = DeletionState.STATE_DELETION_FAILED,
                    deletionType = deletionTypeAllData,
                    chosenRange = ChosenRange.DELETE_RANGE_ALL_DATA,
                )
            )
        }

        launchFragment<DeletionFragment>(Bundle()) {
            (this as DeletionFragment)
                .parentFragmentManager
                .setFragmentResult(
                    START_DELETION_EVENT,
                    bundleOf(DELETION_TYPE to deletionTypeAllData),
                )
        }

        onView(withId(R.id.radio_button_all))
            .inRoot(isDialog())
            .perform(scrollTo())
            .perform(click())

        onView(withText("Next")).inRoot(isDialog()).perform(click())

        onView(withText("Permanently delete all data from all time?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "Connected apps will no longer be able to access this data from Health\u00A0Connect"
                )
            )
            .inRoot(isDialog())
            .check(matches(isDisplayed()))

        onView(withText("Delete")).inRoot(isDialog()).perform(click())

        onView(withText("Deleting your data")).inRoot(isDialog()).check(doesNotExist())
        onView(withText("Couldn't delete data")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Something went wrong and Health\u00A0Connect couldn't delete your data"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }
}
