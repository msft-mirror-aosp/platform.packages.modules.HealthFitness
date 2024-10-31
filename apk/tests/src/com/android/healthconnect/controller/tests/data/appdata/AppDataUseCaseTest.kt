/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.controller.tests.data.appdata

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.HealthDataCategory
import android.health.connect.HealthPermissionCategory
import android.health.connect.MedicalResourceTypeInfo
import android.health.connect.RecordTypeInfoResponse
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.MedicalResource
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.WeightRecord
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.appdata.AppDataUseCase
import com.android.healthconnect.controller.data.appdata.PermissionTypesPerCategory
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.MEDICAL
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.usecase.UseCaseResults.Success
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_DATA_SOURCE
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_DATA_SOURCE_2
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_DATA_SOURCE_DIFFERENT_APP
import com.android.healthconnect.controller.tests.utils.getDataOrigin
import com.android.healthfitness.flags.Flags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@HiltAndroidTest
class AppDataUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)
    private lateinit var appDataUseCase: AppDataUseCase

    @Inject lateinit var appInfoReader: AppInfoReader

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        hiltRule.inject()
        appDataUseCase = AppDataUseCase(healthConnectManager, Dispatchers.Main)
    }

    @Test
    fun loadFitnessData_returnsDataWrittenByGivenApp() = runTest {
        val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
            mapOf(
                StepsRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.STEPS,
                        HealthDataCategory.ACTIVITY,
                        listOf(
                            getDataOrigin(TEST_APP_PACKAGE_NAME),
                            getDataOrigin(TEST_APP_PACKAGE_NAME_2),
                        ),
                    ),
                WeightRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.WEIGHT,
                        HealthDataCategory.BODY_MEASUREMENTS,
                        listOf((getDataOrigin(TEST_APP_PACKAGE_NAME_2))),
                    ),
                HeartRateRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.HEART_RATE,
                        HealthDataCategory.VITALS,
                        listOf((getDataOrigin(TEST_APP_PACKAGE_NAME))),
                    ),
            )
        Mockito.doAnswer(prepareAnswer(recordTypeInfoMap))
            .`when`(healthConnectManager)
            .queryAllRecordTypesInfo(Matchers.any(), Matchers.any())

        val expected =
            Success(
                listOfNotNull(
                    PermissionTypesPerCategory(
                        HealthDataCategory.ACTIVITY,
                        listOf(FitnessPermissionType.STEPS),
                    ),
                    PermissionTypesPerCategory(HealthDataCategory.BODY_MEASUREMENTS, listOf()),
                    PermissionTypesPerCategory(HealthDataCategory.CYCLE_TRACKING, listOf()),
                    PermissionTypesPerCategory(HealthDataCategory.NUTRITION, listOf()),
                    PermissionTypesPerCategory(HealthDataCategory.SLEEP, listOf()),
                    PermissionTypesPerCategory(
                        HealthDataCategory.VITALS,
                        listOf(FitnessPermissionType.HEART_RATE),
                    ),
                    PermissionTypesPerCategory(HealthDataCategory.WELLNESS, listOf()).takeIf {
                        Flags.mindfulness()
                    },
                )
            )
        assertThat(appDataUseCase.loadFitnessAppData(TEST_APP_PACKAGE_NAME)).isEqualTo(expected)
    }

    @Test
    fun loadAllFitnessData_returnsAllData() = runTest {
        val recordTypeInfoMap: Map<Class<out Record>, RecordTypeInfoResponse> =
            mapOf(
                StepsRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.STEPS,
                        HealthDataCategory.ACTIVITY,
                        listOf(
                            getDataOrigin(TEST_APP_PACKAGE_NAME),
                            getDataOrigin(TEST_APP_PACKAGE_NAME_2),
                        ),
                    ),
                WeightRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.WEIGHT,
                        HealthDataCategory.BODY_MEASUREMENTS,
                        listOf((getDataOrigin(TEST_APP_PACKAGE_NAME_2))),
                    ),
                HeartRateRecord::class.java to
                    RecordTypeInfoResponse(
                        HealthPermissionCategory.HEART_RATE,
                        HealthDataCategory.VITALS,
                        listOf((getDataOrigin(TEST_APP_PACKAGE_NAME))),
                    ),
            )
        Mockito.doAnswer(prepareAnswer(recordTypeInfoMap))
            .`when`(healthConnectManager)
            .queryAllRecordTypesInfo(Matchers.any(), Matchers.any())

        val expected =
            Success(
                listOfNotNull(
                    PermissionTypesPerCategory(
                        HealthDataCategory.ACTIVITY,
                        listOf(FitnessPermissionType.STEPS),
                    ),
                    PermissionTypesPerCategory(
                        HealthDataCategory.BODY_MEASUREMENTS,
                        listOf(FitnessPermissionType.WEIGHT),
                    ),
                    PermissionTypesPerCategory(HealthDataCategory.CYCLE_TRACKING, listOf()),
                    PermissionTypesPerCategory(HealthDataCategory.NUTRITION, listOf()),
                    PermissionTypesPerCategory(HealthDataCategory.SLEEP, listOf()),
                    PermissionTypesPerCategory(
                        HealthDataCategory.VITALS,
                        listOf(FitnessPermissionType.HEART_RATE),
                    ),
                    PermissionTypesPerCategory(HealthDataCategory.WELLNESS, listOf()).takeIf {
                        Flags.mindfulness()
                    },
                )
            )
        assertThat(appDataUseCase.loadAllFitnessData()).isEqualTo(expected)
    }

    @Test
    fun loadMedicalAppData_apiReturnsEmptyList_returnEmptyList() = runTest {
        // This test case should not happen IRL because the API always returns all valid
        // MedicalPermissionTypes and makes the set of contributing data sources empty if there is
        // no data.
        Mockito.doAnswer(prepareAnswer(listOf()))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadMedicalAppData(TEST_APP_PACKAGE_NAME)
        assertThat(actual).isEqualTo(Success(listOf<PermissionTypesPerCategory>()))
    }

    @Test
    fun loadMedicalAppData_noData_returnEmptyList() = runTest {
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                    setOf(),
                )
            )
        Mockito.doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadMedicalAppData(TEST_APP_PACKAGE_NAME)
        assertThat(actual).isEqualTo(Success(listOf<PermissionTypesPerCategory>()))
    }

    @Test
    fun loadMedicalAppData_immunization_returnsImmunization() = runTest {
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                    setOf(TEST_MEDICAL_DATA_SOURCE),
                )
            )
        Mockito.doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadMedicalAppData(TEST_APP_PACKAGE_NAME)
        val expected =
            listOf(PermissionTypesPerCategory(MEDICAL, listOf(MedicalPermissionType.IMMUNIZATIONS)))
        assertThat(actual).isEqualTo(Success(expected))
    }

    @Test
    fun loadMedicalAppData_multipleContributingApps_returnsImmunization() = runTest {
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                    setOf(
                        TEST_MEDICAL_DATA_SOURCE,
                        TEST_MEDICAL_DATA_SOURCE_2,
                        TEST_MEDICAL_DATA_SOURCE_DIFFERENT_APP,
                    ),
                )
            )
        Mockito.doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadMedicalAppData(TEST_APP_PACKAGE_NAME)
        val expected =
            listOf(PermissionTypesPerCategory(MEDICAL, listOf(MedicalPermissionType.IMMUNIZATIONS)))
        assertThat(actual).isEqualTo(Success(expected))
    }

    @Test
    fun loadMedicalAppData_onlyOtherAppsContributing_returnsEmptyList() = runTest {
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                    setOf(TEST_MEDICAL_DATA_SOURCE_DIFFERENT_APP),
                )
            )
        Mockito.doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadMedicalAppData(TEST_APP_PACKAGE_NAME)
        assertThat(actual).isEqualTo(Success(listOf<PermissionTypesPerCategory>()))
    }

    @Test
    fun loadAllMedicalAppData_apiReturnsEmptyList_returnEmptyList() = runTest {
        // This test case should not happen IRL because the API always returns all valid
        // MedicalPermissionTypes and makes the set of contributing data sources empty if there is
        // no data.
        Mockito.doAnswer(prepareAnswer(listOf()))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadAllMedicalData()
        assertThat(actual).isEqualTo(Success(listOf<MedicalPermissionType>()))
    }

    @Test
    fun loadAllMedicalAppData_noData_returnEmptyList() = runTest {
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                    setOf(),
                )
            )
        Mockito.doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadAllMedicalData()
        assertThat(actual).isEqualTo(Success(listOf<MedicalPermissionType>()))
    }

    @Test
    fun loadAllMedicalAppData_immunization_returnsImmunization() = runTest {
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                    setOf(TEST_MEDICAL_DATA_SOURCE),
                )
            )
        Mockito.doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadAllMedicalData()
        val expected =
            listOf(PermissionTypesPerCategory(MEDICAL, listOf(MedicalPermissionType.IMMUNIZATIONS)))
        assertThat(actual).isEqualTo(Success(expected))
    }

    @Test
    fun loadAllMedicalAppData_multipleContributingApps_returnsImmunization() = runTest {
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                    setOf(
                        TEST_MEDICAL_DATA_SOURCE,
                        TEST_MEDICAL_DATA_SOURCE_2,
                        TEST_MEDICAL_DATA_SOURCE_DIFFERENT_APP,
                    ),
                )
            )
        Mockito.doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val actual = appDataUseCase.loadAllMedicalData()
        val expected =
            listOf(PermissionTypesPerCategory(MEDICAL, listOf(MedicalPermissionType.IMMUNIZATIONS)))
        assertThat(actual).isEqualTo(Success(expected))
    }

    private fun prepareAnswer(
        map: Map<Class<out Record>, RecordTypeInfoResponse>
    ): (InvocationOnMock) -> Map<Class<out Record>, RecordTypeInfoResponse> {
        val answer = { args: InvocationOnMock ->
            val receiver =
                args.arguments[1]
                    as OutcomeReceiver<Map<Class<out Record>, RecordTypeInfoResponse>, *>
            receiver.onResult(map)
            map
        }
        return answer
    }

    private fun prepareAnswer(
        medicalResourceTypeInfos: List<MedicalResourceTypeInfo>
    ): (InvocationOnMock) -> List<MedicalResourceTypeInfo> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[1] as OutcomeReceiver<Any?, *>
            receiver.onResult(medicalResourceTypeInfos)
            medicalResourceTypeInfos
        }
        return answer
    }
}
