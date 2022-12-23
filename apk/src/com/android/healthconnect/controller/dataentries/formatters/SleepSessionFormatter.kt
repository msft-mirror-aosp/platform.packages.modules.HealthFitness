/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.healthconnect.datatypes.SleepSessionRecord
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.dataentries.formatters.DurationFormatter.formatDurationLong
import com.android.healthconnect.controller.dataentries.formatters.DurationFormatter.formatDurationShort
import com.android.healthconnect.controller.dataentries.formatters.shared.SessionFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import javax.inject.Inject

/** Formatter for printing SleepSessionRecord data. */
class SleepSessionFormatter @Inject constructor(@ApplicationContext private val context: Context) :
    SessionFormatter<SleepSessionRecord>(context) {

    override suspend fun formatValue(
        record: SleepSessionRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatSleepSession(record) { duration -> formatDurationShort(context, duration) }
    }

    override suspend fun formatA11yValue(
        record: SleepSessionRecord,
        unitPreferences: UnitPreferences
    ): String {
        return formatSleepSession(record) { duration -> formatDurationLong(context, duration) }
    }

    override fun getNotes(record: SleepSessionRecord): String? {
        return record.notes?.toString()
    }

    private fun formatSleepSession(
        record: SleepSessionRecord,
        formatDuration: (duration: Duration) -> String
    ): String {
        return if (!record.title.isNullOrBlank() && !record.notes.isNullOrBlank()) {
            context.getString(R.string.sleep_session_with_notes, record.title, record.notes)
        } else if (!record.title.isNullOrBlank()) {
            context.getString(R.string.sleep_session_with_one_field, record.title)
        } else if (!record.notes.isNullOrBlank()) {
            context.getString(R.string.sleep_session_with_one_field, record.notes)
        } else {
            val duration = Duration.between(record.startTime, record.endTime)
            context.getString(R.string.sleep_session_default, formatDuration(duration))
        }
    }
}