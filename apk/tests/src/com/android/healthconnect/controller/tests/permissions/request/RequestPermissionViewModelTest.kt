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
 */

package com.android.healthconnect.controller.tests.permissions.request

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions.READ_EXERCISE
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_HISTORY
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND
import android.health.connect.HealthPermissions.READ_HEART_RATE
import android.health.connect.HealthPermissions.READ_PLANNED_EXERCISE
import android.health.connect.HealthPermissions.READ_SKIN_TEMPERATURE
import android.health.connect.HealthPermissions.READ_SLEEP
import android.health.connect.HealthPermissions.READ_STEPS
import android.health.connect.HealthPermissions.WRITE_DISTANCE
import android.health.connect.HealthPermissions.WRITE_EXERCISE
import android.health.connect.HealthPermissions.WRITE_PLANNED_EXERCISE
import android.health.connect.HealthPermissions.WRITE_SKIN_TEMPERATURE
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.AdditionalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.data.PermissionState
import com.android.healthconnect.controller.permissions.request.RequestPermissionViewModel
import com.android.healthconnect.controller.service.HealthPermissionManagerModule
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.di.FakeHealthPermissionManager
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.FeatureUtils
import com.android.healthconnect.controller.utils.FeaturesModule
import com.google.common.truth.Truth.*
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(HealthPermissionManagerModule::class, FeaturesModule::class)
@HiltAndroidTest
class RequestPermissionViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    @BindValue val permissionManager: HealthPermissionManager = FakeHealthPermissionManager()
    @Inject lateinit var fakeFeatureUtils: FeatureUtils

    @Inject lateinit var appInfoReader: AppInfoReader
    @Inject lateinit var grantHealthPermissionUseCase: GrantHealthPermissionUseCase
    @Inject lateinit var revokeHealthPermissionUseCase: RevokeHealthPermissionUseCase
    @Inject lateinit var getGrantHealthPermissionUseCase: GetGrantedHealthPermissionsUseCase
    @Inject lateinit var getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase
    @BindValue var loadAccessDateUseCase: LoadAccessDateUseCase = mock()

    @Inject lateinit var healthPermissionReader: HealthPermissionReader
    lateinit var viewModel: RequestPermissionViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        permissionManager.revokeAllHealthPermissions(TEST_APP_PACKAGE_NAME)
        Dispatchers.setMain(testDispatcher)
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)
        viewModel =
            RequestPermissionViewModel(
                appInfoReader,
                grantHealthPermissionUseCase,
                revokeHealthPermissionUseCase,
                getGrantHealthPermissionUseCase,
                getHealthPermissionsFlagsUseCase,
                loadAccessDateUseCase,
                healthPermissionReader)
        whenever(loadAccessDateUseCase.invoke(eq(TEST_APP_PACKAGE_NAME))).thenReturn(NOW)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(false)
    }

    @Test
    fun init_loadsAppInfo() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val testObserver = TestObserver<AppMetadata>()
        viewModel.appMetadata.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue().appName).isEqualTo(TEST_APP_NAME)
        assertThat(testObserver.getLastValue().packageName).isEqualTo(TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun init_loadsHealthPermissions() = runTest {
        val permissions =
            arrayOf(
                READ_EXERCISE,
                READ_SLEEP,
                WRITE_EXERCISE,
                READ_HEALTH_DATA_IN_BACKGROUND,
                READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val fitnessPermissionObserver = TestObserver<List<FitnessPermission>>()
        viewModel.fitnessPermissionsList.observeForever(fitnessPermissionObserver)

        val additionalPermissionObserver = TestObserver<List<AdditionalPermission>>()
        viewModel.additionalPermissionsList.observeForever(additionalPermissionObserver)

        val healthPermissionObserver = TestObserver<List<HealthPermission>>()
        viewModel.healthPermissionsList.observeForever(healthPermissionObserver)

        advanceUntilIdle()
        assertThat(fitnessPermissionObserver.getLastValue())
            .isEqualTo(
                listOf(
                    fromPermissionString(READ_EXERCISE),
                    fromPermissionString(READ_SLEEP),
                    fromPermissionString(WRITE_EXERCISE)))
        assertThat(additionalPermissionObserver.getLastValue())
            .isEqualTo(
                listOf(
                    fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND),
                    fromPermissionString(READ_HEALTH_DATA_HISTORY)))
        assertThat(healthPermissionObserver.getLastValue())
            .isEqualTo(
                listOf(
                    fromPermissionString(READ_EXERCISE),
                    fromPermissionString(READ_SLEEP),
                    fromPermissionString(WRITE_EXERCISE),
                    fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND),
                    fromPermissionString(READ_HEALTH_DATA_HISTORY)))
    }

    @Test
    fun initPermissions_filtersOutAdditionalPermissions() = runTest {
        viewModel.init(
            TEST_APP_PACKAGE_NAME,
            arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_IN_BACKGROUND))
        val testObserver = TestObserver<List<FitnessPermission>>()
        viewModel.fitnessPermissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(
                listOf(fromPermissionString(READ_EXERCISE), fromPermissionString(READ_SLEEP)))
    }

    @Test
    fun initPermissions_filtersOutUndeclaredPermissions() = runTest {
        viewModel.init(
            TEST_APP_PACKAGE_NAME,
            arrayOf(READ_STEPS, READ_HEART_RATE, READ_HEALTH_DATA_IN_BACKGROUND))
        val testObserver = TestObserver<List<HealthPermission>>()
        viewModel.healthPermissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(listOf(fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND)))
    }

    @Test
    fun initPermissions_whenPermissionsHidden_filtersOutHiddenPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPlannedExerciseEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(false)

        viewModel.init(
            TEST_APP_PACKAGE_NAME,
            arrayOf(
                READ_EXERCISE,
                READ_SLEEP,
                READ_SKIN_TEMPERATURE,
                WRITE_SKIN_TEMPERATURE,
                READ_PLANNED_EXERCISE,
                WRITE_PLANNED_EXERCISE,
                READ_HEALTH_DATA_HISTORY,
                READ_HEALTH_DATA_IN_BACKGROUND))

        val testObserver = TestObserver<List<HealthPermission>>()
        viewModel.healthPermissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(
                listOf(
                    fromPermissionString(READ_EXERCISE),
                    fromPermissionString(READ_SLEEP),
                    fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND)))
    }

    @Test
    fun initPermissions_whenPermissionsNotHidden_doesNotFilterOutPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPlannedExerciseEnabled(true)

        viewModel.init(
            TEST_APP_PACKAGE_NAME,
            arrayOf(
                READ_SLEEP,
                READ_EXERCISE,
                READ_SKIN_TEMPERATURE,
                WRITE_SKIN_TEMPERATURE,
                READ_PLANNED_EXERCISE,
                WRITE_PLANNED_EXERCISE))
        val testObserver = TestObserver<List<FitnessPermission>>()
        viewModel.fitnessPermissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(
                listOf(
                    fromPermissionString(READ_SLEEP),
                    fromPermissionString(READ_EXERCISE),
                    fromPermissionString(READ_SKIN_TEMPERATURE),
                    fromPermissionString(WRITE_SKIN_TEMPERATURE),
                    fromPermissionString(READ_PLANNED_EXERCISE),
                    fromPermissionString(WRITE_PLANNED_EXERCISE)))
    }

    @Test
    fun initPermissions_filtersOutUnrecognisedPermissions() = runTest {
        viewModel.init(TEST_APP_PACKAGE_NAME, arrayOf(READ_EXERCISE, READ_SLEEP, "permission"))

        val testObserver = TestObserver<List<HealthPermission>>()
        viewModel.healthPermissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(
                listOf(fromPermissionString(READ_EXERCISE), fromPermissionString(READ_SLEEP)))
    }

    @Test
    fun initPermissions_filtersOutGrantedPermissions() = runTest {
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE, READ_HEALTH_DATA_IN_BACKGROUND))
        viewModel.init(
            TEST_APP_PACKAGE_NAME,
            arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_IN_BACKGROUND))

        val testObserver = TestObserver<List<HealthPermission>>()
        viewModel.healthPermissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue()).isEqualTo(listOf(fromPermissionString(READ_SLEEP)))
    }

    @Test
    fun isPermissionLocallyGranted_fitnessPermissionGranted_returnsTrue() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val readExercisePermission = fromPermissionString(READ_EXERCISE)
        viewModel.updateHealthPermission(readExercisePermission, grant = true)

        assertThat(viewModel.isPermissionLocallyGranted(readExercisePermission)).isTrue()
    }

    @Test
    fun isPermissionLocallyGranted_additionalPermissionGranted_returnsTrue() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val historyReadPermission = fromPermissionString(READ_HEALTH_DATA_HISTORY)
        viewModel.updateHealthPermission(historyReadPermission, grant = true)

        assertThat(viewModel.isPermissionLocallyGranted(historyReadPermission)).isTrue()
    }

    @Test
    fun isPermissionLocallyGranted_fitnessPermissionRevoked_returnsFalse() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val readStepsPermission = fromPermissionString(READ_STEPS)
        viewModel.updateHealthPermission(readStepsPermission, grant = false)

        assertThat(viewModel.isPermissionLocallyGranted(fromPermissionString(READ_EXERCISE)))
            .isFalse()
    }

    @Test
    fun isPermissionLocallyGranted_additionalPermissionRevoked_returnsFalse() = runTest {
        val permissions =
            arrayOf(
                READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val historyReadPermission = fromPermissionString(READ_HEALTH_DATA_HISTORY)
        viewModel.updateHealthPermission(historyReadPermission, grant = false)

        assertThat(viewModel.isPermissionLocallyGranted(historyReadPermission)).isFalse()
    }

    @Test
    fun isPermissionLocallyGranted_onlyOneAdditionalPermission_returnsTrue() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val historyReadPermission = fromPermissionString(READ_HEALTH_DATA_HISTORY)

        assertThat(viewModel.isPermissionLocallyGranted(historyReadPermission)).isTrue()
    }

    @Test
    fun init_anyReadPermissionsGranted_whenReadPermissionGranted_returnsTrue() = runTest {
        val permissions =
            arrayOf(
                READ_EXERCISE,
                READ_SLEEP,
                WRITE_DISTANCE,
                READ_HEALTH_DATA_IN_BACKGROUND,
                READ_HEALTH_DATA_HISTORY)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE))
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        advanceUntilIdle()
        assertThat(viewModel.isAnyReadPermissionGranted()).isTrue()
    }

    @Test
    fun init_anyReadPermissionsGranted_whenNoReadPermissionGranted_returnsFalse() = runTest {
        val permissions =
            arrayOf(
                READ_EXERCISE,
                READ_SLEEP,
                WRITE_EXERCISE,
                READ_HEALTH_DATA_IN_BACKGROUND,
                READ_HEALTH_DATA_HISTORY)
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf())
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        advanceUntilIdle()
        assertThat(viewModel.isAnyReadPermissionGranted()).isFalse()
    }

    @Test
    fun init_isHistoryReadPermissionsGranted_whenHistoryReadPermissionGranted_returnsTrue() =
        runTest {
            val permissions =
                arrayOf(
                    READ_EXERCISE,
                    READ_SLEEP,
                    WRITE_EXERCISE,
                    READ_HEALTH_DATA_IN_BACKGROUND,
                    READ_HEALTH_DATA_HISTORY)
            (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
                TEST_APP_PACKAGE_NAME, listOf(READ_HEALTH_DATA_HISTORY))
            viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

            advanceUntilIdle()
            assertThat(viewModel.isHistoryAccessGranted()).isTrue()
        }

    @Test
    fun init_isHistoryReadPermissionsGranted_whenHistoryReadPermissionNotGranted_returnsFalse() =
        runTest {
            val permissions =
                arrayOf(
                    READ_EXERCISE,
                    READ_SLEEP,
                    WRITE_EXERCISE,
                    READ_HEALTH_DATA_IN_BACKGROUND,
                    READ_HEALTH_DATA_HISTORY)
            (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
                TEST_APP_PACKAGE_NAME, listOf(READ_HEALTH_DATA_IN_BACKGROUND))
            viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

            advanceUntilIdle()
            assertThat(viewModel.isHistoryAccessGranted()).isFalse()
        }

    @Test
    fun setFitnessPermissionsConcluded_correctlySets() = runTest {
        viewModel.setFitnessPermissionRequestConcluded(true)
        assertThat(viewModel.isFitnessPermissionRequestConcluded()).isTrue()

        viewModel.setFitnessPermissionRequestConcluded(false)
        assertThat(viewModel.isFitnessPermissionRequestConcluded()).isFalse()
    }

    @Test
    fun updateHealthPermission_grant_updatesGrantedFitnessPermissions() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val readExercisePermission = fromPermissionString(READ_EXERCISE)
        val testObserver = TestObserver<Set<FitnessPermission>>()
        viewModel.grantedFitnessPermissions.observeForever(testObserver)
        viewModel.updateHealthPermission(readExercisePermission, grant = true)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).contains(readExercisePermission)
    }

    @Test
    fun updateHealthPermission_grant_updatesGrantedAdditionalPermissions() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val historyReadPermission = fromPermissionString(READ_HEALTH_DATA_HISTORY)
        val testObserver = TestObserver<Set<AdditionalPermission>>()
        viewModel.grantedAdditionalPermissions.observeForever(testObserver)
        viewModel.updateHealthPermission(historyReadPermission, grant = true)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).contains(historyReadPermission)
    }

    @Test
    fun updateHealthPermission_revoke_updatesGrantedFitnessPermissions() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val readExercisePermission = fromPermissionString(READ_EXERCISE)
        val testObserver = TestObserver<Set<FitnessPermission>>()
        viewModel.grantedFitnessPermissions.observeForever(testObserver)
        viewModel.updateHealthPermission(readExercisePermission, grant = false)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).doesNotContain(readExercisePermission)
    }

    @Test
    fun updateHealthPermission_revoke_updatesGrantedAdditionalPermissions() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val historyReadPermission = fromPermissionString(READ_HEALTH_DATA_HISTORY)
        val testObserver = TestObserver<Set<AdditionalPermission>>()
        viewModel.grantedAdditionalPermissions.observeForever(testObserver)
        viewModel.updateHealthPermission(historyReadPermission, grant = false)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).doesNotContain(historyReadPermission)
    }

    @Test
    fun updateFitnessPermissions_grant_updatesGrantedFitnessPermissions() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val testObserver = TestObserver<Set<FitnessPermission>>()
        viewModel.grantedFitnessPermissions.observeForever(testObserver)
        viewModel.updateFitnessPermissions(grant = true)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue())
            .containsExactly(fromPermissionString(READ_EXERCISE), fromPermissionString(READ_SLEEP))
    }

    @Test
    fun updateAdditionalPermissions_grant_updatesGrantedAdditionalPermissions() = runTest {
        val permissions =
            arrayOf(
                READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val testObserver = TestObserver<Set<AdditionalPermission>>()
        viewModel.grantedAdditionalPermissions.observeForever(testObserver)
        viewModel.updateAdditionalPermissions(grant = true)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue())
            .containsExactly(
                fromPermissionString(READ_HEALTH_DATA_HISTORY),
                fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND))
    }

    @Test
    fun updateFitnessPermissions_revoke_updatesGrantedFitnessPermissions() = runTest {
        val permissions = arrayOf(READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val testObserver = TestObserver<Set<FitnessPermission>>()
        viewModel.grantedFitnessPermissions.observeForever(testObserver)
        viewModel.updateFitnessPermissions(grant = false)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEmpty()
    }

    @Test
    fun updateAdditionalPermissions_revoke_updatesGrantedAdditionalPermissions() = runTest {
        val permissions =
            arrayOf(
                READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)

        val testObserver = TestObserver<Set<AdditionalPermission>>()
        viewModel.grantedAdditionalPermissions.observeForever(testObserver)
        viewModel.updateAdditionalPermissions(grant = false)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEmpty()
    }

    @Test
    fun requestFitnessPermissions_updatesPermissionState() {
        val permissions =
            arrayOf(
                READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)
        viewModel.updateFitnessPermissions(true)

        viewModel.requestFitnessPermissions(TEST_APP_PACKAGE_NAME)
        assertThat(permissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(READ_EXERCISE, READ_SLEEP)
        assertThat(viewModel.getPermissionGrants())
            .isEqualTo(
                mutableMapOf(
                    fromPermissionString(READ_EXERCISE) to PermissionState.GRANTED,
                    fromPermissionString(READ_SLEEP) to PermissionState.GRANTED,
                    fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.NOT_GRANTED,
                    fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to
                        PermissionState.NOT_GRANTED))
    }

    @Test
    fun requestAdditionalPermissions_updatesPermissionState() {
        val permissions =
            arrayOf(
                READ_EXERCISE, READ_SLEEP, READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)
        viewModel.updateAdditionalPermissions(true)

        viewModel.requestAdditionalPermissions(TEST_APP_PACKAGE_NAME)
        assertThat(permissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(READ_HEALTH_DATA_HISTORY, READ_HEALTH_DATA_IN_BACKGROUND)
        assertThat(viewModel.getPermissionGrants())
            .isEqualTo(
                mutableMapOf(
                    fromPermissionString(READ_EXERCISE) to PermissionState.NOT_GRANTED,
                    fromPermissionString(READ_SLEEP) to PermissionState.NOT_GRANTED,
                    fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.GRANTED,
                    fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to
                        PermissionState.GRANTED))
    }

    @Test
    fun requestAdditionalPermissions_skipsExerciseRoutePermission_updatesPermissionState() {
        (permissionManager as FakeHealthPermissionManager).setGrantedPermissionsForTest(
            TEST_APP_PACKAGE_NAME, listOf(READ_EXERCISE_ROUTES))
        val permissions =
            arrayOf(
                READ_EXERCISE,
                READ_SLEEP,
                READ_HEALTH_DATA_HISTORY,
                READ_HEALTH_DATA_IN_BACKGROUND,
                READ_EXERCISE_ROUTES)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)
        viewModel.updateAdditionalPermissions(false)

        viewModel.requestAdditionalPermissions(TEST_APP_PACKAGE_NAME)
        assertThat(permissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(READ_EXERCISE_ROUTES)
        assertThat(viewModel.getPermissionGrants())
            .isEqualTo(
                mutableMapOf(
                    fromPermissionString(READ_EXERCISE) to PermissionState.NOT_GRANTED,
                    fromPermissionString(READ_SLEEP) to PermissionState.NOT_GRANTED,
                    fromPermissionString(READ_HEALTH_DATA_HISTORY) to PermissionState.NOT_GRANTED,
                    fromPermissionString(READ_HEALTH_DATA_IN_BACKGROUND) to
                        PermissionState.NOT_GRANTED,
                    fromPermissionString(READ_EXERCISE_ROUTES) to PermissionState.GRANTED))
    }

    @Test
    fun loadAccessDate_returnsCorrectAccessDate() {
        assertThat(viewModel.loadAccessDate(TEST_APP_PACKAGE_NAME)).isEqualTo(NOW)
    }

    @Test
    fun isAnyPermissionUserFixed_whenNoPermissionUserFixed_returnsFalse() {
        val permissionFlags =
            mapOf(
                READ_EXERCISE to PackageManager.FLAG_PERMISSION_USER_SET,
                READ_SLEEP to PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT)
        (permissionManager as FakeHealthPermissionManager).setHealthPermissionFlags(
            TEST_APP_PACKAGE_NAME, permissionFlags)

        val result =
            viewModel.isAnyPermissionUserFixed(
                TEST_APP_PACKAGE_NAME, arrayOf(READ_EXERCISE, READ_SLEEP))
        assertThat(result).isFalse()
    }

    @Test
    fun isAnyPermissionUserFixed_whenAtLeastOnePermissionIsUserFixed_returnsTrue() {
        val permissionFlags =
            mapOf(
                READ_EXERCISE to PackageManager.FLAG_PERMISSION_USER_SET,
                READ_SLEEP to PackageManager.FLAG_PERMISSION_USER_FIXED)
        (permissionManager as FakeHealthPermissionManager).setHealthPermissionFlags(
            TEST_APP_PACKAGE_NAME, permissionFlags)

        val result =
            viewModel.isAnyPermissionUserFixed(
                TEST_APP_PACKAGE_NAME, arrayOf(READ_EXERCISE, READ_SLEEP))
        assertThat(result).isTrue()
    }
}
