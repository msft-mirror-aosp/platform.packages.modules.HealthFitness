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
import android.os.UserHandle;

import com.android.server.healthconnect.utils.FilesUtil;

import java.io.File;

/**
 * {@link Context} for the staged health connect db.
 *
 * @hide
 */
public final class DatabaseContext extends ContextWrapper {

    private final String mDatabaseDirName;

    private File mDatabaseDir;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    private DatabaseContext(
            @NonNull Context context, String databaseDirName, UserHandle userHandle) {
        super(context);
        requireNonNull(context);
        mDatabaseDirName = databaseDirName;
        setupForUser(userHandle);
    }

    /** Updates the DB directory */
    public void setupForUser(UserHandle userHandle) {
        File hcDirectory = FilesUtil.getDataSystemCeHCDirectoryForUser(userHandle.getIdentifier());
        mDatabaseDir = new File(hcDirectory, mDatabaseDirName);
        mDatabaseDir.mkdirs();
    }

    /** Returns the directory of the staged database */
    public File getDatabaseDir() {
        return mDatabaseDir;
    }

    /** Returns the file of the staged database with the given name */
    @Override
    public File getDatabasePath(String name) {
        return new File(mDatabaseDir, name);
    }

    /** Factory method */
    public static DatabaseContext create(
            @NonNull Context context, String databaseDirName, UserHandle userHandle) {
        return new DatabaseContext(context, databaseDirName, userHandle);
    }
}
