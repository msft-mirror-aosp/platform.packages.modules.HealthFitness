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

package android.healthconnect.cts.aggregation;

import static android.health.connect.datatypes.MindfulnessSessionRecord.MINDFULNESS_DURATION_TOTAL;
import static android.health.connect.datatypes.MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_MEDITATION;
import static android.healthconnect.cts.lib.RecordFactory.newEmptyMetadata;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByDuration;
import static android.healthconnect.cts.utils.TestUtils.insertRecord;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.setupAggregation;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsGroupedByPeriodResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthDataCategory;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.MindfulnessSessionRecord;
import android.healthconnect.cts.lib.MindfulnessSessionRecordFactory;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;

import com.android.healthfitness.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RequiresFlagsEnabled(Flags.FLAG_MINDFULNESS)
public class MindfulnessDurationAggregationTest {

    private static final ZonedDateTime YESTERDAY_11AM =
            LocalDate.now(ZoneId.systemDefault())
                    .minusDays(1)
                    .atTime(11, 0)
                    .atZone(ZoneId.systemDefault());

    private static final ZonedDateTime MIDNIGHT_ONE_WEEK_AGO =
            YESTERDAY_11AM.truncatedTo(ChronoUnit.DAYS).minusDays(7);

    private static final LocalDateTime YESTERDAY_10AM_LOCAL =
            LocalDate.now(ZoneId.systemDefault()).minusDays(1).atTime(LocalTime.parse("10:00"));

    private final MindfulnessSessionRecordFactory mRecordFactory =
            new MindfulnessSessionRecordFactory();

    private static final String TEST_PACKAGE_NAME =
            ApplicationProvider.getApplicationContext().getPackageName();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void noData_largeWindow_returnsNull() throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.EPOCH)
                                                .setEndTime(Instant.now())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNull();
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL)).isNull();
        assertThat(response.getDataOrigins(MINDFULNESS_DURATION_TOTAL)).isEmpty();
    }

    @Test
    public void oneSession_largeWindow_returnsSessionDuration() throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        MindfulnessSessionRecord session =
                mRecordFactory.newEmptyRecord(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.toInstant(),
                        YESTERDAY_11AM.plusMinutes(30).toInstant());
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.EPOCH)
                                                .setEndTime(Instant.now())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(getSessionDuration(session).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(session.getStartZoneOffset());
        assertThat(response.getDataOrigins(MINDFULNESS_DURATION_TOTAL))
                .containsExactly(
                        new DataOrigin.Builder().setPackageName(TEST_PACKAGE_NAME).build());
    }

    @Test
    public void oneSession_startsBeforeWindowStart_endsOnWindowEnd_returnsOverlapDuration()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        MindfulnessSessionRecord session =
                mRecordFactory.newEmptyRecord(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.toInstant(),
                        YESTERDAY_11AM.plusMinutes(30).toInstant());
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(
                                                        session.getStartTime()
                                                                .plus(Duration.ofMinutes(5)))
                                                .setEndTime(session.getEndTime())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(25).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(session.getStartZoneOffset());
    }

    @Test
    public void oneSession_startsOnWindowStart_endsAfterWindowEnd_returnsOverlapDuration()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        MindfulnessSessionRecord session =
                mRecordFactory.newEmptyRecord(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.toInstant(),
                        YESTERDAY_11AM.plusMinutes(30).toInstant());
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(session.getStartTime())
                                                .setEndTime(
                                                        session.getEndTime()
                                                                .minus(Duration.ofMinutes(3)))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(27).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(session.getStartZoneOffset());
    }

    @Test
    public void oneSession_startsBeforeWindowStart_endsAfterWindowEnd_returnsOverlapDuration()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        MindfulnessSessionRecord session =
                mRecordFactory.newEmptyRecord(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.toInstant(),
                        YESTERDAY_11AM.plusMinutes(30).toInstant());
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(
                                                        session.getStartTime()
                                                                .plus(Duration.ofMinutes(6)))
                                                .setEndTime(
                                                        session.getEndTime()
                                                                .minus(Duration.ofMinutes(3)))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNotNull();
        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(21).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(session.getStartZoneOffset());
    }

    @Test
    public void oneSession_startsOnWindowEnd_returnsNull() throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        MindfulnessSessionRecord session =
                mRecordFactory.newEmptyRecord(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.toInstant(),
                        YESTERDAY_11AM.plusMinutes(30).toInstant());
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.EPOCH)
                                                .setEndTime(session.getStartTime())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNull();
    }

    @Test
    public void oneSession_endsOnWindowStart_returnsNull() throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        MindfulnessSessionRecord session =
                mRecordFactory.newEmptyRecord(
                        newEmptyMetadata(),
                        YESTERDAY_11AM.toInstant(),
                        YESTERDAY_11AM.plusMinutes(30).toInstant());
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(session.getEndTime())
                                                .setEndTime(Instant.now())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNull();
    }

    @Test
    public void aggregate_multipleOverlappingSessions_takesOverlapsIntoAccount()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        List<MindfulnessSessionRecord> sessions =
                List.of(
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.toInstant(),
                                YESTERDAY_11AM.plusHours(1).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.plusMinutes(30).toInstant(),
                                YESTERDAY_11AM.plusHours(1).plusMinutes(20).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.plusMinutes(40).toInstant(),
                                YESTERDAY_11AM.plusMinutes(50).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.plusHours(1).plusMinutes(10).toInstant(),
                                YESTERDAY_11AM.plusHours(1).plusMinutes(35).toInstant()));
        insertRecords(sessions);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.EPOCH)
                                                .setEndTime(sessions.get(3).getEndTime())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(
                        Duration.between(
                                        sessions.get(0).getStartTime(),
                                        sessions.get(3).getEndTime())
                                .toMillis());
    }

    @Test
    public void aggregate_multipleNotOverlappingSessions_returnsSumOfDurations()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        List<MindfulnessSessionRecord> sessions =
                List.of(
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.toInstant(),
                                YESTERDAY_11AM.plusMinutes(17).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.plusMinutes(20).toInstant(),
                                YESTERDAY_11AM.plusMinutes(34).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.plusMinutes(40).toInstant(),
                                YESTERDAY_11AM.plusMinutes(51).toInstant()));
        insertRecords(sessions);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(Instant.EPOCH)
                                                .setEndTime(sessions.get(2).getEndTime())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(
                        getSessionDuration(sessions.get(0))
                                .plus(getSessionDuration(sessions.get(1)))
                                .plus(getSessionDuration(sessions.get(2)))
                                .toMillis());
    }

    @Test
    public void aggregate_localTimeFilter_sessionsEqualsToWindow_returnsDuration()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        ZoneOffset startZoneOffset = ZoneOffset.ofHours(1);
        ZoneOffset endZoneOffset = ZoneOffset.ofHours(1);
        MindfulnessSessionRecord session =
                new MindfulnessSessionRecord.Builder(
                                newEmptyMetadata(),
                                YESTERDAY_10AM_LOCAL.toInstant(startZoneOffset),
                                YESTERDAY_10AM_LOCAL.plusMinutes(23).toInstant(endZoneOffset),
                                MINDFULNESS_SESSION_TYPE_MEDITATION)
                        .setStartZoneOffset(startZoneOffset)
                        .setEndZoneOffset(endZoneOffset)
                        .build();
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(YESTERDAY_10AM_LOCAL)
                                                .setEndTime(YESTERDAY_10AM_LOCAL.plusHours(1))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(23).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL)).isEqualTo(startZoneOffset);
    }

    @Test
    public void aggregate_localTimeFilter_minMaxZoneOffsets() throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        ZoneOffset startZoneOffset = ZoneOffset.MAX;
        ZoneOffset endZoneOffset = ZoneOffset.MIN;
        MindfulnessSessionRecord session =
                new MindfulnessSessionRecord.Builder(
                                newEmptyMetadata(),
                                YESTERDAY_10AM_LOCAL.toInstant(startZoneOffset),
                                YESTERDAY_10AM_LOCAL.plusMinutes(37).toInstant(endZoneOffset),
                                MINDFULNESS_SESSION_TYPE_MEDITATION)
                        .setStartZoneOffset(startZoneOffset)
                        .setEndZoneOffset(endZoneOffset)
                        .build();
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(YESTERDAY_10AM_LOCAL)
                                                .setEndTime(YESTERDAY_10AM_LOCAL.plusHours(1))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(37).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL)).isEqualTo(startZoneOffset);
    }

    @Test
    public void aggregate_localTimeFilter_startOffsetGreaterThanEndOffset()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        ZoneOffset startZoneOffset = ZoneOffset.ofHours(4);
        ZoneOffset endZoneOffset = ZoneOffset.ofHours(2);
        MindfulnessSessionRecord session =
                new MindfulnessSessionRecord.Builder(
                                newEmptyMetadata(),
                                YESTERDAY_10AM_LOCAL.toInstant(startZoneOffset),
                                YESTERDAY_10AM_LOCAL.plusMinutes(37).toInstant(endZoneOffset),
                                MINDFULNESS_SESSION_TYPE_MEDITATION)
                        .setStartZoneOffset(startZoneOffset)
                        .setEndZoneOffset(endZoneOffset)
                        .build();
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(YESTERDAY_10AM_LOCAL)
                                                .setEndTime(YESTERDAY_10AM_LOCAL.plusHours(1))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(37).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL)).isEqualTo(startZoneOffset);
    }

    @Test
    public void aggregate_localTimeFilter_startOffsetLessThanEndOffset()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        ZoneOffset startZoneOffset = ZoneOffset.ofHours(-2);
        ZoneOffset endZoneOffset = ZoneOffset.ofHours(-1);
        MindfulnessSessionRecord session =
                new MindfulnessSessionRecord.Builder(
                                newEmptyMetadata(),
                                YESTERDAY_10AM_LOCAL.toInstant(startZoneOffset),
                                YESTERDAY_10AM_LOCAL
                                        .plusHours(2)
                                        .plusMinutes(37)
                                        .toInstant(endZoneOffset),
                                MINDFULNESS_SESSION_TYPE_MEDITATION)
                        .setStartZoneOffset(startZoneOffset)
                        .setEndZoneOffset(endZoneOffset)
                        .build();
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(YESTERDAY_10AM_LOCAL.minusDays(1))
                                                .setEndTime(YESTERDAY_10AM_LOCAL.plusDays(1))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofHours(2).plus(Duration.ofMinutes(37)).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL)).isEqualTo(startZoneOffset);
    }

    @Test
    public void aggregate_localTimeFilter_sessionEndsBeforeWindow_returnsNull()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        ZoneOffset startZoneOffset = ZoneOffset.ofHours(4);
        ZoneOffset endZoneOffset = ZoneOffset.ofHours(2);
        MindfulnessSessionRecord session =
                new MindfulnessSessionRecord.Builder(
                                newEmptyMetadata(),
                                YESTERDAY_10AM_LOCAL.toInstant(startZoneOffset),
                                YESTERDAY_10AM_LOCAL.plusHours(1).toInstant(endZoneOffset),
                                MINDFULNESS_SESSION_TYPE_MEDITATION)
                        .setStartZoneOffset(startZoneOffset)
                        .setEndZoneOffset(endZoneOffset)
                        .build();
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(YESTERDAY_10AM_LOCAL.plusHours(1))
                                                .setEndTime(YESTERDAY_10AM_LOCAL.plusDays(10))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNull();
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL)).isNull();
    }

    @Test
    public void aggregate_localTimeFilter_sessionStartsAfterWindow_returnsNull()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        ZoneOffset startZoneOffset = ZoneOffset.ofHours(4);
        ZoneOffset endZoneOffset = ZoneOffset.ofHours(2);
        MindfulnessSessionRecord session =
                new MindfulnessSessionRecord.Builder(
                                newEmptyMetadata(),
                                YESTERDAY_10AM_LOCAL.toInstant(startZoneOffset),
                                YESTERDAY_10AM_LOCAL.plusHours(1).toInstant(endZoneOffset),
                                MINDFULNESS_SESSION_TYPE_MEDITATION)
                        .setStartZoneOffset(startZoneOffset)
                        .setEndZoneOffset(endZoneOffset)
                        .build();
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(YESTERDAY_10AM_LOCAL.minusDays(10))
                                                .setEndTime(YESTERDAY_10AM_LOCAL)
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL)).isNull();
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL)).isNull();
    }

    @Test
    public void aggregate_localTimeFilter_sessionOverlapsWindow_returnsNull()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        ZoneOffset startZoneOffset = ZoneOffset.ofHours(4);
        ZoneOffset endZoneOffset = ZoneOffset.ofHours(2);
        MindfulnessSessionRecord session =
                new MindfulnessSessionRecord.Builder(
                                newEmptyMetadata(),
                                YESTERDAY_10AM_LOCAL.toInstant(startZoneOffset),
                                YESTERDAY_10AM_LOCAL.plusHours(1).toInstant(endZoneOffset),
                                MINDFULNESS_SESSION_TYPE_MEDITATION)
                        .setStartZoneOffset(startZoneOffset)
                        .setEndZoneOffset(endZoneOffset)
                        .build();
        insertRecord(session);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(YESTERDAY_10AM_LOCAL.plusMinutes(47))
                                                .setEndTime(YESTERDAY_10AM_LOCAL.plusHours(5))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());

        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(13).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL)).isEqualTo(startZoneOffset);
    }

    @Test
    public void aggregate_localTimeFilter_overlappingSessionsWithDifferentOffsets()
            throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        List<MindfulnessSessionRecord> sessions =
                List.of(
                        new MindfulnessSessionRecord.Builder(
                                        newEmptyMetadata(),
                                        YESTERDAY_10AM_LOCAL.toInstant(ZoneOffset.ofHours(2)),
                                        YESTERDAY_10AM_LOCAL
                                                .plusMinutes(52)
                                                .toInstant(ZoneOffset.ofHours(1)),
                                        MINDFULNESS_SESSION_TYPE_MEDITATION)
                                .setStartZoneOffset(ZoneOffset.ofHours(2))
                                .setEndZoneOffset(ZoneOffset.ofHours(1))
                                .build(),
                        new MindfulnessSessionRecord.Builder(
                                        newEmptyMetadata(),
                                        YESTERDAY_10AM_LOCAL
                                                .plusMinutes(35)
                                                .toInstant(ZoneOffset.ofHours(4)),
                                        YESTERDAY_10AM_LOCAL
                                                .plusHours(1)
                                                .toInstant(ZoneOffset.ofHours(3)),
                                        MINDFULNESS_SESSION_TYPE_MEDITATION)
                                .setStartZoneOffset(ZoneOffset.ofHours(4))
                                .setEndZoneOffset(ZoneOffset.ofHours(3))
                                .build());

        insertRecords(sessions);

        AggregateRecordsResponse<Long> response =
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(YESTERDAY_10AM_LOCAL.minusDays(1))
                                                .setEndTime(YESTERDAY_10AM_LOCAL.plusDays(1))
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build());
        assertThat(response.get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofHours(1).toMillis());
        assertThat(response.getZoneOffset(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(ZoneOffset.ofHours(2));
    }

    @Test
    public void aggregateGroupByDuration_multipleOverlappingSessions() throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        List<MindfulnessSessionRecord> sessions =
                List.of(
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.toInstant(),
                                YESTERDAY_11AM.plusHours(1).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.plusMinutes(30).toInstant(),
                                YESTERDAY_11AM.plusHours(1).plusMinutes(20).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.plusMinutes(40).toInstant(),
                                YESTERDAY_11AM.plusMinutes(50).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                YESTERDAY_11AM.plusHours(1).plusMinutes(10).toInstant(),
                                YESTERDAY_11AM.plusHours(1).plusMinutes(35).toInstant()));
        insertRecords(sessions);

        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeInstantRangeFilter.Builder()
                                                .setStartTime(
                                                        YESTERDAY_11AM.minusMinutes(30).toInstant())
                                                .setEndTime(YESTERDAY_11AM.plusHours(2).toInstant())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build(),
                        Duration.of(30, ChronoUnit.MINUTES));

        assertThat(responses).hasSize(5);
        assertThat(responses.get(0).get(MINDFULNESS_DURATION_TOTAL)).isNull();
        assertThat(responses.get(1).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(responses.get(2).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(responses.get(3).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(30).toMillis());
        assertThat(responses.get(4).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(Duration.ofMinutes(5).toMillis());
    }

    @Test
    public void aggregateGroupByPeriod_returnsResponsePerGroup() throws InterruptedException {
        setupAggregation(TEST_PACKAGE_NAME, HealthDataCategory.WELLNESS);
        List<MindfulnessSessionRecord> sessions =
                List.of(
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                MIDNIGHT_ONE_WEEK_AGO.plusHours(10).toInstant(),
                                MIDNIGHT_ONE_WEEK_AGO.plusHours(11).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(1).plusHours(10).toInstant(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(1).plusHours(11).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(1).plusHours(16).toInstant(),
                                MIDNIGHT_ONE_WEEK_AGO
                                        .plusDays(1)
                                        .plusHours(16)
                                        .plusMinutes(32)
                                        .toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(3).plusHours(11).toInstant(),
                                MIDNIGHT_ONE_WEEK_AGO
                                        .plusDays(3)
                                        .plusHours(11)
                                        .plusMinutes(55)
                                        .toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(3).plusHours(10).toInstant(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(3).plusHours(13).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(4).plusHours(10).toInstant(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(4).plusHours(11).toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                MIDNIGHT_ONE_WEEK_AGO
                                        .plusDays(4)
                                        .plusHours(10)
                                        .plusMinutes(30)
                                        .toInstant(),
                                MIDNIGHT_ONE_WEEK_AGO
                                        .plusDays(4)
                                        .plusHours(11)
                                        .plusMinutes(13)
                                        .toInstant()),
                        mRecordFactory.newEmptyRecord(
                                newEmptyMetadata(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(6).minusMinutes(40).toInstant(),
                                MIDNIGHT_ONE_WEEK_AGO.plusDays(6).plusMinutes(35).toInstant()));
        insertRecords(sessions);

        List<AggregateRecordsGroupedByPeriodResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByPeriod(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new LocalTimeRangeFilter.Builder()
                                                .setStartTime(
                                                        MIDNIGHT_ONE_WEEK_AGO.toLocalDateTime())
                                                .setEndTime(
                                                        MIDNIGHT_ONE_WEEK_AGO
                                                                .plusDays(7)
                                                                .toLocalDateTime())
                                                .build())
                                .addAggregationType(MINDFULNESS_DURATION_TOTAL)
                                .build(),
                        Period.ofDays(1));

        assertThat(responses).hasSize(7);
        assertThat(responses.get(0).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(getSessionDuration(sessions.get(0)).toMillis());
        assertThat(responses.get(1).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(
                        getSessionDuration(sessions.get(1))
                                .plus(getSessionDuration(sessions.get(2)))
                                .toMillis());
        assertThat(responses.get(2).get(MINDFULNESS_DURATION_TOTAL)).isNull();
        assertThat(responses.get(3).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(getSessionDuration(sessions.get(4)).toMillis());
        assertThat(responses.get(4).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(
                        Duration.between(
                                        sessions.get(5).getStartTime(),
                                        sessions.get(6).getEndTime())
                                .toMillis());
        assertThat(responses.get(5).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(
                        Duration.between(
                                        sessions.get(7).getStartTime(),
                                        MIDNIGHT_ONE_WEEK_AGO.plusDays(6).toInstant())
                                .toMillis());
        assertThat(responses.get(6).get(MINDFULNESS_DURATION_TOTAL))
                .isEqualTo(
                        Duration.between(
                                        MIDNIGHT_ONE_WEEK_AGO.plusDays(6).toInstant(),
                                        sessions.get(7).getEndTime())
                                .toMillis());
    }

    private static Duration getSessionDuration(MindfulnessSessionRecord session) {
        return Duration.between(session.getStartTime(), session.getEndTime());
    }
}
