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

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.os.Binder;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

import java.time.Instant;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A handler for HealthConnect permission-related logic.
 *
 * @hide
 */
public final class HealthConnectPermissionHelper {
    private static final Period GRANT_TIME_TO_START_ACCESS_DATE_PERIOD = Period.ofDays(30);
    private static final String UNKNOWN_REASON = "Unknown Reason";

    private static final int MASK_PERMISSION_FLAGS =
            PackageManager.FLAG_PERMISSION_USER_SET
                    | PackageManager.FLAG_PERMISSION_USER_FIXED
                    | PackageManager.FLAG_PERMISSION_AUTO_REVOKED;

    private final Context mContext;
    private final PackageManager mPackageManager;
    private final HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;
    private final FirstGrantTimeManager mFirstGrantTimeManager;
    private final HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    private final AppInfoHelper mAppInfoHelper;
    private final HealthConnectMappings mHealthConnectMappings;

    /**
     * Constructs a {@link HealthConnectPermissionHelper}.
     *
     * @param context the service context.
     * @param packageManager a {@link PackageManager} instance.
     * @param permissionIntentTracker a {@link
     *     com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker} instance
     *     that tracks apps allowed to request health permissions.
     */
    public HealthConnectPermissionHelper(
            Context context,
            PackageManager packageManager,
            HealthPermissionIntentAppsTracker permissionIntentTracker,
            FirstGrantTimeManager firstGrantTimeManager,
            HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper,
            AppInfoHelper appInfoHelper,
            HealthConnectMappings healthConnectMappings) {
        mContext = context;
        mPackageManager = packageManager;
        mPermissionIntentAppsTracker = permissionIntentTracker;
        mFirstGrantTimeManager = firstGrantTimeManager;
        mHealthDataCategoryPriorityHelper = healthDataCategoryPriorityHelper;
        mAppInfoHelper = appInfoHelper;
        mHealthConnectMappings = healthConnectMappings;
    }

    /**
     * See {@link HealthConnectManager#grantHealthPermission}.
     *
     * <p>NOTE: Once permission grant is successful, the package name will also be appended to the
     * end of the priority list corresponding to {@code permissionName}'s health permission
     * category.
     */
    public void grantHealthPermission(String packageName, String permissionName, UserHandle user) {
        enforceManageHealthPermissions(/* message= */ "grantHealthPermission");
        enforceValidHealthPermission(permissionName);
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        enforceSupportPermissionsUsageIntent(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            mPackageManager.grantRuntimePermission(packageName, permissionName, checkedUser);
            mPackageManager.updatePermissionFlags(
                    permissionName,
                    packageName,
                    MASK_PERMISSION_FLAGS,
                    PackageManager.FLAG_PERMISSION_USER_SET,
                    checkedUser);
            mAppInfoHelper.getOrInsertAppInfoId(packageName);
            addToPriorityListIfRequired(packageName, permissionName, user);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#revokeHealthPermission}. */
    public void revokeHealthPermission(
            String packageName, String permissionName, @Nullable String reason, UserHandle user) {
        enforceManageHealthPermissions(/* message= */ "revokeHealthPermission");
        enforceValidHealthPermission(permissionName);
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            boolean isAlreadyDenied =
                    mPackageManager.checkPermission(permissionName, packageName)
                            == PackageManager.PERMISSION_DENIED;
            int permissionFlags =
                    mPackageManager.getPermissionFlags(permissionName, packageName, checkedUser);
            if (!isAlreadyDenied) {
                revokeRuntimePermission(packageName, checkedUser, permissionName, reason);
            }
            if (isAlreadyDenied
                    && (permissionFlags & PackageManager.FLAG_PERMISSION_USER_SET) != 0) {
                permissionFlags = permissionFlags | PackageManager.FLAG_PERMISSION_USER_FIXED;
            } else {
                permissionFlags = permissionFlags | PackageManager.FLAG_PERMISSION_USER_SET;
            }
            permissionFlags = permissionFlags & ~PackageManager.FLAG_PERMISSION_AUTO_REVOKED;
            mPackageManager.updatePermissionFlags(
                    permissionName,
                    packageName,
                    MASK_PERMISSION_FLAGS,
                    permissionFlags,
                    checkedUser);

            removeFromPriorityListIfRequired(packageName, permissionName, user);

        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#revokeAllHealthPermissions}. */
    public void revokeAllHealthPermissions(
            String packageName, @Nullable String reason, UserHandle user) {
        enforceManageHealthPermissions(/* message= */ "revokeAllHealthPermissions");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            revokeAllHealthPermissionsUnchecked(packageName, checkedUser, reason);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#getGrantedHealthPermissions}. */
    public List<String> getGrantedHealthPermissions(String packageName, UserHandle user) {
        enforceManageHealthPermissions(/* message= */ "getGrantedHealthPermissions");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            return PackageInfoUtils.getGrantedHealthPermissions(mContext, packageName, checkedUser);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#getHealthPermissionsFlags(String, List)}. */
    public Map<String, Integer> getHealthPermissionsFlags(
            String packageName, UserHandle user, List<String> permissions) {
        enforceManageHealthPermissions(/* message= */ "getHealthPermissionsFlags");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            return getHealthPermissionsFlagsUnchecked(packageName, checkedUser, permissions);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** See {@link HealthConnectManager#setHealthPermissionsUserFixedFlagValue(String, List)}. */
    public void setHealthPermissionsUserFixedFlagValue(
            String packageName, UserHandle user, List<String> permissions, boolean value) {
        enforceManageHealthPermissions(/* message= */ "setHealthPermissionsUserFixedFlagValue");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);
        final long token = Binder.clearCallingIdentity();
        try {
            setHealthPermissionsUserFixedFlagValueUnchecked(
                    packageName, checkedUser, permissions, value);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /**
     * Returns {@code true} if there is at least one granted permission for the provided {@code
     * packageName}, {@code false} otherwise.
     */
    public boolean hasGrantedHealthPermissions(String packageName, UserHandle user) {
        return !getGrantedHealthPermissions(packageName, user).isEmpty();
    }

    /**
     * Returns the date from which an app can read / write health data. See {@link
     * HealthConnectManager#getHealthDataHistoricalAccessStartDate}
     */
    public Optional<Instant> getHealthDataStartDateAccess(String packageName, UserHandle user)
            throws IllegalArgumentException {
        enforceManageHealthPermissions(/* message= */ "getHealthDataStartDateAccess");
        UserHandle checkedUser = UserHandle.of(handleIncomingUser(user.getIdentifier()));
        enforceValidPackage(packageName, checkedUser);

        return mFirstGrantTimeManager
                .getFirstGrantTime(packageName, checkedUser)
                .map(grantTime -> grantTime.minus(GRANT_TIME_TO_START_ACCESS_DATE_PERIOD))
                .or(Optional::empty);
    }

    /**
     * Same as {@link #getHealthDataStartDateAccess(String, UserHandle)} except this method also
     * throws {@link IllegalAccessException} if health permission is in an incorrect state where
     * first grant time can't be fetched.
     */
    public Instant getHealthDataStartDateAccessOrThrow(String packageName, UserHandle user) {
        Optional<Instant> startDateAccess = getHealthDataStartDateAccess(packageName, user);
        if (startDateAccess.isEmpty()) {
            throwExceptionIncorrectPermissionState();
        }
        return startDateAccess.get();
    }

    private void throwExceptionIncorrectPermissionState() {
        throw new IllegalStateException(
                "Incorrect health permission state, likely"
                        + " because the calling application's manifest does not specify handling "
                        + Intent.ACTION_VIEW_PERMISSION_USAGE
                        + " with "
                        + HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS);
    }

    private void addToPriorityListIfRequired(
            String packageName, String permissionName, UserHandle user) {
        if (mHealthConnectMappings.isWritePermission(permissionName)) {
            mHealthDataCategoryPriorityHelper.appendToPriorityList(
                    packageName,
                    mHealthConnectMappings.getHealthDataCategoryForWritePermission(permissionName),
                    user);
        }
    }

    private void removeFromPriorityListIfRequired(
            String packageName, String permissionName, UserHandle user) {
        if (mHealthConnectMappings.isWritePermission(permissionName)) {
            mHealthDataCategoryPriorityHelper.maybeRemoveAppFromPriorityList(
                    packageName,
                    mHealthConnectMappings.getHealthDataCategoryForWritePermission(permissionName),
                    user);
        }
    }

    private Map<String, Integer> getHealthPermissionsFlagsUnchecked(
            String packageName, UserHandle user, List<String> permissions) {
        enforceValidHealthPermissions(packageName, user, permissions);

        Map<String, Integer> result = new ArrayMap<>();

        for (String permission : permissions) {
            result.put(
                    permission, mPackageManager.getPermissionFlags(permission, packageName, user));
        }

        return result;
    }

    private void setHealthPermissionsUserFixedFlagValueUnchecked(
            String packageName, UserHandle user, List<String> permissions, boolean value) {
        enforceValidHealthPermissions(packageName, user, permissions);

        int flagMask = PackageManager.FLAG_PERMISSION_USER_FIXED;
        int flagValues = value ? PackageManager.FLAG_PERMISSION_USER_FIXED : 0;

        for (String permission : permissions) {
            mPackageManager.updatePermissionFlags(
                    permission, packageName, flagMask, flagValues, user);
        }
    }

    private void revokeAllHealthPermissionsUnchecked(
            String packageName, UserHandle user, @Nullable String reason) {
        List<String> grantedHealthPermissions =
                PackageInfoUtils.getGrantedHealthPermissions(mContext, packageName, user);
        for (String perm : grantedHealthPermissions) {
            revokeRuntimePermission(packageName, user, perm, reason);
            mPackageManager.updatePermissionFlags(
                    perm,
                    packageName,
                    MASK_PERMISSION_FLAGS,
                    PackageManager.FLAG_PERMISSION_USER_SET,
                    user);
            removeFromPriorityListIfRequired(packageName, perm, user);
        }
    }

    private void revokeRuntimePermission(
            String packageName, UserHandle user, String permission, @Nullable String reason) {
        mPackageManager.revokeRuntimePermission(
                packageName, permission, user, reason == null ? UNKNOWN_REASON : reason);
    }

    private void enforceValidHealthPermission(String permissionName) {
        if (!HealthConnectManager.getHealthPermissions(mContext).contains(permissionName)) {
            throw new IllegalArgumentException("invalid health permission");
        }
    }

    private void enforceValidPackage(String packageName, UserHandle user) {
        PackageInfoUtils.getPackageInfoUnchecked(
                packageName, user, PackageManager.PackageInfoFlags.of(0), mContext);
    }

    private void enforceManageHealthPermissions(String message) {
        mContext.enforceCallingOrSelfPermission(
                HealthPermissions.MANAGE_HEALTH_PERMISSIONS, message);
    }

    private void enforceSupportPermissionsUsageIntent(String packageName, UserHandle userHandle) {
        // Wear apps are not currently required to support the permission intent.
        if (Flags.replaceBodySensorPermissionEnabled()
                && mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return;
        }

        if (!mPermissionIntentAppsTracker.supportsPermissionUsageIntent(packageName, userHandle)) {
            throw new SecurityException(
                    "Package "
                            + packageName
                            + " for "
                            + userHandle.toString()
                            + " doesn't support health permissions usage intent.");
        }
    }

    /**
     * Checks input user id and converts it to positive id if needed, returns converted user id.
     *
     * @throws java.lang.SecurityException if the caller is affecting different users without
     *     holding the {@link INTERACT_ACROSS_USERS_FULL} permission.
     */
    private int handleIncomingUser(int userId) {
        int callingUserId = UserHandle.getUserHandleForUid(Binder.getCallingUid()).getIdentifier();
        if (userId == callingUserId) {
            return userId;
        }

        boolean canInteractAcrossUsersFull =
                mContext.checkCallingOrSelfPermission(INTERACT_ACROSS_USERS_FULL)
                        == PERMISSION_GRANTED;
        if (canInteractAcrossUsersFull) {
            // If the UserHandle.CURRENT has been passed (negative value),
            // convert it to positive userId.
            if (userId == UserHandle.CURRENT.getIdentifier()) {
                return ActivityManager.getCurrentUser();
            }
            return userId;
        }

        throw new SecurityException(
                "Permission denied. Need to run as either the calling user id ("
                        + callingUserId
                        + "), or with "
                        + INTERACT_ACROSS_USERS_FULL
                        + " permission");
    }

    private void enforceValidHealthPermissions(
            String packageName, UserHandle user, List<String> permissions) {
        PackageInfo packageInfo =
                PackageInfoUtils.getPackageInfoUnchecked(
                        packageName,
                        user,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS),
                        mContext);

        Set<String> requestedPermissions = new ArraySet<>(packageInfo.requestedPermissions);

        for (String permission : permissions) {
            if (!requestedPermissions.contains(permission)) {
                throw new IllegalArgumentException(
                        "undeclared permission " + permission + " for package " + packageName);
            }

            enforceValidHealthPermission(permission);
        }
    }
}
