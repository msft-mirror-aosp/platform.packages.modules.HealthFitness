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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_HISTORY
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND
import android.health.connect.HealthPermissions.READ_HEART_RATE
import android.health.connect.HealthPermissions.READ_STEPS
import android.health.connect.HealthPermissions.WRITE_DISTANCE
import android.health.connect.HealthPermissions.WRITE_EXERCISE
import android.widget.Button
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario.launchActivityForResult
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.WithData
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.AdditionalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.data.HealthPermission.DataTypePermission
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.permissions.request.AdditionalPermissionsInfo
import com.android.healthconnect.controller.permissions.request.PermissionsActivity
import com.android.healthconnect.controller.permissions.request.RequestPermissionViewModel
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.showOnboarding
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.logging.DataRestoreElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.MigrationElement
import com.android.healthconnect.controller.utils.logging.PermissionsElement
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.anyString
import org.mockito.kotlin.anyArray
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify

@HiltAndroidTest
class MockedPermissionsActivityTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: RequestPermissionViewModel = Mockito.mock(RequestPermissionViewModel::class.java)
    @BindValue
    val migrationViewModel: MigrationViewModel = Mockito.mock(MigrationViewModel::class.java)
    @BindValue
    val healthConnectLogger: HealthConnectLogger = Mockito.mock(HealthConnectLogger::class.java)
    @Inject lateinit var fakeFeatureUtils: FeatureUtils

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = getInstrumentation().context
        val permissionsList =
            listOf(
                fromPermissionString(READ_STEPS),
                fromPermissionString(READ_HEART_RATE),
                fromPermissionString(WRITE_DISTANCE),
                fromPermissionString(WRITE_EXERCISE))
        whenever(viewModel.grantedDataTypePermissions).then {
            MutableLiveData(permissionsList.toSet())
        }
        whenever(viewModel.allDataTypePermissionsGranted).then { MutableLiveData(true) }
        whenever(viewModel.appMetadata).then {
            MutableLiveData(
                AppMetadata(
                    TEST_APP_PACKAGE_NAME,
                    TEST_APP_NAME,
                    context.getDrawable(R.drawable.health_connect_logo)))
        }
        whenever(viewModel.isAnyPermissionUserFixed(anyString(), anyArray())).thenReturn(false)
        whenever(viewModel.isAnyReadPermissionGranted()).thenReturn(true)
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        showOnboarding(context, false)
        // disable animations
        toggleAnimation(false)
    }

    @After
    fun teardown() {
        reset(healthConnectLogger)
        // enable animations
        toggleAnimation(true)
    }

    @Test
    fun requestWithDataTypePermissions_sendsResultOk() {
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)

        whenever(viewModel.isHistoryAccessGranted()).thenReturn(false)
        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.GRANTED)
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow $TEST_APP_NAME to access Health Connect?"))
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "If you give read access, the app can read new data and data from the past 30 days"))
            .check(matches(isDisplayed()))

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        verify(healthConnectLogger, atLeast(1))
            .logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
        verify(healthConnectLogger, atLeast(1))
            .logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_GRANTED))
    }

    @Test
    fun requestWithAdditionalPermissions_flagsOff_finishesActivity() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(false)

        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)

        whenever(viewModel.isAnyReadPermissionGranted()).thenReturn(false)

        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.additionalPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    permissions.toPermissionsList().map { it as AdditionalPermission }, TEST_APP))
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.GRANTED,
                fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to PermissionState.GRANTED,
            )
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        Espresso.onIdle()
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun requestWithAdditionalPermissions_noReadPermissionsGranted_sendsResultCancelled() {
        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)

        whenever(viewModel.isAnyReadPermissionGranted()).thenReturn(false)

        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.additionalPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    permissions.toPermissionsList().map { it as AdditionalPermission }, TEST_APP))
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.GRANTED,
                fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to PermissionState.GRANTED,
            )
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    fun requestWithAdditionalPermissions_readPermissionsGranted_sendsResultOk() {
        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)

        whenever(viewModel.isAnyReadPermissionGranted()).thenReturn(true)

        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.additionalPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    permissions.toPermissionsList().map { it as AdditionalPermission }, TEST_APP))
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.GRANTED,
                fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to PermissionState.GRANTED,
            )
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        // TODO here we need other logs
        verify(healthConnectLogger, atLeast(1))
            .logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
        verify(healthConnectLogger, atLeast(1))
            .logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(intArrayOf(PERMISSION_GRANTED, PERMISSION_GRANTED))
    }

    @Test
    fun requestWithAdditionalPermissions_dataTypePermissionsGranted_onRotate_showsCorrectFragment() {
        val permissions = arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)

        whenever(viewModel.isAnyReadPermissionGranted()).thenReturn(true)
        whenever(viewModel.isDataTypePermissionRequestConcluded()).thenReturn(true)
        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.additionalPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    permissions.toPermissionsList().map { it as AdditionalPermission }, TEST_APP))
        }
        whenever(viewModel.grantedAdditionalPermissions).then {
            MutableLiveData(emptySet<AdditionalPermission>())
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.NOT_GRANTED,
                fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to PermissionState.NOT_GRANTED,
            )
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow additional access for $TEST_APP_NAME?"))
            .check(matches(isDisplayed()))
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        Espresso.onIdle()

        onView(withText("Allow additional access for $TEST_APP_NAME?"))
            .check(matches(isDisplayed()))
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        // TODO here we need other logs
        verify(healthConnectLogger, atLeast(1))
            .logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
        verify(healthConnectLogger, atLeast(1))
            .logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(intArrayOf(PERMISSION_DENIED, PERMISSION_DENIED))
    }

    @Test
    fun requestWithCombinedPermissions_noPermissionsGranted_showsBothRequestScreens() {
        val permissions =
            arrayOf(
                READ_STEPS,
                READ_HEART_RATE,
                WRITE_DISTANCE,
                WRITE_EXERCISE,
                READ_HEALTH_DATA_HISTORY,
                READ_HEALTH_DATA_IN_BACKGROUND)
        val healthPermissionsList = permissions.toPermissionsList()
        val dataTypePermissionsList = healthPermissionsList.filterIsInstance<DataTypePermission>()
        val additionalPermissionsList =
            healthPermissionsList.filterIsInstance<AdditionalPermission>()

        whenever(viewModel.isAnyReadPermissionGranted()).thenReturn(true)
        whenever(viewModel.healthPermissionsList).then { MutableLiveData(healthPermissionsList) }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(dataTypePermissionsList)
        }
        whenever(viewModel.additionalPermissionsList).then {
            MutableLiveData(additionalPermissionsList)
        }
        whenever(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(AdditionalPermissionsInfo(additionalPermissionsList, TEST_APP))
        }

        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.GRANTED,
                fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.GRANTED,
                fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to PermissionState.GRANTED)
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow $TEST_APP_NAME to access Health Connect?"))
            .check(matches(isDisplayed()))
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        verify(healthConnectLogger, atLeast(1))
            .logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
        verify(healthConnectLogger, atLeast(1))
            .logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        // Additional permissions
        onView(withText("Allow additional access for $TEST_APP_NAME?"))
            .check(matches(isDisplayed()))
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED))
    }

    // TODO might this be better suited in the not mocked activity?
    @Test
    fun requestWithCombinedPermissions_noPermissionsGranted_denyAdditionalPermissions_sendsResultOk() {
        val permissions =
            arrayOf(
                READ_STEPS,
                READ_HEART_RATE,
                WRITE_DISTANCE,
                WRITE_EXERCISE,
                READ_HEALTH_DATA_HISTORY,
                READ_HEALTH_DATA_IN_BACKGROUND)
        val healthPermissionsList = permissions.toPermissionsList()
        val dataTypePermissionsList = healthPermissionsList.filterIsInstance<DataTypePermission>()
        val additionalPermissionsList =
            healthPermissionsList.filterIsInstance<AdditionalPermission>()

        whenever(viewModel.isAnyReadPermissionGranted()).thenReturn(true)
        whenever(viewModel.healthPermissionsList).then { MutableLiveData(healthPermissionsList) }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(dataTypePermissionsList)
        }
        whenever(viewModel.additionalPermissionsList).then {
            MutableLiveData(additionalPermissionsList)
        }
        whenever(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(AdditionalPermissionsInfo(additionalPermissionsList, TEST_APP))
        }

        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.GRANTED,
                fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.NOT_GRANTED,
                fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to PermissionState.NOT_GRANTED)
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow $TEST_APP_NAME to access Health Connect?"))
            .check(matches(isDisplayed()))
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        verify(healthConnectLogger, atLeast(1))
            .logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
        verify(healthConnectLogger, atLeast(1))
            .logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        // Additional permissions
        onView(withText("Allow additional access for $TEST_APP_NAME?"))
            .check(matches(isDisplayed()))
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.dont_allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_DENIED,
                    PERMISSION_DENIED))
    }

    @Test
    fun requestWithCombinedPermissions_dataTypePermissionsAlreadyGranted_showsAdditionalPermissions() {
        val permissions =
            arrayOf(
                READ_STEPS,
                READ_HEART_RATE,
                WRITE_DISTANCE,
                WRITE_EXERCISE,
                READ_HEALTH_DATA_HISTORY,
                READ_HEALTH_DATA_IN_BACKGROUND)
        val notGrantedPermissions =
            arrayOf(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        val healthPermissionsList = notGrantedPermissions.toPermissionsList()
        val dataTypePermissionsList = healthPermissionsList.filterIsInstance<DataTypePermission>()
        val additionalPermissionsList =
            healthPermissionsList.filterIsInstance<AdditionalPermission>()

        whenever(viewModel.isAnyReadPermissionGranted()).thenReturn(true)
        whenever(viewModel.healthPermissionsList).then { MutableLiveData(healthPermissionsList) }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(dataTypePermissionsList)
        }
        whenever(viewModel.additionalPermissionsList).then {
            MutableLiveData(additionalPermissionsList)
        }
        whenever(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(AdditionalPermissionsInfo(additionalPermissionsList, TEST_APP))
        }

        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.GRANTED,
                fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.GRANTED,
                fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to PermissionState.NOT_GRANTED)
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        // This should show additional permissions only
        onView(withText("Allow additional access for $TEST_APP_NAME?"))
            .check(matches(isDisplayed()))
        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        verify(healthConnectLogger, atLeast(1))
            .logImpression(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)
        verify(healthConnectLogger, atLeast(1))
            .logInteraction(PermissionsElement.ALLOW_PERMISSIONS_BUTTON)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_GRANTED,
                    PERMISSION_DENIED))
    }

    @Test
    fun sendsOkResult_requestWithDataTypePermissionsSomeDenied() {
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)
        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.NOT_GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.GRANTED,
            )
        }
        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_DENIED, PERMISSION_GRANTED))
    }

    @Test
    fun sendsOkResult_requestWithNoDataTypePermissionsGranted() {
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)
        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.NOT_GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.NOT_GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.NOT_GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.NOT_GRANTED,
            )
        }
        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.dont_allow).callOnClick()
        }

        verify(viewModel).updateDataTypePermissions(false)
        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_DENIED, PERMISSION_DENIED, PERMISSION_DENIED, PERMISSION_DENIED))
    }

    @Test
    fun sendsOkResult_requestWithPermissionsSomeWithError() {
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)

        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.ERROR)
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_DENIED))
    }

    @Test
    fun allPermissionsGranted_finishesActivity() {
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)

        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(emptyList<HealthPermission>())
        }
        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.GRANTED)
        }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData<List<DataTypePermission>>(emptyList())
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.result.resultData
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_GRANTED))
    }

    @Test
    fun intent_migrationInProgress_shoesMigrationInProgressDialog() {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IN_PROGRESS,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IN_PROGRESS,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)
        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.grantedDataTypePermissions).then {
            MutableLiveData(setOf(fromPermissionString(READ_STEPS)))
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Health Connect integration in progress"))
        onView(
                withText(
                    "Health Connect is being integrated with the Android system.\n\nYou'll get a notification when the process is complete and you can use $TEST_APP_NAME with Health Connect."))
            .inRoot(isDialog())
            .check(matches(ViewMatchers.isDisplayed()))
        onView(withText("Got it")).inRoot(isDialog()).check(matches(ViewMatchers.isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(isDialog()).perform(click())
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_IN_PROGRESS_DIALOG_BUTTON)

        // Needed to makes sure activity has finished
        Thread.sleep(2_000)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun intent_restoreInProgress_showsRestoreInProgressDialog() {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IDLE,
                dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)
        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.grantedDataTypePermissions).then {
            MutableLiveData(setOf(fromPermissionString(READ_STEPS)))
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Health Connect restore in progress"))
            .inRoot(isDialog())
            .check(matches(ViewMatchers.isDisplayed()))
        onView(
                withText(
                    "Health Connect is restoring data and permissions. This may take some time to complete."))
            .inRoot(isDialog())
            .check(matches(ViewMatchers.isDisplayed()))
        onView(withText("Got it")).inRoot(isDialog()).check(matches(ViewMatchers.isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_BUTTON)

        onView(withText("Got it")).inRoot(isDialog()).perform(click())
        verify(healthConnectLogger)
            .logInteraction(DataRestoreElement.RESTORE_IN_PROGRESS_DIALOG_BUTTON)

        // Needed to makes sure activity has finished
        Thread.sleep(2_000)
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test
    fun intent_migrationPending_showsMigrationPendingDialog() {
        whenever(migrationViewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)
        whenever(viewModel.healthPermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.dataTypePermissionsList).then {
            MutableLiveData(permissions.toPermissionsList())
        }
        whenever(viewModel.grantedDataTypePermissions).then {
            MutableLiveData(setOf(fromPermissionString(READ_STEPS)))
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(
                withText(
                    "Health Connect is ready to be integrated with your Android system. If you give $TEST_APP_NAME access now, some features may not work until integration is complete."))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        // TODO (b/322495982) check navigation to Migration activity
        onView(withText("Start integration")).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText("Continue")).inRoot(isDialog()).check(matches(isDisplayed()))
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CONTAINER)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CONTINUE_BUTTON)
        verify(healthConnectLogger)
            .logImpression(MigrationElement.MIGRATION_PENDING_DIALOG_CANCEL_BUTTON)

        onView(withText("Continue")).inRoot(isDialog()).perform(click())
        onView(withText("Continue")).check(doesNotExist())
        verify(healthConnectLogger)
            .logInteraction(MigrationElement.MIGRATION_PENDING_DIALOG_CONTINUE_BUTTON)
    }

    private fun getPermissionScreenIntent(permissions: Array<String>): Intent =
        Intent.makeMainActivity(ComponentName(context, PermissionsActivity::class.java))
            .putExtra(EXTRA_REQUEST_PERMISSIONS_NAMES, permissions)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

    private fun Array<String>.toPermissionsList(): List<HealthPermission> {
        return this.map { fromPermissionString(it) }.toList()
    }
}
