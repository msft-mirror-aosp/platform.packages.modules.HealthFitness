/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 *
 */

package com.android.healthconnect.controller.tests.permissions.request

import android.app.Activity
import android.app.Activity.RESULT_CANCELED
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_PACKAGE_NAME
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.health.connect.HealthPermissions.READ_EXERCISE
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_HISTORY
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND
import android.health.connect.HealthPermissions.READ_MEDICAL_RESOURCES_IMMUNIZATION
import android.health.connect.HealthPermissions.READ_SKIN_TEMPERATURE
import android.health.connect.HealthPermissions.READ_SLEEP
import android.health.connect.HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED
import android.health.connect.HealthPermissions.WRITE_MEDICAL_RESOURCES
import android.health.connect.HealthPermissions.WRITE_SKIN_TEMPERATURE
import android.health.connect.HealthPermissions.WRITE_SLEEP
import android.widget.Button
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario.launchActivityForResult
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToLastPosition
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.WithData
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.navigation.TrampolineActivity
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.permissions.request.PermissionsActivity
import com.android.healthconnect.controller.service.HealthPermissionManagerModule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.UNSUPPORTED_TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.di.FakeDeviceInfoUtils
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.di.FakeHealthPermissionManager
import com.android.healthconnect.controller.tests.utils.showOnboarding
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.DeviceInfoUtilsModule
import com.android.healthconnect.controller.utils.FeatureUtils
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.mock

@UninstallModules(
    HealthPermissionManagerModule::class,
    DeviceInfoUtilsModule::class,
)
@HiltAndroidTest
class PermissionsActivityTest {

    companion object {
        private val fitnessPermissions =
            arrayOf(READ_EXERCISE, READ_SLEEP, WRITE_SKIN_TEMPERATURE, WRITE_ACTIVE_CALORIES_BURNED)
        private val fitnessAndMedicalPermissions =
            arrayOf(READ_EXERCISE, READ_MEDICAL_RESOURCES_IMMUNIZATION)
        private val fitnessAndAdditionalPermissions =
            arrayOf(WRITE_SLEEP, READ_HEALTH_DATA_IN_BACKGROUND)
        private val medicalPermissions =
            arrayOf(READ_MEDICAL_RESOURCES_IMMUNIZATION, WRITE_MEDICAL_RESOURCES)
        private val medicalAndAdditionalPermissions =
            arrayOf(READ_MEDICAL_RESOURCES_IMMUNIZATION, READ_HEALTH_DATA_IN_BACKGROUND)
        private val allThreeCombined =
            arrayOf(READ_HEALTH_DATA_IN_BACKGROUND, READ_SLEEP, READ_MEDICAL_RESOURCES_IMMUNIZATION)
    }

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val permissionManager: HealthPermissionManager = FakeHealthPermissionManager()
    @BindValue val deviceInfoUtils: DeviceInfoUtils = FakeDeviceInfoUtils()
    @BindValue val migrationViewModel: MigrationViewModel = mock(MigrationViewModel::class.java)
    @BindValue
    val loadAccessDateUseCase: LoadAccessDateUseCase = mock(LoadAccessDateUseCase::class.java)
    @Inject lateinit var fakeFeatureUtils: FeatureUtils

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = getInstrumentation().context
        permissionManager.revokeAllHealthPermissions(TEST_APP_PACKAGE_NAME)
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        whenever(loadAccessDateUseCase.invoke(any())).thenReturn(NOW)
        showOnboarding(context, false)
        toggleAnimation(false)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf())
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(false)
    }

    @After
    fun tearDown() {
        toggleAnimation(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(false)
    }

    @Test
    fun healthConnectNotAvailable_finishesActivity() {
        (deviceInfoUtils as FakeDeviceInfoUtils).setHealthConnectAvailable(false)

        val scenario =
            launchActivityForResult<TrampolineActivity>(
                getPermissionScreenIntent(fitnessPermissions))

        assertThat(scenario.result.resultCode).isEqualTo(RESULT_CANCELED)
    }

    @Test
    fun unsupportedApp_sendsResultCancelled() {
        val unsupportedAppIntent =
            Intent.makeMainActivity(ComponentName(context, PermissionsActivity::class.java))
                .putExtra(EXTRA_REQUEST_PERMISSIONS_NAMES, fitnessPermissions)
                .putExtra(EXTRA_PACKAGE_NAME, UNSUPPORTED_TEST_APP_PACKAGE_NAME)
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                .addFlags(FLAG_ACTIVITY_CLEAR_TASK)

        val scenario = launchActivityForResult<PermissionsActivity>(unsupportedAppIntent)

        assertThat(scenario.result.resultCode).isEqualTo(RESULT_CANCELED)
    }

    @Test
    fun intentSkipsUnrecognisedPermission() {
        val permissions = arrayOf(READ_EXERCISE, WRITE_SLEEP, "permission")
        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Sleep")).check(matches(isDisplayed()))

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(arrayOf(READ_EXERCISE, WRITE_SLEEP))
        val expectedResults = intArrayOf(PERMISSION_DENIED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun intentSkipsGrantedPermissions() {
        val startActivityIntent = getPermissionScreenIntent(fitnessPermissions)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE))

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Exercise")).check(doesNotExist())
        onView(withText("Sleep")).check(matches(isDisplayed()))
        onView(withText("Active calories burned")).check(matches(isDisplayed()))
        onView(withText("Skin temperature")).check(matches(isDisplayed()))

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(fitnessPermissions)
        val expectedResults =
            intArrayOf(PERMISSION_GRANTED, PERMISSION_DENIED, PERMISSION_DENIED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun intentSkipsHiddenFitnessPermissions() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(false)
        val permissions =
            arrayOf(READ_EXERCISE, READ_SLEEP, READ_SKIN_TEMPERATURE, WRITE_ACTIVE_CALORIES_BURNED)
        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Sleep")).check(matches(isDisplayed()))
        onView(withText("Skin temperature")).check(doesNotExist())
        onView(withText("Active calories burned")).check(matches(isDisplayed()))

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(arrayOf(READ_EXERCISE, READ_SLEEP, WRITE_ACTIVE_CALORIES_BURNED))
        val expectedResults = intArrayOf(PERMISSION_DENIED, PERMISSION_DENIED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun intentSkipsHiddenAdditionalPermissions() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(false)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE))
        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES)).isEmpty()
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS)).isEmpty()
    }

    @Test
    fun intentSkipsHiddenMedicalPermissions() {
        val startActivityIntent = getPermissionScreenIntent(medicalPermissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES)).isEmpty()
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS)).isEmpty()
    }

    @Test
    fun emptyRequest_sendsEmptyResultOk() {
        val startActivityIntent = getPermissionScreenIntent(emptyArray())

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES)).isEmpty()
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS)).isEmpty()
    }

    @Test
    fun intentDisplaysFitnessPermissions() {
        val startActivityIntent = getPermissionScreenIntent(fitnessPermissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow $TEST_APP_NAME to access Health Connect?"))
            .check(matches(isDisplayed()))
        onView(withText("Choose data you want this app to read or write to Health Connect"))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "If you give read access, the app can read new data and data from the past 30 days"))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "You can learn how $TEST_APP_NAME handles your data in the developer's privacy policy"))
            .check(matches(isDisplayed()))

        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Sleep")).check(matches(isDisplayed()))
        onView(withText("Active calories burned")).check(matches(isDisplayed()))

        onView(withText("Allow")).check(matches(isDisplayed()))
        onView(withText("Don't allow")).check(matches(isDisplayed()))
    }

    @Test
    fun intentDisplaysAdditionalPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE, READ_SLEEP))

        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow additional access for $TEST_APP_NAME?"))
            .check(matches(isDisplayed()))
        onView(withText("Access data in the background")).check(matches(isDisplayed()))
        onView(withText("Access past data")).check(matches(isDisplayed()))
        onView(withText("Allow")).check(matches(isDisplayed()))
        onView(withText("Don't allow")).check(matches(isDisplayed()))
    }

    @Test
    fun intentDisplaysBackgroundReadPermission() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE, READ_SLEEP))

        val permissions = arrayOf(READ_HEALTH_DATA_IN_BACKGROUND)
        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow $TEST_APP_NAME to access data in the background?"))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "If you allow, this app can access Health Connect data when you're not using the app."))
            .check(matches(isDisplayed()))
        onView(withText("Allow")).check(matches(isDisplayed()))
        onView(withText("Don't allow")).check(matches(isDisplayed()))
    }

    @Test
    fun intentDisplaysHistoryReadPermission() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE, READ_SLEEP))

        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY)
        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow $TEST_APP_NAME to access past data?")).check(matches(isDisplayed()))
        onView(
                withText(
                    "If you allow, this app can access Health Connect data added before October 20, 2022."))
            .check(matches(isDisplayed()))
        onView(withText("Allow")).check(matches(isDisplayed()))
        onView(withText("Don't allow")).check(matches(isDisplayed()))
    }

    @Test
    fun requestFitnessPermissions_alreadyGrantedRestDenied_sendsResultOk() {
        val startActivityIntent = getPermissionScreenIntent(fitnessPermissions)
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(true)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE, READ_SLEEP))

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(fitnessPermissions)
        val expectedResults =
            intArrayOf(PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_DENIED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun whenReadPermissionsAlreadyGranted_requestAdditionalPermissions_sendsResultOk() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)
        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE, READ_SLEEP))

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Access data in the background")).check(matches(isDisplayed()))
        onView(withText("Access data in the background")).perform(click())

        onView(withText("Access past data")).check(matches(isDisplayed()))
        onView(withText("Access past data")).perform(click())
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        val expectedResults = intArrayOf(PERMISSION_GRANTED, PERMISSION_GRANTED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun whenReadPermissionsNotAlreadyGranted_requestAdditionalPermissions_sendsResultCancelled() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf())

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        val expectedResults = intArrayOf(PERMISSION_DENIED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun grantOneFitnessPermission_sendsResultOk() {
        val startActivityIntent = getPermissionScreenIntent(fitnessPermissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Exercise")).perform(click())

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(fitnessPermissions)
        val expectedResults =
            intArrayOf(PERMISSION_GRANTED, PERMISSION_DENIED, PERMISSION_DENIED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun grantOneMedicalPermission_sendsResultOk() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)
        val startActivityIntent = getPermissionScreenIntent(medicalPermissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Immunization")).check(matches(isDisplayed()))
        onView(withText("Immunization")).perform(click())

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(medicalPermissions)
        val expectedResults = intArrayOf(PERMISSION_GRANTED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun requestMedicalAndFitnessPermissions_flagDisabled_onlyShowFitness() {
        val startActivityIntent = getPermissionScreenIntent(fitnessAndMedicalPermissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())

        Espresso.onIdle()
        onView(withText("Exercise")).check(matches(isDisplayed()))
        onView(withText("Exercise")).perform(click())
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(arrayOf(READ_EXERCISE))
        val expectedResults = intArrayOf(PERMISSION_GRANTED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun requestMedicalAndAdditionalPermissions_showBoth() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)
        val startActivityIntent = getPermissionScreenIntent(medicalAndAdditionalPermissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Immunization")).check(matches(isDisplayed()))
        onView(withText("Immunization")).perform(click())
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }
        Espresso.onIdle()
        onView(withText("Allow $TEST_APP_NAME to access data in the background?"))
            .check(matches(isDisplayed()))
        onView(withText("Allow")).perform(click())

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(medicalAndAdditionalPermissions)
        val expectedResults = intArrayOf(PERMISSION_GRANTED, PERMISSION_GRANTED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun requestMedicalAndAdditionalPermissions_backgroundAndHistory_showBoth() {
        val permissions =
            arrayOf(
                READ_MEDICAL_RESOURCES_IMMUNIZATION,
                READ_HEALTH_DATA_IN_BACKGROUND,
                READ_HEALTH_DATA_HISTORY)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)
        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Immunization")).check(matches(isDisplayed()))
        onView(withText("Immunization")).perform(click())
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }
        Espresso.onIdle()
        onView(withText("Access data in the background")).perform(click())
        onView(withText("Allow")).perform(click())

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        val expectedResults = intArrayOf(PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun requestDataTypeAndAdditionalPermissions_showBothScreens() {
        val startActivityIntent = getPermissionScreenIntent(fitnessAndAdditionalPermissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Sleep")).check(matches(isDisplayed()))
        onView(withText("Sleep")).perform(click())
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }
        Espresso.onIdle()
        onView(withText("Allow $TEST_APP_NAME to access data in the background?"))
            .check(matches(isDisplayed()))
        onView(withText("Allow")).perform(click())

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(fitnessAndAdditionalPermissions)
        val expectedResults = intArrayOf(PERMISSION_GRANTED, PERMISSION_GRANTED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun requestDataTypeAndAdditionalPermissions_backgroundAndHistory_showBothScreens() {
        val permissions =
            arrayOf(WRITE_SLEEP, READ_HEALTH_DATA_IN_BACKGROUND, READ_HEALTH_DATA_HISTORY)
        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        onView(withId(androidx.preference.R.id.recycler_view))
            .perform(scrollToLastPosition<RecyclerView.ViewHolder>())
        Espresso.onIdle()

        onView(withText("Sleep")).check(matches(isDisplayed()))
        onView(withText("Sleep")).perform(click())
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }
        Espresso.onIdle()
        onView(withText("Access data in the background")).perform(click())
        onView(withText("Allow")).perform(click())

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        val expectedResults = intArrayOf(PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun clickOnCancel_deniesAllFitnessPermissions_finishesActivity() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.dont_allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        val expectedResults = intArrayOf(PERMISSION_DENIED, PERMISSION_DENIED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun clickOnCancel_deniesAllMedicalPermissions_finishesActivity() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)
        val startActivityIntent = getPermissionScreenIntent(medicalPermissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.dont_allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData

        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(medicalPermissions)
        val expectedResults = intArrayOf(PERMISSION_DENIED, PERMISSION_DENIED)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(expectedResults)
    }

    @Test
    fun whenOnePermissionUserFixed_finishesActivity_sendsResultOk() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(true)
        val startActivityIntent = getPermissionScreenIntent(fitnessPermissions)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE))

        val permissionFlags =
            mapOf(
                READ_EXERCISE to PERMISSION_GRANTED,
                READ_SLEEP to PERMISSION_DENIED,
                WRITE_SKIN_TEMPERATURE to PackageManager.FLAG_PERMISSION_USER_FIXED,
                WRITE_ACTIVE_CALORIES_BURNED to PERMISSION_DENIED)
        permissionManager.setHealthPermissionFlags(TEST_APP_PACKAGE_NAME, permissionFlags)

        val firstScenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        assertThat(firstScenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val firstReturnedIntent = firstScenario.result.resultData

        assertThat(firstReturnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(fitnessPermissions)
        assertThat(firstReturnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED, PERMISSION_DENIED, PERMISSION_DENIED, PERMISSION_DENIED))
    }

    private fun getPermissionScreenIntent(permissions: Array<String>): Intent =
        Intent.makeMainActivity(ComponentName(context, PermissionsActivity::class.java))
            .putExtra(EXTRA_REQUEST_PERMISSIONS_NAMES, permissions)
            .putExtra(EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME)
            .addFlags(FLAG_ACTIVITY_NEW_TASK)
            .addFlags(FLAG_ACTIVITY_CLEAR_TASK)
}
