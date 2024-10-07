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

package com.android.server.healthconnect.storage;

import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_DELETE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_READ;
import static android.health.connect.datatypes.AggregationType.AggregationTypeIdentifier.HEART_RATE_RECORD_BPM_AVG;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEART_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.healthconnect.cts.utils.DataFactory.getDataOrigin;

import static com.android.server.healthconnect.storage.datatypehelpers.InstantRecordHelper.TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createBloodPressureRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getReadTransactionRequest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.aidl.DeleteUsingFiltersRequestParcel;
import android.health.connect.aidl.RecordIdFiltersParcel;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.AggregationTypeIdMapper;
import android.os.Environment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.AggregateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTransactionRequest;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.utils.RecordHelperProvider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TransactionManagerTest {
    private static final String TEST_PACKAGE_NAME = "package.name";

    // SetFlagsRule needs to be executed before any rules that accesses aconfig flags. Otherwise,
    // we will get failure like in b/344587256.
    // This is a workaround due to b/335666574, however the tests are still relevant even if the
    // rules have to run in this order. So we won't have to revert this even when b/335666574 is
    // fixed.
    // See https://chat.google.com/room/AAAAoLBF6rc/4N8gVXyQY5E
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
    public final HealthConnectDatabaseTestRule mHealthConnectDatabaseTestRule =
            new com.android.server.healthconnect.storage.datatypehelpers
                    .HealthConnectDatabaseTestRule();

    private TransactionTestUtils mTransactionTestUtils;
    private TransactionManager mTransactionManager;
    private AppInfoHelper mAppInfoHelper;
    private AccessLogsHelper mAccessLogsHelper;
    private DeviceInfoHelper mDeviceInfoHelper;

    @Before
    public void setup() {
        HealthConnectUserContext context = mHealthConnectDatabaseTestRule.getUserContext();
        mTransactionManager = mHealthConnectDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(context, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);

        AppInfoHelper.resetInstanceForTest();
        AccessLogsHelper.resetInstanceForTest();
        DeviceInfoHelper.resetInstanceForTest();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setTransactionManager(mTransactionManager)
                        .build();
        mAppInfoHelper = healthConnectInjector.getAppInfoHelper();
        mAccessLogsHelper = healthConnectInjector.getAccessLogsHelper();
        mDeviceInfoHelper = healthConnectInjector.getDeviceInfoHelper();
    }

    @Test
    public void readRecordsById_returnsAllRecords() {
        long timeMillis = 456;
        String uuid =
                mTransactionTestUtils
                        .insertRecords(
                                TEST_PACKAGE_NAME,
                                createBloodPressureRecord(timeMillis, 120.0, 80.0))
                        .get(0);

        ReadRecordsRequestUsingIds<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class)
                        .addId(uuid)
                        .build();
        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());

        List<RecordInternal<?>> records =
                mTransactionManager.readRecordsByIds(
                        readTransactionRequest,
                        mAppInfoHelper,
                        mAccessLogsHelper,
                        mDeviceInfoHelper,
                        /* shouldRecordAccessLog= */ false);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getUuid()).isEqualTo(UUID.fromString(uuid));
    }

    @Test
    public void readRecordsById_multipleRecordTypes_returnsAllRecords() {
        long startTimeMillis = 123;
        long endTimeMillis = 456;
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME,
                        createStepsRecord(startTimeMillis, endTimeMillis, 100),
                        createBloodPressureRecord(endTimeMillis, 120.0, 80.0));

        List<UUID> stepsUuids = ImmutableList.of(UUID.fromString(uuids.get(0)));
        List<UUID> bloodPressureUuids = ImmutableList.of(UUID.fromString(uuids.get(1)));
        ReadTransactionRequest request =
                getReadTransactionRequest(
                        ImmutableMap.of(
                                RECORD_TYPE_STEPS,
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
        assertThat(records.get(0).getUuid()).isEqualTo(UUID.fromString(uuids.get(0)));
        assertThat(records.get(1).getUuid()).isEqualTo(UUID.fromString(uuids.get(1)));
    }

    @Test
    @EnableFlags(Flags.FLAG_ADD_MISSING_ACCESS_LOGS)
    public void readRecordsById_isReadingSelfData_NoAccessLog() {
        // TODO(b/366149374): Fix the read by uuid case and add is not reading self data test case
        // Read by id requests are always reading self data. Clients are not allowed to read other
        // apps' data by client id
        ReadRecordsRequestUsingIds<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class)
                        .addClientRecordId("id")
                        .build();
        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());
        mTransactionManager.readRecordsByIds(
                readTransactionRequest,
                mAppInfoHelper,
                mAccessLogsHelper,
                mDeviceInfoHelper,
                /* shouldRecordAccessLog= */ false);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).isEmpty();
    }

    @Test
    public void readRecordsById_readByFilterRequest_throws() {
        ReadRecordsRequestUsingFilters<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .setEndTime(Instant.ofEpochMilli(1000))
                                        .build())
                        .setPageSize(1)
                        .build();
        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());
        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mTransactionManager.readRecordsByIds(
                                        readTransactionRequest,
                                        mAppInfoHelper,
                                        mAccessLogsHelper,
                                        mDeviceInfoHelper,
                                        /* shouldRecordAccessLog= */ false));
        assertThat(thrown).hasMessageThat().contains("Expect read by id request");
    }

    @Test
    public void readRecordsAndPageToken_returnsRecordsAndPageToken() {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME,
                        createStepsRecord(400, 500, 100),
                        createStepsRecord(500, 600, 100));

        ReadRecordsRequestUsingFilters<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .setEndTime(Instant.ofEpochMilli(1000))
                                        .build())
                        .setPageSize(1)
                        .build();
        PageTokenWrapper expectedToken =
                PageTokenWrapper.of(
                        /* isAscending= */ true, /* timeMillis= */ 500, /* offset= */ 0);

        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());
        Pair<List<RecordInternal<?>>, PageTokenWrapper> result =
                mTransactionManager.readRecordsAndPageToken(
                        readTransactionRequest,
                        mAppInfoHelper,
                        mAccessLogsHelper,
                        mDeviceInfoHelper,
                        /* shouldRecordAccessLog= */ false);
        List<RecordInternal<?>> records = result.first;
        assertThat(records).hasSize(1);
        assertThat(result.first.get(0).getUuid()).isEqualTo(UUID.fromString(uuids.get(0)));
        assertThat(result.second).isEqualTo(expectedToken);
    }

    @Test
    @EnableFlags(Flags.FLAG_ADD_MISSING_ACCESS_LOGS)
    public void readRecordsAndPageToken_isNotReadingSelfData_accessLogRecorded() {
        ReadRecordsRequestUsingFilters<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build();

        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());
        mTransactionManager.readRecordsAndPageToken(
                readTransactionRequest,
                mAppInfoHelper,
                mAccessLogsHelper,
                mDeviceInfoHelper,
                /* shouldRecordAccessLog= */ true);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).hasSize(1);
        AccessLog log = result.get(0);
        assertThat(log.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(log.getRecordTypes()).containsExactly(StepsRecord.class);
        assertThat(log.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
    }

    @Test
    @EnableFlags(Flags.FLAG_ADD_MISSING_ACCESS_LOGS)
    public void readRecordsAndPageToken_isReadingSelfData_noAccessLog() {
        ReadRecordsRequestUsingFilters<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .addDataOrigins(getDataOrigin(TEST_PACKAGE_NAME))
                        .build();

        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());
        mTransactionManager.readRecordsAndPageToken(
                readTransactionRequest,
                mAppInfoHelper,
                mAccessLogsHelper,
                mDeviceInfoHelper,
                /* shouldRecordAccessLog= */ true);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).isEmpty();
    }

    @Test
    public void readRecordsAndPageToken_readByIdRequest_throws() {
        ReadRecordsRequestUsingIds<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mTransactionManager.readRecordsAndPageToken(
                                        readTransactionRequest,
                                        mAppInfoHelper,
                                        mAccessLogsHelper,
                                        mDeviceInfoHelper,
                                        /* shouldRecordAccessLog= */ false));
        assertThat(thrown).hasMessageThat().contains("Expect read by filter request");
    }

    @Test
    public void deleteAll_byId_generateChangeLogs() {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME, createStepsRecord(123, 456, 100));
        List<RecordIdFilter> ids = List.of(RecordIdFilter.fromId(StepsRecord.class, uuids.get(0)));

        DeleteUsingFiltersRequestParcel parcel =
                new DeleteUsingFiltersRequestParcel(
                        new RecordIdFiltersParcel(ids), TEST_PACKAGE_NAME);
        assertThat(parcel.usesIdFilters()).isTrue();
        mTransactionManager.deleteAll(
                new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel, mAppInfoHelper),
                /* shouldRecordDeleteAccessLogs= */ false,
                mAccessLogsHelper);
        List<UUID> uuidList = mTransactionTestUtils.getAllDeletedUuids();
        assertThat(uuidList).hasSize(1);
        assertThat(uuidList.get(0).toString()).isEqualTo(uuids.get(0));
    }

    @Test
    public void deleteAll_byTimeFilter_generateChangeLogs() {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME, createStepsRecord(123, 456, 100));

        DeleteUsingFiltersRequest deleteRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .build())
                        .build();
        DeleteUsingFiltersRequestParcel parcel = new DeleteUsingFiltersRequestParcel(deleteRequest);
        assertThat(parcel.usesIdFilters()).isFalse();
        mTransactionManager.deleteAll(
                new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel, mAppInfoHelper),
                /* shouldRecordDeleteAccessLogs= */ false,
                mAccessLogsHelper);
        List<UUID> uuidList = mTransactionTestUtils.getAllDeletedUuids();
        assertThat(uuidList).hasSize(1);
        assertThat(uuidList.get(0).toString()).isEqualTo(uuids.get(0));
    }

    @Test
    public void deleteAll_bulkDeleteByTimeFilter_generateChangeLogs() {
        ImmutableList.Builder<RecordInternal<?>> records = new ImmutableList.Builder<>();
        for (int i = 0; i <= DEFAULT_PAGE_SIZE; i++) {
            records.add(createStepsRecord(i * 1000, (i + 1) * 1000, 9527));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records.build());

        DeleteUsingFiltersRequest deleteRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .build())
                        .build();
        DeleteUsingFiltersRequestParcel parcel = new DeleteUsingFiltersRequestParcel(deleteRequest);
        mTransactionManager.deleteAll(
                new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel, mAppInfoHelper),
                /* shouldRecordDeleteAccessLogs= */ false,
                mAccessLogsHelper);

        List<UUID> uuidList = mTransactionTestUtils.getAllDeletedUuids();
        assertThat(uuidList).hasSize(DEFAULT_PAGE_SIZE + 1);
    }

    @Test
    @EnableFlags(Flags.FLAG_ADD_MISSING_ACCESS_LOGS)
    public void deleteAll_shouldRecordAccessLog_logged() {
        DeleteUsingFiltersRequest deleteRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(StepsRecord.class)
                        .addRecordType(HeartRateRecord.class)
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .build())
                        .build();
        DeleteUsingFiltersRequestParcel parcel = new DeleteUsingFiltersRequestParcel(deleteRequest);
        mTransactionManager.deleteAll(
                new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel, mAppInfoHelper),
                /* shouldRecordDeleteAccessLogs= */ true,
                mAccessLogsHelper);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).hasSize(1);
        AccessLog log = result.get(0);
        assertThat(log.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(log.getRecordTypes()).containsExactly(StepsRecord.class, HeartRateRecord.class);
        assertThat(log.getOperationType()).isEqualTo(OPERATION_TYPE_DELETE);
    }

    @Test
    @EnableFlags(Flags.FLAG_ADD_MISSING_ACCESS_LOGS)
    public void deleteAll_shouldNotRecordAccessLog_noLog() {
        DeleteUsingFiltersRequest deleteRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(StepsRecord.class)
                        .addRecordType(HeartRateRecord.class)
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .build())
                        .build();
        DeleteUsingFiltersRequestParcel parcel = new DeleteUsingFiltersRequestParcel(deleteRequest);
        mTransactionManager.deleteAll(
                new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel, mAppInfoHelper),
                /* shouldRecordDeleteAccessLogs= */ false,
                mAccessLogsHelper);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_ADD_MISSING_ACCESS_LOGS)
    public void populateWithAggregation_accessLogRecorded() {
        RecordHelper<?> helper = RecordHelperProvider.getRecordHelper(RECORD_TYPE_HEART_RATE);
        AggregationType<?> aggregationType =
                AggregationTypeIdMapper.getInstance()
                        .getAggregationTypeFor(HEART_RATE_RECORD_BPM_AVG);
        AggregateTableRequest request =
                helper.getAggregateTableRequest(
                        aggregationType,
                        TEST_PACKAGE_NAME,
                        /* packageFilters= */ List.of(),
                        HealthDataCategoryPriorityHelper.getInstance(),
                        mAppInfoHelper,
                        mTransactionManager,
                        /* startTime= */ 123,
                        /* endTime= */ 456,
                        /* startDateAccess= */ 0,
                        /* useLocalTime= */ false);

        // We have to set group by for single aggregation here because in the
        // AggregateDataRequestParcel this is set and the implementation relies on it
        request.setGroupBy(
                TIME_COLUMN_NAME,
                /* period= */ null,
                Duration.ofMillis(456 - 123),
                new TimeInstantRangeFilter.Builder()
                        .setStartTime(Instant.ofEpochMilli(123))
                        .setEndTime(Instant.ofEpochMilli(456))
                        .build());
        mTransactionManager.populateWithAggregation(
                request,
                TEST_PACKAGE_NAME,
                Set.of(RECORD_TYPE_HEART_RATE),
                mAccessLogsHelper,
                /* shouldRecordAccessLog= */ true);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        AccessLog log = result.get(0);
        assertThat(log.getPackageName()).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(log.getRecordTypes()).containsExactly(HeartRateRecord.class);
        assertThat(log.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
    }
}
