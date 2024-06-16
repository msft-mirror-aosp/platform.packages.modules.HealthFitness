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

package com.android.server.healthconnect.storage;

import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.addCompletedStatus;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceId;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceType;

import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createBloodPressureRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.createStepsRecord;
import static com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils.getReadTransactionRequest;
import static com.android.server.healthconnect.storage.utils.StorageUtils.generateMedicalResourceUUID;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.DeleteUsingFiltersRequest;
import android.health.connect.MedicalResourceId;
import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.RecordIdFilter;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.aidl.DeleteUsingFiltersRequestParcel;
import android.health.connect.aidl.RecordIdFiltersParcel;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.datatypes.StepsRecord;
import android.health.connect.internal.datatypes.MedicalResourceInternal;
import android.health.connect.internal.datatypes.RecordInternal;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;
import com.android.server.healthconnect.storage.request.DeleteTransactionRequest;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TransactionManagerTest {
    private static final String TEST_PACKAGE_NAME = "package.name";

    private final HealthConnectDatabaseTestRule mHealthConnectDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    // SetFlagsRule needs to be executed before any rules that accesses aconfig flags. Otherwise,
    // we will get failure like in b/344587256.
    // This is a workaround due to b/335666574, however the tests are still relevant even if the
    // rules have to run in this order. So we won't have to revert this even when b/335666574 is
    // fixed.
    // See https://chat.google.com/room/AAAAoLBF6rc/4N8gVXyQY5E
    @Rule
    public TestRule chain =
            RuleChain.outerRule(new SetFlagsRule()).around(mHealthConnectDatabaseTestRule);

    private TransactionTestUtils mTransactionTestUtils;
    private TransactionManager mTransactionManager;

    @Before
    public void setup() {
        HealthConnectUserContext context = mHealthConnectDatabaseTestRule.getUserContext();
        mTransactionManager = mHealthConnectDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(context, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
    }

    @Test
    public void readRecordsById_returnsAllRecords() {
        long timeMillis = 456;
        String uuid =
                mTransactionTestUtils
                        .insertRecords(
                                TEST_PACKAGE_NAME,
                                createBloodPressureRecord(timeMillis, 120.0, 80.0))
                        .get(0);

        ReadRecordsRequestUsingIds<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class)
                        .addId(uuid)
                        .build();
        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());

        List<RecordInternal<?>> records =
                mTransactionManager.readRecordsByIds(readTransactionRequest);
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getUuid()).isEqualTo(UUID.fromString(uuid));
    }

    @Test
    public void readRecordsById_multipleRecordTypes_returnsAllRecords() {
        long startTimeMillis = 123;
        long endTimeMillis = 456;
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME,
                        createStepsRecord(startTimeMillis, endTimeMillis, 100),
                        createBloodPressureRecord(endTimeMillis, 120.0, 80.0));

        List<UUID> stepsUuids = ImmutableList.of(UUID.fromString(uuids.get(0)));
        List<UUID> bloodPressureUuids = ImmutableList.of(UUID.fromString(uuids.get(1)));
        ReadTransactionRequest request =
                getReadTransactionRequest(
                        ImmutableMap.of(
                                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                                stepsUuids,
                                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE,
                                bloodPressureUuids));

        List<RecordInternal<?>> records = mTransactionManager.readRecordsByIds(request);
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getUuid()).isEqualTo(UUID.fromString(uuids.get(0)));
        assertThat(records.get(1).getUuid()).isEqualTo(UUID.fromString(uuids.get(1)));
    }

    @Test
    public void readRecordsById_readByFilterRequest_throws() {
        ReadRecordsRequestUsingFilters<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .setEndTime(Instant.ofEpochMilli(1000))
                                        .build())
                        .setPageSize(1)
                        .build();
        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());
        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mTransactionManager.readRecordsByIds(readTransactionRequest));
        assertThat(thrown).hasMessageThat().contains("Expect read by id request");
    }

    @Test
    public void readRecordsAndPageToken_returnsRecordsAndPageToken() {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME,
                        createStepsRecord(400, 500, 100),
                        createStepsRecord(500, 600, 100));

        ReadRecordsRequestUsingFilters<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .setEndTime(Instant.ofEpochMilli(1000))
                                        .build())
                        .setPageSize(1)
                        .build();
        PageTokenWrapper expectedToken =
                PageTokenWrapper.of(
                        /* isAscending= */ true, /* timeMillis= */ 500, /* offset= */ 0);

        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());
        Pair<List<RecordInternal<?>>, PageTokenWrapper> result =
                mTransactionManager.readRecordsAndPageToken(readTransactionRequest);
        List<RecordInternal<?>> records = result.first;
        assertThat(records).hasSize(1);
        assertThat(result.first.get(0).getUuid()).isEqualTo(UUID.fromString(uuids.get(0)));
        assertThat(result.second).isEqualTo(expectedToken);
    }

    @Test
    public void readRecordsAndPageToken_readByIdRequest_throws() {
        ReadRecordsRequestUsingIds<BloodPressureRecord> request =
                new ReadRecordsRequestUsingIds.Builder<>(BloodPressureRecord.class)
                        .addId(UUID.randomUUID().toString())
                        .build();
        ReadTransactionRequest readTransactionRequest =
                getReadTransactionRequest(request.toReadRecordsRequestParcel());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mTransactionManager.readRecordsAndPageToken(readTransactionRequest));
        assertThat(thrown).hasMessageThat().contains("Expect read by filter request");
    }

    @Test
    public void deleteAll_byId_generateChangeLogs() {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME, createStepsRecord(123, 456, 100));
        List<RecordIdFilter> ids = List.of(RecordIdFilter.fromId(StepsRecord.class, uuids.get(0)));

        DeleteUsingFiltersRequestParcel parcel =
                new DeleteUsingFiltersRequestParcel(
                        new RecordIdFiltersParcel(ids), TEST_PACKAGE_NAME);
        assertThat(parcel.usesIdFilters()).isTrue();
        mTransactionManager.deleteAll(new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel));

        List<UUID> uuidList = mTransactionTestUtils.getAllDeletedUuids();
        assertThat(uuidList).hasSize(1);
        assertThat(uuidList.get(0).toString()).isEqualTo(uuids.get(0));
    }

    @Test
    public void deleteAll_byTimeFilter_generateChangeLogs() {
        List<String> uuids =
                mTransactionTestUtils.insertRecords(
                        TEST_PACKAGE_NAME, createStepsRecord(123, 456, 100));

        DeleteUsingFiltersRequest deleteRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .build())
                        .build();
        DeleteUsingFiltersRequestParcel parcel = new DeleteUsingFiltersRequestParcel(deleteRequest);
        assertThat(parcel.usesIdFilters()).isFalse();
        mTransactionManager.deleteAll(new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel));

        List<UUID> uuidList = mTransactionTestUtils.getAllDeletedUuids();
        assertThat(uuidList).hasSize(1);
        assertThat(uuidList.get(0).toString()).isEqualTo(uuids.get(0));
    }

    @Test
    public void deleteAll_bulkDeleteByTimeFilter_generateChangeLogs() {
        ImmutableList.Builder<RecordInternal<?>> records = new ImmutableList.Builder<>();
        for (int i = 0; i <= DEFAULT_PAGE_SIZE; i++) {
            records.add(createStepsRecord(i * 1000, (i + 1) * 1000, 9527));
        }
        mTransactionTestUtils.insertRecords(TEST_PACKAGE_NAME, records.build());

        DeleteUsingFiltersRequest deleteRequest =
                new DeleteUsingFiltersRequest.Builder()
                        .setTimeRangeFilter(
                                new TimeInstantRangeFilter.Builder()
                                        .setStartTime(Instant.EPOCH)
                                        .build())
                        .build();
        DeleteUsingFiltersRequestParcel parcel = new DeleteUsingFiltersRequestParcel(deleteRequest);
        mTransactionManager.deleteAll(new DeleteTransactionRequest(TEST_PACKAGE_NAME, parcel));

        List<UUID> uuidList = mTransactionTestUtils.getAllDeletedUuids();
        assertThat(uuidList).hasSize(DEFAULT_PAGE_SIZE + 1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readMedicalResourcesByIds_returnsEmpty() throws JSONException {
        List<MedicalResourceId> medicalResourceIds =
                List.of(
                        new MedicalResourceId(
                                DATA_SOURCE_ID,
                                getFhirResourceType(FHIR_DATA_IMMUNIZATION),
                                getFhirResourceId(FHIR_DATA_IMMUNIZATION)));

        List<MedicalResource> resources =
                mTransactionManager.readMedicalResourcesByIds(medicalResourceIds);

        assertThat(resources).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMedicalResources_returnsMedicalResources() throws JSONException {
        String fhirResourceId = getFhirResourceId(FHIR_DATA_IMMUNIZATION);
        String fhirResourceType = getFhirResourceType(FHIR_DATA_IMMUNIZATION);
        UUID uuid = generateMedicalResourceUUID(fhirResourceId, fhirResourceType, DATA_SOURCE_ID);
        List<MedicalResource> expected =
                Collections.singletonList(
                        new MedicalResource.Builder(
                                        uuid.toString(),
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        DATA_SOURCE_ID,
                                        FHIR_DATA_IMMUNIZATION)
                                .build());
        MedicalResourceInternal medicalResourceInternal =
                new MedicalResourceInternal()
                        .setFhirResourceId(fhirResourceId)
                        .setFhirResourceType(fhirResourceType)
                        .setData(FHIR_DATA_IMMUNIZATION)
                        .setDataSourceId(DATA_SOURCE_ID);

        List<MedicalResource> result =
                mTransactionManager.upsertMedicalResources(List.of(medicalResourceInternal));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertSingleMedicalResource_readSingleResource() throws JSONException {
        String fhirResourceId = getFhirResourceId(FHIR_DATA_IMMUNIZATION);
        String fhirResourceType = getFhirResourceType(FHIR_DATA_IMMUNIZATION);
        UUID uuid = generateMedicalResourceUUID(fhirResourceId, fhirResourceType, DATA_SOURCE_ID);
        MedicalResourceInternal medicalResourceInternal =
                new MedicalResourceInternal()
                        .setFhirResourceId(fhirResourceId)
                        .setFhirResourceType(fhirResourceType)
                        .setData(FHIR_DATA_IMMUNIZATION)
                        .setDataSourceId(DATA_SOURCE_ID);
        List<MedicalResourceId> medicalIdFilters =
                List.of(new MedicalResourceId(DATA_SOURCE_ID, fhirResourceType, fhirResourceId));
        List<MedicalResource> expected =
                Collections.singletonList(
                        new MedicalResource.Builder(
                                        uuid.toString(),
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        DATA_SOURCE_ID,
                                        FHIR_DATA_IMMUNIZATION)
                                .build());

        mTransactionManager.upsertMedicalResources(List.of(medicalResourceInternal));
        List<MedicalResource> result =
                mTransactionManager.readMedicalResourcesByIds(medicalIdFilters);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_readMultipleResources() throws JSONException {
        String fhirResourceId1 = getFhirResourceId(FHIR_DATA_IMMUNIZATION);
        String fhirResourceType1 = getFhirResourceType(FHIR_DATA_IMMUNIZATION);
        UUID uuid1 =
                generateMedicalResourceUUID(fhirResourceId1, fhirResourceType1, DATA_SOURCE_ID);
        MedicalResourceInternal medicalResourceInternal1 =
                new MedicalResourceInternal()
                        .setFhirResourceId(fhirResourceId1)
                        .setFhirResourceType(fhirResourceType1)
                        .setData(FHIR_DATA_IMMUNIZATION)
                        .setDataSourceId(DATA_SOURCE_ID);
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                uuid1.toString(),
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                DATA_SOURCE_ID,
                                FHIR_DATA_IMMUNIZATION)
                        .build();
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(DATA_SOURCE_ID, fhirResourceType1, fhirResourceId1);
        String fhirResourceId2 = getFhirResourceId(FHIR_DATA_ALLERGY);
        String fhirResourceType2 = getFhirResourceType(FHIR_DATA_ALLERGY);
        UUID uuid2 =
                generateMedicalResourceUUID(
                        fhirResourceId2, fhirResourceType2, DIFFERENT_DATA_SOURCE_ID);
        MedicalResourceInternal medicalResourceInternal2 =
                new MedicalResourceInternal()
                        .setFhirResourceId(fhirResourceId2)
                        .setFhirResourceType(fhirResourceType2)
                        .setData(FHIR_DATA_ALLERGY)
                        .setDataSourceId(DIFFERENT_DATA_SOURCE_ID);
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                uuid2.toString(),
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                DIFFERENT_DATA_SOURCE_ID,
                                FHIR_DATA_ALLERGY)
                        .build();
        List<MedicalResource> expected = List.of(resource1, resource2);
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(DIFFERENT_DATA_SOURCE_ID, fhirResourceType2, fhirResourceId2);
        List<MedicalResourceId> medicalIdFilters = List.of(medicalResourceId1, medicalResourceId2);

        mTransactionManager.upsertMedicalResources(
                List.of(medicalResourceInternal1, medicalResourceInternal2));
        List<MedicalResource> result =
                mTransactionManager.readMedicalResourcesByIds(medicalIdFilters);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void updateSingleMedicalResource_success() throws JSONException {
        String fhirResourceType = getFhirResourceType(FHIR_DATA_IMMUNIZATION);
        String fhirResourceId = getFhirResourceId(FHIR_DATA_IMMUNIZATION);
        MedicalResourceInternal medicalResourceInternal =
                new MedicalResourceInternal()
                        .setFhirResourceId(fhirResourceId)
                        .setFhirResourceType(fhirResourceType)
                        .setData(FHIR_DATA_IMMUNIZATION)
                        .setDataSourceId(DATA_SOURCE_ID);
        MedicalResourceInternal medicalResourceInternalUpdated =
                new MedicalResourceInternal()
                        .setFhirResourceId(fhirResourceId)
                        .setFhirResourceType(fhirResourceType)
                        .setData(addCompletedStatus(FHIR_DATA_IMMUNIZATION))
                        .setDataSourceId(DATA_SOURCE_ID);
        UUID uuid = generateMedicalResourceUUID(fhirResourceId, fhirResourceType, DATA_SOURCE_ID);
        List<MedicalResourceId> medicalIdFilters =
                List.of(new MedicalResourceId(DATA_SOURCE_ID, fhirResourceType, fhirResourceId));
        List<MedicalResource> expected =
                Collections.singletonList(
                        new MedicalResource.Builder(
                                        uuid.toString(),
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        DATA_SOURCE_ID,
                                        addCompletedStatus(FHIR_DATA_IMMUNIZATION))
                                .build());

        mTransactionManager.upsertMedicalResources(List.of(medicalResourceInternal));
        mTransactionManager.upsertMedicalResources(List.of(medicalResourceInternalUpdated));
        List<MedicalResource> result =
                mTransactionManager.readMedicalResourcesByIds(medicalIdFilters);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).isEqualTo(expected);
    }
}
