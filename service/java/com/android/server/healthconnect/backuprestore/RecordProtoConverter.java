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

package com.android.server.healthconnect.backuprestore;

import static com.android.healthfitness.flags.Flags.FLAG_CLOUD_BACKUP_AND_RESTORE;

import static java.util.stream.Collectors.toSet;

import android.annotation.FlaggedApi;
import android.annotation.SuppressLint;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Power;
import android.health.connect.datatypes.units.Velocity;
import android.health.connect.internal.datatypes.ActiveCaloriesBurnedRecordInternal;
import android.health.connect.internal.datatypes.ActivityIntensityRecordInternal;
import android.health.connect.internal.datatypes.BasalBodyTemperatureRecordInternal;
import android.health.connect.internal.datatypes.BasalMetabolicRateRecordInternal;
import android.health.connect.internal.datatypes.BloodGlucoseRecordInternal;
import android.health.connect.internal.datatypes.BloodPressureRecordInternal;
import android.health.connect.internal.datatypes.BodyFatRecordInternal;
import android.health.connect.internal.datatypes.BodyTemperatureRecordInternal;
import android.health.connect.internal.datatypes.BodyWaterMassRecordInternal;
import android.health.connect.internal.datatypes.BoneMassRecordInternal;
import android.health.connect.internal.datatypes.CervicalMucusRecordInternal;
import android.health.connect.internal.datatypes.CyclingPedalingCadenceRecordInternal;
import android.health.connect.internal.datatypes.DistanceRecordInternal;
import android.health.connect.internal.datatypes.ElevationGainedRecordInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal;
import android.health.connect.internal.datatypes.ExerciseLapInternal;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal;
import android.health.connect.internal.datatypes.ExerciseRouteInternal;
import android.health.connect.internal.datatypes.ExerciseRouteInternal.LocationInternal;
import android.health.connect.internal.datatypes.ExerciseSegmentInternal;
import android.health.connect.internal.datatypes.ExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.FloorsClimbedRecordInternal;
import android.health.connect.internal.datatypes.HeartRateRecordInternal;
import android.health.connect.internal.datatypes.HeartRateVariabilityRmssdRecordInternal;
import android.health.connect.internal.datatypes.HeightRecordInternal;
import android.health.connect.internal.datatypes.HydrationRecordInternal;
import android.health.connect.internal.datatypes.InstantRecordInternal;
import android.health.connect.internal.datatypes.IntervalRecordInternal;
import android.health.connect.internal.datatypes.LeanBodyMassRecordInternal;
import android.health.connect.internal.datatypes.MenstruationFlowRecordInternal;
import android.health.connect.internal.datatypes.MindfulnessSessionRecordInternal;
import android.health.connect.internal.datatypes.NutritionRecordInternal;
import android.health.connect.internal.datatypes.OvulationTestRecordInternal;
import android.health.connect.internal.datatypes.OxygenSaturationRecordInternal;
import android.health.connect.internal.datatypes.PlannedExerciseBlockInternal;
import android.health.connect.internal.datatypes.PlannedExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.PlannedExerciseStepInternal;
import android.health.connect.internal.datatypes.PowerRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.RespiratoryRateRecordInternal;
import android.health.connect.internal.datatypes.RestingHeartRateRecordInternal;
import android.health.connect.internal.datatypes.SexualActivityRecordInternal;
import android.health.connect.internal.datatypes.SkinTemperatureRecordInternal;
import android.health.connect.internal.datatypes.SleepSessionRecordInternal;
import android.health.connect.internal.datatypes.SleepStageInternal;
import android.health.connect.internal.datatypes.SpeedRecordInternal;
import android.health.connect.internal.datatypes.StepsCadenceRecordInternal;
import android.health.connect.internal.datatypes.StepsRecordInternal;
import android.health.connect.internal.datatypes.TotalCaloriesBurnedRecordInternal;
import android.health.connect.internal.datatypes.Vo2MaxRecordInternal;
import android.health.connect.internal.datatypes.WeightRecordInternal;
import android.health.connect.internal.datatypes.WheelchairPushesRecordInternal;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;

import com.android.server.healthconnect.proto.backuprestore.ActiveCaloriesBurned;
import com.android.server.healthconnect.proto.backuprestore.ActivityIntensity;
import com.android.server.healthconnect.proto.backuprestore.BasalBodyTemperature;
import com.android.server.healthconnect.proto.backuprestore.BasalMetabolicRate;
import com.android.server.healthconnect.proto.backuprestore.BloodGlucose;
import com.android.server.healthconnect.proto.backuprestore.BloodPressure;
import com.android.server.healthconnect.proto.backuprestore.BodyFat;
import com.android.server.healthconnect.proto.backuprestore.BodyTemperature;
import com.android.server.healthconnect.proto.backuprestore.BodyWaterMass;
import com.android.server.healthconnect.proto.backuprestore.BoneMass;
import com.android.server.healthconnect.proto.backuprestore.CervicalMucus;
import com.android.server.healthconnect.proto.backuprestore.CyclingPedalingCadence;
import com.android.server.healthconnect.proto.backuprestore.Distance;
import com.android.server.healthconnect.proto.backuprestore.ElevationGained;
import com.android.server.healthconnect.proto.backuprestore.ExerciseSession;
import com.android.server.healthconnect.proto.backuprestore.FloorsClimbed;
import com.android.server.healthconnect.proto.backuprestore.HeartRate;
import com.android.server.healthconnect.proto.backuprestore.HeartRateVariabilityRmssd;
import com.android.server.healthconnect.proto.backuprestore.Height;
import com.android.server.healthconnect.proto.backuprestore.Hydration;
import com.android.server.healthconnect.proto.backuprestore.InstantRecord;
import com.android.server.healthconnect.proto.backuprestore.IntervalRecord;
import com.android.server.healthconnect.proto.backuprestore.LeanBodyMass;
import com.android.server.healthconnect.proto.backuprestore.MenstruationFlow;
import com.android.server.healthconnect.proto.backuprestore.MindfulnessSession;
import com.android.server.healthconnect.proto.backuprestore.Nutrition;
import com.android.server.healthconnect.proto.backuprestore.OvulationTest;
import com.android.server.healthconnect.proto.backuprestore.OxygenSaturation;
import com.android.server.healthconnect.proto.backuprestore.PlannedExerciseSession;
import com.android.server.healthconnect.proto.backuprestore.PlannedExerciseSession.PlannedExerciseBlock;
import com.android.server.healthconnect.proto.backuprestore.PlannedExerciseSession.PlannedExerciseBlock.PlannedExerciseStep;
import com.android.server.healthconnect.proto.backuprestore.PlannedExerciseSession.PlannedExerciseBlock.PlannedExerciseStep.ExerciseCompletionGoal;
import com.android.server.healthconnect.proto.backuprestore.PlannedExerciseSession.PlannedExerciseBlock.PlannedExerciseStep.ExercisePerformanceGoal;
import com.android.server.healthconnect.proto.backuprestore.Record;
import com.android.server.healthconnect.proto.backuprestore.RespiratoryRate;
import com.android.server.healthconnect.proto.backuprestore.RestingHeartRate;
import com.android.server.healthconnect.proto.backuprestore.SexualActivity;
import com.android.server.healthconnect.proto.backuprestore.SkinTemperature;
import com.android.server.healthconnect.proto.backuprestore.SleepSession;
import com.android.server.healthconnect.proto.backuprestore.Speed;
import com.android.server.healthconnect.proto.backuprestore.Steps;
import com.android.server.healthconnect.proto.backuprestore.StepsCadence;
import com.android.server.healthconnect.proto.backuprestore.TotalCaloriesBurned;
import com.android.server.healthconnect.proto.backuprestore.Vo2Max;
import com.android.server.healthconnect.proto.backuprestore.Weight;
import com.android.server.healthconnect.proto.backuprestore.WheelchairPushes;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A helper class used to create {@link RecordInternal} objects using its proto.
 *
 * @hide
 */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
public final class RecordProtoConverter {

    private final Map<Integer, Class<? extends RecordInternal<?>>> mDataTypeClassMap =
            HealthConnectMappings.getInstance().getRecordIdToInternalRecordClassMap();

    /** Creates a {@link RecordInternal} from the {@link Record} */
    public RecordInternal<?> toRecordInternal(Record recordProto)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        int recordId = getRecordId(recordProto);
        Class<? extends RecordInternal<?>> recordClass = mDataTypeClassMap.get(recordId);
        Objects.requireNonNull(recordClass);
        RecordInternal<?> recordInternal = recordClass.getConstructor().newInstance();
        populateRecordInternal(recordProto, recordInternal);
        return recordInternal;
    }

    @SuppressLint("WrongConstant") // Proto doesn't know about the device type & rec method IntDefs
    private static void populateRecordInternal(
            Record recordProto, RecordInternal<?> recordInternal) {
        String uuidString = recordProto.getUuid();
        if (!uuidString.isEmpty()) {
            recordInternal.setUuid(UUID.fromString(uuidString));
        }
        if (recordProto.hasPackageName()) {
            recordInternal.setPackageName(recordProto.getPackageName());
        }
        if (recordProto.hasAppName()) {
            recordInternal.setAppName(recordProto.getAppName());
        }
        recordInternal.setLastModifiedTime(recordProto.getLastModifiedTime());
        if (recordProto.hasClientRecordId()) {
            recordInternal.setClientRecordId(recordProto.getClientRecordId());
        }
        recordInternal.setClientRecordVersion(recordProto.getClientRecordVersion());
        if (recordProto.hasManufacturer()) {
            recordInternal.setManufacturer(recordProto.getManufacturer());
        }
        if (recordProto.hasModel()) {
            recordInternal.setModel(recordProto.getModel());
        }
        recordInternal.setDeviceType(recordProto.getDeviceType());
        recordInternal.setRecordingMethod(recordProto.getRecordingMethod());

        switch (recordProto.getSubRecordCase()) {
            case INTERVAL_RECORD ->
                    populateIntervalRecordInternal(
                            recordProto.getIntervalRecord(),
                            (IntervalRecordInternal<?>) recordInternal);
            case INSTANT_RECORD ->
                    populateInstantRecordInternal(
                            recordProto.getInstantRecord(),
                            (InstantRecordInternal<?>) recordInternal);
            case SUBRECORD_NOT_SET -> throw new IllegalArgumentException();
        }
    }

    private static void populateIntervalRecordInternal(
            IntervalRecord intervalRecordProto, IntervalRecordInternal<?> intervalRecordInternal) {
        intervalRecordInternal.setStartTime(intervalRecordProto.getStartTime());
        intervalRecordInternal.setStartZoneOffset(intervalRecordProto.getStartZoneOffset());
        intervalRecordInternal.setEndTime(intervalRecordProto.getEndTime());
        intervalRecordInternal.setEndZoneOffset(intervalRecordProto.getEndZoneOffset());

        switch (intervalRecordProto.getDataCase()) {
            case ACTIVE_CALORIES_BURNED ->
                    populateActiveCaloriesBurnedRecordInternal(
                            intervalRecordProto.getActiveCaloriesBurned(),
                            (ActiveCaloriesBurnedRecordInternal) intervalRecordInternal);
            case ACTIVITY_INTENSITY ->
                    populateActivityIntensityRecordInternal(
                            intervalRecordProto.getActivityIntensity(),
                            (ActivityIntensityRecordInternal) intervalRecordInternal);
            case CYCLING_PEDALING_CADENCE ->
                    populateCyclingPedalingCadenceRecordInternal(
                            intervalRecordProto.getCyclingPedalingCadence(),
                            (CyclingPedalingCadenceRecordInternal) intervalRecordInternal);
            case DISTANCE ->
                    populateDistanceRecordInternal(
                            intervalRecordProto.getDistance(),
                            (DistanceRecordInternal) intervalRecordInternal);
            case ELEVATION_GAINED ->
                    populateElevationGainedRecordInternal(
                            intervalRecordProto.getElevationGained(),
                            (ElevationGainedRecordInternal) intervalRecordInternal);
            case EXERCISE_SESSION ->
                    populateExerciseSessionRecordInternal(
                            intervalRecordProto.getExerciseSession(),
                            (ExerciseSessionRecordInternal) intervalRecordInternal);
            case FLOORS_CLIMBED ->
                    populateFloorsClimbedRecordInternal(
                            intervalRecordProto.getFloorsClimbed(),
                            (FloorsClimbedRecordInternal) intervalRecordInternal);
            case HEART_RATE ->
                    populateHeartRateRecordInternal(
                            intervalRecordProto.getHeartRate(),
                            (HeartRateRecordInternal) intervalRecordInternal);
            case HYDRATION ->
                    populateHydrationRecordInternal(
                            intervalRecordProto.getHydration(),
                            (HydrationRecordInternal) intervalRecordInternal);
            case MENSTRUATION_PERIOD -> {
                // No data to populate.
            }
            case MINDFULNESS_SESSION ->
                    populateMindfulnessSessionRecordInternal(
                            intervalRecordProto.getMindfulnessSession(),
                            (MindfulnessSessionRecordInternal) intervalRecordInternal);
            case NUTRITION ->
                    populateNutritionRecordInternal(
                            intervalRecordProto.getNutrition(),
                            (NutritionRecordInternal) intervalRecordInternal);
            case PLANNED_EXERCISE_SESSION ->
                    populatePlannedExerciseSessionRecordInternal(
                            intervalRecordProto.getPlannedExerciseSession(),
                            (PlannedExerciseSessionRecordInternal) intervalRecordInternal);
            case POWER ->
                    populatePowerRecordInternal(
                            intervalRecordProto.getPower(),
                            (PowerRecordInternal) intervalRecordInternal);
            case SKIN_TEMPERATURE ->
                    populateSkinTemperatureRecordInternal(
                            intervalRecordProto.getSkinTemperature(),
                            (SkinTemperatureRecordInternal) intervalRecordInternal);
            case SLEEP_SESSION ->
                    populateSleepSessionRecordInternal(
                            intervalRecordProto.getSleepSession(),
                            (SleepSessionRecordInternal) intervalRecordInternal);
            case SPEED ->
                    populateSpeedRecordInternal(
                            intervalRecordProto.getSpeed(),
                            (SpeedRecordInternal) intervalRecordInternal);
            case STEPS ->
                    populateStepsRecordInternal(
                            intervalRecordProto.getSteps(),
                            (StepsRecordInternal) intervalRecordInternal);
            case STEPS_CADENCE ->
                    populateStepsCadenceRecordInternal(
                            intervalRecordProto.getStepsCadence(),
                            (StepsCadenceRecordInternal) intervalRecordInternal);
            case TOTAL_CALORIES_BURNED ->
                    populateTotalCaloriesBurnedRecordInternal(
                            intervalRecordProto.getTotalCaloriesBurned(),
                            (TotalCaloriesBurnedRecordInternal) intervalRecordInternal);
            case WHEELCHAIR_PUSHES ->
                    populateWheelchairPushesRecordInternal(
                            intervalRecordProto.getWheelchairPushes(),
                            (WheelchairPushesRecordInternal) intervalRecordInternal);
            case DATA_NOT_SET -> throw new IllegalArgumentException();
        }
    }

    private static void populateActiveCaloriesBurnedRecordInternal(
            ActiveCaloriesBurned activeCaloriesBurnedProto,
            ActiveCaloriesBurnedRecordInternal activeCaloriesBurnedRecordInternal) {
        activeCaloriesBurnedRecordInternal.setEnergy(activeCaloriesBurnedProto.getEnergy());
    }

    private static void populateActivityIntensityRecordInternal(
            ActivityIntensity activityIntensityProto,
            ActivityIntensityRecordInternal activityIntensityRecordInternal) {
        activityIntensityRecordInternal.setActivityIntensityType(
                activityIntensityProto.getActivityIntensityType());
    }

    private static void populateCyclingPedalingCadenceRecordInternal(
            CyclingPedalingCadence cyclingPedalingCadenceProto,
            CyclingPedalingCadenceRecordInternal cyclingPedalingCadenceRecordInternal) {
        cyclingPedalingCadenceRecordInternal.setSamples(
                cyclingPedalingCadenceProto.getSampleList().stream()
                        .map(
                                sample ->
                                        new CyclingPedalingCadenceRecordInternal
                                                .CyclingPedalingCadenceRecordSample(
                                                sample.getRevolutionsPerMinute(),
                                                sample.getEpochMillis()))
                        .collect(toSet()));
    }

    private static void populateDistanceRecordInternal(
            Distance distanceProto, DistanceRecordInternal distanceRecordInternal) {
        distanceRecordInternal.setDistance(distanceProto.getDistance());
    }

    private static void populateElevationGainedRecordInternal(
            ElevationGained elevationGainedProto,
            ElevationGainedRecordInternal elevationGainedRecordInternal) {
        elevationGainedRecordInternal.setElevation(elevationGainedProto.getElevation());
    }

    private static void populateExerciseSessionRecordInternal(
            ExerciseSession exerciseSessionProto,
            ExerciseSessionRecordInternal exerciseSessionRecordInternal) {
        if (exerciseSessionProto.hasNotes()) {
            exerciseSessionRecordInternal.setNotes(exerciseSessionProto.getNotes());
        }
        exerciseSessionRecordInternal.setExerciseType(exerciseSessionProto.getExerciseType());
        if (exerciseSessionProto.hasTitle()) {
            exerciseSessionRecordInternal.setTitle(exerciseSessionProto.getTitle());
        }
        exerciseSessionRecordInternal.setHasRoute(exerciseSessionProto.getHasRoute());
        if (exerciseSessionProto.hasRoute()) {
            List<LocationInternal> locations =
                    exerciseSessionProto.getRoute().getRouteLocationList().stream()
                            .map(
                                    location ->
                                            new LocationInternal()
                                                    .setTime(location.getTime())
                                                    .setLatitude(location.getLatitude())
                                                    .setLongitude(location.getLongitude())
                                                    .setHorizontalAccuracy(
                                                            location.getHorizontalAccuracy())
                                                    .setVerticalAccuracy(
                                                            location.getVerticalAccuracy())
                                                    .setAltitude(location.getAltitude()))
                            .toList();
            exerciseSessionRecordInternal.setRoute(new ExerciseRouteInternal(locations));
        }
        exerciseSessionRecordInternal.setExerciseLaps(
                exerciseSessionProto.getLapList().stream()
                        .map(
                                lap ->
                                        new ExerciseLapInternal()
                                                .setStarTime(lap.getStartTime())
                                                .setEndTime(lap.getEndTime())
                                                .setLength(lap.getLength()))
                        .toList());
        exerciseSessionRecordInternal.setExerciseSegments(
                exerciseSessionProto.getSegmentList().stream()
                        .map(
                                segment ->
                                        new ExerciseSegmentInternal()
                                                .setStarTime(segment.getStartTime())
                                                .setEndTime(segment.getEndTime())
                                                .setSegmentType(segment.getSegmentType())
                                                .setRepetitionsCount(segment.getRepetitionsCount()))
                        .toList());
        if (exerciseSessionProto.hasPlannedExerciseSessionId()) {
            exerciseSessionRecordInternal.setPlannedExerciseSessionId(
                    UUID.fromString(exerciseSessionProto.getPlannedExerciseSessionId()));
        }
    }

    private static void populateFloorsClimbedRecordInternal(
            FloorsClimbed floorsClimbedProto,
            FloorsClimbedRecordInternal floorsClimbedRecordInternal) {
        floorsClimbedRecordInternal.setFloors(floorsClimbedProto.getFloors());
    }

    private static void populateHeartRateRecordInternal(
            HeartRate heartRateProto, HeartRateRecordInternal heartRateRecordInternal) {
        heartRateRecordInternal.setSamples(
                heartRateProto.getSampleList().stream()
                        .map(
                                sample ->
                                        new HeartRateRecordInternal.HeartRateSample(
                                                sample.getBeatsPerMinute(),
                                                sample.getEpochMillis()))
                        .collect(toSet()));
    }

    private static void populateHydrationRecordInternal(
            Hydration hydrationProto, HydrationRecordInternal hydrationRecordInternal) {
        hydrationRecordInternal.setVolume(hydrationProto.getVolume());
    }

    private static void populateMindfulnessSessionRecordInternal(
            MindfulnessSession mindfulnessSessionProto,
            MindfulnessSessionRecordInternal mindfulnessSessionRecordInternal) {
        mindfulnessSessionRecordInternal.setMindfulnessSessionType(
                mindfulnessSessionProto.getMindfulnessSessionType());
        if (mindfulnessSessionProto.hasTitle()) {
            mindfulnessSessionRecordInternal.setTitle(mindfulnessSessionProto.getTitle());
        }
        if (mindfulnessSessionProto.hasNotes()) {
            mindfulnessSessionRecordInternal.setNotes(mindfulnessSessionProto.getNotes());
        }
    }

    private static void populateNutritionRecordInternal(
            Nutrition nutritionProto, NutritionRecordInternal nutritionRecordInternal) {
        nutritionRecordInternal
                .setUnsaturatedFat(nutritionProto.getUnsaturatedFat())
                .setPotassium(nutritionProto.getPotassium())
                .setThiamin(nutritionProto.getThiamin())
                .setMealType(nutritionProto.getMealType())
                .setTransFat(nutritionProto.getTransFat())
                .setManganese(nutritionProto.getManganese())
                .setEnergyFromFat(nutritionProto.getEnergyFromFat())
                .setCaffeine(nutritionProto.getCaffeine())
                .setDietaryFiber(nutritionProto.getDietaryFiber())
                .setSelenium(nutritionProto.getSelenium())
                .setVitaminB6(nutritionProto.getVitaminB6())
                .setProtein(nutritionProto.getProtein())
                .setChloride(nutritionProto.getChloride())
                .setCholesterol(nutritionProto.getCholesterol())
                .setCopper(nutritionProto.getCopper())
                .setIodine(nutritionProto.getIodine())
                .setVitaminB12(nutritionProto.getVitaminB12())
                .setZinc(nutritionProto.getZinc())
                .setRiboflavin(nutritionProto.getRiboflavin())
                .setEnergy(nutritionProto.getEnergy())
                .setMolybdenum(nutritionProto.getMolybdenum())
                .setPhosphorus(nutritionProto.getPhosphorus())
                .setChromium(nutritionProto.getChromium())
                .setTotalFat(nutritionProto.getTotalFat())
                .setCalcium(nutritionProto.getCalcium())
                .setVitaminC(nutritionProto.getVitaminC())
                .setVitaminE(nutritionProto.getVitaminE())
                .setBiotin(nutritionProto.getBiotin())
                .setVitaminD(nutritionProto.getVitaminD())
                .setNiacin(nutritionProto.getNiacin())
                .setMagnesium(nutritionProto.getMagnesium())
                .setTotalCarbohydrate(nutritionProto.getTotalCarbohydrate())
                .setVitaminK(nutritionProto.getVitaminK())
                .setPolyunsaturatedFat(nutritionProto.getPolyunsaturatedFat())
                .setSaturatedFat(nutritionProto.getSaturatedFat())
                .setSodium(nutritionProto.getSodium())
                .setFolate(nutritionProto.getFolate())
                .setMonounsaturatedFat(nutritionProto.getMonounsaturatedFat())
                .setPantothenicAcid(nutritionProto.getPantothenicAcid())
                .setIron(nutritionProto.getIron())
                .setVitaminA(nutritionProto.getVitaminA())
                .setFolicAcid(nutritionProto.getFolicAcid())
                .setSugar(nutritionProto.getSugar());

        if (nutritionProto.hasMealName()) {
            nutritionRecordInternal.setMealName(nutritionProto.getMealName());
        }
    }

    private static void populatePlannedExerciseSessionRecordInternal(
            PlannedExerciseSession plannedExerciseSessionProto,
            PlannedExerciseSessionRecordInternal plannedExerciseSessionRecordInternal) {
        if (plannedExerciseSessionProto.hasNotes()) {
            plannedExerciseSessionRecordInternal.setNotes(plannedExerciseSessionProto.getNotes());
        }
        plannedExerciseSessionRecordInternal.setExerciseType(
                plannedExerciseSessionProto.getExerciseType());
        if (plannedExerciseSessionProto.hasTitle()) {
            plannedExerciseSessionRecordInternal.setTitle(plannedExerciseSessionProto.getTitle());
        }
        plannedExerciseSessionRecordInternal.setHasExplicitTime(
                plannedExerciseSessionProto.getHasExplicitTime());
        plannedExerciseSessionRecordInternal.setExerciseBlocks(
                plannedExerciseSessionProto.getExerciseBlockList().stream()
                        .map(RecordProtoConverter::convertToPlannedExerciseBlockInternal)
                        .toList());
        if (plannedExerciseSessionProto.hasCompletedExerciseSessionId()) {
            plannedExerciseSessionRecordInternal.setCompletedExerciseSessionId(
                    UUID.fromString(plannedExerciseSessionProto.getCompletedExerciseSessionId()));
        }
    }

    private static PlannedExerciseBlockInternal convertToPlannedExerciseBlockInternal(
            PlannedExerciseBlock plannedExerciseBlockProto) {
        var plannedExerciseBlockInternal =
                new PlannedExerciseBlockInternal(plannedExerciseBlockProto.getRepetitions());

        if (plannedExerciseBlockProto.hasDescription()) {
            plannedExerciseBlockInternal.setDescription(plannedExerciseBlockProto.getDescription());
        }
        plannedExerciseBlockInternal.setExerciseSteps(
                plannedExerciseBlockProto.getStepList().stream()
                        .map(RecordProtoConverter::convertToPlannedExerciseStepInternal)
                        .toList());

        return plannedExerciseBlockInternal;
    }

    private static PlannedExerciseStepInternal convertToPlannedExerciseStepInternal(
            PlannedExerciseStep plannedExerciseStepProto) {
        var plannedExerciseStepInternal =
                new PlannedExerciseStepInternal(
                        plannedExerciseStepProto.getExerciseType(),
                        plannedExerciseStepProto.getExerciseCategory(),
                        convertToExerciseCompletionGoalInternal(
                                plannedExerciseStepProto.getCompletionGoal()));

        if (plannedExerciseStepProto.hasDescription()) {
            plannedExerciseStepInternal.setDescription(plannedExerciseStepProto.getDescription());
        }
        plannedExerciseStepInternal.setPerformanceGoals(
                plannedExerciseStepProto.getPerformanceGoalList().stream()
                        .map(RecordProtoConverter::convertToExercisePerformanceGoalInternal)
                        .toList());

        return plannedExerciseStepInternal;
    }

    private static ExerciseCompletionGoalInternal convertToExerciseCompletionGoalInternal(
            ExerciseCompletionGoal exerciseCompletionGoalProto) {
        return switch (exerciseCompletionGoalProto.getGoalCase()) {
            case DISTANCE_GOAL ->
                    new ExerciseCompletionGoalInternal.DistanceGoalInternal(
                            Length.fromMeters(
                                    exerciseCompletionGoalProto.getDistanceGoal().getDistance()));
            case STEPS_GOAL ->
                    new ExerciseCompletionGoalInternal.StepsGoalInternal(
                            exerciseCompletionGoalProto.getStepsGoal().getSteps());
            case DURATION_GOAL ->
                    new ExerciseCompletionGoalInternal.DurationGoalInternal(
                            Duration.ofMillis(
                                    exerciseCompletionGoalProto.getDurationGoal().getDuration()));
            case REPETITIONS_GOAL ->
                    new ExerciseCompletionGoalInternal.RepetitionsGoalInternal(
                            exerciseCompletionGoalProto.getRepetitionsGoal().getRepetitions());
            case TOTAL_CALORIES_BURNED_GOAL ->
                    new ExerciseCompletionGoalInternal.TotalCaloriesBurnedGoalInternal(
                            Energy.fromCalories(
                                    exerciseCompletionGoalProto
                                            .getTotalCaloriesBurnedGoal()
                                            .getTotalCalories()));
            case ACTIVE_CALORIES_BURNED_GOAL ->
                    new ExerciseCompletionGoalInternal.ActiveCaloriesBurnedGoalInternal(
                            Energy.fromCalories(
                                    exerciseCompletionGoalProto
                                            .getActiveCaloriesBurnedGoal()
                                            .getActiveCalories()));
            case DISTANCE_WITH_VARIABLE_REST_GOAL ->
                    new ExerciseCompletionGoalInternal.DistanceWithVariableRestGoalInternal(
                            Length.fromMeters(
                                    exerciseCompletionGoalProto
                                            .getDistanceWithVariableRestGoal()
                                            .getDistance()),
                            Duration.ofMillis(
                                    exerciseCompletionGoalProto
                                            .getDistanceWithVariableRestGoal()
                                            .getDuration()));
            case UNSPECIFIED_GOAL ->
                    ExerciseCompletionGoalInternal.UnspecifiedGoalInternal.INSTANCE;
            case UNKNOWN_GOAL, GOAL_NOT_SET ->
                    ExerciseCompletionGoalInternal.UnknownGoalInternal.INSTANCE;
        };
    }

    private static ExercisePerformanceGoalInternal convertToExercisePerformanceGoalInternal(
            ExercisePerformanceGoal exercisePerformanceGoalProto) {
        return switch (exercisePerformanceGoalProto.getGoalCase()) {
            case POWER_GOAL ->
                    new ExercisePerformanceGoalInternal.PowerGoalInternal(
                            Power.fromWatts(
                                    exercisePerformanceGoalProto.getPowerGoal().getMinPower()),
                            Power.fromWatts(
                                    exercisePerformanceGoalProto.getPowerGoal().getMaxPower()));
            case SPEED_GOAL ->
                    new ExercisePerformanceGoalInternal.SpeedGoalInternal(
                            Velocity.fromMetersPerSecond(
                                    exercisePerformanceGoalProto.getSpeedGoal().getMinSpeed()),
                            Velocity.fromMetersPerSecond(
                                    exercisePerformanceGoalProto.getSpeedGoal().getMaxSpeed()));
            case CADENCE_GOAL ->
                    new ExercisePerformanceGoalInternal.CadenceGoalInternal(
                            exercisePerformanceGoalProto.getCadenceGoal().getMinRpm(),
                            exercisePerformanceGoalProto.getCadenceGoal().getMaxRpm());
            case HEART_RATE_GOAL ->
                    new ExercisePerformanceGoalInternal.HeartRateGoalInternal(
                            exercisePerformanceGoalProto.getHeartRateGoal().getMinBpm(),
                            exercisePerformanceGoalProto.getHeartRateGoal().getMaxBpm());
            case WEIGHT_GOAL ->
                    new ExercisePerformanceGoalInternal.WeightGoalInternal(
                            Mass.fromGrams(exercisePerformanceGoalProto.getWeightGoal().getMass()));
            case RATE_OF_PERCEIVED_EXERTION_GOAL ->
                    new ExercisePerformanceGoalInternal.RateOfPerceivedExertionGoalInternal(
                            exercisePerformanceGoalProto.getRateOfPerceivedExertionGoal().getRpe());
            case AMRAP_GOAL -> ExercisePerformanceGoalInternal.AmrapGoalInternal.INSTANCE;
            case UNKNOWN_GOAL, GOAL_NOT_SET ->
                    ExercisePerformanceGoalInternal.UnknownGoalInternal.INSTANCE;
        };
    }

    private static void populatePowerRecordInternal(
            com.android.server.healthconnect.proto.backuprestore.Power powerProto,
            PowerRecordInternal powerRecordInternal) {
        powerRecordInternal.setSamples(
                powerProto.getSampleList().stream()
                        .map(
                                sample ->
                                        new PowerRecordInternal.PowerRecordSample(
                                                sample.getPower(), sample.getEpochMillis()))
                        .collect(toSet()));
    }

    private static void populateSkinTemperatureRecordInternal(
            SkinTemperature skinTemperatureProto,
            SkinTemperatureRecordInternal skinTemperatureRecordInternal) {
        skinTemperatureRecordInternal.setSamples(
                skinTemperatureProto.getSampleList().stream()
                        .map(
                                sample ->
                                        new SkinTemperatureRecordInternal
                                                .SkinTemperatureDeltaSample(
                                                sample.getTemperatureDeltaInCelsius(),
                                                sample.getEpochMillis()))
                        .collect(toSet()));
    }

    @SuppressLint("WrongConstant")
    private static void populateSleepSessionRecordInternal(
            SleepSession sleepSessionProto, SleepSessionRecordInternal sleepSessionRecordInternal) {
        if (sleepSessionProto.hasNotes()) {
            sleepSessionRecordInternal.setNotes(sleepSessionProto.getNotes());
        }
        if (sleepSessionProto.hasTitle()) {
            sleepSessionRecordInternal.setTitle(sleepSessionProto.getTitle());
        }
        if (sleepSessionProto.getStageCount() > 0) {
            sleepSessionRecordInternal.setSleepStages(
                    sleepSessionProto.getStageList().stream()
                            .map(
                                    sleepStage ->
                                            new SleepStageInternal()
                                                    .setStartTime(sleepStage.getStartTime())
                                                    .setEndTime(sleepStage.getEndTime())
                                                    .setStageType(sleepStage.getStageType()))
                            .toList());
        }
    }

    private static void populateSpeedRecordInternal(
            Speed speedProto, SpeedRecordInternal speedRecordInternal) {
        speedRecordInternal.setSamples(
                speedProto.getSampleList().stream()
                        .map(
                                sample ->
                                        new SpeedRecordInternal.SpeedRecordSample(
                                                sample.getSpeed(), sample.getEpochMillis()))
                        .collect(toSet()));
    }

    private static void populateStepsRecordInternal(
            Steps stepsProto, StepsRecordInternal stepsRecordInternal) {
        stepsRecordInternal.setCount(stepsProto.getCount());
    }

    private static void populateTotalCaloriesBurnedRecordInternal(
            TotalCaloriesBurned totalCaloriesBurnedProto,
            TotalCaloriesBurnedRecordInternal totalCaloriesBurnedRecordInternal) {
        totalCaloriesBurnedRecordInternal.setEnergy(totalCaloriesBurnedProto.getEnergy());
    }

    private static void populateStepsCadenceRecordInternal(
            StepsCadence stepsCadenceProto, StepsCadenceRecordInternal stepsCadenceRecordInternal) {
        stepsCadenceRecordInternal.setSamples(
                stepsCadenceProto.getSampleList().stream()
                        .map(
                                sample ->
                                        new StepsCadenceRecordInternal.StepsCadenceRecordSample(
                                                sample.getRate(), sample.getEpochMillis()))
                        .collect(toSet()));
    }

    private static void populateWheelchairPushesRecordInternal(
            WheelchairPushes wheelchairPushesProto,
            WheelchairPushesRecordInternal wheelchairPushesRecordInternal) {
        wheelchairPushesRecordInternal.setCount(wheelchairPushesProto.getCount());
    }

    private static void populateInstantRecordInternal(
            InstantRecord instantRecordProto, InstantRecordInternal<?> instantRecordInternal) {
        instantRecordInternal
                .setTime(instantRecordProto.getTime())
                .setZoneOffset(instantRecordProto.getZoneOffset());

        switch (instantRecordProto.getDataCase()) {
            case BASAL_BODY_TEMPERATURE ->
                    populateBasalBodyTemperatureRecordInternal(
                            instantRecordProto.getBasalBodyTemperature(),
                            (BasalBodyTemperatureRecordInternal) instantRecordInternal);
            case BASAL_METABOLIC_RATE ->
                    populateBasalMetabolicRateRecordInternal(
                            instantRecordProto.getBasalMetabolicRate(),
                            (BasalMetabolicRateRecordInternal) instantRecordInternal);
            case BLOOD_GLUCOSE ->
                    populateBloodGlucoseRecordInternal(
                            instantRecordProto.getBloodGlucose(),
                            (BloodGlucoseRecordInternal) instantRecordInternal);
            case BLOOD_PRESSURE ->
                    populateBloodPressureRecordInternal(
                            instantRecordProto.getBloodPressure(),
                            (BloodPressureRecordInternal) instantRecordInternal);
            case BODY_FAT ->
                    populateBodyFatRecordInternal(
                            instantRecordProto.getBodyFat(),
                            (BodyFatRecordInternal) instantRecordInternal);
            case BODY_TEMPERATURE ->
                    populateBodyTemperatureRecordInternal(
                            instantRecordProto.getBodyTemperature(),
                            (BodyTemperatureRecordInternal) instantRecordInternal);
            case BODY_WATER_MASS ->
                    populateBodyWaterMassRecordInternal(
                            instantRecordProto.getBodyWaterMass(),
                            (BodyWaterMassRecordInternal) instantRecordInternal);
            case BONE_MASS ->
                    populateBoneMassRecordInternal(
                            instantRecordProto.getBoneMass(),
                            (BoneMassRecordInternal) instantRecordInternal);
            case CERVICAL_MUCUS ->
                    populateCervicalMucusRecordInternal(
                            instantRecordProto.getCervicalMucus(),
                            (CervicalMucusRecordInternal) instantRecordInternal);
            case HEART_RATE_VARIABILITY_RMSSD ->
                    populateHeartRateVariabilityRmssdRecordInternal(
                            instantRecordProto.getHeartRateVariabilityRmssd(),
                            (HeartRateVariabilityRmssdRecordInternal) instantRecordInternal);
            case HEIGHT ->
                    populateHeightRecordInternal(
                            instantRecordProto.getHeight(),
                            (HeightRecordInternal) instantRecordInternal);
            case INTERMENSTRUAL_BLEEDING -> {
                // No data to populate.
            }
            case LEAN_BODY_MASS ->
                    populateLeanBodyMassRecordInternal(
                            instantRecordProto.getLeanBodyMass(),
                            (LeanBodyMassRecordInternal) instantRecordInternal);
            case MENSTRUATION_FLOW ->
                    populateMenstruationFlowRecordInternal(
                            instantRecordProto.getMenstruationFlow(),
                            (MenstruationFlowRecordInternal) instantRecordInternal);
            case OVULATION_TEST ->
                    populateOvulationTestRecordInternal(
                            instantRecordProto.getOvulationTest(),
                            (OvulationTestRecordInternal) instantRecordInternal);
            case OXYGEN_SATURATION ->
                    populateOxygenSaturationRecordInternal(
                            instantRecordProto.getOxygenSaturation(),
                            (OxygenSaturationRecordInternal) instantRecordInternal);
            case RESPIRATORY_RATE ->
                    populateRespiratoryRateRecordInternal(
                            instantRecordProto.getRespiratoryRate(),
                            (RespiratoryRateRecordInternal) instantRecordInternal);
            case RESTING_HEART_RATE ->
                    populateRestingHeartRateRecordInternal(
                            instantRecordProto.getRestingHeartRate(),
                            (RestingHeartRateRecordInternal) instantRecordInternal);
            case SEXUAL_ACTIVITY ->
                    populateSexualActivityRecordInternal(
                            instantRecordProto.getSexualActivity(),
                            (SexualActivityRecordInternal) instantRecordInternal);
            case VO2_MAX ->
                    populateVo2MaxRecordInternal(
                            instantRecordProto.getVo2Max(),
                            (Vo2MaxRecordInternal) instantRecordInternal);
            case WEIGHT ->
                    populateWeightRecordInternal(
                            instantRecordProto.getWeight(),
                            (WeightRecordInternal) instantRecordInternal);
            case DATA_NOT_SET -> throw new IllegalArgumentException();
        }
    }

    private static void populateBasalBodyTemperatureRecordInternal(
            BasalBodyTemperature basalBodyTemperatureProto,
            BasalBodyTemperatureRecordInternal basalBodyTemperatureRecordInternal) {
        basalBodyTemperatureRecordInternal
                .setTemperature(basalBodyTemperatureProto.getTemperature())
                .setMeasurementLocation(basalBodyTemperatureProto.getMeasurementLocation());
    }

    private static void populateBasalMetabolicRateRecordInternal(
            BasalMetabolicRate basalMetabolicRateProto,
            BasalMetabolicRateRecordInternal basalMetabolicRateRecordInternal) {
        basalMetabolicRateRecordInternal.setBasalMetabolicRate(
                basalMetabolicRateProto.getBasalMetabolicRate());
    }

    private static void populateBloodGlucoseRecordInternal(
            BloodGlucose bloodGlucoseProto, BloodGlucoseRecordInternal bloodGlucoseRecordInternal) {
        bloodGlucoseRecordInternal
                .setSpecimenSource(bloodGlucoseProto.getSpecimenSource())
                .setLevel(bloodGlucoseProto.getLevel())
                .setRelationToMeal(bloodGlucoseProto.getRelationToMeal())
                .setMealType(bloodGlucoseProto.getMealType());
    }

    private static void populateBloodPressureRecordInternal(
            BloodPressure bloodPressureProto,
            BloodPressureRecordInternal bloodPressureRecordInternal) {
        bloodPressureRecordInternal
                .setMeasurementLocation(bloodPressureProto.getMeasurementLocation())
                .setSystolic(bloodPressureProto.getSystolic())
                .setDiastolic(bloodPressureProto.getDiastolic())
                .setBodyPosition(bloodPressureProto.getBodyPosition());
    }

    private static void populateBodyFatRecordInternal(
            BodyFat bodyFatProto, BodyFatRecordInternal bodyFatRecordInternal) {
        bodyFatRecordInternal.setPercentage(bodyFatProto.getPercentage());
    }

    private static void populateBodyTemperatureRecordInternal(
            BodyTemperature bodyTemperatureProto,
            BodyTemperatureRecordInternal bodyTemperatureRecordInternal) {
        bodyTemperatureRecordInternal
                .setTemperature(bodyTemperatureProto.getTemperature())
                .setMeasurementLocation(bodyTemperatureProto.getMeasurementLocation());
    }

    private static void populateBodyWaterMassRecordInternal(
            BodyWaterMass bodyWaterMassProto,
            BodyWaterMassRecordInternal bodyWaterMassRecordInternal) {
        bodyWaterMassRecordInternal.setBodyWaterMass(bodyWaterMassProto.getBodyWaterMass());
    }

    private static void populateBoneMassRecordInternal(
            BoneMass boneMassProto, BoneMassRecordInternal boneMassRecordInternal) {
        boneMassRecordInternal.setMass(boneMassProto.getMass());
    }

    private static void populateCervicalMucusRecordInternal(
            CervicalMucus cervicalMucusProto,
            CervicalMucusRecordInternal cervicalMucusRecordInternal) {
        cervicalMucusRecordInternal
                .setSensation(cervicalMucusProto.getSensation())
                .setAppearance(cervicalMucusProto.getAppearance());
    }

    private static void populateMenstruationFlowRecordInternal(
            MenstruationFlow menstruationFlowProto,
            MenstruationFlowRecordInternal menstruationFlowRecordInternal) {
        menstruationFlowRecordInternal.setFlow(menstruationFlowProto.getFlow());
    }

    private static void populateOvulationTestRecordInternal(
            OvulationTest ovulationTestProto,
            OvulationTestRecordInternal ovulationTestRecordInternal) {
        ovulationTestRecordInternal.setResult(ovulationTestProto.getResult());
    }

    private static void populateOxygenSaturationRecordInternal(
            OxygenSaturation oxygenSaturationProto,
            OxygenSaturationRecordInternal oxygenSaturationRecordInternal) {
        oxygenSaturationRecordInternal.setPercentage(oxygenSaturationProto.getPercentage());
    }

    private static void populateHeartRateVariabilityRmssdRecordInternal(
            HeartRateVariabilityRmssd heartRateVariabilityRmssdProto,
            HeartRateVariabilityRmssdRecordInternal heartRateVariabilityRmssdRecordInternal) {
        heartRateVariabilityRmssdRecordInternal.setHeartRateVariabilityMillis(
                heartRateVariabilityRmssdProto.getHeartRateVariabilityMillis());
    }

    private static void populateHeightRecordInternal(
            Height heightProto, HeightRecordInternal heightRecordInternal) {
        heightRecordInternal.setHeight(heightProto.getHeight());
    }

    private static void populateLeanBodyMassRecordInternal(
            LeanBodyMass leanBodyMassProto, LeanBodyMassRecordInternal leanBodyMassRecordInternal) {
        leanBodyMassRecordInternal.setMass(leanBodyMassProto.getMass());
    }

    private static void populateRespiratoryRateRecordInternal(
            RespiratoryRate respiratoryRateProto,
            RespiratoryRateRecordInternal respiratoryRateRecordInternal) {
        respiratoryRateRecordInternal.setRate(respiratoryRateProto.getRate());
    }

    private static void populateRestingHeartRateRecordInternal(
            RestingHeartRate restingHeartRateProto,
            RestingHeartRateRecordInternal restingHeartRateRecordInternal) {
        restingHeartRateRecordInternal.setBeatsPerMinute(restingHeartRateProto.getBeatsPerMinute());
    }

    private static void populateSexualActivityRecordInternal(
            SexualActivity sexualActivityProto,
            SexualActivityRecordInternal sexualActivityRecordInternal) {
        sexualActivityRecordInternal.setProtectionUsed(sexualActivityProto.getProtectionUsed());
    }

    private static void populateVo2MaxRecordInternal(
            Vo2Max vo2MaxProto, Vo2MaxRecordInternal vo2MaxRecordInternal) {
        vo2MaxRecordInternal
                .setMeasurementMethod(vo2MaxProto.getMeasurementMethod())
                .setVo2MillilitersPerMinuteKilogram(
                        vo2MaxProto.getVo2MillilitersPerMinuteKilogram());
    }

    private static void populateWeightRecordInternal(
            Weight weightProto, WeightRecordInternal weightRecordInternal) {
        weightRecordInternal.setWeight(weightProto.getWeight());
    }

    @RecordTypeIdentifier.RecordType
    private int getRecordId(Record protoRecord) {
        if (protoRecord.hasIntervalRecord()) {
            return getIntervalRecordId(protoRecord.getIntervalRecord());
        }
        if (protoRecord.hasInstantRecord()) {
            return getInstantRecordId(protoRecord.getInstantRecord());
        }
        throw new IllegalArgumentException();
    }

    @RecordTypeIdentifier.RecordType
    private int getIntervalRecordId(IntervalRecord protoRecord) {
        return switch (protoRecord.getDataCase()) {
            case ACTIVE_CALORIES_BURNED -> RecordTypeIdentifier.RECORD_TYPE_ACTIVE_CALORIES_BURNED;
            case ACTIVITY_INTENSITY -> RecordTypeIdentifier.RECORD_TYPE_ACTIVITY_INTENSITY;
            case CYCLING_PEDALING_CADENCE ->
                    RecordTypeIdentifier.RECORD_TYPE_CYCLING_PEDALING_CADENCE;
            case DISTANCE -> RecordTypeIdentifier.RECORD_TYPE_DISTANCE;
            case ELEVATION_GAINED -> RecordTypeIdentifier.RECORD_TYPE_ELEVATION_GAINED;
            case EXERCISE_SESSION -> RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION;
            case FLOORS_CLIMBED -> RecordTypeIdentifier.RECORD_TYPE_FLOORS_CLIMBED;
            case HEART_RATE -> RecordTypeIdentifier.RECORD_TYPE_HEART_RATE;
            case HYDRATION -> RecordTypeIdentifier.RECORD_TYPE_HYDRATION;
            case MENSTRUATION_PERIOD -> RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_PERIOD;
            case MINDFULNESS_SESSION -> RecordTypeIdentifier.RECORD_TYPE_MINDFULNESS_SESSION;
            case NUTRITION -> RecordTypeIdentifier.RECORD_TYPE_NUTRITION;
            case PLANNED_EXERCISE_SESSION ->
                    RecordTypeIdentifier.RECORD_TYPE_PLANNED_EXERCISE_SESSION;
            case POWER -> RecordTypeIdentifier.RECORD_TYPE_POWER;
            case SKIN_TEMPERATURE -> RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE;
            case SLEEP_SESSION -> RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION;
            case SPEED -> RecordTypeIdentifier.RECORD_TYPE_SPEED;
            case STEPS -> RecordTypeIdentifier.RECORD_TYPE_STEPS;
            case STEPS_CADENCE -> RecordTypeIdentifier.RECORD_TYPE_STEPS_CADENCE;
            case TOTAL_CALORIES_BURNED -> RecordTypeIdentifier.RECORD_TYPE_TOTAL_CALORIES_BURNED;
            case WHEELCHAIR_PUSHES -> RecordTypeIdentifier.RECORD_TYPE_WHEELCHAIR_PUSHES;
            case DATA_NOT_SET -> throw new IllegalArgumentException();
        };
    }

    @RecordTypeIdentifier.RecordType
    private int getInstantRecordId(InstantRecord protoRecord) {
        return switch (protoRecord.getDataCase()) {
            case BASAL_BODY_TEMPERATURE -> RecordTypeIdentifier.RECORD_TYPE_BASAL_BODY_TEMPERATURE;
            case BASAL_METABOLIC_RATE -> RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE;
            case BLOOD_GLUCOSE -> RecordTypeIdentifier.RECORD_TYPE_BLOOD_GLUCOSE;
            case BLOOD_PRESSURE -> RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE;
            case BODY_FAT -> RecordTypeIdentifier.RECORD_TYPE_BODY_FAT;
            case BODY_TEMPERATURE -> RecordTypeIdentifier.RECORD_TYPE_BODY_TEMPERATURE;
            case BODY_WATER_MASS -> RecordTypeIdentifier.RECORD_TYPE_BODY_WATER_MASS;
            case BONE_MASS -> RecordTypeIdentifier.RECORD_TYPE_BONE_MASS;
            case CERVICAL_MUCUS -> RecordTypeIdentifier.RECORD_TYPE_CERVICAL_MUCUS;
            case HEART_RATE_VARIABILITY_RMSSD ->
                    RecordTypeIdentifier.RECORD_TYPE_HEART_RATE_VARIABILITY_RMSSD;
            case HEIGHT -> RecordTypeIdentifier.RECORD_TYPE_HEIGHT;
            case INTERMENSTRUAL_BLEEDING ->
                    RecordTypeIdentifier.RECORD_TYPE_INTERMENSTRUAL_BLEEDING;
            case LEAN_BODY_MASS -> RecordTypeIdentifier.RECORD_TYPE_LEAN_BODY_MASS;
            case MENSTRUATION_FLOW -> RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_FLOW;
            case OVULATION_TEST -> RecordTypeIdentifier.RECORD_TYPE_OVULATION_TEST;
            case OXYGEN_SATURATION -> RecordTypeIdentifier.RECORD_TYPE_OXYGEN_SATURATION;
            case RESPIRATORY_RATE -> RecordTypeIdentifier.RECORD_TYPE_RESPIRATORY_RATE;
            case RESTING_HEART_RATE -> RecordTypeIdentifier.RECORD_TYPE_RESTING_HEART_RATE;
            case SEXUAL_ACTIVITY -> RecordTypeIdentifier.RECORD_TYPE_SEXUAL_ACTIVITY;
            case VO2_MAX -> RecordTypeIdentifier.RECORD_TYPE_VO2_MAX;
            case WEIGHT -> RecordTypeIdentifier.RECORD_TYPE_WEIGHT;
            case DATA_NOT_SET -> throw new IllegalArgumentException();
        };
    }
}
