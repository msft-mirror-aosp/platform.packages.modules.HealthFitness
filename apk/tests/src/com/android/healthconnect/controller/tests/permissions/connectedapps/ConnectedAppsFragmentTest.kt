/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.permissions.connectedapps

import android.content.Context
import android.health.connect.HealthConnectManager
import android.os.Bundle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToLastPosition
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsFragment
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel.DisconnectAllState.Loading
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel.DisconnectAllState.NotStarted
import com.android.healthconnect.controller.permissions.connectedapps.ConnectedAppsViewModel.DisconnectAllState.Updated
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.app.AppPermissionsType
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.ALLOWED
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.DENIED
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.INACTIVE
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus.NEEDS_UPDATE
import com.android.healthconnect.controller.tests.utils.OLD_TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_3
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME_2
import com.android.healthconnect.controller.tests.utils.di.FakeDeviceInfoUtils
import com.android.healthconnect.controller.tests.utils.isAbove
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.DeviceInfoUtilsModule
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.logging.AppPermissionsElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthfitness.flags.Flags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@UninstallModules(DeviceInfoUtilsModule::class)
@HiltAndroidTest
class ConnectedAppsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val setFlagsRule = SetFlagsRule()

    @Inject lateinit var manager: HealthConnectManager

    @BindValue
    val viewModel: ConnectedAppsViewModel = Mockito.mock(ConnectedAppsViewModel::class.java)

    @BindValue val deviceInfoUtils: DeviceInfoUtils = FakeDeviceInfoUtils()
    @BindValue val navigationUtils: NavigationUtils = Mockito.mock(NavigationUtils::class.java)
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()
    private lateinit var navHostController: TestNavHostController
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        whenever(viewModel.disconnectAllState).then { MutableLiveData(NotStarted) }
        whenever(viewModel.alertDialogActive).then { MutableLiveData(false) }
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
        toggleAnimation(false)
    }

    @After
    fun tearDown() {
        (deviceInfoUtils as FakeDeviceInfoUtils).reset()
        reset(healthConnectLogger)
        toggleAnimation(true)
    }

    @Test
    fun appName_navigatesToFitnessAppPermissions() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(
                    TEST_APP,
                    status = ALLOWED,
                    permissionsType = AppPermissionsType.FITNESS_PERMISSIONS_ONLY,
                )
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        (deviceInfoUtils as FakeDeviceInfoUtils).setSendFeedbackAvailability(false)
        deviceInfoUtils.setPlayStoreAvailability(true)

        launchFragment<ConnectedAppsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.connectedAppsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.fitnessAppFragment)
    }

    @Test
    fun appName_navigatesToMedicalAppPermissions() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(
                    TEST_APP,
                    status = ALLOWED,
                    permissionsType = AppPermissionsType.MEDICAL_PERMISSIONS_ONLY,
                )
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        (deviceInfoUtils as FakeDeviceInfoUtils).setSendFeedbackAvailability(false)
        deviceInfoUtils.setPlayStoreAvailability(true)

        launchFragment<ConnectedAppsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.connectedAppsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.medicalAppFragment)
    }

    @Test
    fun appName_navigatesToCombinedPermissions() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(
                    TEST_APP,
                    status = ALLOWED,
                    permissionsType = AppPermissionsType.COMBINED_PERMISSIONS,
                )
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        (deviceInfoUtils as FakeDeviceInfoUtils).setSendFeedbackAvailability(false)
        deviceInfoUtils.setPlayStoreAvailability(true)

        launchFragment<ConnectedAppsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.connectedAppsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).perform(click())
        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.combinedPermissionsFragment)
    }

    @Test
    fun helpAndFeedback_navigatesToHelpAndFeedback() {
        setupFragmentForNavigation()
        onView(withText("Help & feedback")).check(matches(isDisplayed()))
        onView(withText("Help & feedback")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.helpAndFeedbackFragment)
    }

    @Test
    fun test_allowedApps() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = ALLOWED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("No apps allowed")).check(doesNotExist())
        onView(withText("No apps denied")).check(matches(isDisplayed()))
    }

    @Test
    fun test_deniedApps() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = DENIED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("No apps denied")).check(doesNotExist())
        onView(withText("No apps allowed")).check(matches(isDisplayed()))
    }

    @Test
    fun allowedApps_removeAccessEnabled() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = ALLOWED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(R.string.disconnect_all_apps)).check(matches(isEnabled()))
    }

    @Test
    fun allowedApps_confirmationDialogDisplayed_dialogDoesNotDisAppearWhenActivityRestarts() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = ALLOWED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        whenever(viewModel.alertDialogActive).then { MutableLiveData(true) }

        val scenario = launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("Remove access for all apps?"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))

        scenario.recreate()

        onView(withText("Remove access for all apps?"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    @EnableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE)
    fun allowedApps_newIAEnabled_confirmationDialogDisplayed_checkboxInDialogDisplayed() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = ALLOWED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        whenever(viewModel.alertDialogActive).then { MutableLiveData(true) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("Also delete all Health Connect data"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    fun allowedApps_phrEnabled_confirmationDialogDisplayed_checkboxInDialogDisplayed() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = ALLOWED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        whenever(viewModel.alertDialogActive).then { MutableLiveData(true) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("Also delete all Health Connect data"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun noAllowedApps_removeAccessDisabled() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = DENIED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(R.string.disconnect_all_apps)).check(matches(isDisplayed()))
    }

    @Test
    fun showsLoading() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = DENIED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        whenever(viewModel.disconnectAllState).then { MutableLiveData(Loading) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(R.string.loading)).check(matches(isDisplayed()))
    }

    @Test
    fun dismissLoading() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = DENIED))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        whenever(viewModel.disconnectAllState).then { MutableLiveData(Updated) }
        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(R.string.loading)).check(doesNotExist())
    }

    @Test
    fun inactiveApp_showsInactiveApps() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = INACTIVE))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(R.string.inactive_apps)).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(AppPermissionsElement.INACTIVE_APP_BUTTON)
        verify(healthConnectLogger).logImpression(AppPermissionsElement.INACTIVE_APP_DELETE_BUTTON)
    }

    @Test
    fun allowedApps_displayedAlphabetically() {
        val connectedApps =
            listOf(
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package3", appName = "thirdApp", icon = null),
                    status = ALLOWED,
                ),
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package1", appName = "firstApp", icon = null),
                    status = ALLOWED,
                ),
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package2", appName = "secondApp", icon = null),
                    status = ALLOWED,
                ),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectedApps) }

        launchFragment<ConnectedAppsFragment>(Bundle())
        onView(withText("firstApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("secondApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("firstApp")).check(matches(isAbove(withText("secondApp"))))
        onView(withText("thirdApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("secondApp")).check(matches(isAbove(withText("thirdApp"))))
    }

    @Test
    fun notAllowedApps_displayedAlphabetically() {
        val connectedApps =
            listOf(
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package3", appName = "thirdApp", icon = null),
                    status = DENIED,
                ),
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package1", appName = "firstApp", icon = null),
                    status = DENIED,
                ),
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package2", appName = "secondApp", icon = null),
                    status = DENIED,
                ),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectedApps) }

        launchFragment<ConnectedAppsFragment>(Bundle())
        onView(withText("firstApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("secondApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("firstApp")).check(matches(isAbove(withText("secondApp"))))
        onView(withText("thirdApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("secondApp")).check(matches(isAbove(withText("thirdApp"))))
    }

    @Test
    fun inactiveApps_displayedAlphabetically() {
        val connectedApps =
            listOf(
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package3", appName = "thirdApp", icon = null),
                    status = INACTIVE,
                ),
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package1", appName = "firstApp", icon = null),
                    status = INACTIVE,
                ),
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package2", appName = "secondApp", icon = null),
                    status = INACTIVE,
                ),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectedApps) }

        launchFragment<ConnectedAppsFragment>(Bundle())
        onView(withText("firstApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("secondApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("firstApp")).check(matches(isAbove(withText("secondApp"))))
        onView(withText("thirdApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("secondApp")).check(matches(isAbove(withText("thirdApp"))))
    }

    @Test
    fun needsUpdateApps_displayedAlphabetically() {
        val connectedApps =
            listOf(
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package3", appName = "thirdApp", icon = null),
                    status = NEEDS_UPDATE,
                ),
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package1", appName = "firstApp", icon = null),
                    status = NEEDS_UPDATE,
                ),
                ConnectedAppMetadata(
                    AppMetadata(packageName = "package2", appName = "secondApp", icon = null),
                    status = NEEDS_UPDATE,
                ),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectedApps) }

        launchFragment<ConnectedAppsFragment>(Bundle())
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        onView(withText("firstApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("secondApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("firstApp")).check(matches(isAbove(withText("secondApp"))))
        onView(withText("thirdApp")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("secondApp")).check(matches(isAbove(withText("thirdApp"))))
    }

    @EnableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE)
    @Test
    fun whenClickOnInactiveApp_newIaFlagEnabled_showsDeleteDataDialog() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = INACTIVE))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(R.string.inactive_apps)).check(matches(isDisplayed()))
        onView(withTagValue(`is`("Delete button inactive app"))).perform(click())

        onView(withText("Permanently delete all $TEST_APP_NAME data?"))
            .check(matches(isDisplayed()))
    }

    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    @Test
    fun whenClickOnInactiveApp_phrFlagEnabled_showsDeleteDataDialog() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = INACTIVE))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(R.string.inactive_apps)).check(matches(isDisplayed()))
        onView(withTagValue(`is`("Delete button inactive app"))).perform(click())

        onView(withText("Permanently delete all $TEST_APP_NAME data?"))
            .check(matches(isDisplayed()))
    }

    @DisableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE, Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @Test
    fun whenClickOnInactiveApp_oldDeletion_showsDeleteDataDialog() {
        val connectApp = listOf(ConnectedAppMetadata(TEST_APP, status = INACTIVE))
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(R.string.inactive_apps)).check(matches(isDisplayed()))
        onView(withTagValue(`is`("Delete button inactive app"))).perform(click())

        onView(withText("Permanently delete $TEST_APP_NAME data from all time?"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testWithNoApps_bothFeedbackAndPlayStoreAvailable_bothShouldBeDisplayedInThingsToTryOptions() {
        val connectApp = listOf<ConnectedAppMetadata>()
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)
        deviceInfoUtils.setSendFeedbackAvailability(true)

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("You don't currently have any compatible apps installed"))
            .check(matches(isDisplayed()))
        onView(withText("Things to try")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Check for updates")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Make sure installed apps are up-to-date"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("See all compatible apps"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Find apps on Google\u00A0Play"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Send feedback")).perform(scrollTo()).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(AppPermissionsElement.SEND_FEEDBACK_BUTTON)
        verify(healthConnectLogger).logImpression(AppPermissionsElement.CHECK_FOR_UPDATES_BUTTON)
        verify(healthConnectLogger)
            .logImpression(AppPermissionsElement.SEE_ALL_COMPATIBLE_APPS_BUTTON)
    }

    @Test
    fun testWithNoApps_bothFeedbackAndPlayStoreAreNotAvailable_thingsToTryOptionsShouldNotBeDisplayed() {
        val connectApp = listOf<ConnectedAppMetadata>()
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)
        deviceInfoUtils.setSendFeedbackAvailability(false)

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("You don't currently have any compatible apps installed"))
            .check(matches(isDisplayed()))
        onView(withText("Things to try")).check(doesNotExist())
        onView(withText("Check for updates")).check(doesNotExist())
        onView(withText("Make sure installed apps are up-to-date")).check(doesNotExist())
        onView(withText("See all compatible apps")).check(doesNotExist())
        onView(withText("Find apps on Google\u00A0Play")).check(doesNotExist())
        onView(withText("Send feedback")).check(doesNotExist())
    }

    @Test
    fun test_all() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(TEST_APP, status = DENIED),
                ConnectedAppMetadata(TEST_APP_2, status = ALLOWED),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME_2)).check(matches(isDisplayed()))
        onView(withText("No apps allowed")).check(doesNotExist())
        onView(withText("No apps denied")).check(doesNotExist())

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        onView(withText("Inactive apps")).check(doesNotExist())

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.APP_PERMISSIONS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(AppPermissionsElement.CONNECTED_APP_BUTTON)
        verify(healthConnectLogger).logImpression(AppPermissionsElement.NOT_CONNECTED_APP_BUTTON)
    }

    @Test
    fun eitherFeedbackOrPlayStoreAvailable_helpAndFeedbackPreferenceShouldBeDisplayed() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(TEST_APP, status = DENIED),
                ConnectedAppMetadata(TEST_APP_2, status = ALLOWED),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        (deviceInfoUtils as FakeDeviceInfoUtils).setSendFeedbackAvailability(false)
        deviceInfoUtils.setPlayStoreAvailability(true)

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("Help & feedback")).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(AppPermissionsElement.HELP_AND_FEEDBACK_BUTTON)
    }

    @Test
    fun bothFeedbackAndPlayStoreNotAvailable_helpAndFeedbackPreferenceShouldNotBeDisplayed() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(TEST_APP, status = DENIED),
                ConnectedAppMetadata(TEST_APP_2, status = ALLOWED),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }

        (deviceInfoUtils as FakeDeviceInfoUtils).setSendFeedbackAvailability(false)
        deviceInfoUtils.setPlayStoreAvailability(false)

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("Help & feedback")).check(doesNotExist())
    }

    @Test
    fun appNeedsUpdatingElements_shownWhenOldAppWithDataInstalled() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(TEST_APP, status = ALLOWED),
                ConnectedAppMetadata(TEST_APP_2, status = DENIED),
                ConnectedAppMetadata(OLD_TEST_APP, status = NEEDS_UPDATE),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        launchFragment<ConnectedAppsFragment>(Bundle())

        onView(withText("App update needed")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Old permissions test app needs to be updated to " +
                        "continue syncing with Health Connect"
                )
            )
            .check(matches(isDisplayed()))
        onView(withText("Learn more")).check(matches(isDisplayed()))
        onView(withText("Check for updates")).check(matches(isDisplayed()))
        onView(withText("Needs updating")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Old permissions test app"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))

        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_APP_UPDATE_BANNER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_APP_UPDATE_LEARN_MORE_BUTTON)
        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_APP_UPDATE_BUTTON)
    }

    @Test
    fun appNeedsUpdateBanner_multipleOldApps_playStoreNotAvailable_showsCorrectly() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(TEST_APP, status = ALLOWED),
                ConnectedAppMetadata(TEST_APP_2, status = DENIED),
                ConnectedAppMetadata(OLD_TEST_APP, status = NEEDS_UPDATE),
                ConnectedAppMetadata(TEST_APP_3, status = NEEDS_UPDATE),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)

        launchFragment<ConnectedAppsFragment>(Bundle())
        onView(withText("App update needed")).check(matches(isDisplayed()))
        onView(withText("Some apps need to be updated to continue syncing with Health Connect"))
            .check(matches(isDisplayed()))
        onView(withText("Learn more")).check(matches(isDisplayed()))
        onView(withText("Check for updates")).check(matches(not(isDisplayed())))

        onView(withText("Learn more")).perform(click())
        assertThat(deviceInfoUtils.helpCenterInvoked).isTrue()
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_APP_UPDATE_LEARN_MORE_BUTTON)
    }

    @Test
    fun appNeedsUpdateBanner_navigatesToPlayStoreWhenAvailable() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())

        val connectApp =
            listOf(
                ConnectedAppMetadata(TEST_APP, status = ALLOWED),
                ConnectedAppMetadata(TEST_APP_2, status = DENIED),
                ConnectedAppMetadata(OLD_TEST_APP, status = NEEDS_UPDATE),
                ConnectedAppMetadata(TEST_APP_3, status = NEEDS_UPDATE),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)

        launchFragment<ConnectedAppsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.connectedAppsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
        onView(withText("App update needed")).check(matches(isDisplayed()))
        onView(withText("Some apps need to be updated to continue syncing with Health Connect"))
            .check(matches(isDisplayed()))
        onView(withText("Learn more")).check(matches(isDisplayed()))
        onView(withText("Check for updates")).check(matches(isDisplayed()))

        onView(withText("Check for updates")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.update_apps_activity)
        verify(healthConnectLogger).logInteraction(MigrationElement.MIGRATION_APP_UPDATE_BUTTON)
    }

    private fun setupFragmentForNavigation() {
        val connectApp =
            listOf(
                ConnectedAppMetadata(TEST_APP, status = DENIED),
                ConnectedAppMetadata(TEST_APP_2, status = ALLOWED),
            )
        whenever(viewModel.connectedApps).then { MutableLiveData(connectApp) }
        (deviceInfoUtils as FakeDeviceInfoUtils).setSendFeedbackAvailability(false)
        deviceInfoUtils.setPlayStoreAvailability(true)

        launchFragment<ConnectedAppsFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.connectedAppsFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
    }
}
