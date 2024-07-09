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

import android.health.connect.MedicalPermissionCategory

enum class MedicalPermissionType(val category: Int)  {
    ALL_MEDICAL_DATA(MedicalPermissionCategory.ALL_MEDICAL_DATA),
    IMMUNIZATION(MedicalPermissionCategory.IMMUNIZATION),
}

fun fromMedicalPermissionCategory(medicalPermissionCategory: Int): MedicalPermissionType {
    return when (medicalPermissionCategory) {
        MedicalPermissionCategory.UNKNOWN ->
            throw IllegalArgumentException("MedicalPermissionType is UNKNOWN.")
        MedicalPermissionCategory.ALL_MEDICAL_DATA -> MedicalPermissionType.ALL_MEDICAL_DATA
        MedicalPermissionCategory.IMMUNIZATION -> MedicalPermissionType.IMMUNIZATION
        else -> throw IllegalArgumentException("MedicalPermissionType is not supported.")
    }
}
