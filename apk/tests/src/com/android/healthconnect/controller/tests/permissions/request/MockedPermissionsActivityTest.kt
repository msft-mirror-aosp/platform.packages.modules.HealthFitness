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
package com.android.healthconnect.controller.tests.permissions.request

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES
import android.content.pm.PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.health.connect.HealthPermissions.READ_HEART_RATE
import android.health.connect.HealthPermissions.READ_STEPS
import android.health.connect.HealthPermissions.WRITE_DISTANCE
import android.health.connect.HealthPermissions.WRITE_EXERCISE
import android.widget.Button
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario.launchActivityForResult
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
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
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.permissions.request.PermissionsActivity
import com.android.healthconnect.controller.permissions.request.RequestPermissionViewModel
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.showOnboarding
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.anyString

@HiltAndroidTest
class MockedPermissionsActivityTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: RequestPermissionViewModel = Mockito.mock(RequestPermissionViewModel::class.java)
    @BindValue
    val migrationViewModel: MigrationViewModel = Mockito.mock(MigrationViewModel::class.java)

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
        whenever(viewModel.permissionsList).then { MutableLiveData(permissionsList) }
        whenever(viewModel.grantedPermissions).then { MutableLiveData(permissionsList.toSet()) }
        whenever(viewModel.allPermissionsGranted).then { MutableLiveData(true) }
        whenever(viewModel.appMetadata).then {
            MutableLiveData(
                AppMetadata(
                    TEST_APP_PACKAGE_NAME,
                    TEST_APP_NAME,
                    context.getDrawable(R.drawable.health_connect_logo)))
        }
        whenever(migrationViewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        showOnboarding(context, false)
    }

    @Test
    fun sendsOkResult_requestWithPermissions() {
        whenever(viewModel.request(anyString())).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.GRANTED)
        }
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_GRANTED))
    }

    @Test
    fun allowButton_noPermissionsSelected_isDisabled() {
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(emptySet<HealthPermission>())
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow")).check(matches(isNotEnabled()))
    }

    @Test
    fun allowButton_permissionsSelected_isEnabled() {
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(fromPermissionString(READ_STEPS)))
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Allow")).check(matches(isEnabled()))
    }

    @Test
    fun sendsOkResult_requestWithPermissionsSomeDenied() {
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)
        whenever(viewModel.request(anyString())).then {
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

        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_DENIED, PERMISSION_GRANTED))
    }

    @Test
    fun sendsOkResult_requestWithPermissionsSomeWithError() {
        whenever(viewModel.request(anyString())).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.ERROR)
        }
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        scenario.onActivity { activity: PermissionsActivity ->
            activity.findViewById<Button>(R.id.allow).callOnClick()
        }

        assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.getResult().getResultData()
        assertThat(returnedIntent.getStringArrayExtra(EXTRA_REQUEST_PERMISSIONS_NAMES))
            .isEqualTo(permissions)
        assertThat(returnedIntent.getIntArrayExtra(EXTRA_REQUEST_PERMISSIONS_RESULTS))
            .isEqualTo(
                intArrayOf(
                    PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_GRANTED, PERMISSION_DENIED))
    }

    @Test
    fun allPermissionsGranted_finishesActivity() {
        whenever(viewModel.request(anyString())).then {
            mapOf(
                fromPermissionString(READ_STEPS) to PermissionState.GRANTED,
                fromPermissionString(READ_HEART_RATE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_DISTANCE) to PermissionState.GRANTED,
                fromPermissionString(WRITE_EXERCISE) to PermissionState.GRANTED)
        }
        whenever(viewModel.permissionsList).then {
            MutableLiveData<List<HealthPermission>>(emptyList())
        }
        val permissions = arrayOf(READ_STEPS, READ_HEART_RATE, WRITE_DISTANCE, WRITE_EXERCISE)

        val startActivityIntent = getPermissionScreenIntent(permissions)

        val scenario = launchActivityForResult<PermissionsActivity>(startActivityIntent)

        assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        val returnedIntent = scenario.getResult().getResultData()
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
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(fromPermissionString(READ_STEPS)))
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Health Connect integration in progress"))
        onView(
                withText(
                    "Health Connect is being integrated with the Android system.\n\nYou'll get a notification when the process is complete and you can use $TEST_APP_NAME with Health Connect."))
            .inRoot(RootMatchers.isDialog())
            .check(matches(ViewMatchers.isDisplayed()))
        onView(withText("Got it"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(ViewMatchers.isDisplayed()))

        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())

        // TODO (b/322495982) replace with idling resource
        //        Thread.sleep(2000)
        //        Assert.assertEquals(Lifecycle.State.DESTROYED, scenario.state)
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
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(fromPermissionString(READ_STEPS)))
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(withText("Health Connect restore in progress"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(ViewMatchers.isDisplayed()))
        onView(
                withText(
                    "Health Connect is restoring data and permissions. This may take some time to complete."))
            .inRoot(RootMatchers.isDialog())
            .check(matches(ViewMatchers.isDisplayed()))
        onView(withText("Got it"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(ViewMatchers.isDisplayed()))

        onView(withText("Got it")).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())

        // TODO (b/322495982) replace with idling resource
        //        Thread.sleep(2000)
        //        Assert.assertEquals(Lifecycle.State.DESTROYED, scenario.state)
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
        whenever(viewModel.grantedPermissions).then {
            MutableLiveData(setOf(fromPermissionString(READ_STEPS)))
        }

        val startActivityIntent = getPermissionScreenIntent(permissions)

        launchActivityForResult<PermissionsActivity>(startActivityIntent)

        onView(
                withText(
                    "Health Connect is ready to be integrated with your Android system. If you give $TEST_APP_NAME access now, some features may not work until integration is complete."))
            .inRoot(RootMatchers.isDialog())
            .check(matches(ViewMatchers.isDisplayed()))
        // TODO (b/322495982) check navigation to Migration activity
        onView(withText("Start integration"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(ViewMatchers.isDisplayed()))
        onView(withText("Continue"))
            .inRoot(RootMatchers.isDialog())
            .check(matches(ViewMatchers.isDisplayed()))

        onView(withText("Continue")).inRoot(RootMatchers.isDialog()).perform(ViewActions.click())
        onView(withText("Continue")).check(ViewAssertions.doesNotExist())
    }

    private fun getPermissionScreenIntent(permissions: Array<String>): Intent =
        Intent.makeMainActivity(ComponentName(context, PermissionsActivity::class.java))
            .putExtra(EXTRA_REQUEST_PERMISSIONS_NAMES, permissions)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_APP_PACKAGE_NAME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
}
