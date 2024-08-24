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

import static com.android.healthfitness.flags.Flags.FLAG_DEVELOPMENT_DATABASE;
import static com.android.server.healthconnect.storage.DatabaseTestUtils.createEmptyDatabase;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.request.ReadTableRequest;

import com.google.common.base.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class DevelopmentDatabaseHelperTest {

    private static final SQLiteDatabase.OpenParams READ_ONLY =
            new SQLiteDatabase.OpenParams.Builder()
                    .setOpenFlags(SQLiteDatabase.OPEN_READONLY)
                    .build();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock Context mContext;

    private File mDatabasePath;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mDatabasePath = getMockDatabasePath();
        DatabaseTestUtils.clearDatabase(mDatabasePath);
        when(mContext.getDatabasePath(anyString())).thenReturn(mDatabasePath);
    }

    @After
    public void clearDatabase() {
        DatabaseTestUtils.clearDatabase(getMockDatabasePath());
    }

    private static File getMockDatabasePath() {
        return InstrumentationRegistry.getInstrumentation().getContext().getDatabasePath("mock");
    }

    @Test
    @EnableFlags(FLAG_DEVELOPMENT_DATABASE)
    public void testOnOpen_readOnlyDatabase_successful() {
        // GIVEN we have a guaranteed read only database.
        try (HealthConnectDatabase helper = new HealthConnectDatabase(mContext)) {
            // make sure a database file exists
            helper.getWritableDatabase();
        }
        // Change it to read only
        Preconditions.checkState(mDatabasePath.setReadOnly());
        // Check the above code works
        Preconditions.checkState(mDatabasePath.canRead());
        Preconditions.checkState(!mDatabasePath.canWrite());
        try (SQLiteDatabase readOnlyDatabase =
                SQLiteDatabase.openDatabase(mDatabasePath, READ_ONLY)) {
            Preconditions.checkState(readOnlyDatabase.isReadOnly());

            // WHEN we call onOpen on the read only database THEN there are no errors.
            DevelopmentDatabaseHelper.onOpen(readOnlyDatabase);
        }
    }

    @Test
    @EnableFlags(FLAG_DEVELOPMENT_DATABASE)
    public void testGetOldVersionIfExists_nonExistent() {
        try (SQLiteDatabase db = createEmptyDatabase()) {

            int version = DevelopmentDatabaseHelper.getOldVersionIfExists(db);

            assertThat(version).isEqualTo(DevelopmentDatabaseHelper.NO_DEV_VERSION);
        }
    }

    @Test
    @EnableFlags(FLAG_DEVELOPMENT_DATABASE)
    public void testDropAndCreateDevelopmentSettings_nonExistent_creates() {
        try (SQLiteDatabase db = createEmptyDatabase()) {
            int version = 26;

            DevelopmentDatabaseHelper.dropAndCreateDevelopmentSettingsTable(db, version);

            assertThat(DevelopmentDatabaseHelper.getOldVersionIfExists(db)).isEqualTo(version);
        }
    }

    @Test
    @EnableFlags(FLAG_DEVELOPMENT_DATABASE)
    public void testDropAndCreateDevelopmentSettings_existent_overwrites() {
        try (HealthConnectDatabase helper = new HealthConnectDatabase(mContext)) {
            // getWriteableDatabase() triggers onOpen(), so the dev database with
            // version CURRENT_VERSION should be created.
            SQLiteDatabase db = helper.getWritableDatabase();
            int version = 26;

            DevelopmentDatabaseHelper.dropAndCreateDevelopmentSettingsTable(db, version);

            assertThat(DevelopmentDatabaseHelper.getOldVersionIfExists(db)).isEqualTo(version);
        }
    }

    @Test
    @DisableFlags(FLAG_DEVELOPMENT_DATABASE)
    public void testOnOpen_notDevelopment_deletesDevelopmentTables() {
        try (HealthConnectDatabase helper = new HealthConnectDatabase(mContext)) {
            // Calling getWritableDatabase() triggers onOpen(). With the flag off,
            // should delete the development database, and not create the PHR one.
            SQLiteDatabase db = helper.getWritableDatabase();
            // Now the development database should not be present.
            // Create a table that looks like some old development settings.
            // GIVEN we have some old development database settings
            DevelopmentDatabaseHelper.dropAndCreateDevelopmentSettingsTable(
                    db, DevelopmentDatabaseHelper.CURRENT_VERSION);

            // WHEN onOpen is called
            DevelopmentDatabaseHelper.onOpen(db);

            // THEN the settings table should be deleted
            assertThat(DevelopmentDatabaseHelper.getOldVersionIfExists(db))
                    .isEqualTo(DevelopmentDatabaseHelper.NO_DEV_VERSION);
        }
    }

    @Test
    @EnableFlags(FLAG_DEVELOPMENT_DATABASE)
    public void testOnOpen_isDevelopmentHasDevelopmentTables_noChange() {
        // Make the bad condition that the development settings version looks good, but the PHR
        // database has not been created.
        // GIVEN we have some current development database settings, and the flags are enabled
        try (SQLiteDatabase db = createEmptyDatabase()) {
            DevelopmentDatabaseHelper.dropAndCreateDevelopmentSettingsTable(
                    db, DevelopmentDatabaseHelper.CURRENT_VERSION);

            // WHEN onOpen is called
            DevelopmentDatabaseHelper.onOpen(db);

            // THEN the settings table should be left, and nothing changed (ie PHR not created)
            assertThat(DevelopmentDatabaseHelper.getOldVersionIfExists(db))
                    .isEqualTo(DevelopmentDatabaseHelper.CURRENT_VERSION);
            // Check PHR not created
            assertThrows(
                    SQLException.class,
                    () -> {
                        usePhrDataSourceTable(db);
                    });
        }
    }

    @Test
    @EnableFlags(FLAG_DEVELOPMENT_DATABASE)
    public void testOnOpen_oldDevelopmentSettingsTable_createsNew() {
        try (SQLiteDatabase db = createEmptyDatabase()) {
            DevelopmentDatabaseHelper.dropAndCreateDevelopmentSettingsTable(
                    db, DevelopmentDatabaseHelper.CURRENT_VERSION - 1);
            // We need to create access_logs_table, since we are altering the table
            // in onOpen.
            HealthConnectDatabase.createTable(db, AccessLogsHelper.getCreateTableRequest());

            DevelopmentDatabaseHelper.onOpen(db);

            assertThat(DevelopmentDatabaseHelper.getOldVersionIfExists(db))
                    .isEqualTo(DevelopmentDatabaseHelper.CURRENT_VERSION);
            // Check PHR is created
            usePhrDataSourceTable(db);
            usePhrAccessLogsColumns(db);
        }
    }

    /**
     * Check that the PHR tables were created on a database by attempting to read a random UUID
     * datasource. If the table doesn't exist we expect an SQLException, if the table does exist
     * nothing should happen.
     */
    private static void usePhrDataSourceTable(SQLiteDatabase db) {
        UUID uuid = UUID.randomUUID();
        ReadTableRequest request =
                MedicalDataSourceHelper.getReadTableRequest(List.of(uuid.toString()));
        try (Cursor cursor = db.rawQuery(request.getReadCommand(), new String[] {})) {
            assertThat(cursor.getCount()).isEqualTo(0);
        }
    }

    /**
     * Check that the PHR columns for access logs were added to the {@link
     * AccessLogsHelper#TABLE_NAME}. If the columns don't exist we expect an SQLException, if the
     * columns do exist nothing should happen.
     */
    private static void usePhrAccessLogsColumns(SQLiteDatabase db) {
        try (Cursor cursor =
                db.rawQuery(
                        "SELECT medical_resource_type, medical_data_source_accessed FROM"
                                + " access_logs_table",
                        new String[] {})) {
            assertThat(cursor.getCount()).isEqualTo(0);
        }
    }
}
