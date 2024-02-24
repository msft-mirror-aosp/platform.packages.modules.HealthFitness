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

package android.healthconnect.cts.historicaccess;

import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getWeightRecord;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getRecordIds;
import static android.healthconnect.cts.utils.TestUtils.insertRecordAndGetId;

import static com.google.common.truth.Truth.assertThat;

import static java.time.temporal.ChronoUnit.DAYS;

import android.content.Context;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.WeightRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.DeviceConfigRule;
import android.healthconnect.cts.utils.TestReceiver;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class HistoricAccessLimitWithPermissionTest {

    private Context mContext;
    private Instant mNow;

    @Rule
    public final DeviceConfigRule mDeviceConfigRule =
            new DeviceConfigRule("history_read_enable", "true");

    @Rule
    public final AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mNow = Instant.now();
        deleteAllStagedRemoteData();
        TestReceiver.reset();
    }

    @After
    public void tearDown() throws InterruptedException {
        deleteAllStagedRemoteData();
    }

    @Test
    public void testReadIntervalRecordsByFilters_expectCorrectResponse()
            throws InterruptedException {
        String ownRecordId1 = insertStepsRecord(daysBeforeNow(10), daysBeforeNow(9), 10);
        String ownRecordId2 = insertStepsRecord(daysBeforeNow(11), daysBeforeNow(10), 11);
        String ownRecordId3 = insertStepsRecord(daysBeforeNow(50), daysBeforeNow(40), 12);
        String otherAppsRecordIdAfterHistoricLimit =
                insertStepsRecordViaTestApp(daysBeforeNow(2), daysBeforeNow(1), 13);
        String otherAppsRecordIdBeforeHistoricLimit =
                insertStepsRecordViaTestApp(daysBeforeNow(50), daysBeforeNow(40), 14);

        List<String> stepsRecordsIdsReadByFilters =
                getRecordIds(
                        TestUtils.readRecords(
                                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                                        .build()));

        assertThat(stepsRecordsIdsReadByFilters)
                .containsExactly(
                        ownRecordId1,
                        ownRecordId2,
                        ownRecordId3,
                        otherAppsRecordIdAfterHistoricLimit,
                        otherAppsRecordIdBeforeHistoricLimit);
    }

    @Test
    public void testReadInstantRecordsByFilters_expectCorrectResponse()
            throws InterruptedException {
        String ownRecordId1 = insertWeightRecord(daysBeforeNow(10), 10);
        String ownRecordId2 = insertWeightRecord(daysBeforeNow(11), 11);
        String ownRecordId3 = insertWeightRecord(daysBeforeNow(50), 12);
        String otherAppsRecordIdAfterHistoricLimit =
                insertWeightRecordViaTestApp(daysBeforeNow(2), 13);
        String otherAppsRecordIdBeforeHistoricLimit =
                insertWeightRecordViaTestApp(daysBeforeNow(50), 14);

        List<String> weightRecordsIdsReadByFilters =
                getRecordIds(
                        TestUtils.readRecords(
                                new ReadRecordsRequestUsingFilters.Builder<>(WeightRecord.class)
                                        .build()));

        assertThat(weightRecordsIdsReadByFilters)
                .containsExactly(
                        ownRecordId1,
                        ownRecordId2,
                        ownRecordId3,
                        otherAppsRecordIdAfterHistoricLimit,
                        otherAppsRecordIdBeforeHistoricLimit);
    }

    @Test
    public void testReadIntervalRecordsByIds_expectCorrectResponse() throws InterruptedException {
        List<String> insertedRecordIds =
                List.of(
                        insertStepsRecord(daysBeforeNow(10), daysBeforeNow(9), 10),
                        insertStepsRecord(daysBeforeNow(11), daysBeforeNow(10), 11),
                        insertStepsRecord(daysBeforeNow(50), daysBeforeNow(40), 12),
                        insertStepsRecordViaTestApp(daysBeforeNow(2), daysBeforeNow(1), 13),
                        insertStepsRecordViaTestApp(daysBeforeNow(50), daysBeforeNow(40), 14));

        ReadRecordsRequestUsingIds.Builder<StepsRecord> readUsingIdsRequest =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class);
        insertedRecordIds.forEach(readUsingIdsRequest::addId);
        List<String> recordIdsReadByIds =
                getRecordIds(TestUtils.readRecords(readUsingIdsRequest.build()));

        assertThat(recordIdsReadByIds).containsExactlyElementsIn(insertedRecordIds);
    }

    @Test
    public void testReadInstantRecordsByIds_expectCorrectResponse() throws InterruptedException {
        List<String> insertedRecordIds =
                List.of(
                        insertWeightRecord(daysBeforeNow(10), 10),
                        insertWeightRecord(daysBeforeNow(11), 11),
                        insertWeightRecord(daysBeforeNow(50), 12),
                        insertWeightRecordViaTestApp(daysBeforeNow(2), 13),
                        insertWeightRecordViaTestApp(daysBeforeNow(50), 14));

        ReadRecordsRequestUsingIds.Builder<WeightRecord> readUsingIdsRequest =
                new ReadRecordsRequestUsingIds.Builder<>(WeightRecord.class);
        insertedRecordIds.forEach(readUsingIdsRequest::addId);
        List<String> recordIdsReadByIds =
                getRecordIds(TestUtils.readRecords(readUsingIdsRequest.build()));

        assertThat(recordIdsReadByIds).containsExactlyElementsIn(insertedRecordIds);
    }

    private String insertStepsRecord(Instant startTime, Instant endTime, long value)
            throws InterruptedException {
        return insertRecordAndGetId(getStepsRecord(value, startTime, endTime));
    }

    private String insertWeightRecord(Instant time, double value) throws InterruptedException {
        return insertRecordAndGetId(getWeightRecord(value, time));
    }

    private String insertStepsRecordViaTestApp(Instant startTime, Instant endTime, long value) {
        return TestUtils.insertStepsRecordViaTestApp(mContext, startTime, endTime, value).get(0);
    }

    private String insertWeightRecordViaTestApp(Instant startTime, double value) {
        return TestUtils.insertWeightRecordViaTestApp(mContext, startTime, value).get(0);
    }

    private Instant daysBeforeNow(int days) {
        return mNow.minus(days, DAYS);
    }
}
