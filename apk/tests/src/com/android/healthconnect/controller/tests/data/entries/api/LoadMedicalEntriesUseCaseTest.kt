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
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.data.entries.api.LoadMedicalEntriesInput
import com.android.healthconnect.controller.data.entries.api.LoadMedicalEntriesUseCase
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@Ignore // b/343647465
class LoadMedicalEntriesUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    private lateinit var context: Context
    private lateinit var loadMedicalEntriesUseCase: LoadMedicalEntriesUseCase

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        hiltRule.inject()
        loadMedicalEntriesUseCase = LoadMedicalEntriesUseCase(Dispatchers.Main)
    }

    @Test
    fun invoke_returnsFormattedData() = runTest {
        val input =
            LoadMedicalEntriesInput(
                permissionType = MedicalPermissionType.IMMUNIZATION,
                packageName = null,
                showDataOrigin = true)

        val result = loadMedicalEntriesUseCase.invoke(input)
        assertThat(result is UseCaseResults.Success).isTrue()
        assertThat((result as UseCaseResults.Success).data)
            .containsExactlyElementsIn(listOf<FormattedEntry.FormattedMedicalDataEntry>())
    }
}
