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

import static android.content.pm.PackageManager.GET_PERMISSIONS;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.os.UserHandle;

import com.android.server.healthconnect.permission.PackageInfoUtils;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects Health Connect usage stats.
 *
 * @hide
 */
final class UsageStatsCollector {
    private static final String USER_MOST_RECENT_ACCESS_LOG_TIME =
            "USER_MOST_RECENT_ACCESS_LOG_TIME";
    private static final String EXPORT_PERIOD_PREFERENCE_KEY = "export_period_key";
    private static final int NUMBER_OF_DAYS_FOR_USER_TO_BE_MONTHLY_ACTIVE = 30;
    private final Context mContext;
    private final Map<String, PackageInfo> mPackageNameToPackageInfo = new HashMap<>();

    private final PreferenceHelper mPreferenceHelper;
    private final AccessLogsHelper mAccessLogsHelper;

    UsageStatsCollector(
            Context context,
            UserHandle userHandle,
            PreferenceHelper preferenceHelper,
            AccessLogsHelper accessLogsHelper) {
        mContext = context;
        mPreferenceHelper = preferenceHelper;
        mAccessLogsHelper = accessLogsHelper;
        List<PackageInfo> allPackagesInstalledForUser =
                context.createContextAsUser(userHandle, /* flag= */ 0)
                        .getPackageManager()
                        .getInstalledPackages(PackageManager.PackageInfoFlags.of(GET_PERMISSIONS));
        for (PackageInfo packageInfo : allPackagesInstalledForUser) {
            mPackageNameToPackageInfo.put(packageInfo.packageName, packageInfo);
        }
    }

    /**
     * Returns the number of apps that can be connected to Health Connect.
     *
     * <p>The apps not necessarily have permissions to read/write data. It just mentions permission
     * in the manifest i.e. if not connected yet, it can be connected to Health Connect.
     *
     * @return Number of apps that can be connected (not necessarily connected) to Health Connect
     */
    int getNumberOfAppsCompatibleWithHealthConnect() {
        int numberOfAppsGrantedHealthPermissions = 0;
        for (PackageInfo info : mPackageNameToPackageInfo.values()) {
            if (hasRequestedHealthPermission(info)) {
                numberOfAppsGrantedHealthPermissions++;
            }
        }
        return numberOfAppsGrantedHealthPermissions;
    }

    /**
     * Returns the list of apps that are connected to Health Connect.
     *
     * @return Map of package name to permissions granted for apps that are connected (have
     *     read/write) to Health Connect
     */
    Map<String, List<String>> getPackagesHoldingHealthPermissions() {
        Map<String, List<String>> packageNameToPermissionsGranted = new HashMap<>();
        for (PackageInfo info : mPackageNameToPackageInfo.values()) {
            List<String> grantedHealthPermissions =
                    PackageInfoUtils.getGrantedHealthPermissions(mContext, info);
            if (!grantedHealthPermissions.isEmpty()) {
                packageNameToPermissionsGranted.put(info.packageName, grantedHealthPermissions);
            }
        }
        return packageNameToPermissionsGranted;
    }

    /**
     * Returns the configured export frequency of the user.
     *
     * @return Export frequency of the current user.
     */
    int getExportFrequency() {
        String result = mPreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY);
        if (result == null) {
            return 0;
        }
        return Integer.parseInt(result);
    }

    boolean isUserMonthlyActive() {
        String latestAccessLogTimeStampString =
                mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME);

        // Return false if preference is empty and make sure latest access was within past
        // 30 days.
        return latestAccessLogTimeStampString != null
                && Instant.now()
                                .minus(
                                        NUMBER_OF_DAYS_FOR_USER_TO_BE_MONTHLY_ACTIVE,
                                        ChronoUnit.DAYS)
                                .toEpochMilli()
                        <= Long.parseLong(latestAccessLogTimeStampString);
    }

    void upsertLastAccessLogTimeStamp() {

        long latestAccessLogTimeStamp =
                mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp();

        // Access logs are only stored for 7 days, therefore only update this value if there is an
        // access log. Last access timestamp can be before 7 days and might already exist in
        // preference and in that case we should not overwrite the existing value.
        if (latestAccessLogTimeStamp != Long.MIN_VALUE) {
            mPreferenceHelper.insertOrReplacePreference(
                    USER_MOST_RECENT_ACCESS_LOG_TIME, String.valueOf(latestAccessLogTimeStamp));
        }
    }

    private boolean hasRequestedHealthPermission(PackageInfo packageInfo) {
        if (packageInfo == null || packageInfo.requestedPermissions == null) {
            return false;
        }

        for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
            if (HealthConnectManager.isHealthPermission(
                    mContext, packageInfo.requestedPermissions[i])) {
                return true;
            }
        }
        return false;
    }
}
