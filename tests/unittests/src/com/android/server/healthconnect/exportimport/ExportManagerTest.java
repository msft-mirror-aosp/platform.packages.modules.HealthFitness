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
import android.util.Slog;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStaticClasses;
import com.android.server.healthconnect.FakePreferenceHelper;
import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.logging.ExportImportLogger;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.StorageContext;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@RunWith(AndroidJUnit4.class)
public class ExportManagerTest {
    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String REMOTE_EXPORT_DATABASE_DIR_NAME = "remote";
    private static final String REMOTE_EXPORT_ZIP_FILE_NAME = "remote_file.zip";
    private static final String REMOTE_EXPORT_DATABASE_FILE_NAME = "remote_file.db";
    private static final String ORIGINAL_DATABASE_NAME = "healthconnect.db";

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

    private StorageContext mContext;
    private TransactionTestUtils mTransactionTestUtils;
    private ExportManager mExportManager;
    private StorageContext mExportedDbContext;
    private Instant mTimeStamp;
    private ExportImportSettingsStorage mExportImportSettingsStorage;

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    @Before
    public void setUp() throws Exception {
        mContext = mDatabaseTestRule.getDatabaseContext();
        TransactionManager transactionManager = mDatabaseTestRule.getTransactionManager();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(mContext)
                        .setPreferenceHelper(new FakePreferenceHelper())
                        .setTransactionManager(transactionManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .build();
        mTransactionTestUtils = new TransactionTestUtils(mContext, healthConnectInjector);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);

        mTimeStamp = Instant.parse("2024-06-04T16:39:12Z");
        Clock fakeClock = Clock.fixed(mTimeStamp, ZoneId.of("UTC"));

        mExportImportSettingsStorage = healthConnectInjector.getExportImportSettingsStorage();
        mExportManager =
                new ExportManager(
                        mContext, fakeClock, mExportImportSettingsStorage, transactionManager);

        mExportedDbContext =
                StorageContext.create(
                        mContext, mContext.getUser(), REMOTE_EXPORT_DATABASE_DIR_NAME);
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
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "access_logs_table", 2);

        assertThat(mExportManager.runExport(mContext.getUser())).isTrue();

        decompressExportedZip();
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

        assertSuccessRecorded(Instant.parse("2024-06-04T16:39:12Z"), 2000, 100, 50);
    }

    @Test
    public void testTimeToError_loggedCorrectly() {
        // Set start time to 2s before the fixed clock to emulate time that passed
        long exportStartTime = mTimeStamp.toEpochMilli() - 2000;
        mExportManager.recordError(DATA_EXPORT_ERROR_UNKNOWN, exportStartTime, 100, 50);

        assertErrorStatusStored(DATA_EXPORT_ERROR_UNKNOWN, Instant.parse("2024-06-04T16:39:12Z"));
        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logExportStatus(
                                eq(DATA_EXPORT_ERROR_UNKNOWN),
                                eq(/* timeToError= */ 2000),
                                eq(/* originalFileSizeKb= */ 100),
                                eq(/* compressedFileSizeKb= */ 50)),
                times(1));
    }

    @Test
    public void deletesChangeLogsTableContent() throws Exception {
        mTransactionTestUtils.insertChangeLog();
        mTransactionTestUtils.insertChangeLog();
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "change_logs_table", 2);

        assertThat(mExportManager.runExport(mContext.getUser())).isTrue();

        decompressExportedZip();
        try (HealthConnectDatabase remoteExportHealthConnectDatabase =
                new HealthConnectDatabase(mExportedDbContext, REMOTE_EXPORT_DATABASE_FILE_NAME)) {
            assertTableSize(remoteExportHealthConnectDatabase, "change_logs_table", 0);
        }
    }

    @Test
    public void runExport_whenCompleted_deletesLocalCopies() {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "steps_record_table", 1);

        assertThat(mExportManager.runExport(mContext.getUser())).isTrue();

        StorageContext storageContext =
                StorageContext.create(mContext, mContext.getUser(), LOCAL_EXPORT_DIR_NAME);
        assertThat(storageContext.getDatabasePath(LOCAL_EXPORT_DATABASE_FILE_NAME).exists())
                .isFalse();
        assertThat(storageContext.getDatabasePath(LOCAL_EXPORT_ZIP_FILE_NAME).exists()).isFalse();
    }

    @Test
    @MockStaticClasses({@MockStatic(Files.class), @MockStatic(Slog.class)})
    public void runExport_localExportFails_logsWithGenericError() throws IOException {
        when(Files.copy((Path) any(), any(), any())).thenThrow(new IOException("Copy failed"));

        assertThat(mExportManager.runExport(mContext.getUser())).isFalse();

        // Time not recorded due to fake clock.
        assertErrorStatusStored(DATA_EXPORT_ERROR_UNKNOWN, mTimeStamp);
        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logExportStatus(
                                eq(ScheduledExportStatus.DATA_EXPORT_ERROR_UNKNOWN),
                                eq(/* timeToError= */ 0),
                                /* originalFileSizeKb= */ anyInt(),
                                /* compressedFileSizeKb= */ anyInt()),
                times(1));
        ExtendedMockito.verify(
                () ->
                        Slog.e(
                                eq("HealthConnectExportImport"),
                                eq("Failed to create local file for export"),
                                any()),
                times(1));
    }

    @Test
    // Compressor is mocked so no zip file will be exported.
    @MockStaticClasses({@MockStatic(Compressor.class), @MockStatic(Slog.class)})
    public void runExport_noCompressedFile_logsWithGenericError() {
        assertThat(mExportManager.runExport(mContext.getUser())).isFalse();
        // Time not recorded due to fake clock.
        assertErrorStatusStored(DATA_EXPORT_ERROR_UNKNOWN, mTimeStamp);
        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logExportStatus(
                                eq(ScheduledExportStatus.DATA_EXPORT_ERROR_UNKNOWN),
                                eq(/* timeToError= */ 0),
                                /* originalFileSizeKb= */ anyInt(),
                                /* compressedFileSizeKb= */ anyInt()),
                times(1));
        ExtendedMockito.verify(
                () -> Slog.e(eq("HealthConnectExportImport"), eq("Failed to export to URI"), any()),
                times(1));
    }

    @Test
    public void deleteLocalExportFiles_deletesLocalCopies() {
        StorageContext storageContext =
                StorageContext.create(mContext, mContext.getUser(), LOCAL_EXPORT_DIR_NAME);
        new File(storageContext.getDataDir(), LOCAL_EXPORT_DATABASE_FILE_NAME).mkdirs();
        new File(storageContext.getDataDir(), LOCAL_EXPORT_ZIP_FILE_NAME).mkdirs();

        mExportManager.deleteLocalExportFiles(mContext.getUser());

        assertThat(storageContext.getDatabasePath(LOCAL_EXPORT_DATABASE_FILE_NAME).exists())
                .isFalse();
        assertThat(storageContext.getDatabasePath(LOCAL_EXPORT_ZIP_FILE_NAME).exists()).isFalse();
    }

    @Test
    public void makesRemoteCopyOfDatabase() throws Exception {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "steps_record_table", 1);

        assertThat(mExportManager.runExport(mContext.getUser())).isTrue();

        decompressExportedZip();
        try (HealthConnectDatabase remoteExportHealthConnectDatabase =
                new HealthConnectDatabase(mExportedDbContext, REMOTE_EXPORT_DATABASE_FILE_NAME)) {
            assertTableSize(remoteExportHealthConnectDatabase, "steps_record_table", 1);
        }
    }

    @Test
    @MockStatic(Slog.class)
    public void destinationUriDoesNotExist_exportFailsWithLostFileAccessError() {
        // Inserting multiple rows to vary the size for testing of size logging
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(124, 457, 7));

        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "steps_record_table", 2);

        mExportImportSettingsStorage.setLastExportError(
                ScheduledExportStatus.DATA_EXPORT_ERROR_NONE, mTimeStamp);
        // Set export location to inaccessible directory.
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder()
                        .setUri(Uri.fromFile(new File("inaccessible")))
                        .build());

        assertThat(mExportManager.runExport(mContext.getUser())).isFalse();
        assertExportStartRecorded();

        // time not recorded due to fake clock
        assertErrorStatusStored(ScheduledExportStatus.DATA_EXPORT_LOST_FILE_ACCESS, mTimeStamp);
        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logExportStatus(
                                eq(ScheduledExportStatus.DATA_EXPORT_LOST_FILE_ACCESS),
                                eq(/* timeToError= */ 0),
                                /* originalFileSizeKb= */ anyInt(),
                                /* compressedFileSizeKb= */ anyInt()),
                times(1));
        ExtendedMockito.verify(
                () ->
                        Slog.e(
                                eq("HealthConnectExportImport"),
                                eq("Lost access to export location"),
                                any()),
                times(1));
    }

    @Test
    public void updatesLastSuccessfulExport_onSuccessOnly() throws Exception {
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "steps_record_table", 1);

        // running a successful export records a "last successful export"
        assertThat(mExportManager.runExport(mContext.getUser())).isTrue();
        assertExportStartRecorded();

        // Get the actual size of the files rather than using a fixed size as the size isn't fixed
        // across test runs.
        decompressExportedZip();
        int originalFileSizeInKb =
                getFileSizeInKb(
                        mExportedDbContext.getDatabasePath(REMOTE_EXPORT_DATABASE_FILE_NAME));
        int compressedFileSizeInKb =
                getFileSizeInKb(mExportedDbContext.getDatabasePath(REMOTE_EXPORT_ZIP_FILE_NAME));

        // time not recorded due to fake clock
        assertSuccessRecorded(
                Instant.parse("2024-06-04T16:39:12Z"),
                0,
                originalFileSizeInKb,
                compressedFileSizeInKb);

        // Export running at a later time with an error
        mTimeStamp = Instant.parse("2024-12-12T16:39:12Z");
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder()
                        .setUri(Uri.fromFile(new File("inaccessible")))
                        .build());
        assertThat(mExportManager.runExport(mContext.getUser())).isFalse();

        // Last successful export should hold the previous timestamp as the last export failed
        Instant lastSuccessfulExport =
                mExportImportSettingsStorage
                        .getScheduledExportStatus(mContext)
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
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "steps_record_table", 1);

        // Running a successful export records a "last successful export".
        assertThat(mExportManager.runExport(mContext.getUser())).isTrue();
        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(context)
                                .getLastExportFileName())
                .isEqualTo(REMOTE_EXPORT_ZIP_FILE_NAME);

        // Export running at a later time with an error
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder()
                        .setUri(Uri.fromFile(new File("inaccessible")))
                        .build());
        assertThat(mExportManager.runExport(mContext.getUser())).isFalse();

        // Last successful export should hold the previous file name as the last export failed
        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(context)
                                .getLastExportFileName())
                .isEqualTo(REMOTE_EXPORT_ZIP_FILE_NAME);
    }

    private void configureExportUri() {
        mExportImportSettingsStorage.configure(
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

    private void assertSuccessRecorded(
            Instant timeOfSuccess,
            int timeToSuccess,
            int originalFileSizeKb,
            int compressedFileSizeKb) {
        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logExportStatus(
                                eq(DATA_EXPORT_ERROR_NONE),
                                eq(timeToSuccess),
                                eq(originalFileSizeKb),
                                eq(compressedFileSizeKb)),
                times(1));
        Instant lastSuccessfulExport =
                mExportImportSettingsStorage
                        .getScheduledExportStatus(mContext)
                        .getLastSuccessfulExportTime();
        assertThat(lastSuccessfulExport).isEqualTo(timeOfSuccess);
    }

    private void assertErrorStatusStored(int exportStatus, Instant timeOfError) {
        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getDataExportError())
                .isEqualTo(exportStatus);
        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastFailedExportTime())
                .isEqualTo(timeOfError);
    }

    private int getFileSizeInKb(File file) {
        return (int) (file.length() / 1024.0);
    }

    private void decompressExportedZip() throws IOException {
        Compressor.decompress(
                Uri.fromFile(mExportedDbContext.getDatabasePath(REMOTE_EXPORT_ZIP_FILE_NAME)),
                LOCAL_EXPORT_DATABASE_FILE_NAME,
                mExportedDbContext.getDatabasePath(REMOTE_EXPORT_DATABASE_FILE_NAME),
                mContext);
    }
}
