/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.healthconnect.cts;

import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_DELETE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_READ;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_UPSERT;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.getBasalMetabolicRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getDistanceRecord;
import static android.healthconnect.cts.utils.DataFactory.getHeartRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getTestRecords;
import static android.healthconnect.cts.utils.DataFactory.getUpdatedStepsRecord;
import static android.healthconnect.cts.utils.TestUtils.deleteRecordsByIdFilter;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.queryAccessLogs;
import static android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords;

import static com.android.healthfitness.flags.Flags.FLAG_ADD_MISSING_ACCESS_LOGS;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static com.google.common.truth.Truth.assertThat;

import static java.time.Instant.EPOCH;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** CTS test for {@link HealthConnectManager#queryAccessLogs} API. */
@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class HealthConnectAccessLogsTest {
    private static final String SELF_PACKAGE_NAME = "android.healthconnect.cts";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String packageName = context.getPackageName();
        verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName(packageName).build())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testAccessLogs_read_singleRecordType() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = queryAccessLogs();
        List<Record> testRecord = Collections.singletonList(getStepsRecord());
        insertRecords(testRecord);
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build());

        List<AccessLog> newAccessLogsResponse =
                waitForNewAccessLogsWithExpectedMinSize(
                        HealthConnectAccessLogsTest::queryAccessLogsWithoutThrow,
                        oldAccessLogsResponse.size() + 2,
                        1000,
                        200);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(2);
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    public void testAccessLogs_read_multipleRecordTypes() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = queryAccessLogs();
        List<Record> testRecord = getTestRecords();
        insertRecords(testRecord);
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build());
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class).build());
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(BasalMetabolicRateRecord.class)
                        .build());

        List<AccessLog> newAccessLogsResponse =
                waitForNewAccessLogsWithExpectedMinSize(
                        HealthConnectAccessLogsTest::queryAccessLogsWithoutThrow,
                        oldAccessLogsResponse.size() + 4,
                        1000,
                        200);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(4);
    }

    @Test
    public void testAccessLogs_update_singleRecordType() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = queryAccessLogs();
        Record record = getStepsRecord();
        insertRecords(Collections.singletonList(record));
        List<Record> updatedTestRecord =
                Collections.singletonList(
                        getUpdatedStepsRecord(
                                record,
                                record.getMetadata().getId(),
                                record.getMetadata().getClientRecordId()));
        TestUtils.updateRecords(updatedTestRecord);

        List<AccessLog> newAccessLogsResponse =
                waitForNewAccessLogsWithExpectedMinSize(
                        HealthConnectAccessLogsTest::queryAccessLogsWithoutThrow,
                        oldAccessLogsResponse.size() + 2,
                        1000,
                        200);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(2);
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    public void testAccessLogs_update_multipleRecordTypes() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = queryAccessLogs();
        Record stepsRecord = getStepsRecord();
        Record heartRateRecord = getHeartRateRecord();
        List<Record> records = Arrays.asList(stepsRecord, heartRateRecord);
        insertRecords(records);

        Record updatedStepsRecord =
                getUpdatedStepsRecord(
                        stepsRecord,
                        stepsRecord.getMetadata().getId(),
                        stepsRecord.getMetadata().getClientRecordId());
        Record updatedHeartRateRecord =
                getHeartRateRecord(
                        74, Instant.now(), heartRateRecord.getMetadata().getClientRecordId());
        TestUtils.updateRecords(Arrays.asList(updatedStepsRecord, updatedHeartRateRecord));

        List<AccessLog> newAccessLogsResponse =
                waitForNewAccessLogsWithExpectedMinSize(
                        HealthConnectAccessLogsTest::queryAccessLogsWithoutThrow,
                        oldAccessLogsResponse.size() + 2,
                        1000,
                        200);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(2);
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getRecordTypes()).contains(HeartRateRecord.class);
        assertThat(accessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    public void testAccessLogs_insert_singleRecordType() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = queryAccessLogs();
        List<Record> testRecord = Collections.singletonList(getStepsRecord());
        insertRecords(testRecord);

        List<AccessLog> newAccessLogsResponse =
                waitForNewAccessLogsWithExpectedMinSize(
                        HealthConnectAccessLogsTest::queryAccessLogsWithoutThrow,
                        oldAccessLogsResponse.size() + 1,
                        1000,
                        200);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(1);
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    public void testAccessLogs_insert_multipleRecordTypes() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = queryAccessLogs();
        List<Record> testRecord = getTestRecords();
        insertRecords(testRecord);

        List<AccessLog> newAccessLogsResponse =
                waitForNewAccessLogsWithExpectedMinSize(
                        HealthConnectAccessLogsTest::queryAccessLogsWithoutThrow,
                        oldAccessLogsResponse.size() + 1,
                        1000,
                        200);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(1);
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getRecordTypes()).contains(HeartRateRecord.class);
        assertThat(accessLog.getRecordTypes()).contains(BasalMetabolicRateRecord.class);
        assertThat(accessLog.getRecordTypes()).contains(ExerciseSessionRecord.class);
        assertThat(accessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ADD_MISSING_ACCESS_LOGS})
    public void testAccessLogs_aggregate_expectReadLogs() throws Exception {
        insertRecords(getStepsRecord());

        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(EPOCH)
                                        .setEndTime(Instant.now())
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();

        List<AccessLog> insertAccessLogs = queryAccessLogs();
        assertThat(insertAccessLogs).hasSize((1));
        assertThat(insertAccessLogs.get(0).getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);

        getAggregateResponse(request);

        List<AccessLog> insertAndAggregateAccessLogs = queryAccessLogs();
        assertThat(insertAndAggregateAccessLogs).hasSize((2));

        AccessLog readAccessLog = insertAndAggregateAccessLogs.get(1);
        assertThat(readAccessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(readAccessLog.getRecordTypes()).containsExactly(StepsRecord.class);
        assertThat(readAccessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_ADD_MISSING_ACCESS_LOGS})
    public void testAccessLogs_delete_expectDeleteLogs() throws Exception {
        List<Record> records =
                insertRecords(
                        getHeartRateRecord(), getDistanceRecord(), getBasalMetabolicRateRecord());
        List<AccessLog> insertLog = queryAccessLogs();
        assertThat(insertLog).hasSize((1));
        assertThat(insertLog.get(0).getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);

        List<RecordIdFilter> recordIdFilters =
                List.of(
                        RecordIdFilter.fromId(
                                HeartRateRecord.class, records.get(0).getMetadata().getId()),
                        RecordIdFilter.fromId(
                                BasalMetabolicRateRecord.class,
                                records.get(2).getMetadata().getId()));
        TimeInstantRangeFilter timeFilter =
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(EPOCH)
                        .setEndTime(Instant.now())
                        .build();
        // delete by id
        deleteRecordsByIdFilter(recordIdFilters);
        // delete by filter, generating access log even if no step data is deleted
        verifyDeleteRecords(StepsRecord.class, timeFilter);

        DeleteUsingFiltersRequest deleteRequest =
                new DeleteUsingFiltersRequest.Builder().addRecordType(DistanceRecord.class).build();
        // delete by system, not generating access log
        verifyDeleteRecords(deleteRequest);

        List<AccessLog> insertAndDeleteLogs = queryAccessLogs();
        assertThat(insertAndDeleteLogs).hasSize((3));

        AccessLog deleteByIdLog = insertAndDeleteLogs.get(1);
        assertThat(deleteByIdLog.getOperationType()).isEqualTo(OPERATION_TYPE_DELETE);
        assertThat(deleteByIdLog.getRecordTypes())
                .containsExactly(HeartRateRecord.class, BasalMetabolicRateRecord.class);
        assertThat(deleteByIdLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);

        AccessLog deleteByFilterLog = insertAndDeleteLogs.get(2);
        assertThat(deleteByFilterLog.getOperationType()).isEqualTo(OPERATION_TYPE_DELETE);
        assertThat(deleteByFilterLog.getRecordTypes()).containsExactly(StepsRecord.class);
        assertThat(deleteByFilterLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD})
    public void testAccessLogs_phrFlagOn() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = queryAccessLogs();
        // TODO(b/337018927): Change below to upsert and read MedicalResources once we actually
        // create access logs in serviceImpl.
        List<Record> testRecord = Collections.singletonList(getStepsRecord());
        insertRecords(testRecord);
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build());

        List<AccessLog> newAccessLogsResponse =
                waitForNewAccessLogsWithExpectedMinSize(
                        HealthConnectAccessLogsTest::queryAccessLogsWithoutThrow,
                        oldAccessLogsResponse.size() + 2,
                        1000,
                        200);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(2);
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);
        assertThat(accessLog.getMedicalResourceTypes()).isEmpty();
        assertThat(accessLog.isMedicalDataSourceAccessed()).isFalse();
    }

    /**
     * Wait for some time before fetching new access logs as they are updated in the background.
     *
     * @param newAccessLogsSupplier The supplier to get new AccessLogs.
     * @param expectedMinSize The expected minimum size of the new AccessLogs.
     * @param waitMillis The wait time before each attempt to fetch the new AccessLogs.
     * @param timeoutMillis The hard timeout even if the new AccessLogs cannot be fetched.
     */
    private static List<AccessLog> waitForNewAccessLogsWithExpectedMinSize(
            Supplier<List<AccessLog>> newAccessLogsSupplier,
            int expectedMinSize,
            long waitMillis,
            long timeoutMillis)
            throws InterruptedException {
        long timeoutTimestamp = SystemClock.uptimeMillis() + timeoutMillis;
        List<AccessLog> newAccessLogsResponse;
        do {
            // Wait for some time before fetching access logs as they are updated in the background.
            Thread.sleep(waitMillis);
            newAccessLogsResponse = newAccessLogsSupplier.get();
        } while (newAccessLogsResponse.size() < expectedMinSize
                && SystemClock.uptimeMillis() < timeoutTimestamp);
        return newAccessLogsResponse;
    }

    /**
     * A helper function to just wrap TestUtils.queryAccessLogs and catch the exception, which makes
     * it easier to use as a supplier.
     */
    private static List<AccessLog> queryAccessLogsWithoutThrow() {
        try {
            return queryAccessLogs();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
