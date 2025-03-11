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

import static com.android.server.healthconnect.testing.storage.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.aidl.DeleteUsingFiltersRequestParcel;
import android.health.connect.aidl.RecordIdFiltersParcel;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.internal.datatypes.RecordInternal;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppOpLogsHelper;
import com.android.server.healthconnect.storage.request.DeleteTransactionRequest;
import com.android.server.healthconnect.testing.storage.TransactionTestUtils;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class TransactionManagerTest {
    private static final String TEST_PACKAGE_NAME = "package.name";

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public final TemporaryFolder mEnvironmentDataDir = new TemporaryFolder();

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;
    @Mock private AppOpLogsHelper mAppOpLogsHelper;

    private TransactionTestUtils mTransactionTestUtils;
    private TransactionManager mTransactionManager;
    private AppInfoHelper mAppInfoHelper;
    private AccessLogsHelper mAccessLogsHelper;
    private UserHandle mUserHandle;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .setAppOpLogsHelper(mAppOpLogsHelper)
                        .setEnvironmentDataDirectory(mEnvironmentDataDir.getRoot())
                        .build();
        mTransactionManager = healthConnectInjector.getTransactionManager();
        mAppInfoHelper = healthConnectInjector.getAppInfoHelper();
        mAccessLogsHelper = healthConnectInjector.getAccessLogsHelper();
        mUserHandle = context.getUser();

        mTransactionTestUtils = new TransactionTestUtils(healthConnectInjector);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
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
        mTransactionManager.deleteAllRecords(
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
        mTransactionManager.deleteAllRecords(
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
        mTransactionManager.deleteAllRecords(
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
        mTransactionManager.deleteAllRecords(
                new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel, mAppInfoHelper),
                /* shouldRecordDeleteAccessLogs= */ true,
                mAccessLogsHelper);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs(mUserHandle);
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
        mTransactionManager.deleteAllRecords(
                new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel, mAppInfoHelper),
                /* shouldRecordDeleteAccessLogs= */ false,
                mAccessLogsHelper);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs(mUserHandle);
        assertThat(result).isEmpty();
    }
}
