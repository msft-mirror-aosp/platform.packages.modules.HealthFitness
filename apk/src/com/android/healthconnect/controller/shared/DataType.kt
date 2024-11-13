/**
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.healthconnect.controller.shared

import android.health.connect.datatypes.ActiveCaloriesBurnedRecord
import android.health.connect.datatypes.BasalBodyTemperatureRecord
import android.health.connect.datatypes.BasalMetabolicRateRecord
import android.health.connect.datatypes.BloodGlucoseRecord
import android.health.connect.datatypes.BloodPressureRecord
import android.health.connect.datatypes.BodyFatRecord
import android.health.connect.datatypes.BodyTemperatureRecord
import android.health.connect.datatypes.BodyWaterMassRecord
import android.health.connect.datatypes.BoneMassRecord
import android.health.connect.datatypes.CervicalMucusRecord
import android.health.connect.datatypes.CyclingPedalingCadenceRecord
import android.health.connect.datatypes.DistanceRecord
import android.health.connect.datatypes.ElevationGainedRecord
import android.health.connect.datatypes.ExerciseSessionRecord
import android.health.connect.datatypes.FloorsClimbedRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.HeartRateVariabilityRmssdRecord
import android.health.connect.datatypes.HeightRecord
import android.health.connect.datatypes.HydrationRecord
import android.health.connect.datatypes.IntermenstrualBleedingRecord
import android.health.connect.datatypes.LeanBodyMassRecord
import android.health.connect.datatypes.MenstruationFlowRecord
import android.health.connect.datatypes.MenstruationPeriodRecord
import android.health.connect.datatypes.MindfulnessSessionRecord
import android.health.connect.datatypes.NutritionRecord
import android.health.connect.datatypes.OvulationTestRecord
import android.health.connect.datatypes.OxygenSaturationRecord
import android.health.connect.datatypes.PlannedExerciseSessionRecord
import android.health.connect.datatypes.PowerRecord
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.RespiratoryRateRecord
import android.health.connect.datatypes.RestingHeartRateRecord
import android.health.connect.datatypes.SexualActivityRecord
import android.health.connect.datatypes.SkinTemperatureRecord
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.SpeedRecord
import android.health.connect.datatypes.StepsCadenceRecord
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.TotalCaloriesBurnedRecord
import android.health.connect.datatypes.Vo2MaxRecord
import android.health.connect.datatypes.WeightRecord
import android.health.connect.datatypes.WheelchairPushesRecord
import android.health.connect.internal.datatypes.utils.HealthConnectMappings
import com.android.healthfitness.flags.Flags
import kotlin.reflect.KClass

typealias DataType = KClass<out Record>

fun getDataTypeForClassName(classSimpleName: String): DataType {
    return SUPPORTED_DATA_TYPES.first { it.java.simpleName == classSimpleName }
}

private val SUPPORTED_DATA_TYPES =
    if (Flags.healthConnectMappings()) getSupportedDataTypes()
    else
        listOf(
            ActiveCaloriesBurnedRecord::class,
            BasalMetabolicRateRecord::class,
            DistanceRecord::class,
            HeartRateRecord::class,
            PowerRecord::class,
            SpeedRecord::class,
            StepsRecord::class,
            StepsCadenceRecord::class,
            TotalCaloriesBurnedRecord::class,
            HeightRecord::class,
            BodyFatRecord::class,
            OxygenSaturationRecord::class,
            BodyTemperatureRecord::class,
            BasalBodyTemperatureRecord::class,
            WheelchairPushesRecord::class,
            RestingHeartRateRecord::class,
            RespiratoryRateRecord::class,
            HydrationRecord::class,
            FloorsClimbedRecord::class,
            ElevationGainedRecord::class,
            BoneMassRecord::class,
            LeanBodyMassRecord::class,
            WeightRecord::class,
            BloodGlucoseRecord::class,
            NutritionRecord::class,
            BloodPressureRecord::class,
            Vo2MaxRecord::class,
            CyclingPedalingCadenceRecord::class,
            CervicalMucusRecord::class,
            SexualActivityRecord::class,
            OvulationTestRecord::class,
            MenstruationFlowRecord::class,
            MenstruationPeriodRecord::class,
            SleepSessionRecord::class,
            ExerciseSessionRecord::class,
            BodyWaterMassRecord::class,
            IntermenstrualBleedingRecord::class,
            HeartRateVariabilityRmssdRecord::class,
            SkinTemperatureRecord::class,
            PlannedExerciseSessionRecord::class,
            MindfulnessSessionRecord::class,
        )

private fun getSupportedDataTypes(): List<DataType> {
    return HealthConnectMappings.getInstance()
        .recordIdToExternalRecordClassMap
        .values
        .map { it.kotlin }
        .toList()
}
