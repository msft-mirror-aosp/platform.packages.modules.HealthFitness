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
package com.android.healthconnect.controller.permissions.data

import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.google.common.collect.ImmutableMap

data class MedicalPermissionStrings(
    @StringRes val uppercaseLabel: Int,
    @StringRes val lowercaseLabel: Int,
    @StringRes val contentDescription: Int
) {
    companion object {
        fun fromPermissionType(
            medicalPermissionType: MedicalPermissionType
        ): MedicalPermissionStrings {
            return PERMISSION_TYPE_STRINGS[medicalPermissionType]
                ?: throw IllegalArgumentException(
                    "No strings for permission group " + medicalPermissionType.name)
        }
    }
}

private val PERMISSION_TYPE_STRINGS: ImmutableMap<MedicalPermissionType, MedicalPermissionStrings> =
    ImmutableMap.Builder<MedicalPermissionType, MedicalPermissionStrings>()
        .put(
            MedicalPermissionType.ALL_MEDICAL_DATA,
            MedicalPermissionStrings(
                R.string.all_medical_data_uppercase_label,
                R.string.all_medical_data_lowercase_label,
                R.string.all_medical_data_content_description))
        .put(
            MedicalPermissionType.ALLERGY_INTOLERANCE,
            MedicalPermissionStrings(
                R.string.allergy_intolerance_uppercase_label,
                R.string.allergy_intolerance_lowercase_label,
                R.string.allergy_intolerance_content_description))
        .put(
            MedicalPermissionType.IMMUNIZATION,
            MedicalPermissionStrings(
                R.string.immunization_uppercase_label,
                R.string.immunization_lowercase_label,
                R.string.immunization_content_description))
        .put(
            MedicalPermissionType.LABORATORY_RESULTS,
            MedicalPermissionStrings(
                R.string.laboratory_results_uppercase_label,
                R.string.laboratory_results_lowercase_label,
                R.string.laboratory_results_content_description))
        .put(
            MedicalPermissionType.PREGNANCY,
            MedicalPermissionStrings(
                R.string.pregnancy_uppercase_label,
                R.string.pregnancy_lowercase_label,
                R.string.pregnancy_content_description))
        .put(
            MedicalPermissionType.PROBLEMS,
            MedicalPermissionStrings(
                R.string.problems_uppercase_label,
                R.string.problems_lowercase_label,
                R.string.problems_content_description))
        .put(
            MedicalPermissionType.SOCIAL_HISTORY,
            MedicalPermissionStrings(
                R.string.social_history_uppercase_label,
                R.string.social_history_lowercase_label,
                R.string.social_history_content_description))
        .put(
            MedicalPermissionType.VITAL_SIGNS,
            MedicalPermissionStrings(
                R.string.vital_signs_uppercase_label,
                R.string.vital_signs_lowercase_label,
                R.string.vital_signs_content_description))
        .buildOrThrow()
