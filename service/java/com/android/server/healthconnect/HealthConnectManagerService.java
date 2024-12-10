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

package com.android.server.healthconnect;

import android.annotation.Nullable;
import android.content.Context;
import android.health.connect.ratelimiter.RateLimiter;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;

import com.android.healthfitness.flags.Flags;
import com.android.server.SystemService;
import com.android.server.healthconnect.exportimport.ExportImportJobs;
import com.android.server.healthconnect.exportimport.ExportManager;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.migration.MigrationBroadcastScheduler;
import com.android.server.healthconnect.migration.MigrationStateManager;
import com.android.server.healthconnect.migration.MigrationUiStateManager;
import com.android.server.healthconnect.migration.MigratorPackageChangesReceiver;
import com.android.server.healthconnect.permission.PermissionPackageChangesOrchestrator;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.HealthConnectContext;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper.DatabaseHelpers;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.util.Objects;

/**
 * HealthConnect system service scaffold.
 *
 * @hide
 */
public class HealthConnectManagerService extends SystemService {
    private static final String TAG = "HealthConnectManagerService";
    private final Context mContext;
    private final PermissionPackageChangesOrchestrator mPermissionPackageChangesOrchestrator;
    private final HealthConnectServiceImpl mHealthConnectService;
    private final TransactionManager mTransactionManager;
    private final UserManager mUserManager;
    private final MigrationBroadcastScheduler mMigrationBroadcastScheduler;
    private final MigrationUiStateManager mMigrationUiStateManager;
    private final ExportImportSettingsStorage mExportImportSettingsStorage;
    private final ExportManager mExportManager;
    private final PreferenceHelper mPreferenceHelper;
    private final MigrationStateManager mMigrationStateManager;
    private final DatabaseHelpers mDatabaseHelpers;
    private final HealthConnectInjector mHealthConnectInjector;

    private UserHandle mCurrentForegroundUser;

    public HealthConnectManagerService(Context context) {
        super(context);
        mContext = context;
        mCurrentForegroundUser = context.getUser();
        mUserManager = context.getSystemService(UserManager.class);

        HealthConnectInjector.setInstance(new HealthConnectInjectorImpl(context));
        mHealthConnectInjector = HealthConnectInjector.getInstance();
        mTransactionManager = mHealthConnectInjector.getTransactionManager();
        mPreferenceHelper = mHealthConnectInjector.getPreferenceHelper();
        mMigrationStateManager = mHealthConnectInjector.getMigrationStateManager();
        mPermissionPackageChangesOrchestrator =
                mHealthConnectInjector.getPermissionPackageChangesOrchestrator();
        mExportImportSettingsStorage = mHealthConnectInjector.getExportImportSettingsStorage();
        mExportManager = mHealthConnectInjector.getExportManager();
        mMigrationBroadcastScheduler = mHealthConnectInjector.getMigrationBroadcastScheduler();
        mMigrationUiStateManager = mHealthConnectInjector.getMigrationUiStateManager();
        mDatabaseHelpers = mHealthConnectInjector.getDatabaseHelpers();
        mHealthConnectService =
                new HealthConnectServiceImpl(
                        mContext,
                        mHealthConnectInjector.getTimeSource(),
                        mHealthConnectInjector.getInternalHealthConnectMappings(),
                        mTransactionManager,
                        mHealthConnectInjector.getHealthConnectPermissionHelper(),
                        mHealthConnectInjector.getFirstGrantTimeManager(),
                        mHealthConnectInjector.getMigrationEntityHelper(),
                        mMigrationStateManager,
                        mMigrationUiStateManager,
                        mHealthConnectInjector.getMigrationCleaner(),
                        mHealthConnectInjector.getMedicalResourceHelper(),
                        mHealthConnectInjector.getMedicalDataSourceHelper(),
                        mExportManager,
                        mExportImportSettingsStorage,
                        mHealthConnectInjector.getBackupRestore(),
                        mHealthConnectInjector.getAccessLogsHelper(),
                        mHealthConnectInjector.getHealthDataCategoryPriorityHelper(),
                        mHealthConnectInjector.getActivityDateHelper(),
                        mHealthConnectInjector.getChangeLogsHelper(),
                        mHealthConnectInjector.getChangeLogsRequestHelper(),
                        mHealthConnectInjector.getPriorityMigrationHelper(),
                        mHealthConnectInjector.getAppInfoHelper(),
                        mHealthConnectInjector.getDeviceInfoHelper(),
                        mPreferenceHelper,
                        mDatabaseHelpers,
                        mHealthConnectInjector.getPreferencesManager());
    }

    @Override
    public void onStart() {
        mPermissionPackageChangesOrchestrator.registerBroadcastReceiver(mContext);
        new MigratorPackageChangesReceiver(mMigrationStateManager)
                .registerBroadcastReceiver(mContext);
        publishBinderService(Context.HEALTHCONNECT_SERVICE, mHealthConnectService);
    }

    /**
     * NOTE: Don't put any code that uses DB in onUserSwitching, such code should be part of
     * switchToSetupForUser which is only called once DB is in usable state.
     */
    @Override
    public void onUserSwitching(@Nullable TargetUser from, TargetUser to) {
        if (from != null && mUserManager.isUserUnlocked(from.getUserHandle())) {
            // We need to cancel any pending timers for the foreground user before it goes into the
            // background.
            mHealthConnectService.cancelBackupRestoreTimeouts();
        }

        HealthConnectThreadScheduler.shutdownThreadPools();
        mDatabaseHelpers.clearAllCache();
        mTransactionManager.onUserSwitching();
        RateLimiter.clearCache();
        HealthConnectThreadScheduler.resetThreadPools();
        mMigrationStateManager.onUserSwitching(mContext, to.getUserHandle());
        mCurrentForegroundUser = to.getUserHandle();

        if (mUserManager.isUserUnlocked(to.getUserHandle())) {
            // The user is already in unlocked state, so we should proceed with our setup right now,
            // as we won't be getting a onUserUnlocked callback
            switchToSetupForUser(to.getUserHandle());
        }
    }

    // NOTE: The only scenario in which onUserUnlocked's code should be triggered is if the
    // foreground user is unlocked. If {@code user} is not a foreground user, the following
    // code should only be triggered when the {@code user} actually gets unlocked. And in
    // such cases onUserSwitching will be triggered for {@code user} and this code will be
    // triggered then.
    @Override
    public void onUserUnlocked(TargetUser user) {
        Objects.requireNonNull(user);
        if (!user.getUserHandle().equals(mCurrentForegroundUser)) {
            // Ignore unlocking requests for non-foreground users
            return;
        }

        switchToSetupForUser(user.getUserHandle());
    }

    @Override
    public boolean isUserSupported(TargetUser user) {
        UserManager userManager =
                getUserContext(mContext, user.getUserHandle()).getSystemService(UserManager.class);
        return !(Objects.requireNonNull(userManager).isProfile());
    }

    private void switchToSetupForUser(UserHandle user) {
        Slog.d(TAG, "switchToSetupForUser: " + user);
        HealthConnectContext hcContext =
                HealthConnectContext.create(mContext, mCurrentForegroundUser);
        mTransactionManager.onUserUnlocked(hcContext);
        mHealthConnectService.onUserSwitching(mCurrentForegroundUser);
        mMigrationBroadcastScheduler.setUserId(mCurrentForegroundUser);
        mMigrationUiStateManager.setUserHandle(mCurrentForegroundUser);
        mPermissionPackageChangesOrchestrator.setUserHandle(mCurrentForegroundUser);
        mHealthConnectInjector
                .getHealthPermissionIntentAppsTracker()
                .onUserUnlocked(mCurrentForegroundUser);

        mHealthConnectInjector.getBackupRestore().setupForUser(mCurrentForegroundUser);
        mHealthConnectInjector.getAppInfoHelper().setupForUser(hcContext);
        mHealthConnectInjector.getHealthDataCategoryPriorityHelper().setupForUser(hcContext);

        if (Flags.clearCachesAfterSwitchingUser()) {
            // Clear preferences cache again after the user switching is done as there's a race
            // condition with tasks re-populating the preferences cache between clearing the cache
            // and TransactionManager switching user, see b/355426144.
            mPreferenceHelper.clearCache();
        }

        HealthConnectDailyJobs.cancelAllJobs(mContext);

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        HealthConnectDailyJobs.schedule(mContext, mCurrentForegroundUser);
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to schedule Health Connect daily service.", e);
                    }
                });

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        mMigrationBroadcastScheduler.scheduleNewJobs(
                                mContext, mMigrationStateManager);
                    } catch (Exception e) {
                        Slog.e(TAG, "Migration broadcast schedule failed", e);
                    }
                });

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        mMigrationStateManager.switchToSetupForUser(mContext);
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to start user unlocked state changes actions", e);
                    }
                });
        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        mPreferenceHelper.initializePreferences();
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to initialize preferences cache", e);
                    }
                });

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        ExportImportJobs.schedulePeriodicJobIfNotScheduled(
                                mCurrentForegroundUser,
                                mContext,
                                mExportImportSettingsStorage,
                                mExportManager);
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to schedule periodic export job.", e);
                    }
                });
    }

    private static Context getUserContext(Context context, UserHandle user) {
        if (Process.myUserHandle().equals(user)) {
            return context;
        } else {
            return context.createContextAsUser(user, 0);
        }
    }
}
