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
import static android.health.connect.Constants.DEFAULT_INT;

import static com.android.healthfitness.flags.Flags.personalHealthRecordTelemetry;

import android.content.Context;
import android.health.HealthFitnessStatsLog;
import android.os.UserHandle;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.utils.TimeSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Logs Health Connect usage stats.
 *
 * @hide
 */
final class UsageStatsLogger {
    /**
     * A client is considered as "monthly active" by PHR if it has made any read medical resources
     * API call within this number of days.
     */
    private static final long PHR_MONTHLY_ACTIVE_USER_DURATION = 30; // 30 days

    /** Write Health Connect usage stats to statsd. */
    static void log(
            Context context,
            UserHandle userHandle,
            PreferenceHelper preferenceHelper,
            AccessLogsHelper accessLogsHelper,
            MedicalDataSourceHelper medicalDataSourceHelper,
            MedicalResourceHelper medicalResourceHelper,
            TimeSource timeSource) {
        UsageStatsCollector usageStatsCollector =
                new UsageStatsCollector(context, userHandle, preferenceHelper, accessLogsHelper);
        usageStatsCollector.upsertLastAccessLogTimeStamp();
        Map<String, List<String>> packageNameToPermissionsGranted =
                usageStatsCollector.getPackagesHoldingHealthPermissions();
        int numberOfConnectedApps = packageNameToPermissionsGranted.size();
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
        logPermissionStats(context, packageNameToPermissionsGranted);
        logPhrStats(medicalDataSourceHelper, medicalResourceHelper, preferenceHelper, timeSource);

        HealthFitnessStatsLog.write(
                HealthFitnessStatsLog.HEALTH_CONNECT_USAGE_STATS,
                numberOfConnectedApps,
                numberOfAvailableApps,
                isUserMonthlyActive);
    }

    private static void logPhrStats(
            MedicalDataSourceHelper medicalDataSourceHelper,
            MedicalResourceHelper medicalResourceHelper,
            PreferenceHelper preferenceHelper,
            TimeSource timeSource) {
        if (!personalHealthRecordTelemetry()) {
            return;
        }

        int medicalDataSourcesCount = medicalDataSourceHelper.getMedicalDataSourcesCount();
        int medicalResourcesCount = medicalResourceHelper.getMedicalResourcesCount();
        HealthFitnessStatsLog.write(
                HealthFitnessStatsLog.HEALTH_CONNECT_PHR_USAGE_STATS,
                medicalDataSourcesCount,
                medicalResourcesCount,
                isPhrMonthlyActiveUser(preferenceHelper, timeSource),
                DEFAULT_INT);
    }

    private static boolean isPhrMonthlyActiveUser(
            PreferenceHelper preferenceHelper, TimeSource timeSource) {
        Instant lastReadMedicalResourcesApiTimeStamp =
                preferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp();
        if (lastReadMedicalResourcesApiTimeStamp == null) {
            return false;
        }
        return timeSource
                .getInstantNow()
                .minus(PHR_MONTHLY_ACTIVE_USER_DURATION, ChronoUnit.DAYS)
                .isBefore(lastReadMedicalResourcesApiTimeStamp);
    }

    static void logExportImportStats(UsageStatsCollector usageStatsCollector) {
        int exportFrequency = usageStatsCollector.getExportFrequency();
        HealthFitnessStatsLog.write(
                HealthFitnessStatsLog.HEALTH_CONNECT_EXPORT_IMPORT_STATS_REPORTED, exportFrequency);
    }

    static void logPermissionStats(
            Context context, Map<String, List<String>> packageNameToPermissionsGranted) {

        if (!Flags.permissionMetrics()) {
            return;
        }

        for (Map.Entry<String, List<String>> connectedAppToPermissionsGranted :
                packageNameToPermissionsGranted.entrySet()) {

            List<String> grantedPermissions = connectedAppToPermissionsGranted.getValue();

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
                    HEALTH_CONNECT_PERMISSION_STATS,
                    connectedAppToPermissionsGranted.getKey(),
                    grantedPermissionsShortened);
        }
    }
}
