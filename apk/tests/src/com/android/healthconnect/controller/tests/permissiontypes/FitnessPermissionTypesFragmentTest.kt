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
package com.android.healthconnect.controller.tests.permissiontypes

import android.content.Context
import android.health.connect.HealthDataCategory
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.categories.HealthDataCategoriesFragment
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesViewModel
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesViewModel.AppsWithDataFragmentState
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesViewModel.PermissionTypesState
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_3
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.utils.logging.DeletionDialogTimeRangeElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionTypesElement
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
import org.mockito.kotlin.whenever

@HiltAndroidTest
class FitnessPermissionTypesFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var context: Context
    private lateinit var navHostController: TestNavHostController

    @BindValue
    val viewModel: HealthPermissionTypesViewModel =
        Mockito.mock(HealthPermissionTypesViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    fun dataType_navigatesToDataAccess() {
        setupFragmentForNavigationTesting()
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Distance")).perform(click())
        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.healthDataAccessFragment)
    }

    @Test
    fun dataSourcesAndPriorityButton_navigatesToDataSources() {
        setupFragmentForNavigationTesting()
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.dataSourcesFragment)
    }

    @Test
    fun deletePermissionTypeData_showsDialog() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(
                    listOf(
                        FitnessPermissionType.DISTANCE,
                        FitnessPermissionType.EXERCISE,
                        FitnessPermissionType.STEPS,
                    )
                )
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Delete activity data")).check(matches(isDisplayed()))
        onView(withText("Delete activity data")).perform(click())
        onView(withText("Choose data to delete from Health Connect"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(
                DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_LAST_7_DAYS_BUTTON
            )
        verify(healthConnectLogger)
            .logImpression(
                DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_LAST_24_HOURS_BUTTON
            )
        verify(healthConnectLogger)
            .logImpression(
                DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_LAST_30_DAYS_BUTTON
            )
        verify(healthConnectLogger)
            .logImpression(DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_NEXT_BUTTON)
        verify(healthConnectLogger)
            .logImpression(DeletionDialogTimeRangeElement.DELETION_DIALOG_TIME_RANGE_CANCEL_BUTTON)
    }

    @Test
    fun permissionTypesFragment_activityCategory_isDisplayed() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(
                    listOf(
                        FitnessPermissionType.DISTANCE,
                        FitnessPermissionType.EXERCISE,
                        FitnessPermissionType.STEPS,
                    )
                )
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Active calories burned")).check(doesNotExist())
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Elevation gained")).check(doesNotExist())
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Floors climbed")).check(doesNotExist())
        onView(withText("Power")).check(doesNotExist())
        onView(withText("Steps")).check(matches(isDisplayed()))
        onView(withText("Total calories burned")).check(doesNotExist())
        onView(withText("VO2 max")).check(doesNotExist())
        onView(withText("Wheelchair pushes")).check(doesNotExist())
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("Delete activity data")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.PERMISSION_TYPES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, times(3))
            .logImpression(PermissionTypesElement.PERMISSION_TYPE_BUTTON)
        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.DATA_SOURCES_AND_PRIORITY_BUTTON)
        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.DELETE_CATEGORY_DATA_BUTTON)
    }

    @Test
    fun permissionTypesFragment_activityCategory_trainingPlansAvailable_isDisplayed() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(listOf(FitnessPermissionType.PLANNED_EXERCISE))
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Training plans")).check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.PERMISSION_TYPES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, times(1))
            .logImpression(PermissionTypesElement.PERMISSION_TYPE_BUTTON)
        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.DATA_SOURCES_AND_PRIORITY_BUTTON)
        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.DELETE_CATEGORY_DATA_BUTTON)
    }

    @Test
    fun permissionTypesFragment_sleepCategory_isDisplayed() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(listOf(FitnessPermissionType.SLEEP))
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(sleepCategoryBundle())

        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("Delete sleep data")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
    }

    @Test
    fun permissionTypesFragment_withTwoOrMoreContributingApps_appFilters_areDisplayed() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(
                    listOf(
                        FitnessPermissionType.DISTANCE,
                        FitnessPermissionType.EXERCISE,
                        FitnessPermissionType.STEPS,
                    )
                )
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(
                AppsWithDataFragmentState.WithData(listOf(TEST_APP, TEST_APP_2))
            )
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("All apps") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("All apps")).check(matches(isDisplayed()))
        onView(withText(TEST_APP.appName)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_2.appName)).check(matches(isDisplayed()))
        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.PERMISSION_TYPES_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, atLeast(2))
            .logImpression(PermissionTypesElement.APP_FILTER_BUTTON)
    }

    @Test
    fun permissionTypesFragment_withLessThanTwoContributingApps_appFilters_areNotDisplayed() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(
                    listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.EXERCISE)
                )
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(
                AppsWithDataFragmentState.WithData(listOf(TEST_APP_3))
            )
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("All apps") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("All apps")).check(doesNotExist())
        onView(withText(TEST_APP_3.appName)).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_appFilters_areSelectableCorrectly() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(
                    listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.EXERCISE)
                )
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(
                AppsWithDataFragmentState.WithData(listOf(TEST_APP, TEST_APP_3))
            )
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("All apps") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText(TEST_APP_3.appName)).perform(scrollTo(), click())
        assert(viewModel.selectedAppFilter.value == TEST_APP_3.appName)
        verify(healthConnectLogger).logInteraction(PermissionTypesElement.APP_FILTER_BUTTON)
    }

    @Test
    fun permissionTypesFragment_activityCategory_showsNewAppPriorityButton() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(
                    listOf(
                        FitnessPermissionType.DISTANCE,
                        FitnessPermissionType.EXERCISE,
                        FitnessPermissionType.STEPS,
                    )
                )
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle())

        onView(withText("Active calories burned")).check(doesNotExist())
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Elevation gained")).check(doesNotExist())
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Floors climbed")).check(doesNotExist())
        onView(withText("Power")).check(doesNotExist())
        onView(withText("Steps")).check(matches(isDisplayed()))
        onView(withText("Total calories burned")).check(doesNotExist())
        onView(withText("VO2 max")).check(doesNotExist())
        onView(withText("Wheelchair pushes")).check(doesNotExist())
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
        onView(withText("Delete activity data")).check(matches(isDisplayed()))

        verify(healthConnectLogger)
            .logImpression(PermissionTypesElement.DATA_SOURCES_AND_PRIORITY_BUTTON)
    }

    @Test
    fun permissionTypesFragment_sleepCategory_showsNewAppPriorityButton() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(listOf(FitnessPermissionType.SLEEP))
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(sleepCategoryBundle())

        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
        onView(withText("Delete sleep data")).check(matches(isDisplayed()))
    }

    @Test
    fun permissionTypesFragment_whenBodyMeasurementsCategory_doesNotShowPriorityButton() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(
                    listOf(
                        FitnessPermissionType.BASAL_METABOLIC_RATE,
                        FitnessPermissionType.BODY_FAT,
                        FitnessPermissionType.HEIGHT,
                    )
                )
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(bodyMeasurementsCategoryBundle())

        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenCycleTrackingCategory_doesNotShowPriorityButton() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(listOf(FitnessPermissionType.MENSTRUATION))
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(cycleCategoryBundle())

        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenNutritionCategory_doesNotShowPriorityButton() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(listOf(FitnessPermissionType.NUTRITION))
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(nutritionCategoryBundle())

        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    @Test
    fun permissionTypesFragment_whenVitalsCategory_doesNotShowPriorityButton() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(listOf(FitnessPermissionType.HEART_RATE))
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(vitalsCategoryBundle())

        onView(withText("Data sources and priority")).check(doesNotExist())
    }

    private fun activityCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.ACTIVITY)
        return bundle
    }

    private fun bodyMeasurementsCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(
            HealthDataCategoriesFragment.CATEGORY_KEY,
            HealthDataCategory.BODY_MEASUREMENTS,
        )
        return bundle
    }

    private fun cycleCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.CYCLE_TRACKING)
        return bundle
    }

    private fun nutritionCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.NUTRITION)
        return bundle
    }

    private fun sleepCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.SLEEP)
        return bundle
    }

    private fun vitalsCategoryBundle(): Bundle {
        val bundle = Bundle()
        bundle.putInt(HealthDataCategoriesFragment.CATEGORY_KEY, HealthDataCategory.VITALS)
        return bundle
    }

    private fun setupFragmentForNavigationTesting() {
        whenever(viewModel.permissionTypesData).then {
            MutableLiveData<PermissionTypesState>(
                PermissionTypesState.WithData(
                    listOf(
                        FitnessPermissionType.DISTANCE,
                        FitnessPermissionType.EXERCISE,
                        FitnessPermissionType.STEPS,
                    )
                )
            )
        }
        whenever(viewModel.appsWithData).then {
            MutableLiveData<AppsWithDataFragmentState>(AppsWithDataFragmentState.WithData(listOf()))
        }
        whenever(viewModel.selectedAppFilter).then { MutableLiveData("") }
        launchFragment<HealthPermissionTypesFragment>(activityCategoryBundle()) {
            navHostController.setGraph(R.navigation.data_nav_graph)
            navHostController.setCurrentDestination(R.id.healthPermissionTypesFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
    }
}
