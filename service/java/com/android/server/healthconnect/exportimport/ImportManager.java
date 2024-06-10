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



import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
        Slog.i(TAG, "Import started");
        DatabaseContext dbContext =
                DatabaseContext.create(mContext, IMPORT_DATABASE_DIR_NAME, userHandle);

        File importDbFile = dbContext.getDatabasePath(IMPORT_DATABASE_FILE_NAME);
        importDbFile.mkdirs();
        try {
            Path filePath = FileSystems.getDefault().getPath(file.getPath());
            Path destinationPath = FileSystems.getDefault().getPath(importDbFile.getAbsolutePath());
            Files.copy(filePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SecurityException e) {
            Slog.e(TAG, "Failed to get copy to destination: " + importDbFile.getName(), e);
            importDbFile.delete();
            return;
        }

        if (canMerge(importDbFile)) {
            HealthConnectDatabase stagedDatabase =
                    new HealthConnectDatabase(dbContext, IMPORT_DATABASE_FILE_NAME);
            mDatabaseMerger.merge(stagedDatabase);
        }

        // Delete the staged db as we are done merging.
        Slog.i(TAG, "Deleting staged db after merging");
        dbContext.deleteDatabase(IMPORT_DATABASE_FILE_NAME);
        importDbFile.delete();

        Slog.i(TAG, "Import completed");
    }

    private boolean canMerge(File importDbFile) {
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
                    Slog.i(TAG, "Module needs upgrade for merging to version " + stagedDbVersion);
                    return false;
                }
            }
        } else {
            Slog.i(TAG, "No database file found to merge.");
            return false;
        }

        Slog.i(TAG, "Starting the data merge.");
        return true;
    }
}
