/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.permissions.connectedapps

import android.content.Intent
import android.content.Intent.*
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.preference.PreferenceCategory
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.permissions.app.CombinedPermissionsFragment
import com.android.healthconnect.controller.shared.Constants.EXTRA_APP_NAME
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.TestActivity
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.AppAccessElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.mock

@HiltAndroidTest
class CombinedPermissionsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var fakeFeatureUtils: FeatureUtils

    @BindValue val viewModel: AppPermissionViewModel = mock()
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()
    @BindValue val healthPermissionReader: HealthPermissionReader = mock()
    @BindValue val additionalAccessViewModel: AdditionalAccessViewModel = mock()
    private lateinit var navHostController: TestNavHostController

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        navHostController = TestNavHostController(context)
        hiltRule.inject()
        whenever(viewModel.appInfo).then {
            MutableLiveData(
                AppMetadata(
                    TEST_APP_PACKAGE_NAME,
                    TEST_APP_NAME,
                    context.getDrawable(R.drawable.health_connect_logo)))
        }
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(AdditionalAccessViewModel.State())
        }
        // disable animations
        toggleAnimation(false)
        Intents.init()
    }

    @After
    fun teardown() {
        reset(healthConnectLogger)
        // enable animations
        toggleAnimation(true)
        Intents.release()
    }

    @Test
    fun correctNumberOfPreferences_withoutAdditionalAccess() {
        val scenario =
            launchFragment<CombinedPermissionsFragment>(
                bundleOf(
                    EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        scenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                        as CombinedPermissionsFragment
            val managePermissions =
                fragment.preferenceScreen.findPreference("manage_permissions")
                        as PreferenceCategory?
            val manageApp =
                fragment.preferenceScreen.findPreference("manage_app")
                        as PreferenceCategory?
            assertThat(managePermissions?.preferenceCount).isEqualTo(2)
            assertThat(manageApp?.preferenceCount).isEqualTo(1)
        }
    }

    @Test
    fun correctNumberOfPreferences_withAdditionalAccess() {
        val validState =
            AdditionalAccessViewModel.State(
                exerciseRoutePermissionUIState = PermissionUiState.ASK_EVERY_TIME,
                exercisePermissionUIState = PermissionUiState.ASK_EVERY_TIME)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(validState)
        }
        val scenario =
            launchFragment<CombinedPermissionsFragment>(
                bundleOf(
                    EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        scenario.onActivity { activity: TestActivity ->
            val fragment =
                activity.supportFragmentManager.findFragmentById(android.R.id.content)
                        as CombinedPermissionsFragment
            val managePermissions =
                fragment.preferenceScreen.findPreference("manage_permissions")
                        as PreferenceCategory?
            val manageApp =
                fragment.preferenceScreen.findPreference("manage_app")
                        as PreferenceCategory?
            assertThat(managePermissions?.preferenceCount).isEqualTo(3)
            assertThat(manageApp?.preferenceCount).isEqualTo(1)
        }
    }

    @Test
    fun correctStringsDisplayed_withoutAdditionalAccess() {
        launchFragment<CombinedPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onView(withText("Health Connect test app")).check(matches(isDisplayed()))
        onView(withText("Permissions")).check(matches(isDisplayed()))
        onView(withText("Manage fitness permissions")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Manage health record permissions")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Additional access")).check(doesNotExist())
        onView(withText("Manage app")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("See app data")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    fun footer_isDisplayed() {
        whenever(healthPermissionReader.isRationaleIntentDeclared(TEST_APP_PACKAGE_NAME))
            .thenReturn(true)
        whenever(healthPermissionReader.getApplicationRationaleIntent(TEST_APP_PACKAGE_NAME))
            .thenReturn(Intent())

        launchFragment<CombinedPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onIdle()
        onView(
            withText(
                "To manage other Android permissions this app can " +
                        "access, go to Settings > Apps" +
                        "\n\n" +
                        "Data you share with $TEST_APP_NAME is covered by their privacy policy"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Read privacy policy")).perform(scrollTo()).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(AppAccessElement.PRIVACY_POLICY_LINK)
    }

    @Test
    fun additionalAccessState_notValid_hidesAdditionalAccess() {
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(AdditionalAccessViewModel.State())
        }

        launchFragment<CombinedPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onView(withText("Additional access")).check(doesNotExist())
    }

    @Test
    fun additionalAccessState_valid_showsAdditionalAccess() {
        val validState =
            AdditionalAccessViewModel.State(
                exerciseRoutePermissionUIState = PermissionUiState.ASK_EVERY_TIME,
                exercisePermissionUIState = PermissionUiState.ASK_EVERY_TIME)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(validState)
        }

        launchFragment<CombinedPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onView(withText("Additional access"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    fun additionalAccessState_onlyOneAdditionalPermission_showsAdditionalAccess() {
        val validState =
            AdditionalAccessViewModel.State(
                backgroundReadUIState =
                AdditionalAccessViewModel.AdditionalPermissionState(
                    isDeclared = true, isEnabled = false, isGranted = false))
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(validState)
        }

        launchFragment<CombinedPermissionsFragment>(
            bundleOf(EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME))

        onView(withText("Additional access"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(AppAccessElement.ADDITIONAL_ACCESS_BUTTON)
    }

    @Test
    fun additionalAccessState_onClick_navigatesToAdditionalAccessFragment() {
        val validState =
            AdditionalAccessViewModel.State(
                exerciseRoutePermissionUIState = PermissionUiState.ASK_EVERY_TIME,
                exercisePermissionUIState = PermissionUiState.ASK_EVERY_TIME)
        whenever(additionalAccessViewModel.additionalAccessState).then {
            MutableLiveData(validState)
        }

        launchFragment<CombinedPermissionsFragment>(
            bundleOf(
                EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME)) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.combinedPermissionsFragment)
            Navigation.setViewNavController(requireView(), navHostController)
        }
        onView(withText("Additional access")).perform(scrollTo()).perform(click())

        onIdle()
        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.additionalAccessFragment)
        verify(healthConnectLogger).logInteraction(AppAccessElement.ADDITIONAL_ACCESS_BUTTON)
    }

    @Test
    fun fitnessPermissions_navigatesToFitnessAppFragment() {
        launchFragment<CombinedPermissionsFragment>(
            bundleOf(
                EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME)) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.combinedPermissionsFragment)
            Navigation.setViewNavController(requireView(), navHostController)
        }
        onView(withText("Manage fitness permissions")).perform(scrollTo()).perform(click())

        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.fitnessAppFragment)
    }

    @Test
    fun medicalPermissions_navigatesToMedicalAppFragment() {
        launchFragment<CombinedPermissionsFragment>(
            bundleOf(
                EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME, EXTRA_APP_NAME to TEST_APP_NAME)) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.combinedPermissionsFragment)
            Navigation.setViewNavController(requireView(), navHostController)
        }
        onView(withText("Manage health record permissions")).perform(scrollTo()).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.medicalAppFragment)
    }
}
