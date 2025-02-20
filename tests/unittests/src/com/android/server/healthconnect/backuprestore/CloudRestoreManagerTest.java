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

import static com.android.server.healthconnect.backuprestore.CloudBackupSettingsHelper.AUTO_DELETE_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettingsHelper.DISTANCE_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettingsHelper.ENERGY_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettingsHelper.HEIGHT_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettingsHelper.TEMPERATURE_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettingsHelper.WEIGHT_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.ProtoTestData.TEST_PACKAGE_NAME;
import static com.android.server.healthconnect.backuprestore.ProtoTestData.generateCoreRecord;
import static com.android.server.healthconnect.backuprestore.ProtoTestData.generateExerciseSession;
import static com.android.server.healthconnect.backuprestore.ProtoTestData.generateIntervalRecord;
import static com.android.server.healthconnect.backuprestore.ProtoTestData.generateRecord;
import static com.android.server.healthconnect.backuprestore.RecordProtoConverter.PROTO_VERSION;
import static com.android.server.healthconnect.proto.backuprestore.Settings.AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.Settings.DistanceUnitProto.DISTANCE_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.Settings.EnergyUnitProto.ENERGY_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.Settings.HeightUnitProto.HEIGHT_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.Settings.TemperatureUnitProto.TEMPERATURE_UNIT_UNSPECIFIED;
import static com.android.server.healthconnect.proto.backuprestore.Settings.WeightUnitProto.WEIGHT_UNIT_UNSPECIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

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
import com.android.server.healthconnect.proto.backuprestore.AppInfoMap;
import com.android.server.healthconnect.proto.backuprestore.BackupData;
import com.android.server.healthconnect.proto.backuprestore.Record;
import com.android.server.healthconnect.proto.backuprestore.Settings;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;
import com.android.server.healthconnect.testing.fixtures.EnvironmentFixture;
import com.android.server.healthconnect.testing.fixtures.SQLiteDatabaseFixture;
import com.android.server.healthconnect.testing.storage.TransactionTestUtils;

import com.google.common.collect.ImmutableMap;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Ignore;
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

    private static final String TEST_PACKAGE_NAME_2 = "other.app";
    private static final String TEST_PACKAGE_NAME_3 = "another.app";

    private static final AppInfoMap APP_INFO_MAP =
            AppInfoMap.newBuilder()
                    .putAppInfo(
                            TEST_PACKAGE_NAME,
                            Settings.AppInfo.newBuilder().setAppName("app name 1").build())
                    .putAppInfo(
                            TEST_PACKAGE_NAME_2,
                            Settings.AppInfo.newBuilder().setAppName("app name 2").build())
                    .putAppInfo(
                            TEST_PACKAGE_NAME_3,
                            Settings.AppInfo.newBuilder().setAppName("app name 3").build())
                    .build();

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .addStaticMockFixtures(EnvironmentFixture::new, SQLiteDatabaseFixture::new)
                    .build();

    private AppInfoHelper mAppInfoHelper;
    private DeviceInfoHelper mDeviceInfoHelper;
    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private CloudRestoreManager mCloudRestoreManager;
    private RecordProtoConverter mRecordProtoConverter;
    private HealthDataCategoryPriorityHelper mPriorityHelper;
    private PreferenceHelper mPreferenceHelper;
    private InternalHealthConnectMappings mMappings;

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
        mPriorityHelper = healthConnectInjector.getHealthDataCategoryPriorityHelper();
        mPreferenceHelper = healthConnectInjector.getPreferenceHelper();
        mMappings = healthConnectInjector.getInternalHealthConnectMappings();

        mRecordProtoConverter = new RecordProtoConverter();
        mCloudRestoreManager =
                new CloudRestoreManager(
                        mTransactionManager,
                        mDeviceInfoHelper,
                        mAppInfoHelper,
                        mPriorityHelper,
                        mPreferenceHelper);
        mTransactionTestUtils = new TransactionTestUtils(healthConnectInjector);
    }

    @Test
    public void canRestore() {
        assertThat(mCloudRestoreManager.canRestore(PROTO_VERSION - 1)).isTrue();
        assertThat(mCloudRestoreManager.canRestore(PROTO_VERSION)).isTrue();
        assertThat(mCloudRestoreManager.canRestore(PROTO_VERSION + 1)).isFalse();
    }

    @Test
    public void pushChangesForRestore_restoresChanges() {
        Record stepsRecord = generateRecord(RecordTypeIdentifier.RECORD_TYPE_STEPS);
        RestoreChange stepsChange =
                new RestoreChange(
                        BackupData.newBuilder().setRecord(stepsRecord).build().toByteArray());
        Record bloodPressureRecord =
                generateRecord(RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE);
        RestoreChange bloodPressureChange =
                new RestoreChange(
                        BackupData.newBuilder()
                                .setRecord(bloodPressureRecord)
                                .build()
                                .toByteArray());

        mCloudRestoreManager.pushChangesForRestore(
                List.of(stepsChange, bloodPressureChange), APP_INFO_MAP.toByteArray());

        ReadTransactionRequest request =
                mTransactionTestUtils.getReadTransactionRequest(
                        ImmutableMap.of(
                                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                                List.of(UUID.fromString(stepsRecord.getUuid())),
                                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE,
                                List.of(UUID.fromString(bloodPressureRecord.getUuid()))));
        List<RecordInternal<?>> records =
                mTransactionManager.readRecordsByIdsWithoutAccessLogs(
                        request, mAppInfoHelper, mDeviceInfoHelper);
        assertThat(records).hasSize(2);
        assertThat(mRecordProtoConverter.toRecordProto(records.get(0))).isEqualTo(stepsRecord);
        assertThat(mRecordProtoConverter.toRecordProto(records.get(1)))
                .isEqualTo(bloodPressureRecord);
        assertThat(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .containsExactly(
                        RecordTypeIdentifier.RECORD_TYPE_STEPS,
                        Set.of(stepsRecord.getPackageName()),
                        RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE,
                        Set.of(bloodPressureRecord.getPackageName()));
    }

    @Test
    @Ignore("Priority list merging is currently broken")
    public void whenPushSettingsForRestoreCalled_settingsSuccessfullyRestored() {
        CloudBackupSettingsHelper cloudBackupSettingsHelper =
                new CloudBackupSettingsHelper(mPriorityHelper, mPreferenceHelper, mAppInfoHelper);

        Settings.PrioritizedAppIds expectedAppIds =
                Settings.PrioritizedAppIds.newBuilder()
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_2))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_3))
                        .build();
        Map<Integer, Settings.PrioritizedAppIds> expectedPriorityList = new HashMap<>();
        expectedPriorityList.put(HealthDataCategory.ACTIVITY, expectedAppIds);

        setupInitialSettings();
        Settings settingsToRestore = createSettingsToRestore(false);
        BackupSettings backupSettings = new BackupSettings(settingsToRestore.toByteArray());

        mCloudRestoreManager.pushSettingsForRestore(backupSettings);

        Settings currentSettings = cloudBackupSettingsHelper.collectUserSettings();

        assertSettingsCorrectlyUpdated(settingsToRestore, currentSettings, expectedPriorityList);
    }

    @Test
    @Ignore("Priority list merging is currently broken")
    public void
            whenPushSettingsForRestoreCalled_withUnspecifiedEnums_settingsSuccessfullyRestored() {
        CloudBackupSettingsHelper cloudBackupSettingsHelper =
                new CloudBackupSettingsHelper(mPriorityHelper, mPreferenceHelper, mAppInfoHelper);

        mPreferenceHelper.insertOrReplacePreference(
                ENERGY_UNIT_PREF_KEY, Settings.EnergyUnitProto.CALORIE.toString());

        Settings.PrioritizedAppIds expectedAppIds =
                Settings.PrioritizedAppIds.newBuilder()
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_2))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_3))
                        .build();
        Map<Integer, Settings.PrioritizedAppIds> expectedPriorityList = new HashMap<>();
        expectedPriorityList.put(HealthDataCategory.ACTIVITY, expectedAppIds);

        setupInitialSettings();
        Settings settingsToRestore = createSettingsToRestore(true);
        BackupSettings backupSettings = new BackupSettings(settingsToRestore.toByteArray());

        mCloudRestoreManager.pushSettingsForRestore(backupSettings);

        Settings currentSettings = cloudBackupSettingsHelper.collectUserSettings();

        assertSettingsCorrectlyUpdated(settingsToRestore, currentSettings, expectedPriorityList);
    }

    @Test
    public void pushChangesForRestore_exerciseSession_withMissingTrainingPlan_removesReference() {
        Record exerciseSessionRecord =
                generateRecord(RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION);
        Record sessionWithPlanReference =
                exerciseSessionRecord.toBuilder()
                        .setIntervalRecord(
                                exerciseSessionRecord.getIntervalRecord().toBuilder()
                                        .setExerciseSession(
                                                exerciseSessionRecord
                                                        .getIntervalRecord()
                                                        .getExerciseSession()
                                                        .toBuilder()
                                                        .setPlannedExerciseSessionId(
                                                                UUID.randomUUID().toString())))
                        .build();

        mCloudRestoreManager.pushChangesForRestore(
                List.of(
                        new RestoreChange(
                                BackupData.newBuilder()
                                        .setRecord(sessionWithPlanReference)
                                        .build()
                                        .toByteArray())),
                APP_INFO_MAP.toByteArray());

        var restoredSession = readExerciseSession(exerciseSessionRecord.getUuid());
        assertThat(mRecordProtoConverter.toRecordProto(restoredSession))
                .isEqualTo(exerciseSessionRecord);
    }

    @Test
    public void pushChangesForRestore_exerciseSession_withTrainingPlanInChanges_keepsReference() {
        Record plannedExerciseSessionRecord =
                generateRecord(RecordTypeIdentifier.RECORD_TYPE_PLANNED_EXERCISE_SESSION);
        Record exerciseSessionRecord =
                generateCoreRecord()
                        .setIntervalRecord(
                                generateIntervalRecord()
                                        .setExerciseSession(
                                                generateExerciseSession().toBuilder()
                                                        .setPlannedExerciseSessionId(
                                                                plannedExerciseSessionRecord
                                                                        .getUuid())))
                        .build();

        mCloudRestoreManager.pushChangesForRestore(
                List.of(
                        new RestoreChange(
                                BackupData.newBuilder()
                                        .setRecord(plannedExerciseSessionRecord)
                                        .build()
                                        .toByteArray()),
                        new RestoreChange(
                                BackupData.newBuilder()
                                        .setRecord(exerciseSessionRecord)
                                        .build()
                                        .toByteArray())),
                APP_INFO_MAP.toByteArray());

        var restoredSession = readExerciseSession(exerciseSessionRecord.getUuid());
        assertThat(mRecordProtoConverter.toRecordProto(restoredSession))
                .isEqualTo(exerciseSessionRecord);
    }

    @Test
    public void
            pushChangesForRestore_exerciseSession_withTrainingPlanRestoredEarlier_keepsReference() {
        Record plannedExerciseSessionRecord =
                generateRecord(RecordTypeIdentifier.RECORD_TYPE_PLANNED_EXERCISE_SESSION);
        mCloudRestoreManager.pushChangesForRestore(
                List.of(
                        new RestoreChange(
                                BackupData.newBuilder()
                                        .setRecord(plannedExerciseSessionRecord)
                                        .build()
                                        .toByteArray())),
                APP_INFO_MAP.toByteArray());
        Record exerciseSessionRecord =
                generateCoreRecord()
                        .setIntervalRecord(
                                generateIntervalRecord()
                                        .setExerciseSession(
                                                generateExerciseSession().toBuilder()
                                                        .setPlannedExerciseSessionId(
                                                                plannedExerciseSessionRecord
                                                                        .getUuid())))
                        .build();
        mCloudRestoreManager.pushChangesForRestore(
                List.of(
                        new RestoreChange(
                                BackupData.newBuilder()
                                        .setRecord(exerciseSessionRecord)
                                        .build()
                                        .toByteArray())),
                APP_INFO_MAP.toByteArray());

        var restoredSession = readExerciseSession(exerciseSessionRecord.getUuid());
        assertThat(mRecordProtoConverter.toRecordProto(restoredSession))
                .isEqualTo(exerciseSessionRecord);
    }

    @Test
    public void restoreInvalidSettings_throwsException() {
        BackupSettings backupSettings = new BackupSettings(new byte[] {45, 36});
        assertThrows(
                IllegalArgumentException.class,
                () -> mCloudRestoreManager.pushSettingsForRestore(backupSettings));
    }

    @Test
    public void restoreInvalidChanges_skipsInvalidChange() {
        RestoreChange restoreChange = new RestoreChange(new byte[] {45, 36});
        // test that no exceptions are thrown
        mCloudRestoreManager.pushChangesForRestore(
                List.of(restoreChange), APP_INFO_MAP.toByteArray());
    }

    private void setupInitialSettings() {
        mPriorityHelper.setPriorityOrder(
                HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2));
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_PREF_KEY,
                Settings.AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_NEVER.toString());
        mPreferenceHelper.insertOrReplacePreference(
                ENERGY_UNIT_PREF_KEY, Settings.EnergyUnitProto.CALORIE.toString());
        mPreferenceHelper.insertOrReplacePreference(
                TEMPERATURE_UNIT_PREF_KEY, Settings.TemperatureUnitProto.CELSIUS.toString());
        mPreferenceHelper.insertOrReplacePreference(
                HEIGHT_UNIT_PREF_KEY, Settings.HeightUnitProto.CENTIMETERS.toString());
        mPreferenceHelper.insertOrReplacePreference(
                WEIGHT_UNIT_PREF_KEY, Settings.WeightUnitProto.POUND.toString());
        mPreferenceHelper.insertOrReplacePreference(
                DISTANCE_UNIT_PREF_KEY, Settings.DistanceUnitProto.KILOMETERS.toString());
    }

    private Settings createSettingsToRestore(boolean setEnergyUnitAsUnspecified) {

        Settings.PrioritizedAppIds newAppIds =
                Settings.PrioritizedAppIds.newBuilder()
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_2))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME_3))
                        .addAppId(mAppInfoHelper.getAppInfoId(TEST_PACKAGE_NAME))
                        .build();
        Map<Integer, Settings.PrioritizedAppIds> newPriorityList = new HashMap<>();
        newPriorityList.put(HealthDataCategory.ACTIVITY, newAppIds);

        Settings.Builder settingsRecordBuilder = Settings.newBuilder();

        Settings.EnergyUnitProto energyUnitSetting =
                setEnergyUnitAsUnspecified
                        ? ENERGY_UNIT_UNSPECIFIED
                        : Settings.EnergyUnitProto.KILOJOULE;

        settingsRecordBuilder
                .putAllPriorityList(newPriorityList)
                .setAutoDeleteFrequency(
                        Settings.AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_THREE_MONTHS)
                .setEnergyUnitSetting(energyUnitSetting)
                .setTemperatureUnitSetting(Settings.TemperatureUnitProto.KELVIN)
                .setHeightUnitSetting(Settings.HeightUnitProto.FEET)
                .setWeightUnitSetting(Settings.WeightUnitProto.KILOGRAM)
                .setDistanceUnitSetting(Settings.DistanceUnitProto.MILES);

        return settingsRecordBuilder.build();
    }

    private void assertSettingsCorrectlyUpdated(
            Settings settingsFromBackup,
            Settings restoredSettings,
            Map<Integer, Settings.PrioritizedAppIds> expectedMergedPriorityList) {

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

        assertThat(expectedMergedPriorityList.get(HealthDataCategory.ACTIVITY).getAppIdList())
                .isEqualTo(
                        restoredSettings
                                .getPriorityListMap()
                                .get(HealthDataCategory.ACTIVITY)
                                .getAppIdList());
    }

    @NotNull
    private RecordInternal<?> readExerciseSession(String sessionId) {
        ReadTransactionRequest request =
                new ReadTransactionRequest(
                        mAppInfoHelper,
                        /* packageName= */ "",
                        ImmutableMap.of(
                                RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION,
                                List.of(UUID.fromString(sessionId))),
                        /* startDateAccessMillis= */ 0,
                        Set.copyOf(
                                mMappings
                                        .getRecordHelper(
                                                RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION)
                                        .getExtraReadPermissions()),
                        /* isInForeground= */ true,
                        /* isReadingSelfData= */ false);
        List<RecordInternal<?>> records =
                mTransactionManager.readRecordsByIdsWithoutAccessLogs(
                        request, mAppInfoHelper, mDeviceInfoHelper);
        assertThat(records.size()).isEqualTo(1);
        return records.get(0);
    }
}
