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
import static android.health.connect.datatypes.SpeedRecord.SPEED_AVG;
import static android.health.connect.datatypes.SpeedRecord.SPEED_MAX;
import static android.health.connect.datatypes.SpeedRecord.SPEED_MIN;
import static android.healthconnect.cts.aggregation.DataFactory.getTimeFilter;
import static android.healthconnect.cts.aggregation.Utils.assertDoubleWithTolerance;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.setupAggregation;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.SpeedRecord;
import android.health.connect.datatypes.units.Velocity;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

public class SeriesAggregationTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mPackageName = mContext.getPackageName();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHealthConnectFullySupported,
                    "Tests should run on supported hardware only.");

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
    public void aggregateWithInstantFilter_speed() throws Exception {
        Instant time = Instant.now().minus(1, DAYS);
        insertRecords(
                List.of(
                        getSpeedRecord(
                                time,
                                time.plus(8, HOURS),
                                UTC,
                                getSpeedRecordSample(
                                        Velocity.fromMetersPerSecond(50), time.plus(1, HOURS)),
                                getSpeedRecordSample(
                                        Velocity.fromMetersPerSecond(10), time.plus(2, HOURS)),
                                getSpeedRecordSample(
                                        Velocity.fromMetersPerSecond(6), time.plus(3, HOURS)),
                                getSpeedRecordSample(
                                        Velocity.fromMetersPerSecond(2), time.plus(4, HOURS)),
                                getSpeedRecordSample(
                                        Velocity.fromMetersPerSecond(12), time.plus(5, HOURS)))));

        TimeInstantRangeFilter timeFilter =
                getTimeFilter(time, time.plus(3, HOURS).plus(1, MINUTES));
        AggregateRecordsRequest<Velocity> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Velocity>(timeFilter)
                        .addAggregationType(SPEED_MIN)
                        .addAggregationType(SPEED_MAX)
                        .addAggregationType(SPEED_AVG)
                        .build();

        AggregateRecordsResponse<Velocity> response = getAggregateResponse(aggregateRecordsRequest);
        assertDoubleWithTolerance(response.get(SPEED_MIN).getInMetersPerSecond(), 6);
        assertDoubleWithTolerance(response.get(SPEED_MAX).getInMetersPerSecond(), 50);
        // Expect (50+10+6)/3=22
        assertDoubleWithTolerance(response.get(SPEED_AVG).getInMetersPerSecond(), 22);
    }

    private static SpeedRecord getSpeedRecord(
            Instant start,
            Instant end,
            ZoneOffset offset,
            SpeedRecord.SpeedRecordSample... samples) {
        return new SpeedRecord.Builder(getEmptyMetadata(), start, end, Arrays.asList(samples))
                .setStartZoneOffset(offset)
                .setEndZoneOffset(offset)
                .build();
    }

    private static SpeedRecord.SpeedRecordSample getSpeedRecordSample(
            Velocity velocity, Instant time) {
        return new SpeedRecord.SpeedRecordSample(velocity, time);
    }
}
