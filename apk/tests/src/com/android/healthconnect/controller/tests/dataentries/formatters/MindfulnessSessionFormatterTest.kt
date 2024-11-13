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
 *
 *
 */

package com.android.healthconnect.controller.tests.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.Metadata
import android.health.connect.datatypes.MindfulnessSessionRecord
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.MindfulnessSessionFormatter
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.tests.utils.ClearTimeFormatRule
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.android.healthfitness.flags.Flags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@EnableFlags(Flags.FLAG_MINDFULNESS)
@HiltAndroidTest
class MindfulnessSessionFormatterTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @get:Rule val clearTimeFormatRule = ClearTimeFormatRule()

    @Inject lateinit var formatter: MindfulnessSessionFormatter
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        hiltRule.inject()
    }

    @Test
    fun format_emptyRecord() = runBlocking {
        val startTime = Instant.parse("2022-10-20T07:06:05.432Z")
        val record =
            MindfulnessSessionRecord.Builder(
                    Metadata.Builder().build(),
                    startTime,
                    startTime.plus(Duration.ofMinutes(16).plusSeconds(40)),
                    MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_UNKNOWN,
                )
                .build()

        assertThat(formatter.format(record, "com.app.name"))
            .isEqualTo(
                FormattedEntry.ExerciseSessionEntry(
                    uuid = "",
                    header = "07:06 - 07:22 • com.app.name",
                    headerA11y = "from 07:06 to 07:22 • com.app.name",
                    title = "Unknown type • 16 m",
                    titleA11y = "Unknown type • 16 minutes",
                    dataType = DataType.MINDFULNESS_SESSION,
                    notes = null,
                    route = null,
                    isClickable = false,
                )
            )
    }

    @Test
    fun fullRecord() = runBlocking {
        val record =
            MindfulnessSessionRecord.Builder(
                    getMetaData(),
                    NOW,
                    NOW.plus(Duration.ofMinutes(16).plusSeconds(40)),
                    MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION,
                )
                .setTitle("foo-title")
                .setNotes("foo-notes")
                .setStartZoneOffset(ZoneOffset.ofHours(1))
                .setEndZoneOffset(ZoneOffset.ofHours(2))
                .build()

        assertThat(formatter.format(record, "com.app.name"))
            .isEqualTo(
                FormattedEntry.ExerciseSessionEntry(
                    uuid = "test_id",
                    header = "07:06 - 07:22 • com.app.name",
                    headerA11y = "from 07:06 to 07:22 • com.app.name",
                    title = "Meditation • foo-title",
                    titleA11y = "Meditation • foo-title",
                    dataType = DataType.MINDFULNESS_SESSION,
                    notes = "foo-notes",
                    route = null,
                    isClickable = false,
                )
            )
    }
}
