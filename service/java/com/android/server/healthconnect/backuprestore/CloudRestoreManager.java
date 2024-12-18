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
import android.health.connect.backuprestore.BackupChange;
import android.health.connect.backuprestore.BackupSettings;
import android.util.Slog;

import java.util.List;

/**
 * Manages Cloud Restore operations.
 *
 * @hide
 */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
public class CloudRestoreManager {

    private static final String TAG = "CloudRestoreManager";

    public CloudRestoreManager() {}

    /** Takes the serialized user settings and overwrites existing settings. */
    public void pushSettingsForRestore(BackupSettings newSettings) {
        Slog.i(TAG, "Restoring user settings.");
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /** Checks whether data with a certain version could be restored. */
    public boolean canRestore(int dataVersion) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    /** Restores backup data changes. */
    public void pushChangesForRestore(List<BackupChange> changes) {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
