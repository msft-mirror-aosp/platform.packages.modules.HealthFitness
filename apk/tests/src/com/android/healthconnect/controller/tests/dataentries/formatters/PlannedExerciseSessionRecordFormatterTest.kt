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
import android.health.connect.datatypes.DataOrigin
import android.health.connect.datatypes.ExerciseCompletionGoal
import android.health.connect.datatypes.ExercisePerformanceGoal
import android.health.connect.datatypes.ExerciseSegmentType
import android.health.connect.datatypes.ExerciseSessionType
import android.health.connect.datatypes.Metadata
import android.health.connect.datatypes.PlannedExerciseBlock
import android.health.connect.datatypes.PlannedExerciseSessionRecord
import android.health.connect.datatypes.PlannedExerciseStep
import android.health.connect.datatypes.units.Length
import android.health.connect.datatypes.units.Velocity
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.formatters.PlannedExerciseSessionRecordFormatter
import com.android.healthconnect.controller.tests.utils.START_TIME
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PlannedExerciseSessionRecordFormatterTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: PlannedExerciseSessionRecordFormatter
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.UK)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        hiltRule.inject()
    }

    @Test
    fun formatTitle() = runBlocking {
        val exerciseBlocks =
            listOf(
                getPlannedExerciseBlock(
                    repetitions = 1,
                    description = "Warm up",
                    exerciseSteps =
                        listOf(
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
                                            Velocity.fromMetersPerSecond(15.0)))))),
                getPlannedExerciseBlock(
                    repetitions = 1,
                    description = "Main set",
                    exerciseSteps =
                        listOf(
                            getPlannedExerciseStep(
                                exerciseSegmentType =
                                    ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING,
                                completionGoal =
                                    ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(4000.0)),
                                performanceGoals =
                                    listOf(
                                        ExercisePerformanceGoal.HeartRateGoal(150, 180),
                                        ExercisePerformanceGoal.SpeedGoal(
                                            Velocity.fromMetersPerSecond(50.0),
                                            Velocity.fromMetersPerSecond(25.0)))))))

        assertThat(
                formatter.formatTitle(
                    getPlannedExerciseSessionRecord(
                        title = "Morning Run",
                        note = "Morning quick run by the park",
                        exerciseBlocks = exerciseBlocks)))
            .isEqualTo("Running • Morning Run")
    }

    @Test
    fun formatRecordDetails() = runBlocking {
        val exerciseBlock1 =
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
        val exerciseBlock2 =
            getPlannedExerciseBlock(
                repetitions = 1,
                description = "Main set",
                exerciseSteps =
                    listOf(
                        getPlannedExerciseStep(
                            exerciseSegmentType = ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING,
                            completionGoal =
                                ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(4000.0)),
                            performanceGoals =
                                listOf(
                                    ExercisePerformanceGoal.HeartRateGoal(150, 180),
                                    ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(50.0),
                                        Velocity.fromMetersPerSecond(25.0))))))
        val exerciseBlocks = listOf(exerciseBlock1, exerciseBlock2)

        assertThat(
                formatter.formatRecordDetails(
                    getPlannedExerciseSessionRecord(
                        title = "Morning Run",
                        note = "Morning quick run by the park",
                        exerciseBlocks = exerciseBlocks)))
            .isEqualTo(
                listOf(
                    FormattedEntry.PlannedExerciseBlockEntry(
                        block = exerciseBlock1, title = "Warm up x1", titleA11y = "Warm up 1 time"),
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
                    FormattedEntry.PlannedExerciseBlockEntry(
                        block = exerciseBlock2,
                        title = "Main set x1",
                        titleA11y = "Main set 1 time"),
                    FormattedEntry.PlannedExerciseStepEntry(
                        step =
                            getPlannedExerciseStep(
                                exerciseSegmentType =
                                    ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING,
                                completionGoal =
                                    ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(4000.0)),
                                performanceGoals =
                                    listOf(
                                        ExercisePerformanceGoal.HeartRateGoal(150, 180),
                                        ExercisePerformanceGoal.SpeedGoal(
                                            Velocity.fromMetersPerSecond(50.0),
                                            Velocity.fromMetersPerSecond(25.0)))),
                        title = "4 km Running",
                        titleA11y = "4 kilometres Running")))
    }

    private fun getPlannedExerciseSessionRecord(
        title: String,
        note: String,
        exerciseBlocks: List<PlannedExerciseBlock>
    ): PlannedExerciseSessionRecord {
        return basePlannedExerciseSession(ExerciseSessionType.EXERCISE_SESSION_TYPE_RUNNING)
            .setTitle(title)
            .setNotes(note)
            .setBlocks(exerciseBlocks)
            .build()
    }

    private fun basePlannedExerciseSession(
        exerciseType: Int
    ): PlannedExerciseSessionRecord.Builder {
        val builder: PlannedExerciseSessionRecord.Builder =
            PlannedExerciseSessionRecord.Builder(
                buildMetadata(null), exerciseType, START_TIME, START_TIME.plusSeconds(3600))
        builder.setNotes("Sample training plan notes")
        builder.setTitle("Training plan title")
        builder.setStartZoneOffset(ZoneOffset.UTC)
        builder.setEndZoneOffset(ZoneOffset.UTC)
        return builder
    }

    private fun buildMetadata(clientRecordId: String? = null): Metadata {
        return Metadata.Builder()
            .setDataOrigin(
                DataOrigin.Builder().setPackageName("android.healthconnect.platform").build())
            .setId(UUID.randomUUID().toString())
            .setClientRecordId(clientRecordId)
            .setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED)
            .build()
    }

    private fun getPlannedExerciseBlock(
        repetitions: Int,
        description: String,
        exerciseSteps: List<PlannedExerciseStep>
    ): PlannedExerciseBlock {
        return PlannedExerciseBlock.Builder(repetitions)
            .setDescription(description)
            .setSteps(exerciseSteps)
            .build()
    }

    private fun getPlannedExerciseStep(
        exerciseSegmentType: Int,
        completionGoal: ExerciseCompletionGoal,
        performanceGoals: List<ExercisePerformanceGoal>
    ): PlannedExerciseStep {
        return PlannedExerciseStep.Builder(
                exerciseSegmentType, PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE, completionGoal)
            .setPerformanceGoals(performanceGoals)
            .build()
    }
}
