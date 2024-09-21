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
package com.android.healthconnect.controller.tests.data.appdata

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.health.connect.HealthDataCategory
import androidx.core.os.bundleOf
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.appdata.AppDataFragment
import com.android.healthconnect.controller.data.appdata.AppDataViewModel
import com.android.healthconnect.controller.data.appdata.PermissionTypesPerCategory
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.selectabledeletion.SelectAllCheckboxPreference
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.MEDICAL
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.verify
import com.android.healthfitness.flags.Flags
import android.platform.test.annotations.DisableFlags
import org.junit.Ignore
import com.android.healthconnect.controller.data.appdata.AppDataViewModel.AppDataDeletionScreenState.VIEW
import com.android.healthconnect.controller.data.appdata.AppDataViewModel.AppDataDeletionScreenState.DELETE

@HiltAndroidTest
class AppDataFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @BindValue val appDataViewModel: AppDataViewModel = Mockito.mock(AppDataViewModel::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
        toggleAnimation(false)
        val context = InstrumentationRegistry.getInstrumentation().context
        whenever(appDataViewModel.appInfo).then {
            MediatorLiveData(
                AppMetadata(
                    TEST_APP_PACKAGE_NAME,
                    TEST_APP_NAME,
                    context.getDrawable(R.drawable.health_connect_logo),
                )
            )
        }
        whenever(appDataViewModel.setOfPermissionTypesToBeDeleted).thenReturn(MutableLiveData())
        whenever(appDataViewModel.allPermissionTypesSelected).thenReturn(MutableLiveData(false))
    }

    @After
    fun tearDown(){
        toggleAnimation(true)
    }

    @Test
    fun noData_noDataMessageDisplayed() {
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(AppDataViewModel.AppDataState.WithData(listOf()))
        }
        launchFragment<AppDataFragment>(
            bundleOf(
                Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                Constants.EXTRA_APP_NAME to TEST_APP_NAME,
            )
        )

        onView(withText("No data")).check(matches(isDisplayed()))
        onView(withText("Data from apps with access to Health Connect will show here"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun fitnessDataPresent_populatedDataTypesDisplayed() {
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                AppDataViewModel.AppDataState.WithData(
                    listOf(
                        PermissionTypesPerCategory(
                            HealthDataCategory.ACTIVITY,
                            listOf(
                                FitnessPermissionType.DISTANCE,
                                FitnessPermissionType.EXERCISE,
                                FitnessPermissionType.EXERCISE_ROUTE,
                                FitnessPermissionType.STEPS,
                            ),
                        ),
                        PermissionTypesPerCategory(HealthDataCategory.BODY_MEASUREMENTS, listOf()),
                        PermissionTypesPerCategory(
                            HealthDataCategory.CYCLE_TRACKING,
                            listOf(
                                FitnessPermissionType.MENSTRUATION,
                                FitnessPermissionType.SEXUAL_ACTIVITY,
                            ),
                        ),
                        PermissionTypesPerCategory(HealthDataCategory.NUTRITION, listOf()),
                        PermissionTypesPerCategory(HealthDataCategory.SLEEP, listOf()),
                        PermissionTypesPerCategory(HealthDataCategory.VITALS, listOf()),
                        PermissionTypesPerCategory(MEDICAL, listOf()),
                    )
                )
            )
        }
        launchFragment<AppDataFragment>(
            bundleOf(
                Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                Constants.EXTRA_APP_NAME to TEST_APP_NAME,
            )
        )

        onView(withText("Activity")).check(matches(isDisplayed()))
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Steps")).check(matches(isDisplayed()))

        onView(withText("Cycle tracking")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Menstruation")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Sexual activity")).perform(scrollTo()).check(matches(isDisplayed()))

        onView(withText("Body measurements")).check(doesNotExist())
        onView(withText("Nutrition")).check(doesNotExist())
        onView(withText("Sleep")).check(doesNotExist())
        onView(withText("Vitals")).check(doesNotExist())
        onView(withText("Health records")).check(doesNotExist())
        onView(withText("Immunizations")).check(doesNotExist())
    }
    @Test
    fun fitnessAndMedicalData_fitnessAndMedicalDataShown() {
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                AppDataViewModel.AppDataState.WithData(
                    listOf(
                        PermissionTypesPerCategory(
                            HealthDataCategory.ACTIVITY,
                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.EXERCISE),
                        ),
                        PermissionTypesPerCategory(
                            MEDICAL,
                            listOf(MedicalPermissionType.IMMUNIZATION),
                        ),
                    )
                )
            )
        }
        launchFragment<AppDataFragment>(
            bundleOf(
                Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                Constants.EXTRA_APP_NAME to TEST_APP_NAME,
            )
        )

        onView(withText("Activity")).check(matches(isDisplayed()))
        onView(withText("Distance")).check(matches(isDisplayed()))
        onView(withText("Exercise")).check(matches(isDisplayed()))

        onView(withText("Health records")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Immunization")).perform(scrollTo()).check(matches(isDisplayed()))

        onView(withText("Steps")).check(doesNotExist())
        onView(withText("Body measurements")).check(doesNotExist())
        onView(withText("Cycle tracking")).check(doesNotExist())
    }

    @Test
    fun medicalDataOnly_populatedDataTypesDisplayed() {
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                AppDataViewModel.AppDataState.WithData(
                    listOf(
                        PermissionTypesPerCategory(
                            MEDICAL,
                            listOf(MedicalPermissionType.IMMUNIZATION),
                        )
                    )
                )
            )
        }
        launchFragment<AppDataFragment>(
            bundleOf(
                Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                Constants.EXTRA_APP_NAME to TEST_APP_NAME,
            )
        )

        onView(withText("Activity")).check(doesNotExist())
        onView(withText("Distance")).check(doesNotExist())

        onView(withText("Health records")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Immunization")).perform(scrollTo()).check(matches(isDisplayed()))

        onView(withText("Body measurements")).check(doesNotExist())
    }

    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun triggerDeletionState_showsCheckboxes(){
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                    AppDataViewModel.AppDataState.WithData(
                            listOf(
                                    PermissionTypesPerCategory(
                                            HealthDataCategory.ACTIVITY,
                                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.STEPS)
                                            )
                            )
                    )
            )
        }

        val scenario = launchFragment<AppDataFragment>(
                bundleOf(
                        Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                        Constants.EXTRA_APP_NAME to TEST_APP_NAME))

        assertCheckboxNotShown("Distance")
        assertCheckboxNotShown("Steps")

        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AppDataFragment).triggerDeletionState(DELETE)
        }

        assertCheckboxShown("Distance")
        assertCheckboxShown("Steps")
    }

    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun triggerDeletionState_checkedItemsAddedToDeleteSet(){
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                    AppDataViewModel.AppDataState.WithData(
                            listOf(
                                    PermissionTypesPerCategory(
                                            HealthDataCategory.ACTIVITY,
                                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.STEPS)
                                    )
                            )
                    )
            )
        }

        val scenario = launchFragment<AppDataFragment>(
                bundleOf(
                        Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                        Constants.EXTRA_APP_NAME to TEST_APP_NAME))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AppDataFragment).triggerDeletionState(DELETE)
        }

        onView(withText("Distance")).perform(click())
        onIdle()
        verify(appDataViewModel).addToDeletionSet(FitnessPermissionType.DISTANCE)
    }

    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun triggerDeletionState_uncheckedItemsRemovedFromDeleteSet(){
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                    AppDataViewModel.AppDataState.WithData(
                            listOf(
                                    PermissionTypesPerCategory(
                                            HealthDataCategory.ACTIVITY,
                                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.STEPS)
                                    )
                            )
                    )
            )
        }
        whenever(appDataViewModel.setOfPermissionTypesToBeDeleted).then {
            MediatorLiveData(setOf(FitnessPermissionType.DISTANCE))
        }

        val scenario = launchFragment<AppDataFragment>(
                bundleOf(
                        Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                        Constants.EXTRA_APP_NAME to TEST_APP_NAME))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AppDataFragment).triggerDeletionState(DELETE)
        }

        onView(withText("Distance")).perform(click())
        onIdle()
        verify(appDataViewModel).removeFromDeletionSet(FitnessPermissionType.DISTANCE)
    }

    @Ignore("b/363994647")
    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun triggerDeletionState_checkboxesRemainOnOrientationChange(){
        whenever(appDataViewModel.getDeletionState()).thenReturn(DELETE)
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                    AppDataViewModel.AppDataState.WithData(
                            listOf(
                                    PermissionTypesPerCategory(
                                            HealthDataCategory.ACTIVITY,
                                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.STEPS)
                                    )
                            )
                    )
            )
        }

        val scenario = launchFragment<AppDataFragment>(
                bundleOf(
                        Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                        Constants.EXTRA_APP_NAME to TEST_APP_NAME))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AppDataFragment).triggerDeletionState(DELETE)
        }

        assertCheckboxShown("Distance")
        assertCheckboxShown("Steps")

        scenario.onActivity{ activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        assertCheckboxShown("Distance")
        assertCheckboxShown("Steps")

        scenario.onActivity{ activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun triggerDeletionState_displaysSelectAllButton(){
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                    AppDataViewModel.AppDataState.WithData(
                            listOf(
                                    PermissionTypesPerCategory(
                                            HealthDataCategory.ACTIVITY,
                                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.STEPS)
                                    )
                            )
                    )
            )
        }

        val scenario = launchFragment<AppDataFragment>(
                bundleOf(
                        Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                        Constants.EXTRA_APP_NAME to TEST_APP_NAME))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("")
            (fragment as AppDataFragment).triggerDeletionState(DELETE)
        }

        assertCheckboxShown("Select all")
    }

    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun triggerDeletionState_onSelectAllChecked_allPermissionTypesChecked() {
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                    AppDataViewModel.AppDataState.WithData(
                            listOf(
                                    PermissionTypesPerCategory(
                                            HealthDataCategory.ACTIVITY,
                                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.STEPS)
                                    )
                            )
                    )
            )
        }
        whenever(appDataViewModel.getDeletionState()).thenReturn(DELETE)

        val scenario = launchFragment<AppDataFragment>(
                bundleOf(
                        Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                        Constants.EXTRA_APP_NAME to TEST_APP_NAME))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("") as AppDataFragment
            fragment.triggerDeletionState(DELETE)
            val selectAllPreference =
                    fragment.preferenceScreen.findPreference("key_select_all")
                            as SelectAllCheckboxPreference?
            selectAllPreference?.setIsChecked(false)
            selectAllPreference?.performClick()

            verify(appDataViewModel).addToDeletionSet(FitnessPermissionType.DISTANCE)
            verify(appDataViewModel).addToDeletionSet(FitnessPermissionType.STEPS)
        }
    }

    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun triggerDeletionState_onSelectAllUnchecked_allPermissionTypesUnchecked() {
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                    AppDataViewModel.AppDataState.WithData(
                            listOf(
                                    PermissionTypesPerCategory(
                                            HealthDataCategory.ACTIVITY,
                                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.STEPS)
                                    )
                            )
                    )
            )
        }
        whenever(appDataViewModel.getDeletionState()).thenReturn(DELETE)
        whenever(appDataViewModel.allPermissionTypesSelected).thenReturn(MutableLiveData(true))

        val scenario = launchFragment<AppDataFragment>(
                bundleOf(
                        Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                        Constants.EXTRA_APP_NAME to TEST_APP_NAME))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("") as AppDataFragment
            fragment.triggerDeletionState(DELETE)
            val selectAllPreference =
                    fragment.preferenceScreen.findPreference("key_select_all")
                            as SelectAllCheckboxPreference?
            selectAllPreference?.setIsChecked(true)
            selectAllPreference?.performClick()

            verify(appDataViewModel).removeFromDeletionSet(FitnessPermissionType.DISTANCE)
            verify(appDataViewModel).removeFromDeletionSet(FitnessPermissionType.STEPS)
        }
    }

    @Ignore("b/363994647")
    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun triggerDeletionState_selectAllChecked_checkboxesRemainOnOrientationChange() {
        whenever(appDataViewModel.getDeletionState()).thenReturn(DELETE)
        whenever(appDataViewModel.allPermissionTypesSelected).thenReturn(MutableLiveData(true))
        whenever(appDataViewModel.fitnessAndMedicalData).then {
            MediatorLiveData(
                    AppDataViewModel.AppDataState.WithData(
                            listOf(
                                    PermissionTypesPerCategory(
                                            HealthDataCategory.ACTIVITY,
                                            listOf(FitnessPermissionType.DISTANCE, FitnessPermissionType.STEPS)
                                    )
                            )
                    )
            )
        }

        val scenario = launchFragment<AppDataFragment>(
                bundleOf(
                        Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                        Constants.EXTRA_APP_NAME to TEST_APP_NAME))
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag("") as AppDataFragment
            fragment.triggerDeletionState(DELETE)
            val selectAllPreference =
                    fragment.preferenceScreen.findPreference("key_select_all")
                            as SelectAllCheckboxPreference?

            assertThat(selectAllPreference?.getIsChecked()).isTrue()
        }

        assertCheckboxShown("Select all")

        scenario.onActivity{ activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            val fragment = activity.supportFragmentManager.findFragmentByTag("") as AppDataFragment
            val selectAllPreference =
                    fragment.preferenceScreen.findPreference("key_select_all")
                            as SelectAllCheckboxPreference?

            assertThat(selectAllPreference?.getIsChecked()).isTrue()
        }

        assertCheckboxShown("Select all")

        scenario.onActivity{ activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun assertCheckboxShown(title: String, tag: String = "checkbox") {
        onView(ViewMatchers.withId(androidx.preference.R.id.recycler_view))
                .check(
                        matches(
                                Matchers.allOf(ViewMatchers.hasDescendant(withText(title)), ViewMatchers.hasDescendant(ViewMatchers.withTagValue(Matchers.`is`(tag))))))
    }

    private fun assertCheckboxNotShown(title: String, tag: String = "checkbox") {
        onView(ViewMatchers.withId(androidx.preference.R.id.recycler_view))
                .check(
                        matches(
                                Matchers.allOf(
                                        ViewMatchers.hasDescendant(withText(title)),
                                        Matchers.not(ViewMatchers.hasDescendant(ViewMatchers.withTagValue(Matchers.`is`(tag)))))))
    }
}
