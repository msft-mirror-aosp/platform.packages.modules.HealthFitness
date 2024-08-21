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

import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_READ;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_UPSERT;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;

import static com.android.healthfitness.flags.Flags.FLAG_DEVELOPMENT_DATABASE;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.MEDICAL_DATA_SOURCE_ACCESSED_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.MEDICAL_RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.getAlterTableRequestForPhrAccessLogs;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.queryAccessLogs;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.HealthConnectManager;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.datatypes.StepsRecord;
import android.os.Environment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.modules.utils.testing.ExtendedMockitoRule;
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

    @Before
    public void setup() {
        mTransactionTestUtils =
                new TransactionTestUtils(
                        /* context= */ null,
                        mHealthConnectDatabaseTestRule.getTransactionManager());
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
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
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testAddAccessLogsPhr_accessedSingleMedicalResourceType_success() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        AccessLogsHelper.addAccessLog(
                DATA_SOURCE_PACKAGE_NAME,
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                OPERATION_TYPE_READ,
                /* isMedicalDataSource= */ false);

        List<AccessLog> result = queryAccessLogs();
        AccessLog accessLog = result.get(0);

        assertThat(result).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.getMedicalDataSource()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testAddAccessLogsPhr_accessedMultipleMedicalResourceTypes_success() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        AccessLogsHelper.addAccessLog(
                DATA_SOURCE_PACKAGE_NAME,
                Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN, MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                OPERATION_TYPE_READ,
                /* isMedicalDataSource= */ false);

        List<AccessLog> result = queryAccessLogs();
        AccessLog accessLog = result.get(0);

        assertThat(result).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes())
                .isEqualTo(
                        Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN, MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.getMedicalDataSource()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testAddAccessLogsPhr_accessedMedicalDataSource_success() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        AccessLogsHelper.addAccessLog(
                DATA_SOURCE_PACKAGE_NAME,
                /* medicalResourceTypes= */ Set.of(),
                OPERATION_TYPE_READ,
                /* isMedicalDataSource= */ true);

        List<AccessLog> result = queryAccessLogs();
        AccessLog accessLog = result.get(0);

        assertThat(result).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes()).isEmpty();
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.getMedicalDataSource()).isTrue();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testAddAccessLogsForHCRecordType_queryAccessLogs_expectCorrectResult() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        AccessLogsHelper.addAccessLog(
                DATA_SOURCE_PACKAGE_NAME,
                /* recordTypeList= */ List.of(RECORD_TYPE_STEPS),
                OPERATION_TYPE_READ);

        List<AccessLog> result = queryAccessLogs();
        AccessLog accessLog = result.get(0);

        assertThat(result).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes()).isEmpty();
        assertThat(accessLog.getRecordTypes()).isEqualTo(List.of(StepsRecord.class));
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.getMedicalDataSource()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({FLAG_PERSONAL_HEALTH_RECORD, FLAG_DEVELOPMENT_DATABASE})
    public void testAddAccessLogsPhr_multipleAccessLogs_success() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        AccessLogsHelper.addAccessLog(
                DATA_SOURCE_PACKAGE_NAME,
                /* medicalResourceTypes= */ Set.of(),
                OPERATION_TYPE_READ,
                /* isMedicalDataSource= */ true);
        AccessLogsHelper.addAccessLog(
                DATA_SOURCE_PACKAGE_NAME,
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                OPERATION_TYPE_UPSERT,
                /* isMedicalDataSource= */ false);

        List<AccessLog> result = queryAccessLogs();
        AccessLog accessLog1 = result.get(0);
        AccessLog accessLog2 = result.get(1);

        assertThat(result).hasSize(2);

        assertThat(accessLog1.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog1.getMedicalResourceTypes()).isEmpty();
        assertThat(accessLog1.getRecordTypes()).isEmpty();
        assertThat(accessLog1.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog1.getMedicalDataSource()).isTrue();
        assertThat(accessLog1.getAccessTime()).isNotNull();

        assertThat(accessLog2.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog2.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog2.getRecordTypes()).isEmpty();
        assertThat(accessLog2.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog2.getMedicalDataSource()).isFalse();
        assertThat(accessLog2.getAccessTime()).isNotNull();
    }
}
