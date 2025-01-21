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

import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.AUTO_DELETE_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.DISTANCE_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.ENERGY_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.HEIGHT_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.TEMPERATURE_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.WEIGHT_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.RecordProtoConverter.PROTO_VERSION;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.DistanceUnitProto.DISTANCE_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.EnergyUnitProto.ENERGY_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.HeightUnitProto.HEIGHT_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.TemperatureUnitProto.TEMPERATURE_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.WeightUnitProto.WEIGHT_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.storage.ExportImportSettingsStorage.EXPORT_PERIOD_PREFERENCE_KEY;
import static com.android.server.healthconnect.storage.ExportImportSettingsStorage.EXPORT_URI_PREFERENCE_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.health.connect.HealthDataCategory;
import android.health.connect.backuprestore.BackupSettings;
import android.health.connect.backuprestore.RestoreChange;
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
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Unit test for class {@link CloudRestoreManager}. */
@RunWith(AndroidJUnit4.class)
public class CloudRestoreManagerTest {

    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String TEST_PACKAGE_NAME_2 = "other.app";
    private static final String TEST_PACKAGE_NAME_3 = "another.app";
    private static final String EXPORT_URI = "content://path/to/export/location";
    private static final String EXPORT_URI_2 = "content://different/path/to/export/to";

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
    private HealthDataCategoryPriorityHelper mPriorityHelper;
    private PreferenceHelper mPreferenceHelper;

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
        mPriorityHelper = healthConnectInjector.getHealthDataCategoryPriorityHelper();
        mPreferenceHelper = healthConnectInjector.getPreferenceHelper();

        mRecordProtoConverter = new RecordProtoConverter();
        mCloudRestoreManager =
                new CloudRestoreManager(
                        mTransactionManager,
                        mDeviceInfoHelper,
                        mAppInfoHelper,
                        mPriorityHelper,
                        mPreferenceHelper);
        mTransactionTestUtils = new TransactionTestUtils(healthConnectInjector);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME_2);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME_3);
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
        RestoreChange stepsChange =
                new RestoreChange(
                        BackupData.newBuilder().setRecord(stepsRecord).build().toByteArray());
        Record bloodPressureRecord =
                com.android.server.healthconnect.backuprestore.ProtoTestData.generateRecord(
                        RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE);
        RestoreChange bloodPressureChange =
                new RestoreChange(
                        BackupData.newBuilder()
                                .setRecord(bloodPressureRecord)
                                .build()
                                .toByteArray());

        mCloudRestoreManager.pushChangesForRestore(List.of(stepsChange, bloodPressureChange));

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
        assertThat(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .containsExactly(
                        RecordTypeIdentifier.RECORD_TYPE_STEPS,
                        Set.of(stepsRecord.getPackageName()),
                        RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE,
                        Set.of(bloodPressureRecord.getPackageName()));
    }

    @Test
    public void whenPushSettingsForRestoreCalled_noExportSettings_settingsSuccessfullyRestored() {
        BackupSettingsHelper backupSettingsHelper =
                new BackupSettingsHelper(mPriorityHelper, mPreferenceHelper, mAppInfoHelper);

        SettingsRecord.PrioritizedAppIds expectedAppIds =
                SettingsRecord.PrioritizedAppIds.newBuilder()
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_2))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_3))
                        .build();
        Map<Integer, SettingsRecord.PrioritizedAppIds> expectedPriorityList = new HashMap<>();
        expectedPriorityList.put(HealthDataCategory.ACTIVITY, expectedAppIds);

        setupInitialSettings(false);
        SettingsRecord settingsToRestore = createSettingsToRestore(false, false);
        BackupSettings backupSettings = new BackupSettings(1, settingsToRestore.toByteArray());

        mCloudRestoreManager.pushSettingsForRestore(backupSettings);

        SettingsRecord currentSettings = backupSettingsHelper.collectUserSettings();

        assertSettingsRecordCorrectlyUpdated(
                settingsToRestore, currentSettings, expectedPriorityList);
    }

    @Test
    public void whenPushSettingsForRestoreCalled_withExportSettings_settingsSuccessfullyRestored() {
        BackupSettingsHelper backupSettingsHelper =
                new BackupSettingsHelper(mPriorityHelper, mPreferenceHelper, mAppInfoHelper);

        SettingsRecord.PrioritizedAppIds expectedAppIds =
                SettingsRecord.PrioritizedAppIds.newBuilder()
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_2))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_3))
                        .build();
        Map<Integer, SettingsRecord.PrioritizedAppIds> expectedPriorityList = new HashMap<>();
        expectedPriorityList.put(HealthDataCategory.ACTIVITY, expectedAppIds);

        setupInitialSettings(true);
        SettingsRecord settingsToRestore = createSettingsToRestore(true, false);

        BackupSettings backupSettings = new BackupSettings(1, settingsToRestore.toByteArray());

        mCloudRestoreManager.pushSettingsForRestore(backupSettings);

        SettingsRecord currentSettings = backupSettingsHelper.collectUserSettings();

        assertSettingsRecordCorrectlyUpdated(
                settingsToRestore, currentSettings, expectedPriorityList);
    }

    @Test
    public void
            whenPushSettingsForRestoreCalled_withUnspecifiedEnums_settingsSuccessfullyRestored() {
        BackupSettingsHelper backupSettingsHelper =
                new BackupSettingsHelper(mPriorityHelper, mPreferenceHelper, mAppInfoHelper);

        mPreferenceHelper.insertOrReplacePreference(
                ENERGY_UNIT_PREF_KEY, SettingsRecord.EnergyUnitProto.CALORIE.toString());

        SettingsRecord.PrioritizedAppIds expectedAppIds =
                SettingsRecord.PrioritizedAppIds.newBuilder()
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_2))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_3))
                        .build();
        Map<Integer, SettingsRecord.PrioritizedAppIds> expectedPriorityList = new HashMap<>();
        expectedPriorityList.put(HealthDataCategory.ACTIVITY, expectedAppIds);

        setupInitialSettings(false);
        SettingsRecord settingsToRestore = createSettingsToRestore(false, true);
        BackupSettings backupSettings = new BackupSettings(1, settingsToRestore.toByteArray());

        mCloudRestoreManager.pushSettingsForRestore(backupSettings);

        SettingsRecord currentSettings = backupSettingsHelper.collectUserSettings();

        assertSettingsRecordCorrectlyUpdated(
                settingsToRestore, currentSettings, expectedPriorityList);
    }

    private void setupInitialSettings(boolean includeExportSettings) {
        mPriorityHelper.setPriorityOrder(
                HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2));
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_PREF_KEY,
                SettingsRecord.AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_NEVER.toString());
        mPreferenceHelper.insertOrReplacePreference(
                ENERGY_UNIT_PREF_KEY, SettingsRecord.EnergyUnitProto.CALORIE.toString());
        mPreferenceHelper.insertOrReplacePreference(
                TEMPERATURE_UNIT_PREF_KEY, SettingsRecord.TemperatureUnitProto.CELSIUS.toString());
        mPreferenceHelper.insertOrReplacePreference(
                HEIGHT_UNIT_PREF_KEY, SettingsRecord.HeightUnitProto.CENTIMETERS.toString());
        mPreferenceHelper.insertOrReplacePreference(
                WEIGHT_UNIT_PREF_KEY, SettingsRecord.WeightUnitProto.POUND.toString());
        mPreferenceHelper.insertOrReplacePreference(
                DISTANCE_UNIT_PREF_KEY, SettingsRecord.DistanceUnitProto.KILOMETERS.toString());

        if (includeExportSettings) {
            mPreferenceHelper.insertOrReplacePreference(EXPORT_URI_PREFERENCE_KEY, EXPORT_URI);
            mPreferenceHelper.insertOrReplacePreference(EXPORT_PERIOD_PREFERENCE_KEY, "7");
        }
    }

    private SettingsRecord createSettingsToRestore(
            boolean includeExportSettings, boolean setEnergyUnitAsUnspecified) {

        SettingsRecord.PrioritizedAppIds newAppIds =
                SettingsRecord.PrioritizedAppIds.newBuilder()
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_2))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_3))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME))
                        .build();
        Map<Integer, SettingsRecord.PrioritizedAppIds> newPriorityList = new HashMap<>();
        newPriorityList.put(HealthDataCategory.ACTIVITY, newAppIds);

        SettingsRecord.Builder settingsRecordBuilder = SettingsRecord.newBuilder();

        if (includeExportSettings) {
            settingsRecordBuilder.setExportSettings(
                    SettingsRecord.ExportSettingsProto.newBuilder()
                            .setFrequency(30)
                            .setUri(EXPORT_URI_2)
                            .build());
        }

        SettingsRecord.EnergyUnitProto energyUnitSetting =
                setEnergyUnitAsUnspecified
                        ? ENERGY_UNIT_UNSPECIFIED
                        : SettingsRecord.EnergyUnitProto.KILOJOULE;

        settingsRecordBuilder
                .putAllPriorityList(newPriorityList)
                .setAutoDeleteFrequency(
                        SettingsRecord.AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_THREE_MONTHS)
                .setEnergyUnitSetting(energyUnitSetting)
                .setTemperatureUnitSetting(SettingsRecord.TemperatureUnitProto.KELVIN)
                .setHeightUnitSetting(SettingsRecord.HeightUnitProto.FEET)
                .setWeightUnitSetting(SettingsRecord.WeightUnitProto.KILOGRAM)
                .setDistanceUnitSetting(SettingsRecord.DistanceUnitProto.MILES);

        return settingsRecordBuilder.build();
    }

    private void assertSettingsRecordCorrectlyUpdated(
            SettingsRecord settingsFromBackup,
            SettingsRecord restoredSettings,
            Map<Integer, SettingsRecord.PrioritizedAppIds> expectedMergedPriorityList) {

        if (settingsFromBackup.getEnergyUnitSetting() == ENERGY_UNIT_UNSPECIFIED) {
            assertNotSame(ENERGY_UNIT_UNSPECIFIED, restoredSettings.getEnergyUnitSetting());
        } else {
            assertSame(
                    settingsFromBackup.getEnergyUnitSetting(),
                    restoredSettings.getEnergyUnitSetting());
        }

        assertNotSame(AUTO_DELETE_RANGE_UNSPECIFIED, restoredSettings.getAutoDeleteFrequency());
        assertSame(
                settingsFromBackup.getAutoDeleteFrequency(),
                restoredSettings.getAutoDeleteFrequency());

        assertNotSame(TEMPERATURE_UNIT_UNSPECIFIED, restoredSettings.getTemperatureUnitSetting());
        assertSame(
                settingsFromBackup.getTemperatureUnitSetting(),
                restoredSettings.getTemperatureUnitSetting());

        assertNotSame(HEIGHT_UNIT_UNSPECIFIED, restoredSettings.getHeightUnitSetting());
        assertSame(
                settingsFromBackup.getHeightUnitSetting(), restoredSettings.getHeightUnitSetting());

        assertNotSame(WEIGHT_UNIT_UNSPECIFIED, restoredSettings.getWeightUnitSetting());
        assertSame(
                settingsFromBackup.getWeightUnitSetting(), restoredSettings.getWeightUnitSetting());

        assertNotSame(DISTANCE_UNIT_UNSPECIFIED, restoredSettings.getDistanceUnitSetting());
        assertSame(
                settingsFromBackup.getDistanceUnitSetting(),
                restoredSettings.getDistanceUnitSetting());

        if (settingsFromBackup.hasExportSettings()) {
            assertThat(settingsFromBackup.getExportSettings())
                    .isEqualTo(restoredSettings.getExportSettings());
        }

        assertThat(expectedMergedPriorityList.get(HealthDataCategory.ACTIVITY).getAppIdList())
                .isEqualTo(
                        restoredSettings
                                .getPriorityListMap()
                                .get(HealthDataCategory.ACTIVITY)
                                .getAppIdList());
    }
}
