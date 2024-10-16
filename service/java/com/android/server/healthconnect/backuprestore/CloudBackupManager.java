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
package com.android.server.healthconnect.backuprestore;

import static com.android.healthfitness.flags.Flags.FLAG_CLOUD_BACKUP_AND_RESTORE;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.backuprestore.GetChangesForBackupResponse;
import android.health.connect.backuprestore.GetSettingsForBackupResponse;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;

/**
 * Manages Cloud Backup operations.
 *
 * @hide
 */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
public final class CloudBackupManager {

    private final BackupRestoreDatabaseHelper mDatabaseHelper;

    public CloudBackupManager(
            TransactionManager transactionManager,
            AppInfoHelper appInfoHelper,
            AccessLogsHelper accessLogsHelper,
            DeviceInfoHelper deviceInfoHelper) {
        mDatabaseHelper =
                new BackupRestoreDatabaseHelper(
                        transactionManager, appInfoHelper, accessLogsHelper, deviceInfoHelper);
    }

    /**
     * The changeToken returned by the previous call should be passed in to resume the upload. A
     * null or empty changeToken means we are doing a fresh backup, and should start from the
     * beginning.
     *
     * <p>If the changeToken is not found, it means that HealthConnect can no longer resume the
     * backup from this point, and will respond with an Exception. The caller should restart the
     * backup in this case.
     *
     * <p>If no changes are returned by the API, this means that the client has synced all changes
     * as of now.
     */
    @NonNull
    public GetChangesForBackupResponse getChangesForBackup(@Nullable String changeToken) {
        if (changeToken != null) {
            // TODO: b/369799948 - handles the case when still reading records from data tables.
            throw new UnsupportedOperationException();
        }
        // TODO: b/369799948 - add proper next change token.
        return new GetChangesForBackupResponse(
                mDatabaseHelper.getChangesFromDataTables(), "placeHolderPageToken");
    }

    /** Returns all user settings bundled as a single byte array. */
    @NonNull
    public GetSettingsForBackupResponse getSettingsForBackup() {
        throw new UnsupportedOperationException();
    }
}
