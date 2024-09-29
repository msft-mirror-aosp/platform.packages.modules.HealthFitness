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

package com.android.server.healthconnect.storage.datatypehelpers;

import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_DELETE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_READ;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_UPSERT;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BODY_FAT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_DISTANCE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_HEIGHT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_SKIN_TEMPERATURE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS_CADENCE;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.MEDICAL_DATA_SOURCE_ACCESSED_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.MEDICAL_RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.getAlterTableRequestForPhrAccessLogs;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.HealthConnectManager;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.datatypes.BloodPressureRecord;
import android.health.connect.datatypes.BodyFatRecord;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.HeightRecord;
import android.health.connect.datatypes.SkinTemperatureRecord;
import android.health.connect.datatypes.StepsCadenceRecord;
import android.health.connect.datatypes.StepsRecord;
import android.os.Environment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.AlterTableRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

public class AccessLogsHelperTest {

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Rule(order = 3)
    public final HealthConnectDatabaseTestRule mHealthConnectDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private TransactionTestUtils mTransactionTestUtils;
    private TransactionManager mTransactionManager;
    private AccessLogsHelper mAccessLogsHelper;

    @Before
    public void setup() {
        AccessLogsHelper.resetInstanceForTest();

        mTransactionTestUtils =
                new TransactionTestUtils(
                        mHealthConnectDatabaseTestRule.getUserContext(),
                        mHealthConnectDatabaseTestRule.getTransactionManager());
        mTransactionManager = mHealthConnectDatabaseTestRule.getTransactionManager();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(
                                mHealthConnectDatabaseTestRule.getUserContext())
                        .setTransactionManager(mTransactionManager)
                        .build();
        mAccessLogsHelper = healthConnectInjector.getAccessLogsHelper();

        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetAlterTableRequestForPhrAccessLogs_success() {
        List<Pair<String, String>> columnInfo =
                List.of(
                        Pair.create(MEDICAL_RESOURCE_TYPE_COLUMN_NAME, TEXT_NULL),
                        Pair.create(MEDICAL_DATA_SOURCE_ACCESSED_COLUMN_NAME, INTEGER));
        AlterTableRequest expected = new AlterTableRequest(AccessLogsHelper.TABLE_NAME, columnInfo);

        AlterTableRequest result = getAlterTableRequestForPhrAccessLogs();

        assertThat(result.getAlterTableAddColumnsCommands())
                .isEqualTo(expected.getAlterTableAddColumnsCommands());
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testAddAccessLogsPhr_accessedSingleMedicalResourceType_success() {
        mTransactionManager.runAsTransaction(
                (TransactionManager.TransactionRunnable<RuntimeException>)
                        db ->
                                mAccessLogsHelper.addAccessLog(
                                        db,
                                        DATA_SOURCE_PACKAGE_NAME,
                                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                                        OPERATION_TYPE_READ,
                                        /* accessedMedicalDataSource= */ false));

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        AccessLog accessLog = result.get(0);

        assertThat(result).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS));
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testAddAccessLogsPhr_accessedMultipleMedicalResourceTypes_success() {
        mTransactionManager.runAsTransaction(
                (TransactionManager.TransactionRunnable<RuntimeException>)
                        db ->
                                mAccessLogsHelper.addAccessLog(
                                        db,
                                        DATA_SOURCE_PACKAGE_NAME,
                                        Set.of(
                                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                                        OPERATION_TYPE_READ,
                                        /* accessedMedicalDataSource= */ false));

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        AccessLog accessLog = result.get(0);

        assertThat(result).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes())
                .isEqualTo(
                        Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN, MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS));
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testAddAccessLogsPhr_accessedMedicalDataSource_success() {
        mTransactionManager.runAsTransaction(
                (TransactionManager.TransactionRunnable<RuntimeException>)
                        db ->
                                mAccessLogsHelper.addAccessLog(
                                        db,
                                        DATA_SOURCE_PACKAGE_NAME,
                                        /* medicalResourceTypes= */ Set.of(),
                                        OPERATION_TYPE_READ,
                                        /* accessedMedicalDataSource= */ true));

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        AccessLog accessLog = result.get(0);

        assertThat(result).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes()).isEmpty();
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.isMedicalDataSourceAccessed()).isTrue();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testAddAccessLogsForHCRecordType_queryAccessLogs_expectCorrectResult() {
        mAccessLogsHelper.addAccessLog(
                DATA_SOURCE_PACKAGE_NAME,
                /* recordTypeList= */ List.of(RECORD_TYPE_STEPS),
                OPERATION_TYPE_READ);

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        AccessLog accessLog = result.get(0);

        assertThat(result).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes()).isEmpty();
        assertThat(accessLog.getRecordTypes()).isEqualTo(List.of(StepsRecord.class));
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testAddAccessLogsPhr_multipleAccessLogs_success() {
        mTransactionManager.runAsTransaction(
                db -> {
                    mAccessLogsHelper.addAccessLog(
                            db,
                            DATA_SOURCE_PACKAGE_NAME,
                            /* medicalResourceTypes= */ Set.of(),
                            OPERATION_TYPE_READ,
                            /* accessedMedicalDataSource= */ true);
                    mAccessLogsHelper.addAccessLog(
                            db,
                            DATA_SOURCE_PACKAGE_NAME,
                            Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                            OPERATION_TYPE_UPSERT,
                            /* accessedMedicalDataSource= */ false);
                });

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        AccessLog accessLog1 = result.get(0);
        AccessLog accessLog2 = result.get(1);

        assertThat(result).hasSize(2);

        assertThat(accessLog1.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog1.getMedicalResourceTypes()).isEmpty();
        assertThat(accessLog1.getRecordTypes()).isEmpty();
        assertThat(accessLog1.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog1.isMedicalDataSourceAccessed()).isTrue();
        assertThat(accessLog1.getAccessTime()).isNotNull();

        assertThat(accessLog2.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog2.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS));
        assertThat(accessLog2.getRecordTypes()).isEmpty();
        assertThat(accessLog2.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog2.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog2.getAccessTime()).isNotNull();
    }

    @Test
    public void queryAccessLogs_invalidAppId_skipped() {
        mTransactionManager.runAsTransaction(
                db -> {
                    mAccessLogsHelper.recordDeleteAccessLog(
                            db, DATA_SOURCE_PACKAGE_NAME, Set.of(RECORD_TYPE_BLOOD_PRESSURE));
                    mAccessLogsHelper.recordReadAccessLog(
                            db, "invalid.package", Set.of(RECORD_TYPE_STEPS_CADENCE));
                    mAccessLogsHelper.recordDeleteAccessLog(
                            db, DATA_SOURCE_PACKAGE_NAME, Set.of(RECORD_TYPE_HEIGHT));
                });

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRecordTypes()).containsExactly(BloodPressureRecord.class);
        assertThat(result.get(1).getRecordTypes()).containsExactly(HeightRecord.class);
    }

    @Test
    public void recordDeleteAccessLog_success() {
        Set<Integer> recordTypeIds = Set.of(RECORD_TYPE_STEPS_CADENCE);
        mTransactionManager.runAsTransaction(
                db -> {
                    mAccessLogsHelper.recordDeleteAccessLog(
                            db, DATA_SOURCE_PACKAGE_NAME, recordTypeIds);
                });

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).hasSize(1);
        AccessLog log = result.get(0);
        assertThat(log.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(log.getRecordTypes()).containsExactly(StepsCadenceRecord.class);
        assertThat(log.getOperationType()).isEqualTo(OPERATION_TYPE_DELETE);
    }

    @Test
    public void recordReadAccessLog_success() {
        Set<Integer> recordTypeIds = Set.of(RECORD_TYPE_DISTANCE, RECORD_TYPE_SKIN_TEMPERATURE);
        mTransactionManager.runAsTransaction(
                db -> {
                    mAccessLogsHelper.recordReadAccessLog(
                            db, DATA_SOURCE_PACKAGE_NAME, recordTypeIds);
                });

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).hasSize(1);
        AccessLog log = result.get(0);
        assertThat(log.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(log.getRecordTypes())
                .containsExactly(DistanceRecord.class, SkinTemperatureRecord.class);
        assertThat(log.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
    }

    @Test
    public void recordUpsertAccessLog_success() {
        Set<Integer> recordTypeIds = Set.of(RECORD_TYPE_BODY_FAT, RECORD_TYPE_HEIGHT);
        mTransactionManager.runAsTransaction(
                db -> {
                    mAccessLogsHelper.recordUpsertAccessLog(
                            db, DATA_SOURCE_PACKAGE_NAME, recordTypeIds);
                });

        List<AccessLog> result = mAccessLogsHelper.queryAccessLogs();
        assertThat(result).hasSize(1);
        AccessLog log = result.get(0);
        assertThat(log.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(log.getRecordTypes()).containsExactly(BodyFatRecord.class, HeightRecord.class);
        assertThat(log.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
    }

    @Test
    public void testAddAccessLogsForDelete_getLatestAccessLogTimeStampForMAU_expectCorrectResult() {
        Set<Integer> recordTypeIds = Set.of(RECORD_TYPE_DISTANCE, RECORD_TYPE_SKIN_TEMPERATURE);
        mTransactionManager.runAsTransaction(
                db -> {
                    mAccessLogsHelper.recordDeleteAccessLog(
                            db, DATA_SOURCE_PACKAGE_NAME, recordTypeIds);
                });

        long result = mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp();

        assertThat(result).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    public void testAddAccessLogsForUpsert_getLatestAccessLogTimeStampForMAU_expectCorrectResult() {
        Set<Integer> recordTypeIds = Set.of(RECORD_TYPE_DISTANCE, RECORD_TYPE_SKIN_TEMPERATURE);
        mTransactionManager.runAsTransaction(
                db -> {
                    mAccessLogsHelper.recordUpsertAccessLog(
                            db, DATA_SOURCE_PACKAGE_NAME, recordTypeIds);
                });

        long result = mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp();

        assertThat(result).isNotEqualTo(Long.MIN_VALUE);
    }

    @Test
    public void testAddAccessLogsForRead_getLatestAccessLogTimeStampForMAU_expectCorrectResult() {
        Set<Integer> recordTypeIds = Set.of(RECORD_TYPE_DISTANCE, RECORD_TYPE_SKIN_TEMPERATURE);
        mTransactionManager.runAsTransaction(
                db -> {
                    mAccessLogsHelper.recordReadAccessLog(
                            db, DATA_SOURCE_PACKAGE_NAME, recordTypeIds);
                });

        long result = mAccessLogsHelper.getLatestUpsertOrReadOperationAccessLogTimeStamp();

        assertThat(result).isNotEqualTo(Long.MIN_VALUE);
    }
}
