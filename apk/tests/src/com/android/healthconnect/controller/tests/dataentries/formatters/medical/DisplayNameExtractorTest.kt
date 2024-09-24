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
package com.android.healthconnect.controller.tests.dataentries.formatters.medical

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.medical.DisplayNameExtractor
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class DisplayNameExtractorTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var displayNameExtractor: DisplayNameExtractor
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context

        hiltRule.inject()
    }

    @Test
    fun unknownResourceType() {
        val json =
            """{
            "resourceType": "UnknownResource"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    // AllergyIntolerance tests
    @Test
    fun allergyIntolerance_withoutCodeField() {
        val json =
            """{
            "resourceType": "AllergyIntolerance"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun allergyIntolerance_withEmptyCodeField() {
        val json =
            """{
            "resourceType": "AllergyIntolerance",
            "code": {}
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun allergyIntolerance_withCodeText() {
        val json =
            """{
            "resourceType": "AllergyIntolerance",
            "code": {"text": "Peanut Allergy"}
        }"""
        assertEquals("Peanut Allergy", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun allergyIntolerance_withSnomedCoding() {
        val json =
            """{
            "resourceType": "AllergyIntolerance",
            "code": {
                "coding": [{"system": "http://snomed.info/sct", "display": "Dust Allergy"}]
            }
        }"""
        assertEquals("Dust Allergy", displayNameExtractor.getDisplayName(json))
    }

    // Condition tests
    @Test
    fun condition_withoutCodeField() {
        val json =
            """{
            "resourceType": "Condition"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun condition_withEmptyCodeField() {
        val json =
            """{
            "resourceType": "Condition",
            "code": {}
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun condition_withCodeText() {
        val json =
            """{
            "resourceType": "Condition",
            "code": {"text": "Hypertension"}
        }"""
        assertEquals("Hypertension", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun condition_withCodingSystemSnomed() {
        val json =
            """{
            "resourceType": "Condition",
            "code": {
                "coding": [{"system": "http://snomed.info/sct", "display": "Diabetes"}]
            }
        }"""
        assertEquals("Diabetes", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun condition_withMultipleCodings() {
        val json =
            """{
            "resourceType": "Condition",
            "code": {
                "coding": [
                    {"system": "http://other.system", "display": "Other Condition"},
                    {"system": "http://snomed.info/sct", "display": "Asthma"}
                ]
            }
        }"""
        assertEquals("Asthma", displayNameExtractor.getDisplayName(json))
    }

    // Encounter tests
    @Test
    fun encounter_withoutClassOrServiceType() {
        val json =
            """{
            "resourceType": "Encounter"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun encounter_withEmptyClassAndServiceType() {
        val json =
            """{
            "resourceType": "Encounter",
            "class": {},
            "serviceType": {}
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun encounter_withClassAndServiceType() {
        val json =
            """{
            "resourceType": "Encounter",
            "class": {"text": "Inpatient"},
            "serviceType": {"text": "Cardiology"}
        }"""
        assertEquals("Inpatient - Cardiology", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun encounter_withClassCodingAndServiceTypeCoding() {
        val json =
            """{
            "resourceType": "Encounter",
            "class": {"coding": [{"display": "Outpatient"}]},
            "serviceType": {"coding": [{"display": "Radiology"}]}
        }"""
        assertEquals("Outpatient - Radiology", displayNameExtractor.getDisplayName(json))
    }

    // Immunization tests
    @Test
    fun immunization_withoutVaccineCodeField() {
        val json =
            """{
            "resourceType": "Immunization"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun immunization_withEmptyVaccineCodeField() {
        val json =
            """{
            "resourceType": "Immunization",
            "vaccineCode": {}
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun immunization_withVaccineCodeText() {
        val json =
            """{
            "resourceType": "Immunization",
            "vaccineCode": {"text": "COVID-19 Vaccine"}
        }"""
        assertEquals("COVID-19 Vaccine", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun immunization_withVaccineCoding() {
        val json =
            """{
            "resourceType": "Immunization",
            "vaccineCode": {
                "coding": [{"system": "http://hl7.org/fhir/sid/cvx", "display": "Influenza"}]
            }
        }"""
        assertEquals("Influenza", displayNameExtractor.getDisplayName(json))
    }

    // Location tests
    @Test
    fun location_withoutNameOrAliasField() {
        val json =
            """{
            "resourceType": "Location"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun location_withEmptyAliasField() {
        val json =
            """{
            "resourceType": "Location",
            "alias": []
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun location_withName() {
        val json =
            """{
            "resourceType": "Location",
            "name": "Main Hospital"
        }"""
        assertEquals("Main Hospital", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun location_withAlias() {
        val json =
            """{
            "resourceType": "Location",
            "alias": ["Emergency Room"]
        }"""
        assertEquals("Emergency Room", displayNameExtractor.getDisplayName(json))
    }

    // MedicationRequest tests
    @Test
    fun medicationRequest_withoutMedicationField() {
        val json =
            """{
            "resourceType": "MedicationRequest"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun medicationRequest_withEmptyMedicationCodeableConcept() {
        val json =
            """{
            "resourceType": "MedicationRequest",
            "medicationCodeableConcept": {}
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun medicationRequest_withMedicationText() {
        val json =
            """{
            "resourceType": "MedicationRequest",
            "medicationCodeableConcept": {"text": "Aspirin"}
        }"""
        assertEquals("Aspirin", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun medicationRequest_withSnomedCoding() {
        val json =
            """{
            "resourceType": "MedicationRequest",
            "medicationCodeableConcept": {
                "coding": [{"system": "http://snomed.info/sct", "display": "Ibuprofen"}]
            }
        }"""
        assertEquals("Ibuprofen", displayNameExtractor.getDisplayName(json))
    }

    // Medication tests
    @Test
    fun medication_withoutCodeField() {
        val json =
            """{
            "resourceType": "Medication"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun medication_withEmptyCodeField() {
        val json =
            """{
            "resourceType": "Medication",
            "code": {}
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun medication_withCodeText() {
        val json =
            """{
            "resourceType": "Medication",
            "code": {"text": "Paracetamol"}
        }"""
        assertEquals("Paracetamol", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun medication_withSnomedCoding() {
        val json =
            """{
            "resourceType": "Medication",
            "code": {
                "coding": [{"system": "http://snomed.info/sct", "display": "Amoxicillin"}]
            }
        }"""
        assertEquals("Amoxicillin", displayNameExtractor.getDisplayName(json))
    }

    // Observation tests
    @Test
    fun observation_withoutCodeField() {
        val json =
            """{
            "resourceType": "Observation"
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun observation_withEmptyCodeField() {
        val json =
            """{
            "resourceType": "Observation",
            "code": {}
        }"""
        assertEquals("Unknown Resource", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun observation_withLoincCoding() {
        val json =
            """{
            "resourceType": "Observation",
            "code": {
                "coding": [{"system": "http://loinc.org", "display": "Hemoglobin"}]
            }
        }"""
        assertEquals("Hemoglobin", displayNameExtractor.getDisplayName(json))
    }

    @Test
    fun observation_withSnomedCoding() {
        val json =
            """{
            "resourceType": "Observation",
            "code": {
                "coding": [{"system": "http://snomed.info/sct", "display": "Blood Pressure"}]
            }
        }"""
        assertEquals("Blood Pressure", displayNameExtractor.getDisplayName(json))
    }
}
