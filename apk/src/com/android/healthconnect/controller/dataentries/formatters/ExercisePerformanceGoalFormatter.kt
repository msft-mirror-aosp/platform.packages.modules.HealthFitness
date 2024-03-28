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
package com.android.healthconnect.controller.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.ExercisePerformanceGoal
import android.icu.text.MessageFormat
import android.util.Log
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Formatter for printing ExercisePerformanceGoal data. */
class ExercisePerformanceGoalFormatter
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val speedFormatter: SpeedFormatter
) {

    fun formatGoal(
        goal: ExercisePerformanceGoal,
        unitPreferences: UnitPreferences
    ): FormattedEntry {
        return FormattedEntry.ExercisePerformanceGoalEntry(
            goal = goal,
            title = formatPerformanceGoal(goal, unitPreferences),
            titleA11y = formatPerformanceGoalA11y(goal, unitPreferences))
    }

    private fun formatPerformanceGoal(
        performanceGoal: ExercisePerformanceGoal,
        unitPreferences: UnitPreferences
    ): String {
        return when (performanceGoal) {
            is ExercisePerformanceGoal.PowerGoal ->
                context.getString(
                    R.string.performance_goals_range,
                    MessageFormat.format(
                        context.getString(R.string.watt_format),
                        mapOf("value" to performanceGoal.minPower.inWatts)),
                    MessageFormat.format(
                        context.getString(R.string.watt_format),
                        mapOf("value" to performanceGoal.maxPower.inWatts)))
            is ExercisePerformanceGoal.AmrapGoal ->
                context.getString(R.string.amrap_performance_goal)
            is ExercisePerformanceGoal.CadenceGoal ->
                context.getString(
                    R.string.performance_goals_range,
                    MessageFormat.format(
                        context.getString(R.string.cycling_rpm),
                        mapOf("count" to performanceGoal.minRpm)),
                    MessageFormat.format(
                        context.getString(R.string.cycling_rpm),
                        mapOf("count" to performanceGoal.maxRpm)))
            is ExercisePerformanceGoal.SpeedGoal ->
                context.getString(
                    R.string.performance_goals_range,
                    speedFormatter.formatSpeedValue(
                        SpeedFormatter(context).getUnitRes(unitPreferences),
                        performanceGoal.minSpeed.inMetersPerSecond,
                        unitPreferences),
                    speedFormatter.formatSpeedValue(
                        SpeedFormatter(context).getUnitRes(unitPreferences),
                        performanceGoal.maxSpeed.inMetersPerSecond,
                        unitPreferences))
            is ExercisePerformanceGoal.HeartRateGoal ->
                context.getString(
                    R.string.performance_goals_range,
                    MessageFormat.format(
                        context.getString(R.string.heart_rate_value),
                        mapOf("count" to performanceGoal.minBpm)),
                    MessageFormat.format(
                        context.getString(R.string.heart_rate_value),
                        mapOf("count" to performanceGoal.maxBpm)))
            is ExercisePerformanceGoal.WeightGoal ->
                MassFormatter.formatValue(
                    context, performanceGoal.mass, unitPreferences.getWeightUnit())
            is ExercisePerformanceGoal.RateOfPerceivedExertionGoal ->
                context.getString(R.string.rate_of_perceived_exertion_goal, performanceGoal.rpe)
            else -> {
                Log.e("Error", "Unknown performance goal $performanceGoal")
                return ""
            }
        }
    }

    private fun formatPerformanceGoalA11y(
        performanceGoal: ExercisePerformanceGoal,
        unitPreferences: UnitPreferences
    ): String {
        return when (performanceGoal) {
            is ExercisePerformanceGoal.PowerGoal ->
                context.getString(
                    R.string.performance_goals_range,
                    MessageFormat.format(
                        context.getString(R.string.watt_format_long),
                        mapOf("value" to performanceGoal.minPower.inWatts)),
                    MessageFormat.format(
                        context.getString(R.string.watt_format_long),
                        mapOf("value" to performanceGoal.maxPower.inWatts)))
            is ExercisePerformanceGoal.AmrapGoal ->
                context.getString(R.string.amrap_performance_goal)
            is ExercisePerformanceGoal.CadenceGoal ->
                context.getString(
                    R.string.performance_goals_range,
                    MessageFormat.format(
                        context.getString(R.string.cycling_rpm_long),
                        mapOf("count" to performanceGoal.minRpm)),
                    MessageFormat.format(
                        context.getString(R.string.cycling_rpm_long),
                        mapOf("count" to performanceGoal.maxRpm)))
            is ExercisePerformanceGoal.SpeedGoal ->
                context.getString(
                    R.string.performance_goals_range,
                    speedFormatter.formatSpeedValue(
                        SpeedFormatter(context).getA11yUnitRes(unitPreferences),
                        performanceGoal.minSpeed.inMetersPerSecond,
                        unitPreferences),
                    speedFormatter.formatSpeedValue(
                        SpeedFormatter(context).getA11yUnitRes(unitPreferences),
                        performanceGoal.maxSpeed.inMetersPerSecond,
                        unitPreferences))
            is ExercisePerformanceGoal.HeartRateGoal ->
                context.getString(
                    R.string.performance_goals_range,
                    MessageFormat.format(
                        context.getString(R.string.heart_rate_long_value),
                        mapOf("count" to performanceGoal.minBpm)),
                    MessageFormat.format(
                        context.getString(R.string.heart_rate_long_value),
                        mapOf("count" to performanceGoal.maxBpm)))
            is ExercisePerformanceGoal.WeightGoal ->
                MassFormatter.formatA11yValue(
                    context, performanceGoal.mass, unitPreferences.getWeightUnit())
            is ExercisePerformanceGoal.RateOfPerceivedExertionGoal ->
                context.getString(R.string.rate_of_perceived_exertion_goal, performanceGoal.rpe)
            else -> {
                Log.e("Error", "Unknown performance goal $performanceGoal")
                return ""
            }
        }
    }
}
