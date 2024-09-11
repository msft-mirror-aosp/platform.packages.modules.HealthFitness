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
package com.android.healthconnect.controller.tests.data.rawfhir

import android.content.Context
import android.health.connect.HealthConnectException
import android.health.connect.HealthConnectManager
import android.health.connect.MedicalResourceId
import android.health.connect.datatypes.MedicalResource
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.rawfhir.RawFhirUseCase
import com.android.healthconnect.controller.data.rawfhir.RawFhirViewModel
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_RESOURCE_IMMUNIZATION
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_RESOURCE_IMMUNIZATION_LONG
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_RESOURCE_INVALID_JSON
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
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
import org.mockito.ArgumentMatchers
import org.mockito.Matchers
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class RawFhirViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
    private val testDispatcher = TestCoroutineDispatcher()

    var manager: HealthConnectManager = mock(HealthConnectManager::class.java)

    private lateinit var viewModel: RawFhirViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
        viewModel = RawFhirViewModel(RawFhirUseCase(manager, Dispatchers.Main))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun errorInApiCall_returnsErrorState() = runTest {
        doAnswer(prepareFailureAnswer())
            .`when`(manager)
            .readMedicalResources(
                ArgumentMatchers.any<MutableList<MedicalResourceId>>(),
                Matchers.any(),
                Matchers.any(),
            )

        val testObserver = TestObserver<RawFhirViewModel.RawFhirState>()
        viewModel.rawFhir.observeForever(testObserver)
        viewModel.loadFhirResource(TEST_MEDICAL_RESOURCE_IMMUNIZATION.id)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue()).isEqualTo(RawFhirViewModel.RawFhirState.Error)
    }

    @Test
    fun smallImmunizationFhir_returnsFormattedFhir() = runTest {
        val medicalResources: List<MedicalResource> = listOf(TEST_MEDICAL_RESOURCE_IMMUNIZATION)
        doAnswer(prepareAnswer(medicalResources))
            .`when`(manager)
            .readMedicalResources(
                ArgumentMatchers.any<MutableList<MedicalResourceId>>(),
                Matchers.any(),
                Matchers.any(),
            )

        val testObserver = TestObserver<RawFhirViewModel.RawFhirState>()
        viewModel.rawFhir.observeForever(testObserver)
        viewModel.loadFhirResource(TEST_MEDICAL_RESOURCE_IMMUNIZATION.id)
        advanceUntilIdle()

        val expected =
            listOf(
                RawFhirViewModel.FormattedFhir(
                    "{\n" +
                        "    \"resourceType\" :  \"Immunization\",\n" +
                        "     \"id\" :  \"Immunization_1\"\n" +
                        "}"
                )
            )
        assertThat(testObserver.getLastValue())
            .isEqualTo(RawFhirViewModel.RawFhirState.WithData(expected))
    }

    @Test
    fun longImmunizationFhir_returnsFormattedFhir() = runTest {
        val medicalResources: List<MedicalResource> =
            listOf(TEST_MEDICAL_RESOURCE_IMMUNIZATION_LONG)
        doAnswer(prepareAnswer(medicalResources))
            .`when`(manager)
            .readMedicalResources(
                ArgumentMatchers.any<MutableList<MedicalResourceId>>(),
                Matchers.any(),
                Matchers.any(),
            )

        val testObserver = TestObserver<RawFhirViewModel.RawFhirState>()
        viewModel.rawFhir.observeForever(testObserver)
        viewModel.loadFhirResource(TEST_MEDICAL_RESOURCE_IMMUNIZATION_LONG.id)
        advanceUntilIdle()

        val expected =
            listOf(
                RawFhirViewModel.FormattedFhir(
                    "{\n" +
                        "    \"resourceType\": \"Immunization\",\n" +
                        "    \"id\": \"immunization_1\",\n" +
                        "    \"status\": \"completed\",\n" +
                        "    \"vaccineCode\": {\n" +
                        "        \"coding\": [\n" +
                        "            {\n" +
                        "                \"system\": \"http://hl7.org/fhir/sid/cvx\",\n" +
                        "                \"code\": \"115\"\n" +
                        "            },\n" +
                        "            {\n" +
                        "                \"system\": \"http://hl7.org/fhir/sid/ndc\",\n" +
                        "                \"code\": \"58160-842-11\"\n" +
                        "            }\n" +
                        "        ],\n" +
                        "        \"text\": \"Tdap\"\n" +
                        "    },\n" +
                        "    \"patient\": {\n" +
                        "        \"reference\": \"Patient/patient_1\",\n" +
                        "        \"display\": \"Example, Anne\"\n" +
                        "    },\n" +
                        "    \"encounter\": {\n" +
                        "        \"reference\": \"Encounter/encounter_unk\",\n" +
                        "        \"display\": \"GP Visit\"\n" +
                        "    },\n" +
                        "    \"occurrenceDateTime\": \"2018-05-21\",\n" +
                        "    \"primarySource\": true,\n" +
                        "    \"manufacturer\": {\n" +
                        "        \"display\": \"Sanofi Pasteur\"\n" +
                        "    },\n" +
                        "    \"lotNumber\": \"1\",\n" +
                        "    \"site\": {\n" +
                        "        \"coding\": [\n" +
                        "            {\n" +
                        "                \"system\": \"http://terminology.hl7.org/CodeSystem/v3-ActSite\",\n" +
                        "                \"code\": \"LA\",\n" +
                        "                \"display\": \"Left Arm\"\n" +
                        "            }\n" +
                        "        ],\n" +
                        "        \"text\": \"Left Arm\"\n" +
                        "    },\n" +
                        "    \"route\": {\n" +
                        "        \"coding\": [\n" +
                        "            {\n" +
                        "                \"system\": \"http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration\",\n" +
                        "                \"code\": \"IM\",\n" +
                        "                \"display\": \"Injection, intramuscular\"\n" +
                        "            }\n" +
                        "        ],\n" +
                        "        \"text\": \"Injection, intramuscular\"\n" +
                        "    },\n" +
                        "    \"doseQuantity\": {\n" +
                        "        \"value\": 0.5,\n" +
                        "        \"unit\": \"mL\"\n" +
                        "    },\n" +
                        "    \"performer\": [\n" +
                        "        {\n" +
                        "            \"function\": {\n" +
                        "                \"coding\": [\n" +
                        "                    {\n" +
                        "                        \"system\": \"http://terminology.hl7.org/CodeSystem/v2-0443\",\n" +
                        "                        \"code\": \"AP\",\n" +
                        "                        \"display\": \"Administering Provider\"\n" +
                        "                    }\n" +
                        "                ],\n" +
                        "                \"text\": \"Administering Provider\"\n" +
                        "            },\n" +
                        "            \"actor\": {\n" +
                        "                \"reference\": \"Practitioner/practitioner_1\",\n" +
                        "                \"type\": \"Practitioner\",\n" +
                        "                \"display\": \"Dr Maria Hernandez\"\n" +
                        "            }\n" +
                        "        }\n" +
                        "    ]\n" +
                        "}"
                )
            )
        assertThat(testObserver.getLastValue())
            .isEqualTo(RawFhirViewModel.RawFhirState.WithData(expected))
    }

    @Test
    fun invalid_json_returnsSameText() = runTest {
        val medicalResources: List<MedicalResource> = listOf(TEST_MEDICAL_RESOURCE_INVALID_JSON)
        doAnswer(prepareAnswer(medicalResources))
            .`when`(manager)
            .readMedicalResources(
                ArgumentMatchers.any<MutableList<MedicalResourceId>>(),
                Matchers.any(),
                Matchers.any(),
            )

        val testObserver = TestObserver<RawFhirViewModel.RawFhirState>()
        viewModel.rawFhir.observeForever(testObserver)
        viewModel.loadFhirResource(TEST_MEDICAL_RESOURCE_INVALID_JSON.id)
        advanceUntilIdle()

        val expected =
            listOf(
                RawFhirViewModel.FormattedFhir(TEST_MEDICAL_RESOURCE_INVALID_JSON.fhirResource.data)
            )
        assertThat(testObserver.getLastValue())
            .isEqualTo(RawFhirViewModel.RawFhirState.WithData(expected))
    }

    private fun prepareAnswer(
        medicalResources: List<MedicalResource>
    ): (InvocationOnMock) -> List<MedicalResource> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[2] as OutcomeReceiver<Any?, *>
            receiver.onResult(medicalResources)
            medicalResources
        }
        return answer
    }

    private fun prepareFailureAnswer(): (InvocationOnMock) -> Nothing? {
        val answer = { args: InvocationOnMock ->
            val receiver =
                args.arguments[2] as OutcomeReceiver<List<LocalDate>, HealthConnectException>
            receiver.onError(HealthConnectException(HealthConnectException.ERROR_UNKNOWN))
            null
        }
        return answer
    }
}
