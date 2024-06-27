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
package com.android.healthconnect.controller.tests.permissions.connectedapps

import android.health.connect.TimeInstantRangeFilter
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.api.DeleteAppDataUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState
import com.android.healthconnect.controller.permissions.additionalaccess.PermissionUiState
import com.android.healthconnect.controller.permissions.api.GrantHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.api.LoadAccessDateUseCase
import com.android.healthconnect.controller.permissions.api.RevokeAllHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.RevokeHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.app.AppPermissionViewModel
import com.android.healthconnect.controller.permissions.app.LoadAppPermissionsStatusUseCase
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.MedicalPermission
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.permissions.data.PermissionsAccessType
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TEST_APP_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.di.FakeGetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.tests.utils.di.FakeLoadExerciseRoute
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
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class AppPermissionViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    private val healthPermissionReader: HealthPermissionReader = mock()
    private val getGrantedHealthPermissionsUseCase = FakeGetGrantedHealthPermissionsUseCase()
    private val loadAccessDateUseCase: LoadAccessDateUseCase = mock()
    private val deleteAppDateUseCase: DeleteAppDataUseCase = mock()
    private val revokeAllHealthPermissionsUseCase: RevokeAllHealthPermissionsUseCase = mock()
    private val revokePermissionStatusUseCase: RevokeHealthPermissionUseCase = mock()
    private val grantPermissionsUseCase: GrantHealthPermissionUseCase = mock()
    private val loadExerciseRoutePermissionUseCase = FakeLoadExerciseRoute()

    private lateinit var loadAppPermissionsStatusUseCase: LoadAppPermissionsStatusUseCase
    private lateinit var appPermissionViewModel: AppPermissionViewModel
    @Inject lateinit var appInfoReader: AppInfoReader
    @Inject lateinit var featureUtils: FeatureUtils

    private val readExercisePermission =
        FitnessPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.READ)
    private val readNutritionPermission =
        FitnessPermission(HealthPermissionType.NUTRITION, PermissionsAccessType.READ)
    private val readExerciseRoutesPermission =
        HealthPermission.AdditionalPermission.READ_EXERCISE_ROUTES
    private val readHistoryDataPermission =
        HealthPermission.AdditionalPermission.READ_HEALTH_DATA_HISTORY
    private val readDataInBackgroundPermission =
        HealthPermission.AdditionalPermission.READ_HEALTH_DATA_IN_BACKGROUND
    private val readImmunization = MedicalPermission(MedicalPermissionType.IMMUNIZATION)
    private val writeSleepPermission =
        FitnessPermission(HealthPermissionType.SLEEP, PermissionsAccessType.WRITE)
    private val writeDistancePermission =
        FitnessPermission(HealthPermissionType.DISTANCE, PermissionsAccessType.WRITE)
    private val writeMedicalData = MedicalPermission(MedicalPermissionType.ALL_MEDICAL_DATA)

    @Captor lateinit var appDataCaptor: ArgumentCaptor<DeletionType.DeletionTypeAppData>
    @Captor lateinit var timeFilterCaptor: ArgumentCaptor<TimeInstantRangeFilter>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        loadAppPermissionsStatusUseCase =
            LoadAppPermissionsStatusUseCase(
                getGrantedHealthPermissionsUseCase, healthPermissionReader, Dispatchers.Main)
        appPermissionViewModel =
            AppPermissionViewModel(
                appInfoReader,
                loadAppPermissionsStatusUseCase,
                grantPermissionsUseCase,
                revokePermissionStatusUseCase,
                revokeAllHealthPermissionsUseCase,
                deleteAppDateUseCase,
                loadAccessDateUseCase,
                getGrantedHealthPermissionsUseCase,
                loadExerciseRoutePermissionUseCase,
                healthPermissionReader,
                featureUtils,
                Dispatchers.Main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun loadsCorrectAccessDate() {
        val accessDate = NOW
        whenever(loadAccessDateUseCase.invoke(TEST_APP_PACKAGE_NAME)).thenReturn(accessDate)
        val result = appPermissionViewModel.loadAccessDate(TEST_APP_PACKAGE_NAME)
        assertThat(result).isEqualTo(accessDate)
    }

    @Test
    fun whenPackageSupported_loadAllPermissions_fitnessOnly() = runTest {
        setupDeclaredAndGrantedFitnessPermissions()

        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionsResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionsResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()

        assertThat(fitnessPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))
        assertThat(medicalPermissionsResult).containsExactlyElementsIn(listOf<MedicalPermission>())
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf<MedicalPermission>())
    }

    @Test
    fun whenPackageSupported_loadAllPermissions_fitnessAndMedical() = runTest {
        setupDeclaredAndGrantedFitnessAndMedicalPermissions()
        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionsResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionsResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()
        assertThat(fitnessPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(medicalPermissionsResult)
            .containsExactlyElementsIn(listOf(readImmunization, writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(readImmunization))
    }

    @Test
    fun whenPackageSupported_loadAllPermissions_medicalOnly() = runTest {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(true)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(listOf(readImmunization, writeMedicalData))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME, listOf(writeMedicalData.toString()))
        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionsResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionsResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()
        assertThat(fitnessPermissionsResult).containsExactlyElementsIn(listOf<FitnessPermission>())
        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf<FitnessPermission>())
        assertThat(medicalPermissionsResult)
            .containsExactlyElementsIn(listOf(readImmunization, writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(writeMedicalData))
    }

    @Test
    fun whenPackageNotSupported_fitnessOnly_loadOnlyGrantedPermissions() = runTest {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(false)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME, listOf(readExercisePermission.toString()))
        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()
        assertThat(fitnessPermissionResult)
            .containsExactlyElementsIn(listOf(readExercisePermission))
        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(medicalPermissionResult).containsExactlyElementsIn(listOf<MedicalPermission>())
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf<MedicalPermission>())
    }

    @Test
    fun whenPackageNotSupported_medicalOnly_loadOnlyGrantedPermissions() = runTest {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(false)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(listOf(writeMedicalData, readImmunization))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME, listOf(writeMedicalData.toString()))
        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()
        assertThat(fitnessPermissionResult).containsExactlyElementsIn(listOf<FitnessPermission>())
        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf<FitnessPermission>())
        assertThat(medicalPermissionResult).containsExactlyElementsIn(listOf(writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(writeMedicalData))
    }

    @Test
    fun whenPackageNotSupported_fitnessAndMedical_loadOnlyGrantedPermissions() = runTest {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(false)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    readImmunization,
                    writeSleepPermission,
                    writeDistancePermission,
                    writeMedicalData))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME,
            listOf(readExercisePermission.toString(), writeMedicalData.toString()))
        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()
        assertThat(fitnessPermissionResult)
            .containsExactlyElementsIn(listOf(readExercisePermission))
        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(medicalPermissionResult).containsExactlyElementsIn(listOf(writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(writeMedicalData))
    }

    @Test
    fun updateFitnessPermissions_grant_whenSuccessful_returnsTrue() = runTest {
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val readExercisePermission =
            FitnessPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.READ)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        val result =
            appPermissionViewModel.updatePermission(
                TEST_APP_PACKAGE_NAME, readExercisePermission, true)
        advanceUntilIdle()

        assertThat(grantedPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(result).isTrue()
    }

    @Test
    fun updateFitnessPermissions_grant_whenUnsuccessful_returnsFalse() = runTest {
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val readExercisePermission =
            FitnessPermission(HealthPermissionType.EXERCISE, PermissionsAccessType.READ)
        whenever(grantPermissionsUseCase.invoke(any(), any())).thenThrow(RuntimeException("Error!"))
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        val result =
            appPermissionViewModel.updatePermission(
                TEST_APP_PACKAGE_NAME, readExercisePermission, true)
        advanceUntilIdle()

        assertThat(grantedPermissionsObserver.getLastValue()).isEmpty()
        assertThat(result).isFalse()
    }

    @Test
    fun updateFitnessPermissions_deny_whenSuccessful_returnsTrue() = runTest {
        setupDeclaredAndGrantedFitnessPermissions()
        val appPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(appPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val appPermissionsResult = appPermissionsObserver.getLastValue()
        val grantedPermissionsResult = grantedPermissionsObserver.getLastValue()

        assertThat(appPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))

        val result =
            appPermissionViewModel.updatePermission(
                TEST_APP_PACKAGE_NAME, writeDistancePermission, false)
        advanceUntilIdle()

        assertThat(grantedPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(result).isTrue()
    }

    @Test
    fun updatePermissions_denyLastReadPermission_updatesAdditionalPermissions() = runTest {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(true)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    readExerciseRoutesPermission,
                    readDataInBackgroundPermission,
                    readHistoryDataPermission,
                    writeSleepPermission,
                    writeDistancePermission,
                ))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME,
            listOf(
                readNutritionPermission.toString(),
                writeDistancePermission.toString(),
                readExerciseRoutesPermission.additionalPermission,
                readHistoryDataPermission.additionalPermission,
                readDataInBackgroundPermission.additionalPermission))

        loadExerciseRoutePermissionUseCase.setExerciseRouteState(
            ExerciseRouteState(
                exerciseRoutePermissionState = PermissionUiState.ALWAYS_ALLOW,
                exercisePermissionState = PermissionUiState.ALWAYS_ALLOW))

        val appPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(appPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val appPermissionsResult = appPermissionsObserver.getLastValue()
        val grantedPermissionsResult = grantedPermissionsObserver.getLastValue()

        assertThat(appPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedPermissionsResult)
            .containsExactlyElementsIn(setOf(readNutritionPermission, writeDistancePermission))

        val result =
            appPermissionViewModel.updatePermission(
                TEST_APP_PACKAGE_NAME, readNutritionPermission, false)
        advanceUntilIdle()

        assertThat(grantedPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(writeDistancePermission))
        assertThat(result).isTrue()
        verify(revokePermissionStatusUseCase)
            .invoke(TEST_APP_PACKAGE_NAME, readExerciseRoutesPermission.additionalPermission)
        verify(revokePermissionStatusUseCase)
            .invoke(TEST_APP_PACKAGE_NAME, readDataInBackgroundPermission.additionalPermission)
        verify(revokePermissionStatusUseCase)
            .invoke(TEST_APP_PACKAGE_NAME, readHistoryDataPermission.additionalPermission)
    }

    @Test
    fun updatePermissions_denyLastReadPermission_skipsERIfAlreadyAskEveryTime() = runTest {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(true)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    readExerciseRoutesPermission,
                    readDataInBackgroundPermission,
                    readHistoryDataPermission,
                    writeSleepPermission,
                    writeDistancePermission,
                ))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME,
            listOf(
                readExercisePermission.toString(),
                writeDistancePermission.toString(),
                readHistoryDataPermission.additionalPermission,
                readDataInBackgroundPermission.additionalPermission))

        loadExerciseRoutePermissionUseCase.setExerciseRouteState(
            ExerciseRouteState(
                exerciseRoutePermissionState = PermissionUiState.ASK_EVERY_TIME,
                exercisePermissionState = PermissionUiState.ALWAYS_ALLOW))

        val appPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(appPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val appPermissionsResult = appPermissionsObserver.getLastValue()
        val grantedPermissionsResult = grantedPermissionsObserver.getLastValue()

        assertThat(appPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))

        val result =
            appPermissionViewModel.updatePermission(
                TEST_APP_PACKAGE_NAME, readExercisePermission, false)
        advanceUntilIdle()

        assertThat(grantedPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(writeDistancePermission))
        assertThat(result).isTrue()
        verify(revokePermissionStatusUseCase, times(0))
            .invoke(TEST_APP_PACKAGE_NAME, readExerciseRoutesPermission.additionalPermission)
        verify(revokePermissionStatusUseCase)
            .invoke(TEST_APP_PACKAGE_NAME, readDataInBackgroundPermission.additionalPermission)
        verify(revokePermissionStatusUseCase)
            .invoke(TEST_APP_PACKAGE_NAME, readHistoryDataPermission.additionalPermission)
    }

    @Test
    fun updatePermissions_deny_whenUnsuccessful_returnsFalse() = runTest {
        setupDeclaredAndGrantedFitnessPermissions()

        val appPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(appPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val appPermissionsResult = appPermissionsObserver.getLastValue()
        val grantedPermissionsResult = grantedPermissionsObserver.getLastValue()

        assertThat(appPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))

        whenever(grantPermissionsUseCase.invoke(any(), any())).thenThrow(RuntimeException("Error!"))
        val result =
            appPermissionViewModel.updatePermission(
                TEST_APP_PACKAGE_NAME, readExercisePermission, true)
        advanceUntilIdle()

        assertThat(grantedPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))
        assertThat(result).isFalse()
    }

    @Test
    fun grantAllFitnessPermissions_whenSuccessful_returnsTrue() = runTest {
        setupDeclaredAndGrantedFitnessPermissions()

        val appPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(appPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val appPermissionsResult = appPermissionsObserver.getLastValue()
        val grantedPermissionsResult = grantedPermissionsObserver.getLastValue()

        assertThat(appPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))

        val result = appPermissionViewModel.grantAllFitnessPermissions(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        assertThat(grantedPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(
                setOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(result).isTrue()
    }

    @Test
    fun grantAllFitnessPermissions_whenSuccessful_noChangeInMedicalPermissions() = runTest {
        setupDeclaredAndGrantedFitnessAndMedicalPermissions()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val medicalPermissionsResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()

        assertThat(medicalPermissionsResult)
            .containsExactlyElementsIn(listOf(readImmunization, writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(readImmunization))

        val result = appPermissionViewModel.grantAllFitnessPermissions(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        assertThat(grantedMedicalPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readImmunization))
        assertThat(result).isTrue()
    }

    @Test
    fun grantAllFitnessPermissions_whenUnsuccessful_returnsFalse() = runTest {
        setupDeclaredAndGrantedFitnessPermissions()
        val appPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(appPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val appPermissionsResult = appPermissionsObserver.getLastValue()
        val grantedPermissionsResult = grantedPermissionsObserver.getLastValue()
        assertThat(appPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))

        whenever(grantPermissionsUseCase.invoke(any(), any())).thenThrow(RuntimeException("Error!"))
        val result = appPermissionViewModel.grantAllFitnessPermissions(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        assertThat(grantedPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))
        assertThat(result).isFalse()
    }

    @Test
    fun grantAllFitnessPermissions_whenUnsuccessful_noChangeInMedicalPermission() = runTest {
        setupDeclaredAndGrantedFitnessAndMedicalPermissions()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val medicalPermissionsResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()
        assertThat(medicalPermissionsResult)
            .containsExactlyElementsIn(listOf(readImmunization, writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(readImmunization))

        whenever(grantPermissionsUseCase.invoke(any(), any())).thenThrow(RuntimeException("Error!"))
        val result = appPermissionViewModel.grantAllFitnessPermissions(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        assertThat(grantedMedicalPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readImmunization))
        assertThat(result).isFalse()
    }

    @Test
    fun grantAllMedicalPermissions_whenSuccessful_returnsTrue() = runTest {
        setupDeclaredAndGrantedFitnessAndMedicalPermissions()
        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionsResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionsResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()

        assertThat(fitnessPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(medicalPermissionsResult)
            .containsExactlyElementsIn(listOf(readImmunization, writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(readImmunization))

        val result = appPermissionViewModel.grantAllMedicalPermissions(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        assertThat(grantedFitnessPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(grantedMedicalPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readImmunization, writeMedicalData))
        assertThat(result).isTrue()
    }

    @Test
    fun grantAllMedicalPermissions_whenUnsuccessful_returnsFalse() = runTest {
        setupDeclaredAndGrantedFitnessAndMedicalPermissions()
        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionsResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionsResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()

        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(medicalPermissionsResult)
            .containsExactlyElementsIn(listOf(readImmunization, writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(readImmunization))

        whenever(grantPermissionsUseCase.invoke(any(), any())).thenThrow(RuntimeException("Error!"))
        val result = appPermissionViewModel.grantAllFitnessPermissions(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(grantedMedicalPermissionsObserver.getLastValue())
            .containsExactlyElementsIn(setOf(readImmunization))
        assertThat(result).isFalse()
    }

    @Test
    fun revokeAllFitnessPermissions_whenSuccessful_returnsTrue() = runTest {
        setupDeclaredAndGrantedFitnessPermissions()
        val appPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(appPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(grantedPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val appPermissionsResult = appPermissionsObserver.getLastValue()
        val grantedPermissionsResult = grantedPermissionsObserver.getLastValue()
        assertThat(appPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission, writeDistancePermission))

        val result = appPermissionViewModel.revokeAllHealthPermissions(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()
        assertThat(grantedPermissionsObserver.getLastValue()).isEmpty()
        assertThat(result).isTrue()
    }

    @Test
    fun revokeAllPermissions_fitnessAndMedical_revokesBoth() = runTest {
        setupDeclaredAndGrantedFitnessAndMedicalPermissions()
        val fitnessPermissionsObserver = TestObserver<List<FitnessPermission>>()
        val grantedFitnessPermissionsObserver = TestObserver<Set<FitnessPermission>>()
        val medicalPermissionsObserver = TestObserver<List<MedicalPermission>>()
        val grantedMedicalPermissionsObserver = TestObserver<Set<MedicalPermission>>()
        appPermissionViewModel.fitnessPermissions.observeForever(fitnessPermissionsObserver)
        appPermissionViewModel.grantedFitnessPermissions.observeForever(
            grantedFitnessPermissionsObserver)
        appPermissionViewModel.medicalPermissions.observeForever(medicalPermissionsObserver)
        appPermissionViewModel.grantedMedicalPermissions.observeForever(
            grantedMedicalPermissionsObserver)

        appPermissionViewModel.loadPermissionsForPackage(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()

        val fitnessPermissionsResult = fitnessPermissionsObserver.getLastValue()
        val grantedFitnessPermissionsResult = grantedFitnessPermissionsObserver.getLastValue()
        val medicalPermissionsResult = medicalPermissionsObserver.getLastValue()
        val grantedMedicalPermissionsResult = grantedMedicalPermissionsObserver.getLastValue()
        assertThat(fitnessPermissionsResult)
            .containsExactlyElementsIn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        assertThat(grantedFitnessPermissionsResult)
            .containsExactlyElementsIn(setOf(readExercisePermission))
        assertThat(medicalPermissionsResult)
            .containsExactlyElementsIn(listOf(readImmunization, writeMedicalData))
        assertThat(grantedMedicalPermissionsResult)
            .containsExactlyElementsIn(setOf(readImmunization))

        val result = appPermissionViewModel.revokeAllHealthPermissions(TEST_APP_PACKAGE_NAME)
        advanceUntilIdle()
        assertThat(grantedFitnessPermissionsObserver.getLastValue()).isEmpty()
        assertThat(grantedMedicalPermissionsObserver.getLastValue()).isEmpty()
        assertThat(result).isTrue()
    }

    // TODO (b/324247426) unignore when we can mock suspend functions
    @Test
    @Ignore
    fun deleteAppData_invokesUseCaseWithCorrectFilter() = runTest {
        appPermissionViewModel.deleteAppData(TEST_APP_PACKAGE_NAME, TEST_APP_NAME)
        advanceUntilIdle()

        verify(deleteAppDateUseCase).invoke(appDataCaptor.capture(), timeFilterCaptor.capture())
    }

    @Test
    fun shouldNavigateToFragment_whenPackageNameSupported_returnsTrue() = runTest {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(true)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        getGrantedHealthPermissionsUseCase.updateData(TEST_APP_PACKAGE_NAME, listOf())

        advanceUntilIdle()

        assertThat(
                appPermissionViewModel.shouldNavigateToAppPermissionsFragment(
                    TEST_APP_PACKAGE_NAME))
            .isTrue()
    }

    @Test
    fun shouldNavigateToFragment_whenAnyPermissionGranted_returnsTrue() = runTest {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(false)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME, listOf(writeSleepPermission.toString()))

        advanceUntilIdle()

        assertThat(
                appPermissionViewModel.shouldNavigateToAppPermissionsFragment(
                    TEST_APP_PACKAGE_NAME))
            .isTrue()
    }

    @Test
    fun shouldNavigateToFragment_whenPackageNotSupported_andNoPermissionsGranted_returnsFalse() =
        runTest {
            whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(false)
            whenever(healthPermissionReader.getValidHealthPermissions(any()))
                .thenReturn(
                    listOf(
                        readExercisePermission,
                        readNutritionPermission,
                        writeSleepPermission,
                        writeDistancePermission))
            getGrantedHealthPermissionsUseCase.updateData(TEST_APP_PACKAGE_NAME, listOf())

            advanceUntilIdle()

            assertThat(
                    appPermissionViewModel.shouldNavigateToAppPermissionsFragment(
                        TEST_APP_PACKAGE_NAME))
                .isFalse()
        }

    @Test
    fun isPackageSupported_callsCorrectMethod() {
        appPermissionViewModel.isPackageSupported(TEST_APP_PACKAGE_NAME)
        verify(healthPermissionReader).isRationaleIntentDeclared(TEST_APP_PACKAGE_NAME)
    }

    private fun setupDeclaredAndGrantedFitnessPermissions() {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(true)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    writeSleepPermission,
                    writeDistancePermission))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME,
            listOf(readExercisePermission.toString(), writeDistancePermission.toString()))
    }

    private fun setupDeclaredAndGrantedFitnessAndMedicalPermissions() {
        whenever(healthPermissionReader.isRationaleIntentDeclared(any())).thenReturn(true)
        whenever(healthPermissionReader.getValidHealthPermissions(any()))
            .thenReturn(
                listOf(
                    readExercisePermission,
                    readNutritionPermission,
                    readImmunization,
                    writeSleepPermission,
                    writeDistancePermission,
                    writeMedicalData))
        getGrantedHealthPermissionsUseCase.updateData(
            TEST_APP_PACKAGE_NAME,
            listOf(readExercisePermission.toString(), readImmunization.toString()))
    }
}
