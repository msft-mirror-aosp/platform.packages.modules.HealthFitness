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

package healthconnect.logging;

import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_EXPORT_IMPORT_STATS_REPORTED;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_PERMISSION_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_PHR_STORAGE_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_PHR_USAGE_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_STORAGE_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_USAGE_STATS;
import static android.health.connect.HealthPermissions.READ_DISTANCE;
import static android.health.connect.HealthPermissions.READ_EXERCISE;
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES;
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_CONDITIONS;
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_PERSONAL_DETAILS;
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_VACCINES;
import static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;
import static android.healthconnect.cts.utils.DataFactory.NOW;

import static com.android.healthfitness.flags.Flags.FLAG_PERMISSION_METRICS;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.DatabaseUtils;
import android.health.HealthFitnessStatsLog;
import android.health.connect.HealthConnectManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.logging.DailyLoggingService;
import com.android.server.healthconnect.logging.UsageStatsCollector;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.BloodPressureRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseStatsCollector;
import com.android.server.healthconnect.storage.datatypehelpers.FakeTimeSource;
import com.android.server.healthconnect.storage.datatypehelpers.HeartRateRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HeightRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.SpeedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.StepsRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.TotalCaloriesBurnedRecordHelper;
import com.android.server.healthconnect.storage.datatypehelpers.Vo2MaxRecordHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DailyLoggingServiceTest {

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthFitnessStatsLog.class)
                    .mockStatic(DatabaseUtils.class)
                    .mockStatic(TransactionManager.class)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(PreferenceHelper.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Context mContext;

    @Mock private PackageInfo mPackageInfoConnectedApp;
    @Mock private PackageInfo mPackageInfoConnectedAppTwo;
    @Mock private PackageInfo mPackageInfoNotHoldingPermission;
    @Mock private PackageInfo mPackageInfoNotConnectedApp;
    @Mock private PackageInfo mPackageInfoNotConnectedAppTwo;
    @Mock private PreferenceHelper mPreferenceHelper;
    @Mock private AccessLogsHelper mAccessLogsHelper;
    @Mock private TransactionManager mTransactionManager;
    @Mock private MedicalDataSourceHelper mMedicalDataSourceHelper;
    @Mock private MedicalResourceHelper mMedicalResourceHelper;

    private DatabaseStatsCollector mDatabaseStatsCollector;
    private UsageStatsCollector mUsageStatsCollector;

    private FakeTimeSource mFakeTimeSource;
    @Captor private ArgumentCaptor<List<String>> mStringListCaptor;

    private static final String NOT_HEALTH_PERMISSION = "NOT_HEALTH_PERMISSION";
    private static final String READ_STEPS = "android.permission.health.READ_STEPS";
    private static final String WRITE_STEPS = "android.permission.health.WRITE_STEPS";
    private static final String WRITE_EXERCISE = "android.permission.health.WRITE_EXERCISE";
    private static final String READ_STEPS_SHORTENED = "READ_STEPS";
    private static final String WRITE_STEPS_SHORTENED = "WRITE_STEPS";
    private static final String WRITE_EXERCISE_SHORTENED = "WRITE_EXERCISE";
    private static final String USER_MOST_RECENT_ACCESS_LOG_TIME =
            "USER_MOST_RECENT_ACCESS_LOG_TIME";
    private static final String EXPORT_PERIOD_PREFERENCE_KEY = "export_period_key";
    private static final String CONNECTED_APP_PACKAGE_NAME = "connected.app";
    private static final String CONNECTED_APP_TWO_PACKAGE_NAME = "connected.app.two";
    private static final String NOT_CONNECTED_APP_PACKAGE_NAME = "not.connected.app";
    private static final String NOT_CONNECTED_APP_PACKAGE_NAME_TWO = "not.connected.app.2";
    private static final String NOT_HOLDING_HC_PERMISSIONS_APP_PACKAGE_NAME =
            "not.holding.permission.app";

    @Before
    public void mockStatsLog() {
        mFakeTimeSource = new FakeTimeSource(NOW);
        ExtendedMockito.doReturn(true)
                .when(() -> HealthConnectManager.isHealthPermission(mContext, READ_STEPS));
        ExtendedMockito.doReturn(false)
                .when(
                        () ->
                                HealthConnectManager.isHealthPermission(
                                        mContext, NOT_HEALTH_PERMISSION));
        ExtendedMockito.doReturn(Set.of(READ_STEPS, WRITE_STEPS, WRITE_EXERCISE))
                .when(() -> HealthConnectManager.getHealthPermissions(mContext));
        mPackageInfoConnectedApp.requestedPermissions = new String[] {READ_STEPS, WRITE_STEPS};
        mPackageInfoConnectedApp.requestedPermissionsFlags =
                new int[] {
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        mPackageInfoConnectedApp.packageName = CONNECTED_APP_PACKAGE_NAME;

        mPackageInfoConnectedAppTwo.requestedPermissions =
                new String[] {READ_STEPS, WRITE_STEPS, WRITE_EXERCISE};
        mPackageInfoConnectedAppTwo.requestedPermissionsFlags =
                new int[] {
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        mPackageInfoConnectedAppTwo.packageName = CONNECTED_APP_TWO_PACKAGE_NAME;

        mPackageInfoNotHoldingPermission.requestedPermissions =
                new String[] {NOT_HEALTH_PERMISSION};
        mPackageInfoNotHoldingPermission.requestedPermissionsFlags =
                new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};
        mPackageInfoNotHoldingPermission.packageName = NOT_HOLDING_HC_PERMISSIONS_APP_PACKAGE_NAME;

        mPackageInfoNotConnectedApp.requestedPermissions = new String[] {READ_STEPS};
        mPackageInfoNotConnectedApp.requestedPermissionsFlags =
                new int[] {PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION};
        mPackageInfoNotConnectedApp.packageName = NOT_CONNECTED_APP_PACKAGE_NAME;

        mPackageInfoNotConnectedAppTwo.requestedPermissions = new String[] {READ_STEPS};
        mPackageInfoNotConnectedAppTwo.requestedPermissionsFlags =
                new int[] {PackageInfo.REQUESTED_PERMISSION_NEVER_FOR_LOCATION};
        mPackageInfoNotConnectedAppTwo.packageName = NOT_CONNECTED_APP_PACKAGE_NAME_TWO;

        mDatabaseStatsCollector = Mockito.spy(new DatabaseStatsCollector(mTransactionManager));
        mUsageStatsCollector =
                new UsageStatsCollector(
                        mContext,
                        mPreferenceHelper,
                        mAccessLogsHelper,
                        mFakeTimeSource,
                        mMedicalResourceHelper,
                        mMedicalDataSourceHelper);
    }

    @Test
    public void testDatabaseLogsStats() {

        when(mTransactionManager.getDatabaseSize()).thenReturn(1L);
        when(mTransactionManager.getNumberOfEntriesInTheTable(any())).thenReturn(0L);

        for (String tableName :
                new String[] {
                    ChangeLogsHelper.TABLE_NAME,
                    BloodPressureRecordHelper.BLOOD_PRESSURE_RECORD_TABLE_NAME,
                    HeightRecordHelper.HEIGHT_RECORD_TABLE_NAME,
                    Vo2MaxRecordHelper.VO2_MAX_RECORD_TABLE_NAME,
                    StepsRecordHelper.STEPS_TABLE_NAME,
                    TotalCaloriesBurnedRecordHelper.TOTAL_CALORIES_BURNED_RECORD_TABLE_NAME,
                    SpeedRecordHelper.TABLE_NAME,
                    HeartRateRecordHelper.TABLE_NAME
                }) {
            when(mTransactionManager.getNumberOfEntriesInTheTable(tableName)).thenReturn(2L);
        }

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_STORAGE_STATS),
                                eq(1L), // Database size
                                eq(6L), // Instant Records i.e. 2 for each BloodPressure,
                                // Height, Vo2Max
                                eq(4L), // Interval Records i.e. 2 for each Steps, Total
                                // Calories Burned
                                eq(4L), // Series Records i.e. 2 for each Speed, Heart Rate
                                eq(2L)), // 2 Changelog records
                times(1));
    }

    @Test
    public void testDatabaseLogsStats_userDoesNotUseHealthConnect() {
        when(mTransactionManager.getDatabaseSize()).thenReturn(1L);
        when(mTransactionManager.getNumberOfEntriesInTheTable(any())).thenReturn(0L);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_STORAGE_STATS),
                                anyLong(),
                                anyLong(),
                                anyLong(),
                                anyLong(),
                                anyLong()),
                never());
    }

    @Test
    public void testDailyUsageStatsLogs_oneConnected_oneAvailable_oneNotAvailableApp() {

        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 0));
        when(mContext.getPackageManager().getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoConnectedApp, mPackageInfoNotHoldingPermission));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        // Makes sure we do not have count any app that does not have Health Connect permission
        // declared in the manifest as a connected or an available app.
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(1), eq(1), eq(true)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_oneConnected_oneAvailableApp() {

        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 0));
        when(mContext.getPackageManager().getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoConnectedApp));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(1), eq(1), eq(true)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_zeroConnected_twoAvailableApps() {

        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 31));
        when(mContext.getPackageManager().getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoNotConnectedApp, mPackageInfoNotConnectedAppTwo));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(0), eq(2), eq(false)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_zeroConnected_zeroAvailableApps() {

        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 1));
        when(mContext.getPackageManager().getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoNotHoldingPermission));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 1)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(0), eq(0), eq(true)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_healthConnectAccessedPreviousDay_userMonthlyActive() {
        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 1));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 1)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(0), eq(0), eq(true)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_healthConnectAccessed31DaysAgo_userNotMonthlyActive() {
        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 31));
        when(mContext.getPackageManager().getInstalledPackages(any()))
                .thenReturn(List.of(mPackageInfoNotConnectedApp, mPackageInfoNotConnectedAppTwo));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), eq(0), eq(2), eq(false)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_withConfiguredExportFrequency_logsCorrectExportFrequency() {
        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 1));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 1)));
        when(mPreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY))
                .thenReturn(String.valueOf(7));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_EXPORT_IMPORT_STATS_REPORTED), eq(7)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_noConfiguredExportFrequency_logsExportFrequencyAsNever() {
        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 31));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 1)));
        when(mPreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY)).thenReturn(null);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_EXPORT_IMPORT_STATS_REPORTED), eq(0)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_userDoesNotUseHealthConnect() {
        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 31));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 31)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), anyInt(), anyInt(), anyBoolean()),
                never());
    }

    @Test
    @EnableFlags(FLAG_PERMISSION_METRICS)
    public void permissionMetricsEnabled_oneConnectedApp_testPermissionsStatsLogs() {

        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 0));
        when(mContext.getPackageManager().getInstalledPackages(any()))
                .thenReturn(
                        List.of(
                                mPackageInfoConnectedApp,
                                mPackageInfoNotHoldingPermission,
                                mPackageInfoNotConnectedApp));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PERMISSION_STATS),
                                eq(CONNECTED_APP_PACKAGE_NAME),
                                eq(new String[] {READ_STEPS_SHORTENED, WRITE_STEPS_SHORTENED})),
                times(1));
    }

    @Test
    @EnableFlags(FLAG_PERMISSION_METRICS)
    public void permissionMetricsEnabled_twoConnectedApps_testPermissionsStatsLogs() {

        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 0));
        when(mContext.getPackageManager().getInstalledPackages(any()))
                .thenReturn(
                        List.of(
                                mPackageInfoConnectedApp,
                                mPackageInfoNotHoldingPermission,
                                mPackageInfoNotConnectedApp,
                                mPackageInfoConnectedAppTwo));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PERMISSION_STATS),
                                eq(CONNECTED_APP_PACKAGE_NAME),
                                eq(new String[] {READ_STEPS_SHORTENED, WRITE_STEPS_SHORTENED})),
                times(1));
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PERMISSION_STATS),
                                eq(CONNECTED_APP_TWO_PACKAGE_NAME),
                                eq(
                                        new String[] {
                                            READ_STEPS_SHORTENED,
                                            WRITE_STEPS_SHORTENED,
                                            WRITE_EXERCISE_SHORTENED
                                        })),
                times(1));
    }

    @Test
    @DisableFlags(FLAG_PERMISSION_METRICS)
    public void permissionMetricsDisabled_oneConnectedApps_testPermissionsStatsDoNotLog() {

        when(mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp())
                .thenReturn(subtractDaysFromInstantNow(/* numberOfDays= */ 0));
        when(mContext.getPackageManager().getInstalledPackages(any()))
                .thenReturn(
                        List.of(
                                mPackageInfoConnectedApp,
                                mPackageInfoNotHoldingPermission,
                                mPackageInfoNotConnectedApp));
        when(mPreferenceHelper.getPreference(USER_MOST_RECENT_ACCESS_LOG_TIME))
                .thenReturn(String.valueOf(subtractDaysFromInstantNow(/* numberOfDays= */ 0)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PERMISSION_STATS),
                                eq(CONNECTED_APP_PACKAGE_NAME),
                                eq(new String[] {READ_STEPS_SHORTENED, WRITE_STEPS_SHORTENED})),
                never());
    }

    @Test
    @DisableFlags(FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY)
    public void phrStats_flagDisabled_expectNoLogs() {

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PHR_USAGE_STATS),
                                anyInt(),
                                anyInt(),
                                anyInt(),
                                anyInt()),
                never());

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HealthFitnessStatsLog.HEALTH_CONNECT_PHR_STORAGE_STATS),
                                anyInt()),
                never());
    }

    @Test
    @EnableFlags({
        FLAG_PERSONAL_HEALTH_RECORD,
        FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY
    })
    public void phrStats_flagEnabledAndIsMonthlyActiveUser_expectCorrectLogs() {
        when(mMedicalDataSourceHelper.getMedicalDataSourcesCount()).thenReturn(101);
        when(mMedicalResourceHelper.getMedicalResourcesCount()).thenReturn(204);
        when(mPreferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp())
                .thenReturn(NOW.minus(29, ChronoUnit.DAYS));
        mFakeTimeSource.setInstant(NOW);
        mockGrantedPermissions(
                Map.of(
                        CONNECTED_APP_PACKAGE_NAME,
                        List.of(WRITE_STEPS, READ_STEPS),
                        CONNECTED_APP_PACKAGE_NAME + "1",
                        List.of(WRITE_STEPS, READ_STEPS, WRITE_MEDICAL_DATA),
                        CONNECTED_APP_PACKAGE_NAME + "2",
                        List.of(WRITE_STEPS, READ_STEPS, READ_MEDICAL_DATA_VACCINES),
                        CONNECTED_APP_PACKAGE_NAME + "3",
                        List.of(
                                WRITE_STEPS,
                                READ_STEPS,
                                WRITE_MEDICAL_DATA,
                                READ_MEDICAL_DATA_CONDITIONS)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PHR_USAGE_STATS),
                                eq(101),
                                eq(204),
                                /* isPhrMonthlyActiveUser */ eq(true),
                                anyInt()),
                times(1));
    }

    @Test
    @EnableFlags({
        FLAG_PERSONAL_HEALTH_RECORD,
        FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY
    })
    public void phrStats_flagEnabledAndIsNotMonthlyActiveUser_expectCorrectLogs() {
        when(mMedicalDataSourceHelper.getMedicalDataSourcesCount()).thenReturn(101);
        when(mMedicalResourceHelper.getMedicalResourcesCount()).thenReturn(204);
        when(mPreferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp())
                .thenReturn(NOW.minus(31, ChronoUnit.DAYS));
        mFakeTimeSource.setInstant(NOW);
        mockGrantedPermissions(
                Map.of(
                        CONNECTED_APP_PACKAGE_NAME,
                        List.of(WRITE_STEPS, READ_STEPS),
                        CONNECTED_APP_PACKAGE_NAME + "1",
                        List.of(WRITE_MEDICAL_DATA),
                        CONNECTED_APP_PACKAGE_NAME + "2",
                        List.of(WRITE_STEPS, READ_EXERCISE),
                        CONNECTED_APP_PACKAGE_NAME + "3",
                        List.of(WRITE_STEPS, READ_DISTANCE)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PHR_USAGE_STATS),
                                eq(101),
                                eq(204),
                                /* isPhrMonthlyActiveUser */ eq(false),
                                eq(0)),
                times(1));
    }

    @Test
    @EnableFlags({
        FLAG_PERSONAL_HEALTH_RECORD,
        FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY
    })
    public void phrStats_flagEnabledAndIsNotMonthlyActiveUserDueToNoTimeStamp_expectCorrectLogs() {
        when(mPreferenceHelper.getPhrLastReadMedicalResourcesApiTimeStamp()).thenReturn(null);
        mockGrantedPermissions(
                Map.of(
                        CONNECTED_APP_PACKAGE_NAME,
                        List.of(WRITE_STEPS, READ_STEPS, READ_MEDICAL_DATA_VACCINES),
                        CONNECTED_APP_PACKAGE_NAME + "1",
                        List.of(WRITE_STEPS, READ_STEPS, WRITE_MEDICAL_DATA),
                        CONNECTED_APP_PACKAGE_NAME + "2",
                        List.of(
                                READ_MEDICAL_DATA_PERSONAL_DETAILS,
                                READ_MEDICAL_DATA_PERSONAL_DETAILS),
                        CONNECTED_APP_PACKAGE_NAME + "3",
                        List.of(
                                WRITE_STEPS,
                                READ_DISTANCE,
                                READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PHR_USAGE_STATS),
                                anyInt(),
                                anyInt(),
                                /* isPhrMonthlyActiveUser */ eq(false),
                                eq(3)),
                times(1));
    }

    private void mockGrantedPermissions(
            Map<String, List<String>> packageNameToGrantedPermissionsMap) {
        // This is needed so HealthConnectManager recognizes all the permissions in
        // packageNameToGrantedPermissionsMap as health permissions.
        ExtendedMockito.doReturn(
                        packageNameToGrantedPermissionsMap.values().stream()
                                .flatMap(List::stream)
                                .collect(Collectors.toSet()))
                .when(() -> HealthConnectManager.getHealthPermissions(mContext));

        List<PackageInfo> installedPackages =
                packageNameToGrantedPermissionsMap.entrySet().stream()
                        .map(
                                entry -> {
                                    String packageName = entry.getKey();
                                    List<String> grantedPermissions = entry.getValue();
                                    PackageInfo packageInfo = mock(PackageInfo.class);
                                    packageInfo.requestedPermissions =
                                            grantedPermissions.toArray(new String[0]);
                                    packageInfo.requestedPermissionsFlags =
                                            grantedPermissions.stream()
                                                    .mapToInt(
                                                            perm ->
                                                                    PackageInfo
                                                                            .REQUESTED_PERMISSION_GRANTED)
                                                    .toArray();
                                    packageInfo.packageName = packageName;
                                    return packageInfo;
                                })
                        .toList();
        PackageManager packageManager = mContext.getPackageManager();
        clearInvocations(packageManager);
        when(packageManager.getInstalledPackages(any())).thenReturn(installedPackages);
    }

    @Test
    @EnableFlags({
        FLAG_PERSONAL_HEALTH_RECORD,
        FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY
    })
    public void phrStats_flagEnabled_phrDataExists_expectCorrectPhrDbStatsLogs() {
        doReturn(101L)
                .when(mDatabaseStatsCollector)
                .getFileBytes(
                        eq(
                                Set.of(
                                        MedicalDataSourceHelper.getMainTableName(),
                                        MedicalResourceHelper.getMainTableName(),
                                        MedicalResourceIndicesHelper.getTableName())));
        when(mMedicalResourceHelper.getMedicalResourcesCount()).thenReturn(1);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        ExtendedMockito.verify(
                () -> HealthFitnessStatsLog.write(eq(HEALTH_CONNECT_PHR_STORAGE_STATS), eq(101L)),
                times(1));
    }

    @Test
    @EnableFlags({
        FLAG_PERSONAL_HEALTH_RECORD,
        FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY
    })
    public void phrStats_flagEnabled_noPhRdata_expectNoPhrDbStatsLogs() {
        doReturn(101L).when(mDatabaseStatsCollector).getFileBytes(mStringListCaptor.capture());
        when(mMedicalResourceHelper.getMedicalResourcesCount()).thenReturn(0);
        when(mMedicalDataSourceHelper.getMedicalDataSourcesCount()).thenReturn(0);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);
        verify(mDatabaseStatsCollector, never()).getFileBytes(any());
        ExtendedMockito.verify(
                () -> HealthFitnessStatsLog.write(eq(HEALTH_CONNECT_PHR_STORAGE_STATS), anyInt()),
                never());
    }

    private long subtractDaysFromInstantNow(int numberOfDays) {
        return Instant.now().minus(numberOfDays, ChronoUnit.DAYS).toEpochMilli();
    }
}
