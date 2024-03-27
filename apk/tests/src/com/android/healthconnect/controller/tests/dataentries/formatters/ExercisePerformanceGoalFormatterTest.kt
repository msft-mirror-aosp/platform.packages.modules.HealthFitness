/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.health.connect.datatypes.ExercisePerformanceGoal.AmrapGoal
import android.health.connect.datatypes.ExercisePerformanceGoal.CadenceGoal
import android.health.connect.datatypes.ExercisePerformanceGoal.HeartRateGoal
import android.health.connect.datatypes.ExercisePerformanceGoal.PowerGoal
import android.health.connect.datatypes.ExercisePerformanceGoal.RateOfPerceivedExertionGoal
import android.health.connect.datatypes.ExercisePerformanceGoal.SpeedGoal
import android.health.connect.datatypes.ExercisePerformanceGoal.UnknownGoal
import android.health.connect.datatypes.ExercisePerformanceGoal.WeightGoal
import android.health.connect.datatypes.units.Mass
import android.health.connect.datatypes.units.Power
import android.health.connect.datatypes.units.Velocity
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.ExercisePerformanceGoalFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class ExercisePerformanceGoalFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: ExercisePerformanceGoalFormatter
    @Inject lateinit var unitPreferences: UnitPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        hiltRule.inject()
    }

    @Test
    fun formatGoal_weightPerformanceGoal() = runBlocking {
        Truth.assertThat(
                formatter.formatGoal(
                    WeightGoal(Mass.fromGrams(1000.0)), unitPreferences = unitPreferences))
            .isEqualTo(
                FormattedEntry.ExercisePerformanceGoalEntry(
                    WeightGoal(Mass.fromGrams(1000.0)), title = "2.2 lb", titleA11y = "2.2 pounds"))
    }

    @Test
    fun formatGoal_powerPerformanceGoal() = runBlocking {
        Truth.assertThat(
                formatter.formatGoal(
                    PowerGoal(Power.fromWatts(30.0), Power.fromWatts(100.0)),
                    unitPreferences = unitPreferences))
            .isEqualTo(
                FormattedEntry.ExercisePerformanceGoalEntry(
                    PowerGoal(Power.fromWatts(30.0), Power.fromWatts(100.0)),
                    title = "30 W - 100 W",
                    titleA11y = "30 watts - 100 watts"))
    }

    @Test
    fun formatGoal_amrapPerformanceGoal() = runBlocking {
        Truth.assertThat(
                formatter.formatGoal(AmrapGoal.INSTANCE, unitPreferences = unitPreferences))
            .isEqualTo(
                FormattedEntry.ExercisePerformanceGoalEntry(
                    AmrapGoal.INSTANCE,
                    title = "As many reps as possible",
                    titleA11y = "As many reps as possible"))
    }

    @Test
    fun formatGoal_cadencePerformanceGoal() = runBlocking {
        Truth.assertThat(
                formatter.formatGoal(CadenceGoal(50.0, 60.0), unitPreferences = unitPreferences))
            .isEqualTo(
                FormattedEntry.ExercisePerformanceGoalEntry(
                    CadenceGoal(50.0, 60.0),
                    title = "50 rpm - 60 rpm",
                    titleA11y = "50 revolutions per minute - 60 revolutions per minute"))
    }

    @Test
    fun formatGoal_speedPerformanceGoal() = runBlocking {
        Truth.assertThat(
                formatter.formatGoal(
                    SpeedGoal(
                        Velocity.fromMetersPerSecond(25.0), Velocity.fromMetersPerSecond(15.0)),
                    unitPreferences = unitPreferences))
            .isEqualTo(
                FormattedEntry.ExercisePerformanceGoalEntry(
                    SpeedGoal(
                        Velocity.fromMetersPerSecond(25.0), Velocity.fromMetersPerSecond(15.0)),
                    title = "90 km/h - 54 km/h",
                    titleA11y = "90 kilometres per hour - 54 kilometres per hour"))
    }

    @Test
    fun formatGoal_heartRatePerformanceGoal() = runBlocking {
        Truth.assertThat(
                formatter.formatGoal(HeartRateGoal(100, 150), unitPreferences = unitPreferences))
            .isEqualTo(
                FormattedEntry.ExercisePerformanceGoalEntry(
                    HeartRateGoal(100, 150),
                    title = "100 bpm - 150 bpm",
                    titleA11y = "100 beats per minute - 150 beats per minute"))
    }

    @Test
    fun formatGoal_rateOfPerceivedExertionGoalPerformanceGoal() = runBlocking {
        Truth.assertThat(
                formatter.formatGoal(
                    RateOfPerceivedExertionGoal(4), unitPreferences = unitPreferences))
            .isEqualTo(
                FormattedEntry.ExercisePerformanceGoalEntry(
                    RateOfPerceivedExertionGoal(4),
                    title = "Effort level: 4/10",
                    titleA11y = "Effort level: 4/10"))
    }

    @Test
    fun formatGoal_unknownPerformanceGoal(): Unit = runBlocking {
        Truth.assertThat(
                formatter.formatGoal(UnknownGoal.INSTANCE, unitPreferences = unitPreferences))
            .isEqualTo(
                FormattedEntry.ExercisePerformanceGoalEntry(
                    UnknownGoal.INSTANCE, title = "", titleA11y = ""))
    }
}
