/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.controller.permissions.data

import android.health.connect.HealthPermissions
import androidx.annotation.StringRes
import com.android.healthconnect.controller.R
import com.google.common.collect.ImmutableMap

/** Represents the display strings used for Additional Permissions. */
data class AdditionalPermissionStrings(
    @StringRes val permissionTitle: Int,
    @StringRes val permissionDescription: Int,
    @StringRes val requestTitle: Int,
    @StringRes val requestDescription: Int,
    @StringRes val permissionDescriptionFallback: Int = 0,
    @StringRes val requestDescriptionFallback: Int = 0
) {

    companion object {
        fun fromAdditionalPermission(
            additionalPermission: HealthPermission.AdditionalPermission
        ): AdditionalPermissionStrings {
            return ADDITIONAL_PERMISSION_STRINGS[additionalPermission.additionalPermission]
                ?: throw IllegalArgumentException(
                    "No strings for additional permission $additionalPermission")
        }
    }
}

private val ADDITIONAL_PERMISSION_STRINGS: ImmutableMap<String, AdditionalPermissionStrings> =
    ImmutableMap.Builder<String, AdditionalPermissionStrings>()
        .put(
            HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
            AdditionalPermissionStrings(
                R.string.background_read_title,
                R.string.background_read_description,
                R.string.background_read_request_title,
                R.string.background_read_request_description))
        .put(
            HealthPermissions.READ_HEALTH_DATA_HISTORY,
            AdditionalPermissionStrings(
                R.string.historic_access_title,
                R.string.historic_access_description,
                R.string.historic_access_request_title,
                R.string.historic_access_request_description,
                R.string.historic_access_description_fallback,
                R.string.historic_access_request_description_fallback))
        .buildOrThrow()
