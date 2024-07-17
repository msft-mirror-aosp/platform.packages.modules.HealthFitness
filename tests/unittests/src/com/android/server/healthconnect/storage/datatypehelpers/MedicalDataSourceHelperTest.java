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

import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_PACKAGE_NAME;

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.DATA_SOURCE_UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.DISPLAY_NAME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.FHIR_BASE_URI_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.MEDICAL_DATA_SOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.PACKAGE_NAME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.getCreateTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.getReadTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.getUpsertTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getHexString;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.datatypes.MedicalDataSource;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MedicalDataSourceHelperTest {
    private final HealthConnectDatabaseTestRule mHealthConnectDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    // See b/344587256 for more context.
    @Rule
    public TestRule chain =
            RuleChain.outerRule(new SetFlagsRule()).around(mHealthConnectDatabaseTestRule);

    private MedicalDataSourceHelper mMedicalDataSourceHelper;
    private TransactionManager mTransactionManager;

    @Before
    public void setup() {
        mTransactionManager = mHealthConnectDatabaseTestRule.getTransactionManager();
        mMedicalDataSourceHelper = new MedicalDataSourceHelper(mTransactionManager);
    }

    @Test
    public void getCreateTableRequest_correctResult() {
        List<Pair<String, String>> columnInfo =
                List.of(
                        Pair.create(PRIMARY_COLUMN_NAME, PRIMARY),
                        Pair.create(PACKAGE_NAME_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(DISPLAY_NAME_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_BASE_URI_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(DATA_SOURCE_UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL));
        CreateTableRequest expected =
                new CreateTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME, columnInfo);

        CreateTableRequest result = getCreateTableRequest();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void getUpsertTableRequest_correctResult() {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        UUID uuid = UUID.randomUUID();

        UpsertTableRequest upsertRequest =
                getUpsertTableRequest(
                        uuid, createMedicalDataSourceRequest, DATA_SOURCE_PACKAGE_NAME);
        ContentValues contentValues = upsertRequest.getContentValues();

        assertThat(upsertRequest.getTable()).isEqualTo(MEDICAL_DATA_SOURCE_TABLE_NAME);
        assertThat(upsertRequest.getUniqueColumnsCount()).isEqualTo(1);
        assertThat(contentValues.size()).isEqualTo(4);
        assertThat(contentValues.get(FHIR_BASE_URI_COLUMN_NAME))
                .isEqualTo(DATA_SOURCE_FHIR_BASE_URI);
        assertThat(contentValues.get(DISPLAY_NAME_COLUMN_NAME)).isEqualTo(DATA_SOURCE_DISPLAY_NAME);
        assertThat(contentValues.get(DATA_SOURCE_UUID_COLUMN_NAME))
                .isEqualTo(StorageUtils.convertUUIDToBytes(uuid));
        assertThat(contentValues.get(PACKAGE_NAME_COLUMN_NAME)).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
    }

    @Test
    public void getReadTableRequest_usingMedicalDataSourceId_correctQuery() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        List<String> hexValues = List.of(getHexString(uuid1), getHexString(uuid2));

        ReadTableRequest readRequest =
                getReadTableRequest(List.of(uuid1.toString(), uuid2.toString()));

        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_DATA_SOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM medical_data_source_table WHERE data_source_uuid IN ("
                                + String.join(", ", hexValues)
                                + ")");
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void createAndGetSingleMedicalDataSource_success() {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource expected =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest, DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(List.of(expected.getId()));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0)).isEqualTo(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void createAndGetMultipleMedicalDataSources_success() {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest1 =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest2 =
                new CreateMedicalDataSourceRequest.Builder(
                                DIFFERENT_DATA_SOURCE_BASE_URI, DIFFERENT_DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource dataSource1 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest1, DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest2, DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        List<MedicalDataSource> expected = List.of(dataSource1, dataSource2);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(
                        List.of(dataSource1.getId(), dataSource2.getId()));

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_noIds_success() {
        MedicalDataSourceHelper.deleteMedicalDataSources(Collections.emptyList());
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_badId_success() {
        MedicalDataSourceHelper.deleteMedicalDataSources(List.of("foo"));
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_badId_leavesRecordsUnchanged() {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource existing =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest, DATA_SOURCE_PACKAGE_NAME);

        MedicalDataSourceHelper.deleteMedicalDataSources(List.of("foo"));

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(List.of(existing.getId()));
        assertThat(result).containsExactly(existing);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_noIds_existingDataUnaffected() {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource existing =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest, DATA_SOURCE_PACKAGE_NAME);

        MedicalDataSourceHelper.deleteMedicalDataSources(Collections.emptyList());

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(List.of(existing.getId()));
        assertThat(result).containsExactly(existing);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_oneId_existingDataDeleted() {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource existing =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest, DATA_SOURCE_PACKAGE_NAME);

        MedicalDataSourceHelper.deleteMedicalDataSources(List.of(existing.getId()));

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(List.of(existing.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_multipleIds_existingDataDeleted() {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest1 =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest2 =
                new CreateMedicalDataSourceRequest.Builder(
                                DIFFERENT_DATA_SOURCE_BASE_URI, DIFFERENT_DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource dataSource1 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest1, DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest2, DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        MedicalDataSourceHelper.deleteMedicalDataSources(
                List.of(dataSource1.getId(), dataSource2.getId()));

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(
                        List.of(dataSource1.getId(), dataSource2.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_multiplePresentOneIdRequested_onlyRequestedDeleted() {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest1 =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest2 =
                new CreateMedicalDataSourceRequest.Builder(
                                DIFFERENT_DATA_SOURCE_BASE_URI, DIFFERENT_DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource dataSource1 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest1, DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        createMedicalDataSourceRequest2, DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        MedicalDataSourceHelper.deleteMedicalDataSources(List.of(dataSource1.getId()));

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(
                        List.of(dataSource1.getId(), dataSource2.getId()));
        assertThat(result).containsExactly(dataSource2);
    }

    // TODO: b/351166557 - add unit tests that deleting datasource deletes associated records
}
