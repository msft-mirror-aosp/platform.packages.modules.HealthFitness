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

import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN;
import static android.health.connect.datatypes.FhirVersion.parseFhirVersion;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.addCompletedStatus;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceAllergy;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceBuilder;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceDifferentImmunization;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceId;
import static android.healthconnect.cts.utils.PhrDataFactory.getUpdatedAllergyFhirResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getUpdatedImmunizationFhirResource;

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.DATA_SOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_DATA_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_VERSION_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.MEDICAL_RESOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getCreateTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getMedicalResourceTypeColumnName;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getParentColumnReference;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getTableName;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.LAST_MODIFIED_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.generateMedicalResourceUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getHexString;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.MedicalResourceId;
import android.health.connect.ReadMedicalResourcesRequest;
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

import java.util.ArrayList;
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
    private TransactionManager mTransactionManager;
    private static final long DATA_SOURCE_ROW_ID = 1234;

    @Before
    public void setup() {
        mTransactionManager = mHealthConnectDatabaseTestRule.getTransactionManager();
        mMedicalDataSourceHelper = new MedicalDataSourceHelper(mTransactionManager);
        mMedicalResourceHelper =
                new MedicalResourceHelper(mTransactionManager, mMedicalDataSourceHelper);
    }

    @Test
    public void getCreateTableRequest_correctResult() {
        List<Pair<String, String>> columnInfoMedicalResource =
                List.of(
                        Pair.create(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT),
                        Pair.create(FHIR_RESOURCE_TYPE_COLUMN_NAME, INTEGER_NOT_NULL),
                        Pair.create(FHIR_RESOURCE_ID_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_DATA_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_VERSION_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(DATA_SOURCE_ID_COLUMN_NAME, INTEGER_NOT_NULL),
                        Pair.create(UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL),
                        Pair.create(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER));
        List<Pair<String, String>> columnInfoMedicalResourceIndices =
                List.of(
                        Pair.create(getParentColumnReference(), INTEGER_NOT_NULL),
                        Pair.create(getMedicalResourceTypeColumnName(), INTEGER_NOT_NULL));
        CreateTableRequest childTableRequest =
                new CreateTableRequest(getTableName(), columnInfoMedicalResourceIndices)
                        .addForeignKey(
                                MEDICAL_RESOURCE_TABLE_NAME,
                                Collections.singletonList(getParentColumnReference()),
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getUpsertTableRequest_correctResult() {
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource.getData())
                        .setDataSourceId(DATA_SOURCE_ID);
        UUID uuid =
                generateMedicalResourceUUID(
                        fhirResource.getId(), fhirResource.getType(), DATA_SOURCE_ID);

        UpsertTableRequest upsertRequest =
                MedicalResourceHelper.getUpsertTableRequest(
                        uuid, DATA_SOURCE_ROW_ID, upsertMedicalResourceInternalRequest);
        ContentValues contentValues = upsertRequest.getContentValues();
        UpsertTableRequest childUpsertRequestExpected =
                MedicalResourceIndicesHelper.getChildTableUpsertRequests(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
        UpsertTableRequest childUpsertRequestResult = upsertRequest.getChildTableRequests().get(0);

        assertThat(upsertRequest.getTable()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(upsertRequest.getUniqueColumnsCount()).isEqualTo(1);
        assertThat(contentValues.size()).isEqualTo(6);
        assertThat(contentValues.get(FHIR_RESOURCE_TYPE_COLUMN_NAME))
                .isEqualTo(fhirResource.getType());
        assertThat(contentValues.get(DATA_SOURCE_ID_COLUMN_NAME)).isEqualTo(DATA_SOURCE_ROW_ID);
        assertThat(contentValues.get(FHIR_VERSION_COLUMN_NAME)).isEqualTo(FHIR_VERSION_R4);
        assertThat(contentValues.get(FHIR_DATA_COLUMN_NAME)).isEqualTo(fhirResource.getData());
        assertThat(contentValues.get(UUID_COLUMN_NAME))
                .isEqualTo(StorageUtils.convertUUIDToBytes(uuid));
        assertThat(childUpsertRequestResult.getTable())
                .isEqualTo(childUpsertRequestExpected.getTable());
        assertThat(childUpsertRequestResult.getContentValues())
                .isEqualTo(childUpsertRequestExpected.getContentValues());
        assertThat(childUpsertRequestResult.getUniqueColumnsCount())
                .isEqualTo(childUpsertRequestExpected.getUniqueColumnsCount());
    }

    @Test
    public void getReadTableRequest_usingMedicalResourceId_correctQuery() {
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_IMMUNIZATION, "resourceId1");
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        DIFFERENT_DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_UNKNOWN, "resourceId2");
        List<MedicalResourceId> medicalResourceIds =
                List.of(medicalResourceId1, medicalResourceId2);
        String hex1 = makeMedicalResourceHexString(medicalResourceId1);
        String hex2 = makeMedicalResourceHexString(medicalResourceId2);
        List<String> hexValues = List.of(hex1, hex2);

        ReadTableRequest readRequest =
                MedicalResourceHelper.getReadTableRequestByIds(medicalResourceIds);

        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM ( SELECT * FROM medical_resource_table WHERE uuid IN ("
                                + String.join(", ", hexValues)
                                + ") ) AS inner_query_result  INNER JOIN"
                                + " medical_resource_indices_table ON inner_query_result.row_id ="
                                + " medical_resource_indices_table.medical_resource_id  INNER JOIN"
                                + " medical_data_source_table ON inner_query_result.data_source_id"
                                + " = medical_data_source_table.row_id");
    }

    @Test
    public void getReadTableRequest_usingRequest_correctQuery() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        ReadTableRequest readRequest =
                MedicalResourceHelper.getReadTableRequestUsingRequest(request);

        // TODO(b/352546342): Explore improving the query building logic, so the query below
        // is simpler to read, for context: http://shortn/_2YCniY49K6
        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM ( SELECT * FROM medical_resource_table ) AS"
                                + " inner_query_result  INNER JOIN ( SELECT * FROM"
                                + " medical_resource_indices_table WHERE medical_resource_type = "
                                + "'"
                                + MEDICAL_RESOURCE_TYPE_IMMUNIZATION
                                + "'"
                                + ") medical_resource_indices_table ON inner_query_result.row_id ="
                                + " medical_resource_indices_table.medical_resource_id  INNER JOIN"
                                + " medical_data_source_table ON inner_query_result.data_source_id"
                                + " = medical_data_source_table.row_id");
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readMedicalResourcesByIds_returnsEmpty() throws JSONException {
        List<MedicalResourceId> medicalResourceIds =
                List.of(
                        new MedicalResourceId(
                                DATA_SOURCE_ID,
                                FHIR_RESOURCE_TYPE_IMMUNIZATION,
                                getFhirResourceId(FHIR_DATA_IMMUNIZATION)));

        List<MedicalResource> resources =
                mMedicalResourceHelper.readMedicalResourcesByIds(medicalResourceIds);

        assertThat(resources).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readMedicalResourcesByRequest_dbEmpty_returnsEmpty() {
        ReadMedicalResourcesRequest readImmunizationRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();

        List<MedicalResource> resources =
                mMedicalResourceHelper.readMedicalResourcesByRequest(readImmunizationRequest);

        assertThat(resources).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void upsertAndreadMedicalResourcesByRequest_MedicalResourceTypeDoesNotExist_success() {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertImmunizationResourceRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());

        List<MedicalResource> upsertedResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(upsertImmunizationResourceRequest));
        ReadMedicalResourcesRequest readUnknownRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_UNKNOWN).build();
        List<MedicalResource> resourcesWithUnknownResourceType =
                mMedicalResourceHelper.readMedicalResourcesByRequest(readUnknownRequest);

        assertThat(upsertedResources)
                .containsExactly(
                        new MedicalResource.Builder(
                                        fhirResource.getType(), dataSource.getId(), fhirResource)
                                .build());
        assertThat(resourcesWithUnknownResourceType).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void upsertMedicalResourcesSameDataSource_readMedicalResourcesByRequest_success() {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        MedicalResource immunization =
                new MedicalResource.Builder(
                                fhirResource.getType(), dataSource.getId(), fhirResource)
                        .build();
        FhirResource differentFhirResource = getFhirResourceDifferentImmunization();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(differentFhirResource.getId())
                        .setFhirResourceType(differentFhirResource.getType())
                        .setData(differentFhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        MedicalResource differentImmunization =
                new MedicalResource.Builder(
                                differentFhirResource.getType(),
                                dataSource.getId(),
                                differentFhirResource)
                        .build();
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest3 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(allergyFhirResource.getId())
                        .setFhirResourceType(allergyFhirResource.getType())
                        .setData(allergyFhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        MedicalResource allergy =
                new MedicalResource.Builder(
                                allergyFhirResource.getType(),
                                dataSource.getId(),
                                allergyFhirResource)
                        .build();
        List<MedicalResource> upsertedResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2,
                                upsertMedicalResourceInternalRequest3));

        ReadMedicalResourcesRequest readUnknownRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_UNKNOWN).build();
        ReadMedicalResourcesRequest readImmunizationRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        List<MedicalResource> resourcesWithUnknownResourceTypes =
                mMedicalResourceHelper.readMedicalResourcesByRequest(readUnknownRequest);
        List<MedicalResource> resourcesWithImmunizationResourceType =
                mMedicalResourceHelper.readMedicalResourcesByRequest(readImmunizationRequest);

        assertThat(upsertedResources)
                .containsExactlyElementsIn(List.of(immunization, differentImmunization, allergy));
        assertThat(resourcesWithUnknownResourceTypes).containsExactlyElementsIn(List.of(allergy));
        assertThat(resourcesWithImmunizationResourceType)
                .containsExactlyElementsIn(List.of(immunization, differentImmunization));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void upsertMedicalResourcesDifferentDataSources_readMedicalResourcesByRequest_success() {
        MedicalDataSource dataSource1 =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                insertMedicalDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource1.getId());
        MedicalResource immunization =
                new MedicalResource.Builder(
                                fhirResource.getType(), dataSource1.getId(), fhirResource)
                        .build();
        FhirResource differentFhirResource = getFhirResourceDifferentImmunization();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(differentFhirResource.getId())
                        .setFhirResourceType(differentFhirResource.getType())
                        .setData(differentFhirResource.getData())
                        .setDataSourceId(dataSource2.getId());
        MedicalResource differentImmunization =
                new MedicalResource.Builder(
                                differentFhirResource.getType(),
                                dataSource2.getId(),
                                differentFhirResource)
                        .build();
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest3 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(allergyFhirResource.getId())
                        .setFhirResourceType(allergyFhirResource.getType())
                        .setData(allergyFhirResource.getData())
                        .setDataSourceId(dataSource1.getId());
        MedicalResource allergy =
                new MedicalResource.Builder(
                                allergyFhirResource.getType(),
                                dataSource1.getId(),
                                allergyFhirResource)
                        .build();
        List<MedicalResource> upsertedResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2,
                                upsertMedicalResourceInternalRequest3));

        ReadMedicalResourcesRequest readUnknownRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_UNKNOWN).build();
        ReadMedicalResourcesRequest readImmunizationRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        List<MedicalResource> resourcesWithUnknownResourceTypes =
                mMedicalResourceHelper.readMedicalResourcesByRequest(readUnknownRequest);
        List<MedicalResource> resourcesWithImmunizationResourceType =
                mMedicalResourceHelper.readMedicalResourcesByRequest(readImmunizationRequest);

        assertThat(upsertedResources)
                .containsExactlyElementsIn(List.of(immunization, differentImmunization, allergy));
        assertThat(resourcesWithUnknownResourceTypes).containsExactlyElementsIn(List.of(allergy));
        assertThat(resourcesWithImmunizationResourceType)
                .containsExactlyElementsIn(List.of(immunization, differentImmunization));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMedicalResources_dataSourceNotInserted_exceptionThrown() {
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
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
    public void readSubsetOfResources_multipleResourcesUpserted_success() throws JSONException {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(FHIR_DATA_IMMUNIZATION)
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                fhirResource)
                        .build();
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource.getType(), fhirResource.getId());
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(allergyFhirResource.getId())
                        .setFhirResourceType(allergyFhirResource.getType())
                        .setData(FHIR_DATA_ALLERGY)
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                allergyFhirResource)
                        .build();
        List<Integer> expectedIndices =
                List.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> readResult =
                mMedicalResourceHelper.readMedicalResourcesByIds(List.of(medicalResourceId1));
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(upsertedMedicalResources)
                .containsExactlyElementsIn(List.of(resource1, resource2));
        assertThat(indicesResult.size()).isEqualTo(expectedIndices.size());
        assertThat(indicesResult).containsExactlyElementsIn(expectedIndices);
        assertThat(readResult.size()).isEqualTo(1);
        assertThat(readResult).containsExactlyElementsIn(List.of(resource1));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMedicalResources_returnsMedicalResources() {
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
                                        fhirResource)
                                .build());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
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
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        List<MedicalResourceId> ids =
                List.of(
                        new MedicalResourceId(
                                dataSource.getId(), fhirResource.getType(), fhirResource.getId()));
        List<MedicalResource> expected =
                Collections.singletonList(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        dataSource.getId(),
                                        fhirResource)
                                .build());
        List<Integer> expectedIndices = List.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(upsertMedicalResourceInternalRequest));
        List<MedicalResource> result = mMedicalResourceHelper.readMedicalResourcesByIds(ids);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).isEqualTo(upsertedMedicalResources);
        assertThat(result).isEqualTo(expected);
        assertThat(indicesResult.size()).isEqualTo(expectedIndices.size());
        assertThat(indicesResult).isEqualTo(expectedIndices);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResourcesWithSameDataSource_readMultipleResources()
            throws JSONException {
        // TODO(b/351992434): Create test utilities to make these large repeated code blocks
        // clearer.
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
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource1.getData())
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                fhirResource1)
                        .build();
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource1.getType(), fhirResource1.getId());
        FhirResource fhirResource2 = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource2.getId())
                        .setFhirResourceType(fhirResource2.getType())
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource2.getData())
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN, dataSource.getId(), fhirResource2)
                        .build();
        List<MedicalResource> expected = List.of(resource1, resource2);
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource2.getType(), fhirResource2.getId());
        List<MedicalResourceId> ids = List.of(medicalResourceId1, medicalResourceId2);
        new MedicalResourceId(dataSource.getId(), fhirResource2.getType(), fhirResource2.getId());
        List<Integer> expectedIndices =
                List.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> result = mMedicalResourceHelper.readMedicalResourcesByIds(ids);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(upsertedMedicalResources);
        assertThat(result).containsExactlyElementsIn(expected);
        assertThat(indicesResult.size()).isEqualTo(expectedIndices.size());
        assertThat(indicesResult).isEqualTo(expectedIndices);
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
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource1.getId());
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource1.getId(),
                                fhirResource)
                        .build();
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        dataSource1.getId(), fhirResource.getType(), fhirResource.getId());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource2.getId());
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource2.getId(),
                                fhirResource)
                        .build();
        List<MedicalResource> expected = List.of(resource1, resource2);
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        dataSource2.getId(), fhirResource.getType(), fhirResource.getId());
        List<MedicalResourceId> ids = List.of(medicalResourceId1, medicalResourceId2);
        List<Integer> expectedIndices =
                List.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_IMMUNIZATION);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));

        List<MedicalResource> result = mMedicalResourceHelper.readMedicalResourcesByIds(ids);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(upsertedMedicalResources);
        assertThat(result).containsExactlyElementsIn(expected);
        assertThat(indicesResult.size()).isEqualTo(expectedIndices.size());
        assertThat(indicesResult).isEqualTo(expectedIndices);
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
        FhirResource fhirResourceUpdated =
                getFhirResourceBuilder()
                        .setData(addCompletedStatus(getFhirResource().getData()))
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResourceUpdated.getData())
                        .setDataSourceId(dataSource.getId());
        List<MedicalResourceId> ids =
                List.of(
                        new MedicalResourceId(
                                dataSource.getId(), fhirResource.getType(), fhirResource.getId()));
        List<MedicalResource> expected =
                Collections.singletonList(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        dataSource.getId(),
                                        fhirResourceUpdated)
                                .build());
        List<Integer> expectedIndices = List.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);

        mMedicalResourceHelper.upsertMedicalResources(
                List.of(upsertMedicalResourceInternalRequest));
        List<MedicalResource> updatedMedicalResource =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(upsertMedicalResourceInternalRequestUpdated));
        List<MedicalResource> result = mMedicalResourceHelper.readMedicalResourcesByIds(ids);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).isEqualTo(updatedMedicalResource);
        assertThat(result).isEqualTo(expected);
        assertThat(indicesResult.size()).isEqualTo(expectedIndices.size());
        assertThat(indicesResult).isEqualTo(expectedIndices);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_updateSingleMedicalResource_success()
            throws JSONException {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(FHIR_DATA_IMMUNIZATION)
                        .setDataSourceId(dataSource.getId());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(addCompletedStatus(FHIR_DATA_IMMUNIZATION))
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                fhirResource)
                        .build();
        FhirResource updatedFhirResource = getUpdatedImmunizationFhirResource();
        MedicalResource updatedResource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                updatedFhirResource)
                        .build();
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource.getType(), fhirResource.getId());
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(allergyFhirResource.getId())
                        .setFhirResourceType(allergyFhirResource.getType())
                        .setData(FHIR_DATA_ALLERGY)
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                allergyFhirResource)
                        .build();
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        dataSource.getId(),
                        allergyFhirResource.getType(),
                        allergyFhirResource.getId());
        List<MedicalResource> expected = List.of(updatedResource1, resource2);
        List<MedicalResourceId> medicalIdFilters = List.of(medicalResourceId1, medicalResourceId2);
        List<Integer> expectedIndices =
                List.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> updatedMedicalResource =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(upsertMedicalResourceInternalRequestUpdated1));
        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIds(medicalIdFilters);
        List<Integer> indicesResult =
                List.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);

        assertThat(upsertedMedicalResources)
                .containsExactlyElementsIn(List.of(resource1, resource2));
        assertThat(updatedMedicalResource).isEqualTo(List.of(updatedResource1));
        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(expected);
        assertThat(indicesResult.size()).isEqualTo(expectedIndices.size());
        assertThat(indicesResult).isEqualTo(expectedIndices);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_updateMultipleMedicalResources_success()
            throws JSONException {
        MedicalDataSource dataSource =
                insertMedicalDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(FHIR_DATA_IMMUNIZATION)
                        .setDataSourceId(dataSource.getId());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated1 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource.getId())
                        .setFhirResourceType(fhirResource.getType())
                        .setData(addCompletedStatus(FHIR_DATA_IMMUNIZATION))
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                fhirResource)
                        .build();
        FhirResource updatedFhirResource = getUpdatedImmunizationFhirResource();
        MedicalResource updatedResource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                updatedFhirResource)
                        .build();
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource.getType(), fhirResource.getId());
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(allergyFhirResource.getId())
                        .setFhirResourceType(allergyFhirResource.getType())
                        .setData(FHIR_DATA_ALLERGY)
                        .setDataSourceId(dataSource.getId());
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(allergyFhirResource.getId())
                        .setFhirResourceType(allergyFhirResource.getType())
                        .setData(addCompletedStatus(FHIR_DATA_ALLERGY))
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                allergyFhirResource)
                        .build();
        FhirResource updatedAllergyFhirResource = getUpdatedAllergyFhirResource();
        MedicalResource updatedResource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                updatedAllergyFhirResource)
                        .build();
        MedicalResourceId medicalResourceId2 =
                new MedicalResourceId(
                        dataSource.getId(),
                        allergyFhirResource.getType(),
                        allergyFhirResource.getId());
        List<MedicalResource> expected = List.of(updatedResource1, updatedResource2);
        List<MedicalResourceId> medicalIdFilters = List.of(medicalResourceId1, medicalResourceId2);
        List<Integer> expectedIndices =
                List.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> updatedMedicalResource =
                mMedicalResourceHelper.upsertMedicalResources(
                        List.of(
                                upsertMedicalResourceInternalRequestUpdated1,
                                upsertMedicalResourceInternalRequestUpdated2));
        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIds(medicalIdFilters);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(upsertedMedicalResources)
                .containsExactlyElementsIn(List.of(resource1, resource2));
        assertThat(updatedMedicalResource).isEqualTo(List.of(updatedResource1, updatedResource2));
        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(expected);
        assertThat(indicesResult.size()).isEqualTo(expectedIndices.size());
        assertThat(indicesResult).isEqualTo(expectedIndices);
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
        String fhirResourceId = getFhirResourceId(FHIR_DATA_IMMUNIZATION);

        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                List.of(
                        new MedicalResourceId(
                                DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_IMMUNIZATION, fhirResourceId)));
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
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource.getData())
                        .setDataSourceId(dataSource.getId());
        mMedicalResourceHelper.upsertMedicalResources(
                List.of(upsertMedicalResourceInternalRequest));

        MedicalResourceId id =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource.getType(), fhirResource.getId());
        mMedicalResourceHelper.deleteMedicalResourcesByIds(List.of(id));

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIds(List.of(id));
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result).isEmpty();
        assertThat(indicesResult).isEmpty();
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
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource1.getData())
                        .setDataSourceId(dataSource.getId());
        FhirResource fhirResource2 = getFhirResourceAllergy();
        UpsertMedicalResourceInternalRequest medicalResource2 =
                new UpsertMedicalResourceInternalRequest()
                        .setFhirResourceId(fhirResource2.getId())
                        .setFhirResourceType(fhirResource2.getType())
                        .setFhirVersion(parseFhirVersion(FHIR_VERSION_R4))
                        .setData(fhirResource2.getData())
                        .setDataSourceId(dataSource.getId());

        mMedicalResourceHelper.upsertMedicalResources(List.of(medicalResource1, medicalResource2));

        MedicalResourceId id1 =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource1.getType(), fhirResource1.getId());
        MedicalResourceId id2 =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource2.getType(), fhirResource2.getId());
        mMedicalResourceHelper.deleteMedicalResourcesByIds(List.of(id1));
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIds(List.of(id1, id2));
        assertThat(result)
                .containsExactly(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                        dataSource.getId(),
                                        fhirResource2)
                                .build());
        assertThat(indicesResult).containsExactly(MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    public void getDeleteRequest_noIds_exceptions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MedicalResourceHelper.getDeleteRequest(Collections.emptyList()));
    }

    @Test
    public void getDeleteRequest_oneId_success() {
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
                        DIFFERENT_DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_UNKNOWN, "Allergy1");
        MedicalResourceId medicalResourceId3 =
                new MedicalResourceId(
                        DIFFERENT_DATA_SOURCE_ID, FHIR_RESOURCE_TYPE_UNKNOWN, "Allergy2");
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
        return getHexString(
                generateMedicalResourceUUID(
                        medicalResourceId.getFhirResourceId(),
                        medicalResourceId.getFhirResourceType(),
                        medicalResourceId.getDataSourceId()));
    }

    private MedicalDataSource insertMedicalDataSource(
            String fhirBaseURI, String displayName, String packageName) {
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(fhirBaseURI, displayName).build();
        return mMedicalDataSourceHelper.createMedicalDataSource(
                createMedicalDataSourceRequest, packageName);
    }

    private List<Integer> readEntriesInMedicalResourceIndicesTable() {
        List<Integer> medicalResourceTypes = new ArrayList<>();
        ReadTableRequest readTableRequest = new ReadTableRequest(getTableName());
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            if (cursor.moveToFirst()) {
                do {
                    medicalResourceTypes.add(
                            getCursorInt(cursor, getMedicalResourceTypeColumnName()));
                } while (cursor.moveToNext());
            }
            return medicalResourceTypes;
        }
    }
}
