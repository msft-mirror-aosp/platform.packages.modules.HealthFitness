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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.os.Environment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.core.app.ApplicationProvider;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.TransactionManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
public class BackupChangeTokenHelperTest {

    private static final String TEST_DATA_TABLE_NAME = "step_records_table";
    private static final long TEST_DATA_TABLE_PAGE_TOKEN = 1;
    private static final String TEST_CHANGE_LOGS_REQUEST_TOKEN = "1";

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(Environment.class).build();

    @Rule(order = 3)
    public final HealthConnectDatabaseTestRule mHealthConnectDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private TransactionManager mTransactionManager;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setFirstGrantTimeManager(mock(FirstGrantTimeManager.class))
                        .setHealthPermissionIntentAppsTracker(
                                mock(HealthPermissionIntentAppsTracker.class))
                        .build();
        mTransactionManager = healthConnectInjector.getTransactionManager();
    }

    @Test
    public void getTableName() {
        assertThat(BackupChangeTokenHelper.getTableName()).isEqualTo("backup_change_token_table");
    }

    @Test
    public void getBackupChangeTokenRowId() {
        String backupChangeTokenRowId =
                BackupChangeTokenHelper.getBackupChangeTokenRowId(
                        mTransactionManager,
                        TEST_DATA_TABLE_NAME,
                        TEST_DATA_TABLE_PAGE_TOKEN,
                        TEST_CHANGE_LOGS_REQUEST_TOKEN);

        assertThat(backupChangeTokenRowId).isEqualTo("1");

        String anotherBackupChangeTokenRowId =
                BackupChangeTokenHelper.getBackupChangeTokenRowId(
                        mTransactionManager, null, -1, null);
        assertThat(anotherBackupChangeTokenRowId).isEqualTo("2");
    }

    @Test
    public void getBackupChangeToken_withNullValues() {
        String backupChangeTokenRowId =
                BackupChangeTokenHelper.getBackupChangeTokenRowId(
                        mTransactionManager, null, -1, null);

        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, backupChangeTokenRowId);
        assertThat(backupChangeToken.getChangeLogsRequestToken()).isNull();
        assertThat(backupChangeToken.getDataTablePageToken()).isEqualTo(-1);
        assertThat(backupChangeToken.getDataTableName()).isNull();
    }

    @Test
    public void getBackupChangeToken() {
        String backupChangeTokenRowId =
                BackupChangeTokenHelper.getBackupChangeTokenRowId(
                        mTransactionManager,
                        TEST_DATA_TABLE_NAME,
                        TEST_DATA_TABLE_PAGE_TOKEN,
                        TEST_CHANGE_LOGS_REQUEST_TOKEN);

        BackupChangeTokenHelper.BackupChangeToken backupChangeToken =
                BackupChangeTokenHelper.getBackupChangeToken(
                        mTransactionManager, backupChangeTokenRowId);
        assertThat(backupChangeToken.getChangeLogsRequestToken())
                .isEqualTo(TEST_CHANGE_LOGS_REQUEST_TOKEN);
        assertThat(backupChangeToken.getDataTablePageToken()).isEqualTo(TEST_DATA_TABLE_PAGE_TOKEN);
        assertThat(backupChangeToken.getDataTableName()).isEqualTo(TEST_DATA_TABLE_NAME);
    }
}
