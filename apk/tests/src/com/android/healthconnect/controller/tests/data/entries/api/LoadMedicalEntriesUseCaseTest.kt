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
package com.android.healthconnect.controller.tests.data.entries.api

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.MedicalResourceTypeInfo
import android.health.connect.datatypes.MedicalResource
import android.os.OutcomeReceiver
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.api.LoadEntriesHelper
import com.android.healthconnect.controller.data.entries.api.LoadMedicalEntriesInput
import com.android.healthconnect.controller.data.entries.api.LoadMedicalEntriesUseCase
import com.android.healthconnect.controller.dataentries.formatters.shared.HealthDataEntryFormatter
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_DATA_SOURCE
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.invocation.InvocationOnMock

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@Ignore // b/343647465
class LoadMedicalEntriesUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var loadMedicalEntriesUseCase: LoadMedicalEntriesUseCase
    private lateinit var loadEntriesHelper: LoadEntriesHelper

    @Inject lateinit var healthDataEntryFormatter: HealthDataEntryFormatter

    private val healthConnectManager: HealthConnectManager =
        Mockito.mock(HealthConnectManager::class.java)

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        hiltRule.inject()
        loadEntriesHelper =
            LoadEntriesHelper(context, healthDataEntryFormatter, healthConnectManager)
        loadMedicalEntriesUseCase = LoadMedicalEntriesUseCase(Dispatchers.Main, loadEntriesHelper)
    }

    @Test
    fun invoke_returnsFormattedData() = runTest {
        val input =
            LoadMedicalEntriesInput(
                permissionType = MedicalPermissionType.IMMUNIZATION,
                packageName = null,
                showDataOrigin = true,
            )
        val medicalResourceTypeResources: List<MedicalResourceTypeInfo> =
            listOf(
                MedicalResourceTypeInfo(
                    MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                    setOf(TEST_MEDICAL_DATA_SOURCE),
                )
            )
        Mockito.doAnswer(prepareAnswer(medicalResourceTypeResources))
            .`when`(healthConnectManager)
            .queryAllMedicalResourceTypeInfos(Matchers.any(), Matchers.any())

        val result = loadMedicalEntriesUseCase.invoke(input)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data)
            .containsExactlyElementsIn(listOf<FormattedEntry.FormattedMedicalDataEntry>())
    }

    private fun prepareAnswer(
        MedicalResourceTypeInfo: List<MedicalResourceTypeInfo>
    ): (InvocationOnMock) -> List<MedicalResourceTypeInfo> {
        val answer = { args: InvocationOnMock ->
            val receiver = args.arguments[1] as OutcomeReceiver<Any?, *>
            receiver.onResult(MedicalResourceTypeInfo)
            MedicalResourceTypeInfo
        }
        return answer
    }
}
