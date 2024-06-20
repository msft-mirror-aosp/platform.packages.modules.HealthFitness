/*
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
package com.android.healthconnect.controller.tests.data.alldata

import android.content.pm.ActivityInfo
import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.HealthPermissionCategory
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.MenstruationPeriodRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.StepsRecord
import android.os.OutcomeReceiver
import androidx.preference.PreferenceCategory
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.data.alldata.AllDataFragment
import com.android.healthconnect.controller.data.alldata.AllDataViewModel
import com.android.healthconnect.controller.data.appdata.AppDataUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.selectabledeletion.DeletionPermissionTypesPreference
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.getDataOrigin
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class AllDataFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    var manager: HealthConnectManager = Mockito.mock(HealthConnectManager::class.java)

    private val appDataUseCase: AppDataUseCase = AppDataUseCase(manager, Dispatchers.Main)

    @BindValue val allDataViewModel: AllDataViewModel = AllDataViewModel(appDataUseCase)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun allDataFragment_noData_noDataMessageDisplayed() {
        Mockito.doAnswer(prepareAnswer(mapOf()))
            .`when`(manager)
            .queryAllRecordTypesInfo(any(), any())

        val scenario = launchFragment<AllDataFragment>()
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("No data")).check(matches(isDisplayed()))
        onView(withText("Data from apps with access to Health Connect will show here"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun allDataFragment_dataPresent_populatedDataTypesDisplayed() {
        val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
            mapOf(
                DistanceRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.DISTANCE,
                        HealthDataCategory.ACTIVITY,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                StepsRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.STEPS,
                        HealthDataCategory.ACTIVITY,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                ExerciseSessionRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.EXERCISE,
                        HealthDataCategory.ACTIVITY,
                        listOf((getDataOrigin(TEST_APP_PACKAGE_NAME)))),
                HeartRateRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.HEART_RATE,
                        HealthDataCategory.VITALS,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))))
        doAnswer(prepareAnswer(recordTypeInfoMap))
            .`when`(manager)
            .queryAllRecordTypesInfo(any(), any())

        launchFragment<AllDataFragment>()

        onView(withText("Activity")).check(matches(isDisplayed()))
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Steps")).check(matches(isDisplayed()))
        onView(withText("Vitals")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Heart rate")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Body measurements")).check(doesNotExist())
        onView(withText("No data")).check(doesNotExist())
    }

    @Test
    fun triggerDeletionState_showsCheckboxes() {
        val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
            mapOf(
                StepsRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.DISTANCE,
                        HealthDataCategory.ACTIVITY,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                MenstruationPeriodRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.MENSTRUATION,
                        HealthDataCategory.CYCLE_TRACKING,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))))
        doAnswer(prepareAnswer(recordTypeInfoMap))
            .`when`(manager)
            .queryAllRecordTypesInfo(any(), any())

        val scenario = launchFragment<AllDataFragment>()

        // check checkboxes are not shown
        onView(withId(androidx.preference.R.id.recycler_view))
            .check(
                matches(
                    allOf(
                        hasDescendant(withText("Distance")),
                        not(
                            hasDescendant(
                                withTagValue(`is`("checkbox")))))))
        onView(withId(androidx.preference.R.id.recycler_view))
            .check(
                matches(
                    allOf(
                        hasDescendant(withText("Menstruation")),
                        not(
                            hasDescendant(
                                withTagValue(`is`("checkbox")))))))

        // trigger deletion state
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllDataFragment).triggerDeletionState(true)
        }

        // check checkboxes are shown
        onView(withId(androidx.preference.R.id.recycler_view))
            .check(
                matches(
                    allOf(
                        hasDescendant(withText("Distance")),
                        hasDescendant(
                            withTagValue(`is`("checkbox"))))))
        onView(withId(androidx.preference.R.id.recycler_view))
            .check(
                matches(
                    allOf(
                        hasDescendant(withText("Menstruation")),
                        hasDescendant(
                            withTagValue(`is`("checkbox"))))))
    }

    @Test
    fun triggersDeletionState_checkedItemsAddedToDeleteSet() {
        val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
            mapOf(
                StepsRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.DISTANCE,
                        HealthDataCategory.ACTIVITY,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                HeartRateRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.HEART_RATE,
                        HealthDataCategory.VITALS,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))))
        doAnswer(prepareAnswer(recordTypeInfoMap))
            .`when`(manager)
            .queryAllRecordTypesInfo(any(), any())

        val scenario = launchFragment<AllDataFragment>()
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllDataFragment).triggerDeletionState(true)
        }

        onView(withText("Distance")).perform(click())
        onIdle()
        assertThat(allDataViewModel.getDeleteSet())
            .containsExactlyElementsIn(setOf(HealthPermissionType.DISTANCE))

        onView(withText("Distance")).perform(click())
        assertThat(allDataViewModel.getDeleteSet()).isEmpty()
    }

    @Test
    fun triggerDeletionState_checkboxesRemainOnOrientationChange() = runTest {
        val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
            mapOf(
                StepsRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.DISTANCE,
                        HealthDataCategory.ACTIVITY,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))),
                HeartRateRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.HEART_RATE,
                        HealthDataCategory.VITALS,
                        listOf(getDataOrigin(TEST_APP_PACKAGE_NAME))))
        doAnswer(prepareAnswer(recordTypeInfoMap))
            .`when`(manager)
            .queryAllRecordTypesInfo(any(), any())

        val scenario = launchFragment<AllDataFragment>()
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AllDataFragment).triggerDeletionState(true)
        }

        advanceUntilIdle()

        // check if checkboxes show
        onView(withId(androidx.preference.R.id.recycler_view))
            .check(
                matches(
                    allOf(
                        hasDescendant(withText("Distance")),
                        hasDescendant(
                            withTagValue(`is`("checkbox"))))))
        onView(withId(androidx.preference.R.id.recycler_view))
            .check(
                matches(
                    allOf(
                        hasDescendant(withText("Heart rate")),
                        hasDescendant(
                            withTagValue(`is`("checkbox"))))))
        onView(withText("Distance")).perform(click())

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val fragment = activity.supportFragmentManager.findFragmentByTag("") as AllDataFragment
            fragment.preferenceScreen.children.forEach { preference ->
                if (preference is PreferenceCategory) {
                    preference.children.forEach { permissionTypePreference ->
                        if (permissionTypePreference is DeletionPermissionTypesPreference) {
                            if (permissionTypePreference.getHealthPermissionType() ==
                                HealthPermissionType.DISTANCE) {
                                assertThat(permissionTypePreference.getIsChecked()).isTrue()
                            } else if (permissionTypePreference.getHealthPermissionType() ==
                                HealthPermissionType.MENSTRUATION) {
                                assertThat(permissionTypePreference.getIsChecked()).isFalse()
                            }
                        }
                    }
                }
            }
        }

        onView(withId(androidx.preference.R.id.recycler_view))
            .check(
                matches(
                    allOf(
                        hasDescendant(withText("Distance")),
                        hasDescendant(
                            withTagValue(`is`("checkbox"))))))
        onView(withId(androidx.preference.R.id.recycler_view))
            .check(
                matches(
                    allOf(
                        hasDescendant(withText("Heart rate")),
                        hasDescendant(
                            withTagValue(`is`("checkbox"))))))

        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun prepareAnswer(
        recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse>
    ): (InvocationOnMock) -> Map<Class<out Record>, RecordTypeInfoResponse> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[1] as OutcomeReceiver<Any?, *>
            receiver.onResult(recordTypeInfoMap)
            recordTypeInfoMap
        }
        return answer
    }
}
