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

package android.healthconnect.cts.ratelimiter;

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.health.connect.datatypes.StepsRecord.STEPS_COUNT_TOTAL;
import static android.healthconnect.cts.utils.DataFactory.buildDevice;
import static android.healthconnect.cts.utils.DataFactory.getCompleteStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getUpdatedStepsRecord;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.getUpsertMedicalResourceRequest;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.DeleteMedicalResourcesRequest;
import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.MedicalResourceId;
import android.health.connect.ReadMedicalResourcesInitialRequest;
import android.health.connect.ReadMedicalResourcesRequest;
import android.health.connect.ReadMedicalResourcesResponse;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogTokenResponse;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.Device;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.PhrDataFactory;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class RateLimiterTest {
    private static final String TAG = "RateLimiterTest";
    private static final int MAX_FOREGROUND_WRITE_CALL_15M = 1000;
    private static final int MAX_FOREGROUND_READ_CALL_15M = 2000;
    private static final Duration WINDOW_15M = Duration.ofMinutes(15);
    public static final String ENABLE_RATE_LIMITER_FLAG = "enable_rate_limiter";
    private final UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private int mLimitsAdjustmentForTesting = 1;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
        if (TestUtils.setLowerRateLimitsForTesting(true)) {
            mLimitsAdjustmentForTesting = 10;
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        TestUtils.deleteAllStagedRemoteData();
        TestUtils.setLowerRateLimitsForTesting(false);
    }

    @Test
    public void testTryAcquireApiCallQuota_writeCallsInLimit() throws InterruptedException {
        tryAcquireCallQuotaNTimesForWrite(MAX_FOREGROUND_WRITE_CALL_15M);
    }

    @Test
    public void testTryAcquireApiCallQuota_readCallsInLimit() throws InterruptedException {
        List<Record> testRecord = List.of(getCompleteStepsRecord());

        tryAcquireCallQuotaNTimesForRead(
                testRecord, TestUtils.insertRecords(testRecord), MAX_FOREGROUND_READ_CALL_15M);
    }

    @Test
    @ApiTest(apis = {"android.health.connect#insertRecords"})
    public void testTryAcquireApiCallQuota_insertRecords_writeLimitExceeded() {
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class, () -> exceedWriteQuotaWithInsertRecords());
        assertThat(exception).hasMessageThat().contains("API call quota exceeded");
    }

    @Test
    @ApiTest(apis = {"android.health.connect#createMedicalDataSource"})
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testTryAcquireApiCallQuota_createMedicalDataSource_writeLimitExceeded() {
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        this::exceedWriteQuotaWithCreateMedicalDataSource);
        assertThat(exception).hasMessageThat().contains("API call quota exceeded");
    }

    @Test
    @ApiTest(apis = {"android.health.connect#deleteMedicalResource"})
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testTryAcquireApiCallQuota_deleteMedicalResourcesById_writeLimitExceeded() {
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        this::exceedWriteQuotaWithDeleteMedicalDataSourceByIds);
        assertThat(exception).hasMessageThat().contains("API call quota exceeded");
    }

    @Test
    @ApiTest(apis = {"android.health.connect#deleteMedicalResource"})
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testTryAcquireApiCallQuota_deleteMedicalResourcesByRequest_writeLimitExceeded() {
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        this::exceedWriteQuotaWithDeleteMedicalDataSourceByRequest);
        assertThat(exception).hasMessageThat().contains("API call quota exceeded");
    }

    @Test
    @ApiTest(apis = {"android.health.connect#upsertMedicalResources"})
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testTryAcquireApiCallQuota_upsertMedicalResources_writeLimitExceeded_throws() {
        HealthConnectException thrown =
                assertThrows(
                        HealthConnectException.class,
                        this::exceedWriteQuotaWithUpsertMedicalResources);
        assertThat(thrown.getMessage()).contains("API call quota exceeded");
    }

    // TODO(b/370731291): Add rate limit tests for MemoryRollingQuota, and
    // calling from background.

    @Test
    @ApiTest(apis = {"android.health.connect#readMedicalResourcesByIds"})
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByIds_readLimitExceeded_throws()
            throws InterruptedException {
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        MedicalResource immunization = upsertMedicalData(manager, FHIR_DATA_IMMUNIZATION);
        // Make the maximum number of calls allowed by quota
        int maximumCalls = MAX_FOREGROUND_READ_CALL_15M / mLimitsAdjustmentForTesting;
        for (int i = 0; i < maximumCalls; i++) {
            HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
            manager.readMedicalResources(
                    List.of(immunization.getId()), Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
        }

        // Make 1 extra call and check quota is exceeded
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        manager.readMedicalResources(
                List.of(immunization.getId()), Executors.newSingleThreadExecutor(), receiver);

        HealthConnectException exception = receiver.assertAndGetException();
        assertThat(exception.getMessage()).contains("API call quota exceeded");
    }

    @Test
    @ApiTest(apis = {"android.health.connect#readMedicalResourcesByRequest"})
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_readLimitExceeded_throws()
            throws InterruptedException {
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        upsertMedicalData(manager, FHIR_DATA_IMMUNIZATION);
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        // Make the maximum number of calls allowed by quota
        int maximumCalls = MAX_FOREGROUND_READ_CALL_15M / mLimitsAdjustmentForTesting;
        for (int i = 0; i < maximumCalls; i++) {
            HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                    new HealthConnectReceiver<>();
            manager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
        }

        // Make 1 extra call and check quota is exceeded
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        manager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);

        HealthConnectException exception = receiver.assertAndGetException();
        assertThat(exception.getMessage()).contains("API call quota exceeded");
    }

    @Test
    @ApiTest(apis = {"android.health.connect#readRecords"})
    public void testTryAcquireApiCallQuota_readLimitExceeded() throws InterruptedException {
        HealthConnectException exception =
                assertThrows(HealthConnectException.class, this::exceedReadQuota);
        assertThat(exception).hasMessageThat().contains("API call quota exceeded");
    }

    @Test
    public void testRecordsChunkSizeLimitExceeded() throws InterruptedException {
        HealthConnectException exception =
                assertThrows(HealthConnectException.class, this::exceedChunkMemoryQuota);
        assertThat(exception)
                .hasMessageThat()
                .contains("Records chunk size exceeded the max chunk limit");
    }

    @Test
    public void testRecordSizeLimitExceeded() throws InterruptedException {
        HealthConnectException exception =
                assertThrows(HealthConnectException.class, this::exceedRecordMemoryQuota);
        assertThat(exception)
                .hasMessageThat()
                .contains("Record size exceeded the single record size limit");
    }

    @Test
    @ApiTest(apis = {"android.health.connect#insertRecords"})
    public void testRecordMemoryRollingQuota_foregroundCall_exceedBackgroundLimit()
            throws InterruptedException {
        // No exception expected.
        exceedRecordMemoryRollingQuotaBackgroundLimit();
    }

    // TODO(b/346256048): re-evaluate the "magic number"s in the tests below once we turn on the PHR
    // flags on pre/post submit.
    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testMedicalResourcesChunkSizeLimitExceeded() throws InterruptedException {
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        int nCopies = 1000 / mLimitsAdjustmentForTesting;
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, "UpsertMedicalResourceRequest")
                        .build();
        List<UpsertMedicalResourceRequest> requests = Collections.nCopies(nCopies, request);

        manager.upsertMedicalResources(requests, Executors.newSingleThreadExecutor(), receiver);

        HealthConnectException thrown = receiver.assertAndGetException();
        assertThat(thrown.getMessage()).contains("Records chunk size exceeded the max chunk limit");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testMedicalResourceSizeLimitExceeded() throws InterruptedException {
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        int nCharacters = 200000 / mLimitsAdjustmentForTesting;
        List<Character> data = Collections.nCopies(nCharacters, '0');
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, data.toString())
                        .build();

        manager.upsertMedicalResources(
                List.of(request), Executors.newSingleThreadExecutor(), receiver);

        HealthConnectException thrown = receiver.assertAndGetException();
        assertThat(thrown.getMessage())
                .contains("Record size exceeded the single record size limit");
    }

    private void exceedChunkMemoryQuota() throws InterruptedException {
        List<Record> testRecord = Collections.nCopies(30000, getCompleteStepsRecord());

        TestUtils.insertRecords(testRecord);
    }

    private void exceedRecordMemoryQuota() throws InterruptedException {
        Device device = buildDevice();
        DataOrigin dataOrigin =
                new DataOrigin.Builder().setPackageName("android.healthconnect.cts").build();
        Metadata.Builder testMetadataBuilder = new Metadata.Builder();
        testMetadataBuilder.setDevice(device).setDataOrigin(dataOrigin);
        testMetadataBuilder.setClientRecordId("HRR" + Math.random());
        testMetadataBuilder.setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED);

        HeartRateRecord.HeartRateSample heartRateRecord =
                new HeartRateRecord.HeartRateSample(10, Instant.now().plusMillis(100));
        int nCopies = 85000 / mLimitsAdjustmentForTesting;
        ArrayList<HeartRateRecord.HeartRateSample> heartRateRecords =
                new ArrayList<>(Collections.nCopies(nCopies, heartRateRecord));

        HeartRateRecord testHeartRateRecord =
                new HeartRateRecord.Builder(
                                testMetadataBuilder.build(),
                                Instant.now(),
                                Instant.now().plusMillis(500),
                                heartRateRecords)
                        .build();
        TestUtils.insertRecords(List.of(testHeartRateRecord));
    }

    private void exceedRecordMemoryRollingQuotaBackgroundLimit() throws InterruptedException {
        List<Record> testRecord = Collections.nCopies(350, getCompleteStepsRecord());
        int nTimes = 1000 / mLimitsAdjustmentForTesting;
        for (int i = 0; i < nTimes; i++) {
            TestUtils.insertRecords(testRecord);
        }
    }

    private void exceedWriteQuotaWithInsertRecords() throws InterruptedException {
        float quotaAcquired = acquireCallQuotaForWrite();

        List<Record> testRecord = List.of(getCompleteStepsRecord());

        while (quotaAcquired > 1) {
            TestUtils.insertRecords(testRecord);
            quotaAcquired--;
        }
        int tryWriteWithBuffer = 20;
        while (tryWriteWithBuffer > 0) {
            TestUtils.insertRecords(List.of(getCompleteStepsRecord()));

            tryWriteWithBuffer--;
        }
    }

    private void exceedWriteQuotaWithCreateMedicalDataSource() throws InterruptedException {
        float quotaAcquired = acquireCallQuotaForWrite();
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        int count = 0;

        while (quotaAcquired > 1) {
            count++;
            // Append request count to the fhir base uri to avoid duplicates.
            CreateMedicalDataSourceRequest request =
                    PhrDataFactory.getCreateMedicalDataSourceRequest(String.valueOf(count));
            manager.createMedicalDataSource(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
            quotaAcquired--;
        }
        int tryWriteWithBuffer = 20;
        while (tryWriteWithBuffer > 0) {
            count++;
            CreateMedicalDataSourceRequest request =
                    PhrDataFactory.getCreateMedicalDataSourceRequest(String.valueOf(count));
            manager.createMedicalDataSource(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
            tryWriteWithBuffer--;
        }
    }

    private void exceedWriteQuotaWithDeleteMedicalDataSourceByIds() throws InterruptedException {
        float quotaAcquired = acquireCallQuotaForWrite();
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        // We will try to delete an id that doesn't exist. This doesn't matter.
        List<MedicalResourceId> request = List.of(PhrDataFactory.getMedicalResourceId());

        while (quotaAcquired > 1) {
            // Using a non-existent id is fine for quota check.
            manager.deleteMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
            quotaAcquired--;
        }
        int tryWriteWithBuffer = 20;
        while (tryWriteWithBuffer > 0) {
            // Using a non-existent id is fine for quota check.
            manager.deleteMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
            tryWriteWithBuffer--;
        }
    }

    private void exceedWriteQuotaWithDeleteMedicalDataSourceByRequest()
            throws InterruptedException {
        float quotaAcquired = acquireCallQuotaForWrite();
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        // We will try to delete a data source that doesn't exist. This doesn't matter for quota
        // check.
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(UUID.randomUUID().toString())
                        .build();

        while (quotaAcquired > 1) {
            // Using a non-existent id is fine for quota check.
            manager.deleteMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
            quotaAcquired--;
        }
        int tryWriteWithBuffer = 20;
        while (tryWriteWithBuffer > 0) {
            // Using a non-existent id is fine for quota check.
            manager.deleteMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
            tryWriteWithBuffer--;
        }
    }

    private void exceedWriteQuotaWithUpsertMedicalResources() throws InterruptedException {
        HealthConnectManager manager = TestUtils.getHealthConnectManager();
        float quotaAcquired = acquireCallQuotaForWrite();
        List<UpsertMedicalResourceRequest> request = List.of(getUpsertMedicalResourceRequest());

        while (quotaAcquired > 1) {
            HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
            manager.upsertMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
            quotaAcquired--;
        }
        int tryWriteWithBuffer = 20;
        while (tryWriteWithBuffer > 0) {
            HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
            manager.upsertMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
            tryWriteWithBuffer--;
        }
    }

    private float acquireCallQuotaForWrite() throws InterruptedException {
        Instant startTime = Instant.now();
        tryAcquireCallQuotaNTimesForWrite(MAX_FOREGROUND_WRITE_CALL_15M);
        Instant endTime = Instant.now();
        return getQuotaAcquired(startTime, endTime, WINDOW_15M, MAX_FOREGROUND_WRITE_CALL_15M);
    }

    private void exceedReadQuota() throws InterruptedException {
        ReadRecordsRequestUsingFilters<StepsRecord> readRecordsRequestUsingFilters =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setAscending(true)
                        .build();
        Instant startTime = Instant.now();
        List<Record> testRecord = Arrays.asList(getCompleteStepsRecord());

        List<Record> insertedRecords = TestUtils.insertRecords(testRecord);
        tryAcquireCallQuotaNTimesForRead(testRecord, insertedRecords, MAX_FOREGROUND_READ_CALL_15M);
        Instant endTime = Instant.now();
        float quotaAcquired =
                getQuotaAcquired(startTime, endTime, WINDOW_15M, MAX_FOREGROUND_READ_CALL_15M);
        while (quotaAcquired > 1) {
            readStepsRecordUsingIds(insertedRecords);
            quotaAcquired--;
        }
        int tryReadWithBuffer = 20;
        while (tryReadWithBuffer > 0) {
            TestUtils.readRecords(readRecordsRequestUsingFilters);
            tryReadWithBuffer--;
        }
    }

    private float getQuotaAcquired(
            Instant startTime, Instant endTime, Duration window, int maxQuota) {
        Duration timeSpent = Duration.between(startTime, endTime);
        return timeSpent.toMillis() * ((float) maxQuota / (float) window.toMillis());
    }

    /**
     * This method tries to use the Maximum read quota possible. Distributes the load to
     * ChangeLogToken, ChangeLog, Read, and Aggregate APIs.
     */
    private void tryAcquireCallQuotaNTimesForRead(
            List<Record> testRecord, List<Record> insertedRecords, int nTimes)
            throws InterruptedException {
        nTimes = nTimes / mLimitsAdjustmentForTesting;
        Context context = ApplicationProvider.getApplicationContext();

        // Each getChangelog is 2 reads.
        int changelogCalls = nTimes / 4;
        for (int i = 0; i < changelogCalls; i++) {
            getChangeLog(context);
        }

        int aggregateCalls = nTimes / 4;
        AggregateRecordsRequest<Long> aggregateRecordsRequest =
                new AggregateRecordsRequest.Builder<Long>(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.ofEpochMilli(0))
                                        .setEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                                        .build())
                        .addAggregationType(STEPS_COUNT_TOTAL)
                        .build();
        for (int i = 0; i < aggregateCalls; i++) {
            TestUtils.getAggregateResponse(aggregateRecordsRequest, testRecord);
        }

        for (int i = 0; i < nTimes - aggregateCalls - 2 * changelogCalls; i++) {
            readStepsRecordUsingIds(insertedRecords);
        }
    }

    private void getChangeLog(Context context) throws InterruptedException {
        // Use one read quota.
        ChangeLogTokenResponse tokenResponse =
                TestUtils.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addDataOriginFilter(
                                        new DataOrigin.Builder()
                                                .setPackageName(context.getPackageName())
                                                .build())
                                .addRecordType(StepsRecord.class)
                                .build());

        ChangeLogsRequest changeLogsRequest =
                new ChangeLogsRequest.Builder(tokenResponse.getToken()).build();
        // Use one read quota.
        TestUtils.getChangeLogs(changeLogsRequest);
    }

    /**
     * This method tries to use the Maximum write quota possible. Distributes the load across
     * Insert, and Update APIs. Also, we provide dataManagement permission to
     * MultiAppTestUtils.verifyDeleteRecords. We test unmetered rate limting as well here. No write
     * quota is used by MultiAppTestUtils.verifyDeleteRecords.
     */
    private void tryAcquireCallQuotaNTimesForWrite(int nTimes) throws InterruptedException {
        nTimes = nTimes / mLimitsAdjustmentForTesting;
        List<Record> testRecord = Arrays.asList(getCompleteStepsRecord());

        List<Record> insertedRecords = List.of();
        for (int i = 0; i < nTimes; i++) {
            if (i % 3 == 0) {
                insertedRecords = TestUtils.insertRecords(testRecord);
            } else if (i % 3 == 1) {
                List<Record> updateRecords = Arrays.asList(getCompleteStepsRecord());

                for (int itr = 0; itr < updateRecords.size(); itr++) {
                    updateRecords.set(
                            itr,
                            getUpdatedStepsRecord(
                                    updateRecords.get(itr),
                                    insertedRecords.get(itr).getMetadata().getId(),
                                    insertedRecords.get(itr).getMetadata().getClientRecordId()));
                }
                TestUtils.updateRecords(updateRecords);
            } else {
                TestUtils.insertRecords(testRecord);
                // Unmetered rate limiting as Holds data management is true for verify delete
                // records.
                TestUtils.verifyDeleteRecords(
                        new DeleteUsingFiltersRequest.Builder()
                                .addRecordType(StepsRecord.class)
                                .build());
            }
        }
    }

    private void readStepsRecordUsingIds(List<Record> recordList) throws InterruptedException {
        ReadRecordsRequestUsingIds.Builder<StepsRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class);
        recordList.forEach(v -> request.addId(v.getMetadata().getId()));
        TestUtils.readRecords(request.build());
    }

    private static MedicalResource upsertMedicalData(HealthConnectManager manager, String data)
            throws InterruptedException {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        manager.createMedicalDataSource(
                PhrDataFactory.getCreateMedicalDataSourceRequest(),
                Executors.newSingleThreadExecutor(),
                createReceiver);
        MedicalDataSource dataSource = createReceiver.getResponse();
        String dataSourceId = dataSource.getId();
        HealthConnectReceiver<List<MedicalResource>> dataReceiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(dataSourceId, FHIR_VERSION_R4, data)
                        .build();
        manager.upsertMedicalResources(
                List.of(request), Executors.newSingleThreadExecutor(), dataReceiver);
        // Make sure something got inserted.
        return Iterables.getOnlyElement(dataReceiver.getResponse());
    }
}
