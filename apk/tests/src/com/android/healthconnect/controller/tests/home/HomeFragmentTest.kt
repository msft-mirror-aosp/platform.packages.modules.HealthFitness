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
package com.android.healthconnect.controller.tests.home

import android.Manifest
import android.content.Context
import android.health.connect.HealthDataCategory
import android.os.Bundle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings.ACTION_SECURITY_SETTINGS
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.api.ExportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiState
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiStatus
import com.android.healthconnect.controller.home.HomeFragment
import com.android.healthconnect.controller.home.HomeViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.WithData
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.recentaccess.RecentAccessEntry
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel
import com.android.healthconnect.controller.recentaccess.RecentAccessViewModel.RecentAccessState
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.app.AppPermissionsType
import com.android.healthconnect.controller.shared.app.ConnectedAppMetadata
import com.android.healthconnect.controller.shared.app.ConnectedAppStatus
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.di.FakeDeviceInfoUtils
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.DeviceInfoUtilsModule
import com.android.healthconnect.controller.utils.KeyguardManagerUtil
import com.android.healthconnect.controller.utils.NavigationUtils
import com.android.healthconnect.controller.utils.logging.DataRestoreElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HomePageElement
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.RecentAccessElement
import com.android.healthfitness.flags.Flags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
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

@HiltAndroidTest
@UninstallModules(DeviceInfoUtilsModule::class)
class HomeFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val setFlagsRule = SetFlagsRule()
    private lateinit var context: Context

    @BindValue val homeViewModel: HomeViewModel = Mockito.mock(HomeViewModel::class.java)

    @BindValue
    val recentAccessViewModel: RecentAccessViewModel =
        Mockito.mock(RecentAccessViewModel::class.java)

    @BindValue
    val migrationViewModel: MigrationViewModel = Mockito.mock(MigrationViewModel::class.java)

    @BindValue
    val exportStatusViewModel: ExportStatusViewModel =
        Mockito.mock(ExportStatusViewModel::class.java)

    @BindValue val deviceInfoUtils: DeviceInfoUtils = FakeDeviceInfoUtils()

    @BindValue val timeSource = TestTimeSource
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    private lateinit var navHostController: TestNavHostController
    @BindValue val navigationUtils: NavigationUtils = Mockito.mock(NavigationUtils::class.java)
    @BindValue
    val keyguardManagerUtils: KeyguardManagerUtil = Mockito.mock(KeyguardManagerUtil::class.java)

    companion object {
        private const val TEST_EXPORT_FREQUENCY_IN_DAYS = 1
    }

    @Before
    fun setup() {
        hiltRule.inject()
        // Required for aconfig flag reading for tests run on pre V devices
        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG)
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE,
                    )
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        null,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        /** periodInDays= */
                        0,
                    )
                )
            )
        }
        whenever(homeViewModel.hasAnyMedicalData).then { MutableLiveData(false) }
        navHostController = TestNavHostController(context)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf(ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED)))
        }
        whenever(homeViewModel.showLockScreenBanner).then {
            MediatorLiveData(HomeViewModel.LockScreenBannerState.NoBanner)
        }
        (deviceInfoUtils as FakeDeviceInfoUtils).setIntentHandlerAvailability(true)

        Intents.init()

        // disable animations
        toggleAnimation(false)
        setStartUsingHcBannerSeen(context, false)
    }

    @After
    fun teardown() {
        timeSource.reset()
        Intents.release()
        // enable animations
        toggleAnimation(true)
        reset(healthConnectLogger)
    }

    // region Navigation tests
    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun appPermissions_navigatesToConnectedApps() {
        setupFragmentForNavigation()
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("App permissions")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.connectedAppsFragment)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun dataAndAccess_navigatesToDataAndAccess() {
        setupFragmentForNavigation()
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.data_activity)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun browseMedicalData_navigatesToBrowseMedicalData() {
        whenever(homeViewModel.hasAnyMedicalData).then { MutableLiveData(true) }
        setupFragmentForNavigation()

        onView(withText("Browse health records")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withText("Browse health records")).perform(scrollTo()).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.medicalAllDataFragment)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun seeAllRecentAccess_navigatesToRecentAccess() {
        setupFragmentForNavigation()
        onView(withText("See all recent access")).check(matches(isDisplayed()))
        onView(withText("See all recent access")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.recentAccessFragment)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun recentAccessApp_navigatesToFitnessAppFragment() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle(),
                    ),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle(),
                    ),
            )
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.homeFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.fitnessAppFragment)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun recentAccessApp_navigatesToMedicalAppFragment() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle(),
                    ),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle(),
                    ),
                appPermissionsType = AppPermissionsType.MEDICAL_PERMISSIONS_ONLY,
            )
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.homeFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.medicalAppFragment)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun recentAccessApp_navigatesToCombinedPermissionsFragment() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle(),
                    ),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle(),
                    ),
                appPermissionsType = AppPermissionsType.COMBINED_PERMISSIONS,
            )
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.homeFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).perform(click())
        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.combinedPermissionsFragment)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun manageData_navigatesToManageData() {
        setupFragmentForNavigation()
        onView(withText("Manage data")).check(matches(isDisplayed()))
        onView(withText("Manage data")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.manageDataFragment)
    }

    // endregion

    // region Display tests
    @Test
    @DisableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE, Flags.FLAG_ONBOARDING)
    fun whenRecentAccessApps_showsRecentAccessApps() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle(),
                    ),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle(),
                    ),
            )

        timeSource.setIs24Hour(true)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your device, and control which apps can access it"
                )
            )
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("None")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("18:40")).check(matches(isDisplayed()))
        onView(withText("See all recent access")).check(matches(isDisplayed()))
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenRecentAccessApps_in12HourFormat_showsCorrectTime() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle(),
                    ),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle(),
                    ),
            )

        timeSource.setIs24Hour(false)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle())

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText(TEST_APP_NAME)).check(matches(isDisplayed()))
        onView(withText("6:40 PM")).check(matches(isDisplayed()))
        onView(withText("See all recent access")).check(matches(isDisplayed()))
    }

    @Test
    @DisableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE, Flags.FLAG_ONBOARDING)
    fun oldIA_withNoRecentAccessApps() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your device, and control which apps can access it"
                )
            )
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("2 apps have access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("No apps recently accessed Health\u00A0Connect"))
            .check(matches(isDisplayed()))
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    @EnableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE)
    fun whenOneAppConnected_showsOneAppHasPermissions() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf(ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED)))
        }

        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your device, and control which apps can access it"
                )
            )
            .check(doesNotExist())
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("1 app has access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("See data and which apps can access it")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))
    }

    @Test
    @DisableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE, Flags.FLAG_ONBOARDING)
    fun whenOneAppConnected_oneAppNotConnected_showsCorrectSummary() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.DENIED),
                )
            )
        }

        launchFragment<HomeFragment>(Bundle())

        onView(
                withText(
                    "Manage the health and fitness data on your device, and control which apps can access it"
                )
            )
            .check(matches(isDisplayed()))
        onView(withText("App permissions")).check(matches(isDisplayed()))
        onView(withText("1 of 2 apps has access")).check(matches(isDisplayed()))
        onView(withText("Data and access")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))
    }

    // endregion

    // region Migration tests
    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenMigrationStatePending_showsMigrationBanner() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_PAUSED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE,
                    )
                )
            )
        }
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Resume integration")).check(matches(isDisplayed()))
        onView(withText("Tap to continue integrating Health Connect with the Android system."))
            .check(matches(isDisplayed()))
        onView(withText("Continue")).check(matches(isDisplayed()))

        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_RESUME_BANNER)
        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_RESUME_BANNER_BUTTON)

        onView(withText("Continue")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.migrationActivity)
        verify(healthConnectLogger, atLeast(1))
            .logInteraction(MigrationElement.MIGRATION_RESUME_BANNER_BUTTON)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenDataRestoreStatePending_showsRestoreBanner() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.PENDING,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE,
                    )
                )
            )
        }
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Update needed")).check(matches(isDisplayed()))
        onView(withText("Before continuing restoring your data, update your phone system."))
            .check(matches(isDisplayed()))
        onView(withText("Update now")).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(DataRestoreElement.RESTORE_PENDING_BANNER)
        verify(healthConnectLogger)
            .logImpression(DataRestoreElement.RESTORE_PENDING_BANNER_UPDATE_BUTTON)

        onView(withText("Update now")).perform(click())

        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.systemUpdateActivity)
        verify(healthConnectLogger)
            .logInteraction(DataRestoreElement.RESTORE_PENDING_BANNER_UPDATE_BUTTON)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenMigrationStateComplete_showsDialog() {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.WHATS_NEW_DIALOG_SEEN, false)
        editor.apply()

        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.COMPLETE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE,
                    )
                )
            )
        }
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        val scenario = launchFragment<HomeFragment>(Bundle())

        onView(withText("What's new")).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        onView(
                withText(
                    "You can now access Health Connect directly from your settings. Uninstall the Health Connect app any time to free up storage space."
                )
            )
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_DONE_DIALOG_CONTAINER)
        verify(healthConnectLogger).logImpression(MigrationElement.MIGRATION_DONE_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).perform(click())
        verify(healthConnectLogger).logInteraction(MigrationElement.MIGRATION_DONE_DIALOG_BUTTON)

        scenario.onActivity { activity ->
            val preferences =
                activity.getSharedPreferences("USER_ACTIVITY_TRACKER", Context.MODE_PRIVATE)
            assertThat(preferences.getBoolean("Whats New Seen", false)).isTrue()
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenMigrationStateNotComplete_showsDialog() {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.MIGRATION_NOT_COMPLETE_DIALOG_SEEN, false)
        editor.apply()

        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_ERROR,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE,
                    )
                )
            )
        }
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        val scenario = launchFragment<HomeFragment>(Bundle())

        onView(withText("Health Connect integration didn't complete"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("You'll get a notification when it becomes available again."))
            .inRoot(RootMatchers.isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_NOT_COMPLETE_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_NOT_COMPLETE_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).perform(click())
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_NOT_COMPLETE_DIALOG_BUTTON)

        scenario.onActivity { activity ->
            val preferences =
                activity.getSharedPreferences("USER_ACTIVITY_TRACKER", Context.MODE_PRIVATE)
            assertThat(preferences.getBoolean("Migration Not Complete Seen", false)).isTrue()
        }
    }

    // endregion

    // region Medical data tests
    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING, Flags.FLAG_PERSONAL_HEALTH_RECORD)
    fun browseMedicalData_flagDisabled_notDisplayed() {
        setupFragmentForNavigation()

        onView(withText("Browse health records")).check(doesNotExist())
        onView(withText("View your health records and which apps can access them"))
            .check(doesNotExist())
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun browseMedicalData_errorFetchingMedicalDataOrEmptyMedicalData_notDisplayed() {
        whenever(homeViewModel.hasAnyMedicalData).then { MutableLiveData(false) }

        setupFragmentForNavigation()

        onView(withText("Browse health records")).check(doesNotExist())
        onView(withText("View your health records and which apps can access them"))
            .check(doesNotExist())
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun browseMedicalData_medicalDataExists_isDisplayed() {
        whenever(homeViewModel.hasAnyMedicalData).then { MutableLiveData(true) }

        setupFragmentForNavigation()

        onView(withText("Browse health records")).check(matches(isDisplayed()))
        onView(withText("View your health records and which apps can access them"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    // endregion

    // region Import/Export tests
    @Test
    @DisableFlags(Flags.FLAG_EXPORT_IMPORT, Flags.FLAG_ONBOARDING)
    fun whenExportImportFlagIsDisabled_doesNotShowExportFileAccessErrorBanner() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        NOW,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_LOST_FILE_ACCESS,
                        TEST_EXPORT_FREQUENCY_IN_DAYS,
                    )
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Couldn't export data")).check(doesNotExist())
    }

    @Test
    @DisableFlags(Flags.FLAG_EXPORT_IMPORT, Flags.FLAG_ONBOARDING)
    fun whenExportImportFlagIsDisabled_doesNotShowManageDataSummary() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        NOW,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_LOST_FILE_ACCESS,
                        TEST_EXPORT_FREQUENCY_IN_DAYS,
                    )
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Auto-delete, data sources, backup and restore")).check(doesNotExist())
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT)
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenExportImportFlagIsEnabled_noError_exportFileAccessErrorBannerIsNotShown() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        NOW,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        TEST_EXPORT_FREQUENCY_IN_DAYS,
                    )
                )
            )
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Couldn't export data")).check(doesNotExist())
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT)
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenExportImportFlagIsEnabled_lastFailedExportTimeIsNull_exportFileAccessErrorBannerIsNotShown() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_LOST_FILE_ACCESS,
                        periodInDays = TEST_EXPORT_FREQUENCY_IN_DAYS,
                        lastFailedExportTime = null,
                    )
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Couldn't export data")).check(doesNotExist())
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT)
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenExportImportFlagIsEnabled_withUnknownErrorAndDate_showsExportErrorBanner() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_UNKNOWN,
                        periodInDays = TEST_EXPORT_FREQUENCY_IN_DAYS,
                        lastFailedExportTime = NOW,
                    )
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Couldn't export data")).check(matches(isDisplayed()))
        onView(withText("Set up")).check(matches(isDisplayed()))
        onView(
                withText(
                    "There was a problem with the export for October 20, 2022. Please set up a new scheduled export and try again."
                )
            )
            .check(matches(isDisplayed()))
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT)
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenExportImportFlagIsEnabled_showsManageDataSummary() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        periodInDays = TEST_EXPORT_FREQUENCY_IN_DAYS,
                        lastFailedExportTime = NOW,
                    )
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Auto-delete, data sources, backup and restore"))
            .perform(scrollTo())
            .check(matches(isDisplayed()))
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT)
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenExportImportFlagIsEnabled_withLostFileAccessErrorAndDate_showsExportErrorBanner() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_LOST_FILE_ACCESS,
                        periodInDays = TEST_EXPORT_FREQUENCY_IN_DAYS,
                        lastFailedExportTime = NOW,
                    )
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Couldn't export data")).check(matches(isDisplayed()))
        onView(withText("Set up")).check(matches(isDisplayed()))
        onView(
                withText(
                    "There was a problem with the export for October 20, 2022. Please set up a new scheduled export and try again."
                )
            )
            .check(matches(isDisplayed()))
        verify(healthConnectLogger).logImpression(HomePageElement.EXPORT_ERROR_BANNER)
        verify(healthConnectLogger).logImpression(HomePageElement.EXPORT_ERROR_BANNER_BUTTON)
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT)
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun whenExportImportFlagIsEnabled_withValidErrorTypeAndDate_showsExportErrorBanner_clicksSetupAndNavigatesToExportFlow() {
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        dataExportError =
                            ScheduledExportUiState.DataExportError.DATA_EXPORT_LOST_FILE_ACCESS,
                        periodInDays = TEST_EXPORT_FREQUENCY_IN_DAYS,
                        lastFailedExportTime = NOW,
                    )
                )
            )
        }
        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Set up")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.exportSetupActivity)
    }

    // endregion

    // region Logging
    @Test
    @DisableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE, Flags.FLAG_ONBOARDING)
    fun homeFragmentLogging_oldIA_impressionsLogged() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle(),
                    ),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle(),
                    ),
            )

        timeSource.setIs24Hour(true)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle())

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.HOME_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(HomePageElement.APP_PERMISSIONS_BUTTON)
        verify(healthConnectLogger).logImpression(HomePageElement.DATA_AND_ACCESS_BUTTON)
        verify(healthConnectLogger).logImpression(HomePageElement.SEE_ALL_RECENT_ACCESS_BUTTON)
        verify(healthConnectLogger).logImpression(RecentAccessElement.RECENT_ACCESS_ENTRY_BUTTON)
    }

    @Test
    @EnableFlags(Flags.FLAG_NEW_INFORMATION_ARCHITECTURE)
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun homeFragmentLogging_newIA_impressionsLogged() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle(),
                    ),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle(),
                    ),
            )

        timeSource.setIs24Hour(true)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle())

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.HOME_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(HomePageElement.APP_PERMISSIONS_BUTTON)
        verify(healthConnectLogger).logImpression(HomePageElement.DATA_AND_ACCESS_BUTTON)
        verify(healthConnectLogger).logImpression(HomePageElement.MANAGE_DATA_BUTTON)
        verify(healthConnectLogger).logImpression(HomePageElement.SEE_ALL_RECENT_ACCESS_BUTTON)
        verify(healthConnectLogger).logImpression(RecentAccessElement.RECENT_ACCESS_ENTRY_BUTTON)
    }

    // endregion

    // region Onboarding banners
    @Test
    @EnableFlags(Flags.FLAG_ONBOARDING)
    fun onboardingFlagOn_whenNoAppsConnected_andOneAvailable_showsStartUsingHcBanner() {
        setStartUsingHcBannerSeen(context, false)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf(ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.DENIED)))
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
        onView(withText("Start using Health Connect")).check(matches(isDisplayed()))
        onView(withText("Sync your first apps to share health and fitness data between them"))
            .check(matches(isDisplayed()))
        onView(withText("Set up")).check(matches(isDisplayed()))

        onView(withText("Set up")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.connectedAppsFragment)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun onboardingFlagOff_whenNoAppsConnected_andOneAvailable_doesNotShowStartUsingHcBanner() {
        setStartUsingHcBannerSeen(context, false)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf(ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.DENIED)))
        }

        launchFragment<HomeFragment>(Bundle())
        onView(withText("Start using Health Connect")).check(doesNotExist())
        onView(withText("Sync your first apps to share health and fitness data between them"))
            .check(doesNotExist())
        onView(withText("Set up")).check(doesNotExist())
    }

    @Test
    @EnableFlags(Flags.FLAG_ONBOARDING)
    fun onboardingFlagOn_whenOneAppConnected_andMoreAvailable_showsConnectMoreAppsBanner() {
        setConnectMoreAppsBannerSeen(context, false)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.DENIED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
        onView(withText("Connect more apps")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Start sharing data between Health Connect test app 2 and the health apps on your phone"
                )
            )
            .check(matches(isDisplayed()))
        onView(withText("Set up")).check(matches(isDisplayed()))

        onView(withText("Set up")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.connectedAppsFragment)
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun onboardingFlagOff_whenOneAppConnected_andMoreAvailable_doesNotShowConnectMoreAppsBanner() {
        setConnectMoreAppsBannerSeen(context, false)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(
                listOf(
                    ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.DENIED),
                    ConnectedAppMetadata(TEST_APP_2, ConnectedAppStatus.ALLOWED),
                )
            )
        }

        launchFragment<HomeFragment>(Bundle())
        onView(withText("Connect more apps")).check(doesNotExist())
        onView(
                withText(
                    "Start sharing data between Health Connect test app 2 and the health apps on your phone"
                )
            )
            .check(doesNotExist())
        onView(withText("Set up")).check(doesNotExist())
    }

    @Test
    @EnableFlags(Flags.FLAG_ONBOARDING)
    fun onboardingFlagOn_playstoreAvailable_whenOneAppConnected_andNoMoreAvailable_showsSeeCompatibleAppsBanner() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)
        setConnectMoreAppsBannerSeen(context, false)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf(ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED)))
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
        onView(withText("See compatible apps")).check(matches(isDisplayed()))
        onView(withText("Find more apps to sync with Health Connect test app via Health Connect"))
            .check(matches(isDisplayed()))
        onView(withText("See on app store")).check(matches(isDisplayed()))

        onView(withText("See on app store")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.playstore_activity)
    }

    @Test
    @EnableFlags(Flags.FLAG_ONBOARDING)
    fun onboardingFlagOn_playstoreNotAvailable_doesNotShowSeeCompatibleAppsBanner() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(false)
        setConnectMoreAppsBannerSeen(context, false)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf(ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED)))
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
        onView(withText("See compatible apps")).check(doesNotExist())
        onView(withText("Find more apps to sync with Health Connect test app via Health Connect"))
            .check(doesNotExist())
        onView(withText("See on app store")).check(doesNotExist())
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    fun onboardingFlagOff_whenOneAppConnected_andNoMoreAvailable_doesNotShowSeeCompatibleAppsBanner() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setPlayStoreAvailability(true)
        setConnectMoreAppsBannerSeen(context, false)
        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(emptyList()))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf(ConnectedAppMetadata(TEST_APP, ConnectedAppStatus.ALLOWED)))
        }

        launchFragment<HomeFragment>(Bundle())
        onView(withText("See compatible apps")).check(doesNotExist())
        onView(withText("Find more apps to sync with Health Connect test app via Health Connect"))
            .check(doesNotExist())
        onView(withText("See on app store")).check(doesNotExist())
    }

    // endregion

    // region lock screen banner
    @Test
    @DisableFlags(
        Flags.FLAG_PERSONAL_HEALTH_RECORD,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER,
    )
    fun lockScreenBanner_phrFlagOff_bannerFlagOff_bannerNotShown() {
        launchFragment<HomeFragment>(Bundle())

        onView(withText("Set a screen lock")).check(doesNotExist())
    }

    @Test
    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER)
    fun lockScreenBanner_phrFlagOff_bannerFlagOn_bannerNotShown() {

        launchFragment<HomeFragment>(Bundle())

        onView(withText("Set a screen lock")).check(doesNotExist())
    }

    @Test
    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER)
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    fun lockScreenBanner_phrFlagOn_bannerFlagOff_bannerNotShown() {
        launchFragment<HomeFragment>(Bundle())

        onView(withText("Set a screen lock")).check(doesNotExist())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_PERSONAL_HEALTH_RECORD,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER,
    )
    fun lockScreenBanner_shouldNotShowBanner_bannerNotShown() {
        whenever(homeViewModel.showLockScreenBanner).then {
            MediatorLiveData(HomeViewModel.LockScreenBannerState.NoBanner)
        }

        launchFragment<HomeFragment>(Bundle())

        onView(withText("Set a screen lock")).check(doesNotExist())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_PERSONAL_HEALTH_RECORD,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER,
    )
    fun lockScreenBanner_securityIntentNotHandled_bannerNotShown() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setIntentHandlerAvailability(false)
        whenever(homeViewModel.showLockScreenBanner).then {
            MediatorLiveData(HomeViewModel.LockScreenBannerState.ShowBanner())
        }

        launchFragment<HomeFragment>(Bundle())

        onView(withText("Set a screen lock")).check(doesNotExist())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_PERSONAL_HEALTH_RECORD,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER,
    )
    fun lockScreenBanner_bannerShown() {
        whenever(homeViewModel.showLockScreenBanner).then {
            MediatorLiveData(HomeViewModel.LockScreenBannerState.ShowBanner())
        }

        launchFragment<HomeFragment>(Bundle())

        onView(withText("Set a screen lock")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(
                withText(
                    "For added security for your health data, set a PIN, pattern, or password for this device"
                )
            )
            .perform(scrollTo())
            .check(matches(isDisplayed()))
        onView(withText("Set screen lock")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    @EnableFlags(
        Flags.FLAG_PERSONAL_HEALTH_RECORD,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER,
    )
    fun lockScreenBanner_whenMedicalAndFitnessData_bannerShown() {
        whenever(homeViewModel.showLockScreenBanner).then {
            MediatorLiveData(
                HomeViewModel.LockScreenBannerState.ShowBanner(
                    hasAnyFitnessData = true,
                    hasAnyMedicalData = true,
                )
            )
        }

        launchFragment<HomeFragment>(Bundle())

        onView(withText("Set a screen lock")).perform(scrollTo()).check(matches(isDisplayed()))
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    @EnableFlags(
        Flags.FLAG_PERSONAL_HEALTH_RECORD,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER,
    )
    fun lockScreenBanner_startsNewPasswordIntent() {
        whenever(homeViewModel.showLockScreenBanner).then {
            MediatorLiveData(HomeViewModel.LockScreenBannerState.ShowBanner())
        }

        launchFragment<HomeFragment>(Bundle())
        onView(withText("Set screen lock")).perform(scrollTo()).perform(click())

        intended(hasAction(ACTION_SECURITY_SETTINGS))
    }

    @Test
    @DisableFlags(Flags.FLAG_ONBOARDING)
    @EnableFlags(
        Flags.FLAG_PERSONAL_HEALTH_RECORD,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_LOCK_SCREEN_BANNER,
    )
    fun lockScreenBanner_dismissBanner_bannerDisappears() {
        whenever(homeViewModel.showLockScreenBanner).then {
            MediatorLiveData(HomeViewModel.LockScreenBannerState.ShowBanner())
        }

        launchFragment<HomeFragment>(Bundle())

        onView(withText("Set screen lock")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.dismiss_button)).perform(scrollTo()).perform(click())

        onView(withText("Set screen lock")).check(doesNotExist())
    }

    // endregion

    private fun setupFragmentForNavigation() {
        val recentApp =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle(),
                    ),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle(),
                    ),
            )

        timeSource.setIs24Hour(true)

        whenever(recentAccessViewModel.recentAccessApps).then {
            MutableLiveData<RecentAccessState>(RecentAccessState.WithData(listOf(recentApp)))
        }
        whenever(homeViewModel.connectedApps).then {
            MutableLiveData(listOf<ConnectedAppMetadata>())
        }

        launchFragment<HomeFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }
    }

    private fun setStartUsingHcBannerSeen(context: Context, seen: Boolean) {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.START_USING_HC_BANNER_SEEN, seen)
        editor.apply()
    }

    private fun setConnectMoreAppsBannerSeen(context: Context, seen: Boolean) {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.CONNECT_MORE_APPS_BANNER_SEEN, seen)
        editor.apply()
    }
}
