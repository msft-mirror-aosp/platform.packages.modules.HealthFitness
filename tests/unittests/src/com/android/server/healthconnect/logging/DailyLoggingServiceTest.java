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

package com.android.server.healthconnect.logging;

import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_PERMISSION_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_PHR_STORAGE_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_PHR_USAGE_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_STORAGE_STATS;
import static android.health.HealthFitnessStatsLog.HEALTH_CONNECT_USAGE_STATS;
import static android.health.connect.HealthPermissions.READ_DISTANCE;
import static android.health.connect.HealthPermissions.READ_EXERCISE;
import static android.health.connect.HealthPermissions.READ_STEPS;

import static com.android.healthfitness.flags.Flags.FLAG_PERMISSION_METRICS;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.health.HealthFitnessStatsLog;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseStatsCollector;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class DailyLoggingServiceTest {

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(HealthFitnessStatsLog.class).build();

    @Mock private UsageStatsCollector mUsageStatsCollector;
    @Mock private DatabaseStatsCollector mDatabaseStatsCollector;
    @Captor private ArgumentCaptor<List<String>> mStringListCaptor;

    private static final String CONNECTED_APP_PACKAGE_NAME = "connected.app";
    private static final String CONNECTED_APP_TWO_PACKAGE_NAME = "connected.app.two";

    @Test
    public void testDatabaseLogsStats() {
        when(mDatabaseStatsCollector.getDatabaseSize()).thenReturn(1L);
        when(mDatabaseStatsCollector.getNumberOfChangeLogs()).thenReturn(2L);
        when(mDatabaseStatsCollector.getNumberOfInstantRecordRows()).thenReturn(3L);
        when(mDatabaseStatsCollector.getNumberOfIntervalRecordRows()).thenReturn(4L);
        when(mDatabaseStatsCollector.getNumberOfSeriesRecordRows()).thenReturn(5L);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_STORAGE_STATS),
                                /* databaseSize */ eq(1L),
                                /* numberOfInstantRecords */ eq(3L),
                                /* numberOfIntervalRecords */ eq(4L),
                                /* numberOfSeriesRecords */ eq(5L),
                                /* numberOfChangeLogs */ eq(2L)),
                times(1));
    }

    @Test
    public void testDatabaseLogsStats_userDoesNotUseHealthConnect() {
        when(mDatabaseStatsCollector.getDatabaseSize()).thenReturn(0L);
        when(mDatabaseStatsCollector.getNumberOfChangeLogs()).thenReturn(0L);
        when(mDatabaseStatsCollector.getNumberOfInstantRecordRows()).thenReturn(0L);
        when(mDatabaseStatsCollector.getNumberOfIntervalRecordRows()).thenReturn(0L);
        when(mDatabaseStatsCollector.getNumberOfSeriesRecordRows()).thenReturn(0L);

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
    public void testDailyUsageStatsLogs_oneConnectedApp_twoAvailableApps_userNotMonthlyActive() {

        when(mUsageStatsCollector.getNumberOfAppsCompatibleWithHealthConnect()).thenReturn(2);
        when(mUsageStatsCollector.getPackagesHoldingHealthPermissions())
                .thenReturn(Map.of(CONNECTED_APP_PACKAGE_NAME, List.of(READ_DISTANCE)));
        when(mUsageStatsCollector.isUserMonthlyActive()).thenReturn(false);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), /* connectedAppsCount */
                                eq(1), /* availableAppsCount */
                                eq(2), /* isUserMonthlyActive */
                                eq(false)),
                times(1));
    }

    @Test
    public void testDailyUsageStatsLogs_oneConnectedApp_twoAvailableApps_userMonthlyActive() {

        when(mUsageStatsCollector.getNumberOfAppsCompatibleWithHealthConnect()).thenReturn(2);
        when(mUsageStatsCollector.getPackagesHoldingHealthPermissions())
                .thenReturn(Map.of(CONNECTED_APP_PACKAGE_NAME, List.of(READ_DISTANCE)));
        when(mUsageStatsCollector.isUserMonthlyActive()).thenReturn(true);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_USAGE_STATS), /* connectedAppsCount */
                                eq(1), /* availableAppsCount */
                                eq(2), /* isUserMonthlyActive */
                                eq(true)),
                times(1));
    }

    @Test
    @EnableFlags(FLAG_PERMISSION_METRICS)
    public void permissionMetricsEnabled_twoConnectedApps_testPermissionsStatsLogs() {

        when(mUsageStatsCollector.getPackagesHoldingHealthPermissions())
                .thenReturn(
                        Map.of(
                                CONNECTED_APP_PACKAGE_NAME,
                                List.of(READ_DISTANCE, READ_EXERCISE),
                                CONNECTED_APP_TWO_PACKAGE_NAME,
                                List.of(READ_STEPS)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PERMISSION_STATS),
                                eq(CONNECTED_APP_PACKAGE_NAME),
                                eq(new String[] {"READ_DISTANCE", "READ_EXERCISE"})),
                times(1));
        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PERMISSION_STATS),
                                eq(CONNECTED_APP_TWO_PACKAGE_NAME),
                                eq(
                                        new String[] {
                                            "READ_STEPS",
                                        })),
                times(1));
    }

    @Test
    @DisableFlags(FLAG_PERMISSION_METRICS)
    public void permissionMetricsDisabled_oneConnectedApps_testPermissionsStatsDoNotLog() {
        when(mUsageStatsCollector.getPackagesHoldingHealthPermissions())
                .thenReturn(
                        Map.of(
                                CONNECTED_APP_PACKAGE_NAME,
                                List.of(READ_DISTANCE, READ_EXERCISE),
                                CONNECTED_APP_TWO_PACKAGE_NAME,
                                List.of(READ_STEPS)));

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PERMISSION_STATS),
                                eq(CONNECTED_APP_PACKAGE_NAME),
                                eq(new String[] {"READ_DISTANCE"})),
                never());
    }

    @Test
    @DisableFlags(FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY)
    public void phrStats_flagDisabled_expectNoLogs() {
        when(mUsageStatsCollector.getNumberOfAppsCompatibleWithHealthConnect()).thenReturn(1);

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
                () -> HealthFitnessStatsLog.write(eq(HEALTH_CONNECT_PHR_STORAGE_STATS), anyInt()),
                never());
    }

    @Test
    @EnableFlags({
        FLAG_PERSONAL_HEALTH_RECORD,
        FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY
    })
    public void phrStats_flagEnabledAndIsMonthlyActiveUser_expectCorrectLogs() {
        when(mUsageStatsCollector.getNumberOfAppsCompatibleWithHealthConnect()).thenReturn(1);
        when(mUsageStatsCollector.isPhrMonthlyActiveUser()).thenReturn(true);
        when(mUsageStatsCollector.getMedicalDataSourcesCount()).thenReturn(101);
        when(mUsageStatsCollector.getMedicalResourcesCount()).thenReturn(204);
        when(mUsageStatsCollector.getGrantedPhrAppsCount()).thenReturn(1L);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PHR_USAGE_STATS),
                                /* medicalDataSourcesCount */ eq(101),
                                /* medicalResourcesCount */ eq(204),
                                /* isPhrMonthlyActiveUser */ eq(true),
                                /* phrAppsCount */ eq(1)),
                times(1));
    }

    @Test
    @EnableFlags({
        FLAG_PERSONAL_HEALTH_RECORD,
        FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY
    })
    public void phrStats_flagEnabledAndIsNotMonthlyActiveUser_expectCorrectLogs() {
        when(mUsageStatsCollector.getNumberOfAppsCompatibleWithHealthConnect()).thenReturn(1);
        when(mUsageStatsCollector.isPhrMonthlyActiveUser()).thenReturn(false);
        when(mUsageStatsCollector.getMedicalDataSourcesCount()).thenReturn(101);
        when(mUsageStatsCollector.getMedicalResourcesCount()).thenReturn(204);
        when(mUsageStatsCollector.getGrantedPhrAppsCount()).thenReturn(0L);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);

        ExtendedMockito.verify(
                () ->
                        HealthFitnessStatsLog.write(
                                eq(HEALTH_CONNECT_PHR_USAGE_STATS),
                                /* medicalDataSourcesCount */ eq(101),
                                /* medicalResourcesCount */ eq(204),
                                /* isPhrMonthlyActiveUser */ eq(false),
                                /* phrAppsCount */ eq(0)),
                times(1));
    }

    @Test
    @EnableFlags({
        FLAG_PERSONAL_HEALTH_RECORD,
        FLAG_PERSONAL_HEALTH_RECORD_DATABASE,
        FLAG_PERSONAL_HEALTH_RECORD_TELEMETRY
    })
    public void phrStats_flagEnabled_phrDataExists_expectCorrectPhrDbStatsLogs() {
        when(mDatabaseStatsCollector.getFileBytes(
                        eq(
                                Set.of(
                                        MedicalDataSourceHelper.getMainTableName(),
                                        MedicalResourceHelper.getMainTableName(),
                                        MedicalResourceIndicesHelper.getTableName()))))
                .thenReturn(101L);
        when(mUsageStatsCollector.getMedicalResourcesCount()).thenReturn(1);

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
        when(mDatabaseStatsCollector.getFileBytes(mStringListCaptor.capture())).thenReturn(101L);
        when(mUsageStatsCollector.getMedicalResourcesCount()).thenReturn(0);
        when(mUsageStatsCollector.getMedicalDataSourcesCount()).thenReturn(0);

        DailyLoggingService.logDailyMetrics(mUsageStatsCollector, mDatabaseStatsCollector);

        Mockito.verify(mDatabaseStatsCollector, never()).getFileBytes(any());
        ExtendedMockito.verify(
                () -> HealthFitnessStatsLog.write(eq(HEALTH_CONNECT_PHR_STORAGE_STATS), anyInt()),
                never());
    }
}
