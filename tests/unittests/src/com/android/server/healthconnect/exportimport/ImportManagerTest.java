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

import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_NONE;
import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_VERSION_MISMATCH;
import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_WRONG_FILE;
import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_STARTED;

import static com.android.server.healthconnect.exportimport.ExportManager.LOCAL_EXPORT_DATABASE_FILE_NAME;
import static com.android.server.healthconnect.exportimport.ImportManager.IMPORT_DATABASE_DIR_NAME;
import static com.android.server.healthconnect.exportimport.ImportManager.IMPORT_DATABASE_FILE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createBloodPressureRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getReadTransactionRequest;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthDataCategory;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.net.Uri;
import android.os.Environment;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.FakePreferenceHelper;
import com.android.server.healthconnect.TestUtils;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.logging.ExportImportLogger;
import com.android.server.healthconnect.notifications.HealthConnectNotificationSender;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.StorageContext;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ImportManagerTest {

    private static final String TAG = "ImportManagerTest";
    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String TEST_DIRECTORY_NAME = "test";
    private static final UserHandle DEFAULT_USER_HANDLE = UserHandle.of(UserHandle.myUserId());

    private static final String TEST_PACKAGE_NAME_2 = "other.app";
    private static final String TEST_PACKAGE_NAME_3 = "another.app";

    private static final int TEST_COMPRESSED_FILE_SIZE = 1042;

    @Rule(order = 1)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .setStrictness(Strictness.LENIENT)
                    .mockStatic(ExportImportLogger.class)
                    .build();

    @Rule(order = 2)
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private ImportManager mImportManagerSpy;

    private StorageContext mContext;
    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private HealthDataCategoryPriorityHelper mPriorityHelper;
    private HealthConnectNotificationSender mNotificationSender;
    private ExportImportSettingsStorage mExportImportSettingsStorage;
    private AppInfoHelper mAppInfoHelper;
    private AccessLogsHelper mAccessLogsHelper;
    private DeviceInfoHelper mDeviceInfoHelper;
    private InternalHealthConnectMappings mInternalHealthConnectMappings;

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG);

        HealthDataCategoryPriorityHelper.clearInstanceForTest();
        AppInfoHelper.resetInstanceForTest();
        AccessLogsHelper.resetInstanceForTest();
        DeviceInfoHelper.resetInstanceForTest();
        HealthDataCategoryPriorityHelper.clearInstanceForTest();

        mContext = mDatabaseTestRule.getDatabaseContext();
        mTransactionManager = mDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(mContext, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME_2);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME_3);
        mNotificationSender = mock(HealthConnectNotificationSender.class);

        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(mContext)
                        .setPreferenceHelper(new FakePreferenceHelper())
                        .setTransactionManager(mTransactionManager)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .build();

        mExportImportSettingsStorage = healthConnectInjector.getExportImportSettingsStorage();
        mAppInfoHelper = healthConnectInjector.getAppInfoHelper();
        mAccessLogsHelper = healthConnectInjector.getAccessLogsHelper();
        mDeviceInfoHelper = healthConnectInjector.getDeviceInfoHelper();
        healthConnectInjector.getHealthConnectDeviceConfigManager();
        mPriorityHelper = healthConnectInjector.getHealthDataCategoryPriorityHelper();
        mPriorityHelper.setPriorityOrder(HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME));
        mInternalHealthConnectMappings = healthConnectInjector.getInternalHealthConnectMappings();

        Instant timeStamp = Instant.parse("2024-06-04T16:39:12Z");
        Clock fakeClock = Clock.fixed(timeStamp, ZoneId.of("UTC"));

        ImportManager importManager =
                new ImportManager(
                        mAppInfoHelper,
                        mContext,
                        mExportImportSettingsStorage,
                        mTransactionManager,
                        mDeviceInfoHelper,
                        mPriorityHelper,
                        fakeClock,
                        mNotificationSender);
        mImportManagerSpy = ExtendedMockito.spy(importManager);
        doReturn(TEST_COMPRESSED_FILE_SIZE)
                .when(mImportManagerSpy)
                .getFileSizeInKb(any(ContentResolver.class), any(Uri.class));
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.waitForAllScheduledTasksToComplete();
        DatabaseHelper.clearAllData(mTransactionManager);

        File testDir = mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE);
        File[] allContents = testDir.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                file.delete();
            }
        }
        testDir.delete();
        AppInfoHelper.resetInstanceForTest();
        AccessLogsHelper.resetInstanceForTest();
        DeviceInfoHelper.resetInstanceForTest();
    }

    @Test
    public void copiesAllData() throws Exception {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME,
                        createStepsRecord(123, 345, 100),
                        createBloodPressureRecord(234, 120.0, 80.0));

        File zipToImport = zipExportedDb(exportCurrentDb());

        DatabaseHelper.clearAllData(mTransactionManager);

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE,
                        DEFAULT_USER_HANDLE);

        List<UUID> stepsUuids = ImmutableList.of(UUID.fromString(uuids.get(0)));
        List<UUID> bloodPressureUuids = ImmutableList.of(UUID.fromString(uuids.get(1)));
        ReadTransactionRequest request =
                getReadTransactionRequest(
                        ImmutableMap.of(
                                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                                stepsUuids,
                                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE,
                                bloodPressureUuids));

        List<RecordInternal<?>> records =
                mTransactionManager.readRecordsByIds(
                        request,
                        mAppInfoHelper,
                        mAccessLogsHelper,
                        mDeviceInfoHelper,
                        /* shouldRecordAccessLog= */ false);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getUuid()).isEqualTo(stepsUuids.get(0));
        assertThat(records.get(1).getUuid()).isEqualTo(bloodPressureUuids.get(0));
        assertThat(mExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_NONE);
    }

    @Test
    public void mergesPriorityList() throws Exception {
        // Insert data so that getPriorityOrder doesn't remove apps from priority list.
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 345, 100));
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME_2, createStepsRecord(234, 432, 200));
        mAppInfoHelper.syncAppInfoRecordTypesUsed();

        mPriorityHelper.setPriorityOrder(
                HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2));
        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2)
                .inOrder();

        File zipToImport = zipExportedDb(exportCurrentDb());

        mPriorityHelper.setPriorityOrder(HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME_2));
        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly(TEST_PACKAGE_NAME_2)
                .inOrder();

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE,
                        DEFAULT_USER_HANDLE);

        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly(TEST_PACKAGE_NAME_2, TEST_PACKAGE_NAME)
                .inOrder();
    }

    @Test
    public void mergesPriorityList_handlesDifferentPackageNames() throws Exception {
        // Insert data so that getPriorityOrder doesn't remove apps from priority list.
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 345, 100));
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME_2, createStepsRecord(234, 432, 200));
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME_3, createStepsRecord(400, 510, 305));
        mAppInfoHelper.syncAppInfoRecordTypesUsed();

        mPriorityHelper.setPriorityOrder(
                HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2));
        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2)
                .inOrder();

        File zipToImport = zipExportedDb(exportCurrentDb());

        mPriorityHelper.setPriorityOrder(
                HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME_2, TEST_PACKAGE_NAME_3));
        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly(TEST_PACKAGE_NAME_2, TEST_PACKAGE_NAME_3)
                .inOrder();

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE,
                        DEFAULT_USER_HANDLE);

        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly(TEST_PACKAGE_NAME_2, TEST_PACKAGE_NAME_3, TEST_PACKAGE_NAME)
                .inOrder();
    }

    @Test
    public void skipsMissingTables() throws Exception {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME,
                        createStepsRecord(123, 345, 100),
                        createBloodPressureRecord(234, 120.0, 80.0));

        File dbToImport = exportCurrentDb();

        // Delete steps record table in import db.
        String stepsRecordTableName =
                mInternalHealthConnectMappings
                        .getRecordHelper(RecordTypeIdentifier.RECORD_TYPE_STEPS)
                        .getMainTableName();
        try (SQLiteDatabase importDb =
                SQLiteDatabase.openDatabase(
                        dbToImport, new SQLiteDatabase.OpenParams.Builder().build())) {
            importDb.execSQL("DROP TABLE " + stepsRecordTableName);
        }

        File zipToImport = zipExportedDb(dbToImport);

        DatabaseHelper.clearAllData(mTransactionManager);

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE,
                        DEFAULT_USER_HANDLE);

        List<UUID> stepsUuids = ImmutableList.of(UUID.fromString(uuids.get(0)));
        List<UUID> bloodPressureUuids = ImmutableList.of(UUID.fromString(uuids.get(1)));
        ReadTransactionRequest request =
                getReadTransactionRequest(
                        ImmutableMap.of(
                                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                                stepsUuids,
                                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE,
                                bloodPressureUuids));

        List<RecordInternal<?>> records =
                mTransactionManager.readRecordsByIds(
                        request,
                        mAppInfoHelper,
                        mAccessLogsHelper,
                        mDeviceInfoHelper,
                        /* shouldRecordAccessLog= */ false);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getUuid()).isEqualTo(bloodPressureUuids.get(0));
    }

    @Test
    public void deletesTheDatabase() throws Exception {
        File dbToImport = exportCurrentDb();

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(dbToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender
                                .NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE,
                        DEFAULT_USER_HANDLE);

        File databaseDir =
                StorageContext.create(mContext, mContext.getUser(), IMPORT_DATABASE_DIR_NAME)
                        .getDatabaseDir();
        assertThat(new File(databaseDir, IMPORT_DATABASE_FILE_NAME).exists()).isFalse();
    }

    @Test
    public void importNotADatabase_logsWrongFileError() throws Exception {
        File textFileToImport =
                createTextFile(
                        mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE), "export.txt");
        File zipToImport = zipExportedDb(textFileToImport);

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logImportStatus(
                                eq(DATA_IMPORT_STARTED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED)),
                times(1));
        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logImportStatus(
                                DATA_IMPORT_ERROR_WRONG_FILE, 0, 0, TEST_COMPRESSED_FILE_SIZE));
    }

    @Test
    public void importNotADatabase_setsWrongFileError() throws Exception {
        File textFileToImport =
                createTextFile(
                        mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE), "export.txt");
        File zipToImport = zipExportedDb(textFileToImport);

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender
                                .NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE,
                        DEFAULT_USER_HANDLE);

        assertThat(mExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_WRONG_FILE);
    }

    @Test
    public void importWrongFileName_setsWrongFileError() throws Exception {
        File textFileToImport =
                createTextFile(
                        mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE),
                        "wrong_name.txt");
        File zipToImport =
                new File(mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE), "export.zip");
        Compressor.compress(textFileToImport, "wrong_name.txt", zipToImport);

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender
                                .NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE,
                        DEFAULT_USER_HANDLE);

        assertThat(mExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_WRONG_FILE);
    }

    @Test
    public void importWrongFileName_logsWrongFileError() throws Exception {
        doReturn(0)
                .when(mImportManagerSpy)
                .getFileSizeInKb(any(ContentResolver.class), any(Uri.class));

        File textFileToImport =
                createTextFile(
                        mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE),
                        "wrong_name.txt");
        File zipToImport =
                new File(mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE), "export.zip");
        Compressor.compress(textFileToImport, "wrong_name.txt", zipToImport);

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logImportStatus(
                                eq(DATA_IMPORT_STARTED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED)),
                times(1));
        ExtendedMockito.verify(
                () -> ExportImportLogger.logImportStatus(DATA_IMPORT_ERROR_WRONG_FILE, 0, 0, 0));
    }

    @Test
    public void versionMismatch_setsVersionMismatchError() throws Exception {
        File dbToImport = exportCurrentDb();
        try (SQLiteDatabase sqlDbToImport =
                SQLiteDatabase.openDatabase(
                        dbToImport, new SQLiteDatabase.OpenParams.Builder().build())) {
            sqlDbToImport.setVersion(100);
        }
        File zipToImport = zipExportedDb(dbToImport);

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender
                                .NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_VERSION_MISMATCH,
                        DEFAULT_USER_HANDLE);

        assertThat(mExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_VERSION_MISMATCH);
    }

    @Test
    public void successfulImport_setsNoError() throws Exception {
        File zipToImport = zipExportedDb(exportCurrentDb());

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE,
                        DEFAULT_USER_HANDLE);

        assertThat(mExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_NONE);
    }

    @Test
    public void importedStarted_logsNoError() throws Exception {
        File zipToImport = zipExportedDb(exportCurrentDb());

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logImportStatus(
                                eq(DATA_IMPORT_STARTED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED)),
                times(1));
    }

    @Test
    public void successfulImport_logsNoError() throws Exception {
        File currentDb = exportCurrentDb();
        File zipToImport = zipExportedDb(currentDb);
        int expectedOriginalFileSize = intSizeInKb(currentDb);

        mImportManagerSpy.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logImportStatus(
                                eq(DATA_IMPORT_STARTED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED),
                                eq(ExportImportLogger.NO_VALUE_RECORDED)),
                times(1));

        ExtendedMockito.verify(
                () ->
                        ExportImportLogger.logImportStatus(
                                DATA_IMPORT_ERROR_NONE,
                                0,
                                expectedOriginalFileSize,
                                TEST_COMPRESSED_FILE_SIZE),
                times(1));
    }

    private File exportCurrentDb() throws Exception {
        File originalDb = mTransactionManager.getDatabasePath();
        File dbToImport =
                new File(mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE), "export.db");
        Files.copy(originalDb.toPath(), dbToImport.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return dbToImport;
    }

    private File zipExportedDb(File dbToImport) throws Exception {
        File zipToImport =
                new File(mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE), "export.zip");
        Compressor.compress(dbToImport, LOCAL_EXPORT_DATABASE_FILE_NAME, zipToImport);
        return zipToImport;
    }

    private static File createTextFile(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("Contents of file " + fileName);
        fileWriter.close();
        return file;
    }

    private int intSizeInKb(File file) {
        return (int) (file.length() / 1024.0);
    }
}
