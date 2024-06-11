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

import static com.android.server.healthconnect.exportimport.ImportManager.IMPORT_DATABASE_DIR_NAME;
import static com.android.server.healthconnect.exportimport.ImportManager.IMPORT_DATABASE_FILE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createBloodPressureRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getReadTransactionRequest;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.net.Uri;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.TestUtils;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ImportManagerTest {

    private static final String TEST_PACKAGE_NAME = "package.name";

    @Rule
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private HealthConnectUserContext mContext;

    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;

    private ImportManager mImportManager;

    @Before
    public void setUp() throws Exception {
        mContext = mDatabaseTestRule.getUserContext();
        mTransactionManager = mDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(mContext, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
        TestUtils.runWithShellPermissionIdentity(
                () -> HealthConnectDeviceConfigManager.initializeInstance(mContext),
                Manifest.permission.READ_DEVICE_CONFIG);

        mImportManager = new ImportManager(mContext);
    }

    @Test
    public void copiesAllData() throws Exception {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME,
                        createStepsRecord(123, 345, 100),
                        createBloodPressureRecord(234, 120.0, 80.0));

        File originalDb = mTransactionManager.getDatabasePath();
        File dbToImport = new File(mContext.getDir("test", Context.MODE_PRIVATE), "export.db");
        Files.copy(originalDb.toPath(), dbToImport.toPath(), StandardCopyOption.REPLACE_EXISTING);

        DatabaseHelper.clearAllData(mTransactionManager);

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(dbToImport));

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
    }

    @Test
    public void skipsMissingTables() throws Exception {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME,
                        createStepsRecord(123, 345, 100),
                        createBloodPressureRecord(234, 120.0, 80.0));

        File originalDb = mTransactionManager.getDatabasePath();
        File dbToImport = new File(mContext.getDir("test", Context.MODE_PRIVATE), "export.db");
        Files.copy(originalDb.toPath(), dbToImport.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Delete steps record table in import db.
        String stepsRecordTableName =
                RecordHelperProvider.getRecordHelper(RecordTypeIdentifier.RECORD_TYPE_STEPS)
                        .getMainTableName();
        try (SQLiteDatabase importDb =
                SQLiteDatabase.openDatabase(
                        dbToImport, new SQLiteDatabase.OpenParams.Builder().build())) {
            importDb.execSQL("DROP TABLE " + stepsRecordTableName);
        }

        DatabaseHelper.clearAllData(mTransactionManager);

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(dbToImport));

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
        File originalDb = mTransactionManager.getDatabasePath();
        File dbToImport = new File(mContext.getDir("test", Context.MODE_PRIVATE), "export.db");
        Files.copy(originalDb.toPath(), dbToImport.toPath(), StandardCopyOption.REPLACE_EXISTING);

        mImportManager.runImport(mContext.getUser(), Uri.fromFile(dbToImport));

        File databaseDir =
                DatabaseContext.create(mContext, IMPORT_DATABASE_DIR_NAME, mContext.getUser())
                        .getDatabaseDir();
        assertThat(new File(databaseDir, IMPORT_DATABASE_FILE_NAME).exists()).isFalse();
    }
}
