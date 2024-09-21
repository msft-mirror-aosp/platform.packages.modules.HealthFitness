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

import androidx.annotation.Nullable;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.exportimport.ExportManager;
import com.android.server.healthconnect.migration.MigrationStateManager;
import com.android.server.healthconnect.migration.PriorityMigrationHelper;
import com.android.server.healthconnect.permission.PackageInfoUtils;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

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

    public HealthConnectInjectorImpl(Context context) {
        this(new Builder(context));
    }

    private HealthConnectInjectorImpl(Builder builder) {
        HealthConnectUserContext healthConnectUserContext = builder.mHealthConnectUserContext;
        mHealthConnectDeviceConfigManager =
                builder.mHealthConnectDeviceConfigManager == null
                        ? HealthConnectDeviceConfigManager.initializeInstance(
                                healthConnectUserContext)
                        : builder.mHealthConnectDeviceConfigManager;
        mTransactionManager =
                builder.mTransactionManager == null
                        ? TransactionManager.initializeInstance(healthConnectUserContext)
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
        mHealthDataCategoryPriorityHelper =
                builder.mHealthDataCategoryPriorityHelper == null
                        ? HealthDataCategoryPriorityHelper.getInstance(
                                mAppInfoHelper,
                                mTransactionManager,
                                mHealthConnectDeviceConfigManager,
                                mPreferenceHelper,
                                mPackageInfoUtils)
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
                                healthConnectUserContext,
                                Clock.systemUTC(),
                                mExportImportSettingsStorage,
                                mTransactionManager)
                        : builder.mExportManager;
        mMigrationStateManager =
                builder.mMigrationStateManager == null
                        ? MigrationStateManager.initializeInstance(
                                healthConnectUserContext.getUser().getIdentifier(),
                                mHealthConnectDeviceConfigManager,
                                mPreferenceHelper)
                        : builder.mMigrationStateManager;
        mDeviceInfoHelper =
                builder.mDeviceInfoHelper == null
                        ? DeviceInfoHelper.getInstance(mTransactionManager)
                        : builder.mDeviceInfoHelper;
        mAccessLogsHelper =
                builder.mAccessLogsHelper == null
                        ? AccessLogsHelper.getInstance(mTransactionManager, mAppInfoHelper)
                        : builder.mAccessLogsHelper;
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

        private final HealthConnectUserContext mHealthConnectUserContext;

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

        private Builder(Context context) {
            mHealthConnectUserContext = new HealthConnectUserContext(context, context.getUser());
        }

        /** Set fake or custom PackageInfoUtils */
        public Builder setPackageInfoUtils(PackageInfoUtils packageInfoUtils) {
            Objects.requireNonNull(packageInfoUtils);
            mPackageInfoUtils = packageInfoUtils;
            return this;
        }

        /** Set fake or custom TransactionManager */
        public Builder setTransactionManager(TransactionManager transactionManager) {
            Objects.requireNonNull(transactionManager);
            mTransactionManager = transactionManager;
            return this;
        }

        /** Set fake or custom HealthDataCategoryPriorityHelper */
        public Builder setHealthDataCategoryPriorityHelper(
                HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper) {
            Objects.requireNonNull(healthDataCategoryPriorityHelper);
            mHealthDataCategoryPriorityHelper = healthDataCategoryPriorityHelper;
            return this;
        }

        /** Set fake or custom PriorityMigrationHelper */
        public Builder setPriorityMigrationHelper(PriorityMigrationHelper priorityMigrationHelper) {
            Objects.requireNonNull(priorityMigrationHelper);
            mPriorityMigrationHelper = priorityMigrationHelper;
            return this;
        }

        /** Set fake or custom PreferenceHelper */
        public Builder setPreferenceHelper(PreferenceHelper preferenceHelper) {
            Objects.requireNonNull(preferenceHelper);
            mPreferenceHelper = preferenceHelper;
            return this;
        }

        /** Set fake or custom ExportImportSettingsStorage */
        public Builder setExportImportSettingsStorage(
                ExportImportSettingsStorage exportImportSettingsStorage) {
            Objects.requireNonNull(exportImportSettingsStorage);
            mExportImportSettingsStorage = exportImportSettingsStorage;
            return this;
        }

        /** Set fake or custom ExportManager */
        public Builder setExportManager(ExportManager exportManager) {
            Objects.requireNonNull(exportManager);
            mExportManager = exportManager;
            return this;
        }

        /** Set fake or custom HealthConnectDeviceConfigManager */
        public Builder setHealthConnectDeviceConfigManager(
                HealthConnectDeviceConfigManager healthConnectDeviceConfigManager) {
            Objects.requireNonNull(healthConnectDeviceConfigManager);
            mHealthConnectDeviceConfigManager = healthConnectDeviceConfigManager;
            return this;
        }

        /** Set fake or custom MigrationStateManager */
        public Builder setMigrationStateManager(MigrationStateManager migrationStateManager) {
            Objects.requireNonNull(migrationStateManager);
            mMigrationStateManager = migrationStateManager;
            return this;
        }

        /** Set fake or custom DeviceInfoHelper */
        public Builder setDeviceInfoHelper(DeviceInfoHelper deviceInfoHelper) {
            Objects.requireNonNull(deviceInfoHelper);
            mDeviceInfoHelper = deviceInfoHelper;
            return this;
        }

        /** Set fake or custom AppInfoHelper */
        public Builder setAppInfoHelper(AppInfoHelper appInfoHelper) {
            Objects.requireNonNull(appInfoHelper);
            mAppInfoHelper = appInfoHelper;
            return this;
        }

        /** Set fake or custom AccessLogsHelper */
        public Builder setAccessLogsHelper(AccessLogsHelper accessLogsHelper) {
            Objects.requireNonNull(accessLogsHelper);
            mAccessLogsHelper = accessLogsHelper;
            return this;
        }

        /** Build HealthConnectInjector */
        public HealthConnectInjector build() {
            return new HealthConnectInjectorImpl(this);
        }
    }
}
