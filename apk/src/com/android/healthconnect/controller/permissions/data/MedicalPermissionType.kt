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

import android.content.Context
import android.graphics.drawable.Drawable
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PROBLEMS
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PROCEDURES
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.AttributeResolver

enum class MedicalPermissionType : HealthPermissionType {
    ALL_MEDICAL_DATA,
    ALLERGY_INTOLERANCE,
    IMMUNIZATION,
    LABORATORY_RESULTS,
    PREGNANCY,
    PROBLEMS,
    PROCEDURES,
    SOCIAL_HISTORY,
    VITAL_SIGNS;

    override fun lowerCaseLabel(): Int =
        MedicalPermissionStrings.fromPermissionType(this).lowercaseLabel

    override fun upperCaseLabel(): Int =
        MedicalPermissionStrings.fromPermissionType(this).uppercaseLabel

    override fun icon(context: Context): Drawable? {
        val attrRes: Int =
            when (this) {
                ALLERGY_INTOLERANCE -> R.attr.allergiesIcon
                IMMUNIZATION -> R.attr.immunizationIcon
                else -> throw IllegalArgumentException("PermissionType $this is not supported.")
            }
        return AttributeResolver.getDrawable(context, attrRes)
    }
}

fun isValidMedicalPermissionType(permissionTypeString: String): Boolean {
    try {
        MedicalPermissionType.valueOf(permissionTypeString)
    } catch (e: IllegalArgumentException) {
        return false
    }
    return true
}

fun fromMedicalResourceType(medicalResourceType: Int): MedicalPermissionType {
    return when (medicalResourceType) {
        MEDICAL_RESOURCE_TYPE_UNKNOWN ->
            throw IllegalArgumentException("MedicalResourceType is UNKNOWN.")
        MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE -> MedicalPermissionType.ALLERGY_INTOLERANCE
        MEDICAL_RESOURCE_TYPE_IMMUNIZATION -> MedicalPermissionType.IMMUNIZATION
        MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS -> MedicalPermissionType.LABORATORY_RESULTS
        MEDICAL_RESOURCE_TYPE_PREGNANCY -> MedicalPermissionType.PREGNANCY
        MEDICAL_RESOURCE_TYPE_PROBLEMS -> MedicalPermissionType.PROBLEMS
        MEDICAL_RESOURCE_TYPE_PROCEDURES -> MedicalPermissionType.PROCEDURES
        MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY -> MedicalPermissionType.SOCIAL_HISTORY
        MEDICAL_RESOURCE_TYPE_VITAL_SIGNS -> MedicalPermissionType.VITAL_SIGNS
        else -> throw IllegalArgumentException("MedicalResourceType is not supported.")
    }
}

fun toMedicalResourceType(medicalPermissionType: MedicalPermissionType): Int {
    return when (medicalPermissionType) {
        MedicalPermissionType.ALLERGY_INTOLERANCE -> MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE
        MedicalPermissionType.IMMUNIZATION -> MEDICAL_RESOURCE_TYPE_IMMUNIZATION
        MedicalPermissionType.LABORATORY_RESULTS -> MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS
        MedicalPermissionType.PREGNANCY -> MEDICAL_RESOURCE_TYPE_PREGNANCY
        MedicalPermissionType.PROBLEMS -> MEDICAL_RESOURCE_TYPE_PROBLEMS
        MedicalPermissionType.PROCEDURES -> MEDICAL_RESOURCE_TYPE_PROCEDURES
        MedicalPermissionType.SOCIAL_HISTORY -> MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY
        MedicalPermissionType.VITAL_SIGNS -> MEDICAL_RESOURCE_TYPE_VITAL_SIGNS
        else -> MEDICAL_RESOURCE_TYPE_UNKNOWN
    }
}
