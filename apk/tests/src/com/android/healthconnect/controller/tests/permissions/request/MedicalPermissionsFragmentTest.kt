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
package com.android.healthconnect.controller.tests.permissions.request

import android.health.connect.HealthPermissions.READ_MEDICAL_RESOURCES_IMMUNIZATION
import android.health.connect.HealthPermissions.READ_STEPS
import android.health.connect.HealthPermissions.WRITE_DISTANCE
import android.health.connect.HealthPermissions.WRITE_MEDICAL_RESOURCES
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry.*
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.MedicalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.MedicalPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.permissions.request.MedicalPermissionsFragment
import com.android.healthconnect.controller.permissions.request.PermissionsFragment
import com.android.healthconnect.controller.permissions.request.RequestPermissionViewModel
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.TestActivity
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.any
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import com.android.healthconnect.controller.utils.logging.UIAction
import com.android.settingslib.widget.MainSwitchPreference
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers.eq
import org.mockito.Mockito.*

@ExperimentalCoroutinesApi
@HiltAndroidTest
class MedicalPermissionsFragmentTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @BindValue
    val viewModel: RequestPermissionViewModel = mock(RequestPermissionViewModel::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock(HealthConnectLogger::class.java)

    val emptyFitnessPermissionList: List<HealthPermission> = listOf()

    @Before
    fun setup() {
        hiltRule.inject()
        val context = getInstrumentation().context
        context.setLocale(Locale.US)
        `when`(viewModel.appMetadata).then {
            MutableLiveData(
                AppMetadata(
                    TEST_APP_PACKAGE_NAME,
                    TEST_APP_NAME,
                    context.getDrawable(R.drawable.health_connect_logo)))
        }
        `when`(viewModel.allMedicalPermissionsGranted).then { MutableLiveData(false) }
        `when`(viewModel.grantedMedicalPermissions).then {
            MutableLiveData(emptySet<FitnessPermission>())
        }
        toggleAnimation(false)
    }

    @After
    fun teardown() {
        reset(healthConnectLogger)
        toggleAnimation(true)
    }

    @Test
    fun displaysMedicalCategories() {
        `when`(viewModel.healthPermissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION),
                    fromPermissionString(WRITE_MEDICAL_RESOURCES),
                )
            MutableLiveData(permissions)
        }
        launchFragment<MedicalPermissionsFragment>(bundleOf())

        onView(withText("Allow $TEST_APP_NAME to access Health Connect?"))
            .check(matches(isDisplayed()))
        onView(withText("Choose data you want this app to read or write to Health Connect"))
            .check(matches(isDisplayed()))
        onView(withText("If you give read access, the app can read new and past data"))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "You can learn how $TEST_APP_NAME handles your data in the developer's privacy policy"))
            .check(matches(isDisplayed()))

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Allow \u201C$TEST_APP_NAME\u201D to read"))))
        Espresso.onIdle()
        onView(withText("Allow \u201C$TEST_APP_NAME\u201D to read")).check(matches(isDisplayed()))

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Allow \u201C$TEST_APP_NAME\u201D to write"))))
        Espresso.onIdle()
        onView(withText("Allow \u201C$TEST_APP_NAME\u201D to write")).check(matches(isDisplayed()))

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.UNKNOWN_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger, times(3)).logImpression(ErrorPageElement.UNKNOWN_ELEMENT)
    }

    @Test
    fun whenHistoryReadPermissionAlreadyGranted_displaysCorrectText() {
        `when`(viewModel.healthPermissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION),
                    fromPermissionString(WRITE_MEDICAL_RESOURCES),
                )
            MutableLiveData(permissions)
        }
        `when`(viewModel.isHistoryAccessGranted()).thenReturn(true)
        launchFragment<MedicalPermissionsFragment>(bundleOf())

        onView(withText("Allow $TEST_APP_NAME to access Health Connect?"))
            .check(matches(isDisplayed()))
        onView(withText("Choose data you want this app to read or write to Health Connect"))
            .check(matches(isDisplayed()))
        onView(withText("If you give read access, the app can read new and past data"))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "You can learn how $TEST_APP_NAME handles your data in the developer's privacy policy"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun whenHistoryReadPermissionRevoked_displaysCorrectText() {
        `when`(viewModel.healthPermissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION),
                    fromPermissionString(WRITE_MEDICAL_RESOURCES),
                )
            MutableLiveData(permissions)
        }
        launchFragment<MedicalPermissionsFragment>(bundleOf())

        onView(withText("Allow $TEST_APP_NAME to access Health Connect?"))
            .check(matches(isDisplayed()))
        onView(withText("Choose data you want this app to read or write to Health Connect"))
            .check(matches(isDisplayed()))
        onView(withText("If you give read access, the app can read new and past data"))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "You can learn how $TEST_APP_NAME handles your data in the developer's privacy policy"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun displaysOnlyReadPermissions() {
        `when`(viewModel.healthPermissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION),
                )
            MutableLiveData(permissions)
        }
        launchFragment<MedicalPermissionsFragment>(bundleOf())

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Immunization"))))
        Espresso.onIdle()
        onView(withText("Immunization")).check(matches(isDisplayed()))

        onView(withText("All medical data")).check(doesNotExist())
    }

    @Test
    fun displaysOnlyWritePermissions() {
        `when`(viewModel.healthPermissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(WRITE_MEDICAL_RESOURCES),
                )
            MutableLiveData(permissions)
        }
        launchFragment<MedicalPermissionsFragment>(bundleOf())

        onView(withText("Immunization")).check(doesNotExist())

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("All medical data"))))
        Espresso.onIdle()
        onView(withText("All medical data")).check(matches(isDisplayed()))
    }

    @Test
    fun togglesPermissions_callsUpdatePermissions() {
        `when`(viewModel.healthPermissionsList).then {
            val permissions =
                listOf(
                    fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION),
                    fromPermissionString(WRITE_MEDICAL_RESOURCES),
                )
            MutableLiveData(permissions)
        }
        launchFragment<MedicalPermissionsFragment>(bundleOf())
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(
                    hasDescendant(withText("Immunization"))))
        Espresso.onIdle()
        onView(withText("Immunization")).perform(click())

        verify(viewModel).updateHealthPermission(any(MedicalPermission::class.java), eq(true))
        verify(healthConnectLogger)
            .logInteraction(PermissionsElement.PERMISSION_SWITCH, UIAction.ACTION_TOGGLE_ON)
    }

    @Test
    fun allowAllToggleOn_updatesAllPermissions() {
        val permissions =
            listOf(
                FitnessPermission.fromPermissionString(READ_STEPS),
                FitnessPermission.fromPermissionString(WRITE_DISTANCE),
                fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION),
                fromPermissionString(WRITE_MEDICAL_RESOURCES),
            )
        `when`(viewModel.healthPermissionsList).then { MutableLiveData(permissions) }

        val activityScenario = launchFragment<MedicalPermissionsFragment>(bundleOf())

        activityScenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as PermissionsFragment
            val allowAllPreference =
                fragment.preferenceScreen.findPreference("allow_all_preference")
                    as MainSwitchPreference?
            allowAllPreference?.isChecked =
                false // makes sure the preference is on so OnPreferenceChecked is triggered

            allowAllPreference?.isChecked = true

            verify(viewModel).updateMedicalPermissions(eq(true))
            // TODO (b/325680041) this is not triggered?
            //
            // verify(healthConnectLogger).logInteraction(PermissionsElement.ALLOW_ALL_SWITCH,
            // UIAction.ACTION_TOGGLE_ON)
        }
    }

    @Test
    fun allowAllToggleOff_updatesAllPermissions() {
        val permissions =
            listOf(
                fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION),
                fromPermissionString(WRITE_MEDICAL_RESOURCES),
            )
        `when`(viewModel.healthPermissionsList).then { MutableLiveData(permissions) }
        val activityScenario = launchFragment<MedicalPermissionsFragment>(bundleOf())

        activityScenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                    as PermissionsFragment
            val allowAllPreference =
                fragment.preferenceScreen.findPreference("allow_all_preference")
                    as MainSwitchPreference?
            allowAllPreference?.isChecked =
                true // makes sure the preference is on so OnPreferenceChecked is triggered

            allowAllPreference?.isChecked = false

            assertThat(viewModel.grantedMedicalPermissions.value).isEmpty()
        }
    }

    @Test
    fun allowButton_noMedicalPermissionsSelected_isDisabled() {
        val permissions = listOf(READ_MEDICAL_RESOURCES_IMMUNIZATION, WRITE_MEDICAL_RESOURCES)
        whenever(viewModel.healthPermissionsList).then { MutableLiveData(permissions) }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                HealthPermission.fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION) to
                    PermissionState.GRANTED,
                HealthPermission.fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED)
        }
        whenever(viewModel.grantedMedicalPermissions).then {
            MutableLiveData(emptySet<FitnessPermission>())
        }

        launchFragment<MedicalPermissionsFragment>(bundleOf())
        onView(withText("Allow")).check(matches(ViewMatchers.isNotEnabled()))
    }

    @Test
    fun allowButton_medicalPermissionsSelected_isEnabled() {
        val permissions = listOf(READ_MEDICAL_RESOURCES_IMMUNIZATION, WRITE_MEDICAL_RESOURCES)
        whenever(viewModel.healthPermissionsList).then { MutableLiveData(permissions) }
        whenever(viewModel.grantedMedicalPermissions).then {
            MutableLiveData(
                setOf(HealthPermission.fromPermissionString(READ_MEDICAL_RESOURCES_IMMUNIZATION)))
        }

        launchFragment<MedicalPermissionsFragment>(bundleOf())

        onView(withText("Allow")).check(matches(ViewMatchers.isEnabled()))
    }
}
