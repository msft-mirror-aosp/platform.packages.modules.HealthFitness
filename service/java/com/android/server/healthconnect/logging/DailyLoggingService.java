/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.healthconnect.logging;

import android.content.Context;
import android.os.UserHandle;
import android.util.Slog;

import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.util.Objects;

/**
 * Class to log Health Connect metrics logged every 24hrs.
 *
 * @hide
 */
public class DailyLoggingService {

    private static final String HEALTH_CONNECT_DAILY_LOGGING_SERVICE =
            "HealthConnectDailyLoggingService";

    /** Log daily metrics. */
    public static void logDailyMetrics(
            Context context,
            UserHandle userHandle,
            PreferenceHelper preferenceHelper,
            AccessLogsHelper accessLogsHelper,
            TransactionManager transactionManager,
            HealthConnectPermissionHelper healthConnectPermissionHelper) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(userHandle);

        logDatabaseStats(context, transactionManager);
        logUsageStats(
                context,
                userHandle,
                preferenceHelper,
                accessLogsHelper,
                healthConnectPermissionHelper);
    }

    private static void logDatabaseStats(Context context, TransactionManager transactionManager) {
        try {
            DatabaseStatsLogger.log(context, transactionManager);
        } catch (Exception exception) {
            Slog.e(HEALTH_CONNECT_DAILY_LOGGING_SERVICE, "Failed to log database stats", exception);
        }
    }

    private static void logUsageStats(
            Context context,
            UserHandle userHandle,
            PreferenceHelper preferenceHelper,
            AccessLogsHelper accessLogsHelper,
            HealthConnectPermissionHelper healthConnectPermissionHelper) {
        try {
            UsageStatsLogger.log(
                    context,
                    userHandle,
                    preferenceHelper,
                    accessLogsHelper,
                    healthConnectPermissionHelper);
        } catch (Exception exception) {
            Slog.e(HEALTH_CONNECT_DAILY_LOGGING_SERVICE, "Failed to log usage stats", exception);
        }
    }
}
