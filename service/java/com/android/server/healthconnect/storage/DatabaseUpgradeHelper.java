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

import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_MINDFULNESS_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_PLANNED_EXERCISE_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_UNKNOWN;

import static com.android.healthfitness.flags.DatabaseVersions.DB_VERSION_GENERATED_LOCAL_TIME;
import static com.android.healthfitness.flags.DatabaseVersions.DB_VERSION_MINDFULNESS_SESSION;
import static com.android.healthfitness.flags.DatabaseVersions.DB_VERSION_PLANNED_EXERCISE_SESSIONS;
import static com.android.healthfitness.flags.DatabaseVersions.DB_VERSION_SKIN_TEMPERATURE;
import static com.android.healthfitness.flags.DatabaseVersions.DB_VERSION_UUID_BLOB;
import static com.android.server.healthconnect.storage.datatypehelpers.PlannedExerciseSessionRecordHelper.PLANNED_EXERCISE_SESSION_RECORD_TABLE_NAME;

import android.annotation.NonNull;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.android.server.healthconnect.migration.PriorityMigrationHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ExerciseSessionRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MigrationEntityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MindfulnessSessionRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PlannedExerciseSessionRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.SkinTemperatureRecordHelper;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DropTableRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Class that contains all database upgrades. */
final class DatabaseUpgradeHelper {
    private static final String SQLITE_MASTER_TABLE_NAME = "sqlite_master";

    /**
     * The method creates the initial set of tables in the database, and then applies each upgrade
     * one after the other.
     *
     * <p>Keep the upgrades idempotent, since module rollbacks can mean that some upgrades are
     * applied twice.
     *
     * <p>See go/hc-handling-database-upgrades for things to be taken care of when upgrading.
     */
    static void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Note: This first upgrade is not idempotent since it only drops the set of initial tables.
        // Some tables are left around, which can break foreign key constraints.
        if (oldVersion < DB_VERSION_UUID_BLOB) {
            // Only drop the tables if the db existed beforehand.
            if (oldVersion > 0) {
                dropInitialSetOfTables(db);
            }
            createInitialSetOfTables(db);
        }

        if (oldVersion < DB_VERSION_GENERATED_LOCAL_TIME) {
            forEachRecordHelper(it -> it.applyGeneratedLocalTimeUpgrade(db));
        }
        if (oldVersion < DB_VERSION_SKIN_TEMPERATURE) {
            DatabaseUpgradeHelper.<SkinTemperatureRecordHelper>getRecordHelper(
                            RECORD_TYPE_SKIN_TEMPERATURE)
                    .applySkinTemperatureUpgrade(db);
        }

        if (oldVersion < DB_VERSION_PLANNED_EXERCISE_SESSIONS) {
            applyPlannedExerciseDatabaseUpgrade(db);
        }
        if (oldVersion < DB_VERSION_MINDFULNESS_SESSION) {
            MindfulnessSessionRecordHelper mindfulnessRecordHelper =
                    getRecordHelper(RECORD_TYPE_MINDFULNESS_SESSION);
            mindfulnessRecordHelper.applyMindfulnessSessionUpgrade(db);
        }
    }

    private static void createInitialSetOfTables(@NonNull SQLiteDatabase db) {
        for (CreateTableRequest createTableRequest : getInitialCreateTableRequests()) {
            HealthConnectDatabase.createTable(db, createTableRequest);
        }
    }

    private static void dropInitialSetOfTables(SQLiteDatabase db) {
        List<String> allTables =
                getInitialCreateTableRequests().stream()
                        .map(CreateTableRequest::getTableName)
                        .toList();
        for (String table : allTables) {
            db.execSQL(new DropTableRequest(table).getDropTableCommand());
        }
    }

    private static List<CreateTableRequest> getInitialCreateTableRequests() {
        List<CreateTableRequest> requests = new ArrayList<>();

        // Add all records that were part of the initial schema.
        Map<Integer, RecordHelper<?>> recordHelperMap = RecordHelperProvider.getRecordHelpers();
        recordHelperMap.entrySet().stream()
                .filter(
                        entry ->
                                entry.getKey() > RECORD_TYPE_UNKNOWN
                                        && entry.getKey() < RECORD_TYPE_SKIN_TEMPERATURE)
                .forEach(entry -> requests.add(entry.getValue().getCreateTableRequest()));

        requests.add(DeviceInfoHelper.getCreateTableRequest());
        requests.add(AppInfoHelper.getCreateTableRequest());
        requests.add(ActivityDateHelper.getCreateTableRequest());
        requests.add(ChangeLogsHelper.getCreateTableRequest());
        requests.add(ChangeLogsRequestHelper.getCreateTableRequest());
        requests.add(HealthDataCategoryPriorityHelper.getCreateTableRequest());
        requests.add(PreferenceHelper.getCreateTableRequest());
        requests.add(AccessLogsHelper.getCreateTableRequest());
        requests.add(MigrationEntityHelper.getCreateTableRequest());
        requests.add(PriorityMigrationHelper.getCreateTableRequest());

        return requests;
    }

    private static void forEachRecordHelper(Consumer<RecordHelper<?>> action) {
        RecordHelperProvider.getRecordHelpers().values().forEach(action);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getRecordHelper(int recordTypeIdentifier) {
        return (T) RecordHelperProvider.getRecordHelper(recordTypeIdentifier);
    }

    private static void applyPlannedExerciseDatabaseUpgrade(SQLiteDatabase db) {
        if (doesTableAlreadyExist(db, PLANNED_EXERCISE_SESSION_RECORD_TABLE_NAME)) {
            // Upgrade has already been applied. Return early.
            return;
        }
        PlannedExerciseSessionRecordHelper recordHelper =
                getRecordHelper(RECORD_TYPE_PLANNED_EXERCISE_SESSION);
        HealthConnectDatabase.createTable(db, recordHelper.getCreateTableRequest());
        db.execSQL(
                recordHelper
                        .getAlterTableRequestForPlannedExerciseFeature()
                        .getAlterTableAddColumnsCommand());
        ExerciseSessionRecordHelper exerciseRecordHelper =
                getRecordHelper(RECORD_TYPE_EXERCISE_SESSION);
        db.execSQL(
                exerciseRecordHelper
                        .getAlterTableRequestForPlannedExerciseFeature()
                        .getAlterTableAddColumnsCommand());
    }

    private static boolean doesTableAlreadyExist(SQLiteDatabase db, String tableName) {
        long numEntries =
                DatabaseUtils.queryNumEntries(
                        db,
                        SQLITE_MASTER_TABLE_NAME,
                        /* selection= */ "type = 'table' AND name == '" + tableName + "'",
                        /* selectionArgs= */ null);
        return numEntries > 0;
    }
}
