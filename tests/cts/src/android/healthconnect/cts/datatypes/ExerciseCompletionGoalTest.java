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

package android.healthconnect.cts.datatypes;

import static android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords;

import static com.google.common.truth.Truth.assertThat;

import static java.time.Month.APRIL;
import static java.time.temporal.ChronoUnit.HOURS;

import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.ExerciseCompletionGoal;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.PlannedExerciseBlock;
import android.health.connect.datatypes.PlannedExerciseSessionRecord;
import android.health.connect.datatypes.PlannedExerciseStep;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ExerciseCompletionGoalTest {
    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        verifyDeleteRecords(
                PlannedExerciseSessionRecord.class,
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.EPOCH)
                        .setEndTime(Instant.now())
                        .build());
    }

    @Test
    public void distanceGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)))
                .isNotEqualTo(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(200)));
        assertThat(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)))
                .isEqualTo(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)));

        assertThat(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)).hashCode())
                .isNotEqualTo(
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(200)).hashCode());
        assertThat(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)).hashCode())
                .isEqualTo(
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)).hashCode());
    }

    @Test
    public void stepsGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.StepsGoal(100))
                .isNotEqualTo(new ExerciseCompletionGoal.StepsGoal(200));
        assertThat(new ExerciseCompletionGoal.StepsGoal(100))
                .isEqualTo(new ExerciseCompletionGoal.StepsGoal(100));

        assertThat(new ExerciseCompletionGoal.StepsGoal(100).hashCode())
                .isNotEqualTo(new ExerciseCompletionGoal.StepsGoal(200).hashCode());
        assertThat(new ExerciseCompletionGoal.StepsGoal(100).hashCode())
                .isEqualTo(new ExerciseCompletionGoal.StepsGoal(100).hashCode());
    }

    @Test
    public void durationGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)))
                .isNotEqualTo(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(2)));
        assertThat(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)))
                .isEqualTo(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)));

        assertThat(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)).hashCode())
                .isNotEqualTo(
                        new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(2)).hashCode());
        assertThat(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)).hashCode())
                .isEqualTo(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)).hashCode());
    }

    @Test
    public void repsGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.RepetitionsGoal(1))
                .isNotEqualTo(new ExerciseCompletionGoal.RepetitionsGoal(2));
        assertThat(new ExerciseCompletionGoal.RepetitionsGoal(1))
                .isEqualTo(new ExerciseCompletionGoal.RepetitionsGoal(1));

        assertThat(new ExerciseCompletionGoal.RepetitionsGoal(1).hashCode())
                .isNotEqualTo(new ExerciseCompletionGoal.RepetitionsGoal(2).hashCode());
        assertThat(new ExerciseCompletionGoal.RepetitionsGoal(1).hashCode())
                .isEqualTo(new ExerciseCompletionGoal.RepetitionsGoal(1).hashCode());
    }

    @Test
    public void totalCaloriesBurnedGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100)))
                .isNotEqualTo(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(
                                Energy.fromCalories(200)));
        assertThat(new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100)))
                .isEqualTo(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(
                                Energy.fromCalories(100)));

        assertThat(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100))
                                .hashCode())
                .isNotEqualTo(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(200))
                                .hashCode());
        assertThat(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100))
                                .hashCode())
                .isEqualTo(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100))
                                .hashCode());
    }

    @Test
    public void activeCaloriesBurnedGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(Energy.fromCalories(100)))
                .isNotEqualTo(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                Energy.fromCalories(200)));
        assertThat(new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(Energy.fromCalories(100)))
                .isEqualTo(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                Energy.fromCalories(100)));

        assertThat(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                        Energy.fromCalories(100))
                                .hashCode())
                .isNotEqualTo(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                        Energy.fromCalories(200))
                                .hashCode());
        assertThat(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                        Energy.fromCalories(100))
                                .hashCode())
                .isEqualTo(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                        Energy.fromCalories(100))
                                .hashCode());
    }

    @Test
    public void distanceGoal_insertionAndRead() throws InterruptedException {
        PlannedExerciseSessionRecord record =
                createPlannedSessionWithCompletionGoal(
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(500)));

        TestUtils.insertRecordAndGetId(record);

        assertThat(
                        Iterables.getOnlyElement(
                                TestUtils.readAllRecords(PlannedExerciseSessionRecord.class)))
                .isEqualTo(record);
    }

    @Test
    public void stepsGoal_insertionAndRead() throws InterruptedException {
        PlannedExerciseSessionRecord record =
                createPlannedSessionWithCompletionGoal(new ExerciseCompletionGoal.StepsGoal(250));

        TestUtils.insertRecordAndGetId(record);

        assertThat(
                        Iterables.getOnlyElement(
                                TestUtils.readAllRecords(PlannedExerciseSessionRecord.class)))
                .isEqualTo(record);
    }

    @Test
    public void durationGoal_insertionAndRead() throws InterruptedException {
        PlannedExerciseSessionRecord record =
                createPlannedSessionWithCompletionGoal(
                        new ExerciseCompletionGoal.DurationGoal(Duration.ofMinutes(30)));

        TestUtils.insertRecordAndGetId(record);

        assertThat(
                        Iterables.getOnlyElement(
                                TestUtils.readAllRecords(PlannedExerciseSessionRecord.class)))
                .isEqualTo(record);
    }

    @Test
    public void repetitionsGoal_insertionAndRead() throws InterruptedException {
        PlannedExerciseSessionRecord record =
                createPlannedSessionWithCompletionGoal(
                        new ExerciseCompletionGoal.RepetitionsGoal(8));

        TestUtils.insertRecordAndGetId(record);

        assertThat(
                        Iterables.getOnlyElement(
                                TestUtils.readAllRecords(PlannedExerciseSessionRecord.class)))
                .isEqualTo(record);
    }

    @Test
    public void totalCaloriesBurnedGoal_insertionAndRead() throws InterruptedException {
        PlannedExerciseSessionRecord record =
                createPlannedSessionWithCompletionGoal(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(
                                Energy.fromCalories(260)));

        TestUtils.insertRecordAndGetId(record);

        assertThat(
                        Iterables.getOnlyElement(
                                TestUtils.readAllRecords(PlannedExerciseSessionRecord.class)))
                .isEqualTo(record);
    }

    @Test
    public void activeCaloriesBurnedGoal_insertionAndRead() throws InterruptedException {
        PlannedExerciseSessionRecord record =
                createPlannedSessionWithCompletionGoal(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                Energy.fromCalories(120)));

        TestUtils.insertRecordAndGetId(record);

        assertThat(
                        Iterables.getOnlyElement(
                                TestUtils.readAllRecords(PlannedExerciseSessionRecord.class)))
                .isEqualTo(record);
    }

    @Test
    public void unspecifiedGoal_insertionAndRead() throws InterruptedException {
        PlannedExerciseSessionRecord record =
                createPlannedSessionWithCompletionGoal(
                        ExerciseCompletionGoal.UnspecifiedGoal.INSTANCE);

        TestUtils.insertRecordAndGetId(record);

        assertThat(
                        Iterables.getOnlyElement(
                                TestUtils.readAllRecords(PlannedExerciseSessionRecord.class)))
                .isEqualTo(record);
    }

    private PlannedExerciseSessionRecord createPlannedSessionWithCompletionGoal(
            ExerciseCompletionGoal goal) {
        PlannedExerciseSessionRecord.Builder builder =
                new PlannedExerciseSessionRecord.Builder(
                                new Metadata.Builder()
                                        .setDataOrigin(
                                                new DataOrigin.Builder()
                                                        .setPackageName("android.healthconnect.cts")
                                                        .build())
                                        .setId(UUID.randomUUID().toString())
                                        .setClientRecordId(null)
                                        .setRecordingMethod(
                                                Metadata.RECORDING_METHOD_ACTIVELY_RECORDED)
                                        .build(),
                                ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
                                LocalDate.of(2007, APRIL, 5),
                                Duration.of(1, HOURS))
                        .addBlock(
                                new PlannedExerciseBlock.Builder(1)
                                        .addStep(createStepWithCompletionGoal(goal))
                                        .build());
        return builder.build();
    }

    private PlannedExerciseStep createStepWithCompletionGoal(ExerciseCompletionGoal goal) {
        return new PlannedExerciseStep.Builder(
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
                        PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE,
                        goal)
                .build();
    }
}
