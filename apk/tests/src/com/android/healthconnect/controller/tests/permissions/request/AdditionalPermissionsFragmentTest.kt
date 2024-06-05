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

import android.health.connect.HealthPermissions
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.permissions.data.HealthPermission.AdditionalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.permissions.request.AdditionalPermissionsFragment
import com.android.healthconnect.controller.permissions.request.AdditionalPermissionsInfo
import com.android.healthconnect.controller.permissions.request.RequestPermissionViewModel
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.any
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.toPermissionsList
import com.android.healthconnect.controller.tests.utils.toggleAnimation
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.RequestBackgroundReadPermissionElement
import com.android.healthconnect.controller.utils.logging.RequestCombinedAdditionalPermissionsElement
import com.android.healthconnect.controller.utils.logging.RequestHistoryReadPermissionElement
import com.android.healthconnect.controller.utils.logging.UIAction
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
@HiltAndroidTest
class AdditionalPermissionsFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue
    val viewModel: RequestPermissionViewModel = Mockito.mock(RequestPermissionViewModel::class.java)
    @BindValue
    val healthConnectLogger: HealthConnectLogger = Mockito.mock(HealthConnectLogger::class.java)

    @Before
    fun setup() {
        hiltRule.inject()

        whenever(viewModel.grantedAdditionalPermissions).then {
            MutableLiveData(emptySet<AdditionalPermission>())
        }
        whenever(viewModel.loadAccessDate(org.mockito.kotlin.any())).thenReturn(NOW)
        toggleAnimation(false)
    }

    @After
    fun teardown() {
        Mockito.reset(healthConnectLogger)
        toggleAnimation(true)
    }

    @Test
    fun displaysCombinedAdditionalPermissions() {
        Mockito.`when`(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    listOf(
                        AdditionalPermission.READ_HEALTH_DATA_HISTORY,
                        AdditionalPermission.READ_HEALTH_DATA_IN_BACKGROUND),
                    AppMetadata(TEST_APP_PACKAGE_NAME, TEST_APP_NAME, null)))
        }

        launchFragment<AdditionalPermissionsFragment>(bundleOf())

        onView(withText("Allow additional access for $TEST_APP_NAME?"))
            .check(matches(isDisplayed()))
        onView(withText("Access data in the background")).check(matches(isDisplayed()))
        onView(
                withText(
                    "Allow this app to access Health Connect data when you're not using the app"))
            .check(matches(isDisplayed()))

        onView(withText("Access past data")).check(matches(isDisplayed()))
        onView(
            withText("Allow this app to access Health Connect data added before October 20, 2022"))

        onView(withText("Don't allow")).check(matches(isDisplayed()))
        onView(withText("Allow")).check(matches(isDisplayed()))

        verify(healthConnectLogger).setPageId(PageName.REQUEST_COMBINED_ADDITIONAL_PERMISSIONS_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger)
            .logImpression(
                RequestCombinedAdditionalPermissionsElement
                    .ALLOW_COMBINED_ADDITIONAL_PERMISSIONS_BUTTON)
        verify(healthConnectLogger)
            .logImpression(
                RequestCombinedAdditionalPermissionsElement
                    .CANCEL_COMBINED_ADDITIONAL_PERMISSIONS_BUTTON)
        verify(healthConnectLogger)
            .logImpression(RequestCombinedAdditionalPermissionsElement.BACKGROUND_READ_BUTTON)
        verify(healthConnectLogger)
            .logImpression(RequestCombinedAdditionalPermissionsElement.HISTORY_READ_BUTTON)
    }

    @Test
    fun displaysBackgroundReadPermission() {
        Mockito.`when`(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    listOf(AdditionalPermission.READ_HEALTH_DATA_IN_BACKGROUND),
                    AppMetadata(TEST_APP_PACKAGE_NAME, TEST_APP_NAME, null)))
        }

        launchFragment<AdditionalPermissionsFragment>(bundleOf())

        onView(withText("Allow $TEST_APP_NAME to access data in the background?"))
            .check(matches(isDisplayed()))

        onView(
                withText(
                    "If you allow, this app can access Health Connect data when you're not using the app."))
            .check(matches(isDisplayed()))

        onView(withText("Don't allow")).check(matches(isDisplayed()))
        onView(withText("Allow")).check(matches(isDisplayed()))

        verify(healthConnectLogger).setPageId(PageName.REQUEST_BACKGROUND_READ_PERMISSION_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger)
            .logImpression(RequestBackgroundReadPermissionElement.ALLOW_BACKGROUND_READ_BUTTON)
        verify(healthConnectLogger)
            .logImpression(RequestBackgroundReadPermissionElement.CANCEL_BACKGROUND_READ_BUTTON)
    }

    @Test
    fun displaysHistoryReadPermission() {
        Mockito.`when`(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    listOf(AdditionalPermission.READ_HEALTH_DATA_HISTORY),
                    AppMetadata(TEST_APP_PACKAGE_NAME, TEST_APP_NAME, null)))
        }

        launchFragment<AdditionalPermissionsFragment>(bundleOf())

        onView(withText("Allow $TEST_APP_NAME to access past data?")).check(matches(isDisplayed()))

        onView(
                withText(
                    "If you allow, this app can access Health Connect data added before October 20, 2022."))
            .check(matches(isDisplayed()))

        onView(withText("Don't allow")).check(matches(isDisplayed()))
        onView(withText("Allow")).check(matches(isDisplayed()))

        verify(healthConnectLogger).setPageId(PageName.REQUEST_HISTORY_READ_PERMISSION_PAGE)
        verify(healthConnectLogger).logPageImpression()
        verify(healthConnectLogger)
            .logImpression(RequestHistoryReadPermissionElement.ALLOW_HISTORY_READ_BUTTON)
        verify(healthConnectLogger)
            .logImpression(RequestHistoryReadPermissionElement.CANCEL_HISTORY_READ_BUTTON)
    }

    @Test
    fun toggleOn_updatesAdditionalPermission() {
        Mockito.`when`(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    listOf(
                        AdditionalPermission.READ_HEALTH_DATA_HISTORY,
                        AdditionalPermission.READ_HEALTH_DATA_IN_BACKGROUND),
                    AppMetadata(TEST_APP_PACKAGE_NAME, TEST_APP_NAME, null)))
        }

        launchFragment<AdditionalPermissionsFragment>(bundleOf())
        onView(withText("Access data in the background")).check(matches(isDisplayed()))
        onView(withText("Access data in the background")).perform(click())

        Mockito.verify(viewModel)
            .updateHealthPermission(any(AdditionalPermission::class.java), Matchers.eq(true))

        verify(healthConnectLogger)
            .logInteraction(
                RequestCombinedAdditionalPermissionsElement.BACKGROUND_READ_BUTTON,
                UIAction.ACTION_TOGGLE_ON)
    }

    @Test
    fun toggleOff_updatesAdditionalPermission() {
        Mockito.`when`(viewModel.additionalPermissionsInfo).then {
            MutableLiveData(
                AdditionalPermissionsInfo(
                    listOf(
                        AdditionalPermission.READ_HEALTH_DATA_HISTORY,
                        AdditionalPermission.READ_HEALTH_DATA_IN_BACKGROUND),
                    AppMetadata(TEST_APP_PACKAGE_NAME, TEST_APP_NAME, null)))
        }

        Mockito.`when`(viewModel.grantedAdditionalPermissions).then {
            MutableLiveData(setOf(AdditionalPermission.READ_HEALTH_DATA_HISTORY))
        }
        Mockito.`when`(
                viewModel.isPermissionLocallyGranted(
                    eq(AdditionalPermission.READ_HEALTH_DATA_HISTORY)))
            .thenReturn(true)

        launchFragment<AdditionalPermissionsFragment>(bundleOf())
        onView(withText("Access past data")).check(matches(isDisplayed()))
        onView(withText("Access past data")).perform(click())

        Mockito.verify(viewModel)
            .updateHealthPermission(any(AdditionalPermission::class.java), Matchers.eq(false))

        verify(healthConnectLogger)
            .logInteraction(
                RequestCombinedAdditionalPermissionsElement.HISTORY_READ_BUTTON,
                UIAction.ACTION_TOGGLE_OFF)
    }

    @Test
    fun allowButton_noAdditionalPermissionsSelected_isDisabled() {
        val permissions =
            arrayOf(
                HealthPermissions.READ_HEALTH_DATA_HISTORY,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)

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
                fromPermissionString(HealthPermissions.READ_HEALTH_DATA_HISTORY) to
                    PermissionState.GRANTED,
                fromPermissionString(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND) to
                    PermissionState.GRANTED,
            )
        }
        whenever(viewModel.grantedAdditionalPermissions).then {
            MutableLiveData(emptySet<AdditionalPermission>())
        }

        launchFragment<AdditionalPermissionsFragment>(bundleOf())

        onView(withText("Allow")).check(matches(ViewMatchers.isNotEnabled()))
    }

    @Test
    fun allowButton_additionalPermissionsSelected_isEnabled() {
        val permissions =
            arrayOf(
                HealthPermissions.READ_HEALTH_DATA_HISTORY,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)

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

        whenever(viewModel.getPermissionGrants()).then {
            mapOf(
                fromPermissionString(HealthPermissions.READ_HEALTH_DATA_HISTORY) to
                    PermissionState.GRANTED,
                fromPermissionString(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND) to
                    PermissionState.NOT_GRANTED)
        }
        whenever(viewModel.grantedAdditionalPermissions).then {
            MutableLiveData(setOf(AdditionalPermission.READ_HEALTH_DATA_HISTORY))
        }

        launchFragment<AdditionalPermissionsFragment>(bundleOf())

        onView(withText("Allow")).check(matches(ViewMatchers.isEnabled()))
    }

    @Test
    fun allowButton_medicalRequestConcluded_isEnabled() {
        val permissions =
            arrayOf(
                HealthPermissions.READ_HEALTH_DATA_HISTORY,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)

        whenever(viewModel.isMedicalPermissionRequestConcluded()).thenReturn(true)

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
                fromPermissionString(HealthPermissions.READ_HEALTH_DATA_HISTORY) to
                    PermissionState.GRANTED,
                fromPermissionString(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND) to
                    PermissionState.NOT_GRANTED)
        }
        whenever(viewModel.grantedAdditionalPermissions).then {
            MutableLiveData(setOf(AdditionalPermission.READ_HEALTH_DATA_HISTORY))
        }

        launchFragment<AdditionalPermissionsFragment>(bundleOf())

        onView(withText("Allow")).check(matches(ViewMatchers.isEnabled()))
    }
}
