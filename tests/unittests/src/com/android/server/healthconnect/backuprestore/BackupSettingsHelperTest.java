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

package healthconnect.backuprestore;

import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.AUTO_DELETE_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.DISTANCE_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.ENERGY_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.HEIGHT_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.TEMPERATURE_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.BackupSettingsHelper.WEIGHT_UNIT_PREF_KEY;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.AutoDeleteFrequency;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.DEFAULT_DISTANCE_UNIT;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.DEFAULT_ENERGY_UNIT;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.DEFAULT_HEIGHT_UNIT;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.DEFAULT_TEMPERATURE_UNIT;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.DEFAULT_WEIGHT_UNIT;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.DistanceUnit;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.EnergyUnit;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.HeightUnit;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.TemperatureUnit;
import static com.android.server.healthconnect.backuprestore.CloudBackupSettings.WeightUnit;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthDataCategory;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.net.Uri;
import android.os.Environment;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.FakePreferenceHelper;
import com.android.server.healthconnect.backuprestore.BackupSettingsHelper;
import com.android.server.healthconnect.backuprestore.CloudBackupSettings;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.StorageContext;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class BackupSettingsHelperTest {

    private static final String TEST_PACKAGE_NAME = "package.name";
    private static final String TEST_PACKAGE_NAME_2 = "other.app";
    private static final Uri TEST_URI = Uri.parse("content://exports/hcbackup.zip");

    private PreferenceHelper mPreferenceHelper;
    private HealthDataCategoryPriorityHelper mPriorityHelper;
    private TransactionManager mTransactionManager;
    private BackupSettingsHelper mBackupSettingsHelper;
    private ExportImportSettingsStorage mExportImportSettingsStorage;

    @Rule(order = 1)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Rule(order = 2)
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG);

        HealthConnectInjector.resetInstanceForTest();

        StorageContext context = mDatabaseTestRule.getDatabaseContext();
        mPreferenceHelper = new FakePreferenceHelper();
        mExportImportSettingsStorage = mock(ExportImportSettingsStorage.class);

        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setPreferenceHelper(mPreferenceHelper)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .setExportImportSettingsStorage(mExportImportSettingsStorage)
                        .build();

        mTransactionManager = healthConnectInjector.getTransactionManager();
        TransactionTestUtils transactionTestUtils = new TransactionTestUtils(healthConnectInjector);
        transactionTestUtils.insertApp(TEST_PACKAGE_NAME);
        transactionTestUtils.insertApp(TEST_PACKAGE_NAME_2);
        mPriorityHelper = healthConnectInjector.getHealthDataCategoryPriorityHelper();

        mBackupSettingsHelper =
                new BackupSettingsHelper(
                        mPriorityHelper, mPreferenceHelper, mExportImportSettingsStorage);

        when(mExportImportSettingsStorage.getUri()).thenReturn(TEST_URI);
    }

    @After
    public void tearDown() throws Exception {
        DatabaseHelper.clearAllData(mTransactionManager);
    }

    @Test
    public void emptyPriorityList_setsPriorityListAsEmptyList() {
        Map<Integer, List<Long>> priorityMapImmutable =
                mPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable();
        assertThat(priorityMapImmutable).isEmpty();

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        Map<Integer, List<Long>> actualResult = userSettings.getPriorityListMapSetting();

        assertThat(actualResult).isEmpty();
    }

    @Test
    public void oneCategoryPriorityList_setsPriorityListCorrectly() throws IOException {
        mPriorityHelper.setPriorityOrder(
                HealthDataCategory.ACTIVITY, List.of(TEST_PACKAGE_NAME, TEST_PACKAGE_NAME_2));
        Map<Integer, List<Long>> expectedPriorityList =
                mPriorityHelper.getHealthDataCategoryToAppIdPriorityMapImmutable();
        assertThat(expectedPriorityList).isNotEmpty();

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        Map<Integer, List<Long>> actualPriorityList = userSettings.getPriorityListMapSetting();

        assertThat(actualPriorityList).isEqualTo(expectedPriorityList);
    }

    @Test
    public void defaultUnitPreferences_setsUnitPreferencesCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                TEMPERATURE_UNIT_PREF_KEY, DEFAULT_TEMPERATURE_UNIT.toString());
        mPreferenceHelper.insertOrReplacePreference(
                ENERGY_UNIT_PREF_KEY, DEFAULT_ENERGY_UNIT.toString());
        mPreferenceHelper.insertOrReplacePreference(
                HEIGHT_UNIT_PREF_KEY, DEFAULT_HEIGHT_UNIT.toString());
        mPreferenceHelper.insertOrReplacePreference(
                WEIGHT_UNIT_PREF_KEY, DEFAULT_WEIGHT_UNIT.toString());
        mPreferenceHelper.insertOrReplacePreference(
                DISTANCE_UNIT_PREF_KEY, DEFAULT_DISTANCE_UNIT.toString());

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getTemperatureUnitSetting()).isEqualTo(DEFAULT_TEMPERATURE_UNIT);
        assertThat(userSettings.getEnergyUnitSetting()).isEqualTo(DEFAULT_ENERGY_UNIT);
        assertThat(userSettings.getWeightUnitSetting()).isEqualTo(DEFAULT_WEIGHT_UNIT);
        assertThat(userSettings.getHeightUnitSetting()).isEqualTo(DEFAULT_HEIGHT_UNIT);
        assertThat(userSettings.getDistanceUnitSetting()).isEqualTo(DEFAULT_DISTANCE_UNIT);
    }

    @Test
    public void nonDefaultUnitPreference_setsUnitPreferencesCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                TEMPERATURE_UNIT_PREF_KEY, TemperatureUnit.KELVIN.toString());
        mPreferenceHelper.insertOrReplacePreference(
                ENERGY_UNIT_PREF_KEY, EnergyUnit.KILOJOULE.toString());
        mPreferenceHelper.insertOrReplacePreference(
                HEIGHT_UNIT_PREF_KEY, HeightUnit.FEET.toString());
        mPreferenceHelper.insertOrReplacePreference(
                WEIGHT_UNIT_PREF_KEY, WeightUnit.POUND.toString());
        mPreferenceHelper.insertOrReplacePreference(
                DISTANCE_UNIT_PREF_KEY, DistanceUnit.MILES.toString());

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getTemperatureUnitSetting()).isEqualTo(TemperatureUnit.KELVIN);
        assertThat(userSettings.getEnergyUnitSetting()).isEqualTo(EnergyUnit.KILOJOULE);
        assertThat(userSettings.getWeightUnitSetting()).isEqualTo(WeightUnit.POUND);
        assertThat(userSettings.getHeightUnitSetting()).isEqualTo(HeightUnit.FEET);
        assertThat(userSettings.getDistanceUnitSetting()).isEqualTo(DistanceUnit.MILES);
    }

    @Test
    public void exportSettingsDaily_setsExportSettingsCorrectly() {
        when(mExportImportSettingsStorage.getScheduledExportPeriodInDays()).thenReturn(1);

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        ScheduledExportSettings expectedSettings =
                new ScheduledExportSettings.Builder().setPeriodInDays(1).setUri(TEST_URI).build();
        ScheduledExportSettings actualSettings = userSettings.getScheduledExportSettings();

        assertThat(actualSettings).isNotNull();
        assertThat(actualSettings).isEqualTo(expectedSettings);
    }

    @Test
    public void exportSettingsWeekly_setsExportSettingsCorrectly() {
        when(mExportImportSettingsStorage.getScheduledExportPeriodInDays()).thenReturn(7);

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        ScheduledExportSettings expectedSettings =
                new ScheduledExportSettings.Builder().setPeriodInDays(7).setUri(TEST_URI).build();
        ScheduledExportSettings actualSettings = userSettings.getScheduledExportSettings();

        assertThat(actualSettings).isNotNull();
        assertThat(actualSettings).isEqualTo(expectedSettings);
    }

    @Test
    public void exportSettingsMonthly_setsExportSettingsCorrectly() {
        when(mExportImportSettingsStorage.getScheduledExportPeriodInDays()).thenReturn(30);

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        ScheduledExportSettings expectedSettings =
                new ScheduledExportSettings.Builder().setPeriodInDays(30).setUri(TEST_URI).build();
        ScheduledExportSettings actualSettings = userSettings.getScheduledExportSettings();

        assertThat(actualSettings).isNotNull();
        assertThat(actualSettings).isEqualTo(expectedSettings);
    }

    @Test
    public void autoDeleteSettingsOff_setsExportSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_PREF_KEY, AutoDeleteFrequency.AUTO_DELETE_RANGE_NEVER.toString());

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getAutoDeleteSetting())
                .isEqualTo(AutoDeleteFrequency.AUTO_DELETE_RANGE_NEVER);
    }

    @Test
    public void autoDeleteSettingsThreeMonths_setsExportSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_PREF_KEY,
                AutoDeleteFrequency.AUTO_DELETE_RANGE_THREE_MONTHS.toString());

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getAutoDeleteSetting())
                .isEqualTo(AutoDeleteFrequency.AUTO_DELETE_RANGE_THREE_MONTHS);
    }

    @Test
    public void autoDeleteSettingsEighteenMonths_setsExportSettingsCorrectly() {
        mPreferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_PREF_KEY,
                AutoDeleteFrequency.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS.toString());

        CloudBackupSettings userSettings = mBackupSettingsHelper.collectUserSettings();

        assertThat(userSettings.getAutoDeleteSetting())
                .isEqualTo(AutoDeleteFrequency.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS);
    }
}
