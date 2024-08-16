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

import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_ERROR_NONE;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_ERROR_UNKNOWN;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_STARTED;

import static com.android.server.healthconnect.exportimport.ExportManager.LOCAL_EXPORT_DATABASE_FILE_NAME;
import static com.android.server.healthconnect.exportimport.ExportManager.LOCAL_EXPORT_DIR_NAME;
import static com.android.server.healthconnect.exportimport.ExportManager.LOCAL_EXPORT_ZIP_FILE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.HealthConnectManager;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.health.connect.exportimport.ScheduledExportStatus;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.net.Uri;
import android.os.Environment;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.logging.ExportImportLogger;
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
import org.mockito.quality.Strictness;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

// TODO: b/357864927 - Add tests for export file size logging.
@RunWith(AndroidJUnit4.class)
public class ExportManagerTest {
    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String REMOTE_EXPORT_DATABASE_DIR_NAME = "remote";
    private static final String REMOTE_EXPORT_ZIP_FILE_NAME = "remote_file.zip";
    private static final String REMOTE_EXPORT_DATABASE_FILE_NAME = "remote_file.db";

    @Rule(order = 1)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .setStrictness(Strictness.LENIENT)
                    .mockStatic(HealthConnectDeviceConfigManager.class)
                    .mockStatic(ExportImportLogger.class)
                    .build();

    @Rule(order = 2)
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private HealthConnectUserContext mContext;
    private TransactionTestUtils mTransactionTestUtils;
    private ExportManager mExportManager;
    private DatabaseContext mExportedDbContext;
    private Instant mTimeStamp;
    private Clock mFakeClock;

    @Before
    public void setUp() throws Exception {
        mContext = mDatabaseTestRule.getUserContext();
        TransactionManager transactionManager = mDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(mContext, transactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);

        mTimeStamp = Instant.parse("2024-06-04T16:39:12Z");
        mFakeClock = Clock.fixed(mTimeStamp, ZoneId.of("UTC"));

        mExportManager = new ExportManager(mContext, mFakeClock);

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
    public void testTimeToSuccess_loggedCorrectly() {
        // Set start time to 2s before the fixed clock to emulate time that passed
        long exportStartTime = mTimeStamp.toEpochMilli() - 2000;
        mExportManager.recordSuccess(exportStartTime, 100, 50, Uri.parse("uri"));

        assertSuccessRecorded(Instant.parse("2024-06-04T16:39:12Z"), 2000);
    }

    @Test
    public void testTimeToError_loggedCorrectly() {
        // Set start time to 2s before the fixed clock to emulate time that passed
        long exportStartTime = mTimeStamp.toEpochMilli() - 2000;
        mExportManager.recordError(DATA_EXPORT_ERROR_UNKNOWN, exportStartTime, 100, 50);

        assertErrorRecorded(DATA_EXPORT_ERROR_UNKNOWN, Instant.parse("2024-06-04T16:39:12Z"), 2000);
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
        // Inserting multiple rows to vary the size for testing of size logging
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(124, 457, 7));

        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "steps_record_table", 2);

        ExportImportSettingsStorage.setLastExportError(
                ScheduledExportStatus.DATA_EXPORT_ERROR_NONE, mTimeStamp);
        // Set export location to inaccessible directory.
        ExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder()
                        .setUri(Uri.fromFile(new File("inaccessible")))
                        .build());

        assertThat(mExportManager.runExport()).isFalse();
        assertExportStartRecorded();
        // time not recorded due to fake clock
        assertErrorRecorded(ScheduledExportStatus.DATA_EXPORT_LOST_FILE_ACCESS, mTimeStamp, 0);
    }

    @Test
    public void updatesLastSuccessfulExport_onSuccessOnly() {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "steps_record_table", 1);

        // running a successful export records a "last successful export"
        assertThat(mExportManager.runExport()).isTrue();
        assertExportStartRecorded();
        // time not recorded due to fake clock
        assertSuccessRecorded(Instant.parse("2024-06-04T16:39:12Z"), 0);

        // Export running at a later time with an error
        mTimeStamp = Instant.parse("2024-12-12T16:39:12Z");
        ExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder()
                        .setUri(Uri.fromFile(new File("inaccessible")))
                        .build());
        assertThat(mExportManager.runExport()).isFalse();

        // Last successful export should hold the previous timestamp as the last export failed
        Instant lastSuccessfulExport =
                ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                        .getLastSuccessfulExportTime();
        assertThat(lastSuccessfulExport).isEqualTo(Instant.parse("2024-06-04T16:39:12Z"));
    }

    @Test
    public void updatesLastExportFileName_onSuccessOnly() {
        Context context = mock(Context.class);
        ContentResolver contentResolver = mock(ContentResolver.class);
        Cursor cursor = mock(Cursor.class);
        when(context.getContentResolver()).thenReturn(contentResolver);
        when(contentResolver.query(any(), any(), any(), any(), any())).thenReturn(cursor);
        when(cursor.moveToFirst()).thenReturn(true);
        when(cursor.getString(anyInt())).thenReturn(REMOTE_EXPORT_ZIP_FILE_NAME);
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "steps_record_table", 1);

        // Running a successful export records a "last successful export".
        assertThat(mExportManager.runExport()).isTrue();
        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(context)
                                .getLastExportFileName())
                .isEqualTo(REMOTE_EXPORT_ZIP_FILE_NAME);

        // Export running at a later time with an error
        ExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder()
                        .setUri(Uri.fromFile(new File("inaccessible")))
                        .build());
        assertThat(mExportManager.runExport()).isFalse();

        // Last successful export should hold the previous file name as the last export failed
        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(context)
                                .getLastExportFileName())
                .isEqualTo(REMOTE_EXPORT_ZIP_FILE_NAME);
    }

    private void configureExportUri() {
        ExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder()
                        .setUri(
                                Uri.fromFile(
                                        (mExportedDbContext.getDatabasePath(
                                                REMOTE_EXPORT_ZIP_FILE_NAME))))
                        .build());
    }

    private void assertTableSize(HealthConnectDatabase database, String tableName, int tableRows) {
        Cursor cursor =
                database.getWritableDatabase()
                        .rawQuery("SELECT count(*) FROM " + tableName + ";", null);
        cursor.moveToNext();
        assertThat(cursor.getInt(0)).isEqualTo(tableRows);
    }

    private void assertExportStartRecorded() {
        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logExportStatus(
                                eq(DATA_EXPORT_STARTED),
                                eq(-1 /* no value recorded*/),
                                eq(-1 /* no value recorded*/),
                                eq(-1 /* no value recorded*/)),
                times(1));
    }

    private void assertSuccessRecorded(Instant timeOfSuccess, int timeToSuccess) {
        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logExportStatus(
                                eq(DATA_EXPORT_ERROR_NONE), eq(timeToSuccess), anyInt(), anyInt()),
                times(1));
        Instant lastSuccessfulExport =
                ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                        .getLastSuccessfulExportTime();
        assertThat(lastSuccessfulExport).isEqualTo(timeOfSuccess);
    }

    private void assertErrorRecorded(int exportStatus, Instant timeOfError, int timeToError) {
        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getDataExportError())
                .isEqualTo(exportStatus);
        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getLastFailedExportTime())
                .isEqualTo(timeOfError);

        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logExportStatus(
                                eq(exportStatus), eq(timeToError), anyInt(), anyInt()),
                times(1));
    }
}
