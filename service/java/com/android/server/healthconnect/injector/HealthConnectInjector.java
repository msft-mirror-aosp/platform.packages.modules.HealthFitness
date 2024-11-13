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

import android.health.connect.internal.datatypes.utils.HealthConnectMappings;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.exportimport.ExportManager;
import com.android.server.healthconnect.migration.MigrationBroadcastScheduler;
import com.android.server.healthconnect.migration.MigrationCleaner;
import com.android.server.healthconnect.migration.MigrationStateManager;
import com.android.server.healthconnect.migration.PriorityMigrationHelper;
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
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;
import com.android.server.healthconnect.utils.TimeSource;

/**
 * Interface for Health Connect Dependency Injector.
 *
 * @hide
 */
public abstract class HealthConnectInjector {

    @Nullable private static HealthConnectInjector sHealthConnectInjector;

    /** Getter for {@link PackageInfoUtils} instance initialised by the Health Connect Injector. */
    public abstract PackageInfoUtils getPackageInfoUtils();

    /**
     * Getter for {@link TransactionManager} instance initialised by the Health Connect Injector.
     */
    public abstract TransactionManager getTransactionManager();

    /**
     * Getter for {@link HealthDataCategoryPriorityHelper} instance initialised by the Health
     * Connect Injector.
     */
    public abstract HealthDataCategoryPriorityHelper getHealthDataCategoryPriorityHelper();

    /**
     * Getter for {@link PriorityMigrationHelper} instance initialised by the Health Connect
     * Injector.
     */
    public abstract PriorityMigrationHelper getPriorityMigrationHelper();

    /** Getter for {@link PreferenceHelper} instance initialised by the Health Connect Injector. */
    public abstract PreferenceHelper getPreferenceHelper();

    /**
     * Getter for {@link ExportImportSettingsStorage} instance initialised by the Health Connect
     * Injector.
     */
    public abstract ExportImportSettingsStorage getExportImportSettingsStorage();

    /** Getter for {@link ExportManager} instance initialised by the Health Connect Injector. */
    public abstract ExportManager getExportManager();

    /**
     * Getter for {@link MigrationStateManager} instance initialised by the Health Connect Injector.
     */
    public abstract MigrationStateManager getMigrationStateManager();

    /**
     * Getter for {@link HealthConnectDeviceConfigManager} instance initialised by the Health
     * Connect Injector.
     */
    public abstract HealthConnectDeviceConfigManager getHealthConnectDeviceConfigManager();

    /** Getter for {@link DeviceInfoHelper} instance initialised by the Health Connect Injector. */
    public abstract DeviceInfoHelper getDeviceInfoHelper();

    /** Getter for {@link AppInfoHelper} instance initialised by the Health Connect Injector. */
    public abstract AppInfoHelper getAppInfoHelper();

    /** Getter for {@link AccessLogsHelper} instance initialised by the Health Connect Injector. */
    public abstract AccessLogsHelper getAccessLogsHelper();

    /**
     * Getter for {@link ActivityDateHelper} instance initialised by the Health Connect Injector.
     */
    public abstract ActivityDateHelper getActivityDateHelper();

    /** Getter for {@link ChangeLogsHelper} instance initialised by the Health Connect Injector. */
    public abstract ChangeLogsHelper getChangeLogsHelper();

    /**
     * Getter for {@link ChangeLogsRequestHelper} instance initialised by the Health Connect
     * Injector.
     */
    public abstract ChangeLogsRequestHelper getChangeLogsRequestHelper();

    /**
     * Returns an instance of {@link HealthConnectMappings} initialised by the Health Connect
     * Injector.
     */
    public abstract HealthConnectMappings getHealthConnectMappings();

    /**
     * Returns an instance of {@link InternalHealthConnectMappings} initialised by the Health
     * Connect Injector.
     */
    public abstract InternalHealthConnectMappings getInternalHealthConnectMappings();

    /**
     * Getter for {@link FirstGrantTimeManager} instance initialised by the Health Connect Injector.
     */
    public abstract FirstGrantTimeManager getFirstGrantTimeManager();

    /**
     * Getter for {@link HealthPermissionIntentAppsTracker} instance initialised by the Health
     * Connect Injector.
     */
    public abstract HealthPermissionIntentAppsTracker getHealthPermissionIntentAppsTracker();

    /**
     * Getter for {@link PermissionPackageChangesOrchestrator} instance initialised by the Health
     * Connect Injector.
     */
    public abstract PermissionPackageChangesOrchestrator getPermissionPackageChangesOrchestrator();

    /**
     * Getter for {@link HealthConnectPermissionHelper} instance initialised by the Health Connect
     * Injector.
     */
    public abstract HealthConnectPermissionHelper getHealthConnectPermissionHelper();

    /** Getter for {@link MigrationCleaner} instance initialised by the Health Connect Injector. */
    public abstract MigrationCleaner getMigrationCleaner();

    /**
     * Getter for {@link MedicalResourceHelper} instance initialised by the Health Connect Injector.
     */
    public abstract MedicalResourceHelper getMedicalResourceHelper();

    /**
     * Getter for {@link MedicalDataSourceHelper} instance initialised by the Health Connect
     * Injector.
     */
    public abstract MedicalDataSourceHelper getMedicalDataSourceHelper();

    /** Getter for {@link TimeSource} instance initialised by the Health Connect Injector. */
    public abstract TimeSource getTimeSource();

    /**
     * Getter for {@link MigrationBroadcastScheduler} instance initialised by the Health Connect
     * Injector.
     */
    public abstract MigrationBroadcastScheduler getMigrationBroadcastScheduler();

    /** Used to initialize the Injector. */
    public static void setInstance(HealthConnectInjector healthConnectInjector) {
        if (sHealthConnectInjector != null) {
            throw new IllegalStateException(
                    "An instance of injector has already been initialized.");
        }
        sHealthConnectInjector = healthConnectInjector;
    }

    /**
     * Used to getInstance of the Injector so that it can be used statically by other base services.
     */
    public static HealthConnectInjector getInstance() {
        if (sHealthConnectInjector == null) {
            throw new IllegalStateException(
                    "Please initialize an instance of injector and call setInstance.");
        }
        return sHealthConnectInjector;
    }

    /** Used to reset instance of the Injector for testing. */
    @VisibleForTesting
    public static void resetInstanceForTest() {
        sHealthConnectInjector = null;
    }
}
