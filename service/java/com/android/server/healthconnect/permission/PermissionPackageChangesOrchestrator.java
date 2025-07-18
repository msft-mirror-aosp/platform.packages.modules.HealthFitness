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

import android.annotation.Nullable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.health.connect.Constants;
import android.net.Uri;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.Slog;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.BackgroundThread;
import com.android.server.healthconnect.HealthConnectThreadScheduler;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

/**
 * Tracks packages changes (install, update, uninstall, changed) and calls permission classes to
 * sync with package states.
 *
 * @hide
 */
public class PermissionPackageChangesOrchestrator extends BroadcastReceiver {
    private static final String TAG = "HealthPackageChangesMonitor";
    static final IntentFilter sPackageFilter = buildPackageChangeFilter();
    private final HealthPermissionIntentAppsTracker mPermissionIntentTracker;
    private final FirstGrantTimeManager mFirstGrantTimeManager;
    private final HealthConnectPermissionHelper mPermissionHelper;
    private UserHandle mCurrentForegroundUser;
    private final HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;

    public PermissionPackageChangesOrchestrator(
            HealthPermissionIntentAppsTracker permissionIntentTracker,
            FirstGrantTimeManager grantTimeManager,
            HealthConnectPermissionHelper permissionHelper,
            UserHandle userHandle,
            HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper) {
        mPermissionIntentTracker = permissionIntentTracker;
        mFirstGrantTimeManager = grantTimeManager;
        mPermissionHelper = permissionHelper;
        mCurrentForegroundUser = userHandle;
        mHealthDataCategoryPriorityHelper = healthDataCategoryPriorityHelper;
    }

    /**
     * Register broadcast receiver to track package changes.
     *
     * @hide
     */
    public void registerBroadcastReceiver(Context context) {
        context.registerReceiverForAllUsers(
                this, sPackageFilter, null, BackgroundThread.getHandler());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String packageName = getPackageName(intent);
        UserHandle userHandle = getUserHandle(intent);
        if (packageName == null || userHandle == null) {
            Log.w(TAG, "onReceive package change, can't extract info from the input intent");
            return;
        }
        if (Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "onReceive package change for "
                            + packageName
                            + " user: "
                            + userHandle
                            + " action: "
                            + intent.getAction());
        }

        boolean isPackageRemoved =
                intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)
                        && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        // This call also has a (unintended?) positive side-effect of removing the package from
        // the intent tracker, if the package was removed. Keep calling this even if
        // isPackageRemoved is true.
        boolean removePermissions;
        if (Flags.permissionTrackerFixMappingInit()) {
            removePermissions =
                    !mPermissionIntentTracker.updateAndGetSupportsPackageUsageIntent(
                            packageName, userHandle);
        } else {
            removePermissions =
                    mPermissionIntentTracker.updateStateAndGetIfIntentWasRemoved(
                            packageName, userHandle);
        }

        // Wear Apps don't require the permissions intent. No need to revoke the permissions if it
        // is missing.
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH) &&
            Flags.replaceBodySensorPermissionEnabled()) {
            removePermissions = false;
        }
        // If the package was removed, we reset grant time. If the package is present but the health
        // intent support removed we revoke all health permissions and also reset grant time
        // (is done via onPermissionChanged callback)
        if (isPackageRemoved) {
            final int uid = intent.getIntExtra(Intent.EXTRA_UID, /* default value= */ -1);
            mFirstGrantTimeManager.onPackageRemoved(packageName, uid, userHandle);
            // Call remove app from Priority list only if userHandle equals the
            // current foreground user and current foreground user is in unlocked state
            UserManager userManager = context.getSystemService(UserManager.class);
            if (userHandle.equals(mCurrentForegroundUser)
                    && userManager.isUserUnlocked(userHandle)) {
                HealthConnectThreadScheduler.scheduleInternalTask(
                        () ->
                                mHealthDataCategoryPriorityHelper
                                        .maybeRemoveAppWithoutWritePermissionsFromPriorityList(
                                                packageName));
            }
        } else if (removePermissions) {
            // Revoke all health permissions as we don't grant health permissions if permissions
            // usage intent is not supported.
            if (Constants.DEBUG) {
                Slog.d(
                        TAG,
                        "Revoking all health permissions of "
                                + packageName
                                + " for user: "
                                + userHandle);
            }

            try {
                mPermissionHelper.revokeAllHealthPermissions(
                        packageName,
                        "Health permissions usage activity has been removed.",
                        userHandle);
            } catch (IllegalArgumentException ex) {
                // Catch IllegalArgumentException to fix a crash (b/24679220) due to race condition
                // in case this `revokeAllHealthPermissions()` method is called right after the
                // client app is uninstalled.
                Slog.e(TAG, "Revoking all health permissions failed", ex);
            }
        }
    }

    /** Sets the current foreground user handle. */
    public void setUserHandle(UserHandle userHandle) {
        mCurrentForegroundUser = userHandle;
    }

    private static IntentFilter buildPackageChangeFilter() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme(/* scheme= */ "package");
        return filter;
    }

    @Nullable
    private String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        return uri != null ? uri.getSchemeSpecificPart() : null;
    }

    @Nullable
    private UserHandle getUserHandle(Intent intent) {
        final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
        if (uid >= 0) {
            return UserHandle.getUserHandleForUid(uid);
        } else {
            Log.w(TAG, "UID extra is missing from intent");
            return null;
        }
    }
}
