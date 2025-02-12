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
package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.ActivityIntensityRecord
import android.health.connect.datatypes.ActivityIntensityRecord.ACTIVITY_INTENSITY_TYPE_MODERATE
import android.health.connect.datatypes.ActivityIntensityRecord.ACTIVITY_INTENSITY_TYPE_VIGOROUS
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.shared.EntryFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing [ActivityIntensityRecord] data. */
class ActivityIntensityFormatter
@Inject
constructor(@ApplicationContext private val context: Context) :
    EntryFormatter<ActivityIntensityRecord>(context) {

    override suspend fun formatValue(
        record: ActivityIntensityRecord,
        unitPreferences: UnitPreferences,
    ): String {
        return when (record.activityIntensityType) {
            ACTIVITY_INTENSITY_TYPE_MODERATE ->
                return context.getString(R.string.activity_intensity_type_moderate)

            ACTIVITY_INTENSITY_TYPE_VIGOROUS ->
                return context.getString(R.string.activity_intensity_type_vigorous)

            else -> context.getString(R.string.unknown_type)
        }
    }

    override suspend fun formatA11yValue(
        record: ActivityIntensityRecord,
        unitPreferences: UnitPreferences,
    ): String {
        return formatValue(record, unitPreferences)
    }
}
