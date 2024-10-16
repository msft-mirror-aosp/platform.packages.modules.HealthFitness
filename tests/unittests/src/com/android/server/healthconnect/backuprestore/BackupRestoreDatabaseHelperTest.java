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

import static com.android.server.healthconnect.backuprestore.BackupRestoreDatabaseHelper.MAXIMUM_PAGE_SIZE;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createBloodPressureRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.backuprestore.BackupChange;
import android.health.connect.internal.datatypes.BloodPressureRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.StepsRecordInternal;
import android.os.Environment;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

/** Unit test for class {@link BackupRestoreDatabaseHelper}. */
@RunWith(AndroidJUnit4.class)
public class BackupRestoreDatabaseHelperTest {

    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final long TEST_START_TIME_IN_MILLIS = 2000;
    private static final long TEST_END_TIME_IN_MILLIS = 3000;
    private static final int TEST_STEP_COUNT = 1345;
    private static final int TEST_TIME_IN_MILLIS = 1234;
    private static final double TEST_SYSTOLIC = 60.2;
    private static final double TEST_DIASTOLIC = 92.6;

    private static Object deserializeRecordInternal(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
            return objectInputStream.readObject();
        }
    }

    @Rule(order = 1)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(Environment.class).build();

    @Rule(order = 2)
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private BackupRestoreDatabaseHelper mBackupRestoreDatabaseHelper;
    private TransactionTestUtils mTransactionTestUtils;

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    @Before
    public void setUp() {
        TransactionManager transactionManager = mDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils =
                new TransactionTestUtils(mDatabaseTestRule.getUserContext(), transactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);

        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(mDatabaseTestRule.getUserContext())
                        .setTransactionManager(transactionManager)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .build();

        AppInfoHelper appInfoHelper = healthConnectInjector.getAppInfoHelper();
        AccessLogsHelper accessLogsHelper = healthConnectInjector.getAccessLogsHelper();
        DeviceInfoHelper deviceInfoHelper = healthConnectInjector.getDeviceInfoHelper();
        mBackupRestoreDatabaseHelper =
                new BackupRestoreDatabaseHelper(
                        transactionManager, appInfoHelper, accessLogsHelper, deviceInfoHelper);
    }

    @After
    public void tearDown() {
        AppInfoHelper.resetInstanceForTest();
        AccessLogsHelper.resetInstanceForTest();
        DeviceInfoHelper.resetInstanceForTest();
    }

    @Test
    public void getChangesFromDataTables_noRecordsInDb_emptyListReturned() {
        List<BackupChange> changes = mBackupRestoreDatabaseHelper.getChangesFromDataTables();

        assertThat(changes).isEmpty();
    }

    @Test
    public void getChangesFromDataTables_recordsInDb_correctRecordsReturned()
            throws IOException, ClassNotFoundException {
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT),
                createBloodPressureRecord(TEST_TIME_IN_MILLIS, TEST_SYSTOLIC, TEST_DIASTOLIC));

        List<BackupChange> changes = mBackupRestoreDatabaseHelper.getChangesFromDataTables();

        assertThat(changes.size()).isEqualTo(2);
        BackupChange stepsRecordBackupChange = changes.get(0);
        assertThat(stepsRecordBackupChange.getVersion()).isEqualTo(0);
        assertThat(stepsRecordBackupChange.isDeletion()).isEqualTo(false);
        StepsRecordInternal stepsRecord =
                (StepsRecordInternal) deserializeRecordInternal(stepsRecordBackupChange.getData());
        String uuid = stepsRecord.getUuid() != null ? stepsRecord.getUuid().toString() : null;
        assertThat(stepsRecordBackupChange.getUid()).isEqualTo(uuid);
        assertThat(stepsRecord.getCount()).isEqualTo(TEST_STEP_COUNT);
        assertThat(stepsRecord.getStartTimeInMillis()).isEqualTo(TEST_START_TIME_IN_MILLIS);
        assertThat(stepsRecord.getEndTimeInMillis()).isEqualTo(TEST_END_TIME_IN_MILLIS);
        BackupChange bloodPressureBackupChange = changes.get(1);
        assertThat(bloodPressureBackupChange.getVersion()).isEqualTo(0);
        assertThat(stepsRecordBackupChange.isDeletion()).isEqualTo(false);
        BloodPressureRecordInternal bloodPressureRecord =
                (BloodPressureRecordInternal)
                        deserializeRecordInternal(bloodPressureBackupChange.getData());
        assertThat(bloodPressureRecord.getDiastolic()).isEqualTo(TEST_DIASTOLIC);
        assertThat(bloodPressureRecord.getSystolic()).isEqualTo(TEST_SYSTOLIC);
        assertThat(bloodPressureRecord.getTimeInMillis()).isEqualTo(TEST_TIME_IN_MILLIS);
    }

    @Test
    public void getChangesFromDataTables_recordsExceedPageSize_correctNumberOfRecordsReturned() {
        List<RecordInternal<?>> records = new ArrayList<>();
        for (int recordNumber = 0; recordNumber < 10000; recordNumber++) {
            records.add(
                    createStepsRecord(
                            // Add offsets to start time and end time for distinguishing different
                            // records.
                            TEST_START_TIME_IN_MILLIS + recordNumber,
                            TEST_END_TIME_IN_MILLIS + recordNumber,
                            TEST_STEP_COUNT));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);

        List<BackupChange> changes = mBackupRestoreDatabaseHelper.getChangesFromDataTables();

        assertThat(changes.size()).isEqualTo(MAXIMUM_PAGE_SIZE);
    }
}
