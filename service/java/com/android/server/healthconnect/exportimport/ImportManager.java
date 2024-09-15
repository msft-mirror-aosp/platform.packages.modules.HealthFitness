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

package com.android.server.healthconnect.exportimport;

import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_NONE;
import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_UNKNOWN;
import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_VERSION_MISMATCH;
import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_WRONG_FILE;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Manages import related tasks.
 *
 * @hide
 */
public final class ImportManager {

    @VisibleForTesting static final String IMPORT_DATABASE_DIR_NAME = "export_import";

    @VisibleForTesting static final String IMPORT_DATABASE_FILE_NAME = "health_connect_import.db";

    private static final String TAG = "HealthConnectImportManager";

    private final Context mContext;
    private final DatabaseMerger mDatabaseMerger;

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public ImportManager(@NonNull Context context) {
        requireNonNull(context);
        mContext = context;
        mDatabaseMerger = new DatabaseMerger(context);
    }

    /** Reads and merges the backup data from a local file. */
    public synchronized void runImport(UserHandle userHandle, Uri file) {
        Slog.i(TAG, "Import started.");
        ExportImportSettingsStorage.setImportOngoing(true);
        DatabaseContext dbContext =
                DatabaseContext.create(mContext, IMPORT_DATABASE_DIR_NAME, userHandle);
        File importDbFile = dbContext.getDatabasePath(IMPORT_DATABASE_FILE_NAME);

        try {
            try {
                Compressor.decompress(new File(file.getPath()), importDbFile);
                Slog.i(TAG, "Import file unzipped: " + importDbFile.getAbsolutePath());
            } catch (Exception e) {
                Slog.e(
                        TAG,
                        "Failed to get copy to destination: " + importDbFile.getAbsolutePath(),
                        e);
                ExportImportSettingsStorage.setLastImportError(DATA_IMPORT_ERROR_UNKNOWN);
                return;
            }

            try {
                if (canMerge(importDbFile)) {
                    HealthConnectDatabase stagedDatabase =
                            new HealthConnectDatabase(dbContext, IMPORT_DATABASE_FILE_NAME);
                    mDatabaseMerger.merge(stagedDatabase);
                }
            } catch (SQLiteException e) {
                Slog.i(TAG, "Import failed, not a database: " + e);
                ExportImportSettingsStorage.setLastImportError(DATA_IMPORT_ERROR_WRONG_FILE);
                return;
            } catch (IllegalStateException e) {
                Slog.i(TAG, "Import failed: " + e);
                ExportImportSettingsStorage.setLastImportError(DATA_IMPORT_ERROR_VERSION_MISMATCH);
                return;
            } catch (Exception e) {
                Slog.i(TAG, "Import failed: " + e);
                ExportImportSettingsStorage.setLastImportError(DATA_IMPORT_ERROR_UNKNOWN);
                return;
            }

            ExportImportSettingsStorage.setLastImportError(DATA_IMPORT_ERROR_NONE);
            Slog.i(TAG, "Import completed");
        } finally {
            // Delete the staged db as we are done merging.
            Slog.i(TAG, "Deleting staged db after merging");
            SQLiteDatabase.deleteDatabase(importDbFile);

            ExportImportSettingsStorage.setImportOngoing(false);
        }
    }

    private boolean canMerge(File importDbFile)
            throws FileNotFoundException, IllegalStateException, SQLiteException {
        int currentDbVersion = TransactionManager.getInitialisedInstance().getDatabaseVersion();
        if (importDbFile.exists()) {
            try (SQLiteDatabase importDb =
                    SQLiteDatabase.openDatabase(
                            importDbFile, new SQLiteDatabase.OpenParams.Builder().build())) {
                int stagedDbVersion = importDb.getVersion();
                Slog.i(
                        TAG,
                        "merging staged data, current version = "
                                + currentDbVersion
                                + ", staged version = "
                                + stagedDbVersion);
                if (currentDbVersion < stagedDbVersion) {
                    throw new IllegalStateException("Module needs upgrade for merging to version.");
                }
            }
        } else {
            throw new FileNotFoundException("No database file found to merge.");
        }

        Slog.i(TAG, "File can be merged.");
        return true;
    }
}
