/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.selectabledeletion.api

import android.health.connect.DeleteUsingFiltersRequest
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.selectabledeletion.DeletionType.DeleteHealthPermissionTypesFromApp
import com.android.healthconnect.controller.selectabledeletion.api.DeleteFitnessPermissionTypesFromAppUseCase
import com.android.healthconnect.controller.selectabledeletion.api.DeleteMedicalPermissionTypesFromAppUseCase
import com.android.healthconnect.controller.selectabledeletion.api.DeletePermissionTypesFromAppUseCase
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class DeletePermissionTypesFromAppUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var useCase: DeletePermissionTypesFromAppUseCase

    private val deleteFitnessPermissionTypesFromAppUseCase:
        DeleteFitnessPermissionTypesFromAppUseCase =
        mock(DeleteFitnessPermissionTypesFromAppUseCase::class.java)
    private val deleteMedicalPermissionTypesFromAppUseCase:
        DeleteMedicalPermissionTypesFromAppUseCase =
        mock(DeleteMedicalPermissionTypesFromAppUseCase::class.java)

    @Captor lateinit var filtersCaptor: ArgumentCaptor<DeleteUsingFiltersRequest>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        useCase =
            DeletePermissionTypesFromAppUseCase(
                deleteFitnessPermissionTypesFromAppUseCase,
                deleteMedicalPermissionTypesFromAppUseCase,
                Dispatchers.Main,
            )
    }

    @Test
    fun permissionTypes_emptyDeleteMethod_noDeletionInvoked() = runTest {
        useCase.invoke(
            DeleteHealthPermissionTypesFromApp(
                setOf(),
                totalPermissionTypes = 0,
                "package.name",
                "app name",
            )
        )
        advanceUntilIdle()

        verifyZeroInteractions(deleteFitnessPermissionTypesFromAppUseCase)
        verifyZeroInteractions(deleteMedicalPermissionTypesFromAppUseCase)
    }

    @Test
    fun permissionTypes_delete_deletionInvokedCorrectly() = runTest {
        useCase.invoke(
            DeleteHealthPermissionTypesFromApp(
                setOf(FitnessPermissionType.DISTANCE),
                totalPermissionTypes = 1,
                "package.name",
                "app name",
            )
        )
        advanceUntilIdle()

        val expectedDeletionType =
            DeleteHealthPermissionTypesFromApp(
                setOf(FitnessPermissionType.DISTANCE),
                totalPermissionTypes = 1,
                "package.name",
                "app name",
            )
        verify(deleteFitnessPermissionTypesFromAppUseCase).invoke(expectedDeletionType)
        verifyZeroInteractions(deleteMedicalPermissionTypesFromAppUseCase)
    }

    @Test
    fun permissionTypes_deleteFitnessAndMedical_deletionInvokedCorrectly() = runTest {
        useCase.invoke(
            DeleteHealthPermissionTypesFromApp(
                setOf(FitnessPermissionType.DISTANCE, MedicalPermissionType.IMMUNIZATIONS),
                totalPermissionTypes = 4,
                "package.name",
                "app name",
            )
        )
        advanceUntilIdle()

        val expectedDeletionType =
            DeleteHealthPermissionTypesFromApp(
                setOf(FitnessPermissionType.DISTANCE, MedicalPermissionType.IMMUNIZATIONS),
                totalPermissionTypes = 4,
                "package.name",
                "app name",
            )
        verify(deleteFitnessPermissionTypesFromAppUseCase).invoke(expectedDeletionType)
        verify(deleteMedicalPermissionTypesFromAppUseCase).invoke(expectedDeletionType)
    }

    @Test
    fun permissionTypes_deleteMedical_deletionInvokedCorrectly() = runTest {
        useCase.invoke(
            DeleteHealthPermissionTypesFromApp(
                setOf(MedicalPermissionType.IMMUNIZATIONS),
                totalPermissionTypes = 3,
                "package.name",
                "app name",
            )
        )
        advanceUntilIdle()

        val expectedDeletionType =
            DeleteHealthPermissionTypesFromApp(
                setOf(MedicalPermissionType.IMMUNIZATIONS),
                totalPermissionTypes = 3,
                "package.name",
                "app name",
            )
        verify(deleteMedicalPermissionTypesFromAppUseCase).invoke(expectedDeletionType)
        verifyZeroInteractions(deleteFitnessPermissionTypesFromAppUseCase)
    }
}
