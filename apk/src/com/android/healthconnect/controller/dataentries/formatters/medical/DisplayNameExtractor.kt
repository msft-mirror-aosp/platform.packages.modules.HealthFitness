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
package com.android.healthconnect.controller.dataentries.formatters.medical

import android.content.Context
import com.android.healthconnect.controller.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** Extracts the most relevant display name from the FHIR resource. */
@Singleton
class DisplayNameExtractor @Inject constructor(@ApplicationContext private val context: Context) {

    private lateinit var unknownResource: String
    private lateinit var fhirData: JSONObject

    companion object {
        private const val RESOURCE_TYPE = "resourceType"
        private const val NAME = "name"
        private const val USUAL = "usual"
        private const val OFFICIAL = "official"
        private const val USE = "use"
        private const val TEXT = "text"
        private const val FAMILY = "family"
        private const val GIVEN = "given"
        private const val CLASS = "class"
        private const val SERVICE_TYPE = "serviceType"
        private const val CODE = "code"
        private const val CODING = "coding"
        private const val DISPLAY = "display"
        private const val SYSTEM = "system"
        private const val SNOMED_SYSTEM = "http://snomed.info/sct"
        private const val LOINC_SYSTEM = "http://loinc.org"
        private const val CVX_SYSTEM = "http://hl7.org/fhir/sid/cvx"
        private const val VACCINE_CODE = "vaccineCode"
        private const val MEDICATION_CODEABLE_CONCEPT = "medicationCodeableConcept"
        private const val MEDICATION = "medication"
        private const val ALIAS = "alias"
        private const val PREFIX = "prefix"
        private const val SPECIALTY = "specialty"

        private const val PATIENT = "Patient"
        private const val ENCOUNTER = "Encounter"
        private const val CONDITION = "Condition"
        private const val PROCEDURE = "Procedure"
        private const val OBSERVATION = "Observation"
        private const val ALLERGY_INTOLERANCE = "AllergyIntolerance"
        private const val IMMUNIZATION = "Immunization"
        private const val MEDICATION_REQUEST = "MedicationRequest"
        private const val MEDICATION_STATEMENT = "MedicationStatement"
        private const val MEDICATION_RESOURCE = "Medication"
        private const val LOCATION = "Location"
        private const val ORGANIZATION = "Organization"
        private const val PRACTITIONER_ROLE = "PractitionerRole"
        private const val PRACTITIONER = "Practitioner"
    }

    fun getDisplayName(fhirResourceJson: String): String {
        unknownResource = context.getString(R.string.unkwown_resource)
        fhirData = JSONObject(fhirResourceJson)
        val resourceType = fhirData.optString(RESOURCE_TYPE)

        return when (resourceType) {
            ALLERGY_INTOLERANCE,
            CONDITION,
            MEDICATION_RESOURCE,
            PROCEDURE -> extractCodeDisplay(CODE, listOf(SNOMED_SYSTEM))
            ENCOUNTER -> extractEncounter()
            IMMUNIZATION -> extractCodeDisplay(VACCINE_CODE, listOf(CVX_SYSTEM))
            LOCATION,
            ORGANIZATION -> extractNameOrAlias()
            MEDICATION_REQUEST,
            MEDICATION_STATEMENT -> extractMedicationConcept()
            OBSERVATION -> extractCodeDisplay(CODE, listOf(LOINC_SYSTEM, SNOMED_SYSTEM))
            PATIENT -> extractPatient()
            PRACTITIONER -> extractPractitioner()
            PRACTITIONER_ROLE -> extractPractitionerRole()
            else -> unknownResource
        }
    }

    private fun extractCodeDisplay(code: String, codings: List<String>): String {
        val codeOrVaccineCode = fhirData.optJSONObject(code) ?: return unknownResource
        return codeOrVaccineCode.optString(TEXT).takeIf { !it.isNullOrEmpty() }
            ?: codeOrVaccineCode.optJSONArray(CODING)?.let { codingArray ->
                for (i in 0 until codingArray.length()) {
                    val coding = codingArray.getJSONObject(i)
                    if (coding.optString(SYSTEM) in codings) {
                        return coding.optString(DISPLAY)
                    }
                }
                codingArray.optJSONObject(0)?.optString(DISPLAY)
            }
            ?: unknownResource
    }

    private fun extractEncounter(): String {
        val classText = extractTextOrDisplay(CLASS)
        val serviceText = extractTextOrDisplay(SERVICE_TYPE)
        return if (classText == null || serviceText == null) {
            unknownResource
        } else {
            concat(classText, "-", serviceText)
        }
    }

    private fun extractTextOrDisplay(key: String) =
        (fhirData.optJSONObject(key)?.optStringOrNull(TEXT)
            ?: fhirData
                .optJSONObject(key)
                ?.optJSONArray(CODING)
                ?.optJSONObject(0)
                ?.optString(DISPLAY))

    private fun extractNameOrAlias() =
        (fhirData.optStringOrNull(NAME)
            ?: fhirData.optJSONArray(ALIAS)?.optString(0).takeIf { !it.isNullOrEmpty() }
            ?: unknownResource)

    private fun extractMedicationConcept(): String {
        val medication =
            fhirData.optJSONObject(MEDICATION_CODEABLE_CONCEPT)
                ?: fhirData.optJSONObject(MEDICATION)
                ?: return unknownResource
        return medication.optStringOrNull(TEXT)
            ?: medication.optJSONArray(CODING)?.let { codingArray ->
                for (i in 0 until codingArray.length()) {
                    val coding = codingArray.getJSONObject(i)
                    if (coding.optString(SYSTEM) == SNOMED_SYSTEM) {
                        return coding.optString(DISPLAY)
                    }
                }
                codingArray.optJSONObject(0)?.optString(DISPLAY)
            }
            ?: unknownResource
    }

    private fun extractPatient(): String {
        val names = fhirData.optJSONArray(NAME) ?: return unknownResource
        for (i in 0 until names.length()) {
            val name = names.getJSONObject(i)
            if (name.optString(USE) == USUAL) {
                return name.optString(TEXT)
                    ?: concat(name.optJSONArray(GIVEN)?.optString(0), name.optString(FAMILY))
            } else if (name.optString(USE) == OFFICIAL) {
                return name.optString(TEXT)
                    ?: concat(name.optJSONArray(GIVEN)?.optString(0), name.optString(FAMILY))
            }
        }
        if (names.length() > 0) {
            val firstName = names.getJSONObject(0)
            return firstName.optString(TEXT).takeIf { !it.isNullOrEmpty() }
                ?: concat(firstName.optJSONArray(GIVEN)?.optString(0), firstName.optString(FAMILY))
        }
        return unknownResource
    }

    private fun extractPractitioner(): String {
        val names = fhirData.optJSONArray(NAME) ?: return unknownResource
        for (i in 0 until names.length()) {
            val name = names.getJSONObject(i)
            if (name.optString(USE) == USUAL) {
                return concatWholeName(name)
            } else if (name.optString(USE) == OFFICIAL) {
                return concatWholeName(name)
            }
        }
        if (names.length() > 0) {
            val name = names.getJSONObject(0)
            return concatWholeName(name)
        }
        return unknownResource
    }

    private fun concatWholeName(name: JSONObject): String =
        name.optStringOrNull(TEXT)
            ?: concat(
                name.optJSONArray(PREFIX)?.optString(0),
                name.optJSONArray(GIVEN)?.optString(0),
                name.optString(FAMILY),
            )

    private fun extractPractitionerRole(): String {
        val codeText =
            fhirData.optJSONObject(CODE)?.optStringOrNull(TEXT)
                ?: fhirData
                    .optJSONObject(CODE)
                    ?.optJSONArray(CODING)
                    ?.optJSONObject(0)
                    ?.optString(DISPLAY)
        val specialtyText =
            fhirData.optJSONObject(SPECIALTY)?.optStringOrNull(TEXT)
                ?: fhirData
                    .optJSONObject(SPECIALTY)
                    ?.optJSONArray(CODING)
                    ?.optJSONObject(0)
                    ?.optString(DISPLAY)
        return if (codeText != "" && specialtyText != "") {
            concat(codeText, "-", specialtyText)
        } else {
            unknownResource
        }
    }

    private fun concat(vararg args: String?): String {
        return args.filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
    }
}

private fun JSONObject.optStringOrNull(s: String): String? {
    return this.optString(s).takeIf { it != "" }
}
