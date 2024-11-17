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

import android.annotation.Nullable;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.util.Slog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** @hide */
public class CloudBackupSettings {

    private static final String TAG = "CloudBackupSettings";

    /** Builder class for {@link CloudBackupSettings} */
    public static final class Builder {
        private Map<Integer, List<Long>> mPriorityListMap;
        @Nullable private AutoDeleteFrequency mAutoDeletePreference;
        @Nullable private ScheduledExportSettings mExportSettings;
        @Nullable private EnergyUnit mEnergyUnitPreference;
        @Nullable private TemperatureUnit mTemperatureUnitPreference;
        @Nullable private HeightUnit mHeightUnitPreference;
        @Nullable private WeightUnit mWeightUnitPreference;
        @Nullable private DistanceUnit mDistanceUnitPreference;

        public Builder() {
            this(
                    Map.of(),
                    null,
                    null,
                    DEFAULT_ENERGY_UNIT,
                    DEFAULT_TEMPERATURE_UNIT,
                    DEFAULT_HEIGHT_UNIT,
                    DEFAULT_WEIGHT_UNIT,
                    DEFAULT_DISTANCE_UNIT);
        }

        public Builder(
                Map<Integer, List<Long>> priorityListMap,
                @Nullable AutoDeleteFrequency autoDeletePreference,
                @Nullable ScheduledExportSettings exportSettings,
                @Nullable EnergyUnit energyUnitPreference,
                @Nullable TemperatureUnit temperatureUnitPreference,
                @Nullable HeightUnit heightUnitPreference,
                @Nullable WeightUnit weightUnitPreference,
                @Nullable DistanceUnit distanceUnitPreference) {
            Objects.requireNonNull(priorityListMap);
            mPriorityListMap = priorityListMap;
            mAutoDeletePreference = autoDeletePreference;
            mExportSettings =
                    exportSettings != null
                            ? exportSettings
                            : new ScheduledExportSettings.Builder().build();
            mEnergyUnitPreference =
                    energyUnitPreference != null ? energyUnitPreference : DEFAULT_ENERGY_UNIT;
            mTemperatureUnitPreference =
                    temperatureUnitPreference != null
                            ? temperatureUnitPreference
                            : DEFAULT_TEMPERATURE_UNIT;
            mHeightUnitPreference =
                    heightUnitPreference != null ? heightUnitPreference : DEFAULT_HEIGHT_UNIT;
            mWeightUnitPreference =
                    weightUnitPreference != null ? weightUnitPreference : DEFAULT_WEIGHT_UNIT;
            mDistanceUnitPreference =
                    distanceUnitPreference != null ? distanceUnitPreference : DEFAULT_DISTANCE_UNIT;
        }

        /** Saves the user's priority list and returns the Builder for chaining. */
        public Builder setPriorityList(Map<Integer, List<Long>> priorityListMap) {
            Objects.requireNonNull(priorityListMap);
            mPriorityListMap = priorityListMap;
            return this;
        }

        /** Saves the user's auto-delete setting and returns the Builder for chaining. */
        public Builder setAutoDeleteSetting(@Nullable AutoDeleteFrequency autoDeletePreference) {
            mAutoDeletePreference = autoDeletePreference;
            return this;
        }

        /** Saves the user's export settings and returns the Builder for chaining. */
        public Builder setExportSettings(
                @Nullable ScheduledExportSettings scheduledExportSettings) {
            mExportSettings = scheduledExportSettings;
            return this;
        }

        /** Saves the user's energy unit preference and returns the Builder for chaining. */
        public Builder setEnergyUnitPreference(@Nullable EnergyUnit energyUnitPreference) {
            mEnergyUnitPreference =
                    energyUnitPreference != null ? energyUnitPreference : DEFAULT_ENERGY_UNIT;
            return this;
        }

        /** Saves the user's temperature unit preference and returns the Builder for chaining. */
        public Builder setTemperatureUnitPreference(
                @Nullable TemperatureUnit temperatureUnitPreference) {
            mTemperatureUnitPreference =
                    temperatureUnitPreference != null
                            ? temperatureUnitPreference
                            : DEFAULT_TEMPERATURE_UNIT;
            return this;
        }

        /** Saves the user's weight unit preference and returns the Builder for chaining. */
        public Builder setWeightUnitPreference(@Nullable WeightUnit weightUnitPreference) {
            mWeightUnitPreference =
                    weightUnitPreference != null ? weightUnitPreference : DEFAULT_WEIGHT_UNIT;
            return this;
        }

        /** Saves the user's height unit preference and returns the Builder for chaining. */
        public Builder setHeightUnitPreference(@Nullable HeightUnit heightUnitPreference) {
            mHeightUnitPreference =
                    heightUnitPreference != null ? heightUnitPreference : DEFAULT_HEIGHT_UNIT;
            return this;
        }

        /** Saves the user's distance unit preference and returns the Builder for chaining. */
        public Builder setDistanceUnitPreference(@Nullable DistanceUnit distanceUnitPreference) {
            mDistanceUnitPreference =
                    distanceUnitPreference != null ? distanceUnitPreference : DEFAULT_DISTANCE_UNIT;
            return this;
        }

        /** Builds a CloudBackupSettings object with the set (or default) values. */
        public CloudBackupSettings build() {
            return new CloudBackupSettings(
                    mPriorityListMap,
                    mAutoDeletePreference,
                    mExportSettings,
                    mEnergyUnitPreference,
                    mTemperatureUnitPreference,
                    mHeightUnitPreference,
                    mWeightUnitPreference,
                    mDistanceUnitPreference);
        }
    }

    private final Map<Integer, List<Long>> mPriorityListMapSetting;
    @Nullable private final AutoDeleteFrequency mAutoDeleteSetting;
    @Nullable private final ScheduledExportSettings mExportSettings;
    @Nullable private final EnergyUnit mEnergyUnitSetting;
    @Nullable private final TemperatureUnit mTemperatureUnitSetting;
    @Nullable private final HeightUnit mHeightUnitSetting;
    @Nullable private final WeightUnit mWeightUnitSetting;
    @Nullable private final DistanceUnit mDistanceUnitSetting;

    private CloudBackupSettings(
            Map<Integer, List<Long>> priorityList,
            @Nullable AutoDeleteFrequency autoDeleteSetting,
            @Nullable ScheduledExportSettings exportSettings,
            @Nullable EnergyUnit energyUnit,
            @Nullable TemperatureUnit temperatureUnit,
            @Nullable HeightUnit heightUnit,
            @Nullable WeightUnit weightUnit,
            @Nullable DistanceUnit distanceUnit) {
        mPriorityListMapSetting = priorityList;
        mAutoDeleteSetting = autoDeleteSetting;
        mExportSettings = exportSettings;
        mEnergyUnitSetting = energyUnit;
        mTemperatureUnitSetting = temperatureUnit;
        mHeightUnitSetting = heightUnit;
        mWeightUnitSetting = weightUnit;
        mDistanceUnitSetting = distanceUnit;
    }

    /**
     * @return the {@code CloudBackupSettings} object as a byte array.
     */
    public byte[] toByteArray() {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(byteArrayOutputStream)) {
            out.writeObject(this);
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Slog.d(TAG, "Unable to convert CloudBackupSettings to byte array.");
            return new byte[0];
        }
    }

    /**
     * @return the user's priority list setting.
     */
    public Map<Integer, List<Long>> getPriorityListMapSetting() {
        return mPriorityListMapSetting;
    }

    /**
     * @return the user's auto delete preference setting.
     */
    @Nullable
    public AutoDeleteFrequency getAutoDeleteSetting() {
        return mAutoDeleteSetting;
    }

    /**
     * @return the user's scheduled export settings.
     */
    @Nullable
    public ScheduledExportSettings getScheduledExportSettings() {
        return mExportSettings;
    }

    /**
     * @return the user's energy unit preference setting.
     */
    @Nullable
    public EnergyUnit getEnergyUnitSetting() {
        return mEnergyUnitSetting;
    }

    /**
     * @return the user's temperature unit preference setting.
     */
    @Nullable
    public TemperatureUnit getTemperatureUnitSetting() {
        return mTemperatureUnitSetting;
    }

    /**
     * @return the user's height unit preference setting.
     */
    @Nullable
    public HeightUnit getHeightUnitSetting() {
        return mHeightUnitSetting;
    }

    /**
     * @return the user's weight unit preference setting.
     */
    @Nullable
    public WeightUnit getWeightUnitSetting() {
        return mWeightUnitSetting;
    }

    /**
     * @return the user's distance unit preference setting.
     */
    @Nullable
    public DistanceUnit getDistanceUnitSetting() {
        return mDistanceUnitSetting;
    }

    /** Class grouping the individual unit preferences. */
    public interface UnitPreference {}

    /** The available unit options for energy measurements. */
    public enum EnergyUnit implements UnitPreference {
        CALORIE,
        KILOJOULE
    }

    /** The available unit options for temperature measurements. */
    public enum TemperatureUnit implements UnitPreference {
        CELSIUS,
        FAHRENHEIT,
        KELVIN
    }

    /** The available unit options for height measurements. */
    public enum HeightUnit implements UnitPreference {
        CENTIMETERS,
        FEET
    }

    /** The available unit options for weight measurements. */
    public enum WeightUnit implements UnitPreference {
        POUND,
        KILOGRAM,
        STONE
    }

    /** The available unit options for distance measurements. */
    public enum DistanceUnit implements UnitPreference {
        KILOMETERS,
        MILES
    }

    /** The available auto-delete frequency options for auto-delete preference. */
    public enum AutoDeleteFrequency {
        AUTO_DELETE_RANGE_NEVER,
        AUTO_DELETE_RANGE_THREE_MONTHS,
        AUTO_DELETE_RANGE_EIGHTEEN_MONTHS
    }

    public static final EnergyUnit DEFAULT_ENERGY_UNIT = EnergyUnit.CALORIE;
    public static final TemperatureUnit DEFAULT_TEMPERATURE_UNIT = TemperatureUnit.FAHRENHEIT;
    public static final HeightUnit DEFAULT_HEIGHT_UNIT = HeightUnit.CENTIMETERS;
    public static final WeightUnit DEFAULT_WEIGHT_UNIT = WeightUnit.KILOGRAM;
    public static final DistanceUnit DEFAULT_DISTANCE_UNIT = DistanceUnit.KILOMETERS;
}
