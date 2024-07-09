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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_TYPE_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.addCompletedStatus;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceAllergy;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceId;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceTypeString;

import static com.android.server.healthconnect.phr.FhirJsonExtractor.getFhirResourceTypeInt;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.DATA_SOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_DATA_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_VERSION_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.MEDICAL_RESOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getCreateTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.MEDICAL_RESOURCE_ID;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.MEDICAL_RESOURCE_INDICES_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.MEDICAL_RESOURCE_TYPE;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.LAST_MODIFIED_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.generateMedicalResourceUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getHexString;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ContentValues;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.MedicalResourceId;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MedicalResourceHelperTest {
    private final HealthConnectDatabaseTestRule mHealthConnectDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    @Rule
    public TestRule chain =
            RuleChain.outerRule(new SetFlagsRule()).around(mHealthConnectDatabaseTestRule);

    private MedicalResourceHelper mMedicalResourceHelper;
    private MedicalDataSourceHelper mMedicalDataSourceHelper;
    private static final long DATA_SOURCE_ROW_ID = 1234;

    @Before
    public void setup() {
        TransactionManager transactionManager =
                mHealthConnectDatabaseTestRule.getTransactionManager();
        mMedicalDataSourceHelper = new MedicalDataSourceHelper(transactionManager);
        mMedicalResourceHelper =
                new MedicalResourceHelper(transactionManager, mMedicalDataSourceHelper);
    }

    @Test
    public void getCreateTableRequest_correctResult() {
        List<Pair<String, String>> columnInfoMedicalResource =
                List.of(
                        Pair.create(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT),
                        Pair.create(FHIR_RESOURCE_TYPE_COLUMN_NAME, INTEGER_NOT_NULL),
                        Pair.create(FHIR_RESOURCE_ID_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_DATA_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_VERSION_COLUMN_NAME, TEXT_NULL),
                        Pair.create(DATA_SOURCE_ID_COLUMN_NAME, INTEGER_NOT_NULL),
                        Pair.create(UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL),
                        Pair.create(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER));
        List<Pair<String, String>> columnInfoMedicalResourceIndices =
                List.of(
                        Pair.create(MEDICAL_RESOURCE_ID, INTEGER_NOT_NULL),
                        Pair.create(MEDICAL_RESOURCE_TYPE, INTEGER_NOT_NULL));
        CreateTableRequest childTableRequest =
                new CreateTableRequest(
                                MEDICAL_RESOURCE_INDICES_TABLE_NAME,
                                columnInfoMedicalResourceIndices)
                        .addForeignKey(
                                MEDICAL_RESOURCE_TABLE_NAME,
                                Collections.singletonList(MEDICAL_RESOURCE_ID),
                                Collections.singletonList(PRIMARY_COLUMN_NAME));
        CreateTableRequest expected =
                new CreateTableRequest(MEDICAL_RESOURCE_TABLE_NAME, columnInfoMedicalResource)
                        .addForeignKey(
                                MedicalDataSourceHelper.getMainTableName(),
                                Collections.singletonList(DATA_SOURCE_ID_COLUMN_NAME),
                                Collections.singletonList(PRIMARY_COLUMN_NAME))
                        .setChildTableRequests(List.of(childTableRequest));

        CreateTableRequest result = getCreateTableRequest();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void getUpsertTableRequest_correctResult() {
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(DATA_SOURCE_ID);
        UUID uuid =
                generateMedicalResourceUUID(
                        fhirResource.getId(), fhirResource.getType(), DATA_SOURCE_ID);

        UpsertTableRequest upsertRequest =
                MedicalResourceHelper.getUpsertTableRequest(
                        uuid, DATA_SOURCE_ROW_ID, upsertMedicalResourceInternalRequest);
        ContentValues contentValues = upsertRequest.getContentValues();

        assertThat(upsertRequest.getTable()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(upsertRequest.getUniqueColumnsCount()).isEqualTo(1);
        assertThat(contentValues.size()).isEqualTo(5);
        assertThat(contentValues.get(FHIR_RESOURCE_TYPE_COLUMN_NAME))
                .isEqualTo(fhirResource.getType());
        assertThat(contentValues.get(DATA_SOURCE_ID_COLUMN_NAME)).isEqualTo(DATA_SOURCE_ROW_ID);
        assertThat(contentValues.get(FHIR_DATA_COLUMN_NAME)).isEqualTo(fhirResource.getData());
        assertThat(contentValues.get(UUID_COLUMN_NAME))
                .isEqualTo(StorageUtils.convertUUIDToBytes(uuid));
    }

    @Test
    public void getReadTableRequest_usingMedicalResourceId_correctQuery() throws JSONException {
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_IMMUNIZATION, "resourceId1");
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        DIFFERENT_DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_ALLERGY, "resourceId2");
        List<MedicalResourceId> medicalResourceIds =
                List.of(medicalResourceId1, medicalResourceId2);
        String hex1 = makeMedicalResourceHexString(medicalResourceId1);
        String hex2 = makeMedicalResourceHexString(medicalResourceId2);
        List<String> hexValues = List.of(hex1, hex2);

        ReadTableRequest readRequest =
                MedicalResourceHelper.getReadTableRequest(medicalResourceIds);

        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM ( SELECT * FROM medical_resource_table WHERE uuid IN ("
                                + String.join(", ", hexValues)
                                + ") ) AS inner_query_result  LEFT JOIN medical_data_source_table"
                                + " ON inner_query_result.data_source_id ="
                                + " medical_data_source_table.row_id");
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readMedicalResourcesByIds_returnsEmpty() throws JSONException {
        List<MedicalResourceId> medicalResourceIds =
                List.of(
                        new MedicalResourceId(
                                DATA_SOURCE_ID,
                                getFhirResourceTypeString(FHIR_DATA_IMMUNIZATION),
                                getFhirResourceId(FHIR_DATA_IMMUNIZATION)));

        List<MedicalResource> resources =
                mMedicalResourceHelper.readMedicalResourcesByIds(medicalResourceIds);

        assertThat(resources).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMedicalResources_dataSourceNotInserted_exceptionThrown() {
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId("acc6c726-b7ea-42f1-a063-e34f5b4e6247");

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mMedicalResourceHelper.upsertMedicalResources(
                                        List.of(upsertMedicalResourceInternalRequest)));
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "Invalid data source id: "
                                + upsertMedicalResourceInternalRequest.getDataSourceId());
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMedicalResources_returnsMedicalResources() throws JSONException {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        List<MedicalResource> expected =
                Collections.singletonList(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        dataSource.getId(),
                                        fhirResource.getData())
                                .build());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(upsertMedicalResourceInternalRequest));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertSingleMedicalResource_readSingleResource() throws JSONException {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        List<MedicalResourceId> ids =
                List.of(
                        new MedicalResourceId(
                                dataSource.getId(),
                                getFhirResourceTypeString(fhirResource.getData()),
                                fhirResource.getId()));
        List<MedicalResource> expected =
                Collections.singletonList(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        dataSource.getId(),
                                        FHIR_DATA_IMMUNIZATION)
                                .build());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(upsertMedicalResourceInternalRequest));
        List<MedicalResource> result = mMedicalResourceHelper.readMedicalResourcesByIds(ids);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).isEqualTo(upsertedMedicalResources);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResourcesWithSameDataSource_readMultipleResources()
            throws JSONException {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource1 = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource1.getId())
                        .setFhirResourceType(fhirResource1.getType())
                        .setData(fhirResource1.getData())
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                fhirResource1.getData())
                        .build();
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        dataSource.getId(),
                        getFhirResourceTypeString(fhirResource1.getData()),
                        fhirResource1.getId());
        FhirResource fhirResource2 = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource2.getId())
                        .setFhirResourceType(fhirResource2.getType())
                        .setData(fhirResource2.getData())
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                fhirResource2.getData())
                        .build();
        List<MedicalResource> expected = List.of(resource1, resource2);
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        dataSource.getId(),
                        getFhirResourceTypeString(fhirResource2.getData()),
                        fhirResource2.getId());
        List<MedicalResourceId> ids = List.of(medicalResourceId1, medicalResourceId2);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> result = mMedicalResourceHelper.readMedicalResourcesByIds(ids);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(upsertedMedicalResources);
        assertThat(result).containsExactlyElementsIn(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResourcesWithDifferentDataSources_readMultipleResources()
            throws JSONException {
        MedicalDataSource dataSource1 =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                insertMedicalDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource1.getId());
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource1.getId(),
                                fhirResource.getData())
                        .build();
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        dataSource1.getId(),
                        getFhirResourceTypeString(fhirResource.getData()),
                        fhirResource.getId());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource2.getId());
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource2.getId(),
                                fhirResource.getData())
                        .build();
        List<MedicalResource> expected = List.of(resource1, resource2);
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        dataSource2.getId(),
                        getFhirResourceTypeString(fhirResource.getData()),
                        fhirResource.getId());
        List<MedicalResourceId> ids = List.of(medicalResourceId1, medicalResourceId2);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> result = mMedicalResourceHelper.readMedicalResourcesByIds(ids);

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(upsertedMedicalResources);
        assertThat(result).containsExactlyElementsIn(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void updateSingleMedicalResource_success() throws JSONException {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(addCompletedStatus(fhirResource.getData()))
                        .setDataSourceId(dataSource.getId());
        List<MedicalResourceId> ids =
                List.of(
                        new MedicalResourceId(
                                dataSource.getId(),
                                getFhirResourceTypeString(fhirResource.getData()),
                                fhirResource.getId()));
        List<MedicalResource> expected =
                Collections.singletonList(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        dataSource.getId(),
                                        addCompletedStatus(fhirResource.getData()))
                                .build());

        mMedicalResourceHelper.upsertMedicalResources(
                List.of(upsertMedicalResourceInternalRequest));
        List<MedicalResource> updatedMedicalResource =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(upsertMedicalResourceInternalRequestUpdated));
        List<MedicalResource> result = mMedicalResourceHelper.readMedicalResourcesByIds(ids);

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).isEqualTo(updatedMedicalResource);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_noId_fails() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mMedicalResourceHelper.deleteMedicalResourcesByIds(List.of()));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneIdNotPresent_succeeds() throws Exception {
        String fhirResourceType = getFhirResourceTypeString(FHIR_DATA_IMMUNIZATION);
        String fhirResourceId = getFhirResourceId(FHIR_DATA_IMMUNIZATION);

        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                List.of(new MedicalResourceId(DATA_SOURCE_ID, fhirResourceType, fhirResourceId)));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneIdPresent_succeedsDeleting() throws Exception {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        mMedicalResourceHelper.upsertMedicalResources(
                List.of(upsertMedicalResourceInternalRequest));

        MedicalResourceId id =
                new MedicalResourceId(
                        dataSource.getId(),
                        getFhirResourceTypeString(fhirResource.getData()),
                        fhirResource.getId());
        mMedicalResourceHelper.deleteMedicalResourcesByIds(List.of(id));

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIds(List.of(id));
        assertThat(result).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneOfTwoSpecified_onlySpecifiedDeleted()
            throws Exception {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource1 = getFhirResource();
        UpsertMedicalResourceInternalRequest medicalResource1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource1.getId())
                        .setFhirResourceType(fhirResource1.getType())
                        .setData(fhirResource1.getData())
                        .setDataSourceId(dataSource.getId());
        FhirResource fhirResource2 = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest medicalResource2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource2.getId())
                        .setFhirResourceType(fhirResource2.getType())
                        .setData(fhirResource2.getData())
                        .setDataSourceId(dataSource.getId());

        mMedicalResourceHelper.upsertMedicalResources(List.of(medicalResource1, medicalResource2));

        MedicalResourceId id1 =
                new MedicalResourceId(
                        dataSource.getId(),
                        getFhirResourceTypeString(fhirResource1.getData()),
                        fhirResource1.getId());
        MedicalResourceId id2 =
                new MedicalResourceId(
                        dataSource.getId(),
                        getFhirResourceTypeString(fhirResource2.getData()),
                        fhirResource2.getId());
        mMedicalResourceHelper.deleteMedicalResourcesByIds(List.of(id1));

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIds(List.of(id1, id2));
        assertThat(result)
                .containsExactly(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                        dataSource.getId(),
                                        FHIR_DATA_ALLERGY)
                                .build());
    }

    @Test
    public void getDeleteRequest_noIds_exceptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MedicalResourceHelper.getDeleteRequest(Collections.emptyList()));
    }

    @Test
    public void getDeleteRequest_oneId_success() throws JSONException {
        MedicalResourceId medicalResourceId =
                new MedicalResourceId(DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_IMMUNIZATION, "anId");
        DeleteTableRequest request =
                MedicalResourceHelper.getDeleteRequest(ImmutableList.of(medicalResourceId));
        String hex = makeMedicalResourceHexString(medicalResourceId);

        assertThat(request.getDeleteCommand())
                .isEqualTo("DELETE FROM medical_resource_table WHERE uuid IN (" + hex + ")");
    }

    @Test
    public void getDeleteRequest_multipleId_success() {
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_IMMUNIZATION, "Immunization1");
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        DIFFERENT_DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_ALLERGY, "Allergy1");
        MedicalResourceId medicalResourceId3 =
                new MedicalResourceId(
                        DIFFERENT_DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_ALLERGY, "Allergy2");
        String uuidHex1 = makeMedicalResourceHexString(medicalResourceId1);
        String uuidHex2 = makeMedicalResourceHexString(medicalResourceId2);
        String uuidHex3 = makeMedicalResourceHexString(medicalResourceId3);

        DeleteTableRequest request =
                MedicalResourceHelper.getDeleteRequest(
                        ImmutableList.of(
                                medicalResourceId1, medicalResourceId2, medicalResourceId3));

        assertThat(request.getIds()).isEqualTo(ImmutableList.of(uuidHex1, uuidHex2, uuidHex3));
        assertThat(request.getIdColumnName()).isEqualTo("uuid");
        assertThat(request.getDeleteCommand())
                .isEqualTo(
                        "DELETE FROM medical_resource_table WHERE uuid IN ("
                                + uuidHex1
                                + ", "
                                + uuidHex2
                                + ", "
                                + uuidHex3
                                + ")");
    }

    /**
     * Returns a UUID for the given triple {@code resourceId}, {@code resourceType} and {@code
     * dataSourceId}.
     */
    private static String makeMedicalResourceHexString(MedicalResourceId medicalResourceId) {
        // TODO(b/351138955): Temp use getFhirResourceTypeInt for converting before updating
        // MedicalResourceId to use the IntDef FhirResourceType directly.
        return getHexString(
                generateMedicalResourceUUID(
                        medicalResourceId.getFhirResourceId(),
                        getFhirResourceTypeInt(medicalResourceId.getFhirResourceType()),
                        medicalResourceId.getDataSourceId()));
    }

    private MedicalDataSource insertMedicalDataSource(
            String fhirBaseURI, String displayName, String packageName) {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(fhirBaseURI, displayName).build();
        return mMedicalDataSourceHelper.createMedicalDataSource(
                createMedicalDataSourceRequest, packageName);
    }
}
