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
package com.android.server.healthconnect.backuprestore;

import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;

import static com.android.server.healthconnect.backuprestore.BackupRestoreDatabaseHelper.MAXIMUM_PAGE_SIZE;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.database.sqlite.SQLiteException;
import android.health.connect.HealthConnectManager;
import android.health.connect.backuprestore.GetChangesForBackupResponse;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.os.Environment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BackupChangeTokenHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/** Unit test for class {@link CloudBackupManager}. */
@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
public class CloudBackupManagerTest {
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final long TEST_START_TIME_IN_MILLIS = 2000;
    private static final long TEST_END_TIME_IN_MILLIS = 3000;
    private static final int TEST_STEP_COUNT = 1345;

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .build();

    @Rule(order = 3)
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private CloudBackupManager mCloudBackupManager;

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(mDatabaseTestRule.getDatabaseContext())
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .build();

        mTransactionManager = healthConnectInjector.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(healthConnectInjector);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
        AppInfoHelper appInfoHelper = healthConnectInjector.getAppInfoHelper();
        AccessLogsHelper accessLogsHelper = healthConnectInjector.getAccessLogsHelper();
        DeviceInfoHelper deviceInfoHelper = healthConnectInjector.getDeviceInfoHelper();
        HealthDataCategoryPriorityHelper priorityHelper =
                healthConnectInjector.getHealthDataCategoryPriorityHelper();
        PreferenceHelper preferenceHelper = healthConnectInjector.getPreferenceHelper();
        HealthConnectMappings healthConnectMappings =
                healthConnectInjector.getHealthConnectMappings();
        InternalHealthConnectMappings internalHealthConnectMappings =
                healthConnectInjector.getInternalHealthConnectMappings();
        ChangeLogsHelper changeLogsHelper = healthConnectInjector.getChangeLogsHelper();
        ChangeLogsRequestHelper changeLogsRequestHelper =
                healthConnectInjector.getChangeLogsRequestHelper();
        ExportImportSettingsStorage exportImportSettingsStorage =
                healthConnectInjector.getExportImportSettingsStorage();

        mCloudBackupManager =
                new CloudBackupManager(
                        mTransactionManager,
                        appInfoHelper,
                        accessLogsHelper,
                        deviceInfoHelper,
                        healthConnectMappings,
                        internalHealthConnectMappings,
                        changeLogsHelper,
                        changeLogsRequestHelper,
                        priorityHelper,
                        preferenceHelper,
                        exportImportSettingsStorage);
    }

    @Test
    public void getChangesForBackup_noMoreChangeLogs_correctResponseReturned() {
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT));
        GetChangesForBackupResponse response = mCloudBackupManager.getChangesForBackup(null);
        BackupChangeTokenHelper.BackupChangeToken firstBackupToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, response.getNextChangeToken());

        GetChangesForBackupResponse secondResponse =
                mCloudBackupManager.getChangesForBackup(response.getNextChangeToken());

        assertThat(secondResponse.getChanges().size()).isEqualTo(0);
        BackupChangeTokenHelper.BackupChangeToken secondBackupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, secondResponse.getNextChangeToken());
        assertThat(secondBackupChangeToken.getDataTableName()).isNull();
        assertThat(secondBackupChangeToken.getDataTablePageToken())
                .isEqualTo(EMPTY_PAGE_TOKEN.encode());
        // Same change logs token so the next incremental call will start from the same point.
        assertThat(secondBackupChangeToken.getChangeLogsRequestToken())
                .isEqualTo(firstBackupToken.getChangeLogsRequestToken());
    }

    @Test
    public void getChangesForBackup_dataTableIsNotNull_succeed() {
        List<RecordInternal<?>> records = new ArrayList<>();
        for (int recordNumber = 0; recordNumber < MAXIMUM_PAGE_SIZE + 1; recordNumber++) {
            records.add(
                    createStepsRecord(
                            // Add offsets to start time and end time for distinguishing different
                            // records.
                            TEST_START_TIME_IN_MILLIS + recordNumber,
                            TEST_END_TIME_IN_MILLIS + recordNumber,
                            TEST_STEP_COUNT));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);

        GetChangesForBackupResponse response = mCloudBackupManager.getChangesForBackup(null);

        GetChangesForBackupResponse secondResponse =
                mCloudBackupManager.getChangesForBackup(response.getNextChangeToken());
        assertThat(secondResponse.getChanges().size()).isEqualTo(1);
    }

    @Test
    public void getChangesForBackup_changeLogsTokenInvalid_invalidateToken() {
        List<RecordInternal<?>> records = new ArrayList<>();
        // Use MAXIMUM_PAGE_SIZE + 1 to make sure the returned change token, which to be used for
        // the second call of getChangesForBackup, is not empty.
        for (int recordNumber = 0; recordNumber < MAXIMUM_PAGE_SIZE + 1; recordNumber++) {
            records.add(
                    createStepsRecord(
                            // Add offsets to start time and end time for distinguishing different
                            // records.
                            TEST_START_TIME_IN_MILLIS + recordNumber,
                            TEST_END_TIME_IN_MILLIS + recordNumber,
                            TEST_STEP_COUNT));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);
        GetChangesForBackupResponse response = mCloudBackupManager.getChangesForBackup(null);
        // Delete change logs.
        mTransactionManager.delete(new DeleteTableRequest(ChangeLogsHelper.TABLE_NAME));

        GetChangesForBackupResponse secondResponse =
                mCloudBackupManager.getChangesForBackup(response.getNextChangeToken());
        assertThat(secondResponse.getChanges()).isEmpty();
        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, secondResponse.getNextChangeToken());
        assertThat(backupChangeToken.getChangeLogsRequestToken()).isEqualTo(null);
        assertThat(backupChangeToken.getDataTablePageToken()).isEqualTo(EMPTY_PAGE_TOKEN.encode());
        assertThat(backupChangeToken.getDataTableName()).isEqualTo(null);
    }

    @Test
    public void getChangesForBackup_changeTokenIsNull_succeed() {
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT));

        GetChangesForBackupResponse response = mCloudBackupManager.getChangesForBackup(null);

        assertThat(response.getChanges().size()).isEqualTo(1);
        String nextChangeToken = response.getNextChangeToken();
        assertThat(nextChangeToken).isEqualTo("1");
    }

    @Test
    public void getChangesForBackup_throwsDatabaseException() {
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT));

        // Delete backup_change_token_table.
        HealthConnectDatabase database =
                new HealthConnectDatabase(mDatabaseTestRule.getDatabaseContext());
        database.getWritableDatabase()
                .execSQL("DROP TABLE IF EXISTS " + BackupChangeTokenHelper.getTableName());

        assertThrows(SQLiteException.class, () -> mCloudBackupManager.getChangesForBackup(null));
        // Add backup_change_token_table back to not affect other tests.
        BackupChangeTokenHelper.applyBackupTokenUpgrade(database.getWritableDatabase());
    }
}
