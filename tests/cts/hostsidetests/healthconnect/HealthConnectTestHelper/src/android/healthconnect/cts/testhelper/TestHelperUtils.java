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

package android.healthconnect.cts.testhelper;

import static android.health.connect.HealthPermissions.MANAGE_HEALTH_DATA_PERMISSION;
import static android.healthconnect.cts.utils.HealthConnectReceiver.callAndGetResponse;
import static android.healthconnect.cts.utils.HealthConnectReceiver.callAndGetResponseWithShellPermissionIdentity;

import static java.time.Instant.EPOCH;

import android.annotation.SuppressLint;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.InsertRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.TimeRangeFilter;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.HeartRateRecord.HeartRateSample;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Pressure;
import android.os.OutcomeReceiver;

import androidx.test.InstrumentationRegistry;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executor;

public class TestHelperUtils {
    public static final String MY_PACKAGE_NAME =
            InstrumentationRegistry.getContext().getPackageName();

    public static final int TIMEOUT_SECONDS = 5;

    public static Metadata getMetadata() {
        return new Metadata.Builder().setDataOrigin(getDataOrigin()).build();
    }

    public static Metadata getMetadata(String id) {
        return new Metadata.Builder().setDataOrigin(getDataOrigin()).setId(id).build();
    }

    public static DataOrigin getDataOrigin() {
        return new DataOrigin.Builder().setPackageName(MY_PACKAGE_NAME).build();
    }

    public static BloodPressureRecord getBloodPressureRecord() {
        return new BloodPressureRecord.Builder(
                        getMetadata(),
                        Instant.now(),
                        1,
                        Pressure.fromMillimetersOfMercury(22.0),
                        Pressure.fromMillimetersOfMercury(24.0),
                        1)
                .build();
    }

    public static StepsRecord getStepsRecord() {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(1, ChronoUnit.HOURS);
        return new StepsRecord.Builder(getMetadata(), startTime, endTime, 100).build();
    }

    public static HeartRateRecord getHeartRateRecord() {
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(1, ChronoUnit.HOURS);
        return new HeartRateRecord.Builder(
                        getMetadata(),
                        startTime,
                        endTime,
                        List.of(new HeartRateSample(100, startTime)))
                .build();
    }

    /**
     * Insertion/Reading of Height Record should fail as HealthConnectTestHelper do not have
     * permissions for Height.
     */
    public static HeightRecord getHeightRecord() {
        return new HeightRecord.Builder(getMetadata(), Instant.now(), Length.fromMeters(1.9))
                .build();
    }

    public static TimeRangeFilter getDefaultTimeRangeFilter() {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.ofHours(24)).truncatedTo(ChronoUnit.DAYS);
        Instant end = now.plus(Duration.ofHours(24)).truncatedTo(ChronoUnit.DAYS);
        return new TimeInstantRangeFilter.Builder().setStartTime(start).setEndTime(end).build();
    }

    public static List<Record> insertRecords(
            HealthConnectManager healthConnectManager, List<Record> records)
            throws InterruptedException {
        InsertRecordsResponse response =
                callAndGetResponse(
                        (executor, receiver) ->
                                healthConnectManager.insertRecords(records, executor, receiver));
        return response.getRecords();
    }

    public static void deleteRecords(
            HealthConnectManager healthConnectManager, List<Class<? extends Record>> recordTypes)
            throws InterruptedException {
        for (Class<? extends Record> recordType : recordTypes) {
            deleteRecords(healthConnectManager, recordType);
        }
    }

    /**
     * Deletes all records of the specified types written by the test app.
     *
     * @see HealthConnectManager#deleteRecords(Class, TimeRangeFilter, Executor, OutcomeReceiver)
     */
    public static void deleteRecords(
            HealthConnectManager healthConnectManager, Class<? extends Record> recordType)
            throws InterruptedException {
        TimeRangeFilter allTime = new TimeInstantRangeFilter.Builder().setStartTime(EPOCH).build();
        Void unused =
                callAndGetResponse(
                        (executor, receiver) ->
                                healthConnectManager.deleteRecords(
                                        recordType, allTime, executor, receiver));
    }

    /** Query access logs */
    public static List<AccessLog> queryAccessLogs(HealthConnectManager healthConnectManager)
            throws InterruptedException {
        return callAndGetResponseWithShellPermissionIdentity(
                healthConnectManager::queryAccessLogs, MANAGE_HEALTH_DATA_PERMISSION);
    }

    /** Deletes the records added by the test app. */
    public static void deleteAllRecordsAddedByTestApp(HealthConnectManager healthConnectManager)
            throws InterruptedException {
        DeleteUsingFiltersRequest request =
                new DeleteUsingFiltersRequest.Builder().addDataOrigin(getDataOrigin()).build();
        @SuppressLint("MissingPermission")
        Void unused =
                callAndGetResponseWithShellPermissionIdentity(
                        (executor, receiver) ->
                                healthConnectManager.deleteRecords(request, executor, receiver),
                        MANAGE_HEALTH_DATA_PERMISSION);
    }
}
