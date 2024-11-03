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

package com.android.server.healthconnect.permission;

import static android.content.pm.PackageManager.GET_PERMISSIONS;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class with PackageInfo-related methods for {@link FirstGrantTimeManager}
 *
 * @hide
 */
public final class PackageInfoUtils {
    private static final String TAG = "HCPackageInfoUtils";

    public PackageInfoUtils() {}

    Map<String, Set<Integer>> collectSharedUserNameToUidsMappingForUser(
            List<PackageInfo> packageInfos) {
        Map<String, Set<Integer>> sharedUserNameToUids = new ArrayMap<>();
        for (PackageInfo info : packageInfos) {
            if (info.sharedUserId != null) {
                if (sharedUserNameToUids.get(info.sharedUserId) == null) {
                    sharedUserNameToUids.put(info.sharedUserId, new ArraySet<>());
                }
                sharedUserNameToUids.get(info.sharedUserId).add(info.applicationInfo.uid);
            }
        }
        return sharedUserNameToUids;
    }

    public List<PackageInfo> getPackagesHoldingHealthPermissions(UserHandle user, Context context) {
        // TODO(b/260707328): replace with getPackagesHoldingPermissions
        List<PackageInfo> allInfos =
                getPackageManagerAsUser(context, user)
                        .getInstalledPackages(PackageManager.PackageInfoFlags.of(GET_PERMISSIONS));
        List<PackageInfo> healthAppsInfos = new ArrayList<>();

        for (PackageInfo info : allInfos) {
            if (anyRequestedHealthPermissionGranted(context, info)) {
                healthAppsInfos.add(info);
            }
        }
        return healthAppsInfos;
    }

    @SuppressWarnings("NullAway")
    // TODO(b/317029272): fix this suppression
    boolean hasGrantedHealthPermissions(String[] packageNames, UserHandle user, Context context) {
        for (String packageName : packageNames) {
            PackageInfo info = getPackageInfoWithPermissionsAsUser(packageName, user, context);
            if (anyRequestedHealthPermissionGranted(context, info)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    String[] getPackagesForUid(int packageUid, UserHandle user, Context context) {
        return getPackageManagerAsUser(context, user).getPackagesForUid(packageUid);
    }

    /**
     * Checks if the given package had any read/write permissions to Health Connect.
     *
     * @param context Context
     * @param packageInfo Package to check
     * @return If the given package is connected to Health Connect.
     */
    public static boolean anyRequestedHealthPermissionGranted(
            @Nullable Context context, @Nullable PackageInfo packageInfo) {
        if (context == null || packageInfo == null || packageInfo.requestedPermissions == null) {
            Log.w(TAG, "Can't extract requested permissions from the package info.");
            return false;
        }

        for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
            String currPerm = packageInfo.requestedPermissions[i];
            if (HealthConnectManager.isHealthPermission(context, currPerm)
                    && ((packageInfo.requestedPermissionsFlags[i]
                                    & PackageInfo.REQUESTED_PERMISSION_GRANTED)
                            != 0)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public PackageInfo getPackageInfoWithPermissionsAsUser(
            String packageName, UserHandle user, Context context) {
        try {
            return getPackageManagerAsUser(context, user)
                    .getPackageInfo(
                            packageName, PackageManager.PackageInfoFlags.of(GET_PERMISSIONS));
        } catch (PackageManager.NameNotFoundException e) {
            // App not found.
            Log.e(TAG, "NameNotFoundException for " + packageName);
            return null;
        }
    }

    @Nullable
    String getSharedUserNameFromUid(int uid, Context context) {
        UserHandle user = UserHandle.getUserHandleForUid(uid);
        PackageManager packageManager = getPackageManagerAsUser(context, user);
        String[] packages = packageManager.getPackagesForUid(uid);
        if (packages == null || packages.length == 0) {
            Log.e(TAG, "Can't get package names for UID: " + uid);
            return null;
        }
        try {
            PackageInfo info =
                    packageManager.getPackageInfo(
                            packages[0], PackageManager.PackageInfoFlags.of(0));
            return info.sharedUserId;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Package " + packages[0] + " not found.");
            return null;
        }
    }

    Optional<String> getPackageNameForUid(Context context, int uid) {
        String[] packages = getPackageNamesForUid(context, uid);
        if (packages.length != 1) {
            Log.w(TAG, "Can't get one package name for UID: " + uid);
            return Optional.empty();
        }
        return Optional.of(packages[0]);
    }

    String[] getPackageNamesForUid(Context context, int uid) {
        PackageManager packageManager =
                getPackageManagerAsUser(context, UserHandle.getUserHandleForUid(uid));
        if (packageManager == null) {
            return new String[] {};
        }
        String[] packages = packageManager.getPackagesForUid(uid);
        return packages != null ? packages : new String[] {};
    }

    @Nullable
    Integer getPackageUid(String packageName, UserHandle user, Context context) {
        Integer uid = null;
        try {
            uid =
                    getPackageManagerAsUser(context, user)
                            .getPackageUid(
                                    packageName,
                                    PackageManager.PackageInfoFlags.of(/* flags= */ 0));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "NameNotFound exception for " + packageName);
        }
        return uid;
    }

    /**
     * Returns the list of health permissions granted to a given package name. It does not check if
     * the given package name is valid. TODO(b/368072570): Make this function non-static once DI
     * flag is removed.
     */
    public static List<String> getGrantedHealthPermissions(
            Context context, String packageName, UserHandle user) {
        // Ideally we could've used the Map in the state for this class. However, this function
        // needs
        // to be static due to complications around passing Context to the constructor of this
        // class.
        PackageInfo packageInfo =
                getPackageInfoUnchecked(
                        packageName,
                        user,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS),
                        context);

        return getGrantedHealthPermissions(context, packageInfo);
    }

    /** Returns the list of health permissions granted to the given {@link PackageInfo}. */
    public static List<String> getGrantedHealthPermissions(
            Context context, PackageInfo packageInfo) {
        Set<String> healthPermissions = HealthConnectManager.getHealthPermissions(context);

        if (packageInfo.requestedPermissions == null) {
            return List.of();
        }

        List<String> grantedHealthPerms = new ArrayList<>(packageInfo.requestedPermissions.length);
        for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
            String currPerm = packageInfo.requestedPermissions[i];
            if (packageInfo.requestedPermissionsFlags != null
                    && healthPermissions.contains(currPerm)
                    && ((packageInfo.requestedPermissionsFlags[i]
                                    & PackageInfo.REQUESTED_PERMISSION_GRANTED)
                            != 0)) {
                grantedHealthPerms.add(currPerm);
            }
        }
        return grantedHealthPerms;
    }

    /**
     * Returns the list of {@link PackageInfo} for a given package. It does not check if the given
     * package name is valid.
     */
    public static PackageInfo getPackageInfoUnchecked(
            String packageName,
            UserHandle user,
            PackageManager.PackageInfoFlags flags,
            Context context) {
        try {
            PackageManager packageManager =
                    context.createContextAsUser(user, /* flags= */ 0).getPackageManager();

            return packageManager.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalArgumentException("invalid package", e);
        }
    }

    private PackageManager getPackageManagerAsUser(Context context, UserHandle user) {
        return context.createContextAsUser(user, /* flags */ 0).getPackageManager();
    }
}
