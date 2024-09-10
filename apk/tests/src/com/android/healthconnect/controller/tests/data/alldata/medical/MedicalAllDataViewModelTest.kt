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
package com.android.healthconnect.controller.tests.data.alldata.medical

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.MedicalResourceTypeInfo
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.alldata.medical.MedicalAllDataViewModel
import com.android.healthconnect.controller.data.appdata.AppDataUseCase
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType.IMMUNIZATION
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_DATA_SOURCE
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Locale
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
import org.mockito.Matchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class MedicalAllDataViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    var manager: HealthConnectManager = mock(HealthConnectManager::class.java)

    private lateinit var viewModel: MedicalAllDataViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel = MedicalAllDataViewModel(AppDataUseCase(manager, Dispatchers.Main))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun noData_returnsEmptyList() = runTest {
        doAnswer(prepareAnswer(listOf()))
            .`when`(manager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val testObserver = TestObserver<MedicalAllDataViewModel.AllDataState>()
        viewModel.allData.observeForever(testObserver)
        viewModel.loadAllMedicalData()
        advanceUntilIdle()

        val expected = emptyList<MedicalPermissionType>()
        assertThat(testObserver.getLastValue())
            .isEqualTo(MedicalAllDataViewModel.AllDataState.WithData(expected))
    }

    @Test
    fun hasImmunizationData_returnsImmunization() = runTest {
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                    setOf(TEST_MEDICAL_DATA_SOURCE),
                )
            )
        doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(manager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val testObserver = TestObserver<MedicalAllDataViewModel.AllDataState>()
        viewModel.allData.observeForever(testObserver)
        viewModel.loadAllMedicalData()
        advanceUntilIdle()

        val expected = listOf(IMMUNIZATION)
        assertThat(testObserver.getLastValue())
            .isEqualTo(MedicalAllDataViewModel.AllDataState.WithData(expected))
    }

    private fun prepareAnswer(
        medicalResourceTypeInfo: List<MedicalResourceTypeInfo>
    ): (InvocationOnMock) -> List<MedicalResourceTypeInfo> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[1] as OutcomeReceiver<Any?, *>
            receiver.onResult(medicalResourceTypeInfo)
            medicalResourceTypeInfo
        }
        return answer
    }
}
