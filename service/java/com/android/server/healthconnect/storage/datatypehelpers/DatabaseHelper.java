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

import android.annotation.NonNull;

import com.android.server.healthconnect.migration.PriorityMigrationHelper;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;

import java.util.Set;

/**
 * Parent class for the database helper classes containing common methods
 *
 * @hide
 */
public abstract class DatabaseHelper {

    private static Set<DatabaseHelper> getDatabaseHelpers() {
        return Set.of(
                DeviceInfoHelper.getInstance(),
                AppInfoHelper.getInstance(),
                new ActivityDateHelper(),
                new ChangeLogsHelper(),
                new ChangeLogsRequestHelper(),
                HealthDataCategoryPriorityHelper.getInstance(),
                PreferenceHelper.getInstance(),
                new AccessLogsHelper(),
                new MigrationEntityHelper(),
                PriorityMigrationHelper.getInstance());
    }

    /**
     * Deletes all entries from the database for the helper class and clears the cache. This
     * function is only used for testing, do not use in production.
     */
    public static void clearAllData(@NonNull TransactionManager transactionManager) {
        for (DatabaseHelper databaseHelper : getDatabaseHelpers()) {
            databaseHelper.clearData(transactionManager);
        }
        clearAllCache();
    }

    public static void clearAllCache() {
        for (DatabaseHelper databaseHelper : getDatabaseHelpers()) {
            databaseHelper.clearCache();
        }
    }

    protected void clearData(@NonNull TransactionManager transactionManager) {
        transactionManager.delete(new DeleteTableRequest(getMainTableName()));
    }

    protected void clearCache() {}

    protected abstract String getMainTableName();
}
