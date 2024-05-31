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

import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createBloodPressureRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getReadTransactionRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getUUID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.MedicalIdFilter;
import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.aidl.DeleteUsingFiltersRequestParcel;
import android.health.connect.aidl.MedicalIdFiltersParcel;
import android.health.connect.aidl.RecordIdFiltersParcel;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.internal.datatypes.MedicalResourceInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.DeleteTransactionRequest;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TransactionManagerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    private static final String TEST_PACKAGE_NAME = "package.name";

    @Rule public final HealthConnectDatabaseTestRule testRule = new HealthConnectDatabaseTestRule();

    private TransactionTestUtils mTransactionTestUtils;
    private TransactionManager mTransactionManager;

    @Before
    public void setup() {
        HealthConnectUserContext context = testRule.getUserContext();
        mTransactionManager = testRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(context, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
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
                mTransactionManager.readRecordsByIds(readTransactionRequest);
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
                                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                                stepsUuids,
                                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE,
                                bloodPressureUuids));

        List<RecordInternal<?>> records = mTransactionManager.readRecordsByIds(request);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getUuid()).isEqualTo(UUID.fromString(uuids.get(0)));
        assertThat(records.get(1).getUuid()).isEqualTo(UUID.fromString(uuids.get(1)));
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
                        () -> mTransactionManager.readRecordsByIds(readTransactionRequest));
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
                mTransactionManager.readRecordsAndPageToken(readTransactionRequest);
        List<RecordInternal<?>> records = result.first;
        assertThat(records).hasSize(1);
        assertThat(result.first.get(0).getUuid()).isEqualTo(UUID.fromString(uuids.get(0)));
        assertThat(result.second).isEqualTo(expectedToken);
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
                        () -> mTransactionManager.readRecordsAndPageToken(readTransactionRequest));
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
        mTransactionManager.deleteAll(new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel));

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
        mTransactionManager.deleteAll(new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel));

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
        mTransactionManager.deleteAll(new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel));

        List<UUID> uuidList = mTransactionTestUtils.getAllDeletedUuids();
        assertThat(uuidList).hasSize(DEFAULT_PAGE_SIZE + 1);
    }

    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    @Test
    public void readMedicalResourcesByIds_returnsEmpty() {
        MedicalIdFiltersParcel parcel =
                new MedicalIdFiltersParcel(List.of(MedicalIdFilter.fromId(getUUID())));

        List<MedicalResourceInternal> resources =
                mTransactionManager.readMedicalResourcesByIds(parcel);

        assertThat(resources).isEmpty();
    }
}
