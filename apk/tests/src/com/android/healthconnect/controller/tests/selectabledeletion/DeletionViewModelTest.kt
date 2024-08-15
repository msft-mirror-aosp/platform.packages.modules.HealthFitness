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

import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.selectabledeletion.DeletionType
import com.android.healthconnect.controller.selectabledeletion.DeletionViewModel
import com.android.healthconnect.controller.selectabledeletion.api.DeletePermissionTypesUseCase
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

    private lateinit var viewModel: DeletionViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel = DeletionViewModel(deletePermissionTypesUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun resetPermissionTypesReloadNeeded_valueSetCorrectly() = runTest {
        val testObserver = TestObserver<Boolean>()
        viewModel.permissionTypesReloadNeeded.observeForever(testObserver)
        viewModel.resetPermissionTypesReloadNeeded()
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEqualTo(false)
    }

    @Test
    fun deleteSet_setCorrectly() {
        val deleteSet =
            setOf(
                FitnessPermissionType.DISTANCE,
                FitnessPermissionType.HEART_RATE,
                FitnessPermissionType.STEPS)
        viewModel.setDeleteSet(deleteSet)

        assertThat(viewModel.getDeleteSet())
            .isEqualTo(
                setOf(
                    FitnessPermissionType.DISTANCE,
                    FitnessPermissionType.HEART_RATE,
                    FitnessPermissionType.STEPS))
    }

    @Test
    fun delete_deletionInvokesCorrectly() = runTest {
        viewModel.setDeleteSet(setOf(FitnessPermissionType.DISTANCE))
        viewModel.delete()
        advanceUntilIdle()

        val expectedDeletionType =
            DeletionType.DeletionTypeHealthPermissionTypes(listOf(FitnessPermissionType.DISTANCE))
        verify(deletePermissionTypesUseCase).invoke(expectedDeletionType)
    }
}
