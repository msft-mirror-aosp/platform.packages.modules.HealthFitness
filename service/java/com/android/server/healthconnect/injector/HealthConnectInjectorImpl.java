/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.healthconnect.injector;

import android.content.Context;
import android.health.connect.HealthConnectManager;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.exportimport.ExportManager;
import com.android.server.healthconnect.migration.MigrationCleaner;
import com.android.server.healthconnect.migration.MigrationStateManager;
import com.android.server.healthconnect.migration.PriorityMigrationHelper;
import com.android.server.healthconnect.permission.FirstGrantTimeDatastore;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthConnectPermissionHelper;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.permission.PackageInfoUtils;
import com.android.server.healthconnect.permission.PermissionPackageChangesOrchestrator;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.StorageContext;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

import java.time.Clock;
import java.util.Objects;

/**
 * Injector implementation of HealthConnectInjector containing dependencies to be used in production
 * version of the module.
 *
 * @hide
 */
public class HealthConnectInjectorImpl extends HealthConnectInjector {

    private final PackageInfoUtils mPackageInfoUtils;
    private final TransactionManager mTransactionManager;
    private final HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    private final PriorityMigrationHelper mPriorityMigrationHelper;
    private final PreferenceHelper mPreferenceHelper;
    private final ExportImportSettingsStorage mExportImportSettingsStorage;
    private final ExportManager mExportManager;
    private final HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager;
    private final MigrationStateManager mMigrationStateManager;
    private final DeviceInfoHelper mDeviceInfoHelper;
    private final AppInfoHelper mAppInfoHelper;
    private final AccessLogsHelper mAccessLogsHelper;
    private final ActivityDateHelper mActivityDateHelper;
    private final HealthConnectMappings mHealthConnectMappings;
    private final InternalHealthConnectMappings mInternalHealthConnectMappings;
    private final ChangeLogsHelper mChangeLogsHelper;
    private final ChangeLogsRequestHelper mChangeLogsRequestHelper;
    private final FirstGrantTimeManager mFirstGrantTimeManager;
    private final HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;
    private final PermissionPackageChangesOrchestrator mPermissionPackageChangesOrchestrator;
    private final HealthConnectPermissionHelper mHealthConnectPermissionHelper;
    private final MigrationCleaner mMigrationCleaner;

    public HealthConnectInjectorImpl(Context context) {
        this(new Builder(context));
    }

    private HealthConnectInjectorImpl(Builder builder) {
        Context context = builder.mContext;
        // Don't store the user and make it available via the injector, as this user is always
        // the first / system user, and doesn't change after that.
        // Any class that is using this user below are responsible for making sure that they
        // update any reference to user when it changes.
        UserHandle userHandle = builder.mUserHandle;

        mHealthConnectDeviceConfigManager =
                builder.mHealthConnectDeviceConfigManager == null
                        ? HealthConnectDeviceConfigManager.initializeInstance(context)
                        : builder.mHealthConnectDeviceConfigManager;
        mTransactionManager =
                builder.mTransactionManager == null
                        ? TransactionManager.initializeInstance(
                                new StorageContext(context, userHandle))
                        : builder.mTransactionManager;
        mAppInfoHelper =
                builder.mAppInfoHelper == null
                        ? AppInfoHelper.getInstance(mTransactionManager)
                        : builder.mAppInfoHelper;
        mPackageInfoUtils =
                builder.mPackageInfoUtils == null
                        ? PackageInfoUtils.getInstance()
                        : builder.mPackageInfoUtils;
        mPreferenceHelper =
                builder.mPreferenceHelper == null
                        ? PreferenceHelper.getInstance(mTransactionManager)
                        : builder.mPreferenceHelper;
        mHealthConnectMappings = HealthConnectMappings.getInstance();
        mInternalHealthConnectMappings = InternalHealthConnectMappings.getInstance();
        mHealthDataCategoryPriorityHelper =
                builder.mHealthDataCategoryPriorityHelper == null
                        ? HealthDataCategoryPriorityHelper.getInstance(
                                mAppInfoHelper,
                                mTransactionManager,
                                mHealthConnectDeviceConfigManager,
                                mPreferenceHelper,
                                mPackageInfoUtils,
                                mHealthConnectMappings)
                        : builder.mHealthDataCategoryPriorityHelper;
        mPriorityMigrationHelper =
                builder.mPriorityMigrationHelper == null
                        ? PriorityMigrationHelper.getInstance(
                                mHealthDataCategoryPriorityHelper, mTransactionManager)
                        : builder.mPriorityMigrationHelper;
        mExportImportSettingsStorage =
                builder.mExportImportSettingsStorage == null
                        ? new ExportImportSettingsStorage(mPreferenceHelper)
                        : builder.mExportImportSettingsStorage;
        mExportManager =
                builder.mExportManager == null
                        ? new ExportManager(
                                context,
                                Clock.systemUTC(),
                                mExportImportSettingsStorage,
                                mTransactionManager)
                        : builder.mExportManager;
        mMigrationStateManager =
                builder.mMigrationStateManager == null
                        ? MigrationStateManager.initializeInstance(
                                userHandle, mHealthConnectDeviceConfigManager, mPreferenceHelper)
                        : builder.mMigrationStateManager;
        mDeviceInfoHelper =
                builder.mDeviceInfoHelper == null
                        ? DeviceInfoHelper.getInstance(mTransactionManager)
                        : builder.mDeviceInfoHelper;
        mAccessLogsHelper =
                builder.mAccessLogsHelper == null
                        ? AccessLogsHelper.getInstance(mTransactionManager, mAppInfoHelper)
                        : builder.mAccessLogsHelper;
        mActivityDateHelper =
                builder.mActivityDateHelper == null
                        ? ActivityDateHelper.getInstance(mTransactionManager)
                        : builder.mActivityDateHelper;
        mChangeLogsHelper =
                builder.mChangeLogsHelper == null
                        ? new ChangeLogsHelper(mTransactionManager)
                        : builder.mChangeLogsHelper;
        mChangeLogsRequestHelper =
                builder.mChangeLogsRequestHelper == null
                        ? new ChangeLogsRequestHelper(mTransactionManager)
                        : builder.mChangeLogsRequestHelper;
        mPermissionIntentAppsTracker =
                builder.mPermissionIntentAppsTracker == null
                        ? new HealthPermissionIntentAppsTracker(context)
                        : builder.mPermissionIntentAppsTracker;
        mFirstGrantTimeManager =
                builder.mFirstGrantTimeManager == null
                        ? new FirstGrantTimeManager(
                                context,
                                mPermissionIntentAppsTracker,
                                builder.mFirstGrantTimeDatastore == null
                                        ? FirstGrantTimeDatastore.createInstance()
                                        : builder.mFirstGrantTimeDatastore,
                                mPackageInfoUtils,
                                mHealthDataCategoryPriorityHelper,
                                mMigrationStateManager)
                        : builder.mFirstGrantTimeManager;
        mHealthConnectPermissionHelper =
                builder.mHealthConnectPermissionHelper == null
                        ? new HealthConnectPermissionHelper(
                                context,
                                context.getPackageManager(),
                                HealthConnectManager.getHealthPermissions(context),
                                mPermissionIntentAppsTracker,
                                mFirstGrantTimeManager,
                                mHealthDataCategoryPriorityHelper,
                                mAppInfoHelper,
                                mHealthConnectMappings)
                        : builder.mHealthConnectPermissionHelper;
        mPermissionPackageChangesOrchestrator =
                builder.mPermissionPackageChangesOrchestrator == null
                        ? new PermissionPackageChangesOrchestrator(
                                mPermissionIntentAppsTracker,
                                mFirstGrantTimeManager,
                                mHealthConnectPermissionHelper,
                                context.getUser(),
                                mHealthDataCategoryPriorityHelper)
                        : builder.mPermissionPackageChangesOrchestrator;
        mMigrationCleaner =
                builder.mMigrationCleaner == null
                        ? new MigrationCleaner(mTransactionManager, mPriorityMigrationHelper)
                        : builder.mMigrationCleaner;
    }

    @Override
    public PackageInfoUtils getPackageInfoUtils() {
        return mPackageInfoUtils;
    }

    @Override
    public TransactionManager getTransactionManager() {
        return mTransactionManager;
    }

    @Override
    public HealthDataCategoryPriorityHelper getHealthDataCategoryPriorityHelper() {
        return mHealthDataCategoryPriorityHelper;
    }

    @Override
    public PriorityMigrationHelper getPriorityMigrationHelper() {
        return mPriorityMigrationHelper;
    }

    @Override
    public PreferenceHelper getPreferenceHelper() {
        return mPreferenceHelper;
    }

    @Override
    public ExportImportSettingsStorage getExportImportSettingsStorage() {
        return mExportImportSettingsStorage;
    }

    @Override
    public ExportManager getExportManager() {
        return mExportManager;
    }

    @Override
    public HealthConnectDeviceConfigManager getHealthConnectDeviceConfigManager() {
        return mHealthConnectDeviceConfigManager;
    }

    @Override
    public MigrationStateManager getMigrationStateManager() {
        return mMigrationStateManager;
    }

    @Override
    public DeviceInfoHelper getDeviceInfoHelper() {
        return mDeviceInfoHelper;
    }

    @Override
    public AppInfoHelper getAppInfoHelper() {
        return mAppInfoHelper;
    }

    @Override
    public AccessLogsHelper getAccessLogsHelper() {
        return mAccessLogsHelper;
    }

    @Override
    public ActivityDateHelper getActivityDateHelper() {
        return mActivityDateHelper;
    }

    @Override
    public ChangeLogsHelper getChangeLogsHelper() {
        return mChangeLogsHelper;
    }

    @Override
    public ChangeLogsRequestHelper getChangeLogsRequestHelper() {
        return mChangeLogsRequestHelper;
    }

    @Override
    public HealthConnectMappings getHealthConnectMappings() {
        return mHealthConnectMappings;
    }

    @Override
    public InternalHealthConnectMappings getInternalHealthConnectMappings() {
        return mInternalHealthConnectMappings;
    }

    @Override
    public FirstGrantTimeManager getFirstGrantTimeManager() {
        return mFirstGrantTimeManager;
    }

    @Override
    public HealthPermissionIntentAppsTracker getHealthPermissionIntentAppsTracker() {
        return mPermissionIntentAppsTracker;
    }

    @Override
    public PermissionPackageChangesOrchestrator getPermissionPackageChangesOrchestrator() {
        return mPermissionPackageChangesOrchestrator;
    }

    @Override
    public HealthConnectPermissionHelper getHealthConnectPermissionHelper() {
        return mHealthConnectPermissionHelper;
    }

    @Override
    public MigrationCleaner getMigrationCleaner() {
        return mMigrationCleaner;
    }

    /**
     * Returns a new Builder of Health Connect Injector
     *
     * <p>USE ONLY DURING TESTING.
     */
    public static Builder newBuilderForTest(Context context) {
        return new Builder(context);
    }

    /**
     * Used to build injector.
     *
     * <p>The setters are used only when we need a custom implementation of any dependency which is
     * ONLY for testing. Do not use setters if we need default implementation of a dependency.
     */
    public static class Builder {

        private final Context mContext;
        private final UserHandle mUserHandle;

        @Nullable private PackageInfoUtils mPackageInfoUtils;
        @Nullable private TransactionManager mTransactionManager;
        @Nullable private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
        @Nullable private PriorityMigrationHelper mPriorityMigrationHelper;
        @Nullable private PreferenceHelper mPreferenceHelper;
        @Nullable private ExportImportSettingsStorage mExportImportSettingsStorage;
        @Nullable private ExportManager mExportManager;
        @Nullable private HealthConnectDeviceConfigManager mHealthConnectDeviceConfigManager;
        @Nullable private MigrationStateManager mMigrationStateManager;
        @Nullable private DeviceInfoHelper mDeviceInfoHelper;
        @Nullable private AppInfoHelper mAppInfoHelper;
        @Nullable private AccessLogsHelper mAccessLogsHelper;
        @Nullable private ActivityDateHelper mActivityDateHelper;
        @Nullable private ChangeLogsHelper mChangeLogsHelper;
        @Nullable private ChangeLogsRequestHelper mChangeLogsRequestHelper;
        @Nullable private FirstGrantTimeManager mFirstGrantTimeManager;
        @Nullable private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;
        @Nullable private FirstGrantTimeDatastore mFirstGrantTimeDatastore;

        @Nullable
        private PermissionPackageChangesOrchestrator mPermissionPackageChangesOrchestrator;

        @Nullable private HealthConnectPermissionHelper mHealthConnectPermissionHelper;
        @Nullable private MigrationCleaner mMigrationCleaner;

        private Builder(Context context) {
            mContext = context;
            mUserHandle = context.getUser();
        }

        /** Set fake or custom {@link PackageInfoUtils} */
        public Builder setPackageInfoUtils(PackageInfoUtils packageInfoUtils) {
            Objects.requireNonNull(packageInfoUtils);
            mPackageInfoUtils = packageInfoUtils;
            return this;
        }

        /** Set fake or custom {@link TransactionManager} */
        public Builder setTransactionManager(TransactionManager transactionManager) {
            Objects.requireNonNull(transactionManager);
            mTransactionManager = transactionManager;
            return this;
        }

        /** Set fake or custom {@link HealthDataCategoryPriorityHelper} */
        public Builder setHealthDataCategoryPriorityHelper(
                HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper) {
            Objects.requireNonNull(healthDataCategoryPriorityHelper);
            mHealthDataCategoryPriorityHelper = healthDataCategoryPriorityHelper;
            return this;
        }

        /** Set fake or custom {@link PriorityMigrationHelper} */
        public Builder setPriorityMigrationHelper(PriorityMigrationHelper priorityMigrationHelper) {
            Objects.requireNonNull(priorityMigrationHelper);
            mPriorityMigrationHelper = priorityMigrationHelper;
            return this;
        }

        /** Set fake or custom {@link PreferenceHelper} */
        public Builder setPreferenceHelper(PreferenceHelper preferenceHelper) {
            Objects.requireNonNull(preferenceHelper);
            mPreferenceHelper = preferenceHelper;
            return this;
        }

        /** Set fake or custom {@link ExportImportSettingsStorage} */
        public Builder setExportImportSettingsStorage(
                ExportImportSettingsStorage exportImportSettingsStorage) {
            Objects.requireNonNull(exportImportSettingsStorage);
            mExportImportSettingsStorage = exportImportSettingsStorage;
            return this;
        }

        /** Set fake or custom {@link ExportManager} */
        public Builder setExportManager(ExportManager exportManager) {
            Objects.requireNonNull(exportManager);
            mExportManager = exportManager;
            return this;
        }

        /** Set fake or custom {@link HealthConnectDeviceConfigManager} */
        public Builder setHealthConnectDeviceConfigManager(
                HealthConnectDeviceConfigManager healthConnectDeviceConfigManager) {
            Objects.requireNonNull(healthConnectDeviceConfigManager);
            mHealthConnectDeviceConfigManager = healthConnectDeviceConfigManager;
            return this;
        }

        /** Set fake or custom {@link MigrationStateManager} */
        public Builder setMigrationStateManager(MigrationStateManager migrationStateManager) {
            Objects.requireNonNull(migrationStateManager);
            mMigrationStateManager = migrationStateManager;
            return this;
        }

        /** Set fake or custom {@link DeviceInfoHelper} */
        public Builder setDeviceInfoHelper(DeviceInfoHelper deviceInfoHelper) {
            Objects.requireNonNull(deviceInfoHelper);
            mDeviceInfoHelper = deviceInfoHelper;
            return this;
        }

        /** Set fake or custom {@link AppInfoHelper} */
        public Builder setAppInfoHelper(AppInfoHelper appInfoHelper) {
            Objects.requireNonNull(appInfoHelper);
            mAppInfoHelper = appInfoHelper;
            return this;
        }

        /** Set fake or custom {@link AccessLogsHelper} */
        public Builder setAccessLogsHelper(AccessLogsHelper accessLogsHelper) {
            Objects.requireNonNull(accessLogsHelper);
            mAccessLogsHelper = accessLogsHelper;
            return this;
        }

        /** Set fake or custom {@link ActivityDateHelper} */
        public Builder setActivityDateHelper(ActivityDateHelper activityDateHelper) {
            Objects.requireNonNull(activityDateHelper);
            mActivityDateHelper = activityDateHelper;
            return this;
        }

        /** Set fake or custom {@link ChangeLogsHelper} */
        public Builder setChangeLogsHelper(ChangeLogsHelper changeLogsHelper) {
            Objects.requireNonNull(changeLogsHelper);
            mChangeLogsHelper = changeLogsHelper;
            return this;
        }

        /** Set fake or custom {@link ChangeLogsRequestHelper} */
        public Builder setChangeLogsRequestHelper(ChangeLogsRequestHelper changeLogsRequestHelper) {
            Objects.requireNonNull(changeLogsRequestHelper);
            mChangeLogsRequestHelper = changeLogsRequestHelper;
            return this;
        }

        /** Set fake or custom {@link FirstGrantTimeManager} */
        public Builder setFirstGrantTimeManager(FirstGrantTimeManager firstGrantTimeManager) {
            Objects.requireNonNull(firstGrantTimeManager);
            mFirstGrantTimeManager = firstGrantTimeManager;
            return this;
        }

        /** Set fake or custom {@link FirstGrantTimeDatastore} */
        public Builder setFirstGrantTimeDatastore(FirstGrantTimeDatastore firstGrantTimeDatastore) {
            Objects.requireNonNull(firstGrantTimeDatastore);
            mFirstGrantTimeDatastore = firstGrantTimeDatastore;
            return this;
        }

        /** Set fake or custom {@link HealthPermissionIntentAppsTracker} */
        public Builder setHealthPermissionIntentAppsTracker(
                HealthPermissionIntentAppsTracker healthPermissionIntentAppsTracker) {
            Objects.requireNonNull(healthPermissionIntentAppsTracker);
            mPermissionIntentAppsTracker = healthPermissionIntentAppsTracker;
            return this;
        }

        /** Set fake or custom {@link PermissionPackageChangesOrchestrator} */
        public Builder setPermissionPackageChangesOrchestrator(
                PermissionPackageChangesOrchestrator permissionPackageChangesOrchestrator) {
            Objects.requireNonNull(permissionPackageChangesOrchestrator);
            mPermissionPackageChangesOrchestrator = permissionPackageChangesOrchestrator;
            return this;
        }

        /** Set fake or custom {@link HealthConnectPermissionHelper} */
        public Builder setHealthConnectPermissionHelper(
                HealthConnectPermissionHelper healthConnectPermissionHelper) {
            Objects.requireNonNull(healthConnectPermissionHelper);
            mHealthConnectPermissionHelper = healthConnectPermissionHelper;
            return this;
        }

        /** Set fake or custom {@link MigrationCleaner} */
        public Builder setMigrationCleaner(MigrationCleaner migrationCleaner) {
            Objects.requireNonNull(migrationCleaner);
            mMigrationCleaner = migrationCleaner;
            return this;
        }

        /** Build HealthConnectInjector */
        public HealthConnectInjector build() {
            return new HealthConnectInjectorImpl(this);
        }
    }
}
