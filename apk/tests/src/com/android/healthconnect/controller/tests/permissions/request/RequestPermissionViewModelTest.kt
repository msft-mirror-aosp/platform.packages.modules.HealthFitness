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
import android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND
import android.health.connect.HealthPermissions.READ_HEART_RATE
import android.health.connect.HealthPermissions.READ_PLANNED_EXERCISE
import android.health.connect.HealthPermissions.READ_SKIN_TEMPERATURE
import android.health.connect.HealthPermissions.READ_STEPS
import android.health.connect.HealthPermissions.WRITE_PLANNED_EXERCISE
import android.health.connect.HealthPermissions.WRITE_SKIN_TEMPERATURE
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.HealthPermissionManager
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.data.DataTypePermission
import com.android.healthconnect.controller.permissions.data.DataTypePermission.Companion.fromPermissionString
import com.android.healthconnect.controller.permissions.request.RequestPermissionViewModel
import com.android.healthconnect.controller.service.HealthPermissionManagerModule
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.tests.utils.di.FakeHealthPermissionManager
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

@OptIn(ExperimentalCoroutinesApi::class)
@UninstallModules(HealthPermissionManagerModule::class, FeaturesModule::class)
@HiltAndroidTest
class RequestPermissionViewModelTest {

    companion object {
        private val permissions = arrayOf(READ_STEPS, READ_HEART_RATE)
    }

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
    @Inject lateinit var healthPermissionReader: HealthPermissionReader

    lateinit var viewModel: RequestPermissionViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        permissionManager.revokeAllHealthPermissions(TEST_APP_PACKAGE_NAME)
        Dispatchers.setMain(testDispatcher)
        viewModel =
            RequestPermissionViewModel(
                appInfoReader,
                grantHealthPermissionUseCase,
                revokeHealthPermissionUseCase,
                getGrantHealthPermissionUseCase,
                getHealthPermissionsFlagsUseCase,
                healthPermissionReader)
        viewModel.init(TEST_APP_PACKAGE_NAME, permissions)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun init_initAppInfo_initPermissions() = runTest {
        val testObserver = TestObserver<AppMetadata>()
        viewModel.appMetadata.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue().appName).isEqualTo(TEST_APP_NAME)
        assertThat(testObserver.getLastValue().packageName).isEqualTo(TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun init_initPermissions() = runTest {
        val testObserver = TestObserver<List<DataTypePermission>>()
        viewModel.permissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(
                listOf(fromPermissionString(READ_STEPS), fromPermissionString(READ_HEART_RATE)))
    }

    @Test
    fun initPermissions_filtersOutAdditionalPermissions() = runTest {
        viewModel.init(
            TEST_APP_PACKAGE_NAME,
            arrayOf(READ_STEPS, READ_HEART_RATE, READ_HEALTH_DATA_IN_BACKGROUND))
        val testObserver = TestObserver<List<DataTypePermission>>()
        viewModel.permissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(
                listOf(fromPermissionString(READ_STEPS), fromPermissionString(READ_HEART_RATE)))
    }

    @Test
    fun initPermissions_whenPermissionsHidden_filtersOutHiddenPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPlannedExerciseEnabled(false)

        viewModel.init(
            TEST_APP_PACKAGE_NAME,
            arrayOf(
                READ_STEPS,
                READ_HEART_RATE,
                READ_SKIN_TEMPERATURE,
                WRITE_SKIN_TEMPERATURE,
                READ_PLANNED_EXERCISE,
                WRITE_PLANNED_EXERCISE))
        val testObserver = TestObserver<List<DataTypePermission>>()
        viewModel.permissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(
                listOf(fromPermissionString(READ_STEPS), fromPermissionString(READ_HEART_RATE)))
    }

    @Test
    fun initPermissions_whenPermissionsNotHidden_doesNotFilterOutPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPlannedExerciseEnabled(true)

        viewModel.init(
            TEST_APP_PACKAGE_NAME,
            arrayOf(
                READ_STEPS,
                READ_HEART_RATE,
                READ_SKIN_TEMPERATURE,
                WRITE_SKIN_TEMPERATURE,
                READ_PLANNED_EXERCISE,
                WRITE_PLANNED_EXERCISE))
        val testObserver = TestObserver<List<DataTypePermission>>()
        viewModel.permissionsList.observeForever(testObserver)
        advanceUntilIdle()
        assertThat(testObserver.getLastValue())
            .isEqualTo(
                listOf(
                    fromPermissionString(READ_STEPS),
                    fromPermissionString(READ_HEART_RATE),
                    fromPermissionString(READ_SKIN_TEMPERATURE),
                    fromPermissionString(WRITE_SKIN_TEMPERATURE),
                    fromPermissionString(READ_PLANNED_EXERCISE),
                    fromPermissionString(WRITE_PLANNED_EXERCISE)))
    }

    @Test
    fun isPermissionGranted_permissionGranted_returnsTrue() = runTest {
        val readStepsPermission = fromPermissionString(READ_STEPS)
        viewModel.updatePermission(readStepsPermission, grant = true)

        assertThat(viewModel.isPermissionLocallyGranted(readStepsPermission)).isTrue()
    }

    @Test
    fun isPermissionGranted_permissionRevoked_returnsFalse() = runTest {
        val readStepsPermission = fromPermissionString(READ_STEPS)
        viewModel.updatePermission(readStepsPermission, grant = false)

        assertThat(viewModel.isPermissionLocallyGranted(fromPermissionString(READ_STEPS))).isFalse()
    }

    @Test
    fun updatePermission_grant_updatesGrantedPermissions() = runTest {
        val readStepsPermission = fromPermissionString(READ_STEPS)
        val testObserver = TestObserver<Set<DataTypePermission>>()
        viewModel.grantedPermissions.observeForever(testObserver)
        viewModel.updatePermission(readStepsPermission, grant = true)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).contains(readStepsPermission)
    }

    @Test
    fun updatePermission_revoke_updatesGrantedPermissions() = runTest {
        val readStepsPermission = fromPermissionString(READ_STEPS)
        val testObserver = TestObserver<Set<DataTypePermission>>()
        viewModel.grantedPermissions.observeForever(testObserver)
        viewModel.updatePermission(readStepsPermission, grant = false)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).doesNotContain(readStepsPermission)
    }

    @Test
    fun updatePermissions_grant_updatesGrantedPermissions() = runTest {
        val testObserver = TestObserver<Set<DataTypePermission>>()
        viewModel.grantedPermissions.observeForever(testObserver)
        viewModel.updatePermissions(grant = true)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue())
            .containsExactly(
                fromPermissionString(READ_STEPS), fromPermissionString(READ_HEART_RATE))
    }

    @Test
    fun updatePermissions_revoke_updatesGrantedPermissions() = runTest {
        val testObserver = TestObserver<Set<DataTypePermission>>()
        viewModel.grantedPermissions.observeForever(testObserver)
        viewModel.updatePermissions(grant = false)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEmpty()
    }

    @Test
    fun request_allPermissionsGranted_updatesPermissionState() {
        viewModel.updatePermissions(true)

        viewModel.request(TEST_APP_PACKAGE_NAME)

        assertThat(permissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(READ_STEPS, READ_HEART_RATE)
    }

    @Test
    fun request_allPermissionsRevoked_updatesPermissionState() {
        viewModel.updatePermissions(false)

        viewModel.request(TEST_APP_PACKAGE_NAME)

        assertThat(permissionManager.getGrantedHealthPermissions(TEST_APP_PACKAGE_NAME)).isEmpty()
    }

    @Test
    fun isAnyPermissionUserFixed_whenNoPermissionUserFixed_returnsFalse() {
        val permissionFlags =
            mapOf(
                READ_STEPS to PackageManager.FLAG_PERMISSION_USER_SET,
                READ_HEART_RATE to PackageManager.FLAG_PERMISSION_GRANTED_BY_DEFAULT)
        (permissionManager as FakeHealthPermissionManager).setHealthPermissionFlags(
            TEST_APP_PACKAGE_NAME, permissionFlags)

        val result =
            viewModel.isAnyPermissionUserFixed(
                TEST_APP_PACKAGE_NAME, arrayOf(READ_STEPS, READ_HEART_RATE))
        assertThat(result).isFalse()
    }

    @Test
    fun isAnyPermissionUserFixed_whenAtLeastOnePermissionIsUserFixed_returnsTrue() {
        val permissionFlags =
            mapOf(
                READ_STEPS to PackageManager.FLAG_PERMISSION_USER_SET,
                READ_HEART_RATE to PackageManager.FLAG_PERMISSION_USER_FIXED)
        (permissionManager as FakeHealthPermissionManager).setHealthPermissionFlags(
            TEST_APP_PACKAGE_NAME, permissionFlags)

        val result =
            viewModel.isAnyPermissionUserFixed(
                TEST_APP_PACKAGE_NAME, arrayOf(READ_STEPS, READ_HEART_RATE))
        assertThat(result).isTrue()
    }
}
