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

import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION
import android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN

enum class MedicalPermissionType : HealthPermissionType {
    ALL_MEDICAL_DATA,
    IMMUNIZATION,
    ALLERGY_INTOLERANCE;

    override fun lowerCaseLabel(): Int =
        MedicalPermissionStrings.fromPermissionType(this).lowercaseLabel

    override fun upperCaseLabel(): Int =
        MedicalPermissionStrings.fromPermissionType(this).uppercaseLabel
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
        MEDICAL_RESOURCE_TYPE_IMMUNIZATION -> MedicalPermissionType.IMMUNIZATION
        MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE -> MedicalPermissionType.ALLERGY_INTOLERANCE
        else -> throw IllegalArgumentException("MedicalResourceType is not supported.")
    }
}

fun toMedicalResourceType(medicalPermissionType: MedicalPermissionType): Int {
    return when (medicalPermissionType) {
        MedicalPermissionType.IMMUNIZATION -> MEDICAL_RESOURCE_TYPE_IMMUNIZATION
        MedicalPermissionType.ALLERGY_INTOLERANCE -> MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE
        else -> MEDICAL_RESOURCE_TYPE_UNKNOWN
    }
}
