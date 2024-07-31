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
package com.android.healthconnect.controller.tests.data.alldata.medical

import android.content.pm.ActivityInfo
import android.health.connect.HealthConnectManager
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.data.alldata.medical.MedicalAllDataFragment
import com.android.healthconnect.controller.data.alldata.medical.MedicalAllDataViewModel
import com.android.healthconnect.controller.data.alldata.medical.MedicalAllDataViewModel.AllDataState.WithData
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType.IMMUNIZATION
import com.android.healthconnect.controller.tests.utils.launchFragment
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before

import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class MedicalAllDataFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    var manager: HealthConnectManager = mock(HealthConnectManager::class.java)

    @BindValue val viewModel: MedicalAllDataViewModel = mock(
        MedicalAllDataViewModel::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun medicalAllDataFragment_noData_noDataMessageDisplayed() {
        Mockito.`when`(viewModel.allData).then { MutableLiveData(WithData(listOf())) }

        val scenario = launchFragment<MedicalAllDataFragment>()
        scenario.onActivity { activity ->
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        onView(withText("No data")).check(matches(isDisplayed()))
        onView(withText("Data from apps with access to Health Connect will show here"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun medicalAllDataFragment_dataPresent_populatedDataTypesDisplayed() {
        Mockito.`when`(viewModel.allData).then { MutableLiveData(WithData(listOf(IMMUNIZATION))) }

        launchFragment<MedicalAllDataFragment>()

        onView(withText("Immunization")).check(matches(isDisplayed()))
        onView(withText("Distance")).check(doesNotExist())
        onView(withText("No data")).check(doesNotExist())
    }
}
