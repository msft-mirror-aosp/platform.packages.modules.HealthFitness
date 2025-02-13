/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Class to log metrics for Backup and Restore
 *
 * @hide
 */
public class BackupRestoreLogger {

    // TODO(b/394767444): Replace uses with the correct values once atoms implemented.
    public static final int NOT_IMPLEMENTED = -1;

    /**
     * Status enums used in logging.
     *
     * @hide
     */
    public static final class BackupRestoreEnums {
        // enums for DataBackupStatus
        public static final int DATA_BACKUP_STATUS_ERROR_UNSPECIFIED = NOT_IMPLEMENTED;
        public static final int DATA_BACKUP_STATUS_ERROR_NONE = NOT_IMPLEMENTED;
        public static final int DATA_BACKUP_STATUS_ERROR_UNKNOWN = NOT_IMPLEMENTED;
        public static final int DATA_BACKUP_STATUS_ERROR_PARTIAL_BACKUP = NOT_IMPLEMENTED;
        public static final int DATA_BACKUP_STATUS_STARTED = NOT_IMPLEMENTED;
        // enums for SettingsBackupStatus
        public static final int SETTINGS_BACKUP_STATUS_ERROR_UNSPECIFIED = NOT_IMPLEMENTED;
        public static final int SETTINGS_BACKUP_STATUS_ERROR_NONE = NOT_IMPLEMENTED;
        public static final int SETTINGS_BACKUP_STATUS_ERROR_UNKNOWN = NOT_IMPLEMENTED;
        public static final int SETTINGS_BACKUP_STATUS_ERROR_COLLATION_FAILED = NOT_IMPLEMENTED;
        public static final int SETTINGS_BACKUP_STATUS_ERROR_PARTIAL_BACKUP = NOT_IMPLEMENTED;
        public static final int SETTINGS_BACKUP_STATUS_STARTED = NOT_IMPLEMENTED;
        // enums for DataRestoreStatus
        public static final int DATA_RESTORE_STATUS_ERROR_UNSPECIFIED = NOT_IMPLEMENTED;
        public static final int DATA_RESTORE_STATUS_ERROR_NONE = NOT_IMPLEMENTED;
        public static final int DATA_RESTORE_STATUS_ERROR_UNKNOWN = NOT_IMPLEMENTED;
        public static final int DATA_RESTORE_STATUS_ERROR_CONVERSION_FAILED = NOT_IMPLEMENTED;
        public static final int DATA_RESTORE_STATUS_ERROR_PARTIAL_RESTORE = NOT_IMPLEMENTED;
        public static final int DATA_RESTORE_STATUS_STARTED = NOT_IMPLEMENTED;
        // enums for SettingsRestoreStatus
        public static final int SETTINGS_RESTORE_STATUS_ERROR_UNSPECIFIED = NOT_IMPLEMENTED;
        public static final int SETTINGS_RESTORE_STATUS_ERROR_NONE = NOT_IMPLEMENTED;
        public static final int SETTINGS_RESTORE_STATUS_ERROR_UNKNOWN = NOT_IMPLEMENTED;
        public static final int SETTINGS_RESTORE_STATUS_ERROR_CONVERSION_FAILED = NOT_IMPLEMENTED;
        public static final int SETTINGS_RESTORE_STATUS_ERROR_PARTIAL_RESTORE = NOT_IMPLEMENTED;
        public static final int SETTINGS_RESTORE_STATUS_STARTED = NOT_IMPLEMENTED;

        @IntDef({
            DATA_BACKUP_STATUS_ERROR_UNSPECIFIED,
            DATA_BACKUP_STATUS_ERROR_NONE,
            DATA_BACKUP_STATUS_ERROR_UNKNOWN,
            DATA_BACKUP_STATUS_ERROR_PARTIAL_BACKUP,
            DATA_BACKUP_STATUS_STARTED
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface DataBackupState {}

        @IntDef({
            SETTINGS_BACKUP_STATUS_ERROR_UNSPECIFIED,
            SETTINGS_BACKUP_STATUS_ERROR_NONE,
            SETTINGS_BACKUP_STATUS_ERROR_UNKNOWN,
            SETTINGS_BACKUP_STATUS_ERROR_COLLATION_FAILED,
            SETTINGS_BACKUP_STATUS_ERROR_PARTIAL_BACKUP,
            SETTINGS_BACKUP_STATUS_STARTED
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface SettingsBackupState {}

        @IntDef({
            DATA_RESTORE_STATUS_ERROR_UNSPECIFIED,
            DATA_RESTORE_STATUS_ERROR_NONE,
            DATA_RESTORE_STATUS_ERROR_UNKNOWN,
            DATA_RESTORE_STATUS_ERROR_CONVERSION_FAILED,
            DATA_RESTORE_STATUS_ERROR_PARTIAL_RESTORE,
            DATA_RESTORE_STATUS_STARTED
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface DataRestoreState {}

        @IntDef({
            SETTINGS_RESTORE_STATUS_ERROR_UNSPECIFIED,
            SETTINGS_RESTORE_STATUS_ERROR_NONE,
            SETTINGS_RESTORE_STATUS_ERROR_UNKNOWN,
            SETTINGS_RESTORE_STATUS_ERROR_CONVERSION_FAILED,
            SETTINGS_RESTORE_STATUS_ERROR_PARTIAL_RESTORE,
            SETTINGS_RESTORE_STATUS_STARTED
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface SettingsRestoreState {}
    }

    /** The type of data backup being logged - either incremental or full. */
    public enum DataBackupType {
        INCREMENTAL_BACKUP,
        FULL_BACKUP
    }

    /** Log the data backup metrics. */
    public static void logDataBackupStatus(
            @BackupRestoreEnums.DataBackupState int dataBackupStatus,
            int timeToSucceedOrFailMillis,
            int dataSize,
            DataBackupType dataBackupType) {
        throw new UnsupportedOperationException();
    }

    /** Log the settings backup metrics. */
    public static void logSettingsBackupStatus(
            @BackupRestoreEnums.SettingsBackupState int settingsBackupStatus,
            int timeToSucceedOrFailMillis,
            int dataSize) {
        throw new UnsupportedOperationException();
    }

    /** Log the data restore metrics. */
    public static void logDataRestoreStatus(
            @BackupRestoreEnums.DataRestoreState int dataRestoreStatus,
            int timeToSucceedOrFailMillis,
            int dataSize) {
        throw new UnsupportedOperationException();
    }

    /** Log the settings restore metrics. */
    public static void logSettingsRestoreStatus(
            @BackupRestoreEnums.SettingsRestoreState int settingsRestoreStatus,
            int timeToSucceedOrFailMillis,
            int dataSize) {
        throw new UnsupportedOperationException();
    }

    /** Log the data restore eligibility metrics. */
    public static void logDataRestoreEligibility(boolean eligibility) {
        throw new UnsupportedOperationException();
    }
}
