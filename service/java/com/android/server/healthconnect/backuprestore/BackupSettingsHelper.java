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

import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.util.List;
import java.util.Map;

/**
 * Class that manages compiling the user settings into a Parcelable format.
 *
 * @hide
 */
public final class BackupSettingsHelper {

    private final HealthDataCategoryPriorityHelper mPriorityHelper;
    private final PreferenceHelper mPreferenceHelper;
    private final ExportImportSettingsStorage mExportImportSettingsStorage;

    public static final String TAG = "BackupSettingsHelper";

    public static final String ENERGY_UNIT_PREF_KEY = "ENERGY_UNIT_KEY";
    public static final String TEMPERATURE_UNIT_PREF_KEY = "TEMPERATURE_UNIT_KEY";
    public static final String HEIGHT_UNIT_PREF_KEY = "HEIGHT_UNIT_KEY";
    public static final String WEIGHT_UNIT_PREF_KEY = "WEIGHT_UNIT_KEY";
    public static final String DISTANCE_UNIT_PREF_KEY = "DISTANCE_UNIT_KEY";

    public BackupSettingsHelper(
            HealthDataCategoryPriorityHelper priorityHelper,
            PreferenceHelper preferenceHelper,
            ExportImportSettingsStorage exportImportSettingsStorage) {
        mPriorityHelper = priorityHelper;
        mPreferenceHelper = preferenceHelper;
        mExportImportSettingsStorage = exportImportSettingsStorage;
    }

    /**
     * Collate the user's priority list and unit preferences into a single object.
     *
     * @return the user's settings as a {@code CloudBackupSettings} object
     */
    public CloudBackupSettings collectUserSettings() {
        return new CloudBackupSettings.Builder()
                .setPriorityList(getPriorityList())
                .setExportSettings(getExportSettings())
                .setEnergyUnitPreference(getEnergyPreference())
                .setTemperatureUnitPreference(getTemperaturePreference())
                .setHeightUnitPreference(getHeightPreference())
                .setWeightUnitPreference(getWeightPreference())
                .setDistanceUnitPreference(getDistancePreference())
                .build();
    }

    private Map<Integer, List<Long>> getPriorityList() {
        Map<Integer, List<Long>> priorityListMap =
                mPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable();
        if (priorityListMap.isEmpty()) {
            Slog.d(TAG, "Priority list is empty.");
            return Map.of();
        }
        return priorityListMap;
    }

    private ScheduledExportSettings getExportSettings() {
        ScheduledExportSettings.Builder scheduledExportSettingsBuilder =
                new ScheduledExportSettings.Builder();
        scheduledExportSettingsBuilder.setPeriodInDays(
                mExportImportSettingsStorage.getScheduledExportPeriodInDays());
        scheduledExportSettingsBuilder.setUri(mExportImportSettingsStorage.getUri());
        return scheduledExportSettingsBuilder.build();
    }

    @Nullable
    private CloudBackupSettings.TemperatureUnit getTemperaturePreference() {
        String preference = mPreferenceHelper.getPreference(TEMPERATURE_UNIT_PREF_KEY);
        return preference == null ? null : CloudBackupSettings.TemperatureUnit.valueOf(preference);
    }

    @Nullable
    private CloudBackupSettings.EnergyUnit getEnergyPreference() {
        String preference = mPreferenceHelper.getPreference(ENERGY_UNIT_PREF_KEY);
        return preference == null ? null : CloudBackupSettings.EnergyUnit.valueOf(preference);
    }

    @Nullable
    private CloudBackupSettings.HeightUnit getHeightPreference() {
        String preference = mPreferenceHelper.getPreference(HEIGHT_UNIT_PREF_KEY);
        return preference == null ? null : CloudBackupSettings.HeightUnit.valueOf(preference);
    }

    @Nullable
    private CloudBackupSettings.WeightUnit getWeightPreference() {
        String preference = mPreferenceHelper.getPreference(WEIGHT_UNIT_PREF_KEY);
        return preference == null ? null : CloudBackupSettings.WeightUnit.valueOf(preference);
    }

    @Nullable
    private CloudBackupSettings.DistanceUnit getDistancePreference() {
        String preference = mPreferenceHelper.getPreference(DISTANCE_UNIT_PREF_KEY);
        return preference == null ? null : CloudBackupSettings.DistanceUnit.valueOf(preference);
    }
}
