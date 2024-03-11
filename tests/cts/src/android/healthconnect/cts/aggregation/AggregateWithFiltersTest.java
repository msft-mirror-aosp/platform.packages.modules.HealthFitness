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

package android.healthconnect.cts.aggregation;

import static android.health.connect.HealthDataCategory.ACTIVITY;
import static android.health.connect.datatypes.ElevationGainedRecord.ELEVATION_GAINED_TOTAL;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.aggregation.DataFactory.getTimeFilter;
import static android.healthconnect.cts.utils.DataFactory.getDataOrigin;
import static android.healthconnect.cts.utils.DataFactory.getDistanceRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.TestUtils.PKG_TEST_APP;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByDuration;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByPeriod;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.insertStepsRecordViaTestApp;
import static android.healthconnect.cts.utils.TestUtils.setupAggregation;
import static android.healthconnect.cts.utils.TestUtils.updatePriorityWithManageHealthDataPermission;

import static com.google.common.truth.Truth.assertThat;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;

import android.content.Context;
import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsGroupedByPeriodResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.units.Length;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.core.app.ApplicationProvider;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;

public class AggregateWithFiltersTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mPackageName = mContext.getPackageName();
    private final ZoneOffset mCurrentZone =
            ZoneOffset.systemDefault().getRules().getOffset(Instant.now());

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        deleteAllStagedRemoteData();
        setupAggregation(mPackageName, ACTIVITY);
    }

    @After
    public void tearDown() {
        deleteAllStagedRemoteData();
    }

    @Test
    public void dataOriginFilter_noFilter_everythingIncluded() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        insertRecords(
                List.of(
                        getStepsRecord(
                                100, startTime.plusMillis(1000), startTime.plusMillis(2000))));
        updatePriorityWithManageHealthDataPermission(
                ACTIVITY, ImmutableList.of(PKG_TEST_APP, mPackageName));

        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(
                                getTimeFilter(startTime.minus(1, HOURS), startTime.plus(1, HOURS)))
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();

        AggregateRecordsResponse<Long> response = getAggregateResponse(request);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(150);
        assertThat(response.getDataOrigins(STEPS_COUNT_TOTAL))
                .containsExactly(getDataOrigin(mPackageName), getDataOrigin(PKG_TEST_APP));
    }

    @Test
    public void dataOriginFilter_validFilter_onlyDataFromFilteredApps() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        insertRecords(
                List.of(
                        getStepsRecord(
                                100, startTime.plusMillis(1000), startTime.plusMillis(2000))));
        updatePriorityWithManageHealthDataPermission(
                ACTIVITY, ImmutableList.of(PKG_TEST_APP, mPackageName));

        AggregateRecordsRequest<Long> request =
                new AggregateRecordsRequest.Builder<Long>(
                                getTimeFilter(startTime.minus(1, HOURS), startTime.plus(1, HOURS)))
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin(PKG_TEST_APP))
                        .build();

        AggregateRecordsResponse<Long> response = getAggregateResponse(request);
        assertThat(response.get(STEPS_COUNT_TOTAL)).isEqualTo(50);
        assertThat(response.getDataOrigins(STEPS_COUNT_TOTAL))
                .containsExactly(getDataOrigin(PKG_TEST_APP));
    }

    @Test
    public void dataOriginFilter_invalidApp_noDataReturned() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        LocalDateTime localTime = startTime.atOffset(mCurrentZone).toLocalDateTime();
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        insertRecords(
                List.of(
                        getStepsRecord(
                                100,
                                startTime.plusMillis(1000),
                                startTime.plusMillis(2000),
                                /* clientId= */ "own_steps")));
        updatePriorityWithManageHealthDataPermission(
                ACTIVITY, ImmutableList.of(PKG_TEST_APP, mPackageName));

        TimeInstantRangeFilter instantFilter =
                getTimeFilter(startTime.minus(1, HOURS), startTime.plus(1, HOURS));
        AggregateRecordsRequest<Long> requestWithInstantFilter =
                new AggregateRecordsRequest.Builder<Long>(instantFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin("invalid.app.pkg"))
                        .build();
        LocalTimeRangeFilter localTimeFilter =
                getTimeFilter(localTime.minusHours(1), localTime.plusHours(1));
        AggregateRecordsRequest<Long> requestWithLocalTimeFilter =
                new AggregateRecordsRequest.Builder<Long>(localTimeFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin("invalid.app.pkg"))
                        .build();

        AggregateRecordsResponse<Long> aggregateResponse =
                getAggregateResponse(requestWithInstantFilter);
        assertThat(aggregateResponse.get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(aggregateResponse.getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByDurationResponse<Long>> groupedByDurationResponses =
                getAggregateResponseGroupByDuration(requestWithInstantFilter, Duration.ofDays(1));
        assertThat(groupedByDurationResponses).hasSize(1);
        assertThat(groupedByDurationResponses.get(0).get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(groupedByDurationResponses.get(0).getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByPeriodResponse<Long>> groupedByPeriodResponses =
                getAggregateResponseGroupByPeriod(requestWithLocalTimeFilter, Period.ofDays(1));
        assertThat(groupedByPeriodResponses).hasSize(1);
        assertThat(groupedByPeriodResponses.get(0).get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(groupedByPeriodResponses.get(0).getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();
    }

    @Test
    public void dataOriginFilter_appNotInPriorityList_noDataReturned() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        LocalDateTime localTime = startTime.atOffset(mCurrentZone).toLocalDateTime();
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        insertRecords(
                List.of(
                        getStepsRecord(
                                100, startTime.plusMillis(1000), startTime.plusMillis(2000))));
        // remove mPackageName from priority list
        updatePriorityWithManageHealthDataPermission(ACTIVITY, ImmutableList.of(PKG_TEST_APP));

        TimeInstantRangeFilter instantFilter =
                getTimeFilter(startTime.minus(1, HOURS), startTime.plus(1, HOURS));
        AggregateRecordsRequest<Long> requestWithInstantFilter =
                new AggregateRecordsRequest.Builder<Long>(instantFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin(mPackageName))
                        .build();
        LocalTimeRangeFilter localTimeFilter =
                getTimeFilter(localTime.minusHours(1), localTime.plusHours(1));
        AggregateRecordsRequest<Long> requestWithLocalTimeFilter =
                new AggregateRecordsRequest.Builder<Long>(localTimeFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin(mPackageName))
                        .build();

        AggregateRecordsResponse<Long> aggregateResponse =
                getAggregateResponse(requestWithInstantFilter);
        assertThat(aggregateResponse.get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(aggregateResponse.getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByDurationResponse<Long>> groupedByDurationResponses =
                getAggregateResponseGroupByDuration(requestWithInstantFilter, Duration.ofDays(1));
        assertThat(groupedByDurationResponses).hasSize(1);
        assertThat(groupedByDurationResponses.get(0).get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(groupedByDurationResponses.get(0).getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByPeriodResponse<Long>> groupedByPeriodResponses =
                getAggregateResponseGroupByPeriod(requestWithLocalTimeFilter, Period.ofDays(1));
        assertThat(groupedByPeriodResponses).hasSize(1);
        assertThat(groupedByPeriodResponses.get(0).get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(groupedByPeriodResponses.get(0).getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();
    }

    @Test
    public void dataOriginFilter_noDataFromFilteredApps_noDataReturned() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        LocalDateTime localTime = startTime.atOffset(mCurrentZone).toLocalDateTime();
        insertStepsRecordViaTestApp(mContext, startTime, startTime.plusMillis(1000), 50);
        updatePriorityWithManageHealthDataPermission(
                ACTIVITY, ImmutableList.of(PKG_TEST_APP, mPackageName));

        TimeInstantRangeFilter instantFilter =
                getTimeFilter(startTime.minus(1, HOURS), startTime.plus(1, HOURS));
        AggregateRecordsRequest<Long> requestWithInstantFilter =
                new AggregateRecordsRequest.Builder<Long>(instantFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin(mPackageName))
                        .build();
        LocalTimeRangeFilter localTimeFilter =
                getTimeFilter(localTime.minusHours(1), localTime.plusHours(1));
        AggregateRecordsRequest<Long> requestWithLocalTimeFilter =
                new AggregateRecordsRequest.Builder<Long>(localTimeFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .addDataOriginsFilter(getDataOrigin(mPackageName))
                        .build();

        AggregateRecordsResponse<Long> aggregateResponse =
                getAggregateResponse(requestWithInstantFilter);
        assertThat(aggregateResponse.get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(aggregateResponse.getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByDurationResponse<Long>> groupedByDurationResponses =
                getAggregateResponseGroupByDuration(requestWithInstantFilter, Duration.ofDays(1));
        assertThat(groupedByDurationResponses).hasSize(1);
        assertThat(groupedByDurationResponses.get(0).get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(groupedByDurationResponses.get(0).getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByPeriodResponse<Long>> groupedByPeriodResponses =
                getAggregateResponseGroupByPeriod(requestWithLocalTimeFilter, Period.ofDays(1));
        assertThat(groupedByPeriodResponses).hasSize(1);
        assertThat(groupedByPeriodResponses.get(0).get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(groupedByPeriodResponses.get(0).getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();
    }

    @Test
    public void timeRangeFilter_noDataWithinTimeInterval_noDataReturned() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        LocalDateTime localTime = startTime.atOffset(mCurrentZone).toLocalDateTime();
        insertRecords(
                List.of(
                        getStepsRecord(
                                100, startTime.plusMillis(1000), startTime.plusMillis(2000))));

        TimeInstantRangeFilter instantFilter = getTimeFilter(startTime.minus(1, HOURS), startTime);
        AggregateRecordsRequest<Long> requestWithInstantFilter =
                new AggregateRecordsRequest.Builder<Long>(instantFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();
        LocalTimeRangeFilter localTimeFilter = getTimeFilter(localTime.minusHours(1), localTime);
        AggregateRecordsRequest<Long> requestWithLocalTimeFilter =
                new AggregateRecordsRequest.Builder<Long>(localTimeFilter)
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();

        AggregateRecordsResponse<Long> aggregateResponse =
                getAggregateResponse(requestWithInstantFilter);
        assertThat(aggregateResponse.get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(aggregateResponse.getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByDurationResponse<Long>> groupedByDurationResponses =
                getAggregateResponseGroupByDuration(requestWithInstantFilter, Duration.ofDays(1));
        assertThat(groupedByDurationResponses).hasSize(1);
        assertThat(groupedByDurationResponses.get(0).get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(groupedByDurationResponses.get(0).getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByPeriodResponse<Long>> groupedByPeriodResponses =
                getAggregateResponseGroupByPeriod(requestWithLocalTimeFilter, Period.ofDays(1));
        assertThat(groupedByPeriodResponses).hasSize(1);
        assertThat(groupedByPeriodResponses.get(0).get(STEPS_COUNT_TOTAL)).isNull();
        assertThat(groupedByPeriodResponses.get(0).getDataOrigins(STEPS_COUNT_TOTAL)).isEmpty();
    }

    @Test
    public void aggregationTypeFilter_noDataForFilteredType_noDataReturned() throws Exception {
        Instant startTime = Instant.now().minus(1, DAYS);
        LocalDateTime localTime = startTime.atOffset(mCurrentZone).toLocalDateTime();
        insertRecords(List.of(getDistanceRecord(123.4, startTime, startTime.plusMillis(1000))));

        TimeInstantRangeFilter instantFilter =
                getTimeFilter(startTime.minus(1, HOURS), startTime.plus(1, HOURS));
        AggregateRecordsRequest<Length> requestWithInstantFilter =
                new AggregateRecordsRequest.Builder<Length>(instantFilter)
                        .addAggregationType(ELEVATION_GAINED_TOTAL)
                        .build();
        LocalTimeRangeFilter localTimeFilter =
                getTimeFilter(localTime.minusHours(1), localTime.plusHours(1));
        AggregateRecordsRequest<Length> requestWithLocalTimeFilter =
                new AggregateRecordsRequest.Builder<Length>(localTimeFilter)
                        .addAggregationType(ELEVATION_GAINED_TOTAL)
                        .build();

        AggregateRecordsResponse<Length> aggregateResponse =
                getAggregateResponse(requestWithInstantFilter);
        assertThat(aggregateResponse.get(ELEVATION_GAINED_TOTAL)).isNull();
        assertThat(aggregateResponse.getDataOrigins(ELEVATION_GAINED_TOTAL)).isEmpty();

        List<AggregateRecordsGroupedByDurationResponse<Length>> groupedByDurationResponses =
                getAggregateResponseGroupByDuration(requestWithInstantFilter, Duration.ofDays(1));
        assertThat(groupedByDurationResponses).hasSize(1);
        assertThat(groupedByDurationResponses.get(0).get(ELEVATION_GAINED_TOTAL)).isNull();
        assertThat(groupedByDurationResponses.get(0).getDataOrigins(ELEVATION_GAINED_TOTAL))
                .isEmpty();

        List<AggregateRecordsGroupedByPeriodResponse<Length>> groupedByPeriodResponses =
                getAggregateResponseGroupByPeriod(requestWithLocalTimeFilter, Period.ofDays(1));
        assertThat(groupedByPeriodResponses).hasSize(1);
        assertThat(groupedByPeriodResponses.get(0).get(ELEVATION_GAINED_TOTAL)).isNull();
        assertThat(groupedByPeriodResponses.get(0).getDataOrigins(ELEVATION_GAINED_TOTAL))
                .isEmpty();
    }
}
