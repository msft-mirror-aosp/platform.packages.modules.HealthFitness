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
 * {@link Context} for storing, accessing and deleting a user database in HealthConnect.
 *
 * <p>By default, the database is created in the top level health connect directory in storage. But
 * other use cases that create a copy of the database (e.g. D2D, export/import) can pass in a
 * directory to create a database in a sub-directory.
 *
 * @hide
 */
public final class DatabaseContext extends ContextWrapper {

    private static final String TAG = "HealthConnectDatabaseContext";

    private final UserHandle mUserHandle;
    private final File mDatabaseDir;

    public DatabaseContext(Context context, UserHandle userHandle) {
        this(context, userHandle, null);
    }

    public DatabaseContext(
            Context context, UserHandle userHandle, @Nullable String databaseDirName) {
        super(context.createContextAsUser(userHandle, 0));
        mUserHandle = userHandle;

        if (databaseDirName == null) {
            mDatabaseDir = FilesUtil.getDataSystemCeHCDirectoryForUser(userHandle.getIdentifier());
        } else {
            File hcDirectory =
                    FilesUtil.getDataSystemCeHCDirectoryForUser(userHandle.getIdentifier());
            mDatabaseDir = new File(hcDirectory, databaseDirName);
        }
        mDatabaseDir.mkdirs();
    }

    /** Returns the directory in which the database is stored */
    public File getDatabaseDir() {
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
        return new File(mDatabaseDir, name);
    }

    /** Factory method */
    public static DatabaseContext create(
            Context context, @Nullable String databaseDirName, UserHandle userHandle) {
        return new DatabaseContext(context, userHandle, databaseDirName);
    }
}
