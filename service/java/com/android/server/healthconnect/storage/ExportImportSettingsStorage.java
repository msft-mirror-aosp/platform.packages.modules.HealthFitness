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

package com.android.server.healthconnect.storage;

import static android.health.connect.Constants.DEFAULT_INT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.HealthConnectManager;
import android.health.connect.exportimport.ImportStatus;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.health.connect.exportimport.ScheduledExportStatus;
import android.net.Uri;

import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.time.Instant;

/**
 * Stores the settings for the scheduled export service.
 *
 * @hide
 */
public final class ExportImportSettingsStorage {
    // Scheduled Export Settings
    private static final String EXPORT_URI_PREFERENCE_KEY = "export_uri_key";
    private static final String EXPORT_PERIOD_PREFERENCE_KEY = "export_period_key";

    // Scheduled Export State
    private static final String LAST_SUCCESSFUL_EXPORT_PREFERENCE_KEY =
            "last_successful_export_key";
    private static final String LAST_EXPORT_ERROR_PREFERENCE_KEY = "last_export_error_key";

    // Import State
    private static final String IMPORT_ONGOING_PREFERENCE_KEY = "import_ongoing_key";
    private static final String LAST_IMPORT_ERROR_PREFERENCE_KEY = "last_import_error_key";

    /**
     * Configures the settings for the scheduled export of Health Connect data.
     *
     * @param settings Settings to use for the scheduled export. Use null to clear the settings.
     */
    public static void configure(@Nullable ScheduledExportSettings settings) {
        if (settings != null) {
            configureNonNull(settings);
        } else {
            clear();
        }
    }

    /** Configures the settings for the scheduled export of Health Connect data. */
    private static void configureNonNull(@NonNull ScheduledExportSettings settings) {
        if (settings.getUri() != null) {
            PreferenceHelper.getInstance()
                    .insertOrReplacePreference(
                            EXPORT_URI_PREFERENCE_KEY, settings.getUri().toString());
            String lastExportError =
                    PreferenceHelper.getInstance().getPreference(LAST_EXPORT_ERROR_PREFERENCE_KEY);
            if (lastExportError != null
                    && lastExportError.equals(
                            String.valueOf(HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS))) {
                PreferenceHelper.getInstance().removeKey(LAST_EXPORT_ERROR_PREFERENCE_KEY);
            }
        }

        if (settings.getPeriodInDays() != DEFAULT_INT) {
            String periodInDays = String.valueOf(settings.getPeriodInDays());
            PreferenceHelper.getInstance()
                    .insertOrReplacePreference(EXPORT_PERIOD_PREFERENCE_KEY, periodInDays);
        }
    }

    /** Clears the settings for the scheduled export of Health Connect data. */
    private static void clear() {
        PreferenceHelper.getInstance().removeKey(EXPORT_URI_PREFERENCE_KEY);
        PreferenceHelper.getInstance().removeKey(EXPORT_PERIOD_PREFERENCE_KEY);
    }

    /** Gets scheduled export URI for exporting Health Connect data. */
    public static Uri getUri() {
        String result = PreferenceHelper.getInstance().getPreference(EXPORT_URI_PREFERENCE_KEY);
        if (result == null) throw new IllegalArgumentException("Export URI cannot be null.");
        return Uri.parse(result);
    }

    /** Gets scheduled export period for exporting Health Connect data. */
    public static int getScheduledExportPeriodInDays() {
        String result = PreferenceHelper.getInstance().getPreference(EXPORT_PERIOD_PREFERENCE_KEY);

        if (result == null) return 0;
        return Integer.parseInt(result);
    }

    /** Set the last successful export time for the currently configured export. */
    public static void setLastSuccessfulExport(Instant instant) {
        PreferenceHelper.getInstance()
                .insertOrReplacePreference(
                        LAST_SUCCESSFUL_EXPORT_PREFERENCE_KEY,
                        String.valueOf(instant.toEpochMilli()));
        PreferenceHelper.getInstance().removeKey(LAST_EXPORT_ERROR_PREFERENCE_KEY);
    }

    /** Set errors during the last failed export attempt. */
    public static void setLastExportError(@HealthConnectManager.DataExportError int error) {
        PreferenceHelper.getInstance()
                .insertOrReplacePreference(LAST_EXPORT_ERROR_PREFERENCE_KEY, String.valueOf(error));
    }

    /** Get the status of the currently scheduled export. */
    public static ScheduledExportStatus getScheduledExportStatus() {
        PreferenceHelper prefHelper = PreferenceHelper.getInstance();
        String lastExportTime = prefHelper.getPreference(LAST_SUCCESSFUL_EXPORT_PREFERENCE_KEY);
        String lastExportError = prefHelper.getPreference(LAST_EXPORT_ERROR_PREFERENCE_KEY);
        String periodInDays = prefHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY);

        return new ScheduledExportStatus(
                lastExportTime == null
                        ? null
                        : Instant.ofEpochMilli(Long.parseLong(lastExportTime)),
                lastExportError == null
                        ? HealthConnectManager.DATA_EXPORT_ERROR_NONE
                        : Integer.parseInt(lastExportError),
                periodInDays == null ? 0 : Integer.parseInt(periodInDays));
    }

    /** Set to true when an import starts and to false when a data import completes */
    public static void setImportOngoing(boolean importOngoing) {
        PreferenceHelper.getInstance()
                .insertOrReplacePreference(
                        IMPORT_ONGOING_PREFERENCE_KEY, String.valueOf(importOngoing));
    }

    /** Set errors during the last failed import attempt. */
    public static void setLastImportError(@ImportStatus.DataImportError int error) {
        PreferenceHelper.getInstance()
                .insertOrReplacePreference(LAST_IMPORT_ERROR_PREFERENCE_KEY, String.valueOf(error));
    }

    /** Get the status of the last data import. */
    public static ImportStatus getImportStatus() {
        PreferenceHelper prefHelper = PreferenceHelper.getInstance();
        String lastImportError = prefHelper.getPreference(LAST_IMPORT_ERROR_PREFERENCE_KEY);
        boolean importOngoing =
                Boolean.parseBoolean(prefHelper.getPreference(IMPORT_ONGOING_PREFERENCE_KEY));

        return new ImportStatus(
                lastImportError == null
                        ? ImportStatus.DATA_IMPORT_ERROR_NONE
                        : Integer.parseInt(lastImportError),
                importOngoing);
    }
}
