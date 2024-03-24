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

import static android.healthconnect.cts.utils.DataFactory.getHeartRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getTestRecords;
import static android.healthconnect.cts.utils.DataFactory.getUpdatedStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** CTS test for {@link HealthConnectManager#queryAccessLogs} API. */
@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class HealthConnectAccessLogsTest {
    private static final String SELF_PACKAGE_NAME = "android.healthconnect.cts";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @After
    public void tearDown() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String packageName = context.getPackageName();
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName(packageName).build())
                        .build());
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testAccessLogs_read_singleRecordType() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = TestUtils.queryAccessLogs();
        List<Record> testRecord = Collections.singletonList(getStepsRecord());
        TestUtils.insertRecords(testRecord);
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build());
        // Wait for some time before fetching access logs as they are updated in the background.
        Thread.sleep(500);
        List<AccessLog> newAccessLogsResponse = TestUtils.queryAccessLogs();
        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isGreaterThan(1);
        int size = newAccessLogsResponse.size();
        AccessLog accessLog = newAccessLogsResponse.get(size - 1);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getOperationType())
                .isEqualTo(AccessLog.OperationType.OPERATION_TYPE_READ);
        assertThat(accessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    public void testAccessLogs_read_multipleRecordTypes() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = TestUtils.queryAccessLogs();
        List<Record> testRecord = getTestRecords();
        TestUtils.insertRecords(testRecord);
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build());
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class).build());
        TestUtils.readRecords(
                new ReadRecordsRequestUsingFilters.Builder<>(BasalMetabolicRateRecord.class)
                        .build());
        // Wait for some time before fetching access logs as they are updated in the background.
        Thread.sleep(500);
        List<AccessLog> newAccessLogsResponse = TestUtils.queryAccessLogs();
        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isGreaterThan(3);
    }

    @Test
    public void testAccessLogs_update_singleRecordType() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = TestUtils.queryAccessLogs();
        Record record = getStepsRecord();
        TestUtils.insertRecords(Collections.singletonList(record));
        List<Record> updatedTestRecord =
                Collections.singletonList(
                        getUpdatedStepsRecord(
                                record,
                                record.getMetadata().getId(),
                                record.getMetadata().getClientRecordId()));
        TestUtils.updateRecords(updatedTestRecord);

        // Wait for some time before fetching access logs as they are updated in the background.
        Thread.sleep(500);

        List<AccessLog> newAccessLogsResponse = TestUtils.queryAccessLogs();
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(2);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getOperationType())
                .isEqualTo(AccessLog.OperationType.OPERATION_TYPE_UPSERT);
        assertThat(accessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    public void testAccessLogs_update_multipleRecordTypes() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = TestUtils.queryAccessLogs();
        Record stepsRecord = getStepsRecord();
        Record heartRateRecord = getHeartRateRecord();
        List<Record> records = Arrays.asList(stepsRecord, heartRateRecord);
        TestUtils.insertRecords(records);

        Record updatedStepsRecord =
                getUpdatedStepsRecord(
                        stepsRecord,
                        stepsRecord.getMetadata().getId(),
                        stepsRecord.getMetadata().getClientRecordId());
        Record updatedHeartRateRecord =
                getHeartRateRecord(
                        74, Instant.now(), heartRateRecord.getMetadata().getClientRecordId());
        TestUtils.updateRecords(Arrays.asList(updatedStepsRecord, updatedHeartRateRecord));

        // Wait for some time before fetching access logs as they are updated in the background.
        Thread.sleep(500);

        List<AccessLog> newAccessLogsResponse = TestUtils.queryAccessLogs();
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);

        assertThat(newAccessLogsResponse.size() - oldAccessLogsResponse.size()).isEqualTo(2);
        assertThat(accessLog.getOperationType())
                .isEqualTo(AccessLog.OperationType.OPERATION_TYPE_UPSERT);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getRecordTypes()).contains(HeartRateRecord.class);
        assertThat(accessLog.getPackageName()).isEqualTo(SELF_PACKAGE_NAME);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    public void testAccessLogs_afterInsert() throws InterruptedException {
        List<AccessLog> oldAccessLogsResponse = TestUtils.queryAccessLogs();
        List<Record> testRecord = getTestRecords();
        TestUtils.insertRecords(testRecord);
        // Wait for some time before fetching access logs as they are updated in the background.
        Thread.sleep(500);
        List<AccessLog> newAccessLogsResponse = TestUtils.queryAccessLogs();
        AccessLog accessLog = newAccessLogsResponse.get(newAccessLogsResponse.size() - 1);
        assertThat(newAccessLogsResponse.size()).isGreaterThan(oldAccessLogsResponse.size());
        assertThat(accessLog.getOperationType())
                .isEqualTo(AccessLog.OperationType.OPERATION_TYPE_UPSERT);
        assertThat(accessLog.getRecordTypes()).contains(StepsRecord.class);
        assertThat(accessLog.getRecordTypes()).contains(HeartRateRecord.class);
        assertThat(accessLog.getRecordTypes()).contains(BasalMetabolicRateRecord.class);
        assertThat(accessLog.getAccessTime()).isNotNull();
    }
}
