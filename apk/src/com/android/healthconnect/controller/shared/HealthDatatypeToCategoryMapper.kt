/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.healthconnect.controller.shared

import android.health.connect.datatypes.Record
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.safelyFromFitnessPermissionType

fun dataTypeToCategory(dataType: Class<out Record>): @HealthDataCategoryInt Int {
    return safelyDataTypeToCategory(dataType)
        ?: throw UnsupportedOperationException("This data type is not mapped to any category.")
}

fun safelyDataTypeToCategory(dataType: Class<out Record>): @HealthDataCategoryInt Int? {
    return HealthPermissionToDatatypeMapper.getAllDataTypes()
        .filterValues { it.contains(dataType) }
        .keys
        .firstOrNull()
        ?.let { safelyFromFitnessPermissionType(it) }
}
