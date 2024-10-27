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
package com.android.healthconnect.controller.tests.utiltests

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.formatDateTimeForTimePeriod
import com.android.healthconnect.controller.utils.getPeriodStartDate
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

class TimeUtilsTest {
    private lateinit var context: Context
    private lateinit var dateFormatter: LocalDateTimeFormatter
    private lateinit var timeSource: TimeSource

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        dateFormatter = LocalDateTimeFormatter(context)
        timeSource = TestTimeSource
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))
    }

    @Test
    fun getPeriodStartDate_periodDay_startTimeSameDateAsLocal() {
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY

        val expectedResult = Instant.parse("2021-09-19T00:00:00.000Z")
        val actualResult = getPeriodStartDate(startTime, period)
        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun getPeriodStartDate_periodDay_startTimeDiffDateToLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY

        val expectedResult = Instant.parse("2021-09-19T15:00:00.000Z")
        val actualResult = getPeriodStartDate(startTime, period)
        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun getPeriodStartDate_periodWeek_startTimeSameDateAsLocal() {
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK

        val expectedResult = Instant.parse("2021-09-13T00:00:00.000Z")
        val actualResult = getPeriodStartDate(startTime, period)
        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun getPeriodStartDate_periodWeek_startTimeDiffDateToLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK

        val expectedResult = Instant.parse("2021-09-19T15:00:00.000Z")
        val actualResult = getPeriodStartDate(startTime, period)
        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun getPeriodStartDate_periodMonth_startTimeSameDateAsLocal() {
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_MONTH

        val expectedResult = Instant.parse("2021-09-01T00:00:00.000Z")
        val actualResult = getPeriodStartDate(startTime, period)
        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun getPeriodStartDate_periodMonth_startTimeDiffDateToLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2021-09-30T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_MONTH

        val expectedResult = Instant.parse("2021-09-30T15:00:00.000Z")
        val actualResult = getPeriodStartDate(startTime, period)
        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodDay_withWeekday_pastYear_startTimeSameDateAsLocal() {
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY
        val useWeekday = true

        val expectedResult = "Sun, Sep 19, 2021"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodDay_withWeekday_sameYear_startTimeSameDateAsLocal() {
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2022-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY
        val useWeekday = true

        val expectedResult = "Mon, Sep 19"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodDay_withoutWeekday_pastYear_startTimeSameDateAsLocal() {
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY
        val useWeekday = false

        val expectedResult = "Sep 19, 2021"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodDay_withoutWeekday_sameYear_startTimeSameDateAsLocal() {
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2022-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY
        val useWeekday = false

        val expectedResult = "Sep 19"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodDay_withWeekday_pastYear_startTimeDiffDateAsLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY
        val useWeekday = true

        val expectedResult = "Mon, Sep 20, 2021"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodDay_withWeekday_sameYear_startTimeDiffDateAsLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2022-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY
        val useWeekday = true

        val expectedResult = "Tue, Sep 20"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodDay_withoutWeekday_pastYear_startTimeDiffDateAsLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2021-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY
        val useWeekday = false

        val expectedResult = "Sep 20, 2021"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodDay_withoutWeekday_sameYear_startTimeDiffDateAsLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2022-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_DAY
        val useWeekday = false

        val expectedResult = "Sep 20"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodWeek_sameYear_startTimeNotOnMonday_sameTimeZone() {
        // UTC
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2022-09-20T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK
        val useWeekday = true

        val expectedResult = "Sep 19 – 25"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodWeek_sameYear_startTimeOnMonday_sameTimeZone() {
        // UTC
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2022-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK
        val useWeekday = true

        val expectedResult = "Sep 19 – 25"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodWeek_sameYear_startTimeNotOnMonday_diffTimeZone() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2022-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK
        val useWeekday = true

        val expectedResult = "Sep 19 – 25"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodWeek_sameYear_startTimeOnMonday_diffTimeZone() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2022-09-18T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK
        val useWeekday = true

        val expectedResult = "Sep 19 – 25"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodWeek_pastYear_startTimeNotOnMonday_sameTimeZone() {
        // UTC
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2020-09-20T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK
        val useWeekday = true

        val expectedResult = "Sep 14 – 20, 2020"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodWeek_pastYear_startTimeOnMonday_sameTimeZone() {
        // UTC
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2020-09-21T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK
        val useWeekday = true

        val expectedResult = "Sep 21 – 27, 2020"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodWeek_pastYear_startTimeNotOnMonday_diffTimeZone() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2020-09-19T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK
        val useWeekday = true

        val expectedResult = "Sep 14 – 20, 2020"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodWeek_pastYear_startTimeOnMonday_diffTimeZone() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2020-09-20T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_WEEK
        val useWeekday = true

        val expectedResult = "Sep 21 – 27, 2020"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodMonth_sameYear_startTimeSameDateAsLocal() {
        // UTC
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2022-09-21T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_MONTH
        val useWeekday = true

        val expectedResult = "September"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodMonth_sameYear_startTimeDiffDateAsLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2022-09-30T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_MONTH
        val useWeekday = true

        val expectedResult = "October"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodMonth_pastYear_startTimeSameDateAsLocal() {
        // UTC
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("UTC"))
        val startTime = Instant.parse("2020-09-21T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_MONTH
        val useWeekday = true

        val expectedResult = "September 2020"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }

    @Test
    fun formatDateTimeForTimePeriod_periodMonth_pastYear_startTimeDiffDateAsLocal() {
        // UTC + 9
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo")))
        (timeSource as TestTimeSource).setNow(NOW)
        (timeSource as TestTimeSource).setDeviceZoneOffset(ZoneId.of("Asia/Tokyo"))
        val startTime = Instant.parse("2020-09-30T20:00:00.000Z")
        val period = DateNavigationPeriod.PERIOD_MONTH
        val useWeekday = true

        val expectedResult = "October 2020"
        val actualResult =
            formatDateTimeForTimePeriod(startTime, period, dateFormatter, timeSource, useWeekday)

        assertThat(actualResult).isEqualTo(expectedResult)
    }
}
