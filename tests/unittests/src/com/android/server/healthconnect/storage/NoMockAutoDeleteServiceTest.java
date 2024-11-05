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

import static com.android.server.healthconnect.storage.datatypehelpers.StepsRecordHelper.STEPS_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import android.database.Cursor;
import android.health.connect.HealthConnectManager;
import android.health.connect.internal.datatypes.RecordInternal;
import android.os.Environment;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.StepsRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.ReadTableRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

public class NoMockAutoDeleteServiceTest {
    @Rule(order = 1)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Rule(order = 2)
    public final HealthConnectDatabaseTestRule testRule = new HealthConnectDatabaseTestRule();

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    private static final String TEST_PACKAGE_NAME = "package.name";

    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private HealthConnectInjector mHealthConnectInjector;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        PreferenceHelper.clearInstanceForTest();

        StorageContext context = testRule.getDatabaseContext();
        mTransactionManager = testRule.getTransactionManager();
        DatabaseHelper.clearAllData(mTransactionManager);
        DeviceInfoHelper.resetInstanceForTest();

        mHealthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setTransactionManager(mTransactionManager)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .build();
        mTransactionTestUtils = new TransactionTestUtils(context, mHealthConnectInjector);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
    }

    @Test
    public void startAutoDelete_changeLogsGenerated() {
        String uuid =
                mTransactionTestUtils
                        .insertRecords(TEST_PACKAGE_NAME, createStepsRecord(4000, 5000, 100))
                        .get(0);
        RecordHelper<?> helper = new StepsRecordHelper();
        try (Cursor cursor = mTransactionManager.read(new ReadTableRequest(STEPS_TABLE_NAME))) {
            List<RecordInternal<?>> records =
                    helper.getInternalRecords(
                            cursor,
                            mHealthConnectInjector.getDeviceInfoHelper(),
                            mHealthConnectInjector.getAppInfoHelper());
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getUuid()).isEqualTo(UUID.fromString(uuid));
        }

        AutoDeleteService.setRecordRetentionPeriodInDays(
                30, mHealthConnectInjector.getPreferenceHelper());
        assertThat(
                        AutoDeleteService.getRecordRetentionPeriodInDays(
                                mHealthConnectInjector.getPreferenceHelper()))
                .isEqualTo(30);
        AutoDeleteService.startAutoDelete(
                testRule.getDatabaseContext(),
                mHealthConnectInjector.getHealthDataCategoryPriorityHelper(),
                mHealthConnectInjector.getPreferenceHelper(),
                mHealthConnectInjector.getAppInfoHelper(),
                mHealthConnectInjector.getTransactionManager(),
                mHealthConnectInjector.getAccessLogsHelper(),
                mHealthConnectInjector.getActivityDateHelper());

        try (Cursor cursor = mTransactionManager.read(new ReadTableRequest(STEPS_TABLE_NAME))) {
            List<RecordInternal<?>> records =
                    helper.getInternalRecords(
                            cursor,
                            mHealthConnectInjector.getDeviceInfoHelper(),
                            mHealthConnectInjector.getAppInfoHelper());
            assertThat(records).isEmpty();
        }

        assertThat(mTransactionTestUtils.getAllDeletedUuids())
                .containsExactly(UUID.fromString(uuid));
    }
}
