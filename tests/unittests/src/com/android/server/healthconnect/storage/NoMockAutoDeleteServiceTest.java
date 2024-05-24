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
import android.health.connect.internal.datatypes.RecordInternal;

import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.StepsRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.ReadTableRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

public class NoMockAutoDeleteServiceTest {
    @Rule public final HealthConnectDatabaseTestRule testRule = new HealthConnectDatabaseTestRule();

    private static final String TEST_PACKAGE_NAME = "package.name";

    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;

    @Before
    public void setup() throws Exception {
        HealthConnectUserContext context = testRule.getUserContext();
        mTransactionManager = testRule.getTransactionManager();
        DatabaseHelper.clearAllData(mTransactionManager);
        mTransactionTestUtils = new TransactionTestUtils(context, mTransactionManager);
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
            List<RecordInternal<?>> records = helper.getInternalRecords(cursor);
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getUuid()).isEqualTo(UUID.fromString(uuid));
        }

        AutoDeleteService.setRecordRetentionPeriodInDays(30);
        assertThat(AutoDeleteService.getRecordRetentionPeriodInDays()).isEqualTo(30);
        AutoDeleteService.startAutoDelete(testRule.getUserContext());

        try (Cursor cursor = mTransactionManager.read(new ReadTableRequest(STEPS_TABLE_NAME))) {
            List<RecordInternal<?>> records = helper.getInternalRecords(cursor);
            assertThat(records).isEmpty();
        }

        assertThat(mTransactionTestUtils.getAllDeletedUuids())
                .containsExactly(UUID.fromString(uuid));
    }
}
