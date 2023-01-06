/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.healthconnect.cts;

import static android.healthconnect.datatypes.HeartRateRecord.BPM_AVG;
import static android.healthconnect.datatypes.HeartRateRecord.BPM_MAX;
import static android.healthconnect.datatypes.HeartRateRecord.BPM_MIN;
import static android.healthconnect.datatypes.HeartRateRecord.HEART_MEASUREMENTS_COUNT;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.healthconnect.AggregateRecordsGroupedByDurationResponse;
import android.healthconnect.AggregateRecordsGroupedByPeriodResponse;
import android.healthconnect.AggregateRecordsRequest;
import android.healthconnect.AggregateRecordsResponse;
import android.healthconnect.DeleteUsingFiltersRequest;
import android.healthconnect.ReadRecordsRequestUsingFilters;
import android.healthconnect.ReadRecordsRequestUsingIds;
import android.healthconnect.RecordIdFilter;
import android.healthconnect.TimeRangeFilter;
import android.healthconnect.datatypes.DataOrigin;
import android.healthconnect.datatypes.Device;
import android.healthconnect.datatypes.HeartRateRecord;
import android.healthconnect.datatypes.Metadata;
import android.healthconnect.datatypes.Record;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class HeartRateRecordTest {
    private static final String TAG = "HeartRateRecordTest";

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                HeartRateRecord.class,
                new TimeRangeFilter.Builder(Instant.EPOCH, Instant.now()).build());
    }

    @Test
    public void testInsertHeartRateRecord() throws InterruptedException {
        TestUtils.insertRecords(
                Arrays.asList(getBaseHeartRateRecord(), getCompleteHeartRateRecord()));
    }

    @Test
    public void testReadHeartRateRecord_usingIds() throws InterruptedException {
        testReadHeartRateRecordIds();
    }

    @Test
    public void testReadHeartRateRecord_invalidIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<HeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class)
                        .addId("abc")
                        .build();
        List<HeartRateRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadHeartRateRecord_usingClientRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord());
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        readHeartRateRecordUsingClientId(insertedRecords);
    }

    @Test
    public void testReadHeartRateRecord_invalidClientRecordIds() throws InterruptedException {
        ReadRecordsRequestUsingIds<HeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class)
                        .addClientRecordId("abc")
                        .build();
        List<HeartRateRecord> result = TestUtils.readRecords(request);
        assertThat(result.size()).isEqualTo(0);
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_default() throws InterruptedException {
        List<HeartRateRecord> oldHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .build());
        HeartRateRecord testRecord = getCompleteHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeartRateRecord> newHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .build());
        assertThat(newHeartRateRecords.size()).isEqualTo(oldHeartRateRecords.size() + 1);
        assertThat(newHeartRateRecords.get(newHeartRateRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_timeFilter() throws InterruptedException {
        TimeRangeFilter filter =
                new TimeRangeFilter.Builder(Instant.now(), Instant.now().plusMillis(3000)).build();
        HeartRateRecord testRecord = getCompleteHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeartRateRecord> newHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .setTimeRangeFilter(filter)
                                .build());
        assertThat(newHeartRateRecords.size()).isEqualTo(1);
        assertThat(newHeartRateRecords.get(newHeartRateRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_dataFilter_correct()
            throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        List<HeartRateRecord> oldHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        HeartRateRecord testRecord = getCompleteHeartRateRecord();
        TestUtils.insertRecords(Collections.singletonList(testRecord));
        List<HeartRateRecord> newHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build());
        assertThat(newHeartRateRecords.size() - oldHeartRateRecords.size()).isEqualTo(1);
        assertThat(newHeartRateRecords.get(newHeartRateRecords.size() - 1).equals(testRecord))
                .isTrue();
    }

    @Test
    public void testReadHeartRateRecordUsingFilters_dataFilter_incorrect()
            throws InterruptedException {
        TestUtils.insertRecords(Collections.singletonList(getCompleteHeartRateRecord()));
        List<HeartRateRecord> newHeartRateRecords =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(HeartRateRecord.class)
                                .addDataOrigins(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build());
        assertThat(newHeartRateRecords.size()).isEqualTo(0);
    }

    @Test
    public void testDeleteHeartRateRecord_no_filters() throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(new DeleteUsingFiltersRequest.Builder().build());
        TestUtils.assertRecordNotFound(id, HeartRateRecord.class);
    }

    @Test
    public void testDeleteHeartRateRecord_time_filters() throws InterruptedException {
        TimeRangeFilter timeRangeFilter =
                new TimeRangeFilter.Builder(Instant.now(), Instant.now().plusMillis(1000)).build();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addRecordType(HeartRateRecord.class)
                        .setTimeRangeFilter(timeRangeFilter)
                        .build());
        TestUtils.assertRecordNotFound(id, HeartRateRecord.class);
    }

    @Test
    public void testDeleteHeartRateRecord_recordId_filters() throws InterruptedException {
        List<Record> records = List.of(getBaseHeartRateRecord(), getCompleteHeartRateRecord());
        TestUtils.insertRecords(records);

        for (Record record : records) {
            TestUtils.verifyDeleteRecords(
                    new DeleteUsingFiltersRequest.Builder()
                            .addRecordType(record.getClass())
                            .build());
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteHeartRateRecord_dataOrigin_filters() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder()
                                        .setPackageName(context.getPackageName())
                                        .build())
                        .build());
        TestUtils.assertRecordNotFound(id, HeartRateRecord.class);
    }

    @Test
    public void testDeleteHeartRateRecord_dataOrigin_filter_incorrect()
            throws InterruptedException {
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(new DataOrigin.Builder().setPackageName("abc").build())
                        .build());
        TestUtils.assertRecordFound(id, HeartRateRecord.class);
    }

    @Test
    public void testDeleteHeartRateRecord_usingIds() throws InterruptedException {
        List<Record> records = List.of(getBaseHeartRateRecord(), getCompleteHeartRateRecord());
        List<Record> insertedRecord = TestUtils.insertRecords(records);
        List<RecordIdFilter> recordIds = new ArrayList<>(records.size());
        for (Record record : insertedRecord) {
            recordIds.add(
                    new RecordIdFilter.Builder(record.getClass())
                            .setId(record.getMetadata().getId())
                            .build());
        }

        TestUtils.verifyDeleteRecords(recordIds);
        for (Record record : records) {
            TestUtils.assertRecordNotFound(record.getMetadata().getId(), record.getClass());
        }
    }

    @Test
    public void testDeleteHeartRateRecord_time_range() throws InterruptedException {
        TimeRangeFilter timeRangeFilter =
                new TimeRangeFilter.Builder(Instant.now(), Instant.now().plusMillis(1000)).build();
        String id = TestUtils.insertRecordAndGetId(getCompleteHeartRateRecord());
        TestUtils.verifyDeleteRecords(HeartRateRecord.class, timeRangeFilter);
        TestUtils.assertRecordNotFound(id, HeartRateRecord.class);
    }

    @Test
    public void testZoneOffsets() {
        final ZoneOffset defaultZoneOffset =
                ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
        final ZoneOffset startZoneOffset = ZoneOffset.UTC;
        final ZoneOffset endZoneOffset = ZoneOffset.MAX;
        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(10, Instant.now());
        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords = new ArrayList<>();
        heartRateRecords.add(heartRateRecord);
        heartRateRecords.add(heartRateRecord);
        HeartRateRecord.Builder builder =
                new HeartRateRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now(),
                        heartRateRecords);

        assertThat(builder.setStartZoneOffset(startZoneOffset).build().getStartZoneOffset())
                .isEqualTo(startZoneOffset);
        assertThat(builder.setEndZoneOffset(endZoneOffset).build().getEndZoneOffset())
                .isEqualTo(endZoneOffset);
        assertThat(builder.clearStartZoneOffset().build().getStartZoneOffset())
                .isEqualTo(defaultZoneOffset);
        assertThat(builder.clearEndZoneOffset().build().getEndZoneOffset())
                .isEqualTo(defaultZoneOffset);
    }

    private void testReadHeartRateRecordIds() throws InterruptedException {
        List<Record> recordList =
                Arrays.asList(getCompleteHeartRateRecord(), getCompleteHeartRateRecord());
        readHeartRateRecordUsingIds(recordList);
    }

    private void readHeartRateRecordUsingClientId(List<Record> insertedRecord)
            throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<HeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class);
        for (Record record : insertedRecord) {
            request.addClientRecordId(record.getMetadata().getClientRecordId());
        }
        List<HeartRateRecord> result = TestUtils.readRecords(request.build());
        result.sort(Comparator.comparing(item -> item.getMetadata().getClientRecordId()));
        insertedRecord.sort(Comparator.comparing(item -> item.getMetadata().getClientRecordId()));

        for (int i = 0; i < result.size(); i++) {
            HeartRateRecord other = (HeartRateRecord) insertedRecord.get(i);
            assertThat(result.get(i).equals(other)).isTrue();
        }
    }

    private void readHeartRateRecordUsingIds(List<Record> recordList) throws InterruptedException {
        List<Record> insertedRecords = TestUtils.insertRecords(recordList);
        ReadRecordsRequestUsingIds.Builder<HeartRateRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class);
        for (Record record : insertedRecords) {
            request.addId(record.getMetadata().getId());
        }
        List<HeartRateRecord> result = TestUtils.readRecords(request.build());
        assertThat(result).hasSize(insertedRecords.size());
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).equals(insertedRecords.get(i))).isTrue();
        }
    }

    private static HeartRateRecord getBaseHeartRateRecord() {
        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(10, Instant.now());
        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords = new ArrayList<>();
        heartRateRecords.add(heartRateRecord);
        heartRateRecords.add(heartRateRecord);

        return new HeartRateRecord.Builder(
                        new Metadata.Builder().build(),
                        Instant.now(),
                        Instant.now(),
                        heartRateRecords)
                .build();
    }

    private static HeartRateRecord getCompleteHeartRateRecord() {

        Device device =
                new Device.Builder()
                        .setManufacturer("google")
                        .setModel("Pixel4a")
                        .setType(2)
                        .build();
        DataOrigin dataOrigin =
                new DataOrigin.Builder().setPackageName("android.healthconnect.cts").build();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        testMetadataBuilder.setClientRecordId("HRR" + Math.random());

        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(10, Instant.now());

        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords = new ArrayList<>();
        heartRateRecords.add(heartRateRecord);
        heartRateRecords.add(heartRateRecord);

        return new HeartRateRecord.Builder(
                        testMetadataBuilder.build(), Instant.now(), Instant.now(), heartRateRecords)
                .build();
    }

    @Test
    public void testBpmAggregation_timeRange_all() throws Exception {
        List<Record> records =
                Arrays.asList(
                        TestUtils.getHeartRateRecord(71),
                        TestUtils.getHeartRateRecord(72),
                        TestUtils.getHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeRangeFilter.Builder(
                                                        Instant.ofEpochMilli(0),
                                                        Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .build(),
                        records);
        assertThat(response.get(BPM_MAX)).isNotNull();
        assertThat(response.get(BPM_MAX)).isEqualTo(73);
        assertThat(response.getZoneOffset(BPM_MAX))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(BPM_MIN)).isNotNull();
        assertThat(response.get(BPM_MIN)).isEqualTo(71);
        assertThat(response.getZoneOffset(BPM_MIN))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(BPM_AVG)).isNotNull();
        assertThat(response.get(BPM_AVG)).isEqualTo(72);
        assertThat(response.getZoneOffset(BPM_AVG))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    @Test
    public void testBpmAggregation_timeRange_not_present() throws Exception {
        List<Record> records =
                Arrays.asList(
                        TestUtils.getHeartRateRecord(71),
                        TestUtils.getHeartRateRecord(72),
                        TestUtils.getHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeRangeFilter.Builder(
                                                        Instant.now().plusMillis(1000),
                                                        Instant.now().plusMillis(2000))
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .build(),
                        records);
        assertThat(response.get(BPM_MAX)).isNull();
        assertThat(response.getZoneOffset(BPM_MAX)).isNull();
        assertThat(response.get(BPM_MIN)).isNull();
        assertThat(response.getZoneOffset(BPM_MIN)).isNull();
        assertThat(response.get(BPM_AVG)).isNull();
        assertThat(response.getZoneOffset(BPM_AVG)).isNull();
    }

    @Test
    public void testBpmAggregation_withDataOrigin_correct() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        List<Record> records =
                Arrays.asList(
                        TestUtils.getHeartRateRecord(71),
                        TestUtils.getHeartRateRecord(72),
                        TestUtils.getHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeRangeFilter.Builder(
                                                        Instant.ofEpochMilli(0),
                                                        Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .build(),
                        records);
        assertThat(response.get(BPM_MAX)).isNotNull();
        assertThat(response.get(BPM_MAX)).isEqualTo(73);
        assertThat(response.getZoneOffset(BPM_MAX))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(BPM_MIN)).isNotNull();
        assertThat(response.get(BPM_MIN)).isEqualTo(71);
        assertThat(response.getZoneOffset(BPM_MIN))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        assertThat(response.get(BPM_AVG)).isNotNull();
        assertThat(response.get(BPM_AVG)).isEqualTo(72);
        assertThat(response.getZoneOffset(BPM_AVG))
                .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
    }

    @Test
    public void testBpmAggregation_withDataOrigin_incorrect() throws Exception {
        List<Record> records =
                Arrays.asList(
                        TestUtils.getHeartRateRecord(71),
                        TestUtils.getHeartRateRecord(72),
                        TestUtils.getHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeRangeFilter.Builder(
                                                        Instant.ofEpochMilli(0),
                                                        Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build(),
                        records);
        assertThat(response.get(BPM_MAX)).isNull();
        assertThat(response.getZoneOffset(BPM_MAX)).isNull();
        assertThat(response.get(BPM_MIN)).isNull();
        assertThat(response.getZoneOffset(BPM_MIN)).isNull();
        assertThat(response.get(BPM_AVG)).isNull();
        assertThat(response.getZoneOffset(BPM_AVG)).isNull();
    }

    @Test
    public void testBpmAggregation_groupBy_Duration() throws Exception {
        Instant start = Instant.now().minusMillis(500);
        Instant end = Instant.now().plusMillis(2500);
        insertHeartRateRecordsWithDelay(1000, 3);
        List<AggregateRecordsGroupedByDurationResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeRangeFilter.Builder(start, end).build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .addAggregationType(BPM_AVG)
                                .build(),
                        Duration.ofSeconds(1));
        assertThat(responses.size()).isEqualTo(3);
        for (AggregateRecordsGroupedByDurationResponse<Long> response : responses) {
            assertThat(response.get(BPM_MAX)).isNotNull();
            assertThat(response.get(BPM_MAX)).isEqualTo(73);
            assertThat(response.getZoneOffset(BPM_MAX))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
            assertThat(response.get(BPM_MIN)).isNotNull();
            assertThat(response.get(BPM_MIN)).isEqualTo(71);
            assertThat(response.getZoneOffset(BPM_MIN))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
            assertThat(response.get(BPM_AVG)).isNotNull();
            assertThat(response.get(BPM_AVG)).isEqualTo(72);
            assertThat(response.getZoneOffset(BPM_AVG))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
            assertThat(response.getStartTime()).isNotNull();
            assertThat(response.getEndTime()).isNotNull();
            start = start.plus(1, ChronoUnit.SECONDS);
        }
    }

    @Test
    public void testBpmAggregation_groupBy_Period() throws Exception {
        Instant start = Instant.now().minus(3, ChronoUnit.DAYS);
        Instant end = start.plus(3, ChronoUnit.DAYS);
        insertHeartRateRecordsInPastDays(4);
        List<AggregateRecordsGroupedByPeriodResponse<Long>> responses =
                TestUtils.getAggregateResponseGroupByPeriod(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeRangeFilter.Builder(start, end).build())
                                .addAggregationType(BPM_MAX)
                                .addAggregationType(BPM_MIN)
                                .build(),
                        Period.ofDays(1));
        assertThat(responses.size()).isAtLeast(3);
        for (AggregateRecordsGroupedByPeriodResponse<Long> response : responses) {
            if (start.toEpochMilli()
                    < response.getStartTime()
                            .atZone(ZoneOffset.systemDefault())
                            .toInstant()
                            .toEpochMilli()) {
                Long bpm = response.get(BPM_MAX);
                ZoneOffset zoneOffset = response.getZoneOffset(BPM_MAX);

                if (bpm == null) {
                    assertThat(zoneOffset).isNull();
                } else {
                    assertThat(zoneOffset).isNotNull();
                }
                // skip the check if our request doesn't fall with in the instant time we inserted
                // the record
                continue;
            }

            if (end.toEpochMilli()
                    > response.getEndTime()
                            .atZone(ZoneOffset.systemDefault())
                            .toInstant()
                            .toEpochMilli()) {
                Long bpm = response.get(BPM_MAX);
                ZoneOffset zoneOffset = response.getZoneOffset(BPM_MAX);

                if (bpm == null) {
                    assertThat(zoneOffset).isNull();
                } else {
                    assertThat(zoneOffset).isNotNull();
                }
                // skip the check if our request doesn't fall with in the instant time we inserted
                // the record
                continue;
            }

            assertThat(response.get(BPM_MAX)).isNotNull();
            assertThat(response.get(BPM_MAX)).isEqualTo(73);
            assertThat(response.getZoneOffset(BPM_MAX))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
            assertThat(response.get(BPM_MIN)).isNotNull();
            assertThat(response.get(BPM_MIN)).isEqualTo(71);
            assertThat(response.getZoneOffset(BPM_MIN))
                    .isEqualTo(ZoneOffset.systemDefault().getRules().getOffset(Instant.now()));
        }
    }

    @Test
    public void testHeartAggregation_measurement_count() throws Exception {
        List<Record> records =
                Arrays.asList(
                        TestUtils.getHeartRateRecord(71),
                        TestUtils.getHeartRateRecord(72),
                        TestUtils.getHeartRateRecord(73));
        AggregateRecordsResponse<Long> response =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeRangeFilter.Builder(
                                                        Instant.ofEpochMilli(0),
                                                        Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(HEART_MEASUREMENTS_COUNT)
                                .addAggregationType(BPM_MAX)
                                .build(),
                        records);
        List<Record> recordsNew =
                Arrays.asList(
                        TestUtils.getHeartRateRecord(71),
                        TestUtils.getHeartRateRecord(72),
                        TestUtils.getHeartRateRecord(73));
        AggregateRecordsResponse<Long> newResponse =
                TestUtils.getAggregateResponse(
                        new AggregateRecordsRequest.Builder<Long>(
                                        new TimeRangeFilter.Builder(
                                                        Instant.ofEpochMilli(0),
                                                        Instant.now().plus(1, ChronoUnit.DAYS))
                                                .build())
                                .addAggregationType(HEART_MEASUREMENTS_COUNT)
                                .build(),
                        recordsNew);
        assertThat(newResponse.get(HEART_MEASUREMENTS_COUNT)).isNotNull();
        assertThat(response.get(HEART_MEASUREMENTS_COUNT)).isNotNull();
        assertThat(
                        newResponse.get(HEART_MEASUREMENTS_COUNT)
                                - response.get(HEART_MEASUREMENTS_COUNT))
                .isEqualTo(6);
    }

    private void insertHeartRateRecordsWithDelay(long delayInMillis, int times)
            throws InterruptedException {
        for (int i = 0; i < times; i++) {
            List<Record> records =
                    Arrays.asList(
                            TestUtils.getHeartRateRecord(71),
                            TestUtils.getHeartRateRecord(72),
                            TestUtils.getHeartRateRecord(73));

            TestUtils.insertRecords(records);
            Thread.sleep(delayInMillis);
        }
    }

    private void insertHeartRateRecordsInPastDays(int numDays) throws InterruptedException {
        for (int i = numDays; i > 0; i--) {
            List<Record> records =
                    Arrays.asList(
                            TestUtils.getHeartRateRecord(
                                    71, Instant.now().minus(i, ChronoUnit.DAYS)),
                            TestUtils.getHeartRateRecord(
                                    72, Instant.now().minus(i, ChronoUnit.DAYS)),
                            TestUtils.getHeartRateRecord(
                                    73, Instant.now().minus(i, ChronoUnit.DAYS)));

            TestUtils.insertRecords(records);
        }
    }
}
