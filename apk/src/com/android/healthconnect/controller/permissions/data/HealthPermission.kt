/**
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

import android.health.connect.HealthPermissions

sealed class HealthPermission {

    companion object {
        /** Special health permissions that don't represent health data types. */
        private val additionalPermissions =
            setOf(
                HealthPermissions.READ_EXERCISE_ROUTES,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                HealthPermissions.READ_HEALTH_DATA_HISTORY)

        /** Permissions that are grouped separately to general health data types */
        private val medicalPermissions =
            setOf(
                HealthPermissions.WRITE_MEDICAL_DATA,
                HealthPermissions.READ_MEDICAL_DATA_IMMUNIZATION)

        fun fromPermissionString(permission: String): HealthPermission {
            return if (permission in additionalPermissions) {
                AdditionalPermission.fromPermissionString(permission)
            } else if (permission in medicalPermissions) {
                MedicalPermission.fromPermissionString(permission)
            } else {
                FitnessPermission.fromPermissionString(permission)
            }
        }
    }

    /** Pair of {@link HealthPermissionType} and {@link PermissionsAccessType}. */
    data class FitnessPermission(
        val healthPermissionType: HealthPermissionType,
        val permissionsAccessType: PermissionsAccessType
    ) : HealthPermission() {
        companion object {
            private const val READ_PERMISSION_PREFIX = "android.permission.health.READ_"
            private const val WRITE_PERMISSION_PREFIX = "android.permission.health.WRITE_"

            fun fromPermissionString(permission: String): FitnessPermission {
                return if (permission.startsWith(READ_PERMISSION_PREFIX)) {
                    val type =
                        getHealthPermissionType(permission.substring(READ_PERMISSION_PREFIX.length))
                    FitnessPermission(type, PermissionsAccessType.READ)
                } else if (permission.startsWith(WRITE_PERMISSION_PREFIX)) {
                    val type =
                        getHealthPermissionType(
                            permission.substring(WRITE_PERMISSION_PREFIX.length))
                    FitnessPermission(type, PermissionsAccessType.WRITE)
                } else {
                    throw IllegalArgumentException("Fitness permission not supported! $permission")
                }
            }

            private fun getHealthPermissionType(value: String): HealthPermissionType {
                return HealthPermissionType.valueOf(value)
            }
        }

        override fun toString(): String {
            return if (permissionsAccessType == PermissionsAccessType.READ) {
                "$READ_PERMISSION_PREFIX${healthPermissionType.name}"
            } else {
                "$WRITE_PERMISSION_PREFIX${healthPermissionType.name}"
            }
        }
    }

    data class AdditionalPermission(val additionalPermission: String) : HealthPermission() {

        companion object {
            fun fromPermissionString(permission: String): AdditionalPermission =
                AdditionalPermission(permission)

            // Predefined instances for convenience
            val READ_HEALTH_DATA_HISTORY =
                AdditionalPermission(HealthPermissions.READ_HEALTH_DATA_HISTORY)
            val READ_HEALTH_DATA_IN_BACKGROUND =
                AdditionalPermission(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)
            val READ_EXERCISE_ROUTES = AdditionalPermission(HealthPermissions.READ_EXERCISE_ROUTES)
        }

        override fun toString(): String {
            return additionalPermission
        }

        fun isExerciseRoutesPermission(): Boolean =
            this.additionalPermission == HealthPermissions.READ_EXERCISE_ROUTES

        fun isBackgroundReadPermission(): Boolean =
            this.additionalPermission == HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND

        fun isHistoryReadPermission(): Boolean =
            this.additionalPermission == HealthPermissions.READ_HEALTH_DATA_HISTORY
    }

    data class MedicalPermission(val medicalPermissionType: MedicalPermissionType) :
        HealthPermission() {
        companion object {
            private const val WRITE_MEDICAL_DATA =
                "android.permission.health.WRITE_MEDICAL_DATA"
            private const val READ_MEDICAL_DATA_PREFIX =
                "android.permission.health.READ_MEDICAL_DATA_"

            fun fromPermissionString(permission: String): MedicalPermission {
                return if (permission == WRITE_MEDICAL_DATA) {
                    MedicalPermission(MedicalPermissionType.ALL_MEDICAL_DATA)
                } else if (permission.startsWith(READ_MEDICAL_DATA_PREFIX)) {
                    val medicalType =
                        getHealthPermissionType(
                            permission.substring(READ_MEDICAL_DATA_PREFIX.length))
                    MedicalPermission(medicalType)
                } else {
                    throw IllegalArgumentException(" Medical permission not supported! $permission")
                }
            }

            private fun getHealthPermissionType(value: String): MedicalPermissionType {
                return MedicalPermissionType.valueOf(value)
            }
        }

        override fun toString(): String {
            return if (medicalPermissionType == MedicalPermissionType.ALL_MEDICAL_DATA) {
                WRITE_MEDICAL_DATA
            } else {
                "$READ_MEDICAL_DATA_PREFIX${medicalPermissionType.name}"
            }
        }
    }
}
