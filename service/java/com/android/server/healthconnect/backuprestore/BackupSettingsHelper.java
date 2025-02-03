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
import com.android.server.healthconnect.proto.backuprestore.Settings;
import com.android.server.healthconnect.proto.backuprestore.Settings.AppInfo;
import com.android.server.healthconnect.proto.backuprestore.Settings.AutoDeleteFrequencyProto;
import com.android.server.healthconnect.proto.backuprestore.Settings.DistanceUnitProto;
import com.android.server.healthconnect.proto.backuprestore.Settings.EnergyUnitProto;
import com.android.server.healthconnect.proto.backuprestore.Settings.ExportSettingsProto;
import com.android.server.healthconnect.proto.backuprestore.Settings.HeightUnitProto;
import com.android.server.healthconnect.proto.backuprestore.Settings.PrioritizedAppIds;
import com.android.server.healthconnect.proto.backuprestore.Settings.TemperatureUnitProto;
import com.android.server.healthconnect.proto.backuprestore.Settings.WeightUnitProto;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Class that manages compiling the user settings into a proto.
 *
 * @hide
 */
public final class BackupSettingsHelper {

    private final HealthDataCategoryPriorityHelper mPriorityHelper;
    private final PreferenceHelper mPreferenceHelper;
    private final AppInfoHelper mAppInfoHelper;

    public static final String TAG = "BackupSettingsHelper";

    public static final String ENERGY_UNIT_PREF_KEY = "ENERGY_UNIT_KEY";
    public static final String TEMPERATURE_UNIT_PREF_KEY = "TEMPERATURE_UNIT_KEY";
    public static final String HEIGHT_UNIT_PREF_KEY = "HEIGHT_UNIT_KEY";
    public static final String WEIGHT_UNIT_PREF_KEY = "WEIGHT_UNIT_KEY";
    public static final String DISTANCE_UNIT_PREF_KEY = "DISTANCE_UNIT_KEY";
    public static final String AUTO_DELETE_PREF_KEY = "auto_delete_range_picker";

    public BackupSettingsHelper(
            HealthDataCategoryPriorityHelper priorityHelper,
            PreferenceHelper preferenceHelper,
            AppInfoHelper appInfoHelper) {
        mPriorityHelper = priorityHelper;
        mPreferenceHelper = preferenceHelper;
        mAppInfoHelper = appInfoHelper;
    }

    /**
     * Collate the user's priority list and unit preferences into a single object.
     *
     * @return the user's settings as a {@code Settings} object
     */
    public Settings collectUserSettings() {
        Settings.Builder builder =
                Settings.newBuilder()
                        .putAllPriorityList(getPriorityList())
                        .putAllAppInfo(getAppInfo())
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

    /**
     * Override the current settings with the provided new user settings, with the exception of the
     * priority list which should be a merged version of the old and new priority list.
     */
    public void restoreUserSettings(Settings newUserSettings) {
        mergePriorityLists(
                mPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable(),
                fromProtoToPriorityList(newUserSettings.getPriorityListMap()));
        restoreAppInfo(newUserSettings.getAppInfoMap());
        if (newUserSettings.hasExportSettings()) {
            mPreferenceHelper.insertOrReplacePreference(
                    EXPORT_URI_PREFERENCE_KEY, newUserSettings.getExportSettings().getUri());
            mPreferenceHelper.insertOrReplacePreference(
                    EXPORT_PERIOD_PREFERENCE_KEY,
                    String.valueOf(newUserSettings.getExportSettings().getFrequency()));
        }
        AutoDeleteFrequencyProto newAutoDeleteFrequency = newUserSettings.getAutoDeleteFrequency();
        if (newAutoDeleteFrequency != AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_UNSPECIFIED) {
            mPreferenceHelper.insertOrReplacePreference(
                    AUTO_DELETE_PREF_KEY, newAutoDeleteFrequency.name());
        }
        EnergyUnitProto newEnergyUnit = newUserSettings.getEnergyUnitSetting();
        if (newEnergyUnit != EnergyUnitProto.ENERGY_UNIT_UNSPECIFIED) {
            mPreferenceHelper.insertOrReplacePreference(ENERGY_UNIT_PREF_KEY, newEnergyUnit.name());
        }
        TemperatureUnitProto newTemperatureUnit = newUserSettings.getTemperatureUnitSetting();
        if (newTemperatureUnit != TemperatureUnitProto.TEMPERATURE_UNIT_UNSPECIFIED) {
            mPreferenceHelper.insertOrReplacePreference(
                    TEMPERATURE_UNIT_PREF_KEY, newTemperatureUnit.name());
        }
        HeightUnitProto newHeightUnit = newUserSettings.getHeightUnitSetting();
        if (newHeightUnit != HeightUnitProto.HEIGHT_UNIT_UNSPECIFIED) {
            mPreferenceHelper.insertOrReplacePreference(HEIGHT_UNIT_PREF_KEY, newHeightUnit.name());
        }
        WeightUnitProto newWeightUnit = newUserSettings.getWeightUnitSetting();
        if (newWeightUnit != WeightUnitProto.WEIGHT_UNIT_UNSPECIFIED) {
            mPreferenceHelper.insertOrReplacePreference(WEIGHT_UNIT_PREF_KEY, newWeightUnit.name());
        }
        DistanceUnitProto newDistanceUnit = newUserSettings.getDistanceUnitSetting();
        if (newDistanceUnit != DistanceUnitProto.DISTANCE_UNIT_UNSPECIFIED) {
            mPreferenceHelper.insertOrReplacePreference(
                    DISTANCE_UNIT_PREF_KEY, newDistanceUnit.name());
        }
    }

    /**
     * Restores a user's AppInfo settings from the passed in {@code Map<String, AppInfo>} object.
     *
     * @param appInfoMap the AppInfo being restored
     */
    @VisibleForTesting
    public void restoreAppInfo(Map<String, AppInfo> appInfoMap) {
        for (var appInfoEntry : appInfoMap.entrySet()) {
            String packageName = appInfoEntry.getKey();
            AppInfo appInfo = appInfoEntry.getValue();
            String appName = null;
            if (appInfo.hasAppName()) {
                appName = appInfo.getAppName();
            }
            mAppInfoHelper.addOrUpdateAppInfoIfNoAppInfoEntryExists(packageName, appName);
        }
    }

    /**
     * Converts a priority list from the proto format (entries of type {@code PrioritizedAppIds}) to
     * the standard format (entries of type {@code List<long>}).
     *
     * @param priorityListProto the proto-formatted priority list
     * @return the converted priority list
     */
    @VisibleForTesting
    Map<Integer, List<Long>> fromProtoToPriorityList(
            Map<Integer, PrioritizedAppIds> priorityListProto) {
        Map<Integer, List<Long>> parsedPriorityList = new HashMap<>();
        for (var priorityRecord : priorityListProto.entrySet()) {
            parsedPriorityList.put(
                    priorityRecord.getKey(), priorityRecord.getValue().getAppIdList());
        }
        return parsedPriorityList;
    }

    /**
     * Take two priority lists and merge them, removing any duplicate entries, and replace the
     * existing priority list settings with this newly merged version.
     *
     * @param current the priority list currently stored in settings
     * @param imported the new priority list being restored
     */
    @VisibleForTesting
    void mergePriorityLists(Map<Integer, List<Long>> current, Map<Integer, List<Long>> imported) {
        imported.forEach(
                (category, appIdList) -> {
                    if (appIdList.isEmpty()) {
                        return;
                    }
                    List<Long> currentPriorities =
                            Optional.ofNullable(current.get(category)).orElse(List.of());
                    List<Long> mergedPrioritisedAppIds =
                            Stream.concat(currentPriorities.stream(), appIdList.stream())
                                    .distinct()
                                    .toList();
                    mPriorityHelper.setPriorityOrder(
                            category, mAppInfoHelper.getPackageNames(mergedPrioritisedAppIds));
                });
    }

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

    private Map<String, AppInfo> getAppInfo() {
        Map<String, AppInfo> appInfoMap = new HashMap<>();
        for (var appInfoEntry : mAppInfoHelper.getAppInfoMap().entrySet()) {
            String appName = appInfoEntry.getValue().getName();
            AppInfo.Builder appInfoBuilder = AppInfo.newBuilder();
            if (appName != null) {
                appInfoBuilder.setAppName(appName);
            }
            appInfoMap.putIfAbsent(appInfoEntry.getKey(), appInfoBuilder.build());
        }
        return appInfoMap;
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
