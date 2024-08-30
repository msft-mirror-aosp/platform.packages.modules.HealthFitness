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

import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_READ;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_UPSERT;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_UNKNOWN;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_UNKNOWN;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_ID_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.R4_VERSION_STRING;
import static android.healthconnect.cts.utils.PhrDataFactory.addCompletedStatus;
import static android.healthconnect.cts.utils.PhrDataFactory.createAllergyMedicalResource;
import static android.healthconnect.cts.utils.PhrDataFactory.createImmunizationMedicalResource;
import static android.healthconnect.cts.utils.PhrDataFactory.createImmunizationMedicalResources;
import static android.healthconnect.cts.utils.PhrDataFactory.createUpdatedImmunizationMedicalResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceAllergy;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceBuilder;
import static android.healthconnect.cts.utils.PhrDataFactory.getFhirResourceDifferentImmunization;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResourceId;
import static android.healthconnect.cts.utils.PhrDataFactory.getUpdatedAllergyFhirResource;
import static android.healthconnect.cts.utils.PhrDataFactory.getUpdatedImmunizationFhirResource;

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.DATA_SOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_DATA_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_VERSION_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.MEDICAL_RESOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getCreateTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getPrimaryColumn;
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
import android.health.connect.HealthConnectManager;
import android.health.connect.MedicalResourceId;
import android.health.connect.ReadMedicalResourcesRequest;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.FhirVersion;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.utils.PhrDataFactory;
import android.os.Environment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.phr.PhrPageTokenWrapper;
import com.android.server.healthconnect.phr.ReadMedicalResourcesInternalResponse;
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
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MedicalResourceHelperTest {

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

    private static final long DATA_SOURCE_ROW_ID = 1234;
    private static final String INVALID_PAGE_TOKEN = "aw==";

    private MedicalResourceHelper mMedicalResourceHelper;
    private MedicalDataSourceHelper mMedicalDataSourceHelper;
    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private AppInfoHelper mAppInfoHelper;
    private HealthConnectUserContext mContext;

    @Before
    public void setup() {
        mTransactionManager = mHealthConnectDatabaseTestRule.getTransactionManager();
        mContext = mHealthConnectDatabaseTestRule.getUserContext();
        mTransactionTestUtils = new TransactionTestUtils(mContext, mTransactionManager);
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        mTransactionTestUtils.insertApp(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mAppInfoHelper = AppInfoHelper.getInstance();

        mMedicalDataSourceHelper = new MedicalDataSourceHelper(mTransactionManager, mAppInfoHelper);
        mMedicalResourceHelper =
                new MedicalResourceHelper(mTransactionManager, mMedicalDataSourceHelper);
    }

    @Test
    public void getCreateTableRequest_correctResult() {
        List<Pair<String, String>> columnInfoMedicalResource =
                List.of(
                        Pair.create(getPrimaryColumn(), PRIMARY_AUTOINCREMENT),
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
                                Collections.singletonList(getPrimaryColumn()));
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void getUpsertTableRequest_correctResult() {
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                makeUpsertRequest(
                        fhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_VERSION_R4,
                        DATA_SOURCE_ID);
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
        assertThat(contentValues.get(FHIR_VERSION_COLUMN_NAME)).isEqualTo(R4_VERSION_STRING);
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
                MedicalResourceHelper.getReadTableRequestByIdsJoinWithIndicesAndDataSourceTables(
                        medicalResourceIds);

        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM ( SELECT * FROM medical_resource_table WHERE uuid IN ("
                                + String.join(", ", hexValues)
                                + ") ) AS inner_query_result  INNER JOIN"
                                + " medical_resource_indices_table ON"
                                + " inner_query_result.medical_resource_row_id ="
                                + " medical_resource_indices_table.medical_resource_id  INNER JOIN"
                                + " medical_data_source_table ON inner_query_result.data_source_id"
                                + " = medical_data_source_table.row_id");
    }

    @Test
    public void getReadTableRequest_usingRequest_correctQuery() {
        ReadMedicalResourcesRequest request =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        ReadTableRequest readRequest =
                MedicalResourceHelper.getReadTableRequestUsingRequestFilterOnMedicalResourceTypes(
                        request);

        // TODO(b/352546342): Explore improving the query building logic, so the query below
        // is simpler to read, for context: http://shortn/_2YCniY49K6
        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM ( SELECT * FROM medical_resource_table ORDER BY"
                                + " medical_resource_row_id LIMIT "
                                + (DEFAULT_PAGE_SIZE + 1)
                                + " ) AS"
                                + " inner_query_result  INNER JOIN ( SELECT * FROM"
                                + " medical_resource_indices_table WHERE medical_resource_type IN "
                                + "("
                                + MEDICAL_RESOURCE_TYPE_IMMUNIZATION
                                + ")"
                                + ") medical_resource_indices_table ON"
                                + " inner_query_result.medical_resource_row_id ="
                                + " medical_resource_indices_table.medical_resource_id  INNER JOIN"
                                + " medical_data_source_table ON inner_query_result.data_source_id"
                                + " = medical_data_source_table.row_id");
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readMedicalResourcesByIds_WithoutPermissionChecks_returnsEmpty() {
        List<MedicalResourceId> medicalResourceIds =
                List.of(
                        new MedicalResourceId(
                                DATA_SOURCE_ID,
                                FHIR_RESOURCE_TYPE_IMMUNIZATION,
                                FHIR_RESOURCE_ID_IMMUNIZATION));

        List<MedicalResource> resources =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        medicalResourceIds);

        assertThat(resources).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readMedicalResourcesByRequest_dbEmpty_returnsEmpty() {
        ReadMedicalResourcesRequest readImmunizationRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();

        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readImmunizationRequest);

        assertThat(result.getMedicalResources()).isEmpty();
        assertThat(result.getPageToken()).isEqualTo(null);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void upsertAndReadMedicalResourcesByRequest_MedicalResourceTypeDoesNotExist_success() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertImmunizationResourceRequest =
                makeUpsertRequest(
                        fhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_VERSION_R4,
                        dataSource.getId());

        List<MedicalResource> upsertedResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, List.of(upsertImmunizationResourceRequest));
        ReadMedicalResourcesRequest readUnknownRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_UNKNOWN).build();
        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readUnknownRequest);

        assertThat(upsertedResources)
                .containsExactly(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                        dataSource.getId(),
                                        FHIR_VERSION_R4,
                                        fhirResource)
                                .build());
        assertThat(result.getMedicalResources()).isEmpty();
        assertThat(result.getPageToken()).isEqualTo(null);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void upsertMedicalResourcesSameDataSource_readMedicalResourcesByRequest_success() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource immunization =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(immunization);
        FhirResource differentFhirResource = getFhirResourceDifferentImmunization();
        MedicalResource differentImmunization =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                differentFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(differentImmunization);
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource allergy =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                allergyFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest3 =
                makeUpsertRequest(allergy);
        List<MedicalResource> upsertedResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2,
                                upsertMedicalResourceInternalRequest3));

        ReadMedicalResourcesRequest readUnknownRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_UNKNOWN).build();
        ReadMedicalResourcesRequest readImmunizationRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        ReadMedicalResourcesInternalResponse unknownResourcesResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readUnknownRequest);
        ReadMedicalResourcesInternalResponse immunizationResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readImmunizationRequest);

        assertThat(upsertedResources).containsExactly(immunization, differentImmunization, allergy);
        assertThat(unknownResourcesResult.getMedicalResources()).containsExactly(allergy);
        assertThat(unknownResourcesResult.getPageToken()).isEqualTo(null);
        assertThat(immunizationResult.getMedicalResources())
                .containsExactly(immunization, differentImmunization);
        assertThat(immunizationResult.getPageToken()).isEqualTo(null);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void upsertMedicalResourcesDifferentDataSources_readMedicalResourcesByRequest_success() {
        MedicalDataSource dataSource1 = insertMedicalDataSource("id1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                insertMedicalDataSource("id2", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource immunization =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource1.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(immunization);
        FhirResource differentFhirResource = getFhirResourceDifferentImmunization();
        MedicalResource differentImmunization =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource2.getId(),
                                FHIR_VERSION_R4,
                                differentFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(differentImmunization);
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource allergy =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource1.getId(),
                                FHIR_VERSION_R4,
                                allergyFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest3 =
                makeUpsertRequest(allergy);
        List<MedicalResource> upsertedResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2,
                                upsertMedicalResourceInternalRequest3));

        ReadMedicalResourcesRequest readUnknownRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_UNKNOWN).build();
        ReadMedicalResourcesRequest readImmunizationRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        ReadMedicalResourcesInternalResponse unknownResourcesResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readUnknownRequest);
        ReadMedicalResourcesInternalResponse immunizationResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readImmunizationRequest);

        assertThat(upsertedResources).containsExactly(immunization, differentImmunization, allergy);
        assertThat(unknownResourcesResult.getMedicalResources()).containsExactly(allergy);
        assertThat(unknownResourcesResult.getPageToken()).isEqualTo(null);
        assertThat(immunizationResult.getMedicalResources())
                .containsExactly(immunization, differentImmunization);
        assertThat(immunizationResult.getPageToken()).isEqualTo(null);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void insertMedicalResources_dataSourceNotInserted_exceptionThrown() {
        FhirResource fhirResource = getFhirResource();
        String datasourceId = "acc6c726-b7ea-42f1-a063-e34f5b4e6247";
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                makeUpsertRequest(
                        fhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_VERSION_R4,
                        datasourceId);

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mMedicalResourceHelper.upsertMedicalResources(
                                        DATA_SOURCE_PACKAGE_NAME,
                                        List.of(upsertMedicalResourceInternalRequest)));
        assertThat(thrown)
                .hasMessageThat()
                .contains(
                        "Invalid data source id: "
                                + upsertMedicalResourceInternalRequest.getDataSourceId());
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readSubsetOfResources_multipleResourcesUpserted_success() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(resource1);
        MedicalResourceId medicalResourceId1 =
                new MedicalResourceId(
                        dataSource.getId(), fhirResource.getType(), fhirResource.getId());
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                allergyFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(resource2);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> readResult =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResourceId1));
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(upsertedMedicalResources).containsExactly(resource1, resource2);
        assertThat(indicesResult)
                .containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);
        assertThat(readResult).containsExactly(resource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void insertMedicalResources_returnsMedicalResources() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource expectedResource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                makeUpsertRequest(expectedResource);

        List<MedicalResource> result =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, List.of(upsertMedicalResourceInternalRequest));

        assertThat(result).containsExactly(expectedResource);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void insertSingleMedicalResource_readSingleResource() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource expectedResource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                makeUpsertRequest(expectedResource);
        List<MedicalResourceId> ids = List.of(expectedResource.getId());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, List.of(upsertMedicalResourceInternalRequest));
        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(ids);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result).isEqualTo(upsertedMedicalResources);
        assertThat(result).containsExactly(expectedResource);
        assertThat(indicesResult).containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_noReadOrWritePermissions_throws() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                                List.of(),
                                /* grantedReadMedicalResourceTypes= */ Set.of(),
                                DATA_SOURCE_PACKAGE_NAME,
                                /* hasWritePermission= */ false,
                                /* isCalledFromBgWithoutBgRead= */ false));
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inBgWithoutBgPerm_hasWritePerm_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(AccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inBgWithoutBgPerm_hasWritePerm_hasReadPermForResourceTypes_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(AccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inBgWithoutBgPerm_noWritePerm_immunizationReadPermOnly_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ false,
                /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(AccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsOnlyContainsNonSelfRead() {
        MedicalDataSource dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationPackage1 =
                createImmunizationMedicalResource(dataSource1.getId());
        MedicalResource immunizationPackage2 =
                createImmunizationMedicalResource(dataSource2.getId());
        MedicalResource unknownResourcePackage2 = createAllergyMedicalResource(dataSource2.getId());

        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME, List.of(makeUpsertRequest(immunizationPackage1)));
        mMedicalResourceHelper.upsertMedicalResources(
                DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                List.of(
                        makeUpsertRequest(immunizationPackage2),
                        makeUpsertRequest(unknownResourcePackage2)));
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(
                        immunizationPackage1.getId(),
                        immunizationPackage2.getId(),
                        unknownResourcePackage2.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_UNKNOWN read permission.
        // has write permission.
        // The data that the calling app can read: immunizationPackage1 (through selfRead)
        // unknownResourcePackage2 (through read permission)
        // In this case, read access log is only created for non self read data:
        // MEDICAL_RESOURCE_TYPE_UNKNOWN.
        List<AccessLog> accessLogs = sortByAccessTime(AccessLogsHelper.queryAccessLogs());
        AccessLog accessLog1 = accessLogs.get(0);
        AccessLog accessLog2 = accessLogs.get(1);
        AccessLog accessLog3 = accessLogs.get(2);

        assertThat(accessLogs).hasSize(3);
        assertThat(accessLog1.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog1.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog1.getRecordTypes()).isEmpty();
        assertThat(accessLog1.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog1.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog1.getAccessTime()).isNotNull();

        assertThat(accessLog2.getPackageName()).isEqualTo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog2.getMedicalResourceTypes())
                .isEqualTo(
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN));
        assertThat(accessLog2.getRecordTypes()).isEmpty();
        assertThat(accessLog2.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog2.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog2.getAccessTime()).isNotNull();

        assertThat(accessLog3.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog3.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN));
        assertThat(accessLog3.getRecordTypes()).isEmpty();
        assertThat(accessLog3.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog3.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog3.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsWhenAppHasNoWritePermHasReadPermButReadOnlySelfData() {
        MedicalDataSource dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationPackage1 =
                createImmunizationMedicalResource(dataSource1.getId());
        MedicalResource unknownResourcePackage2 = createAllergyMedicalResource(dataSource2.getId());

        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME, List.of(makeUpsertRequest(immunizationPackage1)));
        mMedicalResourceHelper.upsertMedicalResources(
                DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                List.of(makeUpsertRequest(unknownResourcePackage2)));
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunizationPackage1.getId(), unknownResourcePackage2.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ false,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_IMMUNIZATION read permission.
        // no write permission.
        // The data that the calling app can read: immunizationPackage1 (through read permission)
        // In this case, read access log is created based on the intention of the app
        // even though the actual data accessed is self data: MEDICAL_RESOURCE_TYPE_IMMUNIZATION.
        List<AccessLog> accessLogs = sortByAccessTime(AccessLogsHelper.queryAccessLogs());
        AccessLog accessLog1 = accessLogs.get(0);
        AccessLog accessLog2 = accessLogs.get(1);
        AccessLog accessLog3 = accessLogs.get(2);

        assertThat(accessLogs).hasSize(3);
        assertThat(accessLog1.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog1.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog1.getRecordTypes()).isEmpty();
        assertThat(accessLog1.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog1.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog1.getAccessTime()).isNotNull();

        assertThat(accessLog2.getPackageName()).isEqualTo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog2.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN));
        assertThat(accessLog2.getRecordTypes()).isEmpty();
        assertThat(accessLog2.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog2.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog2.getAccessTime()).isNotNull();

        assertThat(accessLog3.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog3.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog3.getRecordTypes()).isEmpty();
        assertThat(accessLog3.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog3.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog3.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsWhenAppHasNoWritePermHasReadPermReadNonSelfData() {
        String dataSource =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDifferentPackage =
                createImmunizationMedicalResource(dataSource);
        mMedicalResourceHelper.upsertMedicalResources(
                DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                List.of(makeUpsertRequest(immunizationDifferentPackage)));
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunizationDifferentPackage.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ false,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_IMMUNIZATION read permission.
        // no write permission.
        // The data that the calling app can read: immunization (through read permission)
        // In this case, read access log is created: MEDICAL_RESOURCE_TYPE_IMMUNIZATION.
        List<AccessLog> accessLogs = sortByAccessTime(AccessLogsHelper.queryAccessLogs());
        AccessLog accessLog1 = accessLogs.get(0);
        AccessLog accessLog2 = accessLogs.get(1);

        assertThat(accessLogs).hasSize(2);
        assertThat(accessLog1.getPackageName()).isEqualTo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog1.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog1.getRecordTypes()).isEmpty();
        assertThat(accessLog1.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog1.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog1.getAccessTime()).isNotNull();

        assertThat(accessLog2.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog2.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog2.getRecordTypes()).isEmpty();
        assertThat(accessLog2.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog2.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog2.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inForegroundOrBgWithPerm_hasReadImmunization_noResourceRead_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(AccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inForegroundOrBgWithPerm_hasWritePerm_noReadPerm_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        // No access log should be created since app is intending to access self data as it has
        // no read permissions.
        assertThat(AccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsWhenAppHasWritePermHasReadPermReadSelfData() {
        String dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunization =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource);
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunization.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_IMMUNIZATION read permission.
        // has write permission.
        // The data that the calling app can read: immunization (through read permission)
        // In this case, read access log is created based on the intention of the app
        // even though the actual data accessed is self data: MEDICAL_RESOURCE_TYPE_IMMUNIZATION.
        List<AccessLog> accessLogs = sortByAccessTime(AccessLogsHelper.queryAccessLogs());
        AccessLog accessLog1 = accessLogs.get(0);
        AccessLog accessLog2 = accessLogs.get(1);

        assertThat(accessLogs).hasSize(2);
        assertThat(accessLog1.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog1.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog1.getRecordTypes()).isEmpty();
        assertThat(accessLog1.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog1.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog1.getAccessTime()).isNotNull();

        assertThat(accessLog2.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog2.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog2.getRecordTypes()).isEmpty();
        assertThat(accessLog2.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog2.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog2.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsForEachResourceTypeReadBasedOnReadPerm() {
        String dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunization =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource);
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunization.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN, MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_IMMUNIZATION and MEDICAL_RESOURCE_TYPE_UNKNOWN read permission.
        // has write permission.
        // The data that the calling app reads: immunization (through read permission)
        // In this case, read access log is created only for: MEDICAL_RESOURCE_TYPE_IMMUNIZATION.
        // Even though the app has read permission for MEDICAL_RESOURCE_TYPE_UNKNOWN, the app did
        // not read any data of that type, so no access logs added for that.
        List<AccessLog> accessLogs = sortByAccessTime(AccessLogsHelper.queryAccessLogs());
        AccessLog accessLog1 = accessLogs.get(0);
        AccessLog accessLog2 = accessLogs.get(1);

        assertThat(accessLogs).hasSize(2);
        assertThat(accessLog1.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog1.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog1.getRecordTypes()).isEmpty();
        assertThat(accessLog1.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog1.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog1.getAccessTime()).isNotNull();

        assertThat(accessLog2.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog2.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog2.getRecordTypes()).isEmpty();
        assertThat(accessLog2.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog2.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog2.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_inBgWithoutBgPerm_hasWritePerm_success() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(immunizationDatasource1, allergyDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_inBgWithoutBgPerm_hasWritePerm_hasReadPermForResourceTypes_success() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(immunizationDatasource1, allergyDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_inBgWithoutBgPerm_noWritePerm_immunizationReadPermOnly_success() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(immunizationDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void
            readById_inBgWithoutBgPerm_noWritePerm_bothUnknownAndImmunizationReadPerm_success() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN, MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(immunizationDatasource1, allergyDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_inForegroundOrinBgWithBgPerm_noWritePerm_success() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(immunizationDatasource1, immunizationDatasource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_inForeground_hasWritePerm_noReadResourceTypesPerm_canOnlyReadSelfData() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(immunizationDatasource1, allergyDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_inForeground_noWritePerm_readImmunizationPerm_canOnlyReadImmunization() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(immunizationDatasource1, immunizationDatasource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_inForeground_noWritePerm_readAllergyPerm_canOnlyReadAllergy() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(allergyDatasource1, allergyDatasource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readById_inForeground_noWritePerm_readImmunizationAndAllergyPerm_canReadBoth() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN, MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result)
                .containsExactly(
                        immunizationDatasource1,
                        immunizationDatasource2,
                        allergyDatasource1,
                        allergyDatasource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void
            readById_inForeground_hasWritePermAndReadImmunization_readsSelfDataAndImmunizations() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDatasource1 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result)
                .containsExactly(
                        immunizationDatasource1, immunizationDatasource2, allergyDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readByRequest_isNotEnforceSelfRead_createsAccessLog() {
        ReadMedicalResourcesRequest readRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                readRequest, DATA_SOURCE_PACKAGE_NAME, /* enforceSelfRead= */ false);

        List<AccessLog> accessLogs = AccessLogsHelper.queryAccessLogs();
        AccessLog accessLog = accessLogs.get(0);

        assertThat(accessLogs).hasSize(1);
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_READ);
        assertThat(accessLog.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readByRequest_isEnforceSelfRead_doesNotCreateAccessLog() {
        ReadMedicalResourcesRequest readRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION).build();
        mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                readRequest, DATA_SOURCE_PACKAGE_NAME, /* enforceSelfRead= */ true);

        List<AccessLog> accessLogs = AccessLogsHelper.queryAccessLogs();

        assertThat(accessLogs).hasSize(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readByRequest_isNotEnforceSelfRead_immunizationFilter_canReadAllImmunizations() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        List<MedicalResource> immunizationsDataSource1 =
                upsertResources(
                        PhrDataFactory::createImmunizationMedicalResources,
                        /* numOfResources= */ 4,
                        dataSource1);
        List<MedicalResource> immunizationsDataSource2 =
                upsertResources(
                        PhrDataFactory::createImmunizationMedicalResources,
                        /* numOfResources= */ 2,
                        dataSource2);
        List<MedicalResource> allergyDatasource1 =
                upsertResources(
                        PhrDataFactory::createAllergyMedicalResources,
                        /* numOfResources= */ 2,
                        dataSource1);
        List<MedicalResource> resources =
                joinLists(immunizationsDataSource1, immunizationsDataSource2, allergyDatasource1);

        ReadMedicalResourcesRequest readRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(2)
                        .build();
        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        readRequest, DATA_SOURCE_PACKAGE_NAME, /* enforceSelfRead= */ false);
        String pageToken = result.getPageToken();
        assertThat(result.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(0), resources.get(1)));
        assertThat(pageToken)
                .isEqualTo(PhrPageTokenWrapper.of(readRequest, /* lastRowId= */ 2).encode());

        ReadMedicalResourcesRequest readRequest1 =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(2)
                        .setPageToken(pageToken)
                        .build();
        ReadMedicalResourcesInternalResponse result1 =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        readRequest1, DATA_SOURCE_PACKAGE_NAME, /* enforceSelfRead= */ false);
        String pageToken1 = result1.getPageToken();
        assertThat(result1.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(2), resources.get(3)));
        assertThat(pageToken1)
                .isEqualTo(PhrPageTokenWrapper.of(readRequest1, /* lastRowId= */ 4).encode());

        ReadMedicalResourcesRequest readRequest2 =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(2)
                        .setPageToken(pageToken1)
                        .build();
        ReadMedicalResourcesInternalResponse result2 =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        readRequest2, DATA_SOURCE_PACKAGE_NAME, /* enforceSelfRead= */ false);
        String pageToken2 = result2.getPageToken();
        assertThat(result2.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(4), resources.get(5)));
        assertThat(pageToken2).isEqualTo(null);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE})
    public void readByRequest_enforceSelfRead_immunizationFilter_canReadOnlySelfImmunization() {
        String dataSource1 = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        String dataSource2 =
                insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        List<MedicalResource> immunizationsDataSource1 =
                upsertResources(
                        PhrDataFactory::createImmunizationMedicalResources,
                        /* numOfResources= */ 4,
                        dataSource1);
        List<MedicalResource> immunizationsDataSource2 =
                upsertResources(
                        PhrDataFactory::createImmunizationMedicalResources,
                        /* numOfResources= */ 2,
                        dataSource2);
        List<MedicalResource> allergyDatasource1 =
                upsertResources(
                        PhrDataFactory::createAllergyMedicalResources,
                        /* numOfResources= */ 2,
                        dataSource1);
        List<MedicalResource> resources =
                joinLists(immunizationsDataSource1, immunizationsDataSource2, allergyDatasource1);

        ReadMedicalResourcesRequest readRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(2)
                        .build();
        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        readRequest, DATA_SOURCE_PACKAGE_NAME, /* enforceSelfRead= */ true);
        String pageToken = result.getPageToken();
        assertThat(result.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(0), resources.get(1)));
        assertThat(pageToken)
                .isEqualTo(PhrPageTokenWrapper.of(readRequest, /* lastRowId= */ 2).encode());

        ReadMedicalResourcesRequest readRequest1 =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(2)
                        .setPageToken(pageToken)
                        .build();
        ReadMedicalResourcesInternalResponse result1 =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        readRequest1, DATA_SOURCE_PACKAGE_NAME, /* enforceSelfRead= */ true);
        String pageToken1 = result1.getPageToken();
        assertThat(result1.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(2), resources.get(3)));
        assertThat(pageToken1).isEqualTo(null);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void insertMultipleMedicalResourcesWithSameDataSource_readMultipleResources() {
        // TODO(b/351992434): Create test utilities to make these large repeated code blocks
        // clearer.
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource1 = getFhirResource();
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource1)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(resource1);
        FhirResource fhirResource2 = getFhirResourceAllergy();
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource2)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(resource2);
        List<MedicalResourceId> ids = List.of(resource1.getId(), resource2.getId());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(ids);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result).containsExactly(resource1, resource2);
        assertThat(upsertedMedicalResources).containsExactly(resource1, resource2);
        assertThat(indicesResult)
                .containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void insertMedicalResourcesOfSameType_createsAccessLog_success() {
        String dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        upsertResources(
                PhrDataFactory::createImmunizationMedicalResources,
                /* numOfResources= */ 6,
                dataSource);

        List<AccessLog> accessLogs = AccessLogsHelper.queryAccessLogs();
        AccessLog accessLog = accessLogs.get(0);

        assertThat(accessLogs).hasSize(1);
        assertThat(accessLog.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void insertMedicalResourcesOfDifferentTypes_createsAccessLog_success() {
        String dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunization = createImmunizationMedicalResource(dataSource);
        MedicalResource allergy = createAllergyMedicalResource(dataSource);
        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME,
                createUpsertMedicalResourceRequests(List.of(immunization, allergy), dataSource));

        List<AccessLog> accessLogs = AccessLogsHelper.queryAccessLogs();
        AccessLog accessLog = accessLogs.get(0);

        assertThat(accessLogs).hasSize(1);
        assertThat(accessLog.getMedicalResourceTypes())
                .isEqualTo(
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN));
        assertThat(accessLog.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog.getRecordTypes()).isEmpty();
        assertThat(accessLog.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void insertAndUpdateMedicalResources_createsAccessLog_success() throws JSONException {
        String dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunization = createImmunizationMedicalResource(dataSource);
        MedicalResource allergy = createAllergyMedicalResource(dataSource);
        MedicalResource updatedImmunization = createUpdatedImmunizationMedicalResource(dataSource);
        // initial insert
        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME,
                createUpsertMedicalResourceRequests(List.of(immunization, allergy), dataSource));
        // update the immunization resource
        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME,
                createUpsertMedicalResourceRequests(List.of(updatedImmunization), dataSource));

        List<AccessLog> accessLogs = AccessLogsHelper.queryAccessLogs();
        AccessLog accessLog1 = accessLogs.get(0);
        AccessLog accessLog2 = accessLogs.get(1);

        assertThat(accessLogs).hasSize(2);

        assertThat(accessLog1.getMedicalResourceTypes())
                .isEqualTo(
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN));
        assertThat(accessLog1.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog1.getRecordTypes()).isEmpty();
        assertThat(accessLog1.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog1.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog1.getAccessTime()).isNotNull();

        assertThat(accessLog2.getMedicalResourceTypes())
                .isEqualTo(Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));
        assertThat(accessLog2.getPackageName()).isEqualTo(DATA_SOURCE_PACKAGE_NAME);
        assertThat(accessLog2.getRecordTypes()).isEmpty();
        assertThat(accessLog2.getOperationType()).isEqualTo(OPERATION_TYPE_UPSERT);
        assertThat(accessLog2.isMedicalDataSourceAccessed()).isFalse();
        assertThat(accessLog2.getAccessTime()).isNotNull();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void insertMultipleMedicalResourcesWithDifferentDataSources_readMultipleResources() {
        MedicalDataSource dataSource1 = insertMedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 = insertMedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource1.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(resource1);
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource2.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(resource2);
        List<MedicalResourceId> ids = List.of(resource1.getId(), resource2.getId());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(ids);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result).containsExactly(resource1, resource2);
        assertThat(upsertedMedicalResources).containsExactly(resource1, resource2);
        assertThat(indicesResult)
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void updateSingleMedicalResource_success() throws JSONException {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource originalFhirResource = getFhirResource();
        FhirResource fhirResourceUpdated =
                getFhirResourceBuilder()
                        .setData(addCompletedStatus(getFhirResource().getData()))
                        .build();
        UpsertMedicalResourceInternalRequest originalUpsertRequest =
                makeUpsertRequest(
                        originalFhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_VERSION_R4,
                        dataSource.getId());
        MedicalResource expectedUpdatedMedicalResource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResourceUpdated)
                        .build();
        UpsertMedicalResourceInternalRequest updateRequest =
                makeUpsertRequest(expectedUpdatedMedicalResource);
        List<MedicalResourceId> ids = List.of(expectedUpdatedMedicalResource.getId());

        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME, List.of(originalUpsertRequest));
        List<MedicalResource> updatedMedicalResource =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, List.of(updateRequest));
        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(ids);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result).containsExactly(expectedUpdatedMedicalResource);
        assertThat(result).isEqualTo(updatedMedicalResource);
        assertThat(indicesResult).containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATION);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_updateSingleMedicalResource_success()
            throws JSONException {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource originalResource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(originalResource);
        FhirResource updatedFhirResource = getUpdatedImmunizationFhirResource();
        MedicalResource updatedResource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                updatedFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated1 =
                makeUpsertRequest(updatedResource1);
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                allergyFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(resource2);
        List<MedicalResourceId> medicalIdFilters =
                List.of(updatedResource1.getId(), resource2.getId());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> updatedMedicalResource =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(upsertMedicalResourceInternalRequestUpdated1));
        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        medicalIdFilters);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result).containsExactly(updatedResource1, resource2);
        assertThat(upsertedMedicalResources).containsExactly(originalResource, resource2);
        assertThat(updatedMedicalResource).containsExactly(updatedResource1);
        assertThat(indicesResult)
                .containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_updateMultipleMedicalResources_success()
            throws JSONException {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(resource1);
        FhirResource updatedFhirResource = getUpdatedImmunizationFhirResource();
        MedicalResource updatedResource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                updatedFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated1 =
                makeUpsertRequest(updatedResource1);
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                allergyFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(resource2);
        FhirResource updatedAllergyFhirResource = getUpdatedAllergyFhirResource();
        MedicalResource updatedResource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_UNKNOWN,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                updatedAllergyFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated2 =
                makeUpsertRequest(updatedResource2);

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertMedicalResourceInternalRequest1,
                                upsertMedicalResourceInternalRequest2));
        List<MedicalResource> updatedMedicalResource =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertMedicalResourceInternalRequestUpdated1,
                                upsertMedicalResourceInternalRequestUpdated2));
        List<MedicalResourceId> medicalIdFilters = List.of(resource1.getId(), resource2.getId());
        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        medicalIdFilters);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(upsertedMedicalResources).containsExactly(resource1, resource2);
        assertThat(updatedMedicalResource)
                .containsExactly(updatedResource1, updatedResource2)
                .inOrder();
        assertThat(result).containsExactly(updatedResource1, updatedResource2);
        assertThat(indicesResult)
                .containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_readByRequest_success() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        List<MedicalResource> resources =
                createImmunizationMedicalResources(/* numOfResources= */ 6, dataSource.getId());
        List<UpsertMedicalResourceInternalRequest> upsertRequests =
                createUpsertMedicalResourceRequests(resources, dataSource.getId());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, upsertRequests);
        assertThat(upsertedMedicalResources).containsExactlyElementsIn(resources);

        ReadMedicalResourcesRequest readRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(2)
                        .build();
        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readRequest);
        String pageToken = result.getPageToken();
        assertThat(result.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(0), resources.get(1)));
        assertThat(pageToken)
                .isEqualTo(PhrPageTokenWrapper.of(readRequest, /* lastRowId= */ 2).encode());

        ReadMedicalResourcesRequest readRequest1 =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(2)
                        .setPageToken(pageToken)
                        .build();
        ReadMedicalResourcesInternalResponse result1 =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readRequest1);
        String pageToken1 = result1.getPageToken();
        assertThat(result1.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(2), resources.get(3)));
        assertThat(pageToken1)
                .isEqualTo(PhrPageTokenWrapper.of(readRequest1, /* lastRowId= */ 4).encode());

        ReadMedicalResourcesRequest readRequest2 =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(2)
                        .setPageToken(pageToken1)
                        .build();
        ReadMedicalResourcesInternalResponse result2 =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readRequest2);
        String pageToken2 = result2.getPageToken();
        assertThat(result2.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(4), resources.get(5)));
        assertThat(pageToken2).isEqualTo(null);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResourcesReadByRequest_pageSizeLargerThanResources_success() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        List<MedicalResource> resources =
                createImmunizationMedicalResources(/* numOfResources= */ 6, dataSource.getId());
        List<UpsertMedicalResourceInternalRequest> requests =
                createUpsertMedicalResourceRequests(resources, dataSource.getId());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(DATA_SOURCE_PACKAGE_NAME, requests);
        ReadMedicalResourcesRequest readRequest =
                new ReadMedicalResourcesRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                        .setPageSize(10)
                        .build();
        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        readRequest);

        assertThat(upsertedMedicalResources).containsExactlyElementsIn(resources);
        assertThat(result.getMedicalResources()).containsExactlyElementsIn(resources);
        assertThat(result.getPageToken()).isEqualTo(null);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readMedicalResourcedByRequest_invalidPageToken_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                                new ReadMedicalResourcesRequest.Builder(
                                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION)
                                        .setPageToken(INVALID_PAGE_TOKEN)
                                        .build()));
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_noId_fails() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                                List.of(), /* appInfoIdRestriction= */ null));
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneIdNotPresent_succeeds() {
        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                List.of(
                        new MedicalResourceId(
                                DATA_SOURCE_ID,
                                FHIR_RESOURCE_TYPE_IMMUNIZATION,
                                FHIR_RESOURCE_ID_IMMUNIZATION)),
                /* appInfoIdRestriction= */ null);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneIdPresent_succeedsDeleting() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource medicalResource1 =
                upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource.getId());

        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                List.of(medicalResource1.getId()), /* appInfoIdRestriction= */ null);

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResource1.getId()));
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result).isEmpty();
        assertThat(indicesResult).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneOfTwoSpecified_onlySpecifiedDeleted() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource medicalResource1 =
                upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource.getId());
        MedicalResource medicalResource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource.getId());

        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                List.of(medicalResource1.getId()), /* appInfoIdRestriction= */ null);
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResource1.getId(), medicalResource2.getId()));
        assertThat(result).containsExactly(medicalResource2);
        assertThat(indicesResult).containsExactly(MEDICAL_RESOURCE_TYPE_UNKNOWN);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneOfTwoSpecifiedWrongPackage_nothingDeleted() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource medicalResource1 =
                upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource.getId());
        MedicalResource medicalResource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource.getId());

        long appInfoRestriction = mAppInfoHelper.getAppInfoId(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                List.of(medicalResource1.getId()), appInfoRestriction);

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResource1.getId(), medicalResource2.getId()));
        assertThat(result).containsExactly(medicalResource1, medicalResource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneOfTwoSpecifiedRightPackage_oneOfTwo() {
        MedicalDataSource dataSource = insertMedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource medicalResource1 =
                upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource.getId());
        MedicalResource medicalResource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource.getId());
        long appInfoRestriction = mAppInfoHelper.getAppInfoId(DATA_SOURCE_PACKAGE_NAME);

        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                List.of(medicalResource1.getId()), appInfoRestriction);

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResource1.getId(), medicalResource2.getId()));
        assertThat(result).containsExactly(medicalResource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_multipleIdsFromDifferentPackages_succeeds() {
        MedicalDataSource dataSource1 = insertMedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                insertMedicalDataSource("ds2", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource expectedResource1Source1 =
                upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1.getId());
        MedicalResource expectedResource1Source2 =
                upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2.getId());
        MedicalResource expectedResource2Source1 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1.getId());
        MedicalResource expectedResource2Source2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2.getId());

        mMedicalResourceHelper.deleteMedicalResourcesByIds(
                List.of(expectedResource1Source1.getId(), expectedResource2Source2.getId()),
                /* appInfoIdRestriction= */ null);

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(
                                expectedResource1Source1.getId(),
                                expectedResource1Source2.getId(),
                                expectedResource2Source1.getId(),
                                expectedResource2Source2.getId()));
        assertThat(result).containsExactly(expectedResource1Source2, expectedResource2Source1);
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

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByDataSources_noIds_succeeds() {
        mMedicalResourceHelper.deleteMedicalResourcesByDataSources(
                List.of(), /* appInfoRestriction= */ null);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByDataSources_singleDataSource_succeeds() {
        // Create two datasources, with one resource each.
        MedicalDataSource dataSource1 = insertMedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 = insertMedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource dataSource1Resource1 =
                upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1.getId());
        MedicalResource dataSource2Resource1 =
                upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2.getId());
        MedicalResource dataSource1Resource2 =
                upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1.getId());

        // Delete all of the data for just the first datasource
        mMedicalResourceHelper.deleteMedicalResourcesByDataSources(
                List.of(dataSource1.getId()), /* appInfoRestriction= */ null);

        // Test that the data for data source 1 is gone, but 2 is still present
        assertThat(
                        mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                                List.of(
                                        dataSource1Resource1.getId(),
                                        dataSource1Resource2.getId())))
                .hasSize(0);
        assertThat(
                        mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                                List.of(dataSource2Resource1.getId())))
                .hasSize(1);
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

    /**
     * Insert and return a {@link MedicalDataSource} where the display name, and URI will contain
     * the given name.
     */
    private MedicalDataSource insertMedicalDataSource(String name, String packageName) {
        String uri = String.format("%s/%s", DATA_SOURCE_FHIR_BASE_URI, name);
        String displayName = String.format("%s %s", DATA_SOURCE_DISPLAY_NAME, name);

        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(uri, displayName).build();
        return mMedicalDataSourceHelper.createMedicalDataSource(
                mContext, createMedicalDataSourceRequest, packageName);
    }

    private MedicalResource upsertResource(MedicalResourceCreator creator, String dataSourceId) {
        MedicalResource medicalResource = creator.create(dataSourceId);
        return mMedicalResourceHelper
                .upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, List.of(makeUpsertRequest(medicalResource)))
                .get(0);
    }

    private List<MedicalResource> upsertResources(
            MedicalResourcesCreator creator, int numOfResources, String dataSourceId) {
        List<MedicalResource> medicalResources = creator.create(numOfResources, dataSourceId);
        return mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME,
                medicalResources.stream()
                        .map(MedicalResourceHelperTest::makeUpsertRequest)
                        .toList());
    }

    private interface MedicalResourceCreator {
        MedicalResource create(String dataSourceId);
    }

    private interface MedicalResourcesCreator {
        List<MedicalResource> create(int num, String dataSourceId);
    }

    /** Returns a request to upsert the given {@link MedicalResource}. */
    private static UpsertMedicalResourceInternalRequest makeUpsertRequest(
            MedicalResource resource) {
        return makeUpsertRequest(
                resource.getFhirResource(),
                resource.getType(),
                resource.getFhirVersion(),
                resource.getDataSourceId());
    }

    /**
     * Returns a request to upsert the given {@link FhirResource}, along with required source
     * information.
     */
    private static UpsertMedicalResourceInternalRequest makeUpsertRequest(
            FhirResource resource,
            int medicalResourceType,
            FhirVersion fhirVersion,
            String datasourceId) {
        return new UpsertMedicalResourceInternalRequest()
                .setMedicalResourceType(medicalResourceType)
                .setFhirResourceId(resource.getId())
                .setFhirResourceType(resource.getType())
                .setFhirVersion(fhirVersion)
                .setData(resource.getData())
                .setDataSourceId(datasourceId);
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

    /**
     * Creates a list of {@link UpsertMedicalResourceInternalRequest}s for the given list of {@link
     * MedicalResource}s and {@code dataSourceId}.
     */
    private static List<UpsertMedicalResourceInternalRequest> createUpsertMedicalResourceRequests(
            List<MedicalResource> medicalResources, String dataSourceId) {
        List<UpsertMedicalResourceInternalRequest> requests = new ArrayList<>();
        for (MedicalResource medicalResource : medicalResources) {
            FhirResource fhirResource = medicalResource.getFhirResource();
            UpsertMedicalResourceInternalRequest request =
                    new UpsertMedicalResourceInternalRequest()
                            .setMedicalResourceType(medicalResource.getType())
                            .setFhirResourceId(fhirResource.getId())
                            .setFhirResourceType(fhirResource.getType())
                            .setFhirVersion(medicalResource.getFhirVersion())
                            .setData(fhirResource.getData())
                            .setDataSourceId(dataSourceId);
            requests.add(request);
        }
        return requests;
    }

    /**
     * Returns the list of {@link AccessLog}s sorted based on the {@link AccessLog#getAccessTime()}
     * in an ascending order.
     */
    private static List<AccessLog> sortByAccessTime(List<AccessLog> accessLogs) {
        return accessLogs.stream()
                .sorted(Comparator.comparing(AccessLog::getAccessTime))
                .collect(Collectors.toList());
    }

    private static <T> List<T> joinLists(List<T>... lists) {
        return Stream.of(lists).flatMap(Collection::stream).collect(Collectors.toList());
    }
}
