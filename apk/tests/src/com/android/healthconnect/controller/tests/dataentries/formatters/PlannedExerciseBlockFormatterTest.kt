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
package com.android.healthconnect.controller.tests.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.ExerciseCompletionGoal
import android.health.connect.datatypes.ExercisePerformanceGoal
import android.health.connect.datatypes.ExerciseSegmentType
import android.health.connect.datatypes.units.Length
import android.health.connect.datatypes.units.Velocity
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.PlannedExerciseBlockFormatter
import com.android.healthconnect.controller.dataentries.units.DistanceUnit
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.tests.utils.getPlannedExerciseBlock
import com.android.healthconnect.controller.tests.utils.getPlannedExerciseStep
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
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
class PlannedExerciseBlockFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: PlannedExerciseBlockFormatter
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
    fun formatBlock_singleRepetition() = runBlocking {
        unitPreferences.setDistanceUnit(DistanceUnit.KILOMETERS)
        val exerciseBlock =
            getPlannedExerciseBlock(
                repetitions = 1,
                description = "Warm up",
                exerciseSteps =
                    listOf(
                        getPlannedExerciseStep(
                            exerciseSegmentType = ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING,
                            completionGoal =
                                ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(1000.0)),
                            performanceGoals =
                                listOf(
                                    ExercisePerformanceGoal.HeartRateGoal(100, 150),
                                    ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(25.0),
                                        Velocity.fromMetersPerSecond(15.0))))))
        assertThat(formatter.formatBlock(exerciseBlock))
            .isEqualTo(
                FormattedEntry.PlannedExerciseBlockEntry(
                    block = exerciseBlock, title = "Warm up x1", titleA11y = "Warm up 1 time"))
    }

    @Test
    fun formatBlock_multipleRepetitions() = runBlocking {
        unitPreferences.setDistanceUnit(DistanceUnit.KILOMETERS)
        val exerciseBlock =
            getPlannedExerciseBlock(
                repetitions = 2,
                description = "Main set",
                exerciseSteps =
                    listOf(
                        getPlannedExerciseStep(
                            exerciseSegmentType = ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING,
                            completionGoal =
                                ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(1000.0)),
                            performanceGoals =
                                listOf(
                                    ExercisePerformanceGoal.HeartRateGoal(100, 150),
                                    ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(25.0),
                                        Velocity.fromMetersPerSecond(15.0)))),
                        getPlannedExerciseStep(
                            exerciseSegmentType = ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BIKING,
                            completionGoal =
                                ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(3500.0)),
                            performanceGoals =
                                listOf(
                                    ExercisePerformanceGoal.HeartRateGoal(150, 180),
                                    ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(60.0),
                                        Velocity.fromMetersPerSecond(50.0))))))
        assertThat(formatter.formatBlock(exerciseBlock))
            .isEqualTo(
                FormattedEntry.PlannedExerciseBlockEntry(
                    block = exerciseBlock, title = "Main set x2", titleA11y = "Main set 2 times"))
    }

    @Test
    fun formatBlockDetails() = runBlocking {
        unitPreferences.setDistanceUnit(DistanceUnit.KILOMETERS)
        val exerciseBlock =
            getPlannedExerciseBlock(
                repetitions = 2,
                description = "Main set",
                exerciseSteps =
                    listOf(
                        getPlannedExerciseStep(
                            exerciseSegmentType = ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING,
                            completionGoal =
                                ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(1000.0)),
                            performanceGoals =
                                listOf(
                                    ExercisePerformanceGoal.HeartRateGoal(100, 150),
                                    ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(25.0),
                                        Velocity.fromMetersPerSecond(15.0)))),
                        getPlannedExerciseStep(
                            exerciseSegmentType = ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BIKING,
                            completionGoal =
                                ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(3500.0)),
                            performanceGoals =
                                listOf(
                                    ExercisePerformanceGoal.HeartRateGoal(150, 180),
                                    ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(60.0),
                                        Velocity.fromMetersPerSecond(50.0))))))
        assertThat(formatter.formatBlockDetails(exerciseBlock, unitPreferences))
            .isEqualTo(
                listOf(
                    FormattedEntry.PlannedExerciseStepEntry(
                        step =
                            getPlannedExerciseStep(
                                exerciseSegmentType =
                                    ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING,
                                completionGoal =
                                    ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(1000.0)),
                                performanceGoals =
                                    listOf(
                                        ExercisePerformanceGoal.HeartRateGoal(100, 150),
                                        ExercisePerformanceGoal.SpeedGoal(
                                            Velocity.fromMetersPerSecond(25.0),
                                            Velocity.fromMetersPerSecond(15.0)))),
                        title = "1 km Running",
                        titleA11y = "1 kilometre Running"),
                    FormattedEntry.PlannedExerciseStepEntry(
                        step =
                            getPlannedExerciseStep(
                                exerciseSegmentType =
                                    ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BIKING,
                                completionGoal =
                                    ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(3500.0)),
                                performanceGoals =
                                    listOf(
                                        ExercisePerformanceGoal.HeartRateGoal(150, 180),
                                        ExercisePerformanceGoal.SpeedGoal(
                                            Velocity.fromMetersPerSecond(60.0),
                                            Velocity.fromMetersPerSecond(50.0)))),
                        title = "3.5 km Cycling",
                        titleA11y = "3.5 kilometres Cycling")))
    }
}
