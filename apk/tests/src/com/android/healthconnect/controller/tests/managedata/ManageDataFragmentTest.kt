package com.android.healthconnect.controller.tests.managedata

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.lifecycle.MutableLiveData
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.autodelete.AutoDeleteRange
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel
import com.android.healthconnect.controller.managedata.ManageDataFragment
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.ManageDataElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthfitness.flags.Flags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@HiltAndroidTest
class ManageDataFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val setFlagsRule = SetFlagsRule()
    @BindValue
    val autoDeleteViewModel: AutoDeleteViewModel = Mockito.mock(AutoDeleteViewModel::class.java)
    @Inject lateinit var fakeFeatureUtils: FeatureUtils
    private lateinit var context: Context
    private lateinit var navHostController: TestNavHostController
    @BindValue val healthConnectLogger: HealthConnectLogger = mock()

    @Before
    fun setup() {
        hiltRule.inject()

        // Required for aconfig flag reading for tests run on pre V devices
        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG)

        whenever(autoDeleteViewModel.storedAutoDeleteRange).then {
            MutableLiveData(
                AutoDeleteViewModel.AutoDeleteState.WithData(
                    AutoDeleteRange.AUTO_DELETE_RANGE_NEVER))
        }
        context = InstrumentationRegistry.getInstrumentation().context
        navHostController = TestNavHostController(context)
    }

    @After
    fun tearDown() {
        reset(healthConnectLogger)
    }

    @Test
    fun manageDataFragmentLogging_impressionsLogged() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        launchFragment<ManageDataFragment>(Bundle())

        verify(healthConnectLogger, atLeast(1)).setPageId(PageName.MANAGE_DATA_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger).logImpression(ManageDataElement.AUTO_DELETE_BUTTON)
        verify(healthConnectLogger)
            .logImpression(ManageDataElement.DATA_SOURCES_AND_PRIORITY_BUTTON)
        verify(healthConnectLogger).logImpression(ManageDataElement.SET_UNITS_BUTTON)
    }

    @Test
    fun manageDataFragment_isDisplayed_newAppPriorityFlagOn() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        launchFragment<ManageDataFragment>(Bundle())

        onView(withText("Auto-delete")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
        onView(withText("Set units")).check(matches(isDisplayed()))
    }

    @Test
    fun manageDataFragment_isDisplayed_newAppPriorityFlagOff() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(false)
        launchFragment<ManageDataFragment>(Bundle())

        onView(withText("Auto-delete")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).check(doesNotExist())
        onView(withText("Set units")).check(matches(isDisplayed()))
    }

    @Test
    @DisableFlags(Flags.FLAG_EXPORT_IMPORT)
    fun manageDataFragment_importExportFlagOff_preferenceCategoriesAndBackupButtonNotDisplayed() {
        launchFragment<ManageDataFragment>(Bundle())

        onView(withText("Auto-delete")).check(matches(isDisplayed()))
        onView(withText("Set units")).check(matches(isDisplayed()))
        onView(withText("Backup and restore")).check(doesNotExist())
        onView(withText("Preferences")).check(doesNotExist())
        onView(withText("Manage data")).check(doesNotExist())
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT)
    fun manageDataFragment_importExportFlagOn_displayedCorrectly() {
        launchFragment<ManageDataFragment>(Bundle())

        onView(withText("Auto-delete")).check(matches(isDisplayed()))
        onView(withText("Set units")).check(matches(isDisplayed()))
        onView(withText("Backup and restore")).check(matches(isDisplayed()))
        onView(withText("Preferences")).check(matches(isDisplayed()))
        onView(withText("Manage data")).check(matches(isDisplayed()))
    }

    @Test
    fun autoDelete_navigatesToAutoDelete() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        launchFragment<ManageDataFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.manageDataFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Auto-delete")).check(matches(isDisplayed()))
        onView(withText("Auto-delete")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.autoDeleteFragment)
        verify(healthConnectLogger).logInteraction(ManageDataElement.AUTO_DELETE_BUTTON)
    }

    @Test
    fun dataSources_navigatesToDataSources() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        launchFragment<ManageDataFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.manageDataFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Data sources and priority")).check(matches(isDisplayed()))
        onView(withText("Data sources and priority")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.dataSourcesFragment)
        verify(healthConnectLogger)
            .logInteraction(ManageDataElement.DATA_SOURCES_AND_PRIORITY_BUTTON)
    }

    @Test
    fun setUnits_navigatesToSetUnitsFragment() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewAppPriorityEnabled(true)
        launchFragment<ManageDataFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.manageDataFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Set units")).check(matches(isDisplayed()))
        onView(withText("Set units")).perform(click())
        assertThat(navHostController.currentDestination?.id).isEqualTo(R.id.setUnitsFragment)
        verify(healthConnectLogger).logInteraction(ManageDataElement.SET_UNITS_BUTTON)
    }

    @Test
    @EnableFlags(Flags.FLAG_EXPORT_IMPORT)
    fun manageDataFragment_importExportFlagOn_navigatesToBackupAndRestoreSettingsFragment() {
        launchFragment<ManageDataFragment>(Bundle()) {
            navHostController.setGraph(R.navigation.nav_graph)
            navHostController.setCurrentDestination(R.id.manageDataFragment)
            Navigation.setViewNavController(this.requireView(), navHostController)
        }

        onView(withText("Backup and restore")).check(matches(isDisplayed()))
        onView(withText("Backup and restore")).perform(click())
        assertThat(navHostController.currentDestination?.id)
            .isEqualTo(R.id.backupAndRestoreSettingsFragment)
    }
}
