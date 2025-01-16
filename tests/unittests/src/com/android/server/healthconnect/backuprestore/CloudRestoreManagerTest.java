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

import static com.android.server.healthconnect.backuprestore.RecordProtoConverter.PROTO_VERSION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.health.connect.backuprestore.BackupChange;
import android.health.connect.backuprestore.BackupSettings;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.proto.backuprestore.BackupData;
import com.android.server.healthconnect.proto.backuprestore.Record;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ReadAccessLogsHelper;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.testing.fixtures.EnvironmentFixture;
import com.android.server.healthconnect.testing.fixtures.SQLiteDatabaseFixture;
import com.android.server.healthconnect.testing.storage.TransactionTestUtils;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.List;
import java.util.UUID;

/** Unit test for class {@link CloudRestoreManager}. */
@RunWith(AndroidJUnit4.class)
public class CloudRestoreManagerTest {

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .addStaticMockFixtures(EnvironmentFixture::new, SQLiteDatabaseFixture::new)
                    .build();

    private AppInfoHelper mAppInfoHelper;
    private DeviceInfoHelper mDeviceInfoHelper;
    private AccessLogsHelper mAccessLogsHelper;
    private ReadAccessLogsHelper mReadAccessLogsHelper;
    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private CloudRestoreManager mCloudRestoreManager;
    private RecordProtoConverter mRecordProtoConverter;

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
        mDeviceInfoHelper = healthConnectInjector.getDeviceInfoHelper();
        mAccessLogsHelper = healthConnectInjector.getAccessLogsHelper();
        mReadAccessLogsHelper = healthConnectInjector.getReadAccessLogsHelper();

        mRecordProtoConverter = new RecordProtoConverter();
        mCloudRestoreManager =
                new CloudRestoreManager(mTransactionManager, mDeviceInfoHelper, mAppInfoHelper);
        mTransactionTestUtils = new TransactionTestUtils(healthConnectInjector);
    }

    @Test
    public void whenPushSettingsForRestoreCalled_unsupportedOperationExceptionThrown() {
        BackupSettings backupSettings = new BackupSettings(0, new byte[0]);
        assertThrows(
                UnsupportedOperationException.class,
                () -> mCloudRestoreManager.pushSettingsForRestore(backupSettings));
    }

    @Test
    public void canRestore() {
        assertThat(mCloudRestoreManager.canRestore(PROTO_VERSION - 1)).isTrue();
        assertThat(mCloudRestoreManager.canRestore(PROTO_VERSION)).isTrue();
        assertThat(mCloudRestoreManager.canRestore(PROTO_VERSION + 1)).isFalse();
    }

    @Test
    public void pushChangesForRestore_restoresChanges() {
        Record stepsRecord =
                com.android.server.healthconnect.backuprestore.ProtoTestData.generateRecord(
                        RecordTypeIdentifier.RECORD_TYPE_STEPS);
        BackupChange stepsChange =
                new BackupChange(
                        stepsRecord.getUuid(),
                        false,
                        BackupData.newBuilder().setRecord(stepsRecord).build().toByteArray());
        BackupChange deletionChange = new BackupChange(UUID.randomUUID().toString(), true, null);
        Record bloodPressureRecord =
                com.android.server.healthconnect.backuprestore.ProtoTestData.generateRecord(
                        RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE);
        BackupChange bloodPressureChange =
                new BackupChange(
                        bloodPressureRecord.getUuid(),
                        false,
                        BackupData.newBuilder()
                                .setRecord(bloodPressureRecord)
                                .build()
                                .toByteArray());

        mCloudRestoreManager.pushChangesForRestore(
                List.of(stepsChange, deletionChange, bloodPressureChange));

        ReadTransactionRequest request =
                mTransactionTestUtils.getReadTransactionRequest(
                        ImmutableMap.of(
                                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                                List.of(UUID.fromString(stepsRecord.getUuid())),
                                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE,
                                List.of(UUID.fromString(bloodPressureRecord.getUuid()))));
        List<RecordInternal<?>> records =
                mTransactionManager.readRecordsByIds(
                        request,
                        mAppInfoHelper,
                        mAccessLogsHelper,
                        mDeviceInfoHelper,
                        mReadAccessLogsHelper,
                        /* shouldRecordAccessLog= */ false);
        assertThat(records).hasSize(2);
        // TODO: b/369801384 - Handle missing app name & last modified time being updated
        assertThat(mRecordProtoConverter.toRecordProto(records.get(0)))
                .isEqualTo(
                        stepsRecord.toBuilder()
                                .clearAppName()
                                .setLastModifiedTime(records.get(0).getLastModifiedTime())
                                .build());
        assertThat(mRecordProtoConverter.toRecordProto(records.get(1)))
                .isEqualTo(
                        bloodPressureRecord.toBuilder()
                                .clearAppName()
                                .setLastModifiedTime(records.get(1).getLastModifiedTime())
                                .build());
    }
}
