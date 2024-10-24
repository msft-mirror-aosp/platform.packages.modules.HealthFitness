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

import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_PERMISSION_STATS;

import android.content.Context;
import android.health.HealthFitnessStatsLog;
import android.os.UserHandle;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.util.List;
import java.util.Objects;

/**
 * Logs Health Connect usage stats.
 *
 * @hide
 */
final class UsageStatsLogger {

    /** Write Health Connect usage stats to statsd. */
    static void log(
            Context context,
            UserHandle userHandle,
            PreferenceHelper preferenceHelper,
            AccessLogsHelper accessLogsHelper,
            HealthConnectPermissionHelper healthConnectPermissionHelper) {
        Objects.requireNonNull(userHandle);
        Objects.requireNonNull(context);

        UsageStatsCollector usageStatsCollector =
                new UsageStatsCollector(context, userHandle, preferenceHelper, accessLogsHelper);
        usageStatsCollector.upsertLastAccessLogTimeStamp();
        List<String> connectedApps = usageStatsCollector.getPackagesHoldingHealthPermissions();
        int numberOfConnectedApps = connectedApps.size();
        int numberOfAvailableApps =
                usageStatsCollector.getNumberOfAppsCompatibleWithHealthConnect();
        boolean isUserMonthlyActive = usageStatsCollector.isUserMonthlyActive();

        // If this condition is true then the user does not uses HC and we should not collect data.
        // This will reduce the load on logging service otherwise we will get daily data from
        // billions of Android devices.
        if (numberOfConnectedApps == 0 && numberOfAvailableApps == 0 && !isUserMonthlyActive) {
            return;
        }

        logExportImportStats(usageStatsCollector);
        logPermissionStats(healthConnectPermissionHelper, userHandle, connectedApps);

        HealthFitnessStatsLog.write(
                HealthFitnessStatsLog.HEALTH_CONNECT_USAGE_STATS,
                numberOfConnectedApps,
                numberOfAvailableApps,
                isUserMonthlyActive);
    }

    static void logExportImportStats(UsageStatsCollector usageStatsCollector) {
        int exportFrequency = usageStatsCollector.getExportFrequency();
        HealthFitnessStatsLog.write(
                HealthFitnessStatsLog.HEALTH_CONNECT_EXPORT_IMPORT_STATS_REPORTED, exportFrequency);
    }

    static void logPermissionStats(
            HealthConnectPermissionHelper healthConnectPermissionHelper,
            UserHandle userHandle,
            List<String> connectedApps) {

        if (!Flags.permissionMetrics()) {
            return;
        }

        for (String connectedApp : connectedApps) {
            List<String> grantedPermissions =
                    healthConnectPermissionHelper.getGrantedHealthPermissions(
                            connectedApp, userHandle);

            // This is done to remove the common prefix android.permission.health from all
            // permissions
            String[] grantedPermissionsShortened = new String[grantedPermissions.size()];
            for (int permissionIndex = 0;
                    permissionIndex < grantedPermissions.size();
                    permissionIndex++) {
                String grantedPermission = grantedPermissions.get(permissionIndex);
                grantedPermissionsShortened[permissionIndex] =
                        grantedPermission.substring(grantedPermission.lastIndexOf('.') + 1);
            }

            HealthFitnessStatsLog.write(
                    HEALTH_CONNECT_PERMISSION_STATS, connectedApp, grantedPermissionsShortened);
        }
    }
}
