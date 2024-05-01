/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.NonNull;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.android.server.healthconnect.storage.request.CreateTableRequest;

import java.io.File;

/**
 * Class to maintain the health connect DB. Actual operations are performed by {@link
 * TransactionManager}
 *
 * @hide
 */
public class HealthConnectDatabase extends SQLiteOpenHelper {
    private static final String TAG = "HealthConnectDatabase";

    private static final String DEFAULT_DATABASE_NAME = "healthconnect.db";
    private final Context mContext;

    public HealthConnectDatabase(@NonNull Context context) {
        this(context, DEFAULT_DATABASE_NAME);
    }

    public HealthConnectDatabase(@NonNull Context context, String databaseName) {
        super(context, databaseName, null, DatabaseUpgradeHelper.DATABASE_VERSION);
        mContext = context;
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        // We implement onCreate as a series of upgrades.
        onUpgrade(db, 0, DatabaseUpgradeHelper.DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        DatabaseUpgradeHelper.onUpgrade(db, this, oldVersion);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        // Enforce FK constraints for DB writes as we want to enforce FK constraints on DB write.
        // This is also required for when we delete entries, for cascade to work
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onDowngrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "onDowngrade oldVersion = " + oldVersion + " newVersion = " + newVersion);
    }

    public File getDatabasePath() {
        return mContext.getDatabasePath(getDatabaseName());
    }

    /** Runs create table request on database. */
    public static void createTable(SQLiteDatabase db, CreateTableRequest createTableRequest) {
        db.execSQL(createTableRequest.getCreateCommand());
        createTableRequest.getCreateIndexStatements().forEach(db::execSQL);
        for (CreateTableRequest childRequest : createTableRequest.getChildTableRequests()) {
            createTable(db, childRequest);
        }
    }
}
