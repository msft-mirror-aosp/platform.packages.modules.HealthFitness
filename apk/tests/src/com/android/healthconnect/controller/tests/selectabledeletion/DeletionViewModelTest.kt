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
package com.android.healthconnect.controller.tests.selectabledeletion

import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.selectabledeletion.DeletionType
import com.android.healthconnect.controller.selectabledeletion.DeletionViewModel
import com.android.healthconnect.controller.selectabledeletion.api.DeleteEntriesUseCase
import com.android.healthconnect.controller.selectabledeletion.api.DeletePermissionTypesUseCase
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class DeletionViewModelTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = TestCoroutineDispatcher()

    private val deletePermissionTypesUseCase: DeletePermissionTypesUseCase =
        mock(DeletePermissionTypesUseCase::class.java)
    private val deleteEntriesUseCase: DeleteEntriesUseCase = mock(DeleteEntriesUseCase::class.java)

    private lateinit var viewModel: DeletionViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel = DeletionViewModel(deletePermissionTypesUseCase, deleteEntriesUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun permissionTypes_resetPermissionTypesReloadNeeded_valueSetCorrectly() = runTest {
        val testObserver = TestObserver<Boolean>()
        viewModel.permissionTypesReloadNeeded.observeForever(testObserver)
        viewModel.resetPermissionTypesReloadNeeded()
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEqualTo(false)
    }

    @Test
    fun permissionTypes_deleteSet_setCorrectly() {
        val deleteSet =
            setOf(
                FitnessPermissionType.DISTANCE,
                FitnessPermissionType.HEART_RATE,
                FitnessPermissionType.STEPS,
            )
        viewModel.setPermissionTypesDeleteSet(deleteSet)

        assertThat(viewModel.getPermissionTypesDeleteSet())
            .isEqualTo(
                setOf(
                    FitnessPermissionType.DISTANCE,
                    FitnessPermissionType.HEART_RATE,
                    FitnessPermissionType.STEPS,
                )
            )
    }

    @Test
    fun permissionTypes_delete_deletionInvokedCorrectly() = runTest {
        viewModel.setPermissionTypesDeleteSet(setOf(FitnessPermissionType.DISTANCE))
        viewModel.delete()
        advanceUntilIdle()

        val expectedDeletionType =
            DeletionType.DeletionTypeHealthPermissionTypes(listOf(FitnessPermissionType.DISTANCE))
        verify(deletePermissionTypesUseCase).invoke(expectedDeletionType)
    }

    @Test
    fun permissionTypes_deleteFitnessAndMedical_deletionInvokedCorrectly() = runTest {
        viewModel.setPermissionTypesDeleteSet(
            setOf(FitnessPermissionType.DISTANCE, MedicalPermissionType.IMMUNIZATION)
        )
        viewModel.delete()
        advanceUntilIdle()

        val expectedDeletionType =
            DeletionType.DeletionTypeHealthPermissionTypes(
                listOf(FitnessPermissionType.DISTANCE, MedicalPermissionType.IMMUNIZATION)
            )
        verify(deletePermissionTypesUseCase).invoke(expectedDeletionType)
    }

    @Test
    fun permissionTypes_deleteMedical_deletionInvokedCorrectly() = runTest {
        viewModel.setPermissionTypesDeleteSet(setOf(MedicalPermissionType.IMMUNIZATION))
        viewModel.delete()
        advanceUntilIdle()

        val expectedDeletionType =
            DeletionType.DeletionTypeHealthPermissionTypes(
                listOf(MedicalPermissionType.IMMUNIZATION)
            )
        verify(deletePermissionTypesUseCase).invoke(expectedDeletionType)
    }

    @Test
    fun entries_resetEntriesReloadNeeded_valueSetCorrectly() = runTest {
        val testObserver = TestObserver<Boolean>()
        viewModel.entriesReloadNeeded.observeForever(testObserver)
        viewModel.resetEntriesReloadNeeded()
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEqualTo(false)
    }

    @Test
    fun entries_deleteSet_setCorrectly() {
        val deleteSet = setOf(FORMATTED_STEPS.uuid, FORMATTED_STEPS_2.uuid)
        viewModel.setEntriesDeleteSet(deleteSet, DataType.STEPS)

        assertThat(viewModel.getEntriesDeleteSet()).isEqualTo(setOf("test_id", "test_id_2"))
    }

    @Test
    fun entries_delete_deletionInvokesCorrectly() = runTest {
        val deleteSet = setOf(FORMATTED_STEPS.uuid, FORMATTED_STEPS_2.uuid)
        viewModel.setEntriesDeleteSet(deleteSet, DataType.STEPS)
        viewModel.delete()
        advanceUntilIdle()

        val expectedDeletionType =
            DeletionType.DeletionTypeEntries(deleteSet.toList(), DataType.STEPS)
        verify(deleteEntriesUseCase).invoke(expectedDeletionType)
    }
}

private val FORMATTED_STEPS =
    FormattedEntry.FormattedDataEntry(
        uuid = "test_id",
        header = "7:06 - 7:06",
        headerA11y = "from 7:06 to 7:06",
        title = "12 steps",
        titleA11y = "12 steps",
        dataType = DataType.STEPS,
    )
private val FORMATTED_STEPS_2 =
    FormattedEntry.FormattedDataEntry(
        uuid = "test_id_2",
        header = "8:06 - 8:06",
        headerA11y = "from 8:06 to 8:06",
        title = "15 steps",
        titleA11y = "15 steps",
        dataType = DataType.STEPS,
    )
