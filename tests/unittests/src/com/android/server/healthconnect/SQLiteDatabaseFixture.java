/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.healthconnect;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import static org.mockito.ArgumentMatchers.any;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.modules.utils.testing.StaticMockFixture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A fixture for use with {@link com.android.modules.utils.testing.ExtendedMockitoRule} that spies
 * {@link SQLiteDatabase#openDatabase(File, SQLiteDatabase.OpenParams)} and closes and deletes all
 * opened databases at the end of the test.
 */
public class SQLiteDatabaseFixture implements StaticMockFixture {

    private static final String TAG = "SQLiteDatabaseFixture";

    private final List<SQLiteDatabase> mOpenedDatabases = new ArrayList<>();

    @Override
    public StaticMockitoSessionBuilder setUpMockedClasses(
            StaticMockitoSessionBuilder sessionBuilder) {
        return sessionBuilder.spyStatic(SQLiteDatabase.class);
    }

    @Override
    public void setUpMockBehaviors() {
        doAnswer(
                        invocation -> {
                            SQLiteDatabase database = (SQLiteDatabase) invocation.callRealMethod();
                            mOpenedDatabases.add(database);
                            return database;
                        })
                .when(() -> SQLiteDatabase.openDatabase(any(), any()));
    }

    @Override
    public void tearDown() {
        for (SQLiteDatabase database : mOpenedDatabases) {
            while (database.isOpen()) {
                database.close();
            }
            File path = new File(database.getPath());
            if (path.exists()) {
                if (!SQLiteDatabase.deleteDatabase(path)) {
                    throw new IllegalStateException("Failed to delete " + path);
                }
                Log.v(TAG, "Deleted " + path);
            }
        }
    }
}
