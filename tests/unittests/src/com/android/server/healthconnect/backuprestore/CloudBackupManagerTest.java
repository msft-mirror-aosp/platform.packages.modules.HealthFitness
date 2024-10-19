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

import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.backuprestore.GetChangesForBackupResponse;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
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
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/** Unit test for class {@link CloudBackupManager}. */
@RunWith(AndroidJUnit4.class)
public class CloudBackupManagerTest {
    private static final String TEST_PACKAGE_NAME = "test.package.name";
    private static final long TEST_START_TIME_IN_MILLIS = 2000;
    private static final long TEST_END_TIME_IN_MILLIS = 3000;
    private static final int TEST_STEP_COUNT = 1345;

    @Rule(order = 1)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(Environment.class).build();

    @Rule(order = 2)
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private TransactionTestUtils mTransactionTestUtils;
    private CloudBackupManager mCloudBackupManager;

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
        HealthConnectMappings healthConnectMappings =
                healthConnectInjector.getHealthConnectMappings();
        InternalHealthConnectMappings internalHealthConnectMappings =
                healthConnectInjector.getInternalHealthConnectMappings();

        mCloudBackupManager =
                new CloudBackupManager(
                        transactionManager,
                        appInfoHelper,
                        accessLogsHelper,
                        deviceInfoHelper,
                        healthConnectMappings,
                        internalHealthConnectMappings);
    }

    @After
    public void tearDown() {
        AppInfoHelper.resetInstanceForTest();
        AccessLogsHelper.resetInstanceForTest();
        DeviceInfoHelper.resetInstanceForTest();
    }

    @Test
    public void getChangesForBackup_changeTokenIsNotNull_throwsUnsupportedOperationException() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> mCloudBackupManager.getChangesForBackup("testChangeToken"));
    }

    @Test
    public void getChangesForBackup_changeTokenIsNull_succeed() {
        mTransactionTestUtils.insertRecords(
                TEST_PACKAGE_NAME,
                createStepsRecord(
                        TEST_START_TIME_IN_MILLIS, TEST_END_TIME_IN_MILLIS, TEST_STEP_COUNT));

        GetChangesForBackupResponse response = mCloudBackupManager.getChangesForBackup(null);

        assertThat(response.getChanges().size()).isEqualTo(1);
        assertThat(response.getNextChangeToken()).isEqualTo("placeHolderPageToken");
    }
}
