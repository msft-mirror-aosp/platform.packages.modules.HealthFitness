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

import static com.android.server.healthconnect.storage.ExportImportSettingsStorage.EXPORT_PERIOD_PREFERENCE_KEY;
import static com.android.server.healthconnect.storage.ExportImportSettingsStorage.EXPORT_URI_PREFERENCE_KEY;

import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.AutoDeleteFrequencyProto;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.DistanceUnitProto;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.EnergyUnitProto;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.ExportSettingsProto;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.HeightUnitProto;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.PrioritizedAppIds;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.TemperatureUnitProto;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.WeightUnitProto;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Class that manages compiling the user settings into a SettingsRecord object.
 *
 * @hide
 */
public final class BackupSettingsHelper {

    private final HealthDataCategoryPriorityHelper mPriorityHelper;
    private final PreferenceHelper mPreferenceHelper;

    public static final String TAG = "BackupSettingsHelper";

    public static final String ENERGY_UNIT_PREF_KEY = "ENERGY_UNIT_KEY";
    public static final String TEMPERATURE_UNIT_PREF_KEY = "TEMPERATURE_UNIT_KEY";
    public static final String HEIGHT_UNIT_PREF_KEY = "HEIGHT_UNIT_KEY";
    public static final String WEIGHT_UNIT_PREF_KEY = "WEIGHT_UNIT_KEY";
    public static final String DISTANCE_UNIT_PREF_KEY = "DISTANCE_UNIT_KEY";
    public static final String AUTO_DELETE_PREF_KEY = "auto_delete_range_picker";

    public BackupSettingsHelper(
            HealthDataCategoryPriorityHelper priorityHelper, PreferenceHelper preferenceHelper) {
        mPriorityHelper = priorityHelper;
        mPreferenceHelper = preferenceHelper;
    }

    /**
     * Collate the user's priority list and unit preferences into a single object.
     *
     * @return the user's settings as a {@code SettingsRecord} object
     */
    public SettingsRecord collectUserSettings() {
        SettingsRecord.Builder builder =
                SettingsRecord.newBuilder()
                        .putAllPriorityList(getPriorityList())
                        .setAutoDeleteFrequency(getAutoDeleteSetting())
                        .setEnergyUnitSetting(getEnergyPreference())
                        .setTemperatureUnitSetting(getTemperaturePreference())
                        .setHeightUnitSetting(getHeightPreference())
                        .setWeightUnitSetting(getWeightPreference())
                        .setDistanceUnitSetting(getDistancePreference());
        Optional<ExportSettingsProto> exportSettings = getExportSettings();
        exportSettings.ifPresent(builder::setExportSettings);
        return builder.build();
    }

    @VisibleForTesting
    private Map<Integer, PrioritizedAppIds> getPriorityList() {
        Map<Integer, List<Long>> priorityListMap =
                mPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable();
        if (priorityListMap.isEmpty()) {
            Slog.d(TAG, "Priority list is empty.");
            return Map.of();
        }
        Map<Integer, PrioritizedAppIds> protoFormattedPriorityList = new HashMap<>();
        for (var categoryRecord : priorityListMap.entrySet()) {
            PrioritizedAppIds formattedAppIds =
                    PrioritizedAppIds.newBuilder().addAllAppId(categoryRecord.getValue()).build();
            protoFormattedPriorityList.put(categoryRecord.getKey(), formattedAppIds);
        }
        return protoFormattedPriorityList;
    }

    private AutoDeleteFrequencyProto getAutoDeleteSetting() {
        String preference = mPreferenceHelper.getPreference(AUTO_DELETE_PREF_KEY);
        return preference == null
                ? AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_UNSPECIFIED
                : AutoDeleteFrequencyProto.valueOf(preference);
    }

    private Optional<ExportSettingsProto> getExportSettings() {
        String exportUriPreference = mPreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY);
        String exportFrequencyPreference =
                mPreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY);
        if (exportUriPreference == null || exportFrequencyPreference == null) {
            return Optional.empty();
        }
        return Optional.of(
                ExportSettingsProto.newBuilder()
                        .setUri(exportUriPreference)
                        .setFrequency(Integer.parseInt(exportFrequencyPreference))
                        .build());
    }

    private TemperatureUnitProto getTemperaturePreference() {
        String preference = mPreferenceHelper.getPreference(TEMPERATURE_UNIT_PREF_KEY);
        return preference == null
                ? TemperatureUnitProto.TEMPERATURE_UNIT_UNSPECIFIED
                : TemperatureUnitProto.valueOf(preference);
    }

    private EnergyUnitProto getEnergyPreference() {
        String preference = mPreferenceHelper.getPreference(ENERGY_UNIT_PREF_KEY);
        return preference == null
                ? EnergyUnitProto.ENERGY_UNIT_UNSPECIFIED
                : EnergyUnitProto.valueOf(preference);
    }

    private HeightUnitProto getHeightPreference() {
        String preference = mPreferenceHelper.getPreference(HEIGHT_UNIT_PREF_KEY);
        return preference == null
                ? HeightUnitProto.HEIGHT_UNIT_UNSPECIFIED
                : HeightUnitProto.valueOf(preference);
    }

    private WeightUnitProto getWeightPreference() {
        String preference = mPreferenceHelper.getPreference(WEIGHT_UNIT_PREF_KEY);
        return preference == null
                ? WeightUnitProto.WEIGHT_UNIT_UNSPECIFIED
                : WeightUnitProto.valueOf(preference);
    }

    private DistanceUnitProto getDistancePreference() {
        String preference = mPreferenceHelper.getPreference(DISTANCE_UNIT_PREF_KEY);
        return preference == null
                ? DistanceUnitProto.DISTANCE_UNIT_UNSPECIFIED
                : DistanceUnitProto.valueOf(preference);
    }
}
