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

import static com.android.server.healthconnect.exportimport.ExportManager.LOCAL_EXPORT_DATABASE_FILE_NAME;
import static com.android.server.healthconnect.exportimport.ImportManager.IMPORT_DATABASE_DIR_NAME;
import static com.android.server.healthconnect.exportimport.ImportManager.IMPORT_DATABASE_FILE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createBloodPressureRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getReadTransactionRequest;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.HealthDataCategory;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.net.Uri;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.notifications.HealthConnectNotificationSender;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ImportManagerTest {

    private static final String TAG = "ImportManagerTest";
    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String TEST_DIRECTORY_NAME = "test";
    private static final UserHandle DEFAULT_USER_HANDLE = UserHandle.of(UserHandle.myUserId());

    private static final String CHANNEL_ID = "healthconnect-channel";

    @Rule
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private HealthConnectUserContext mContext;

    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private HealthDataCategoryPriorityHelper mPriorityHelper;

    private ImportManager mImportManager;
    private HealthConnectNotificationSender mNotificationSender;

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG);
        mContext = mDatabaseTestRule.getUserContext();
        mTransactionManager = mDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(mContext, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
        mNotificationSender = mock(HealthConnectNotificationSender.class);
        mImportManager = new ImportManager(mContext, mNotificationSender);
        HealthConnectDeviceConfigManager.initializeInstance(mContext);
        mPriorityHelper = HealthDataCategoryPriorityHelper.getInstance();
        mPriorityHelper.setPriorityOrder(HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME));
    }

    @After
    public void tearDown() throws Exception {
        DatabaseHelper.clearAllData(mTransactionManager);

        File testDir = mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE);
        File[] allContents = testDir.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                file.delete();
            }
        }
        testDir.delete();
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

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

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

        List<RecordInternal<?>> records = mTransactionManager.readRecordsByIds(request);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getUuid()).isEqualTo(stepsUuids.get(0));
        assertThat(records.get(1).getUuid()).isEqualTo(bloodPressureUuids.get(0));
        assertThat(ExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_NONE);
    }

    @Test
    public void mergesPriorityList() throws Exception {
        // Insert data so that getPriorityOrder doesn't remove apps from priority list.
        mTransactionTestUtils.insertApp("other.app");
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 345, 100));
        mTransactionTestUtils.insertRecords("other.app", createStepsRecord(234, 432, 200));
        AppInfoHelper.getInstance().syncAppInfoRecordTypesUsed();

        mPriorityHelper.setPriorityOrder(
                HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME, "other.app"));
        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly(TEST_PACKAGE_NAME, "other.app")
                .inOrder();

        File zipToImport = zipExportedDb(exportCurrentDb());

        mPriorityHelper.setPriorityOrder(HealthDataCategory.ACTIVITY, List.of("other.app"));
        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly("other.app")
                .inOrder();

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE,
                        DEFAULT_USER_HANDLE);

        assertThat(mPriorityHelper.getPriorityOrder(HealthDataCategory.ACTIVITY, mContext))
                .containsExactly("other.app", TEST_PACKAGE_NAME)
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
                RecordHelperProvider.getRecordHelper(RecordTypeIdentifier.RECORD_TYPE_STEPS)
                        .getMainTableName();
        try (SQLiteDatabase importDb =
                SQLiteDatabase.openDatabase(
                        dbToImport, new SQLiteDatabase.OpenParams.Builder().build())) {
            importDb.execSQL("DROP TABLE " + stepsRecordTableName);
        }

        File zipToImport = zipExportedDb(dbToImport);

        DatabaseHelper.clearAllData(mTransactionManager);

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

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

        List<RecordInternal<?>> records = mTransactionManager.readRecordsByIds(request);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getUuid()).isEqualTo(bloodPressureUuids.get(0));
    }

    @Test
    public void deletesTheDatabase() throws Exception {
        File dbToImport = exportCurrentDb();

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(dbToImport));

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
                DatabaseContext.create(mContext, IMPORT_DATABASE_DIR_NAME, mContext.getUser())
                        .getDatabaseDir();
        assertThat(new File(databaseDir, IMPORT_DATABASE_FILE_NAME).exists()).isFalse();
    }

    @Test
    public void importNotADatabase_setsWrongFileError() throws Exception {
        File textFileToImport =
                createTextFile(
                        mContext.getDir(TEST_DIRECTORY_NAME, Context.MODE_PRIVATE), "export.txt");
        File zipToImport = zipExportedDb(textFileToImport);

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender
                                .NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE,
                        DEFAULT_USER_HANDLE);

        assertThat(ExportImportSettingsStorage.getImportStatus().getDataImportError())
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

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender
                                .NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_INVALID_FILE,
                        DEFAULT_USER_HANDLE);

        assertThat(ExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_WRONG_FILE);
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

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender
                                .NOTIFICATION_TYPE_IMPORT_UNSUCCESSFUL_VERSION_MISMATCH,
                        DEFAULT_USER_HANDLE);

        assertThat(ExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_VERSION_MISMATCH);
    }

    @Test
    public void successfulImport_setsNoError() throws Exception {
        File zipToImport = zipExportedDb(exportCurrentDb());

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(zipToImport));

        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_IN_PROGRESS,
                        DEFAULT_USER_HANDLE);
        verify(mNotificationSender, times(1))
                .sendNotificationAsUser(
                        ExportImportNotificationSender.NOTIFICATION_TYPE_IMPORT_COMPLETE,
                        DEFAULT_USER_HANDLE);

        assertThat(ExportImportSettingsStorage.getImportStatus().getDataImportError())
                .isEqualTo(DATA_IMPORT_ERROR_NONE);
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
}
