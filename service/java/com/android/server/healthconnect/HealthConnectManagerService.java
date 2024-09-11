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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.health.connect.HealthConnectManager;
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
import com.android.server.healthconnect.migration.MigrationCleaner;
import com.android.server.healthconnect.migration.MigrationStateManager;
import com.android.server.healthconnect.migration.MigrationUiStateManager;
import com.android.server.healthconnect.migration.MigratorPackageChangesReceiver;
import com.android.server.healthconnect.migration.PriorityMigrationHelper;
import com.android.server.healthconnect.migration.notification.MigrationNotificationSender;
import com.android.server.healthconnect.permission.FirstGrantTimeDatastore;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.permission.PermissionPackageChangesOrchestrator;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import java.time.Clock;
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
    private UserHandle mCurrentForegroundUser;
    private MigrationUiStateManager mMigrationUiStateManager;
    private final MigrationNotificationSender mMigrationNotificationSender;
    private final ExportImportSettingsStorage mExportImportSettingsStorage;
    private final ExportManager mExportManager;
    private final PreferenceHelper mPreferenceHelper;

    @Nullable
    private HealthConnectInjector mHealthConnectInjector;

    public HealthConnectManagerService(Context context) {
        super(context);
        mContext = context;
        mCurrentForegroundUser = context.getUser();
        HealthConnectDeviceConfigManager healthConnectDeviceConfigManager =
                HealthConnectDeviceConfigManager.initializeInstance(context);
        MigrationStateManager migrationStateManager;
        // This is needed now because MigrationStatedManager uses PreferenceHelper and
        // PreferenceHelper
        // after refactoring needs TransactionManager in the constructor. This will be cleaned up
        // once DI
        // is launched.
        if (Flags.dependencyInjection()) {
            HealthConnectInjector.setInstance(new HealthConnectInjectorImpl(context));
            mHealthConnectInjector = HealthConnectInjector.getInstance();
            mTransactionManager = mHealthConnectInjector.getTransactionManager();
            mPreferenceHelper = mHealthConnectInjector.getPreferenceHelper();
            migrationStateManager =
                    MigrationStateManager.initializeInstance(
                            mCurrentForegroundUser.getIdentifier(),
                            healthConnectDeviceConfigManager,
                            mHealthConnectInjector.getPreferenceHelper());
        } else {
            mPreferenceHelper = PreferenceHelper.getInstance();
            mTransactionManager =
                    TransactionManager.initializeInstance(
                            new HealthConnectUserContext(mContext, mCurrentForegroundUser));
            migrationStateManager =
                    MigrationStateManager.initializeInstance(
                            mCurrentForegroundUser.getIdentifier(),
                            healthConnectDeviceConfigManager,
                            mPreferenceHelper);
        }

        HealthPermissionIntentAppsTracker permissionIntentTracker =
                new HealthPermissionIntentAppsTracker(context);
        FirstGrantTimeManager firstGrantTimeManager;
        HealthConnectPermissionHelper permissionHelper;
        MigrationCleaner migrationCleaner;

        if (Flags.dependencyInjection()) {
            Objects.requireNonNull(mHealthConnectInjector);
            firstGrantTimeManager =
                    new FirstGrantTimeManager(
                            context,
                            permissionIntentTracker,
                            FirstGrantTimeDatastore.createInstance(),
                            mHealthConnectInjector.getPackageInfoUtils(),
                            mHealthConnectInjector.getHealthDataCategoryPriorityHelper());
            permissionHelper =
                    new HealthConnectPermissionHelper(
                            context,
                            context.getPackageManager(),
                            HealthConnectManager.getHealthPermissions(context),
                            permissionIntentTracker,
                            firstGrantTimeManager,
                            mHealthConnectInjector.getHealthDataCategoryPriorityHelper());
            mPermissionPackageChangesOrchestrator =
                    new PermissionPackageChangesOrchestrator(
                            permissionIntentTracker,
                            firstGrantTimeManager,
                            permissionHelper,
                            mCurrentForegroundUser,
                            mHealthConnectInjector.getHealthDataCategoryPriorityHelper());
            migrationCleaner =
                    new MigrationCleaner(
                            mHealthConnectInjector.getTransactionManager(),
                            mHealthConnectInjector.getPriorityMigrationHelper());
            mExportImportSettingsStorage = mHealthConnectInjector.getExportImportSettingsStorage();
            mExportManager = mHealthConnectInjector.getExportManager();
        } else {
            firstGrantTimeManager =
                    new FirstGrantTimeManager(
                            context,
                            permissionIntentTracker,
                            FirstGrantTimeDatastore.createInstance());
            permissionHelper =
                    new HealthConnectPermissionHelper(
                            context,
                            context.getPackageManager(),
                            HealthConnectManager.getHealthPermissions(context),
                            permissionIntentTracker,
                            firstGrantTimeManager);
            mPermissionPackageChangesOrchestrator =
                    new PermissionPackageChangesOrchestrator(
                            permissionIntentTracker,
                            firstGrantTimeManager,
                            permissionHelper,
                            mCurrentForegroundUser);
            migrationCleaner =
                    new MigrationCleaner(
                            mTransactionManager, PriorityMigrationHelper.getInstance());
            mExportImportSettingsStorage = new ExportImportSettingsStorage(mPreferenceHelper);
            mExportManager =
                    new ExportManager(
                            context,
                            Clock.systemUTC(),
                            mExportImportSettingsStorage,
                            mTransactionManager);
        }

        mUserManager = context.getSystemService(UserManager.class);
        mMigrationBroadcastScheduler =
                new MigrationBroadcastScheduler(mCurrentForegroundUser.getIdentifier());
        migrationStateManager.setMigrationBroadcastScheduler(mMigrationBroadcastScheduler);
        mMigrationNotificationSender = new MigrationNotificationSender(context);
        mMigrationUiStateManager =
                new MigrationUiStateManager(
                        mContext,
                        mCurrentForegroundUser,
                        migrationStateManager,
                        mMigrationNotificationSender);
        MedicalDataSourceHelper medicalDataSourceHelper =
                new MedicalDataSourceHelper(mTransactionManager, AppInfoHelper.getInstance());
        mHealthConnectService =
                new HealthConnectServiceImpl(
                        mTransactionManager,
                        healthConnectDeviceConfigManager,
                        permissionHelper,
                        migrationCleaner,
                        firstGrantTimeManager,
                        migrationStateManager,
                        mMigrationUiStateManager,
                        mContext,
                        new MedicalResourceHelper(mTransactionManager, medicalDataSourceHelper),
                        medicalDataSourceHelper,
                        mExportManager,
                        mExportImportSettingsStorage);
    }

    @Override
    public void onStart() {
        mPermissionPackageChangesOrchestrator.registerBroadcastReceiver(mContext);
        new MigratorPackageChangesReceiver(MigrationStateManager.getInitialisedInstance())
                .registerBroadcastReceiver(mContext);
        publishBinderService(Context.HEALTHCONNECT_SERVICE, mHealthConnectService);
        HealthConnectDeviceConfigManager.getInitialisedInstance().updateRateLimiterValues();
    }

    /**
     * NOTE: Don't put any code that uses DB in onUserSwitching, such code should be part of
     * switchToSetupForUser which is only called once DB is in usable state.
     */
    @Override
    public void onUserSwitching(@Nullable TargetUser from, @NonNull TargetUser to) {
        if (from != null && mUserManager.isUserUnlocked(from.getUserHandle())) {
            // We need to cancel any pending timers for the foreground user before it goes into the
            // background.
            mHealthConnectService.cancelBackupRestoreTimeouts();
        }

        HealthConnectThreadScheduler.shutdownThreadPools();
        DatabaseHelper.clearAllCache();
        mTransactionManager.onUserSwitching();
        RateLimiter.clearCache();
        HealthConnectThreadScheduler.resetThreadPools();
        MigrationStateManager migrationStateManager =
                MigrationStateManager.getInitialisedInstance();
        migrationStateManager.onUserSwitching(mContext, to.getUserHandle().getIdentifier());

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
    public void onUserUnlocked(@NonNull TargetUser user) {
        Objects.requireNonNull(user);
        if (!user.getUserHandle().equals(mCurrentForegroundUser)) {
            // Ignore unlocking requests for non-foreground users
            return;
        }

        switchToSetupForUser(user.getUserHandle());
    }

    @Override
    public boolean isUserSupported(@NonNull TargetUser user) {
        UserManager userManager =
                getUserContext(mContext, user.getUserHandle()).getSystemService(UserManager.class);
        return !(Objects.requireNonNull(userManager).isProfile());
    }

    private void switchToSetupForUser(UserHandle user) {
        // Note: This is for test setup debugging, please don't surround with DEBUG flag
        Slog.d(TAG, "switchToSetupForUser: " + user);
        mTransactionManager.onUserUnlocked(
                new HealthConnectUserContext(mContext, mCurrentForegroundUser));
        mHealthConnectService.onUserSwitching(mCurrentForegroundUser);
        mMigrationBroadcastScheduler.setUserId(mCurrentForegroundUser.getIdentifier());
        mMigrationUiStateManager.setUserHandle(mCurrentForegroundUser);
        mPermissionPackageChangesOrchestrator.setUserHandle(mCurrentForegroundUser);

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
                        HealthConnectDailyJobs.schedule(
                                mContext, mCurrentForegroundUser.getIdentifier());
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to schedule Health Connect daily service.", e);
                    }
                });

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        mMigrationBroadcastScheduler.scheduleNewJobs(mContext);
                    } catch (Exception e) {
                        Slog.e(TAG, "Migration broadcast schedule failed", e);
                    }
                });

        HealthConnectThreadScheduler.scheduleInternalTask(
                () -> {
                    try {
                        MigrationStateManager.getInitialisedInstance()
                                .switchToSetupForUser(mContext);
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
                        ExportImportJobs.schedulePeriodicExportJob(
                                mCurrentForegroundUser.getIdentifier(),
                                mContext,
                                mExportImportSettingsStorage,
                                mExportManager);
                    } catch (Exception e) {
                        Slog.e(TAG, "Failed to schedule periodic export job.", e);
                    }
                });
    }

    @NonNull
    private static Context getUserContext(@NonNull Context context, @NonNull UserHandle user) {
        if (Process.myUserHandle().equals(user)) {
            return context;
        } else {
            return context.createContextAsUser(user, 0);
        }
    }
}
