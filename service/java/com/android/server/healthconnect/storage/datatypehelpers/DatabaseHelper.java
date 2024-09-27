/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.healthconnect.storage.datatypehelpers;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * Parent class for the database helper classes containing common methods
 *
 * @hide
 */
public abstract class DatabaseHelper {

    private static final Set<DatabaseHelper> sDatabaseHelpers = new HashSet<>();

    protected DatabaseHelper() {
        sDatabaseHelpers.add(this);
    }

    /**
     * Deletes all entries from the database and clears the cache for all the helper class.
     *
     * <p>This function is only used for testing, do not use in production.
     */
    public static void clearAllData(TransactionManager transactionManager) {
        for (DatabaseHelper databaseHelper : sDatabaseHelpers) {
            databaseHelper.clearData(transactionManager);
        }
    }

    public static void clearAllCache() {
        for (DatabaseHelper databaseHelper : sDatabaseHelpers) {
            databaseHelper.clearCache();
        }
    }

    /** Deletes all entries from the database and clears the cache for the helper class. */
    public final synchronized void clearData(TransactionManager transactionManager) {
        transactionManager.delete(new DeleteTableRequest(getMainTableName()));
        clearCache();
    }

    protected void clearCache() {}

    protected abstract String getMainTableName();
}
