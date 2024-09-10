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
package com.android.healthconnect.controller.tests.permissions.additionalaccess

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions.READ_EXERCISE
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_HISTORY
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND
import android.health.connect.HealthPermissions.WRITE_DISTANCE
import com.android.healthconnect.controller.permissions.additionalaccess.AdditionalAccessViewModel
import com.android.healthconnect.controller.permissions.additionalaccess.GetAdditionalPermissionUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.LoadDeclaredHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.LoadExerciseRoutePermissionUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.SetHealthPermissionsUserFixedFlagValueUseCase
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class AdditionalAccessViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    private val featureUtils: FeatureUtils = FakeFeatureUtils()
    private val grantHealthPermissionUseCase: GrantHealthPermissionUseCase = mock()
    private val revokeHealthPermissionUseCase: RevokeHealthPermissionUseCase = mock()
    private val setHealthPermissionsUserFixedFlagValueUseCase:
        SetHealthPermissionsUserFixedFlagValueUseCase =
        mock()
    private val getAdditionalPermissionUseCase: GetAdditionalPermissionUseCase = mock()
    private val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase = mock()
    private val loadAccessDateUseCase: LoadAccessDateUseCase = mock()
    private val loadDeclaredHealthPermissionUseCase: LoadDeclaredHealthPermissionUseCase = mock()
    private val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase = mock()

    @Inject lateinit var appInfoReader: AppInfoReader

    private lateinit var additionalAccessViewModel: AdditionalAccessViewModel
    private lateinit var loadExerciseRoutePermissionUseCase: LoadExerciseRoutePermissionUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        loadExerciseRoutePermissionUseCase =
            LoadExerciseRoutePermissionUseCase(
                loadDeclaredHealthPermissionUseCase,
                getHealthPermissionsFlagsUseCase,
                getGrantedHealthPermissionsUseCase,
                testDispatcher)
        additionalAccessViewModel =
            AdditionalAccessViewModel(
                featureUtils,
                appInfoReader,
                loadExerciseRoutePermissionUseCase,
                grantHealthPermissionUseCase,
                revokeHealthPermissionUseCase,
                setHealthPermissionsUserFixedFlagValueUseCase,
                getAdditionalPermissionUseCase,
                getGrantedHealthPermissionsUseCase,
                loadAccessDateUseCase)

        whenever(loadAccessDateUseCase.invoke(anyString())).thenReturn(NOW)

        (featureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        featureUtils.setIsBackgroundReadEnabled(true)

        whenever(loadDeclaredHealthPermissionUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    READ_EXERCISE,
                    READ_EXERCISE_ROUTES,
                    READ_HEALTH_DATA_HISTORY,
                    READ_HEALTH_DATA_IN_BACKGROUND,
                    WRITE_DISTANCE))

        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).then {
            mapOf(
                READ_EXERCISE_ROUTES to PackageManager.FLAG_PERMISSION_USER_SET,
                READ_EXERCISE to PackageManager.FLAG_PERMISSION_USER_SET)
        }
        whenever(getAdditionalPermissionUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    READ_EXERCISE_ROUTES,
                    READ_HEALTH_DATA_HISTORY,
                    READ_HEALTH_DATA_IN_BACKGROUND,
                ))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()

        (featureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(false)
        featureUtils.setIsBackgroundReadEnabled(false)
    }

    @Test
    fun loadAccessDate_returnsCorrectDate() {
        val accessDate = NOW
        whenever(loadAccessDateUseCase.invoke(TEST_APP_PACKAGE_NAME)).thenReturn(accessDate)
        val result = additionalAccessViewModel.loadAccessDate(TEST_APP_PACKAGE_NAME)
        assertThat(result).isEqualTo(accessDate)
    }

    @Test
    fun loadAdditionalAccessPreferences_loadsAllAdditionalAccess() = runTest {
        whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(READ_EXERCISE, READ_EXERCISE_ROUTES, READ_HEALTH_DATA_HISTORY))

        val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()

        additionalAccessViewModel.additionalAccessState.observeForever(
            additionalAccessStateObserver)
        additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val additionalAccessResult = additionalAccessStateObserver.getLastValue()

        assertThat(additionalAccessResult.exercisePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
        assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
        assertThat(additionalAccessResult.historyReadUIState)
            .isEqualTo(
                AdditionalAccessViewModel.AdditionalPermissionState(
                    isDeclared = true, isEnabled = true, isGranted = true))
        assertThat(additionalAccessResult.backgroundReadUIState)
            .isEqualTo(
                AdditionalAccessViewModel.AdditionalPermissionState(
                    isDeclared = true, isEnabled = true, isGranted = false))
        assertThat(additionalAccessResult.isValid()).isTrue()
        assertThat(additionalAccessResult.showFooter()).isFalse()
        assertThat(
                additionalAccessResult.isAdditionalPermissionDisabled(
                    additionalAccessResult.backgroundReadUIState))
            .isFalse()
        assertThat(
                additionalAccessResult.isAdditionalPermissionDisabled(
                    additionalAccessResult.historyReadUIState))
            .isFalse()
    }

    @Test
    fun loadAdditionalAccessPreferences_historyReadNotEnabled_doesNotReturnHistoryRead() = runTest {
        (featureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(false)
        whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(listOf(READ_EXERCISE, READ_EXERCISE_ROUTES, READ_HEALTH_DATA_HISTORY))
        val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()

        additionalAccessViewModel.additionalAccessState.observeForever(
            additionalAccessStateObserver)
        additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val additionalAccessResult = additionalAccessStateObserver.getLastValue()

        assertThat(additionalAccessResult.exercisePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
        assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
        assertThat(additionalAccessResult.historyReadUIState)
            .isEqualTo(
                AdditionalAccessViewModel.AdditionalPermissionState(
                    isDeclared = false, isEnabled = false, isGranted = false))
        assertThat(additionalAccessResult.backgroundReadUIState)
            .isEqualTo(
                AdditionalAccessViewModel.AdditionalPermissionState(
                    isDeclared = true, isEnabled = true, isGranted = false))
        assertThat(additionalAccessResult.isValid()).isTrue()
        assertThat(additionalAccessResult.showFooter()).isFalse()
        assertThat(
                additionalAccessResult.isAdditionalPermissionDisabled(
                    additionalAccessResult.backgroundReadUIState))
            .isFalse()
        assertThat(
                additionalAccessResult.isAdditionalPermissionDisabled(
                    additionalAccessResult.historyReadUIState))
            .isFalse()
    }

    @Test
    fun loadAdditionalAccessPreferences_backgroundReadNotEnabled_doesNotReturnBackgroundRead() =
        runTest {
            (featureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(false)
            whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
                .thenReturn(listOf(READ_EXERCISE, READ_EXERCISE_ROUTES, READ_HEALTH_DATA_HISTORY))

            val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()

            additionalAccessViewModel.additionalAccessState.observeForever(
                additionalAccessStateObserver)
            additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
            advanceUntilIdle()

            val additionalAccessResult = additionalAccessStateObserver.getLastValue()

            assertThat(additionalAccessResult.exercisePermissionUIState)
                .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
            assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
                .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
            assertThat(additionalAccessResult.historyReadUIState)
                .isEqualTo(
                    AdditionalAccessViewModel.AdditionalPermissionState(
                        isDeclared = true, isEnabled = true, isGranted = true))
            assertThat(additionalAccessResult.backgroundReadUIState)
                .isEqualTo(
                    AdditionalAccessViewModel.AdditionalPermissionState(
                        isDeclared = false, isEnabled = false, isGranted = false))
            assertThat(additionalAccessResult.isValid()).isTrue()
            assertThat(additionalAccessResult.showFooter()).isFalse()
            assertThat(
                    additionalAccessResult.isAdditionalPermissionDisabled(
                        additionalAccessResult.backgroundReadUIState))
                .isFalse()
            assertThat(
                    additionalAccessResult.isAdditionalPermissionDisabled(
                        additionalAccessResult.historyReadUIState))
                .isFalse()
        }

    @Test
    fun loadAdditionalAccessPreferences_noReadPermissionsGranted_additionalPermissionsDisabled() =
        runTest {
            whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
                .thenReturn(
                    listOf(
                        WRITE_DISTANCE,
                    ))

            val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()

            additionalAccessViewModel.additionalAccessState.observeForever(
                additionalAccessStateObserver)
            additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
            advanceUntilIdle()

            val additionalAccessResult = additionalAccessStateObserver.getLastValue()

            assertThat(additionalAccessResult.exercisePermissionUIState)
                .isEqualTo(PermissionUiState.ASK_EVERY_TIME)
            assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
                .isEqualTo(PermissionUiState.ASK_EVERY_TIME)
            assertThat(additionalAccessResult.historyReadUIState)
                .isEqualTo(
                    AdditionalAccessViewModel.AdditionalPermissionState(
                        isDeclared = true, isEnabled = false, isGranted = false))
            assertThat(additionalAccessResult.backgroundReadUIState)
                .isEqualTo(
                    AdditionalAccessViewModel.AdditionalPermissionState(
                        isDeclared = true, isEnabled = false, isGranted = false))
            assertThat(additionalAccessResult.isValid()).isTrue()
            assertThat(additionalAccessResult.showFooter()).isTrue()
            assertThat(
                    additionalAccessResult.isAdditionalPermissionDisabled(
                        additionalAccessResult.backgroundReadUIState))
                .isTrue()
            assertThat(
                    additionalAccessResult.isAdditionalPermissionDisabled(
                        additionalAccessResult.historyReadUIState))
                .isTrue()
        }

    @Test
    fun updateExerciseRouteState_toAlwaysAllow_noExercisePermission_showsDialog() = runTest {
        whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    WRITE_DISTANCE,
                ))

        val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()
        val showEnableExerciseEventObserver =
            TestObserver<AdditionalAccessViewModel.EnableExerciseDialogEvent>()

        additionalAccessViewModel.additionalAccessState.observeForever(
            additionalAccessStateObserver)
        additionalAccessViewModel.showEnableExerciseEvent.observeForever(
            showEnableExerciseEventObserver)
        additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val additionalAccessResult = additionalAccessStateObserver.getLastValue()

        assertThat(additionalAccessResult.exercisePermissionUIState)
            .isEqualTo(PermissionUiState.ASK_EVERY_TIME)
        assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
            .isEqualTo(PermissionUiState.ASK_EVERY_TIME)

        additionalAccessViewModel.updateExerciseRouteState(
            TEST_APP_PACKAGE_NAME, PermissionUiState.ALWAYS_ALLOW)
        advanceUntilIdle()

        val enableExerciseEventResult = showEnableExerciseEventObserver.getLastValue()
        assertThat(enableExerciseEventResult.shouldShowDialog).isTrue()
    }

    @Test
    fun updateExerciseRouteState_toAlwaysAllow_withExercisePermission_grantsPermission() = runTest {
        whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    READ_EXERCISE,
                    WRITE_DISTANCE,
                ))

        val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()
        val showEnableExerciseEventObserver =
            TestObserver<AdditionalAccessViewModel.EnableExerciseDialogEvent>()

        additionalAccessViewModel.additionalAccessState.observeForever(
            additionalAccessStateObserver)
        additionalAccessViewModel.showEnableExerciseEvent.observeForever(
            showEnableExerciseEventObserver)
        additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val additionalAccessResult = additionalAccessStateObserver.getLastValue()

        assertThat(additionalAccessResult.exercisePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
        assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
            .isEqualTo(PermissionUiState.ASK_EVERY_TIME)

        additionalAccessViewModel.updateExerciseRouteState(
            TEST_APP_PACKAGE_NAME, PermissionUiState.ALWAYS_ALLOW)
        advanceUntilIdle()

        val enableExerciseEventResult = showEnableExerciseEventObserver.getLastValue()
        assertThat(enableExerciseEventResult.shouldShowDialog).isFalse()
        verify(grantHealthPermissionUseCase).invoke(TEST_APP_PACKAGE_NAME, READ_EXERCISE_ROUTES)
    }

    @Test
    fun updateExerciseRouteState_toAskEveryTime_fromAlwaysAllow_revokesPermission() = runTest {
        whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    READ_EXERCISE,
                    READ_EXERCISE_ROUTES,
                    WRITE_DISTANCE,
                ))

        val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()
        val showEnableExerciseEventObserver =
            TestObserver<AdditionalAccessViewModel.EnableExerciseDialogEvent>()

        additionalAccessViewModel.additionalAccessState.observeForever(
            additionalAccessStateObserver)
        additionalAccessViewModel.showEnableExerciseEvent.observeForever(
            showEnableExerciseEventObserver)
        additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val additionalAccessResult = additionalAccessStateObserver.getLastValue()

        assertThat(additionalAccessResult.exercisePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
        assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)

        additionalAccessViewModel.updateExerciseRouteState(
            TEST_APP_PACKAGE_NAME, PermissionUiState.ASK_EVERY_TIME)
        advanceUntilIdle()

        val enableExerciseEventResult = showEnableExerciseEventObserver.getLastValue()
        assertThat(enableExerciseEventResult.shouldShowDialog).isFalse()
        verify(revokeHealthPermissionUseCase).invoke(TEST_APP_PACKAGE_NAME, READ_EXERCISE_ROUTES)
    }

    @Test
    fun updateExerciseRouteState_toAskEveryTime_fromNeverAllow_setsFlag() = runTest {
        whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    READ_EXERCISE,
                    WRITE_DISTANCE,
                ))
        whenever(getHealthPermissionsFlagsUseCase.invoke(any(), any())).then {
            mapOf(
                READ_EXERCISE_ROUTES to PackageManager.FLAG_PERMISSION_USER_FIXED,
                READ_EXERCISE to PackageManager.FLAG_PERMISSION_USER_SET)
        }

        val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()
        val showEnableExerciseEventObserver =
            TestObserver<AdditionalAccessViewModel.EnableExerciseDialogEvent>()

        additionalAccessViewModel.additionalAccessState.observeForever(
            additionalAccessStateObserver)
        additionalAccessViewModel.showEnableExerciseEvent.observeForever(
            showEnableExerciseEventObserver)
        additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val additionalAccessResult = additionalAccessStateObserver.getLastValue()

        assertThat(additionalAccessResult.exercisePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
        assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
            .isEqualTo(PermissionUiState.NEVER_ALLOW)

        additionalAccessViewModel.updateExerciseRouteState(
            TEST_APP_PACKAGE_NAME, PermissionUiState.ASK_EVERY_TIME)
        advanceUntilIdle()

        val enableExerciseEventResult = showEnableExerciseEventObserver.getLastValue()
        assertThat(enableExerciseEventResult.shouldShowDialog).isFalse()
        verify(setHealthPermissionsUserFixedFlagValueUseCase)
            .invoke(TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE_ROUTES), false)
    }

    @Test
    fun updateExerciseRouteState_toNeverAllow_revokesPermission_setsFlag() = runTest {
        whenever(getGrantedHealthPermissionsUseCase.invoke(TEST_APP_PACKAGE_NAME))
            .thenReturn(
                listOf(
                    READ_EXERCISE,
                    READ_EXERCISE_ROUTES,
                    WRITE_DISTANCE,
                ))

        val additionalAccessStateObserver = TestObserver<AdditionalAccessViewModel.State>()
        val showEnableExerciseEventObserver =
            TestObserver<AdditionalAccessViewModel.EnableExerciseDialogEvent>()

        additionalAccessViewModel.additionalAccessState.observeForever(
            additionalAccessStateObserver)
        additionalAccessViewModel.showEnableExerciseEvent.observeForever(
            showEnableExerciseEventObserver)
        additionalAccessViewModel.loadAdditionalAccessPreferences(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val additionalAccessResult = additionalAccessStateObserver.getLastValue()

        assertThat(additionalAccessResult.exercisePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)
        assertThat(additionalAccessResult.exerciseRoutePermissionUIState)
            .isEqualTo(PermissionUiState.ALWAYS_ALLOW)

        additionalAccessViewModel.updateExerciseRouteState(
            TEST_APP_PACKAGE_NAME, PermissionUiState.NEVER_ALLOW)
        advanceUntilIdle()

        val enableExerciseEventResult = showEnableExerciseEventObserver.getLastValue()
        assertThat(enableExerciseEventResult.shouldShowDialog).isFalse()
        verify(revokeHealthPermissionUseCase).invoke(TEST_APP_PACKAGE_NAME, READ_EXERCISE_ROUTES)
        verify(setHealthPermissionsUserFixedFlagValueUseCase)
            .invoke(TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE_ROUTES), true)
    }

    @Test
    fun enableExercisePermission_invokesGrantUseCase_forExerciseAndExerciseRoutes() {
        additionalAccessViewModel.enableExercisePermission(TEST_APP_PACKAGE_NAME)
        verify(grantHealthPermissionUseCase).invoke(TEST_APP_PACKAGE_NAME, READ_EXERCISE_ROUTES)
        verify(grantHealthPermissionUseCase).invoke(TEST_APP_PACKAGE_NAME, READ_EXERCISE)
    }

    @Test
    fun hideExercisePermissionRequestDialog_hidesDialog() = runTest {
        val showEnableExerciseEventObserver =
            TestObserver<AdditionalAccessViewModel.EnableExerciseDialogEvent>()
        additionalAccessViewModel.showEnableExerciseEvent.observeForever(
            showEnableExerciseEventObserver)

        additionalAccessViewModel.hideExercisePermissionRequestDialog()
        advanceUntilIdle()

        assertThat(showEnableExerciseEventObserver.getLastValue().shouldShowDialog).isFalse()
    }

    @Test
    fun updatePermission_grants() {
        additionalAccessViewModel.updatePermission(TEST_APP_PACKAGE_NAME, READ_EXERCISE, true)
        verify(grantHealthPermissionUseCase).invoke(TEST_APP_PACKAGE_NAME, READ_EXERCISE)
    }

    @Test
    fun updatePermission_revokes() {
        additionalAccessViewModel.updatePermission(TEST_APP_PACKAGE_NAME, WRITE_DISTANCE, false)
        verify(revokeHealthPermissionUseCase).invoke(TEST_APP_PACKAGE_NAME, WRITE_DISTANCE)
    }
}
