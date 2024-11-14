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

package com.android.server.healthconnect.storage.datatypehelpers;

import android.annotation.Nullable;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;

import com.android.server.healthconnect.storage.TransactionManager;

import java.util.Collection;

/**
 * Helper class for getting statistics about database table size.
 *
 * @hide
 */
public class TableSizeHelper {

    private static final long NO_DATA = -1;
    private final TransactionManager mTransactionManager;

    public TableSizeHelper(TransactionManager transactionManager) {
        this.mTransactionManager = transactionManager;
    }

    /**
     * Reads the total number of bytes of disk used to store the given tables. See <a
     * href="https://sqlite.org/dbstat.html">SQLite {@code dbstat} documentation</a>.
     *
     * @param tables a collection of table names to add together
     * @return the total number of bytes used to store those tables, or null if this information
     *     cannot be read
     */
    @Nullable
    public Long getFileBytes(Collection<String> tables) {
        if (tables.isEmpty()) {
            return 0L;
        }
        StringBuilder sql = new StringBuilder("SELECT SUM(pgsize) FROM dbstat WHERE name IN (");
        String[] args = new String[tables.size()];
        int index = 0;
        for (String table : tables) {
            sql.append("?,");
            args[index++] = table;
        }
        sql.setCharAt(sql.length() - 1, ')');
        try {
            long bytes =
                    mTransactionManager.runAsTransaction(
                            db -> {
                                try (Cursor cursor = db.rawQuery(sql.toString(), args)) {
                                    if (!cursor.moveToFirst()) {
                                        return NO_DATA;
                                    }
                                    return cursor.getLong(0);
                                }
                            });
            if (bytes == NO_DATA) {
                return null;
            }
            return bytes;
        } catch (SQLiteException e) {
            // This can happen if the dbstat table does not exist. If so, carry on.
            return null;
        }
    }
}
