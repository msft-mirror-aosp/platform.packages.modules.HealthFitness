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
package com.android.healthconnect.controller.tests.data.rawfhir

import android.health.connect.HealthConnectManager
import androidx.core.os.bundleOf
import androidx.lifecycle.MutableLiveData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.rawfhir.RawFhirFragment
import com.android.healthconnect.controller.data.rawfhir.RawFhirViewModel
import com.android.healthconnect.controller.data.rawfhir.RawFhirViewModel.RawFhirState.Error
import com.android.healthconnect.controller.data.rawfhir.RawFhirViewModel.RawFhirState.Loading
import com.android.healthconnect.controller.data.rawfhir.RawFhirViewModel.RawFhirState.WithData
import com.android.healthconnect.controller.tests.utils.TEST_MEDICAL_RESOURCE_IMMUNIZATION
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
class RawFhirFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    var manager: HealthConnectManager = mock(HealthConnectManager::class.java)

    @BindValue val viewModel: RawFhirViewModel = mock(RawFhirViewModel::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun error_errorMessageDisplayed() {
        Mockito.`when`(viewModel.rawFhir).then { MutableLiveData(Error) }

        launchFragment<RawFhirFragment>(
            bundleOf(
                RawFhirFragment.MEDICAL_RESOURCE_ID_KEY to TEST_MEDICAL_RESOURCE_IMMUNIZATION.id
            )
        )

        onView(withText("Something went wrong. Please try again.")).check(matches(isDisplayed()))
        // onView(ViewMatchers.withId(R.id.loading)).check(doesNotExist())
    }

    @Test
    fun loading_loadingDisplayed() {
        Mockito.`when`(viewModel.rawFhir).then { MutableLiveData(Loading) }

        launchFragment<RawFhirFragment>(
            bundleOf(
                RawFhirFragment.MEDICAL_RESOURCE_ID_KEY to TEST_MEDICAL_RESOURCE_IMMUNIZATION.id
            )
        )

        onView(ViewMatchers.withId(R.id.loading)).check(matches(isDisplayed()))
        // onView(withText("Something went wrong. Please try again.")).check(doesNotExist())
        onView(withSubstring("resourceType")).check(doesNotExist())
    }

    @Test
    fun fhirResourcePresent_displaysFhirResource() {
        Mockito.`when`(viewModel.rawFhir).then {
            MutableLiveData(
                WithData(
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
                )
            )
        }

        launchFragment<RawFhirFragment>(
            bundleOf(
                RawFhirFragment.MEDICAL_RESOURCE_ID_KEY to TEST_MEDICAL_RESOURCE_IMMUNIZATION.id
            )
        )

        onView(withSubstring("resourceType")).check(matches(isDisplayed()))
        onView(withSubstring("display")).check(matches(isDisplayed()))
        // onView(ViewMatchers.withId(R.id.loading)).check(doesNotExist())
    }
}
