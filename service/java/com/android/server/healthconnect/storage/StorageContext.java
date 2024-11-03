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

package com.android.server.healthconnect.storage;

import android.annotation.Nullable;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.os.UserHandle;
import android.util.Slog;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.utils.FilesUtil;

import java.io.File;

/**
 * {@link Context} for accessing storage directory for Health Connect.
 *
 * <p>It creates a different directory per user on the device, and provides data separation between
 * users.
 *
 * <p>This is used to initialize {@link HealthConnectDatabase} to perform database operations.
 *
 * <p>Use cases that require creating an additional database (e.g. D2D, export/import) can pass in a
 * sub-directory to create the database in that directory.
 *
 * @hide
 */
public final class StorageContext extends ContextWrapper {

    private static final String TAG = "HealthConnectDatabaseContext";

    private final File mDatabaseDir;

    private StorageContext(
            Context context, UserHandle userHandle, @Nullable String databaseDirName) {
        super(context.createContextAsUser(userHandle, 0));

        if (databaseDirName == null) {
            mDatabaseDir = FilesUtil.getDataSystemCeHCDirectoryForUser(userHandle.getIdentifier());
        } else {
            File hcDirectory =
                    FilesUtil.getDataSystemCeHCDirectoryForUser(userHandle.getIdentifier());
            mDatabaseDir = new File(hcDirectory, databaseDirName);
        }
    }

    /**
     * Returns the data directory where files are stored.
     *
     * <p>HealthConnect stores files in the directory returned by this method, and doesn't use the
     * files and cache sub-directories.
     */
    @Override
    public File getDataDir() {
        mDatabaseDir.mkdirs();
        return mDatabaseDir;
    }

    @Override
    public boolean deleteDatabase(String name) {
        if (Flags.d2dFileDeletionBugFix()) {
            try {
                File f = getDatabasePath(name);
                return SQLiteDatabase.deleteDatabase(f);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to delete database = " + getDatabasePath(name));
            }
            return false;
        } else {
            return super.deleteDatabase(name);
        }
    }

    /** Returns the file of the staged database with the given name */
    @Override
    public File getDatabasePath(String name) {
        return new File(getDataDir(), name);
    }

    /** Factory method */
    public static StorageContext create(Context context, UserHandle userHandle) {
        return create(context, userHandle, null);
    }

    /** Factory method */
    public static StorageContext create(
            Context context, UserHandle userHandle, @Nullable String databaseDirName) {
        return new StorageContext(context, userHandle, databaseDirName);
    }
}
