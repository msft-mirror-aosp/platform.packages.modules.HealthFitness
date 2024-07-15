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

package android.healthconnect.cts.nopermission;

import static android.health.connect.HealthPermissions.READ_DISTANCE;
import static android.health.connect.HealthPermissions.READ_EXERCISE;
import static android.health.connect.HealthPermissions.READ_HEART_RATE;
import static android.health.connect.HealthPermissions.READ_SLEEP;
import static android.health.connect.HealthPermissions.READ_STEPS;
import static android.health.connect.HealthPermissions.READ_TOTAL_CALORIES_BURNED;
import static android.health.connect.datatypes.DistanceRecord.DISTANCE_TOTAL;
import static android.health.connect.datatypes.ExerciseSessionRecord.EXERCISE_DURATION_TOTAL;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.FhirVersion.parseFhirVersion;
import static android.health.connect.datatypes.HeartRateRecord.BPM_MAX;
import static android.health.connect.datatypes.SleepSessionRecord.SLEEP_DURATION_TOTAL;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.health.connect.datatypes.TotalCaloriesBurnedRecord.ENERGY_TOTAL;
import static android.healthconnect.cts.lib.RecordFactory.newEmptyMetadataWithClientId;
import static android.healthconnect.cts.utils.DataFactory.NOW;
import static android.healthconnect.cts.utils.DataFactory.buildExerciseSession;
import static android.healthconnect.cts.utils.DataFactory.buildSleepSession;
import static android.healthconnect.cts.utils.DataFactory.getDistanceRecord;
import static android.healthconnect.cts.utils.DataFactory.getDistanceRecordWithNonEmptyId;
import static android.healthconnect.cts.utils.DataFactory.getHeartRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getTotalCaloriesBurnedRecord;
import static android.healthconnect.cts.utils.DataFactory.getTotalCaloriesBurnedRecordWithEmptyMetadata;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAllPermissions;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.TestUtils.deleteRecords;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponse;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByDuration;
import static android.healthconnect.cts.utils.TestUtils.getAggregateResponseGroupByPeriod;
import static android.healthconnect.cts.utils.TestUtils.getChangeLogToken;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.readRecords;
import static android.healthconnect.cts.utils.TestUtils.updateRecords;
import static android.healthconnect.cts.utils.TestUtils.verifyDeleteRecords;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.time.temporal.ChronoUnit.DAYS;

import android.health.connect.AggregateRecordsRequest;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.MedicalResourceId;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.SleepSessionRecord;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.TotalCaloriesBurnedRecord;
import android.healthconnect.cts.lib.MindfulnessSessionRecordFactory;
import android.healthconnect.cts.lib.TestAppProxy;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Pair;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** These test run under an environment which has no HC permissions */
@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class HealthConnectManagerNoPermissionsGrantedTest {
    private static final TestAppProxy APP_A_WITH_READ_WRITE_PERMS =
            TestAppProxy.forPackageName("android.healthconnect.cts.testapp.readWritePerms.A");
    private static final MindfulnessSessionRecordFactory MINDFULNESS_SESSION_RECORD_FACTORY =
            new MindfulnessSessionRecordFactory();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testInsert_noPermissions_expectError() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                insertRecords(Collections.singletonList(testRecord));
                Assert.fail("Insert must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testUpdate_noPermissions_expectError() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                updateRecords(Collections.singletonList(testRecord));
                Assert.fail("Update must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testDeleteUsingId_noPermissions_expectError() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                deleteRecords(Collections.singletonList(testRecord));
                Assert.fail("Delete using ids must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testDeleteUsingFilter_noPermissions_expectError() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                verifyDeleteRecords(
                        testRecord.getClass(),
                        new TimeInstantRangeFilter.Builder()
                                .setStartTime(Instant.now())
                                .setEndTime(Instant.now().plusMillis(1000))
                                .build());
                Assert.fail("Delete using filters must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testChangeLogsToken_noPermissions_expectError() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(testRecord.getClass())
                                .build());
                Assert.fail(
                        "Getting change log token must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testGetChangeLogs_noPermissions_expectError() throws Exception {
        TestAppProxy testApp = APP_A_WITH_READ_WRITE_PERMS;
        String packageName = testApp.getPackageName();
        revokeAllPermissions(packageName, /* reason= */ "for test");
        List<Pair<String, Class<? extends Record>>> permissionAndRecordClassPairs =
                List.of(
                        new Pair<>(READ_STEPS, StepsRecord.class),
                        new Pair<>(READ_DISTANCE, DistanceRecord.class),
                        new Pair<>(READ_HEART_RATE, HeartRateRecord.class),
                        new Pair<>(READ_SLEEP, SleepSessionRecord.class),
                        new Pair<>(READ_EXERCISE, ExerciseSessionRecord.class),
                        new Pair<>(READ_TOTAL_CALORIES_BURNED, TotalCaloriesBurnedRecord.class));

        for (var permissionAndRecordClass : permissionAndRecordClassPairs) {
            String permission = permissionAndRecordClass.first;
            Class<? extends Record> recordClass = permissionAndRecordClass.second;
            grantPermission(packageName, permission);
            String token =
                    testApp.getChangeLogToken(
                            new ChangeLogTokenRequest.Builder().addRecordType(recordClass).build());
            revokeAllPermissions(packageName, /* reason= */ "for test");

            try {
                testApp.getChangeLogs(new ChangeLogsRequest.Builder(token).build());

                Assert.fail(
                        String.format(
                                "Getting change logs for %s must be not allowed with %s permission",
                                recordClass.getSimpleName(), permission));
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testReadByFilters_noPermissions_expectError() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(testRecord.getClass())
                                .build());
                Assert.fail(
                        "Read records by filters must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testReadByRecordIds_noPermissions_expectError() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(testRecord.getClass())
                                .addId("id")
                                .build());
                Assert.fail(
                        "Read records by record ids must be not allowed without right HC "
                                + "permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testReadByClientIds_noPermissions_expectError() throws InterruptedException {
        for (Record testRecord : getTestRecords()) {
            try {
                readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(testRecord.getClass())
                                .addClientRecordId("client_id")
                                .build());
                Assert.fail(
                        "Read records by client ids must be not allowed without right HC "
                                + "permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testAggregate_noPermissions_expectError() throws InterruptedException {
        List<Pair<Record, AggregationType<?>>> recordAndAggregationTypePairs =
                List.of(
                        new Pair<>(getHeartRateRecord(), BPM_MAX),
                        new Pair<>(getStepsRecord(), STEPS_COUNT_TOTAL),
                        new Pair<>(getDistanceRecord(), DISTANCE_TOTAL),
                        new Pair<>(getTotalCaloriesBurnedRecordWithEmptyMetadata(), ENERGY_TOTAL),
                        new Pair<>(buildSleepSession(), SLEEP_DURATION_TOTAL),
                        new Pair<>(buildExerciseSession(), EXERCISE_DURATION_TOTAL));
        for (var recordAndAggregationType : recordAndAggregationTypePairs) {
            try {
                List<Record> records = List.of(recordAndAggregationType.first);
                AggregationType<?> aggregationType = recordAndAggregationType.second;
                TimeInstantRangeFilter timeInstantRangeFilter =
                        new TimeInstantRangeFilter.Builder()
                                .setStartTime(Instant.ofEpochMilli(0))
                                .setEndTime(NOW.plus(1000, DAYS))
                                .build();
                getAggregateResponse(
                        new AggregateRecordsRequest.Builder<>(timeInstantRangeFilter)
                                .addAggregationType((AggregationType<Object>) aggregationType)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build(),
                        records);
                Assert.fail("Get Aggregations must be not allowed without right HC permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testAggregateGroupByDuration_noPermissions_expectError()
            throws InterruptedException {
        List<AggregationType<?>> aggregationTypes =
                List.of(
                        BPM_MAX,
                        STEPS_COUNT_TOTAL,
                        DISTANCE_TOTAL,
                        ENERGY_TOTAL,
                        SLEEP_DURATION_TOTAL,
                        EXERCISE_DURATION_TOTAL);
        for (var aggregationType : aggregationTypes) {
            try {
                TimeInstantRangeFilter timeInstantRangeFilter =
                        new TimeInstantRangeFilter.Builder()
                                .setStartTime(NOW.minusMillis(500))
                                .setEndTime(NOW.plusMillis(2500))
                                .build();
                getAggregateResponseGroupByDuration(
                        new AggregateRecordsRequest.Builder<>(timeInstantRangeFilter)
                                .addAggregationType((AggregationType<Object>) aggregationType)
                                .addDataOriginsFilter(
                                        new DataOrigin.Builder().setPackageName("abc").build())
                                .build(),
                        Duration.ofSeconds(1));
                Assert.fail(
                        "Aggregations group by duration must be not allowed without right HC"
                                + " permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    public void testAggregateGroupByPeriod_noPermissions_expectError() throws InterruptedException {
        List<AggregationType<?>> aggregationTypes =
                List.of(
                        BPM_MAX,
                        STEPS_COUNT_TOTAL,
                        DISTANCE_TOTAL,
                        ENERGY_TOTAL,
                        SLEEP_DURATION_TOTAL,
                        EXERCISE_DURATION_TOTAL);
        for (var aggregationType : aggregationTypes) {
            try {
                Instant start = NOW.minus(3, DAYS);
                Instant end = start.plus(3, DAYS);
                LocalTimeRangeFilter localTimeRangeFilter =
                        new LocalTimeRangeFilter.Builder()
                                .setStartTime(LocalDateTime.ofInstant(start, ZoneOffset.UTC))
                                .setEndTime(LocalDateTime.ofInstant(end, ZoneOffset.UTC))
                                .build();
                getAggregateResponseGroupByPeriod(
                        new AggregateRecordsRequest.Builder<>(localTimeRangeFilter)
                                .addAggregationType((AggregationType<Object>) aggregationType)
                                .build(),
                        Period.ofDays(1));
                Assert.fail(
                        "Aggregation group by period must be not allowed without right HC"
                                + " permission");
            } catch (HealthConnectException healthConnectException) {
                assertThat(healthConnectException.getErrorCode())
                        .isEqualTo(HealthConnectException.ERROR_SECURITY);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testReadMedicalResources_noPermission_expectError() throws InterruptedException {
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();

        manager.readMedicalResources(
                List.of(new MedicalResourceId("123", FHIR_RESOURCE_TYPE_IMMUNIZATION, "456")),
                Executors.newSingleThreadExecutor(),
                receiver);

        HealthConnectException exception = receiver.assertAndGetException();
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception.getMessage())
                .contains("Caller doesn't have permission to read or write medical data");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void createMedicalDataSource_noPermission_expectError() throws InterruptedException {
        CreateMedicalDataSourceRequest request =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        manager.createMedicalDataSource(request, executor, receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void upsertMedicalResources_noPermission_expectError() {
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                parseFhirVersion(FHIR_VERSION_R4),
                                FHIR_DATA_IMMUNIZATION)
                        .build();

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                manager.upsertMedicalResources(
                                        List.of(request),
                                        Executors.newSingleThreadExecutor(),
                                        receiver));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    private static List<Record> getTestRecords() {
        return Arrays.asList(
                getStepsRecord(),
                getHeartRateRecord(),
                buildSleepSession(),
                getDistanceRecordWithNonEmptyId(),
                getTotalCaloriesBurnedRecord("client_id"),
                buildExerciseSession(),
                MINDFULNESS_SESSION_RECORD_FACTORY.newEmptyRecord(
                        newEmptyMetadataWithClientId("mindfulness-client-id"),
                        NOW.minus(Duration.ofMinutes(20)),
                        NOW.minus(Duration.ofMinutes(10))));
    }
}
