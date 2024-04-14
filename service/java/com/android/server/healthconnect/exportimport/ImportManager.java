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
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.utils.FilesUtil;

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

    private static final String TAG = "HealthConnectImportManager";
    private static final String STAGED_DATABASE_NAME = "health_connect_import.db";

    private final StagedDatabaseContext mStagedDbContext;
    private final DatabaseMerger mDatabaseMerger;

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public ImportManager(@NonNull Context context) {
        requireNonNull(context);
        mStagedDbContext = new StagedDatabaseContext(context, context.getUser());
        mDatabaseMerger = new DatabaseMerger(context);
    }

    /** Updates import DB location. */
    public void onUserSwitching(UserHandle currentForegroundUser) {
        mStagedDbContext.setupForUser(currentForegroundUser);
    }

    /** Reads and merges the backup data from a local file. */
    public void runImport(Path pathToImport) {
        File stagedDbFile = getDbFile();
        try {
            Path destinationPath = FileSystems.getDefault().getPath(stagedDbFile.getAbsolutePath());
            Files.copy(pathToImport, destinationPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SecurityException e) {
            Slog.e(TAG, "Failed to get copy to destination: " + stagedDbFile.getName(), e);
            stagedDbFile.delete();
            return;
        }

        if (canMerge(stagedDbFile)) {
            HealthConnectDatabase stagedDatabase =
                    new HealthConnectDatabase(mStagedDbContext, STAGED_DATABASE_NAME);
            mDatabaseMerger.merge(stagedDatabase);
        }

        // Delete the staged db as we are done merging.
        Slog.i(TAG, "Deleting staged db after merging.");
        mStagedDbContext.deleteDatabase(STAGED_DATABASE_NAME);
        stagedDbFile.delete();
    }

    private boolean canMerge(File stagedDbFile) {
        int currentDbVersion = TransactionManager.getInitialisedInstance().getDatabaseVersion();
        if (stagedDbFile.exists()) {
            try (SQLiteDatabase stagedDb =
                    SQLiteDatabase.openDatabase(
                            stagedDbFile, new SQLiteDatabase.OpenParams.Builder().build())) {
                int stagedDbVersion = stagedDb.getVersion();
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

    @VisibleForTesting
    public File getDbFile() {
        return mStagedDbContext.getDatabasePath(STAGED_DATABASE_NAME);
    }

    /**
     * {@link Context} for the staged health connect db.
     *
     * @hide
     */
    private static final class StagedDatabaseContext extends ContextWrapper {

        private File mDatabaseDir;

        @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
        StagedDatabaseContext(@NonNull Context context, UserHandle userHandle) {
            super(context);
            requireNonNull(context);
            setupForUser(userHandle);
        }

        public void setupForUser(UserHandle userHandle) {
            File hcDirectory =
                    FilesUtil.getDataSystemCeHCDirectoryForUser(userHandle.getIdentifier());
            mDatabaseDir = new File(hcDirectory, "remote_import");
            mDatabaseDir.mkdirs();
        }

        @Override
        public File getDatabasePath(String name) {
            return new File(mDatabaseDir, name);
        }
    }
}
