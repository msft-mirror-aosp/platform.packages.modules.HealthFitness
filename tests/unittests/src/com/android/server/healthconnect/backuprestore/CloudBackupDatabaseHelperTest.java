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

import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;

import static com.android.server.healthconnect.backuprestore.RecordProtoConverter.PROTO_VERSION;
import static com.android.server.healthconnect.testing.storage.TransactionTestUtils.createBloodPressureRecord;
import static com.android.server.healthconnect.testing.storage.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.aidl.DeleteUsingFiltersRequestParcel;
import android.health.connect.backuprestore.BackupChange;
import android.health.connect.backuprestore.GetChangesForBackupResponse;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.internal.datatypes.BloodPressureRecordInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.StepsRecordInternal;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.proto.backuprestore.BackupData;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BackupChangeTokenHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTransactionRequest;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;
import com.android.server.healthconnect.testing.fixtures.EnvironmentFixture;
import com.android.server.healthconnect.testing.fixtures.SQLiteDatabaseFixture;
import com.android.server.healthconnect.testing.storage.TransactionTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Unit test for class {@link CloudBackupDatabaseHelper}. */
@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
public class CloudBackupDatabaseHelperTest {

    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final long TEST_START_TIME_IN_MILLIS = 2000;
    private static final long TEST_END_TIME_IN_MILLIS = 3000;
    private static final int TEST_STEP_COUNT = 1345;
    private static final int TEST_TIME_IN_MILLIS = 1234;
    private static final double TEST_SYSTOLIC = 60.2;
    private static final double TEST_DIASTOLIC = 92.6;
    private static final String ACTIVE_CALORIES_BURNED_RECORD_TABLE =
            "active_calories_burned_record_table";
    private static final String BLOOD_PRESSURE_RECORD_TABLE = "blood_pressure_record_table";

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .addStaticMockFixtures(EnvironmentFixture::new, SQLiteDatabaseFixture::new)
                    .build();

    private CloudBackupDatabaseHelper mCloudBackupDatabaseHelper;
    private TransactionTestUtils mTransactionTestUtils;
    private TransactionManager mTransactionManager;
    private AccessLogsHelper mAccessLogsHelper;
    private AppInfoHelper mAppInfoHelper;
    private final RecordProtoConverter mRecordProtoConverter = new RecordProtoConverter();

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .build();
        mTransactionManager = healthConnectInjector.getTransactionManager();
        mAppInfoHelper = healthConnectInjector.getAppInfoHelper();
        mAccessLogsHelper = healthConnectInjector.getAccessLogsHelper();

        mTransactionTestUtils = new TransactionTestUtils(healthConnectInjector);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);

        DeviceInfoHelper deviceInfoHelper = healthConnectInjector.getDeviceInfoHelper();
        HealthConnectMappings healthConnectMappings =
                healthConnectInjector.getHealthConnectMappings();
        InternalHealthConnectMappings internalHealthConnectMappings =
                healthConnectInjector.getInternalHealthConnectMappings();
        ChangeLogsHelper changeLogsHelper = healthConnectInjector.getChangeLogsHelper();
        ChangeLogsRequestHelper changeLogsRequestHelper =
                healthConnectInjector.getChangeLogsRequestHelper();

        mCloudBackupDatabaseHelper =
                new CloudBackupDatabaseHelper(
                        mTransactionManager,
                        mAppInfoHelper,
                        mAccessLogsHelper,
                        deviceInfoHelper,
                        healthConnectMappings,
                        internalHealthConnectMappings,
                        changeLogsHelper,
                        changeLogsRequestHelper,
                        healthConnectInjector.getReadAccessLogsHelper());
    }

    @Test
    public void getChangesAndTokenFromDataTables_noRecordsInDb_noChangesReturned() {
        List<BackupChange> changes =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables().getChanges();

        assertThat(changes).isEmpty();
    }

    @Test
    public void getChangesAndTokenFromDataTables_recordsInDb_correctRecordsReturned()
            throws Exception {
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT),
                createBloodPressureRecord(TEST_TIME_IN_MILLIS, TEST_SYSTOLIC, TEST_DIASTOLIC));

        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();
        assertThat(response.getVersion()).isEqualTo(PROTO_VERSION);

        List<BackupChange> changes = response.getChanges();
        assertThat(changes.size()).isEqualTo(2);
        BackupChange stepsRecordBackupChange = changes.get(0);
        assertThat(stepsRecordBackupChange.isDeletion()).isEqualTo(false);
        StepsRecordInternal stepsRecord =
                (StepsRecordInternal)
                        mRecordProtoConverter.toRecordInternal(
                                BackupData.parseFrom(stepsRecordBackupChange.getData())
                                        .getRecord());
        String uuid = stepsRecord.getUuid() != null ? stepsRecord.getUuid().toString() : null;
        assertThat(stepsRecordBackupChange.getUid()).isEqualTo(uuid);
        assertThat(stepsRecord.getCount()).isEqualTo(TEST_STEP_COUNT);
        assertThat(stepsRecord.getStartTimeInMillis()).isEqualTo(TEST_START_TIME_IN_MILLIS);
        assertThat(stepsRecord.getEndTimeInMillis()).isEqualTo(TEST_END_TIME_IN_MILLIS);
        BackupChange bloodPressureBackupChange = changes.get(1);
        assertThat(stepsRecordBackupChange.isDeletion()).isEqualTo(false);
        BloodPressureRecordInternal bloodPressureRecord =
                (BloodPressureRecordInternal)
                        mRecordProtoConverter.toRecordInternal(
                                BackupData.parseFrom(bloodPressureBackupChange.getData())
                                        .getRecord());
        assertThat(bloodPressureRecord.getDiastolic()).isEqualTo(TEST_DIASTOLIC);
        assertThat(bloodPressureRecord.getSystolic()).isEqualTo(TEST_SYSTOLIC);
        assertThat(bloodPressureRecord.getTimeInMillis()).isEqualTo(TEST_TIME_IN_MILLIS);
    }

    @Test
    public void getChangesFromDataTables_singleRecordsExceedPageSize_correctResponseReturned() {
        List<RecordInternal<?>> records = new ArrayList<>();
        for (int recordNumber = 0; recordNumber < DEFAULT_PAGE_SIZE * 2; recordNumber++) {
            records.add(
                    createStepsRecord(
                            // Add offsets to start time and end time for distinguishing different
                            // records.
                            TEST_START_TIME_IN_MILLIS + recordNumber,
                            TEST_END_TIME_IN_MILLIS + recordNumber,
                            TEST_STEP_COUNT));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);

        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();

        assertThat(response.getChanges().size()).isEqualTo(DEFAULT_PAGE_SIZE);
        String nextChangeTokenRowId = response.getNextChangeToken();
        assertThat(nextChangeTokenRowId).isEqualTo("1");
        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, nextChangeTokenRowId);
        // See {@link android.health.connect.PageTokenWrapper}.
        assertThat(backupChangeToken.getDataTablePageToken()).isEqualTo(6000);
        assertThat(backupChangeToken.getDataTableName()).isEqualTo("steps_record_table");
        assertThat(backupChangeToken.getChangeLogsRequestToken()).isEqualTo("1");
    }

    @Test
    public void getChangesFromDataTables_withSingleRecords_usingToken_correctResponseReturned() {
        List<RecordInternal<?>> records = new ArrayList<>();
        for (int recordNumber = 0; recordNumber < DEFAULT_PAGE_SIZE * 2; recordNumber++) {
            records.add(
                    createStepsRecord(
                            // Add offsets to start time and end time for distinguishing different
                            // records.
                            TEST_START_TIME_IN_MILLIS + recordNumber,
                            TEST_END_TIME_IN_MILLIS + recordNumber,
                            TEST_STEP_COUNT));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);

        GetChangesForBackupResponse firstResponse =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();
        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, firstResponse.getNextChangeToken());
        GetChangesForBackupResponse secondResponse =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables(
                        backupChangeToken.getDataTableName(),
                        backupChangeToken.getDataTablePageToken(),
                        backupChangeToken.getChangeLogsRequestToken());

        assertThat(secondResponse.getChanges().size()).isEqualTo(DEFAULT_PAGE_SIZE);
        String secondChangeTokenRowId = secondResponse.getNextChangeToken();
        assertThat(secondChangeTokenRowId).isEqualTo("2");
        BackupChangeTokenHelper.BackupChangeToken secondBackupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, secondChangeTokenRowId);
        assertThat(secondBackupChangeToken.getDataTablePageToken()).isEqualTo(-1);
        assertThat(secondBackupChangeToken.getDataTableName())
                .isEqualTo(ACTIVE_CALORIES_BURNED_RECORD_TABLE);
        // Change logs token is still the same.
        assertThat(secondBackupChangeToken.getChangeLogsRequestToken())
                .isEqualTo(backupChangeToken.getChangeLogsRequestToken());
    }

    @Test
    public void getChangesFromDataTables_mixedRecordsNotInSamePage_correctChangeTokenReturned() {
        List<RecordInternal<?>> records = new ArrayList<>();
        for (int recordNumber = 0; recordNumber < DEFAULT_PAGE_SIZE; recordNumber++) {
            records.add(
                    createStepsRecord(
                            // Add offsets to start time and end time for distinguishing different
                            // records.
                            TEST_START_TIME_IN_MILLIS + recordNumber,
                            TEST_END_TIME_IN_MILLIS + recordNumber,
                            TEST_STEP_COUNT));
        }
        records.add(createBloodPressureRecord(TEST_TIME_IN_MILLIS, TEST_SYSTOLIC, TEST_DIASTOLIC));
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);

        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();

        assertThat(response.getChanges().size()).isEqualTo(DEFAULT_PAGE_SIZE);
        String nextChangeTokenRowId = response.getNextChangeToken();
        assertThat(nextChangeTokenRowId).isEqualTo("1");
        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, nextChangeTokenRowId);
        // All data in step_record_table has been returned, page token reset as -1.
        assertThat(backupChangeToken.getDataTablePageToken()).isEqualTo(-1);
        assertThat(backupChangeToken.getDataTableName())
                .isEqualTo(ACTIVE_CALORIES_BURNED_RECORD_TABLE);
        assertThat(backupChangeToken.getChangeLogsRequestToken()).isEqualTo("1");
    }

    @Test
    public void getChangesFromDataTables_mixedRecordsNotInSamePage_usingToken_responseReturned() {
        List<RecordInternal<?>> records = new ArrayList<>();
        for (int recordNumber = 0; recordNumber < DEFAULT_PAGE_SIZE; recordNumber++) {
            records.add(
                    createStepsRecord(
                            // Add offsets to start time and end time for distinguishing different
                            // records.
                            TEST_START_TIME_IN_MILLIS + recordNumber,
                            TEST_END_TIME_IN_MILLIS + recordNumber,
                            TEST_STEP_COUNT));
        }
        records.add(createBloodPressureRecord(TEST_TIME_IN_MILLIS, TEST_SYSTOLIC, TEST_DIASTOLIC));
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);

        GetChangesForBackupResponse firstResponse =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();
        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, firstResponse.getNextChangeToken());
        GetChangesForBackupResponse secondResponse =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables(
                        backupChangeToken.getDataTableName(),
                        backupChangeToken.getDataTablePageToken(),
                        backupChangeToken.getChangeLogsRequestToken());

        assertThat(secondResponse.getChanges().size()).isEqualTo(1);
        String secondChangeTokenRowId = secondResponse.getNextChangeToken();
        assertThat(secondChangeTokenRowId).isEqualTo("2");
        BackupChangeTokenHelper.BackupChangeToken secondBackupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, secondChangeTokenRowId);
        assertThat(secondBackupChangeToken.getDataTablePageToken()).isEqualTo(-1);
        assertThat(secondBackupChangeToken.getDataTableName()).isEqualTo(null);
        // Change logs token is still the same.
        assertThat(secondBackupChangeToken.getChangeLogsRequestToken())
                .isEqualTo(backupChangeToken.getChangeLogsRequestToken());
    }

    @Test
    public void getChangesFromDataTables_mixedRecordsWithinSamePage_correctChangeTokenReturned() {
        List<RecordInternal<?>> records = new ArrayList<>();
        // Create 2500 step records and 2501 blood pressure records.
        for (int recordNumber = 0; recordNumber < DEFAULT_PAGE_SIZE / 2; recordNumber++) {
            records.add(
                    createStepsRecord(
                            // Add offsets to start time and end time for distinguishing different
                            // records.
                            TEST_START_TIME_IN_MILLIS + recordNumber,
                            TEST_END_TIME_IN_MILLIS + recordNumber,
                            TEST_STEP_COUNT));
        }
        for (int recordNumber = 0; recordNumber < DEFAULT_PAGE_SIZE / 2 + 1; recordNumber++) {
            records.add(
                    createBloodPressureRecord(
                            TEST_TIME_IN_MILLIS + recordNumber, TEST_SYSTOLIC, TEST_DIASTOLIC));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records);

        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();

        assertThat(response.getChanges().size()).isEqualTo(DEFAULT_PAGE_SIZE);
        String nextChangeTokenRowId = response.getNextChangeToken();
        assertThat(nextChangeTokenRowId).isEqualTo("1");
        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, nextChangeTokenRowId);
        assertThat(backupChangeToken.getDataTablePageToken()).isEqualTo(3468);
        assertThat(backupChangeToken.getDataTableName()).isEqualTo(BLOOD_PRESSURE_RECORD_TABLE);
        assertThat(backupChangeToken.getChangeLogsRequestToken()).isEqualTo("1");
    }

    @Test
    public void isChangeLogsTokenValid_tokenIsNull_invalid() {
        assertThat(mCloudBackupDatabaseHelper.isChangeLogsTokenValid(null)).isFalse();
    }

    @Test
    public void isChangeLogsTokenValid_nextChangeLogNoLongerExists_invalid() {
        RecordInternal<StepsRecord> stepRecord =
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT);
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, stepRecord);
        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();
        // Insert a blood pressure record and generate a change log so the previous returned token
        // does not point to the end of the table.
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createBloodPressureRecord(TEST_TIME_IN_MILLIS, TEST_SYSTOLIC, TEST_DIASTOLIC));

        // Delete some change logs.
        mTransactionManager.delete(
                new DeleteTableRequest(ChangeLogsHelper.TABLE_NAME, stepRecord.getRecordType()));

        assertThat(mCloudBackupDatabaseHelper.isChangeLogsTokenValid(response.getNextChangeToken()))
                .isFalse();
    }

    @Test
    public void isChangeLogsTokenValid_nextChangeLogExists_valid() {
        RecordInternal<StepsRecord> stepRecord =
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT);
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, stepRecord);
        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();
        // Insert a blood pressure record and generate a change log so the previous returned token
        // does not point to the end of the table.
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createBloodPressureRecord(TEST_TIME_IN_MILLIS, TEST_SYSTOLIC, TEST_DIASTOLIC));

        assertThat(mCloudBackupDatabaseHelper.isChangeLogsTokenValid(response.getNextChangeToken()))
                .isTrue();
    }

    @Test
    public void isChangeLogsTokenValid_tokenPointsToEndOfTable_valid() {
        RecordInternal<StepsRecord> stepRecord =
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT);
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, stepRecord);
        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();

        assertThat(mCloudBackupDatabaseHelper.isChangeLogsTokenValid(response.getNextChangeToken()))
                .isTrue();
    }

    @Test
    public void getIncrementalChanges_changeLogsTokenIsNull_throwsException() {
        assertThrows(
                IllegalStateException.class,
                () -> mCloudBackupDatabaseHelper.getIncrementalChanges(null));
    }

    @Test
    public void getIncrementalChanges_upsertRecords_correctChangeReturned() throws Exception {
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT));
        // All data tables have been iterated through.
        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();
        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, response.getNextChangeToken());

        // Insert a new record and generate access logs.
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createBloodPressureRecord(TEST_TIME_IN_MILLIS, TEST_SYSTOLIC, TEST_DIASTOLIC));
        GetChangesForBackupResponse secondResponse =
                mCloudBackupDatabaseHelper.getIncrementalChanges(
                        backupChangeToken.getChangeLogsRequestToken());

        assertThat(secondResponse.getVersion()).isEqualTo(PROTO_VERSION);
        assertThat(secondResponse.getChanges().size()).isEqualTo(1);
        BackupChange bloodPressureBackupChange = secondResponse.getChanges().get(0);
        BloodPressureRecordInternal bloodPressureRecord =
                (BloodPressureRecordInternal)
                        mRecordProtoConverter.toRecordInternal(
                                BackupData.parseFrom(bloodPressureBackupChange.getData())
                                        .getRecord());
        assertThat(bloodPressureRecord.getDiastolic()).isEqualTo(TEST_DIASTOLIC);
        assertThat(bloodPressureRecord.getSystolic()).isEqualTo(TEST_SYSTOLIC);
        assertThat(bloodPressureRecord.getTimeInMillis()).isEqualTo(TEST_TIME_IN_MILLIS);
    }

    @Test
    public void getIncrementalChanges_includesDeletedRecords_correctChangeReturned() {
        RecordInternal<BloodPressureRecord> bloodPressureRecordInternal =
                createBloodPressureRecord(TEST_TIME_IN_MILLIS, TEST_SYSTOLIC, TEST_DIASTOLIC);
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT),
                bloodPressureRecordInternal);
        // All data tables have been iterated through.
        GetChangesForBackupResponse response =
                mCloudBackupDatabaseHelper.getChangesAndTokenFromDataTables();
        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, response.getNextChangeToken());

        // Delete the blood pressure record.
        DeleteUsingFiltersRequest deleteRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(BloodPressureRecord.class)
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .build())
                        .build();
        DeleteUsingFiltersRequestParcel parcel = new DeleteUsingFiltersRequestParcel(deleteRequest);
        mTransactionManager.deleteAllRecords(
                new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel, mAppInfoHelper),
                /* shouldRecordDeleteAccessLogs= */ true,
                mAccessLogsHelper);

        GetChangesForBackupResponse secondResponse =
                mCloudBackupDatabaseHelper.getIncrementalChanges(
                        backupChangeToken.getChangeLogsRequestToken());

        assertThat(secondResponse.getChanges().size()).isEqualTo(1);
        BackupChange deletedBloodPressureBackupChange = secondResponse.getChanges().get(0);
        UUID bloodPressureRecordUuid = bloodPressureRecordInternal.getUuid();
        assertThat(bloodPressureRecordUuid).isNotNull();
        assertThat(deletedBloodPressureBackupChange.isDeletion()).isTrue();
        assertThat(deletedBloodPressureBackupChange.getUid())
                .isEqualTo(bloodPressureRecordUuid.toString());
        assertThat(deletedBloodPressureBackupChange.getData()).isNull();
    }
}
