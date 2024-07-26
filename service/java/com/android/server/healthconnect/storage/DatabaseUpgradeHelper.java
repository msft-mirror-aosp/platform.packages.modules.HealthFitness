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

import static com.android.server.healthconnect.storage.datatypehelpers.PlannedExerciseSessionRecordHelper.PLANNED_EXERCISE_SESSION_RECORD_TABLE_NAME;

import android.annotation.NonNull;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.migration.PriorityMigrationHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ExerciseSessionRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper;
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
    public static final int DB_VERSION_UUID_BLOB = 9;
    public static final int DB_VERSION_GENERATED_LOCAL_TIME = 10;
    public static final int DB_VERSION_SKIN_TEMPERATURE = 11;
    public static final int DB_VERSION_PLANNED_EXERCISE_SESSIONS = 12;
    // No schema changes between version 12 and 13. See ag/26747988 for more details.
    public static final int DB_VERSION_PLANNED_EXERCISE_SESSIONS_FLAG_RELEASE = 13;
    public static final int DB_VERSION_MINDFULNESS_SESSION = 14;

    // TODO(b/346981687): increment db version for PHR, once done with development.

    /**
     * A shared DB version to guard all schema changes of under development features in HC.
     *
     * <p>See more at go/hc-aconfig-and-db
     */
    private static final int DB_VERSION_UNDER_DEVELOPMENT = 1_000_000;

    private static final String SQLITE_MASTER_TABLE_NAME = "sqlite_master";

    // Whenever we are bumping the database version, take a look at potential problems described in:
    // go/hc-handling-database-upgrades.
    // This value is used to update the database to the latest version. Update this to the latest
    // version that we want to upgrade the database to.
    // This has to be a static method rather than a static field, otherwise the value of the static
    // field would be calculated when the class is loaded which makes testing different scenarios
    // with different values very difficult. See this chat:
    // https://chat.google.com/room/AAAAl1xxgQM/uokEORpq24c.
    static int getDatabaseVersion() {
        return Flags.personalHealthRecordDatabase()
                ? DB_VERSION_UNDER_DEVELOPMENT
                : DB_VERSION_MINDFULNESS_SESSION;
    }

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

        if (oldVersion < DB_VERSION_UNDER_DEVELOPMENT
                && DB_VERSION_UNDER_DEVELOPMENT <= newVersion) {
            if (Flags.personalHealthRecordDatabase()) {
                MedicalDataSourceHelper.onInitialUpgrade(db);
                MedicalResourceHelper.onInitialUpgrade(db);
            }
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
