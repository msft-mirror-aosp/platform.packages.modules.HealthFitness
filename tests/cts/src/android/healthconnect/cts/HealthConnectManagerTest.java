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

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.MIGRATE_HEALTH_CONNECT_DATA;
import static android.Manifest.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA;
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IDLE;
import static android.health.connect.HealthConnectDataState.RESTORE_ERROR_FETCHING_DATA;
import static android.health.connect.HealthConnectDataState.RESTORE_ERROR_NONE;
import static android.health.connect.HealthConnectDataState.RESTORE_STATE_IDLE;
import static android.health.connect.HealthConnectDataState.RESTORE_STATE_PENDING;
import static android.health.connect.HealthConnectManager.DATA_DOWNLOAD_COMPLETE;
import static android.health.connect.HealthConnectManager.DATA_DOWNLOAD_FAILED;
import static android.health.connect.HealthConnectManager.DATA_DOWNLOAD_STARTED;
import static android.health.connect.HealthConnectManager.isHealthPermission;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEART_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.getRecordsAndIdentifiers;
import static android.healthconnect.cts.utils.HealthConnectReceiver.callAndGetResponseWithShellPermissionIdentity;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.TestOutcomeReceiver.outcomeExecutor;
import static android.healthconnect.cts.utils.TestUtils.finishMigrationWithShellPermissionIdentity;
import static android.healthconnect.cts.utils.TestUtils.getRecordById;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.startMigrationWithShellPermissionIdentity;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Objects.requireNonNull;

import android.app.UiAutomation;
import android.content.Context;
import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectDataState;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthDataCategory;
import android.health.connect.HealthPermissions;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordTypeInfoResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.HydrationRecord;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Power;
import android.health.connect.datatypes.units.Volume;
import android.health.connect.restore.StageRemoteDataException;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.DataFactory;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestOutcomeReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** CTS test for API provided by HealthConnectManager. */
@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class HealthConnectManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "HealthConnectManagerTest";
    private static final String APP_PACKAGE_NAME = "android.healthconnect.cts";

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHealthConnectFullySupported,
                    "Tests should run on supported hardware only.");

    private Context mContext;
    private HealthConnectManager mManager;

    @Before
    public void before() throws InterruptedException {
        deleteAllRecords();
        TestUtils.deleteAllStagedRemoteData();
        mContext = ApplicationProvider.getApplicationContext();
        mManager = requireNonNull(mContext.getSystemService(HealthConnectManager.class));
    }

    @After
    public void after() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
    }

    private void deleteAllRecords() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME).build())
                        .build());
    }

    @Test
    public void testHCManagerIsAccessible_viaContextConstant() {
        assertThat(mContext.getSystemService(Context.HEALTHCONNECT_SERVICE))
                .isInstanceOf(HealthConnectManager.class);
    }

    @Test
    public void testRecordIdentifiers() {
        for (TestUtils.RecordAndIdentifier recordAndIdentifier : getRecordsAndIdentifiers()) {
            assertThat(recordAndIdentifier.getRecordClass().getRecordType())
                    .isEqualTo(recordAndIdentifier.getId());
        }
    }

    @Test
    public void testIsHealthPermission_forHealthPermission_returnsTrue() {
        assertThat(isHealthPermission(mContext, HealthPermissions.READ_ACTIVE_CALORIES_BURNED))
                .isTrue();
        assertThat(isHealthPermission(mContext, HealthPermissions.READ_ACTIVE_CALORIES_BURNED))
                .isTrue();
    }

    @Test
    public void testIsHealthPermission_forNonHealthGroupPermission_returnsFalse() {
        assertThat(isHealthPermission(mContext, HealthPermissions.MANAGE_HEALTH_PERMISSIONS))
                .isFalse();
        assertThat(isHealthPermission(mContext, CAMERA)).isFalse();
    }

    @Test
    public void testRandomIdWithInsert() throws Exception {
        // Insert a sample record of each data type.
        List<Record> insertRecords =
                TestUtils.insertRecords(
                        Collections.singletonList(DataFactory.getStepsRecord("abc")));
        assertThat(insertRecords.get(0).getMetadata().getId()).isNotNull();
        assertThat(insertRecords.get(0).getMetadata().getId()).isNotEqualTo("abc");
    }

    /**
     * Test to verify the working of {@link HealthConnectManager#updateRecords(java.util.List,
     * java.util.concurrent.Executor, android.os.OutcomeReceiver)}.
     *
     * <p>Insert a sample record of each dataType, update them and check by reading them.
     */
    @Test
    public void testUpdateRecords_validInput_dataBaseUpdatedSuccessfully() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectException> responseException = new AtomicReference<>();

        // Insert a sample record of each data type.
        List<Record> insertRecords = TestUtils.insertRecords(getTestRecords());

        // read inserted records and verify that the data is same as inserted.
        assertThat(insertRecords).containsExactlyElementsIn(readMultipleRecordTypes(insertRecords));

        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords = getTestRecords();

        // Modify the Uid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    setTestRecordId(
                            updateRecords.get(itr), insertRecords.get(itr).getMetadata().getId()));
        }

        mManager.updateRecords(
                updateRecords,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void result) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(@NonNull HealthConnectException exception) {
                        responseException.set(exception);
                        latch.countDown();
                        Log.e(
                                TAG,
                                "Exception: "
                                        + exception.getMessage()
                                        + ", error code: "
                                        + exception.getErrorCode());
                    }
                });

        // assert the inserted data has been modified per the updateRecords.
        assertThat(latch.await(3, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(responseException.get()).isNull();

        // assert the inserted data has been modified by reading the data.
        assertThat(updateRecords).containsExactlyElementsIn(readMultipleRecordTypes(updateRecords));
    }

    /**
     * Test to verify the working of {@link HealthConnectManager#updateRecords(java.util.List,
     * java.util.concurrent.Executor, android.os.OutcomeReceiver)}.
     *
     * <p>Insert a sample record of each dataType, while updating provide input with a few invalid
     * records. These records will have UUIDs that are not present in the table. Since this record
     * won't be updated, the transaction should fail and revert and no other record(even though
     * valid inputs) should not be modified either.
     */
    @Test
    public void testUpdateRecords_invalidInputRecords_noChangeInDataBase() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectException> responseException = new AtomicReference<>();

        // Insert a sample record of each data type.
        List<Record> insertRecords = TestUtils.insertRecords(getTestRecords());

        // read inserted records and verify that the data is same as inserted.
        assertThat(insertRecords).containsExactlyElementsIn(readMultipleRecordTypes(insertRecords));
        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords = getTestRecords();

        // Modify the Uid of the updateRecords to the UUID that was present in the insert records,
        // leaving out alternate records so that they have a new UUID which is not present in the
        // dataBase.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    setTestRecordId(
                            updateRecords.get(itr),
                            itr % 2 == 0
                                    ? insertRecords.get(itr).getMetadata().getId()
                                    : UUID.randomUUID().toString()));
        }

        // perform the update operation.
        mManager.updateRecords(
                updateRecords,
                Executors.newSingleThreadExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void result) {}

                    @Override
                    public void onError(@NonNull HealthConnectException exception) {
                        responseException.set(exception);
                        latch.countDown();
                        Log.e(
                                TAG,
                                "Exception: "
                                        + exception.getMessage()
                                        + ", error code: "
                                        + exception.getErrorCode());
                    }
                });

        assertThat(latch.await(/* timeout */ 3, TimeUnit.SECONDS)).isEqualTo(true);

        // assert the inserted data has not been modified by reading the data.
        assertThat(insertRecords).containsExactlyElementsIn(readMultipleRecordTypes(insertRecords));

        // verify that the testcase failed due to invalid argument exception.
        assertThat(responseException.get()).isNotNull();
        assertThat(responseException.get().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    /**
     * Test to verify the working of {@link HealthConnectManager#updateRecords(java.util.List,
     * java.util.concurrent.Executor, android.os.OutcomeReceiver)}.
     *
     * <p>Insert a sample record of each dataType, while updating add an input record with an
     * invalid packageName. Since this is an invalid record the transaction should fail and revert
     * and no other record(even though valid inputs) should not be modified either.
     */
    @Test
    public void testUpdateRecords_recordWithInvalidPackageName_noChangeInDataBase()
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> responseException = new AtomicReference<>();

        // Insert a sample record of each data type.
        List<Record> insertRecords = TestUtils.insertRecords(getTestRecords());

        // read inserted records and verify that the data is same as inserted.
        assertThat(insertRecords).containsExactlyElementsIn(readMultipleRecordTypes(insertRecords));
        // Generate a second set of records that will be used to perform the update operation.
        List<Record> updateRecords = getTestRecords();

        // Modify the Uuid of the updateRecords to the uuid that was present in the insert records.
        for (int itr = 0; itr < updateRecords.size(); itr++) {
            updateRecords.set(
                    itr,
                    setTestRecordId(
                            updateRecords.get(itr), insertRecords.get(itr).getMetadata().getId()));
            //             adding an entry with invalid packageName.
            if (updateRecords.get(itr).getRecordType() == RECORD_TYPE_STEPS) {
                updateRecords.set(itr, getStepsRecord(/* packageName= */ "abc.xyz.pqr"));
            }
        }

        try {
            // perform the update operation.
            mManager.updateRecords(
                    updateRecords,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException exception) {
                            responseException.set(exception);
                            latch.countDown();
                            Log.e(
                                    TAG,
                                    "Exception: "
                                            + exception.getMessage()
                                            + ", error code: "
                                            + exception.getErrorCode());
                        }
                    });

        } catch (Exception exception) {
            latch.countDown();
            responseException.set(exception);
        }
        assertThat(latch.await(/* timeout */ 3, TimeUnit.SECONDS)).isEqualTo(true);

        // assert the inserted data has not been modified by reading the data.
        assertThat(insertRecords).containsExactlyElementsIn(readMultipleRecordTypes(insertRecords));

        // verify that the testcase failed due to invalid argument exception.
        assertThat(responseException.get()).isNotNull();
        assertThat(responseException.get().getClass()).isEqualTo(IllegalArgumentException.class);
    }

    @Test
    public void testInsertRecords_intervalWithSameClientId_overwrites()
            throws InterruptedException {
        final String clientId = "stepsClientId";
        final int count1 = 10;
        final int count2 = 10;
        final Instant endTime1 = Instant.now();
        final Instant startTime1 = endTime1.minusMillis(1000L);
        final Instant endTime2 = endTime1.minusMillis(100L);
        final Instant startTime2 = startTime1.minusMillis(100L);

        TestUtils.insertRecordAndGetId(
                getStepsRecord(clientId, /* packageName= */ "", count1, startTime1, endTime1));
        TestUtils.insertRecordAndGetId(
                getStepsRecord(clientId, /* packageName= */ "", count2, startTime2, endTime2));

        final List<StepsRecord> records =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                                .addClientRecordId(clientId)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getCount()).isEqualTo(count2);
    }

    @Test
    public void testInsertRecords_intervalNoClientIdsAndSameTime_overwrites()
            throws InterruptedException {
        final int count1 = 10;
        final int count2 = 20;
        final Instant endTime = Instant.now();
        final Instant startTime = endTime.minusMillis(1000L);

        final String id1 =
                TestUtils.insertRecordAndGetId(
                        getStepsRecord(
                                /* clientRecordId= */ null,
                                /* packageName= */ "",
                                count1,
                                startTime,
                                endTime));
        final String id2 =
                TestUtils.insertRecordAndGetId(
                        getStepsRecord(
                                /* clientRecordId= */ null,
                                /* packageName= */ "",
                                count2,
                                startTime,
                                endTime));

        final List<StepsRecord> records =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                                .addId(id1)
                                .addId(id2)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(getRecordById(records, id2).getCount()).isEqualTo(count2);
    }

    @Test
    public void testInsertRecords_intervalDifferentClientIdsAndSameTime_doesNotOverwrite()
            throws InterruptedException {
        final int count1 = 10;
        final int count2 = 20;
        final Instant endTime = Instant.now();
        final Instant startTime = endTime.minusMillis(1000L);

        final String id1 =
                TestUtils.insertRecordAndGetId(
                        getStepsRecord(
                                "stepsClientId1",
                                /* packageName= */ "",
                                count1,
                                startTime,
                                endTime));
        final String id2 =
                TestUtils.insertRecordAndGetId(
                        getStepsRecord(
                                "stepsClientId2",
                                /* packageName= */ "",
                                count2,
                                startTime,
                                endTime));

        final List<StepsRecord> records =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                                .addId(id1)
                                .addId(id2)
                                .build());

        assertThat(records).hasSize(2);
        assertThat(getRecordById(records, id1).getCount()).isEqualTo(count1);
        assertThat(getRecordById(records, id2).getCount()).isEqualTo(count2);
    }

    @Test
    public void testInsertRecords_instantWithSameClientId_overwrites() throws InterruptedException {
        final String clientId = "bmrClientId";
        final Power bmr1 = Power.fromWatts(100.0);
        final Power bmr2 = Power.fromWatts(110.0);
        final Instant time1 = Instant.now();
        final Instant time2 = time1.minusMillis(100L);

        TestUtils.insertRecordAndGetId(getBasalMetabolicRateRecord(clientId, bmr1, time1));
        TestUtils.insertRecordAndGetId(getBasalMetabolicRateRecord(clientId, bmr2, time2));

        final List<BasalMetabolicRateRecord> records =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(BasalMetabolicRateRecord.class)
                                .addClientRecordId(clientId)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getBasalMetabolicRate()).isEqualTo(bmr2);
    }

    @Test
    public void testInsertRecords_instantNoClientIdsAndSameTime_overwrites()
            throws InterruptedException {
        final Power bmr1 = Power.fromWatts(100.0);
        final Power bmr2 = Power.fromWatts(110.0);
        final Instant time = Instant.now();

        final String id1 =
                TestUtils.insertRecordAndGetId(
                        getBasalMetabolicRateRecord(/* clientRecordId= */ null, bmr1, time));
        final String id2 =
                TestUtils.insertRecordAndGetId(
                        getBasalMetabolicRateRecord(/* clientRecordId= */ null, bmr2, time));

        final List<BasalMetabolicRateRecord> records =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(BasalMetabolicRateRecord.class)
                                .addId(id1)
                                .addId(id2)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(getRecordById(records, id2).getBasalMetabolicRate()).isEqualTo(bmr2);
    }

    @Test
    public void testInsertRecords_instantDifferentClientIdsAndSameTime_doesNotOverwrite()
            throws InterruptedException {
        final Power bmr1 = Power.fromWatts(100.0);
        final Power bmr2 = Power.fromWatts(110.0);
        final Instant time = Instant.now();

        final String id1 =
                TestUtils.insertRecordAndGetId(
                        getBasalMetabolicRateRecord(
                                /* clientRecordId= */ "bmrClientId1", bmr1, time));
        final String id2 =
                TestUtils.insertRecordAndGetId(
                        getBasalMetabolicRateRecord(
                                /* clientRecordId= */ "bmrClientId2", bmr2, time));

        final List<BasalMetabolicRateRecord> records =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(BasalMetabolicRateRecord.class)
                                .addId(id1)
                                .addId(id2)
                                .build());

        assertThat(records).hasSize(2);
        assertThat(getRecordById(records, id1).getBasalMetabolicRate()).isEqualTo(bmr1);
        assertThat(getRecordById(records, id2).getBasalMetabolicRate()).isEqualTo(bmr2);
    }

    // Special case for hydration, must not override
    @Test
    public void testInsertRecords_hydrationNoClientIdsAndSameTime_doesNotOverwrite()
            throws InterruptedException {
        final Volume volume1 = Volume.fromLiters(0.1);
        final Volume volume2 = Volume.fromLiters(0.2);
        final Instant endTime = Instant.now();
        final Instant startTime = endTime.minusMillis(1000L);

        final String id1 =
                TestUtils.insertRecordAndGetId(getHydrationRecord(startTime, endTime, volume1));
        final String id2 =
                TestUtils.insertRecordAndGetId(getHydrationRecord(startTime, endTime, volume2));

        final List<HydrationRecord> records =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(HydrationRecord.class)
                                .addId(id1)
                                .addId(id2)
                                .build());

        assertThat(records).hasSize(2);
        assertThat(getRecordById(records, id1).getVolume()).isEqualTo(volume1);
        assertThat(getRecordById(records, id2).getVolume()).isEqualTo(volume2);
    }

    // Special case for nutrition, must not override
    @Test
    public void testInsertRecords_nutritionNoClientIdsAndSameTime_doesNotOverwrite()
            throws InterruptedException {
        final Mass protein1 = Mass.fromGrams(1.0);
        final Mass protein2 = Mass.fromGrams(1.0);
        final Instant endTime = Instant.now();
        final Instant startTime = endTime.minusMillis(1000L);

        final String id1 =
                TestUtils.insertRecordAndGetId(getNutritionRecord(startTime, endTime, protein1));
        final String id2 =
                TestUtils.insertRecordAndGetId(getNutritionRecord(startTime, endTime, protein2));

        final List<NutritionRecord> records =
                TestUtils.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(NutritionRecord.class)
                                .addId(id1)
                                .addId(id2)
                                .build());

        assertThat(records).hasSize(2);
        assertThat(getRecordById(records, id1).getProtein()).isEqualTo(protein1);
        assertThat(getRecordById(records, id2).getProtein()).isEqualTo(protein2);
    }

    // b/24128192
    @Test
    public void testInsertRecords_metadataGiven_responseDoesNotMutateMetadataReference()
            throws InterruptedException {
        Metadata metadata = DataFactory.getEmptyMetadata();
        StepsRecord stepsRecord = DataFactory.getStepsRecord(20, metadata);

        TestUtils.insertRecordAndGetId(stepsRecord);

        assertThat(stepsRecord.getMetadata().getId()).isEmpty();
    }

    @Test
    public void testAggregation_stepsCountTotal_acrossDST_works() throws Exception {
        ZoneOffset utcPlusOne = ZoneOffset.ofTotalSeconds(UTC.getTotalSeconds() + 3600);

        Instant midNight = Instant.now().truncatedTo(DAYS).minus(1, DAYS);

        Instant t0057 = midNight.plus(57, MINUTES);
        Instant t0058 = midNight.plus(58, MINUTES);
        Instant t0059 = midNight.plus(59, MINUTES);
        Instant t0100 = midNight.plus(1, HOURS);
        Instant t0300 = midNight.plus(3, HOURS);
        Instant t0400 = midNight.plus(4, HOURS);

        List<Record> records =
                Arrays.asList(
                        getStepsRecord(
                                t0057, utcPlusOne, t0058, utcPlusOne, 12), // 1:57-1:58 in test
                        // this will be removed by the workaround
                        getStepsRecord(t0059, utcPlusOne, t0100, UTC, 16), // 1:59-1:00 in test
                        getStepsRecord(t0300, UTC, t0400, UTC, 250));
        TestUtils.setupAggregation(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        TestUtils.insertRecords(records);
        LocalDateTime startOfYesterday = LocalDateTime.now(UTC).truncatedTo(DAYS).minusDays(1);
        AggregateRecordsRequest<Long> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Long>(
                                new LocalTimeRangeFilter.Builder()
                                        .setStartTime(startOfYesterday.plusHours(1))
                                        .setEndTime(startOfYesterday.plusHours(4))
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();
        assertThat(aggregateRecordsRequest.getAggregationTypes()).isNotNull();
        assertThat(aggregateRecordsRequest.getTimeRangeFilter()).isNotNull();
        assertThat(aggregateRecordsRequest.getDataOriginsFilters()).isNotNull();

        AggregateRecordsResponse<Long> aggregateResponse =
                TestUtils.getAggregateResponse(aggregateRecordsRequest);
        assertThat(aggregateResponse.get(STEPS_COUNT_TOTAL)).isEqualTo(262);

        List<AggregateRecordsGroupedByDurationResponse<Long>> groupByResponse =
                TestUtils.getAggregateResponseGroupByDuration(
                        aggregateRecordsRequest, Duration.ofHours(1));

        assertThat(groupByResponse.get(0).getStartTime()).isEqualTo(midNight);
        assertThat(groupByResponse.get(0).getEndTime()).isEqualTo(t0100);
        assertThat(groupByResponse.get(0).getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(utcPlusOne);
        assertThat(groupByResponse.get(0).get(STEPS_COUNT_TOTAL)).isEqualTo(12);

        // When no data falls in a given bucket, zone offset will be null and we use system default
        // zone to set bucket start and end time
        LocalDateTime localStart = t0100.atZone(utcPlusOne).toLocalDateTime();
        LocalDateTime localEnd = localStart.plusHours(1);
        ZoneOffset startZone = ZoneOffset.systemDefault().getRules().getOffset(localStart);
        Instant start = localStart.atZone(startZone).toInstant();
        ZoneOffset endZone = ZoneOffset.systemDefault().getRules().getOffset(localEnd);
        Instant end = localEnd.atZone(endZone).toInstant();
        assertThat(groupByResponse.get(1).getStartTime()).isEqualTo(start);
        assertThat(groupByResponse.get(1).getEndTime()).isEqualTo(end);
        assertThat(groupByResponse.get(1).getZoneOffset(STEPS_COUNT_TOTAL)).isNull();

        assertThat(groupByResponse.get(2).getStartTime()).isEqualTo(t0300);
        assertThat(groupByResponse.get(2).getEndTime()).isEqualTo(t0400);
        assertThat(groupByResponse.get(2).getZoneOffset(STEPS_COUNT_TOTAL)).isEqualTo(UTC);
        assertThat(groupByResponse.get(2).get(STEPS_COUNT_TOTAL)).isEqualTo(250);
    }

    @Test
    public void testAutoDeleteApis() throws InterruptedException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        TestUtils.setAutoDeletePeriod(30);
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            assertThat(mManager.getRecordRetentionPeriodInDays()).isEqualTo(30);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }

        TestUtils.setAutoDeletePeriod(0);
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            assertThat(mManager.getRecordRetentionPeriodInDays()).isEqualTo(0);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testStageRemoteData_withValidInput_noExceptionsReturned() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        Void unused =
                callAndGetResponseWithShellPermissionIdentity(
                        StageRemoteDataException.class,
                        (executor, receiver) ->
                                mManager.stageAllHealthConnectRemoteData(
                                        pfdsByFileName, executor, receiver),
                        STAGE_HEALTH_CONNECT_REMOTE_DATA);
    }

    @Test
    public void testStageRemoteData_whenNotReadMode_errorIoReturned() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_WRITE_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        TestOutcomeReceiver<Void, StageRemoteDataException> receiver = new TestOutcomeReceiver<>();
        StageRemoteDataException stageRemoteDataException =
                runWithShellPermissionIdentity(
                        () -> {
                            mManager.stageAllHealthConnectRemoteData(
                                    pfdsByFileName, outcomeExecutor(), receiver);
                            return receiver.assertAndGetException();
                        },
                        STAGE_HEALTH_CONNECT_REMOTE_DATA);

        assertThat(stageRemoteDataException.getExceptionsByFileNames())
                .comparingValuesUsing(
                        transforming(HealthConnectException::getErrorCode, "has error code"))
                .containsExactly("testRestoreFile1", HealthConnectException.ERROR_IO);
    }

    @Test
    public void testStageRemoteData_whenStagingStagedData_noExceptionsReturned() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        Void unused =
                callAndGetResponseWithShellPermissionIdentity(
                        StageRemoteDataException.class,
                        (executor, receiver) ->
                                mManager.stageAllHealthConnectRemoteData(
                                        pfdsByFileName, executor, receiver),
                        STAGE_HEALTH_CONNECT_REMOTE_DATA);

        // send the files again
        unused =
                callAndGetResponseWithShellPermissionIdentity(
                        StageRemoteDataException.class,
                        (executor, receiver) ->
                                mManager.stageAllHealthConnectRemoteData(
                                        pfdsByFileName, executor, receiver),
                        STAGE_HEALTH_CONNECT_REMOTE_DATA);
    }

    @Test
    public void testStageRemoteData_withoutPermission_errorSecurityReturned() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_WRITE_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        TestOutcomeReceiver<Void, StageRemoteDataException> receiver = new TestOutcomeReceiver<>();
        mManager.stageAllHealthConnectRemoteData(pfdsByFileName, outcomeExecutor(), receiver);

        StageRemoteDataException stageRemoteDataException = receiver.assertAndGetException();
        assertThat(stageRemoteDataException.getExceptionsByFileNames())
                .comparingValuesUsing(
                        transforming(HealthConnectException::getErrorCode, "has error code"))
                .containsExactly("", HealthConnectException.ERROR_SECURITY);
    }

    @Test
    public void testUpdateDataDownloadState_withoutPermission_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> mManager.updateDataDownloadState(DATA_DOWNLOAD_STARTED));
    }

    @Test
    public void testGetHealthConnectDataState_beforeDownload_returnsIdleState() throws Exception {
        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        mManager::getHealthConnectDataState, MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataRestoreState()).isEqualTo(RESTORE_STATE_IDLE);
    }

    @Test
    public void
            testGetHealthConnectDataState_beforeDownload_withMigrationPermission_returnsIdleState()
                    throws Exception {
        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        mManager::getHealthConnectDataState, MIGRATE_HEALTH_CONNECT_DATA);

        assertThat(healthConnectDataState.getDataRestoreState()).isEqualTo(RESTORE_STATE_IDLE);
    }

    @Test
    public void testGetHealthConnectDataState_duringDownload_returnsRestorePendingState()
            throws Exception {
        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        (executor, receiver) -> {
                            mManager.updateDataDownloadState(DATA_DOWNLOAD_STARTED);
                            mManager.getHealthConnectDataState(executor, receiver);
                        },
                        STAGE_HEALTH_CONNECT_REMOTE_DATA,
                        MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataRestoreState()).isEqualTo(RESTORE_STATE_PENDING);
    }

    @Test
    public void testGetHealthConnectDataState_whenDownloadDone_returnsRestorePendingState()
            throws Exception {
        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        (executor, receiver) -> {
                            mManager.updateDataDownloadState(DATA_DOWNLOAD_COMPLETE);
                            mManager.getHealthConnectDataState(executor, receiver);
                        },
                        STAGE_HEALTH_CONNECT_REMOTE_DATA,
                        MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataRestoreState()).isEqualTo(RESTORE_STATE_PENDING);
    }

    @Test
    public void testGetHealthConnectDataState_whenDownloadFailed_returnsIdleState()
            throws Exception {
        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        (executor, receiver) -> {
                            mManager.updateDataDownloadState(DATA_DOWNLOAD_FAILED);
                            mManager.getHealthConnectDataState(executor, receiver);
                        },
                        STAGE_HEALTH_CONNECT_REMOTE_DATA,
                        MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataRestoreState()).isEqualTo(RESTORE_STATE_IDLE);
    }

    @Test
    public void testGetHealthConnectDataState_afterStagingAndMerge_returnsStateIdle()
            throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        Void unused =
                callAndGetResponseWithShellPermissionIdentity(
                        StageRemoteDataException.class,
                        (executor, receiver) ->
                                mManager.stageAllHealthConnectRemoteData(
                                        pfdsByFileName, executor, receiver),
                        STAGE_HEALTH_CONNECT_REMOTE_DATA);

        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        mManager::getHealthConnectDataState, MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataRestoreState()).isEqualTo(RESTORE_STATE_IDLE);
    }

    @Test
    public void testGetDataRestoreError_onErrorDuringStaging_returnsErrorFetching()
            throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_WRITE_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        TestOutcomeReceiver<Void, StageRemoteDataException> stageReceiver =
                new TestOutcomeReceiver<>();
        runWithShellPermissionIdentity(
                () -> {
                    mManager.stageAllHealthConnectRemoteData(
                            pfdsByFileName, outcomeExecutor(), stageReceiver);
                    stageReceiver.assertAndGetException();
                },
                STAGE_HEALTH_CONNECT_REMOTE_DATA);

        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        mManager::getHealthConnectDataState, MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataRestoreError())
                .isEqualTo(RESTORE_ERROR_FETCHING_DATA);
    }

    @Test
    public void testGetDataRestoreError_onDownloadFailed_returnsErrorFetching() throws Exception {
        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        (executor, receiver) -> {
                            mManager.updateDataDownloadState(DATA_DOWNLOAD_FAILED);
                            mManager.getHealthConnectDataState(executor, receiver);
                        },
                        STAGE_HEALTH_CONNECT_REMOTE_DATA,
                        MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataRestoreError())
                .isEqualTo(RESTORE_ERROR_FETCHING_DATA);
    }

    @Test
    public void testGetDataRestoreError_onNoErrorDuringRestore_returnsNoError() throws Exception {
        File dataDir = mContext.getDataDir();
        File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
        File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

        assertThat(testRestoreFile1.exists()).isTrue();
        assertThat(testRestoreFile2.exists()).isTrue();

        Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
        pfdsByFileName.put(
                testRestoreFile1.getName(),
                ParcelFileDescriptor.open(testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
        pfdsByFileName.put(
                testRestoreFile2.getName(),
                ParcelFileDescriptor.open(testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

        Void unused =
                callAndGetResponseWithShellPermissionIdentity(
                        StageRemoteDataException.class,
                        (executor, receiver) ->
                                mManager.stageAllHealthConnectRemoteData(
                                        pfdsByFileName, executor, receiver),
                        STAGE_HEALTH_CONNECT_REMOTE_DATA);

        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        mManager::getHealthConnectDataState, MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataRestoreError()).isEqualTo(RESTORE_ERROR_NONE);
    }

    @Test
    public void testDataMigrationState_byDefault_returnsIdleState() throws Exception {
        HealthConnectDataState healthConnectDataState =
                callAndGetResponseWithShellPermissionIdentity(
                        mManager::getHealthConnectDataState, MANAGE_HEALTH_DATA);

        assertThat(healthConnectDataState.getDataMigrationState()).isEqualTo(MIGRATION_STATE_IDLE);
    }

    @Test
    public void testGetHealthConnectDataState_withoutPermission_returnsSecurityException()
            throws Exception {
        HealthConnectReceiver<HealthConnectDataState> receiver = new HealthConnectReceiver<>();
        mManager.getHealthConnectDataState(outcomeExecutor(), receiver);

        HealthConnectException healthConnectException = receiver.assertAndGetException();
        assertThat(healthConnectException.getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    public void testDataApis_migrationInProgress_apisBlocked() throws InterruptedException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        startMigrationWithShellPermissionIdentity();

        StepsRecord testRecord = DataFactory.getStepsRecord();

        try {
            testRecord = (StepsRecord) TestUtils.insertRecord(testRecord);
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        try {
            ReadRecordsRequestUsingIds<StepsRecord> request =
                    new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                            .addId(testRecord.getMetadata().getId())
                            .build();
            TestUtils.readRecords(request);
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        try {
            TestUtils.updateRecords(Collections.singletonList(testRecord));
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        try {
            TestUtils.deleteRecords(Collections.singletonList(testRecord));
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        try {
            TestUtils.getActivityDates(Collections.singletonList(testRecord.getClass()));
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            TestUtils.getApplicationInfo();
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            TestUtils.queryAccessLogs();
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }

        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            TestUtils.setAutoDeletePeriod(1);
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }

        try {
            TestUtils.getChangeLogToken(
                    new ChangeLogTokenRequest.Builder().addRecordType(StepsRecord.class).build());
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        try {
            TestUtils.getChangeLogs(new ChangeLogsRequest.Builder(/* token */ "").build());
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        AggregateRecordsRequest<Long> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.now().minus(3, DAYS))
                                        .setEndTime(Instant.now())
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();

        try {
            TestUtils.getAggregateResponse(
                    aggregateRecordsRequest, Collections.singletonList(testRecord));
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        try {
            TestUtils.getAggregateResponseGroupByDuration(
                    aggregateRecordsRequest, Duration.ofDays(1));
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        try {
            TestUtils.getAggregateResponseGroupByPeriod(
                    new AggregateRecordsRequest.Builder<Long>(
                                    new LocalTimeRangeFilter.Builder()
                                            .setStartTime(LocalDateTime.now(UTC).minusDays(2))
                                            .setEndTime(LocalDateTime.now(UTC))
                                            .build())
                            .addAggregationType(STEPS_COUNT_TOTAL)
                            .build(),
                    Period.ofDays(1));
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
        }

        finishMigrationWithShellPermissionIdentity();
    }

    @Test
    public void testGetRecordTypeInfo_InsertRecords_correctContributingPackages() throws Exception {
        // Insert a set of test records for StepRecords, ExerciseSessionRecord, HeartRateRecord,
        // BasalMetabolicRateRecord.
        List<Record> testRecords = DataFactory.getTestRecords();
        List<Record> insertedRecords = insertRecords(testRecords);

        // Populate expected records. This method puts empty lists as contributing packages for all
        // records.
        HashMap<Class<? extends Record>, TestUtils.RecordTypeInfoTestResponse> expectedResponseMap =
                new HashMap<>();
        TestUtils.populateAndResetExpectedResponseMap(expectedResponseMap);
        // Populate contributing packages list for expected records by adding the current cts
        // package.
        expectedResponseMap.get(StepsRecord.class).getContributingPackages().add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(ExerciseSessionRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(HeartRateRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(BasalMetabolicRateRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);

        // When recordTypes are modified the appInfo also gets updated and this update happens on
        // a background thread. To ensure the test has the latest values for appInfo, add a wait
        // time before fetching it.
        Thread.sleep(500);
        // since test records contains the following records
        Map<Class<? extends Record>, RecordTypeInfoResponse> response =
                TestUtils.queryAllRecordTypesInfo();
        if (isEmptyContributingPackagesForAll(response)) {
            return;
        }

        // verify response data is correct.
        verifyRecordTypeResponse(response, expectedResponseMap);

        // delete first set inserted records.
        TestUtils.deleteRecords(insertedRecords);

        // clear out contributing packages.
        TestUtils.populateAndResetExpectedResponseMap(expectedResponseMap);
        // delete inserted records.

        // When recordTypes are modified the appInfo also gets updated and this update happens on
        // a background thread. To ensure the test has the latest values for appInfo, add a wait
        // time before fetching it.
        Thread.sleep(500);
        response = TestUtils.queryAllRecordTypesInfo();
        // verify that the API still returns all record types with the cts packages as contributing
        // package. this is because only one of the inserted record for each record type was
        // deleted.
        verifyRecordTypeResponse(response, expectedResponseMap);
    }

    @Test
    public void testGetRecordTypeInfo_partiallyDeleteInsertedRecords_correctContributingPackages()
            throws Exception {
        // Insert a sets of test records for StepRecords, ExerciseSessionRecord, HeartRateRecord,
        // BasalMetabolicRateRecord.
        List<Record> testRecords = DataFactory.getTestRecords();
        List<Record> insertedRecords = insertRecords(testRecords);

        // Populate expected records. This method puts empty lists as contributing packages for all
        // records.
        HashMap<Class<? extends Record>, TestUtils.RecordTypeInfoTestResponse> expectedResponseMap =
                new HashMap<>();
        TestUtils.populateAndResetExpectedResponseMap(expectedResponseMap);
        // Populate contributing packages list for expected records by adding the current cts
        // package.
        expectedResponseMap.get(StepsRecord.class).getContributingPackages().add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(ExerciseSessionRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(HeartRateRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(BasalMetabolicRateRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);

        // When recordTypes are modified the appInfo also gets updated and this update happens on
        // a background thread. To ensure the test has the latest values for appInfo, add a wait
        // time before fetching it.
        Thread.sleep(500);
        // since test records contains the following records
        Map<Class<? extends Record>, RecordTypeInfoResponse> response =
                TestUtils.queryAllRecordTypesInfo();
        if (isEmptyContributingPackagesForAll(response)) {
            return;
        }
        // verify response data is correct.
        verifyRecordTypeResponse(response, expectedResponseMap);

        // delete 2 of the inserted records.
        ArrayList<Record> recordsToBeDeleted = new ArrayList<>();
        for (int itr = 0; itr < insertedRecords.size() / 2; itr++) {
            recordsToBeDeleted.add(insertedRecords.get(itr));
            expectedResponseMap
                    .get(insertedRecords.get(itr).getClass())
                    .getContributingPackages()
                    .clear();
        }

        TestUtils.deleteRecords(recordsToBeDeleted);

        // When recordTypes are modified the appInfo also gets updated and this update happens on
        // a background thread. To ensure the test has the latest values for appInfo, add a wait
        // time before fetching it.
        Thread.sleep(500);
        response = TestUtils.queryAllRecordTypesInfo();
        if (isEmptyContributingPackagesForAll(response)) {
            return;
        }
        verifyRecordTypeResponse(response, expectedResponseMap);
    }

    @Test
    public void testGetRecordTypeInfo_MultipleInsertedRecords_correctContributingPackages()
            throws Exception {
        // Insert 2 sets of test records for StepRecords, ExerciseSessionRecord, HeartRateRecord,
        // BasalMetabolicRateRecord.
        List<Record> testRecords = TestUtils.insertRecords(DataFactory.getTestRecords());

        TestUtils.insertRecords(DataFactory.getTestRecords());

        // When recordTypes are modified the appInfo also gets updated and this update happens on
        // a background thread. To ensure the test has the latest values for appInfo, add a wait
        // time before fetching it.
        Thread.sleep(500);
        // Populate expected records. This method puts empty lists as contributing packages for all
        // records.
        HashMap<Class<? extends Record>, TestUtils.RecordTypeInfoTestResponse> expectedResponseMap =
                new HashMap<>();
        TestUtils.populateAndResetExpectedResponseMap(expectedResponseMap);
        // Populate contributing packages list for expected records by adding the current cts
        // package.
        expectedResponseMap.get(StepsRecord.class).getContributingPackages().add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(ExerciseSessionRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(HeartRateRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);
        expectedResponseMap
                .get(BasalMetabolicRateRecord.class)
                .getContributingPackages()
                .add(APP_PACKAGE_NAME);

        // since test records contains the following records
        Map<Class<? extends Record>, RecordTypeInfoResponse> response =
                TestUtils.queryAllRecordTypesInfo();
        if (isEmptyContributingPackagesForAll(response)) {
            return;
        }
        // verify response data is correct.
        verifyRecordTypeResponse(response, expectedResponseMap);

        // delete only one set of inserted records.
        TestUtils.deleteRecords(testRecords);

        // When recordTypes are modified the appInfo also gets updated and this update happens on
        // a background thread. To ensure the test has the latest values for appInfo, add a wait
        // time before fetching it.
        Thread.sleep(500);
        response = TestUtils.queryAllRecordTypesInfo();
        if (isEmptyContributingPackagesForAll(response)) {
            return;
        }

        verifyRecordTypeResponse(response, expectedResponseMap);
    }

    private boolean isEmptyContributingPackagesForAll(
            Map<Class<? extends Record>, RecordTypeInfoResponse> response) {
        // If all the responses have empty lists in their contributing packages then we
        // return true. This can happen when the sync or insert took a long time to run, or they
        // faced an issue while running.
        return response.values().stream()
                .map(RecordTypeInfoResponse::getContributingPackages)
                .allMatch(List::isEmpty);
    }

    private static void verifyRecordTypeResponse(
            Map<Class<? extends Record>, RecordTypeInfoResponse> responses,
            HashMap<Class<? extends Record>, TestUtils.RecordTypeInfoTestResponse>
                    expectedResponse) {
        responses.forEach(
                (recordTypeClass, recordTypeInfoResponse) -> {
                    TestUtils.RecordTypeInfoTestResponse expectedTestResponse =
                            expectedResponse.get(recordTypeClass);
                    // Ignore unknown record types in the response.
                    if (expectedTestResponse == null) {
                        return;
                    }
                    assertThat(recordTypeInfoResponse.getPermissionCategory())
                            .isEqualTo(expectedTestResponse.getRecordTypePermission());
                    assertThat(recordTypeInfoResponse.getDataCategory())
                            .isEqualTo(expectedTestResponse.getRecordTypeCategory());
                    ArrayList<String> contributingPackagesAsStrings = new ArrayList<>();
                    for (DataOrigin pck : recordTypeInfoResponse.getContributingPackages()) {
                        contributingPackagesAsStrings.add(pck.getPackageName());
                    }
                    Collections.sort(contributingPackagesAsStrings);
                    Collections.sort(expectedTestResponse.getContributingPackages());
                    assertThat(contributingPackagesAsStrings)
                            .isEqualTo(expectedTestResponse.getContributingPackages());
                });
    }

    private static List<Record> getTestRecords() {
        return Arrays.asList(
                getStepsRecord(/* packageName= */ ""),
                getHeartRateRecord(),
                getBasalMetabolicRateRecord());
    }

    private static Record setTestRecordId(Record record, String id) {
        Metadata metadata = record.getMetadata();
        Metadata metadataWithId =
                new Metadata.Builder()
                        .setId(id)
                        .setClientRecordId(metadata.getClientRecordId())
                        .setClientRecordVersion(metadata.getClientRecordVersion())
                        .setDataOrigin(metadata.getDataOrigin())
                        .setDevice(metadata.getDevice())
                        .setLastModifiedTime(metadata.getLastModifiedTime())
                        .build();
        switch (record.getRecordType()) {
            case RECORD_TYPE_STEPS:
                return new StepsRecord.Builder(
                                metadataWithId, Instant.now(), Instant.now().plusMillis(1000), 10)
                        .build();
            case RECORD_TYPE_HEART_RATE:
                HeartRateRecord.HeartRateSample heartRateSample =
                        new HeartRateRecord.HeartRateSample(72, Instant.now().plusMillis(100));
                ArrayList<HeartRateRecord.HeartRateSample> heartRateSamples = new ArrayList<>();
                heartRateSamples.add(heartRateSample);
                heartRateSamples.add(heartRateSample);
                return new HeartRateRecord.Builder(
                                metadataWithId,
                                Instant.now(),
                                Instant.now().plusMillis(1000),
                                heartRateSamples)
                        .build();
            case RECORD_TYPE_BASAL_METABOLIC_RATE:
                return new BasalMetabolicRateRecord.Builder(
                                metadataWithId, Instant.now(), Power.fromWatts(100.0))
                        .setZoneOffset(
                                ZoneOffset.systemDefault().getRules().getOffset(Instant.now()))
                        .build();
            default:
                throw new IllegalStateException("Invalid record type.");
        }
    }

    private static List<Record> readMultipleRecordTypes(List<Record> insertedRecords)
            throws InterruptedException {
        List<Record> readRecords = new ArrayList<>();
        for (Record record : insertedRecords) {
            switch (record.getRecordType()) {
                case RECORD_TYPE_STEPS:
                    readRecords.addAll(
                            TestUtils.readRecords(
                                    new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                                            .addId(record.getMetadata().getId())
                                            .build()));
                    break;
                case RECORD_TYPE_HEART_RATE:
                    readRecords.addAll(
                            TestUtils.readRecords(
                                    new ReadRecordsRequestUsingIds.Builder<>(HeartRateRecord.class)
                                            .addId(record.getMetadata().getId())
                                            .build()));
                    break;
                case RECORD_TYPE_BASAL_METABOLIC_RATE:
                    readRecords.addAll(
                            TestUtils.readRecords(
                                    new ReadRecordsRequestUsingIds.Builder<>(
                                                    BasalMetabolicRateRecord.class)
                                            .addId(record.getMetadata().getId())
                                            .build()));
                    break;
            }
        }
        return readRecords;
    }

    private static StepsRecord getStepsRecord(String packageName) {
        return getStepsRecord(
                /* clientRecordId= */ null,
                packageName,
                /* count= */ 10,
                Instant.now(),
                Instant.now().plusMillis(1000));
    }

    private static StepsRecord getStepsRecord(
            @Nullable String clientRecordId,
            String packageName,
            int count,
            Instant startTime,
            Instant endTime) {
        Device device = getWatchDevice();
        DataOrigin dataOrigin = getDataOrigin(packageName);
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        if (clientRecordId != null) {
            testMetadataBuilder.setClientRecordId(clientRecordId);
        }
        return new StepsRecord.Builder(testMetadataBuilder.build(), startTime, endTime, count)
                .build();
    }

    private static StepsRecord getStepsRecord(
            Instant startTime,
            ZoneOffset startOffset,
            Instant endTime,
            ZoneOffset endOffset,
            int count) {
        StepsRecord.Builder builder =
                new StepsRecord.Builder(new Metadata.Builder().build(), startTime, endTime, count);
        if (startOffset != null) {
            builder.setStartZoneOffset(startOffset);
        }
        if (endOffset != null) {
            builder.setEndZoneOffset(endOffset);
        }
        return builder.build();
    }

    private static HeartRateRecord getHeartRateRecord() {
        HeartRateRecord.HeartRateSample heartRateSample =
                new HeartRateRecord.HeartRateSample(72, Instant.now().plusMillis(100));
        ArrayList<HeartRateRecord.HeartRateSample> heartRateSamples = new ArrayList<>();
        heartRateSamples.add(heartRateSample);
        heartRateSamples.add(heartRateSample);
        Device device = getWatchDevice();
        DataOrigin dataOrigin = getDataOrigin();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        return new HeartRateRecord.Builder(
                        testMetadataBuilder.build(),
                        Instant.now(),
                        Instant.now().plusMillis(1000),
                        heartRateSamples)
                .build();
    }

    private static BasalMetabolicRateRecord getBasalMetabolicRateRecord() {
        return getBasalMetabolicRateRecord(
                /* clientRecordId= */ null, /* bmr= */ Power.fromWatts(100.0), Instant.now());
    }

    private static BasalMetabolicRateRecord getBasalMetabolicRateRecord(
            String clientRecordId, Power bmr, Instant time) {
        Device device = getPhoneDevice();
        DataOrigin dataOrigin = getDataOrigin();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        if (clientRecordId != null) {
            testMetadataBuilder.setClientRecordId(clientRecordId);
        }
        return new BasalMetabolicRateRecord.Builder(testMetadataBuilder.build(), time, bmr).build();
    }

    private static HydrationRecord getHydrationRecord(
            Instant startTime, Instant endTime, Volume volume) {
        Device device = getPhoneDevice();
        DataOrigin dataOrigin = getDataOrigin();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        return new HydrationRecord.Builder(testMetadataBuilder.build(), startTime, endTime, volume)
                .build();
    }

    private static NutritionRecord getNutritionRecord(
            Instant startTime, Instant endTime, Mass protein) {
        Device device = getPhoneDevice();
        DataOrigin dataOrigin = getDataOrigin();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        return new NutritionRecord.Builder(testMetadataBuilder.build(), startTime, endTime)
                .setProtein(protein)
                .build();
    }

    private static Device getWatchDevice() {
        return new Device.Builder().setManufacturer("google").setModel("Pixel").setType(1).build();
    }

    private static Device getPhoneDevice() {
        return new Device.Builder()
                .setManufacturer("google")
                .setModel("Pixel4a")
                .setType(2)
                .build();
    }

    private static DataOrigin getDataOrigin() {
        return getDataOrigin(/* packageName= */ "");
    }

    private static DataOrigin getDataOrigin(String packageName) {
        return new DataOrigin.Builder()
                .setPackageName(packageName.isEmpty() ? APP_PACKAGE_NAME : packageName)
                .build();
    }

    private static File createAndGetNonEmptyFile(File dir, String fileName) throws IOException {
        File file = new File(dir, fileName);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("Contents of file " + fileName);
        fileWriter.close();
        return file;
    }
}
