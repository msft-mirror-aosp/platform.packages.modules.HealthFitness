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
import android.health.connect.HealthConnectManager;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.health.connect.ratelimiter.RateLimiter;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Slog;

import com.android.healthfitness.flags.Flags;
import com.android.server.SystemService;
import com.android.server.healthconnect.backuprestore.CloudBackupManager;
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
import com.android.server.healthconnect.permission.PackageInfoUtils;
import com.android.server.healthconnect.permission.PermissionPackageChangesOrchestrator;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;
import com.android.server.healthconnect.utils.TimeSource;
import com.android.server.healthconnect.utils.TimeSourceImpl;

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
    private final HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager;
    private final MigrationStateManager mMigrationStateManager;
    private final CloudBackupManager mCloudBackupManager;

    @Nullable private HealthConnectInjector mHealthConnectInjector;

    public HealthConnectManagerService(Context context) {
        super(context);
        mContext = context;
        mCurrentForegroundUser = context.getUser();
        HealthPermissionIntentAppsTracker permissionIntentTracker =
                new HealthPermissionIntentAppsTracker(context);

        AppInfoHelper appInfoHelper;
        AccessLogsHelper accessLogsHelper;
        HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper;
        ActivityDateHelper activityDateHelper;
        ChangeLogsHelper changeLogsHelper;
        ChangeLogsRequestHelper changeLogsRequestHelper;
        FirstGrantTimeManager firstGrantTimeManager;
        HealthConnectMappings healthConnectMappings;
        HealthConnectPermissionHelper permissionHelper;
        MigrationCleaner migrationCleaner;
        InternalHealthConnectMappings internalHealthConnectMappings;

        if (Flags.dependencyInjection()) {
            HealthConnectInjector.setInstance(new HealthConnectInjectorImpl(context));
            mHealthConnectInjector = HealthConnectInjector.getInstance();
            mHealthConnectDeviceConfigManager =
                    mHealthConnectInjector.getHealthConnectDeviceConfigManager();
            mTransactionManager = mHealthConnectInjector.getTransactionManager();
            mPreferenceHelper = mHealthConnectInjector.getPreferenceHelper();
            mMigrationStateManager = mHealthConnectInjector.getMigrationStateManager();
            appInfoHelper = mHealthConnectInjector.getAppInfoHelper();
            accessLogsHelper = mHealthConnectInjector.getAccessLogsHelper();
            healthDataCategoryPriorityHelper =
                    mHealthConnectInjector.getHealthDataCategoryPriorityHelper();
            activityDateHelper = mHealthConnectInjector.getActivityDateHelper();
            changeLogsHelper = mHealthConnectInjector.getChangeLogsHelper();
            changeLogsRequestHelper = mHealthConnectInjector.getChangeLogsRequestHelper();
            firstGrantTimeManager =
                    new FirstGrantTimeManager(
                            context,
                            permissionIntentTracker,
                            FirstGrantTimeDatastore.createInstance(),
                            mHealthConnectInjector.getPackageInfoUtils(),
                            healthDataCategoryPriorityHelper,
                            mMigrationStateManager);
            healthConnectMappings = mHealthConnectInjector.getHealthConnectMappings();
            internalHealthConnectMappings =
                    mHealthConnectInjector.getInternalHealthConnectMappings();
            permissionHelper =
                    new HealthConnectPermissionHelper(
                            context,
                            context.getPackageManager(),
                            HealthConnectManager.getHealthPermissions(context),
                            permissionIntentTracker,
                            firstGrantTimeManager,
                            healthDataCategoryPriorityHelper,
                            appInfoHelper,
                            healthConnectMappings);
            mPermissionPackageChangesOrchestrator =
                    new PermissionPackageChangesOrchestrator(
                            permissionIntentTracker,
                            firstGrantTimeManager,
                            permissionHelper,
                            mCurrentForegroundUser,
                            healthDataCategoryPriorityHelper);
            migrationCleaner =
                    new MigrationCleaner(
                            mHealthConnectInjector.getTransactionManager(),
                            mHealthConnectInjector.getPriorityMigrationHelper());
            mExportImportSettingsStorage = mHealthConnectInjector.getExportImportSettingsStorage();
            mExportManager = mHealthConnectInjector.getExportManager();
            mCloudBackupManager = mHealthConnectInjector.getCloudBackupManager();
        } else {
            mHealthConnectDeviceConfigManager =
                    HealthConnectDeviceConfigManager.initializeInstance(context);
            mTransactionManager =
                    TransactionManager.initializeInstance(
                            new HealthConnectUserContext(mContext, mCurrentForegroundUser));
            mPreferenceHelper = PreferenceHelper.getInstance();
            mMigrationStateManager =
                    MigrationStateManager.initializeInstance(
                            mCurrentForegroundUser.getIdentifier(),
                            mHealthConnectDeviceConfigManager,
                            mPreferenceHelper);
            appInfoHelper = AppInfoHelper.getInstance(mTransactionManager);
            accessLogsHelper = AccessLogsHelper.getInstance(mTransactionManager, appInfoHelper);
            changeLogsHelper = new ChangeLogsHelper(mTransactionManager);
            changeLogsRequestHelper = new ChangeLogsRequestHelper(mTransactionManager);
            healthDataCategoryPriorityHelper = HealthDataCategoryPriorityHelper.getInstance();
            activityDateHelper = ActivityDateHelper.getInstance();
            firstGrantTimeManager =
                    new FirstGrantTimeManager(
                            context,
                            permissionIntentTracker,
                            FirstGrantTimeDatastore.createInstance(),
                            PackageInfoUtils.getInstance(),
                            healthDataCategoryPriorityHelper,
                            mMigrationStateManager);
            healthConnectMappings = new HealthConnectMappings();
            internalHealthConnectMappings =
                    new InternalHealthConnectMappings(healthConnectMappings);
            permissionHelper =
                    new HealthConnectPermissionHelper(
                            context,
                            context.getPackageManager(),
                            HealthConnectManager.getHealthPermissions(context),
                            permissionIntentTracker,
                            firstGrantTimeManager,
                            healthDataCategoryPriorityHelper,
                            appInfoHelper,
                            healthConnectMappings);
            mPermissionPackageChangesOrchestrator =
                    new PermissionPackageChangesOrchestrator(
                            permissionIntentTracker,
                            firstGrantTimeManager,
                            permissionHelper,
                            mCurrentForegroundUser,
                            healthDataCategoryPriorityHelper);
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
            mCloudBackupManager = new CloudBackupManager();
        }

        mUserManager = context.getSystemService(UserManager.class);
        mMigrationBroadcastScheduler =
                new MigrationBroadcastScheduler(
                        mCurrentForegroundUser.getIdentifier(),
                        mHealthConnectDeviceConfigManager,
                        mMigrationStateManager);
        mMigrationStateManager.setMigrationBroadcastScheduler(mMigrationBroadcastScheduler);
        mMigrationNotificationSender =
                new MigrationNotificationSender(context, mHealthConnectDeviceConfigManager);
        mMigrationUiStateManager =
                new MigrationUiStateManager(
                        mContext,
                        mCurrentForegroundUser,
                        mMigrationStateManager,
                        mMigrationNotificationSender);
        TimeSource timeSource = new TimeSourceImpl();
        MedicalDataSourceHelper medicalDataSourceHelper =
                new MedicalDataSourceHelper(
                        mTransactionManager, appInfoHelper, timeSource, accessLogsHelper);
        mHealthConnectService =
                new HealthConnectServiceImpl(
                        mTransactionManager,
                        mHealthConnectDeviceConfigManager,
                        permissionHelper,
                        migrationCleaner,
                        firstGrantTimeManager,
                        mMigrationStateManager,
                        mMigrationUiStateManager,
                        new MedicalResourceHelper(
                                mTransactionManager,
                                appInfoHelper,
                                medicalDataSourceHelper,
                                timeSource,
                                accessLogsHelper),
                        medicalDataSourceHelper,
                        mContext,
                        mExportManager,
                        mExportImportSettingsStorage,
                        accessLogsHelper,
                        healthDataCategoryPriorityHelper,
                        activityDateHelper,
                        changeLogsHelper,
                        changeLogsRequestHelper,
                        internalHealthConnectMappings,
                        mCloudBackupManager);
    }

    @Override
    public void onStart() {
        mPermissionPackageChangesOrchestrator.registerBroadcastReceiver(mContext);
        new MigratorPackageChangesReceiver(mMigrationStateManager)
                .registerBroadcastReceiver(mContext);
        publishBinderService(Context.HEALTHCONNECT_SERVICE, mHealthConnectService);
        mHealthConnectDeviceConfigManager.updateRateLimiterValues();
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
        DatabaseHelper.clearAllCache();
        mTransactionManager.onUserSwitching();
        RateLimiter.clearCache();
        HealthConnectThreadScheduler.resetThreadPools();
        mMigrationStateManager.onUserSwitching(mContext, to.getUserHandle().getIdentifier());

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
                                mCurrentForegroundUser.getIdentifier(),
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
