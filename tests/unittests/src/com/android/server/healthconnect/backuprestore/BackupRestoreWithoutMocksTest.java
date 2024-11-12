/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.healthconnect.backuprestore;

import static com.android.server.healthconnect.TestUtils.assertTableSize;
import static com.android.server.healthconnect.backuprestore.BackupRestore.GRANT_TIME_FILE_NAME;
import static com.android.server.healthconnect.backuprestore.BackupRestore.STAGED_DATABASE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.health.connect.HealthConnectManager;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.restore.StageRemoteDataRequest;
import android.healthconnect.cts.phr.utils.PhrDataFactory;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.FakePreferenceHelper;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.GrantTimeXmlHelper;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.permission.UserGrantTimeState;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.PhrTestUtils;
import com.android.server.healthconnect.storage.StorageContext;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class BackupRestoreWithoutMocksTest {
    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String ORIGINAL_DATABASE_NAME = "healthconnect.db";

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Rule(order = 3)
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private StorageContext mContext;
    private TransactionTestUtils mTransactionTestUtils;
    private BackupRestore mBackupRestore;
    private PhrTestUtils mPhrTestUtils;

    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    @Before
    public void setUp() throws Exception {
        mContext = mDatabaseTestRule.getDatabaseContext();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(mContext)
                        .setPreferenceHelper(new FakePreferenceHelper())
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .build();
        mTransactionTestUtils = new TransactionTestUtils(mContext, healthConnectInjector);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);

        AppInfoHelper appInfoHelper = healthConnectInjector.getAppInfoHelper();
        TransactionManager transactionManager = healthConnectInjector.getTransactionManager();
        mBackupRestore =
                new BackupRestore(
                        appInfoHelper,
                        mFirstGrantTimeManager,
                        healthConnectInjector.getMigrationStateManager(),
                        healthConnectInjector.getPreferenceHelper(),
                        transactionManager,
                        mContext,
                        healthConnectInjector.getDeviceInfoHelper(),
                        healthConnectInjector.getHealthDataCategoryPriorityHelper());

        mPhrTestUtils =
                new PhrTestUtils(
                        mContext,
                        transactionManager,
                        healthConnectInjector.getMedicalResourceHelper(),
                        healthConnectInjector.getMedicalDataSourceHelper());
    }

    @Test
    @EnableFlags({
        Flags.FLAG_PERSONAL_HEALTH_RECORD,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        Flags.FLAG_PERSONAL_HEALTH_RECORD_DISABLE_D2D
    })
    public void testGetAllDataForBackup_disableD2dFlagEnabled_copiesAllDataExceptPhr()
            throws Exception {
        // Insert a MedicalDataSource and MedicalResource.
        MedicalDataSource dataSource =
                mPhrTestUtils.insertR4MedicalDataSource("ds", TEST_PACKAGE_NAME);
        mPhrTestUtils.upsertResource(PhrDataFactory::createVaccineMedicalResource, dataSource);
        // Insert a Step record.
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        // Ensure the original database contains the inserted data above.
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "medical_data_source_table", 1);
        assertTableSize(originalDatabase, "medical_resource_table", 1);
        assertTableSize(originalDatabase, "steps_record_table", 1);

        // Create the files where the database and the grant time files will be backed up to.
        File dbFileBacked = createAndGetEmptyFile(mContext.getDataDir(), STAGED_DATABASE_NAME);
        File grantTimeFileBacked =
                createAndGetEmptyFile(mContext.getDataDir(), GRANT_TIME_FILE_NAME);
        UserGrantTimeState userGrantTimeState =
                new UserGrantTimeState(Map.of("package", Instant.now()), Map.of(), 1);
        when(mFirstGrantTimeManager.getGrantTimeStateForUser(mContext.getUser()))
                .thenReturn(userGrantTimeState);
        // Prepare the pfds where the database and the grant time files are backed up to.
        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                dbFileBacked.getName(),
                ParcelFileDescriptor.open(dbFileBacked, ParcelFileDescriptor.MODE_READ_WRITE));
        pfdsByFileName.put(
                grantTimeFileBacked.getName(),
                ParcelFileDescriptor.open(
                        grantTimeFileBacked, ParcelFileDescriptor.MODE_READ_WRITE));

        mBackupRestore.getAllDataForBackup(
                new StageRemoteDataRequest(pfdsByFileName), mContext.getUser());

        // Ensure the backed up database does not contain PHR data but includes everything else.
        try (HealthConnectDatabase backupDatabase =
                new HealthConnectDatabase(mContext, dbFileBacked.getName())) {
            assertTableSize(backupDatabase, "medical_data_source_table", 0);
            assertTableSize(backupDatabase, "medical_resource_table", 0);
            assertTableSize(backupDatabase, "steps_record_table", 1);
        }
        assertThat(GrantTimeXmlHelper.parseGrantTime(grantTimeFileBacked).toString())
                .isEqualTo(userGrantTimeState.toString());
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    @DisableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DISABLE_D2D})
    public void testGetAllDataForBackup_disableD2dFlagDisabled_copiesAllDataIncludingPhr()
            throws Exception {
        // Insert a MedicalDataSource and MedicalResource.
        MedicalDataSource dataSource =
                mPhrTestUtils.insertR4MedicalDataSource("ds", TEST_PACKAGE_NAME);
        mPhrTestUtils.upsertResource(PhrDataFactory::createVaccineMedicalResource, dataSource);
        // Insert a Step record.
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, createStepsRecord(123, 456, 7));
        // Ensure the original database contains the inserted data above.
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, ORIGINAL_DATABASE_NAME);
        assertTableSize(originalDatabase, "medical_data_source_table", 1);
        assertTableSize(originalDatabase, "medical_resource_table", 1);
        assertTableSize(originalDatabase, "steps_record_table", 1);

        // Create the files where the database and the grant time files will be backed up to.
        File dbFileBacked = createAndGetEmptyFile(mContext.getDataDir(), STAGED_DATABASE_NAME);
        File grantTimeFileBacked =
                createAndGetEmptyFile(mContext.getDataDir(), GRANT_TIME_FILE_NAME);
        UserGrantTimeState userGrantTimeState =
                new UserGrantTimeState(Map.of("package", Instant.now()), Map.of(), 1);
        when(mFirstGrantTimeManager.getGrantTimeStateForUser(mContext.getUser()))
                .thenReturn(userGrantTimeState);
        // Prepare the pfds where the database and the grant time files are backed up to.
        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                dbFileBacked.getName(),
                ParcelFileDescriptor.open(dbFileBacked, ParcelFileDescriptor.MODE_READ_WRITE));
        pfdsByFileName.put(
                grantTimeFileBacked.getName(),
                ParcelFileDescriptor.open(
                        grantTimeFileBacked, ParcelFileDescriptor.MODE_READ_WRITE));

        mBackupRestore.getAllDataForBackup(
                new StageRemoteDataRequest(pfdsByFileName), mContext.getUser());

        // Ensure the backed up database does not contain PHR data but includes everything else.
        try (HealthConnectDatabase backupDatabase =
                new HealthConnectDatabase(mContext, dbFileBacked.getName())) {
            assertTableSize(backupDatabase, "medical_data_source_table", 1);
            assertTableSize(backupDatabase, "medical_resource_table", 1);
            assertTableSize(backupDatabase, "steps_record_table", 1);
        }
        assertThat(GrantTimeXmlHelper.parseGrantTime(grantTimeFileBacked).toString())
                .isEqualTo(userGrantTimeState.toString());
    }

    private static File createAndGetEmptyFile(File dir, String fileName) throws IOException {
        dir.mkdirs();
        File file = new File(dir, fileName);
        file.createNewFile();
        return file;
    }
}
