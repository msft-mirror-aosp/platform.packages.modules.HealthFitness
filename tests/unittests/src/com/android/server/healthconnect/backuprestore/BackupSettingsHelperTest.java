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
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.AutoDeleteFrequencyProto;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.DistanceUnitProto;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.EnergyUnitProto;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.HeightUnitProto;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.TemperatureUnitProto;
import static com.android.server.healthconnect.proto.backuprestore.SettingsRecord.WeightUnitProto;
import static com.android.server.healthconnect.storage.ExportImportSettingsStorage.EXPORT_PERIOD_PREFERENCE_KEY;
import static com.android.server.healthconnect.storage.ExportImportSettingsStorage.EXPORT_URI_PREFERENCE_KEY;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.health.connect.HealthDataCategory;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.ExportSettingsProto;
import com.android.server.healthconnect.proto.backuprestore.SettingsRecord.PrioritizedAppIds;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.testing.fakes.FakePreferenceHelper;
import com.android.server.healthconnect.testing.fixtures.EnvironmentFixture;
import com.android.server.healthconnect.testing.fixtures.SQLiteDatabaseFixture;
import com.android.server.healthconnect.testing.storage.TransactionTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class BackupSettingsHelperTest {

    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String TEST_PACKAGE_NAME_2 = "other.app";
    private static final Uri TEST_URI = Uri.parse("content://exports/hcbackup.zip");

    private PreferenceHelper mPreferenceHelper;
    private HealthDataCategoryPriorityHelper mPriorityHelper;
    private BackupSettingsHelper mBackupSettingsHelper;

    @Rule(order = 1)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .addStaticMockFixtures(EnvironmentFixture::new, SQLiteDatabaseFixture::new)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Mock private ExportImportSettingsStorage mExportImportSettingsStorage;
    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    @Before
    public void setUp() throws Exception {
        HealthConnectInjector.resetInstanceForTest();

        Context context = ApplicationProvider.getApplicationContext();
        mPreferenceHelper = new FakePreferenceHelper();

        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setPreferenceHelper(mPreferenceHelper)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .setExportImportSettingsStorage(mExportImportSettingsStorage)
                        .build();

        TransactionTestUtils transactionTestUtils = new TransactionTestUtils(healthConnectInjector);
        transactionTestUtils.insertApp(TEST_PACKAGE_NAME);
        transactionTestUtils.insertApp(TEST_PACKAGE_NAME_2);

        mPriorityHelper = healthConnectInjector.getHealthDataCategoryPriorityHelper();
        mBackupSettingsHelper = new BackupSettingsHelper(mPriorityHelper, mPreferenceHelper);
    }

    @Test
    public void emptyPriorityList_setsPriorityListAsEmptyList() {
        Map<Integer, List<Long>> priorityMapImmutable =
                mPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable();
        assertThat(priorityMapImmutable).isEmpty();

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        Map<Integer, PrioritizedAppIds> actualResult = userSettings.getPriorityListMap();

        assertThat(actualResult).isEmpty();
    }

    @Test
    public void oneCategoryPriorityList_setsPriorityListCorrectly() {
        mPriorityHelper.setPriorityOrder(
                HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2));
        Map<Integer, PrioritizedAppIds> expectedPriorityList = new HashMap<>();
        mPriorityHelper
                .getHealthDataCategoryToAppIdPriorityMapImmutable()
                .forEach(
                        (category, appIdList) -> {
                            expectedPriorityList.put(
                                    category,
                                    PrioritizedAppIds.newBuilder().addAllAppId(appIdList).build());
                        });

        assertThat(expectedPriorityList).isNotEmpty();

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getPriorityListMap()).isEqualTo(expectedPriorityList);
    }

    @Test
    public void defaultUnitPreferences_setsUnitPreferencesCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                TEMPERATURE_UNIT_PREF_KEY, TemperatureUnitProto.CELSIUS.toString());
        mPreferenceHelper.insertOrReplacePreference(
                ENERGY_UNIT_PREF_KEY, EnergyUnitProto.CALORIE.toString());
        mPreferenceHelper.insertOrReplacePreference(
                HEIGHT_UNIT_PREF_KEY, HeightUnitProto.CENTIMETERS.toString());
        mPreferenceHelper.insertOrReplacePreference(
                WEIGHT_UNIT_PREF_KEY, WeightUnitProto.POUND.toString());
        mPreferenceHelper.insertOrReplacePreference(
                DISTANCE_UNIT_PREF_KEY, DistanceUnitProto.KILOMETERS.toString());

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getTemperatureUnitSetting())
                .isEqualTo(TemperatureUnitProto.CELSIUS);
        assertThat(userSettings.getEnergyUnitSetting()).isEqualTo(EnergyUnitProto.CALORIE);
        assertThat(userSettings.getWeightUnitSetting()).isEqualTo(WeightUnitProto.POUND);
        assertThat(userSettings.getHeightUnitSetting()).isEqualTo(HeightUnitProto.CENTIMETERS);
        assertThat(userSettings.getDistanceUnitSetting()).isEqualTo(DistanceUnitProto.KILOMETERS);
    }

    @Test
    public void nonDefaultUnitPreference_setsUnitPreferencesCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                TEMPERATURE_UNIT_PREF_KEY, TemperatureUnitProto.KELVIN.toString());
        mPreferenceHelper.insertOrReplacePreference(
                ENERGY_UNIT_PREF_KEY, EnergyUnitProto.KILOJOULE.toString());
        mPreferenceHelper.insertOrReplacePreference(
                HEIGHT_UNIT_PREF_KEY, HeightUnitProto.FEET.toString());
        mPreferenceHelper.insertOrReplacePreference(
                WEIGHT_UNIT_PREF_KEY, WeightUnitProto.POUND.toString());
        mPreferenceHelper.insertOrReplacePreference(
                DISTANCE_UNIT_PREF_KEY, DistanceUnitProto.MILES.toString());

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getTemperatureUnitSetting()).isEqualTo(TemperatureUnitProto.KELVIN);
        assertThat(userSettings.getEnergyUnitSetting()).isEqualTo(EnergyUnitProto.KILOJOULE);
        assertThat(userSettings.getWeightUnitSetting()).isEqualTo(WeightUnitProto.POUND);
        assertThat(userSettings.getHeightUnitSetting()).isEqualTo(HeightUnitProto.FEET);
        assertThat(userSettings.getDistanceUnitSetting()).isEqualTo(DistanceUnitProto.MILES);
    }

    @Test
    public void exportSettingsDaily_setsExportSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(EXPORT_URI_PREFERENCE_KEY, TEST_URI.toString());
        mPreferenceHelper.insertOrReplacePreference(EXPORT_PERIOD_PREFERENCE_KEY, "1");

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        ExportSettingsProto exportSettingsProto =
                ExportSettingsProto.newBuilder()
                        .setUri(TEST_URI.toString())
                        .setFrequency(1)
                        .build();

        assertThat(userSettings.hasExportSettings()).isTrue();

        ExportSettingsProto actualSettings = userSettings.getExportSettings();

        assertThat(actualSettings).isNotNull();
        assertThat(actualSettings).isEqualTo(exportSettingsProto);
    }

    @Test
    public void exportSettingsWeekly_setsExportSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(EXPORT_URI_PREFERENCE_KEY, TEST_URI.toString());
        mPreferenceHelper.insertOrReplacePreference(EXPORT_PERIOD_PREFERENCE_KEY, "7");

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        ExportSettingsProto exportSettingsProto =
                ExportSettingsProto.newBuilder()
                        .setUri(TEST_URI.toString())
                        .setFrequency(7)
                        .build();

        assertThat(userSettings.hasExportSettings()).isTrue();

        ExportSettingsProto actualSettings = userSettings.getExportSettings();

        assertThat(actualSettings).isNotNull();
        assertThat(actualSettings).isEqualTo(exportSettingsProto);
    }

    @Test
    public void exportSettingsMonthly_setsExportSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(EXPORT_URI_PREFERENCE_KEY, TEST_URI.toString());
        mPreferenceHelper.insertOrReplacePreference(EXPORT_PERIOD_PREFERENCE_KEY, "30");

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        ExportSettingsProto exportSettingsProto =
                ExportSettingsProto.newBuilder()
                        .setUri(TEST_URI.toString())
                        .setFrequency(30)
                        .build();

        assertThat(userSettings.hasExportSettings()).isTrue();

        ExportSettingsProto actualSettings = userSettings.getExportSettings();

        assertThat(actualSettings).isNotNull();
        assertThat(actualSettings).isEqualTo(exportSettingsProto);
    }

    @Test
    public void autoDeleteSettingsOff_setsAutoDeleteSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_PREF_KEY, AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_NEVER.toString());

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getAutoDeleteFrequency())
                .isEqualTo(AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_NEVER);
    }

    @Test
    public void autoDeleteSettingsThreeMonths_setsAutoDeleteSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_PREF_KEY,
                AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_THREE_MONTHS.toString());

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getAutoDeleteFrequency())
                .isEqualTo(AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_THREE_MONTHS);
    }

    @Test
    public void autoDeleteSettingsEighteenMonths_setsAutoDeleteSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_PREF_KEY,
                AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS.toString());

        SettingsRecord userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getAutoDeleteFrequency())
                .isEqualTo(AutoDeleteFrequencyProto.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS);
    }
}
