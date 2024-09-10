/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.tests.permissions.connectedapps

import android.content.Intent
import android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET
import android.health.connect.HealthPermissions
import androidx.core.os.bundleOf
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.app.ConnectedAppFragment
import com.android.healthconnect.controller.service.HealthPermissionManagerModule
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.isAbove
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import javax.inject.Inject
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@HiltAndroidTest
@UninstallModules(HealthPermissionManagerModule::class)
class MockedConnectedAppFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Inject lateinit var fakeFeatureUtils: FeatureUtils
    @BindValue val healthPermissionManager: HealthPermissionManager = mock()

    @Before
    fun setup() {
        hiltRule.inject()
        (fakeFeatureUtils as FakeFeatureUtils).setIsNewInformationArchitectureEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsExerciseRoutesReadAllEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        toggleAnimation(false)
    }

    @After
    fun tearDown() {
        toggleAnimation(true)
    }

    @Test
    fun exerciseRoutesAlwaysAllow_revokeExercise_notLastReadPermission_showsDialog() {
        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    HealthPermissions.READ_EXERCISE,
                    HealthPermissions.READ_SLEEP,
                    HealthPermissions.READ_EXERCISE_ROUTES,
                    HealthPermissions.READ_HEALTH_DATA_HISTORY))

        launchFragment<ConnectedAppFragment>(
            bundleOf(
                Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                Constants.EXTRA_APP_NAME to TEST_APP_NAME))

        onView(allOf(withText("Exercise"), isAbove(withText("Allowed to write")))).perform(click())

        // check for dialog
        onView(withText("Disable both data types?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "$TEST_APP_NAME requires exercise access in order for exercise routes to be enabled"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Yes")).inRoot(isDialog()).perform(click())

        verify(healthPermissionManager)
            .revokeHealthPermission(TEST_APP_PACKAGE_NAME, HealthPermissions.READ_EXERCISE)
        verify(healthPermissionManager)
            .revokeHealthPermission(TEST_APP_PACKAGE_NAME, HealthPermissions.READ_EXERCISE_ROUTES)
        verify(healthPermissionManager, never())
            .revokeHealthPermission(
                TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEALTH_DATA_HISTORY)
    }

    @Test
    fun exerciseRoutesAlwaysAllow_revokeExercise_lastReadPermission_showsDialog() {
        var revokeCalled = false
        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenAnswer {
                if (revokeCalled) {
                    listOf(
                        HealthPermissions.READ_EXERCISE, HealthPermissions.READ_HEALTH_DATA_HISTORY)
                } else {
                    listOf(
                        HealthPermissions.READ_EXERCISE,
                        HealthPermissions.READ_EXERCISE_ROUTES,
                        HealthPermissions.READ_HEALTH_DATA_HISTORY)
                }
            }

        // After revoking ER, do not return it in granted permissions anymore as this will trigger
        // calling the revoke API again
        whenever(
                healthPermissionManager.revokeHealthPermission(
                    TEST_APP_PACKAGE_NAME, HealthPermissions.READ_EXERCISE_ROUTES))
            .then {
                revokeCalled = true
                null
            }

        launchFragment<ConnectedAppFragment>(
            bundleOf(
                Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                Constants.EXTRA_APP_NAME to TEST_APP_NAME))

        onView(allOf(withText("Exercise"), isAbove(withText("Allowed to write")))).perform(click())

        // check for dialog
        onView(withText("Disable both data types?"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(
                withText(
                    "$TEST_APP_NAME requires exercise access in order for exercise routes to be enabled"))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText("Yes")).inRoot(isDialog()).perform(click())

        verify(healthPermissionManager)
            .revokeHealthPermission(TEST_APP_PACKAGE_NAME, HealthPermissions.READ_EXERCISE)
        verify(healthPermissionManager)
            .revokeHealthPermission(TEST_APP_PACKAGE_NAME, HealthPermissions.READ_EXERCISE_ROUTES)
        verify(healthPermissionManager)
            .revokeHealthPermission(
                TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEALTH_DATA_HISTORY)
    }

    @Test
    fun exerciseRoutesAskEveryTime_revokeExercise_lastReadPermission_doesNotShowDialog() {
        whenever(healthPermissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(HealthPermissions.READ_EXERCISE, HealthPermissions.READ_HEALTH_DATA_HISTORY))

        whenever(
                healthPermissionManager.getHealthPermissionsFlags(
                    TEST_APP_PACKAGE_NAME,
                    listOf(
                        HealthPermissions.READ_EXERCISE_ROUTES, HealthPermissions.READ_EXERCISE)))
            .thenReturn(
                mapOf(
                    HealthPermissions.READ_EXERCISE_ROUTES to FLAG_PERMISSION_USER_SET,
                    HealthPermissions.READ_EXERCISE to FLAG_PERMISSION_USER_SET))

        launchFragment<ConnectedAppFragment>(
            bundleOf(
                Intent.EXTRA_PACKAGE_NAME to TEST_APP_PACKAGE_NAME,
                Constants.EXTRA_APP_NAME to TEST_APP_NAME))

        onView(allOf(withText("Exercise"), isAbove(withText("Allowed to write")))).perform(click())

        // check for dialog
        onView(withText("Disable both data types?")).check(doesNotExist())

        verify(healthPermissionManager)
            .revokeHealthPermission(TEST_APP_PACKAGE_NAME, HealthPermissions.READ_EXERCISE)
        verify(healthPermissionManager, never())
            .revokeHealthPermission(TEST_APP_PACKAGE_NAME, HealthPermissions.READ_EXERCISE_ROUTES)
        verify(healthPermissionManager)
            .revokeHealthPermission(
                TEST_APP_PACKAGE_NAME, HealthPermissions.READ_HEALTH_DATA_HISTORY)
    }
}
