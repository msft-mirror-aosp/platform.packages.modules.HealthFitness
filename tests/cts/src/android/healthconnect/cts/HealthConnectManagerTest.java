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
import static android.health.connect.HealthConnectDataState.MIGRATION_STATE_IDLE;
import static android.health.connect.HealthConnectDataState.RESTORE_ERROR_FETCHING_DATA;
import static android.health.connect.HealthConnectDataState.RESTORE_ERROR_NONE;
import static android.health.connect.HealthConnectDataState.RESTORE_STATE_IDLE;
import static android.health.connect.HealthConnectDataState.RESTORE_STATE_PENDING;
import static android.health.connect.HealthConnectManager.DATA_DOWNLOAD_COMPLETE;
import static android.health.connect.HealthConnectManager.DATA_DOWNLOAD_FAILED;
import static android.health.connect.HealthConnectManager.DATA_DOWNLOAD_STARTED;
import static android.health.connect.HealthConnectManager.isHealthPermission;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEART_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.MAXIMUM_PAGE_SIZE;
import static android.healthconnect.cts.utils.DataFactory.getRecordsAndIdentifiers;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_ID_EMPTY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_UNSUPPORTED_RESOURCE_TYPE;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_ID_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_UNSUPPORTED;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResourceId;
import static android.healthconnect.cts.utils.PhrDataFactory.getUpsertMedicalResourceRequest;
import static android.healthconnect.cts.utils.TestUtils.finishMigrationWithShellPermissionIdentity;
import static android.healthconnect.cts.utils.TestUtils.getRecordById;
import static android.healthconnect.cts.utils.TestUtils.insertRecords;
import static android.healthconnect.cts.utils.TestUtils.startMigrationWithShellPermissionIdentity;

import static com.android.healthfitness.flags.Flags.FLAG_DEVELOPMENT_DATABASE;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static com.google.common.truth.Correspondence.transforming;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;

import android.app.UiAutomation;
import android.content.Context;
import android.health.connect.AggregateRecordsGroupedByDurationResponse;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.DeleteMedicalResourcesRequest;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.GetMedicalDataSourcesRequest;
import android.health.connect.HealthConnectDataState;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthDataCategory;
import android.health.connect.HealthPermissions;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.MedicalResourceId;
import android.health.connect.MedicalResourceTypeInfo;
import android.health.connect.ReadMedicalResourcesRequest;
import android.health.connect.ReadMedicalResourcesResponse;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordTypeInfoResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.datatypes.BasalMetabolicRateRecord;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.HydrationRecord;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.NutritionRecord;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Power;
import android.health.connect.datatypes.units.Volume;
import android.health.connect.restore.StageRemoteDataException;
import android.healthconnect.cts.lib.TestAppProxy;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.DataFactory;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
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
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private HealthConnectManager mManager;

    @Before
    public void before() throws InterruptedException {
        deleteAllRecords();
        TestUtils.deleteAllStagedRemoteData();
        TestUtils.deleteAllMedicalData();
        mManager = TestUtils.getHealthConnectManager();
    }

    @After
    public void after() throws InterruptedException {
        deleteAllRecords();
        TestUtils.deleteAllMedicalData();
    }

    private void deleteAllRecords() throws InterruptedException {
        TestUtils.verifyDeleteRecords(
                new DeleteUsingFiltersRequest.Builder()
                        .addDataOrigin(
                                new DataOrigin.Builder().setPackageName(APP_PACKAGE_NAME).build())
                        .build());
    }

    @After
    public void tearDown() {
        TestUtils.deleteAllStagedRemoteData();
    }

    @Test
    public void testHCManagerIsAccessible_viaHCManager() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();
    }

    @Test
    public void testHCManagerIsAccessible_viaContextConstant() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();
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
        Context context = ApplicationProvider.getApplicationContext();
        assertThat(isHealthPermission(context, HealthPermissions.READ_ACTIVE_CALORIES_BURNED))
                .isTrue();
        assertThat(isHealthPermission(context, HealthPermissions.READ_ACTIVE_CALORIES_BURNED))
                .isTrue();
    }

    @Test
    public void testIsHealthPermission_forNonHealthGroupPermission_returnsFalse() {
        Context context = ApplicationProvider.getApplicationContext();
        assertThat(isHealthPermission(context, HealthPermissions.MANAGE_HEALTH_PERMISSIONS))
                .isFalse();
        assertThat(isHealthPermission(context, CAMERA)).isFalse();
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

        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch latch = new CountDownLatch(1);
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();
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

        service.updateRecords(
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

        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch latch = new CountDownLatch(1);
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();
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
        service.updateRecords(
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

        Context context = ApplicationProvider.getApplicationContext();
        CountDownLatch latch = new CountDownLatch(1);
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();
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
            service.updateRecords(
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

        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();
        TestUtils.setAutoDeletePeriod(30);
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            assertThat(service.getRecordRetentionPeriodInDays()).isEqualTo(30);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }

        TestUtils.setAutoDeletePeriod(0);
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            assertThat(service.getRecordRetentionPeriodInDays()).isEqualTo(0);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    public void testStageRemoteData_withValidInput_noExceptionsReturned() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            File dataDir = context.getDataDir();
            File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
            File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

            assertThat(testRestoreFile1.exists()).isTrue();
            assertThat(testRestoreFile2.exists()).isTrue();

            Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
            pfdsByFileName.put(
                    testRestoreFile1.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
            pfdsByFileName.put(
                    testRestoreFile2.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA");
            service.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {}
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error creating / writing to test files.", e);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);

        deleteAllStagedRemoteData();
    }

    @Test
    public void testStageRemoteData_whenNotReadMode_errorIoReturned() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, HealthConnectException>> observedExceptionsByFileName =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            File dataDir = context.getDataDir();
            File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
            File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

            assertThat(testRestoreFile1.exists()).isTrue();
            assertThat(testRestoreFile2.exists()).isTrue();

            Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
            pfdsByFileName.put(
                    testRestoreFile1.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile1, ParcelFileDescriptor.MODE_WRITE_ONLY));
            pfdsByFileName.put(
                    testRestoreFile2.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA");
            service.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void unused) {}

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {
                            observedExceptionsByFileName.set(error.getExceptionsByFileNames());
                            latch.countDown();
                        }
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error creating / writing to test files.", e);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(observedExceptionsByFileName.get())
                .comparingValuesUsing(
                        transforming(HealthConnectException::getErrorCode, "has error code"))
                .containsExactly("testRestoreFile1", HealthConnectException.ERROR_IO);

        deleteAllStagedRemoteData();
    }

    @Test
    public void testStageRemoteData_whenStagingStagedData_noExceptionsReturned() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch retryLatch = new CountDownLatch(1);
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            File dataDir = context.getDataDir();
            File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
            File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

            assertThat(testRestoreFile1.exists()).isTrue();
            assertThat(testRestoreFile2.exists()).isTrue();

            Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
            pfdsByFileName.put(
                    testRestoreFile1.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
            pfdsByFileName.put(
                    testRestoreFile2.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA");
            service.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {}
                    });
            assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);

            // send the files again
            service.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {
                            retryLatch.countDown();
                        }

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {}
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error creating / writing to test files.", e);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        assertThat(retryLatch.await(10, TimeUnit.SECONDS)).isEqualTo(true);

        deleteAllStagedRemoteData();
    }

    @Test
    public void testStageRemoteData_withoutPermission_errorSecurityReturned() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Map<String, HealthConnectException>> observedExceptionsByFileName =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            File dataDir = context.getDataDir();
            File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
            File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

            assertThat(testRestoreFile1.exists()).isTrue();
            assertThat(testRestoreFile2.exists()).isTrue();

            Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
            pfdsByFileName.put(
                    testRestoreFile1.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile1, ParcelFileDescriptor.MODE_WRITE_ONLY));
            pfdsByFileName.put(
                    testRestoreFile2.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

            service.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void unused) {}

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {
                            observedExceptionsByFileName.set(error.getExceptionsByFileNames());
                            latch.countDown();
                        }
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error creating / writing to test files.", e);
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(observedExceptionsByFileName.get())
                .comparingValuesUsing(
                        transforming(HealthConnectException::getErrorCode, "has error code"))
                .containsExactly("", HealthConnectException.ERROR_SECURITY);

        deleteAllStagedRemoteData();
    }

    @Test
    public void testUpdateDataDownloadState_withoutPermission_throwsSecurityException() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            service.updateDataDownloadState(DATA_DOWNLOAD_STARTED);
        } catch (SecurityException e) {
            /* pass */
        }
    }

    @Test
    public void testGetHealthConnectDataState_beforeDownload_returnsIdleState() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreState())
                .isEqualTo(RESTORE_STATE_IDLE);
        deleteAllStagedRemoteData();
    }

    @Test
    public void
            testGetHealthConnectDataState_beforeDownload_withMigrationPermission_returnsIdleState()
                    throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(MIGRATE_HEALTH_CONNECT_DATA);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreState())
                .isEqualTo(RESTORE_STATE_IDLE);
        deleteAllStagedRemoteData();
    }

    @Test
    public void testGetHealthConnectDataState_duringDownload_returnsRestorePendingState()
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA",
                            MANAGE_HEALTH_DATA);
            service.updateDataDownloadState(DATA_DOWNLOAD_STARTED);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreState())
                .isEqualTo(RESTORE_STATE_PENDING);
        deleteAllStagedRemoteData();
    }

    @Test
    public void testGetHealthConnectDataState_whenDownloadDone_returnsRestorePendingState()
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA",
                            MANAGE_HEALTH_DATA);
            service.updateDataDownloadState(DATA_DOWNLOAD_COMPLETE);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreState())
                .isEqualTo(RESTORE_STATE_PENDING);
        deleteAllStagedRemoteData();
    }

    @Test
    public void testGetHealthConnectDataState_whenDownloadFailed_returnsIdleState()
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA",
                            MANAGE_HEALTH_DATA);
            service.updateDataDownloadState(DATA_DOWNLOAD_FAILED);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreState())
                .isEqualTo(RESTORE_STATE_IDLE);

        deleteAllStagedRemoteData();
    }

    @Test
    public void testGetHealthConnectDataState_afterStagingAndMerge_returnsStateIdle()
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch stateLatch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            File dataDir = context.getDataDir();
            File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
            File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

            assertThat(testRestoreFile1.exists()).isTrue();
            assertThat(testRestoreFile2.exists()).isTrue();

            Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
            pfdsByFileName.put(
                    testRestoreFile1.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
            pfdsByFileName.put(
                    testRestoreFile2.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA",
                            MANAGE_HEALTH_DATA);
            service.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {}
                    });
            assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            stateLatch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error creating / writing to test files.", e);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        assertThat(stateLatch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreState())
                .isEqualTo(RESTORE_STATE_IDLE);

        deleteAllStagedRemoteData();
    }

    @Test
    public void testGetDataRestoreError_onErrorDuringStaging_returnsErrorFetching()
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch stateLatch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            File dataDir = context.getDataDir();
            File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
            File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

            assertThat(testRestoreFile1.exists()).isTrue();
            assertThat(testRestoreFile2.exists()).isTrue();

            Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
            pfdsByFileName.put(
                    testRestoreFile1.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile1, ParcelFileDescriptor.MODE_WRITE_ONLY));
            pfdsByFileName.put(
                    testRestoreFile2.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA",
                            MANAGE_HEALTH_DATA);
            service.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {}

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {
                            latch.countDown();
                        }
                    });
            assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            stateLatch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error creating / writing to test files.", e);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        assertThat(stateLatch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreError())
                .isEqualTo(RESTORE_ERROR_FETCHING_DATA);
        deleteAllStagedRemoteData();
    }

    @Test
    public void testGetDataRestoreError_onDownloadFailed_returnsErrorFetching() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA",
                            MANAGE_HEALTH_DATA);
            service.updateDataDownloadState(DATA_DOWNLOAD_FAILED);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreError())
                .isEqualTo(RESTORE_ERROR_FETCHING_DATA);
        deleteAllStagedRemoteData();
    }

    @Test
    public void testGetDataRestoreError_onNoErrorDuringRestore_returnsNoError() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch stateLatch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            File dataDir = context.getDataDir();
            File testRestoreFile1 = createAndGetNonEmptyFile(dataDir, "testRestoreFile1");
            File testRestoreFile2 = createAndGetNonEmptyFile(dataDir, "testRestoreFile2");

            assertThat(testRestoreFile1.exists()).isTrue();
            assertThat(testRestoreFile2.exists()).isTrue();

            Map<String, ParcelFileDescriptor> pfdsByFileName = new ArrayMap<>();
            pfdsByFileName.put(
                    testRestoreFile1.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile1, ParcelFileDescriptor.MODE_READ_ONLY));
            pfdsByFileName.put(
                    testRestoreFile2.getName(),
                    ParcelFileDescriptor.open(
                            testRestoreFile2, ParcelFileDescriptor.MODE_READ_ONLY));

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.STAGE_HEALTH_CONNECT_REMOTE_DATA",
                            MANAGE_HEALTH_DATA);
            service.stageAllHealthConnectRemoteData(
                    pfdsByFileName,
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Void result) {
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull StageRemoteDataException error) {}
                    });
            assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            stateLatch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } catch (IOException e) {
            Log.e(TAG, "Error creating / writing to test files.", e);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        assertThat(stateLatch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataRestoreError())
                .isEqualTo(RESTORE_ERROR_NONE);
        deleteAllStagedRemoteData();
    }

    @Test
    public void testDataMigrationState_byDefault_returnsIdleState() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectDataState> returnedHealthConnectDataState =
                new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {
                            returnedHealthConnectDataState.set(healthConnectDataState);
                            latch.countDown();
                        }

                        @Override
                        public void onError(@NonNull HealthConnectException e) {}
                    });
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedHealthConnectDataState.get().getDataMigrationState())
                .isEqualTo(MIGRATION_STATE_IDLE);
        deleteAllStagedRemoteData();
    }

    @Test
    public void testGetHealthConnectDataState_withoutPermission_returnsSecurityException()
            throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HealthConnectException> returnedException = new AtomicReference<>();
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
        assertThat(service).isNotNull();

        try {
            service.getHealthConnectDataState(
                    Executors.newSingleThreadExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(HealthConnectDataState healthConnectDataState) {}

                        @Override
                        public void onError(@NonNull HealthConnectException e) {
                            returnedException.set(e);
                            latch.countDown();
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThat(latch.await(10, TimeUnit.SECONDS)).isEqualTo(true);
        assertThat(returnedException.get().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_SECURITY);
        deleteAllStagedRemoteData();
    }

    @Test
    public void testDataApis_migrationInProgress_apisBlocked() throws InterruptedException {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
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

        try {
            uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
            TestUtils.getApplicationInfo();
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
            uiAutomation.dropShellPermissionIdentity();
        }

        try {
            uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

            TestUtils.queryAccessLogs();
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
            uiAutomation.dropShellPermissionIdentity();
        }

        try {
            uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

            TestUtils.setAutoDeletePeriod(1);
            Assert.fail();
        } catch (HealthConnectException exception) {
            assertThat(exception).isNotNull();
            assertThat(exception.getErrorCode())
                    .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);
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
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testCreateMedicalDataSource_migrationInProgress_apiBlocked()
            throws InterruptedException {
        startMigrationWithShellPermissionIdentity();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();

        mManager.createMedicalDataSource(getCreateMedicalDataSourceRequest(), executor, receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);

        finishMigrationWithShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testReadMedicalResourcesByRequest_migrationInProgress_apiBlocked()
            throws InterruptedException {
        startMigrationWithShellPermissionIdentity();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();

        mManager.readMedicalResources(request, executor, receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);

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

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testCreateMedicalDataSource_succeeds() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();
        CreateMedicalDataSourceRequest request = getCreateMedicalDataSourceRequest();

        mManager.createMedicalDataSource(request, executor, receiver);

        MedicalDataSource responseDataSource = receiver.getResponse();
        assertThat(responseDataSource).isInstanceOf(MedicalDataSource.class);
        assertThat(responseDataSource.getId()).isNotEmpty();
        assertThat(responseDataSource.getFhirBaseUri()).isEqualTo(request.getFhirBaseUri());
        assertThat(responseDataSource.getDisplayName()).isEqualTo(request.getDisplayName());
        assertThat(responseDataSource.getPackageName()).isEqualTo(APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testCreateMedicalDataSource_hasDataManagementPermission_throws()
            throws InterruptedException {
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();

        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.createMedicalDataSource(
                                getCreateMedicalDataSourceRequest(),
                                Executors.newSingleThreadExecutor(),
                                receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalDataSources_emptyIds_returnsEmptyList() throws InterruptedException {
        HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();

        mManager.getMedicalDataSources(List.of(), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalDataSourcesById_notPresent_returnsEmptyList() throws Exception {
        HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();
        List<String> ids = List.of("foo");

        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.getMedicalDataSources(
                                ids, Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalDataSourcesById_onePresent_returnsIt() throws Exception {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                getCreateMedicalDataSourceRequest(),
                Executors.newSingleThreadExecutor(),
                createReceiver);
        MedicalDataSource dataSource = createReceiver.getResponse();
        HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();

        mManager.getMedicalDataSources(
                List.of(dataSource.getId()), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).containsExactly(dataSource);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalDataSourcesByRequest_nothingPresent_returnsEmpty() throws Exception {
        HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();

        mManager.getMedicalDataSources(request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetMedicalDataSourcesByRequest_onePresent_returnsIt() throws Exception {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                getCreateMedicalDataSourceRequest(),
                Executors.newSingleThreadExecutor(),
                createReceiver);
        MedicalDataSource dataSource = createReceiver.getResponse();
        HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();

        mManager.getMedicalDataSources(request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).containsExactly(dataSource);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testHealthConnectManager_deleteMedicalDataSourceDoesntExist_fails()
            throws Exception {
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(
                "foo", Executors.newSingleThreadExecutor(), callback);

        assertThat(callback.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testHealthConnectManager_deleteMedicalDataSourceExists_succeedsAndDeletes()
            throws Exception {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                getCreateMedicalDataSourceRequest(),
                Executors.newSingleThreadExecutor(),
                createReceiver);
        MedicalDataSource dataSource = createReceiver.getResponse();
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(
                dataSource.getId(), Executors.newSingleThreadExecutor(), callback);

        assertThat(callback.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testHealthConnectManager_deleteMedicalDataSourceExistsWithData_succeedsAndDeletes()
            throws Exception {
        // Create the datasource
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        mManager.createMedicalDataSource(
                getCreateMedicalDataSourceRequest(), executor, createReceiver);
        MedicalDataSource dataSource = createReceiver.getResponse();
        MedicalResource resource = upsertMedicalData(dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(dataSource.getId(), executor, callback);

        assertThat(callback.getResponse()).isNull();
        // Check for existence of data
        HealthConnectReceiver<List<MedicalResource>> readResourceReceiver =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(List.of(resource.getId()), executor, readResourceReceiver);
        assertThat(readResourceReceiver.getResponse()).isEmpty();
        // Check for existence of datasource by trying to create again.
        // TODO: b/350010046 - switch to using read when it is implemented.
        HealthConnectReceiver<MedicalDataSource> secondCreateReceiver =
                new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                getCreateMedicalDataSourceRequest(), executor, secondCreateReceiver);
        secondCreateReceiver.verifyNoExceptionOrThrow();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testHealthConnectManager_deleteMedicalDataSourceDifferentPackage_denied()
            throws Exception {
        // Create the datasource
        MedicalDataSource dataSource =
                TestAppProxy.APP_WRITE_PERMS_ONLY.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest());
        MedicalResource resource =
                TestAppProxy.APP_WRITE_PERMS_ONLY.upsertMedicalResource(
                        dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(dataSource.getId(), executor, callback);

        assertThat(callback.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);

        // Check for existence of the medicalResource and the dataSource.
        HealthConnectReceiver<List<MedicalResource>> readResourceReceiver =
                new HealthConnectReceiver<>();
        HealthConnectReceiver<List<MedicalDataSource>> getDataSourceReceiver =
                new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mManager.readMedicalResources(
                            List.of(resource.getId()), executor, readResourceReceiver);
                    mManager.getMedicalDataSources(
                            List.of(dataSource.getId()), executor, getDataSourceReceiver);
                },
                MANAGE_HEALTH_DATA);
        assertThat(readResourceReceiver.getResponse()).containsExactly(resource);
        assertThat(getDataSourceReceiver.getResponse()).containsExactly(dataSource);
    }

    // TODO(b/343923754): Add more upsert/readMedicalResources tests once deleteAll can be called.
    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_emptyList_returnsEmptyList()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();

        mManager.upsertMedicalResources(List.of(), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_invalidJson_throws() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_missingResourceId_throws() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_emptyResourceId_throws() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, FHIR_DATA_IMMUNIZATION_ID_EMPTY)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_missingResourceType_throws()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_unsupportedResourceType_throws()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                FHIR_DATA_IMMUNIZATION_UNSUPPORTED_RESOURCE_TYPE)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_unsupportedVersion_throws() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_UNSUPPORTED, FHIR_DATA_IMMUNIZATION)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testUpsertMedicalResources_hasDataManagementPermission_throws()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();

        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.upsertMedicalResources(
                                List.of(getUpsertMedicalResourceRequest()),
                                Executors.newSingleThreadExecutor(),
                                receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testReadMedicalResources_emptyIds_returnsEmptyList() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();

        mManager.readMedicalResources(List.of(), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testReadMedicalResources_byIds_exceedsMaxPageSize_throws() {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        List<MedicalResourceId> ids = new ArrayList<>(MAXIMUM_PAGE_SIZE + 1);
        for (int i = 0; i < MAXIMUM_PAGE_SIZE + 1; i++) {
            ids.add(
                    new MedicalResourceId(
                            Integer.toString(i),
                            FHIR_RESOURCE_TYPE_IMMUNIZATION,
                            FHIR_RESOURCE_ID_IMMUNIZATION));
        }

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mManager.readMedicalResources(
                                ids, Executors.newSingleThreadExecutor(), receiver));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testReadMedicalResources_byIds_noData_returnsEmptyList()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());

        mManager.readMedicalResources(ids, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testReadMedicalResources_byRequest_noData_returnsEmptyList()
            throws InterruptedException {
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();

        mManager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
        assertThat(receiver.getResponse().getMedicalResources()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testReadMedicalResources_byRequest_filtersByMedicalResourceType()
            throws InterruptedException {
        // Create two data sources.
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert two Immunizations and one Allergy.
        MedicalResource immunization1 =
                upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource immunization2 =
                upsertMedicalData(dataSource2.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        upsertMedicalData(dataSource1.getId(), FHIR_DATA_ALLERGY);
        // Read all Immunizations.
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesRequest allImmunizationsRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();

        mManager.readMedicalResources(
                allImmunizationsRequest, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse().getMedicalResources())
                .containsExactly(immunization1, immunization2);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testReadMedicalResources_byRequest_filtersByMedicalResourceTypeAndOneDataSource()
            throws InterruptedException {
        // Create two data sources.
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert two Immunizations and one Allergy.
        MedicalResource immunizationFromDataSource1 =
                upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        upsertMedicalData(dataSource2.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        upsertMedicalData(dataSource1.getId(), FHIR_DATA_ALLERGY);
        // Read Immunizations only from data source 1.
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesRequest immunizationsFromDataSource1Request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .addDataSourceId(dataSource1.getId())
                        .build();

        mManager.readMedicalResources(
                immunizationsFromDataSource1Request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse().getMedicalResources())
                .containsExactly(immunizationFromDataSource1);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testReadMedicalResources_byRequest_filtersByMedicalResourceTypeAndBothDataSources()
            throws InterruptedException {
        // Create two data sources.
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert two Immunizations and one Allergy.
        MedicalResource immunizationFromDataSource1 =
                upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource immunizationFromDataSource2 =
                upsertMedicalData(dataSource2.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        upsertMedicalData(dataSource1.getId(), FHIR_DATA_ALLERGY);
        // Read Immunizations only from both data sources.
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesRequest immunizationsFromBothDataSourcesRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .addDataSourceId(dataSource1.getId())
                        .addDataSourceId(dataSource2.getId())
                        .build();

        mManager.readMedicalResources(
                immunizationsFromBothDataSourcesRequest,
                Executors.newSingleThreadExecutor(),
                receiver);

        assertThat(receiver.getResponse().getMedicalResources())
                .containsExactly(immunizationFromDataSource1, immunizationFromDataSource2);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResources_byIdsNonExistent_succeeds() throws InterruptedException {
        MedicalDataSource dataSource = createDataSource(getCreateMedicalDataSourceRequest());
        List<MedicalResourceId> ids = new ArrayList<>(MAXIMUM_PAGE_SIZE + 1);
        for (int i = 0; i < MAXIMUM_PAGE_SIZE + 1; i++) {
            ids.add(
                    new MedicalResourceId(
                            dataSource.getId(),
                            FHIR_RESOURCE_TYPE_IMMUNIZATION,
                            FHIR_RESOURCE_ID_IMMUNIZATION + "." + i));
        }
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();

        mManager.deleteMedicalResources(ids, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResourcesByIds_managementPermissionNoData_succeeds()
            throws InterruptedException {
        MedicalDataSource dataSource = createDataSource(getCreateMedicalDataSourceRequest());
        List<MedicalResourceId> ids = new ArrayList<>(MAXIMUM_PAGE_SIZE + 1);
        for (int i = 0; i < MAXIMUM_PAGE_SIZE + 1; i++) {
            ids.add(
                    new MedicalResourceId(
                            dataSource.getId(),
                            FHIR_RESOURCE_TYPE_IMMUNIZATION,
                            FHIR_RESOURCE_ID_IMMUNIZATION + "." + i));
        }
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();

        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                ids, Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        receiver.verifyNoExceptionOrThrow();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResourcesByIds_anIdMissing_succeeds() throws InterruptedException {
        MedicalDataSource dataSource = createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();
        MedicalResourceId id =
                new MedicalResourceId(
                        dataSource.getId(),
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);

        mManager.deleteMedicalResources(List.of(id), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResourcesByIds_emptyIds_succeeds() throws InterruptedException {
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();

        mManager.deleteMedicalResources(List.of(), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResourcesByIds_managementPermissionAMissingId_succeeds()
            throws InterruptedException {
        MedicalDataSource dataSource = createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();
        MedicalResourceId id =
                new MedicalResourceId(
                        dataSource.getId(),
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);

        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                List.of(id), Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        receiver.verifyNoExceptionOrThrow();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResourcesByIds_managementPermissionEmptyIds_succeeds()
            throws InterruptedException {
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();

        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                List.of(), Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testDeleteMedicalResourcesByIds_managementPermissionCreate2Delete1_succeeds()
            throws InterruptedException {
        MedicalDataSource dataSource = createDataSource(getCreateMedicalDataSourceRequest());
        // Insert some data
        MedicalResource resource1 = upsertMedicalData(dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource resource2 =
                upsertMedicalData(dataSource.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                List.of(resource1.getId()),
                                Executors.newSingleThreadExecutor(),
                                callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.getResponse()).isNull();
        // Test resource2 is still present
        HealthConnectReceiver<ReadMedicalResourcesResponse> readReceiver =
                new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.readMedicalResources(
                                new ReadMedicalResourcesRequest.Builder(
                                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                                        .build(),
                                Executors.newSingleThreadExecutor(),
                                readReceiver));
        assertThat(readReceiver.getResponse().getMedicalResources()).containsExactly(resource2);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testDeleteMedicalResourcesByRequest_managementPermissionCreate2Delete1_succeeds()
            throws InterruptedException {
        // Create the datasource
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert some data
        MedicalResource resource1 = upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource resource2 = upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);

        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(dataSource1.getId())
                        .build();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                request, Executors.newSingleThreadExecutor(), callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.getResponse()).isNull();
        // Test resource2 is still present
        HealthConnectReceiver<List<MedicalResource>> readReceiver2 = new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.readMedicalResources(
                                List.of(resource1.getId(), resource2.getId()),
                                Executors.newSingleThreadExecutor(),
                                readReceiver2));
        assertThat(readReceiver2.getResponse()).containsExactly(resource2);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testDeleteMedicalResourcesByRequest_badDataSourceId_doesntDeleteAll()
            throws InterruptedException {
        // Create the datasource
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        // Insert some data
        MedicalResource resource1 = upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);

        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId("illegal id").build();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                request, Executors.newSingleThreadExecutor(), callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
        // Test resource is still present
        HealthConnectReceiver<List<MedicalResource>> readReceiver2 = new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.readMedicalResources(
                                List.of(resource1.getId()),
                                Executors.newSingleThreadExecutor(),
                                readReceiver2));
        assertThat(readReceiver2.getResponse()).containsExactly(resource1);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testDeleteMedicalResourcesByRequest_resourceTypesMatch_succeeds()
            throws InterruptedException {
        // Create the datasource
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert some data
        MedicalResource imm1 = upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource imm2 = upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);

        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .build();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                request, Executors.newSingleThreadExecutor(), callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.getResponse()).isNull();
        // Test resource2 is still present
        HealthConnectReceiver<List<MedicalResource>> readReceiver2 = new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.readMedicalResources(
                                List.of(imm1.getId(), imm2.getId()),
                                Executors.newSingleThreadExecutor(),
                                readReceiver2));
        assertThat(readReceiver2.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testDeleteMedicalResourcesByRequest_resourceTypesMismatch_succeeds()
            throws InterruptedException {
        // Create the datasource
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert some data
        MedicalResource imm1 = upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource imm2 = upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);

        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATION + 1)
                        .build();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                request, Executors.newSingleThreadExecutor(), callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.getResponse()).isNull();
        // Test resource2 is still present
        HealthConnectReceiver<List<MedicalResource>> readReceiver2 = new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.readMedicalResources(
                                List.of(imm1.getId(), imm2.getId()),
                                Executors.newSingleThreadExecutor(),
                                readReceiver2));
        assertThat(readReceiver2.getResponse()).containsExactly(imm1, imm2);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResources_byRequestNothingPresent_succeeds() throws Exception {
        // Insert a data source to ensure we have an appInfoId.
        createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(UUID.randomUUID().toString())
                        .build();

        mManager.deleteMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testQueryAllMedicalResourceTypeInfos_succeeds() throws InterruptedException {
        // Create some data sources with data: ds1 contains [immunization, differentImmunization,
        // allergy], ds2 contains [immunization], and ds3 contains [allergy].
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        MedicalDataSource dataSource3 = createDataSource(getCreateMedicalDataSourceRequest("3"));
        upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        upsertMedicalData(dataSource1.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        upsertMedicalData(dataSource1.getId(), FHIR_DATA_ALLERGY);
        upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);
        upsertMedicalData(dataSource3.getId(), FHIR_DATA_ALLERGY);

        HealthConnectReceiver<List<MedicalResourceTypeInfo>> receiver =
                new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.queryAllMedicalResourceTypeInfos(
                                Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.getResponse())
                .containsExactly(
                        new MedicalResourceTypeInfo(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                Set.of(dataSource1, dataSource2)),
                        new MedicalResourceTypeInfo(
                                MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE,
                                Set.of(dataSource1, dataSource3)));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testQueryAllMedicalResourceTypeInfos_noDataSources_succeeds()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResourceTypeInfo>> receiver =
                new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.queryAllMedicalResourceTypeInfos(
                                Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.getResponse())
                .containsExactly(
                        new MedicalResourceTypeInfo(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, Set.of()),
                        new MedicalResourceTypeInfo(
                                MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE, Set.of()));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testQueryAllMedicalResourceTypeInfos_noMedicalResources_succeeds()
            throws InterruptedException {
        createDataSource(getCreateMedicalDataSourceRequest("1"));

        HealthConnectReceiver<List<MedicalResourceTypeInfo>> receiver =
                new HealthConnectReceiver<>();
        SystemUtil.runWithShellPermissionIdentity(
                () ->
                        mManager.queryAllMedicalResourceTypeInfos(
                                Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.getResponse())
                .containsExactly(
                        new MedicalResourceTypeInfo(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, Set.of()),
                        new MedicalResourceTypeInfo(
                                MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE, Set.of()));
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

    private MedicalDataSource createDataSource(CreateMedicalDataSourceRequest createRequest)
            throws InterruptedException {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                createRequest, Executors.newSingleThreadExecutor(), createReceiver);
        return createReceiver.getResponse();
    }

    private MedicalResource upsertMedicalData(String dataSourceId, String data)
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> dataReceiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(dataSourceId, FHIR_VERSION_R4, data)
                        .build();
        mManager.upsertMedicalResources(
                List.of(request), Executors.newSingleThreadExecutor(), dataReceiver);
        // Make sure something got inserted.
        return Iterables.getOnlyElement(dataReceiver.getResponse());
    }

    private static void deleteAllStagedRemoteData()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Context context = ApplicationProvider.getApplicationContext();
            HealthConnectManager service = context.getSystemService(HealthConnectManager.class);
            assertThat(service).isNotNull();

            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .adoptShellPermissionIdentity(
                            "android.permission.DELETE_STAGED_HEALTH_CONNECT_REMOTE_DATA");
            // TODO(b/241542162): Avoid using reflection as a workaround once test apis can be
            //  run in CTS tests.
            service.getClass().getMethod("deleteAllStagedRemoteData").invoke(service);
        } finally {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .dropShellPermissionIdentity();
        }
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
