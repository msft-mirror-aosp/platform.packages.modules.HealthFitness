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
package com.android.healthconnect.controller.utils

import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_DAY
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_MONTH
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod.PERIOD_WEEK
import java.time.Instant
import java.time.Period

/**
 * Formats [startTime] and [period] as follows:
 * * Day (if useWeekday): "Sun, Aug 20" or "Mon, Aug 20, 2022"
 * * Day (if not useWeekday): "Aug 20" or "Aug 20, 2022"
 * * Week: "Aug 21-27" or "Aug 21-27, 2022"
 * * Month: "August" or "August 2022"
 */
fun formatDateTimeForTimePeriod(
    startTime: Instant,
    period: DateNavigationPeriod,
    dateFormatter: LocalDateTimeFormatter,
    timeSource: TimeSource,
    useWeekday: Boolean = true,
): String {
    if (
        areInSameYear(startTime, Instant.ofEpochMilli(timeSource.currentTimeMillis()), timeSource)
    ) {
        return when (period) {
            PERIOD_DAY -> {
                if (useWeekday) {
                    dateFormatter.formatWeekdayDateWithoutYear(startTime)
                } else {
                    dateFormatter.formatShortDateWithoutYear(startTime)
                }
            }
            PERIOD_WEEK -> {
                dateFormatter.formatDateRangeWithoutYear(
                    startTime,
                    startTime.plus(Period.ofWeeks(1)),
                )
            }
            PERIOD_MONTH -> {
                dateFormatter.formatMonthWithoutYear(startTime)
            }
        }
    }
    return when (period) {
        PERIOD_DAY -> {
            if (useWeekday) {
                dateFormatter.formatWeekdayDateWithYear(startTime)
            } else {
                dateFormatter.formatShortDateWithYear(startTime)
            }
        }
        PERIOD_WEEK -> {
            dateFormatter.formatDateRangeWithYear(startTime, startTime.plus(Period.ofWeeks(1)))
        }
        PERIOD_MONTH -> {
            dateFormatter.formatMonthWithYear(startTime)
        }
    }
}

/** Whether [instant1] and [instant2] are inn the same calendar year. */
private fun areInSameYear(instant1: Instant, instant2: Instant, timeSource: TimeSource): Boolean {
    val year1 = instant1.atZone(timeSource.deviceZoneOffset()).toLocalDate().year
    val year2 = instant2.atZone(timeSource.deviceZoneOffset()).toLocalDate().year
    return year1 == year2
}
