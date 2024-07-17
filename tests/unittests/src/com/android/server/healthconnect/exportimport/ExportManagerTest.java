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

import static com.android.server.healthconnect.exportimport.ExportManager.LOCAL_EXPORT_DATABASE_FILE_NAME;
import static com.android.server.healthconnect.exportimport.ExportManager.LOCAL_EXPORT_DIR_NAME;
import static com.android.server.healthconnect.exportimport.ExportManager.LOCAL_EXPORT_ZIP_FILE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.HealthConnectManager;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.net.Uri;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@RunWith(AndroidJUnit4.class)
public class ExportManagerTest {
    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String REMOTE_EXPORT_DATABASE_DIR_NAME = "remote";
    private static final String REMOTE_EXPORT_ZIP_FILE_NAME = "remote_file.zip";
    private static final String REMOTE_EXPORT_DATABASE_FILE_NAME = "remote_file.db";

    @Rule
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private HealthConnectUserContext mContext;

    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private ExportManager mExportManager;
    private DatabaseContext mExportedDbContext;
    private Instant mTimeStamp;

    @Before
    public void setUp() throws Exception {
        mContext = mDatabaseTestRule.getUserContext();
        mTransactionManager = mDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(mContext, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);

        mTimeStamp = Instant.parse("2024-06-04T16:39:12Z");
        Clock fakeClock = Clock.fixed(mTimeStamp, ZoneId.of("UTC"));

        mExportManager = new ExportManager(mContext, fakeClock);

        mExportedDbContext =
                DatabaseContext.create(
                        mContext, REMOTE_EXPORT_DATABASE_DIR_NAME, mContext.getUser());
        configureExportUri();
    }

    @After
    public void tearDown() throws Exception {
        SQLiteDatabase.deleteDatabase(
                mExportedDbContext.getDatabasePath(REMOTE_EXPORT_DATABASE_FILE_NAME));
        mExportedDbContext.getDatabasePath(REMOTE_EXPORT_ZIP_FILE_NAME).delete();
    }

    @Test
    public void deletesAccessLogsTableContent() throws Exception {
        mTransactionTestUtils.insertAccessLog();
        mTransactionTestUtils.insertAccessLog();
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "access_logs_table", 2);

        assertThat(mExportManager.runExport()).isTrue();

        Compressor.decompress(
                Uri.fromFile(mExportedDbContext.getDatabasePath(REMOTE_EXPORT_ZIP_FILE_NAME)),
                LOCAL_EXPORT_DATABASE_FILE_NAME,
                mExportedDbContext.getDatabasePath(REMOTE_EXPORT_DATABASE_FILE_NAME),
                mContext);
        try (HealthConnectDatabase remoteExportHealthConnectDatabase =
                new HealthConnectDatabase(mExportedDbContext, REMOTE_EXPORT_DATABASE_FILE_NAME)) {
            assertTableSize(remoteExportHealthConnectDatabase, "access_logs_table", 0);
        }
    }

    @Test
    public void deletesChangeLogsTableContent() throws Exception {
        mTransactionTestUtils.insertChangeLog();
        mTransactionTestUtils.insertChangeLog();
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "change_logs_table", 2);

        assertThat(mExportManager.runExport()).isTrue();

        Compressor.decompress(
                Uri.fromFile(mExportedDbContext.getDatabasePath(REMOTE_EXPORT_ZIP_FILE_NAME)),
                LOCAL_EXPORT_DATABASE_FILE_NAME,
                mExportedDbContext.getDatabasePath(REMOTE_EXPORT_DATABASE_FILE_NAME),
                mContext);
        try (HealthConnectDatabase remoteExportHealthConnectDatabase =
                new HealthConnectDatabase(mExportedDbContext, REMOTE_EXPORT_DATABASE_FILE_NAME)) {
            assertTableSize(remoteExportHealthConnectDatabase, "change_logs_table", 0);
        }
    }

    @Test
    public void deletesLocalCopies() {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "steps_record_table", 1);

        assertThat(mExportManager.runExport()).isTrue();

        DatabaseContext databaseContext =
                DatabaseContext.create(mContext, LOCAL_EXPORT_DIR_NAME, mContext.getUser());
        assertThat(databaseContext.getDatabasePath(LOCAL_EXPORT_DATABASE_FILE_NAME).exists())
                .isFalse();
        assertThat(databaseContext.getDatabasePath(LOCAL_EXPORT_ZIP_FILE_NAME).exists()).isFalse();
    }

    @Test
    public void makesRemoteCopyOfDatabase() throws Exception {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "steps_record_table", 1);

        assertThat(mExportManager.runExport()).isTrue();

        Compressor.decompress(
                Uri.fromFile(mExportedDbContext.getDatabasePath(REMOTE_EXPORT_ZIP_FILE_NAME)),
                LOCAL_EXPORT_DATABASE_FILE_NAME,
                mExportedDbContext.getDatabasePath(REMOTE_EXPORT_DATABASE_FILE_NAME),
                mContext);
        try (HealthConnectDatabase remoteExportHealthConnectDatabase =
                new HealthConnectDatabase(mExportedDbContext, REMOTE_EXPORT_DATABASE_FILE_NAME)) {
            assertTableSize(remoteExportHealthConnectDatabase, "steps_record_table", 1);
        }
    }

    @Test
    public void destinationUriDoesNotExist_exportFails() {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "steps_record_table", 1);

        ExportImportSettingsStorage.setLastExportError(HealthConnectManager.DATA_EXPORT_ERROR_NONE);
        // Set export location to inaccessible directory.
        ExportImportSettingsStorage.configure(
                ScheduledExportSettings.withUri(Uri.fromFile(new File("inaccessible"))));

        assertThat(mExportManager.runExport()).isFalse();
        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getDataExportError())
                .isEqualTo(HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS);
    }

    @Test
    public void updatesLastSuccessfulExport_onSuccessOnly() {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "steps_record_table", 1);

        // running a successful export records a "last successful export"
        assertThat(mExportManager.runExport()).isTrue();
        Instant lastSuccessfulExport =
                ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                        .getLastSuccessfulExportTime();
        assertThat(lastSuccessfulExport).isEqualTo(Instant.parse("2024-06-04T16:39:12Z"));

        // Export running at a later time with an error
        mTimeStamp = Instant.parse("2024-12-12T16:39:12Z");
        ExportImportSettingsStorage.configure(
                ScheduledExportSettings.withUri(Uri.fromFile(new File("inaccessible"))));
        assertThat(mExportManager.runExport()).isFalse();

        // Last successful export should hold the previous timestamp as the last export failed
        assertThat(lastSuccessfulExport).isEqualTo(Instant.parse("2024-06-04T16:39:12Z"));
    }

    @Test
    public void updatesLastExportFileName_onSuccessOnly() {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "steps_record_table", 1);

        // Running a successful export records a "last successful export".
        assertThat(mExportManager.runExport()).isTrue();
        String lastExportFileName =
                ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                        .getLastExportFileName();
        assertThat(lastExportFileName).isEqualTo(REMOTE_EXPORT_ZIP_FILE_NAME);

        // Export running at a later time with an error
        ExportImportSettingsStorage.configure(
                ScheduledExportSettings.withUri(Uri.fromFile(new File("inaccessible"))));
        assertThat(mExportManager.runExport()).isFalse();

        // Last successful export should hold the previous file name as the last export failed
        assertThat(lastExportFileName).isEqualTo(REMOTE_EXPORT_ZIP_FILE_NAME);
    }

    private void configureExportUri() {
        ExportImportSettingsStorage.configure(
                ScheduledExportSettings.withUri(
                        Uri.fromFile(
                                (mExportedDbContext.getDatabasePath(
                                        REMOTE_EXPORT_ZIP_FILE_NAME)))));
    }

    private void assertTableSize(HealthConnectDatabase database, String tableName, int tableRows) {
        Cursor cursor =
                database.getWritableDatabase()
                        .rawQuery("SELECT count(*) FROM " + tableName + ";", null);
        cursor.moveToNext();
        assertThat(cursor.getInt(0)).isEqualTo(tableRows);
    }
}
