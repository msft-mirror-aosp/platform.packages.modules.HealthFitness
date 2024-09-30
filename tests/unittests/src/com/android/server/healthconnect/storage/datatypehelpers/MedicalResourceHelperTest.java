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
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_DELETE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_READ;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_UPSERT;
import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_ID_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4B;
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

import static com.android.server.healthconnect.storage.PhrTestUtils.ACCESS_LOG_EQUIVALENCE;
import static com.android.server.healthconnect.storage.PhrTestUtils.makeUpsertRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.DATA_SOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_DATA_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.FHIR_RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.MEDICAL_RESOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getCreateTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getPrimaryColumn;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper.getReadQueryForMedicalResourceTypeToDataSourceIdsMap;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getMedicalResourceTypeColumnName;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getParentColumnReference;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceIndicesHelper.getTableName;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.LAST_MODIFIED_TIME_COLUMN_NAME;
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
import android.health.connect.DeleteMedicalResourcesRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.MedicalResourceId;
import android.health.connect.ReadMedicalResourcesInitialRequest;
import android.health.connect.ReadMedicalResourcesPageRequest;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.utils.PhrDataFactory;
import android.os.Environment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import com.android.healthfitness.flags.AconfigFlagHelperTestRule;
import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.phr.PhrPageTokenWrapper;
import com.android.server.healthconnect.phr.ReadMedicalResourcesInternalResponse;
import com.android.server.healthconnect.storage.PhrTestUtils;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MedicalResourceHelperTest {

    @Rule(order = 0)
    public final AconfigFlagHelperTestRule mAconfigFlagHelperTestRule =
            new AconfigFlagHelperTestRule();

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
    private static final Instant INSTANT_NOW = Instant.now();

    private MedicalResourceHelper mMedicalResourceHelper;
    private MedicalDataSourceHelper mMedicalDataSourceHelper;
    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private AppInfoHelper mAppInfoHelper;
    private AccessLogsHelper mAccessLogsHelper;
    private HealthConnectUserContext mContext;
    private PhrTestUtils mUtil;
    private FakeTimeSource mFakeTimeSource;

    @Before
    public void setup() {
        AccessLogsHelper.resetInstanceForTest();
        AppInfoHelper.resetInstanceForTest();
        mTransactionManager = mHealthConnectDatabaseTestRule.getTransactionManager();
        mContext = mHealthConnectDatabaseTestRule.getUserContext();
        mTransactionTestUtils = new TransactionTestUtils(mContext, mTransactionManager);
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        mTransactionTestUtils.insertApp(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mAppInfoHelper = AppInfoHelper.getInstance(mTransactionManager);
        mAccessLogsHelper = AccessLogsHelper.getInstance(mTransactionManager, mAppInfoHelper);
        mFakeTimeSource = new FakeTimeSource(INSTANT_NOW);
        mMedicalDataSourceHelper =
                new MedicalDataSourceHelper(
                        mTransactionManager, mAppInfoHelper, mFakeTimeSource, mAccessLogsHelper);
        mMedicalResourceHelper =
                new MedicalResourceHelper(
                        mTransactionManager,
                        mAppInfoHelper,
                        mMedicalDataSourceHelper,
                        mFakeTimeSource,
                        mAccessLogsHelper);

        mUtil =
                new PhrTestUtils(
                        mContext,
                        mTransactionManager,
                        mMedicalResourceHelper,
                        mMedicalDataSourceHelper);
    }

    @Test
    public void getCreateTableRequest_correctResult() {
        List<Pair<String, String>> columnInfoMedicalResource =
                List.of(
                        Pair.create(getPrimaryColumn(), PRIMARY_AUTOINCREMENT),
                        Pair.create(FHIR_RESOURCE_TYPE_COLUMN_NAME, INTEGER_NOT_NULL),
                        Pair.create(FHIR_RESOURCE_ID_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_DATA_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(DATA_SOURCE_ID_COLUMN_NAME, INTEGER_NOT_NULL),
                        Pair.create(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER_NOT_NULL));
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
                                Collections.singletonList(
                                        MedicalDataSourceHelper.getPrimaryColumnName()))
                        .createIndexOn(LAST_MODIFIED_TIME_COLUMN_NAME)
                        .setChildTableRequests(List.of(childTableRequest));

        CreateTableRequest result = getCreateTableRequest();
        assertThat(result).isEqualTo(expected);
    }

    @Test
    public void getUpsertContentValues_correctResult() {
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                makeUpsertRequest(
                        fhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        FHIR_VERSION_R4,
                        DATA_SOURCE_ID);

        ContentValues contentValues =
                MedicalResourceHelper.getContentValues(
                        DATA_SOURCE_ROW_ID, upsertMedicalResourceInternalRequest, INSTANT_NOW);

        assertThat(contentValues.size()).isEqualTo(5);
        assertThat(contentValues.get(FHIR_RESOURCE_TYPE_COLUMN_NAME))
                .isEqualTo(fhirResource.getType());
        assertThat(contentValues.get(DATA_SOURCE_ID_COLUMN_NAME)).isEqualTo(DATA_SOURCE_ROW_ID);
        assertThat(contentValues.get(FHIR_DATA_COLUMN_NAME)).isEqualTo(fhirResource.getData());
        assertThat(contentValues.get(LAST_MODIFIED_TIME_COLUMN_NAME))
                .isEqualTo(INSTANT_NOW.toEpochMilli());
    }

    @Test
    public void getReadTableRequest_distinctResourceTypesUsingAppIdAndDataSourceIds_correctQuery() {
        List<UUID> dataSourceIds = List.of(UUID.fromString("a6194e35-698c-4706-918f-00bf959f123b"));
        long appId = 123L;
        List<String> hexValues = StorageUtils.getListOfHexStrings(dataSourceIds);

        ReadTableRequest request =
                MedicalResourceHelper.getFilteredReadRequestForDistinctResourceTypes(
                        dataSourceIds, new HashSet<>(), appId);

        assertThat(request.getReadCommand())
                .isEqualTo(
                        "SELECT DISTINCT medical_resource_type FROM ( SELECT * FROM"
                                + " medical_resource_table ) AS inner_query_result"
                                + "  INNER JOIN ( SELECT"
                                + " * FROM medical_data_source_table WHERE app_info_id = '"
                                + appId
                                + "'"
                                + " AND data_source_uuid IN ("
                                + String.join(", ", hexValues)
                                + ")) medical_data_source_table ON"
                                + " inner_query_result.data_source_id ="
                                + " medical_data_source_table.medical_data_source_row_id"
                                + "  INNER JOIN medical_resource_indices_table ON"
                                + " inner_query_result.medical_resource_row_id ="
                                + " medical_resource_indices_table.medical_resource_id");
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertAndUpdateResource_lastModifiedTimeIsUpdated() throws JSONException {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        UpsertMedicalResourceInternalRequest upsertRequest =
                makeUpsertRequest(
                        getFhirResource(),
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        FHIR_VERSION_R4,
                        dataSource.getId());
        // The same MedicalResource with FHIR JSON updated.
        UpsertMedicalResourceInternalRequest updateRequest =
                makeUpsertRequest(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                        dataSource.getId(),
                                        FHIR_VERSION_R4,
                                        getFhirResourceBuilder()
                                                .setData(
                                                        addCompletedStatus(
                                                                getFhirResource().getData()))
                                                .build())
                                .build());

        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME, List.of(upsertRequest));
        long lastModifiedTimeOriginal =
                mUtil.readLastModifiedTimestamp(MEDICAL_RESOURCE_TABLE_NAME);
        assertThat(lastModifiedTimeOriginal).isEqualTo(INSTANT_NOW.toEpochMilli());

        Instant upadatedInstant = Instant.now();
        mFakeTimeSource.setInstant(upadatedInstant);

        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME, List.of(updateRequest));
        long lastModifiedTimeUpdated = mUtil.readLastModifiedTimestamp(MEDICAL_RESOURCE_TABLE_NAME);

        assertThat(lastModifiedTimeUpdated).isEqualTo(upadatedInstant.toEpochMilli());
    }

    @Test
    public void getReadTableRequest_usingRequest_correctQuery() {
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadTableRequest readRequest =
                MedicalResourceHelper.getReadTableRequestUsingRequestFilters(
                        PhrPageTokenWrapper.from(request.toParcel()), request.getPageSize());

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
                                + MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS
                                + ")"
                                + ") medical_resource_indices_table ON"
                                + " inner_query_result.medical_resource_row_id ="
                                + " medical_resource_indices_table.medical_resource_id  INNER JOIN"
                                + " medical_data_source_table ON inner_query_result.data_source_id"
                                + " = medical_data_source_table.medical_data_source_row_id");
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getReadTableRequest_usingRequestWithDataSourceIds_correctQuery() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("id1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("id2", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(dataSource1.getId())
                        .addDataSourceId(dataSource2.getId())
                        .build();
        ReadTableRequest readRequest =
                MedicalResourceHelper.getReadTableRequestUsingRequestFilters(
                        PhrPageTokenWrapper.from(request.toParcel()), request.getPageSize());
        List<String> dataSourceIdHexValues =
                StorageUtils.toUuids(request.getDataSourceIds()).stream()
                        .map(StorageUtils::getHexString)
                        .toList();

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
                                + MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS
                                + ")) medical_resource_indices_table ON"
                                + " inner_query_result.medical_resource_row_id ="
                                + " medical_resource_indices_table.medical_resource_id  INNER JOIN"
                                + " ( SELECT * FROM medical_data_source_table WHERE"
                                + " data_source_uuid IN ("
                                + String.join(", ", dataSourceIdHexValues)
                                + ")) medical_data_source_table ON"
                                + " inner_query_result.data_source_id ="
                                + " medical_data_source_table.medical_data_source_row_id");
    }

    @Test
    public void getReadQuery_forMedicalResourceTypeToDataSourceIdsMap_correctQuery() {
        String readQuery = getReadQueryForMedicalResourceTypeToDataSourceIdsMap();

        assertThat(readQuery)
                .isEqualTo(
                        "SELECT medical_resource_type, "
                                + "GROUP_CONCAT(data_source_id, ',') AS data_source_id "
                                + "FROM ("
                                + "SELECT DISTINCT medical_resource_type,data_source_id "
                                + "FROM ( SELECT * FROM medical_resource_table )"
                                + " AS inner_query_result "
                                + " INNER JOIN medical_resource_indices_table"
                                + " ON inner_query_result.medical_resource_row_id"
                                + " = medical_resource_indices_table.medical_resource_id)"
                                + " GROUP BY medical_resource_type");
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readMedicalResourcesByIds_dbEmpty_returnsEmpty() {
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readMedicalResourcesByRequest_dbEmpty_returnsEmpty() {
        ReadMedicalResourcesInitialRequest readImmunizationRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();

        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(readImmunizationRequest.toParcel()),
                        readImmunizationRequest.getPageSize());

        assertThat(result.getMedicalResources()).isEmpty();
        assertThat(result.getPageToken()).isEqualTo(null);
        assertThat(result.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void upsertAndReadMedicalResourcesByRequest_MedicalResourceTypeDoesNotExist_success() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertImmunizationResourceRequest =
                makeUpsertRequest(
                        fhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        FHIR_VERSION_R4,
                        dataSource.getId());

        List<MedicalResource> upsertedResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, List.of(upsertImmunizationResourceRequest));
        ReadMedicalResourcesInitialRequest readAllergyRequest =
                new ReadMedicalResourcesInitialRequest.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES)
                        .build();
        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(readAllergyRequest.toParcel()),
                        readAllergyRequest.getPageSize());

        assertThat(upsertedResources)
                .containsExactly(
                        new MedicalResource.Builder(
                                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                        dataSource.getId(),
                                        FHIR_VERSION_R4,
                                        fhirResource)
                                .build());
        assertThat(result.getMedicalResources()).isEmpty();
        assertThat(result.getPageToken()).isEqualTo(null);
        assertThat(result.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void upsertMedicalResourcesSameDataSource_readMedicalResourcesByRequest_success() {
        // Upsert 3 resources in this test: immunization, differentImmunization and allergy, all
        // with the same data source.
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource immunization =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(immunization);
        FhirResource differentFhirResource = getFhirResourceDifferentImmunization();
        MedicalResource differentImmunization =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                differentFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(differentImmunization);
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource allergy =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
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

        ReadMedicalResourcesInitialRequest readAllergyRequest =
                new ReadMedicalResourcesInitialRequest.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES)
                        .build();
        ReadMedicalResourcesInitialRequest readAllImmunizationsRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesInitialRequest readImmunizationsFromSameDataSourceRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(dataSource.getId())
                        .build();
        ReadMedicalResourcesInitialRequest readImmunizationsFromDifferentDataSourceRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(DIFFERENT_DATA_SOURCE_ID)
                        .build();
        ReadMedicalResourcesInternalResponse allergyResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(readAllergyRequest.toParcel()),
                        readAllergyRequest.getPageSize());
        ReadMedicalResourcesInternalResponse allImmunizationsResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(readAllImmunizationsRequest.toParcel()),
                        readAllImmunizationsRequest.getPageSize());
        ReadMedicalResourcesInternalResponse immunizationsFromSameDataSourceResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(
                                readImmunizationsFromSameDataSourceRequest.toParcel()),
                        readImmunizationsFromSameDataSourceRequest.getPageSize());
        ReadMedicalResourcesInternalResponse immunizationsFromDifferentDataSourceResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(
                                readImmunizationsFromDifferentDataSourceRequest.toParcel()),
                        readImmunizationsFromDifferentDataSourceRequest.getPageSize());

        assertThat(upsertedResources).containsExactly(immunization, differentImmunization, allergy);
        assertThat(allergyResult.getMedicalResources()).containsExactly(allergy);
        assertThat(allergyResult.getPageToken()).isEqualTo(null);
        assertThat(allergyResult.getRemainingCount()).isEqualTo(0);
        assertThat(allImmunizationsResult.getMedicalResources())
                .containsExactly(immunization, differentImmunization);
        assertThat(allImmunizationsResult.getPageToken()).isEqualTo(null);
        assertThat(allImmunizationsResult.getRemainingCount()).isEqualTo(0);
        assertThat(immunizationsFromSameDataSourceResult.getMedicalResources())
                .containsExactly(immunization, differentImmunization);
        assertThat(immunizationsFromSameDataSourceResult.getPageToken()).isEqualTo(null);
        assertThat(immunizationsFromDifferentDataSourceResult.getMedicalResources()).isEmpty();
        assertThat(immunizationsFromDifferentDataSourceResult.getPageToken()).isEqualTo(null);
        assertThat(immunizationsFromDifferentDataSourceResult.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void upsertMedicalResourcesDifferentDataSources_readMedicalResourcesByRequest_success() {
        // Upsert 3 resources in this test: immunization, differentImmunization and allergy. Among
        // which immunization and allergy are from data source 1 and the differentImmunization is
        // from data source 2.
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("id1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("id2", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource immunizationDS1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource1.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertRequestImmunizationDataSource1 =
                makeUpsertRequest(immunizationDS1);
        FhirResource differentFhirResource = getFhirResourceDifferentImmunization();
        MedicalResource differentImmunizationDS2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource2.getId(),
                                FHIR_VERSION_R4,
                                differentFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertRequestImmunization2DataSource2 =
                makeUpsertRequest(differentImmunizationDS2);
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource allergyDS1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
                                dataSource1.getId(),
                                FHIR_VERSION_R4,
                                allergyFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertRequestAllergyDataSource1 =
                makeUpsertRequest(allergyDS1);
        List<MedicalResource> upsertedResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME,
                        List.of(
                                upsertRequestImmunizationDataSource1,
                                upsertRequestAllergyDataSource1));
        upsertedResources.addAll(
                mMedicalResourceHelper.upsertMedicalResources(
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                        List.of(upsertRequestImmunization2DataSource2)));

        ReadMedicalResourcesInitialRequest readAllergyRequest =
                new ReadMedicalResourcesInitialRequest.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES)
                        .build();
        ReadMedicalResourcesInitialRequest readImmunizationRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesInitialRequest readImmunizationsFromDataSource1Request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(dataSource1.getId())
                        .build();
        ReadMedicalResourcesInitialRequest readImmunizationsFromDataSource2Request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(dataSource2.getId())
                        .build();
        ReadMedicalResourcesInternalResponse allergyResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(readAllergyRequest.toParcel()),
                        readAllergyRequest.getPageSize());
        ReadMedicalResourcesInternalResponse immunizationResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(readImmunizationRequest.toParcel()),
                        readImmunizationRequest.getPageSize());
        ReadMedicalResourcesInternalResponse immunizationsFromDataSource1Result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(
                                readImmunizationsFromDataSource1Request.toParcel()),
                        readImmunizationsFromDataSource1Request.getPageSize());
        ReadMedicalResourcesInternalResponse immunizationsFromDataSource2Result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(
                                readImmunizationsFromDataSource2Request.toParcel()),
                        readImmunizationsFromDataSource2Request.getPageSize());

        assertThat(upsertedResources)
                .containsExactly(immunizationDS1, differentImmunizationDS2, allergyDS1);
        assertThat(allergyResult.getMedicalResources()).containsExactly(allergyDS1);
        assertThat(allergyResult.getPageToken()).isEqualTo(null);
        assertThat(allergyResult.getRemainingCount()).isEqualTo(0);
        assertThat(immunizationResult.getMedicalResources())
                .containsExactly(immunizationDS1, differentImmunizationDS2);
        assertThat(immunizationResult.getPageToken()).isEqualTo(null);
        assertThat(immunizationResult.getRemainingCount()).isEqualTo(0);
        assertThat(immunizationsFromDataSource1Result.getMedicalResources())
                .containsExactly(immunizationDS1);
        assertThat(immunizationsFromDataSource1Result.getPageToken()).isEqualTo(null);
        assertThat(immunizationsFromDataSource1Result.getRemainingCount()).isEqualTo(0);
        assertThat(immunizationsFromDataSource2Result.getMedicalResources())
                .containsExactly(differentImmunizationDS2);
        assertThat(immunizationsFromDataSource2Result.getPageToken()).isEqualTo(null);
        assertThat(immunizationsFromDataSource2Result.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertMedicalResources_dataSourceNotInserted_exceptionThrown() {
        FhirResource fhirResource = getFhirResource();
        String datasourceId = "acc6c726-b7ea-42f1-a063-e34f5b4e6247";
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                makeUpsertRequest(
                        fhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertMedicalResources_dataSourceBelongsToDifferentApp_exceptionThrownNoWrite() {
        MedicalDataSource ownDataSource =
                mUtil.insertR4MedicalDataSource("id1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource differentDataSource =
                mUtil.insertR4MedicalDataSource("id2", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        UpsertMedicalResourceInternalRequest upsertRequestOwnDataSource =
                makeUpsertRequest(
                        getFhirResource(),
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        FHIR_VERSION_R4,
                        ownDataSource.getId());
        UpsertMedicalResourceInternalRequest upsertRequestDifferentDataSource =
                makeUpsertRequest(
                        getFhirResource(),
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        FHIR_VERSION_R4,
                        differentDataSource.getId());

        Throwable thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mMedicalResourceHelper.upsertMedicalResources(
                                        DATA_SOURCE_PACKAGE_NAME,
                                        List.of(
                                                upsertRequestOwnDataSource,
                                                upsertRequestDifferentDataSource)));
        assertThat(thrown)
                .hasMessageThat()
                .contains("Invalid data source id: " + differentDataSource.getId());
        assertThat(getMedicalResourcesTableRowCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_DEVELOPMENT_DATABASE})
    public void upsertMedicalResources_fhirVersionDoesNotMatchDataSource_exceptionThrown() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("id1", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest =
                makeUpsertRequest(
                        fhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        FHIR_VERSION_R4B,
                        dataSource.getId());

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
                        String.format(
                                "Invalid fhir version: %s. It did not match the data source's fhir"
                                        + " version",
                                FHIR_VERSION_R4B));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readSubsetOfResourcesByIds_multipleResourcesUpserted_success() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
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
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
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
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES);
        assertThat(readResult).containsExactly(resource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertMedicalResources_returnsMedicalResources() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource expectedResource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertSingleMedicalResource_readSingleResource() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource expectedResource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
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
        assertThat(indicesResult).containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inBgWithoutBgPerm_hasWritePerm_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(mAccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inBgWithoutBgPerm_hasWritePerm_hasReadPermForResourceTypes_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(mAccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inBgWithoutBgPerm_noWritePerm_immunizationReadPermOnly_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ false,
                /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(mAccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsOnlyContainsNonSelfRead() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationPackage1 =
                createImmunizationMedicalResource(dataSource1.getId());
        MedicalResource immunizationPackage2 =
                createImmunizationMedicalResource(dataSource2.getId());
        MedicalResource allergyResourcePackage2 = createAllergyMedicalResource(dataSource2.getId());

        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME, List.of(makeUpsertRequest(immunizationPackage1)));
        mMedicalResourceHelper.upsertMedicalResources(
                DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                List.of(
                        makeUpsertRequest(immunizationPackage2),
                        makeUpsertRequest(allergyResourcePackage2)));
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(
                        immunizationPackage1.getId(),
                        immunizationPackage2.getId(),
                        allergyResourcePackage2.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES read permission.
        // has write permission.
        // The data that the calling app can read: immunizationPackage1 (through selfRead)
        // allergyResourcePackage2 (through read permission)
        // In this case, read access log is only created for non self read data:
        // MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES.
        AccessLog expected =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_READ,
                        /* medicalResourceTypes= */ Set.of(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsWhenAppHasNoWritePermHasReadPermButReadOnlySelfData() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationPackage1 =
                createImmunizationMedicalResource(dataSource1.getId());
        MedicalResource allergyResourcePackage2 = createAllergyMedicalResource(dataSource2.getId());

        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME, List.of(makeUpsertRequest(immunizationPackage1)));
        mMedicalResourceHelper.upsertMedicalResources(
                DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                List.of(makeUpsertRequest(allergyResourcePackage2)));
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunizationPackage1.getId(), allergyResourcePackage2.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ false,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS read permission.
        // no write permission.
        // The data that the calling app can read: immunizationPackage1 (through read permission)
        // In this case, read access log is created based on the intention of the app
        // even though the actual data accessed is self data: MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS.
        AccessLog expected =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_READ,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsWhenAppHasNoWritePermHasReadPermReadNonSelfData() {
        String dataSource =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunizationDifferentPackage =
                createImmunizationMedicalResource(dataSource);
        mMedicalResourceHelper.upsertMedicalResources(
                DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                List.of(makeUpsertRequest(immunizationDifferentPackage)));
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunizationDifferentPackage.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ false,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS read permission.
        // no write permission.
        // The data that the calling app can read: immunization (through read permission)
        // In this case, read access log is created: MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS.
        AccessLog expected =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_READ,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_inForegroundOrBgWithPerm_hasReadImmunization_noResourceRead_noAccessLog() {
        List<MedicalResourceId> ids = List.of(getMedicalResourceId());
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                ids,
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
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
        assertThat(mAccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsWhenAppHasWritePermHasReadPermReadSelfData() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunization =
                mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource);
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunization.getId()),
                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS read permission.
        // has write permission.
        // The data that the calling app can read: immunization (through read permission)
        // In this case, read access log is created based on the intention of the app
        // even though the actual data accessed is self data: MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS.
        AccessLog expected =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_READ,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readById_expectAccessLogsForEachResourceTypeReadBasedOnReadPerm() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunization =
                mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource);
        mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunization.getId()),
                Set.of(
                        MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                DATA_SOURCE_PACKAGE_NAME,
                /* hasWritePermission= */ true,
                /* isCalledFromBgWithoutBgRead= */ false);

        // Testing the case where calling app:
        // is calling from foreground or background with permission.
        // has MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS and MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES
        // read
        // permission.
        // has write permission.
        // The data that the calling app reads: immunization (through read permission)
        // In this case, read access log is created only for: MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS.
        // Even though the app has read permission for MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
        // the app did
        // not read any data of that type, so no access logs added for that.
        AccessLog expected =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_READ,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readById_inBgWithoutBgPerm_hasWritePerm_success() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readById_inBgWithoutBgPerm_hasWritePerm_hasReadPermForResourceTypes_success() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(immunizationDatasource1, allergyDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readById_inBgWithoutBgPerm_noWritePerm_immunizationReadPermOnly_success() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(immunizationDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void
            readById_inBgWithoutBgPerm_noWritePerm_bothAllergyAndImmunizationReadPerm_success() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(immunizationDatasource1, allergyDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readById_inForegroundOrinBgWithBgPerm_noWritePerm_success() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(immunizationDatasource1, immunizationDatasource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readById_inForeground_hasWritePerm_noReadResourceTypesPerm_canOnlyReadSelfData() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readById_inForeground_noWritePerm_readImmunizationPerm_canOnlyReadImmunization() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(immunizationDatasource1, immunizationDatasource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readById_inForeground_noWritePerm_readAllergyPerm_canOnlyReadAllergy() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(allergyDatasource1, allergyDatasource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readById_inForeground_noWritePerm_readImmunizationAndAllergyPerm_canReadBoth() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void
            readById_inForeground_hasWritePermAndReadImmunization_readsSelfDataAndImmunizations() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationDatasource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource allergyDatasource1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource immunizationDatasource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyDatasource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        List<MedicalResourceId> ids =
                List.of(
                        immunizationDatasource1.getId(),
                        immunizationDatasource2.getId(),
                        allergyDatasource1.getId(),
                        allergyDatasource2.getId());

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithPermissionChecks(
                        ids,
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result)
                .containsExactly(
                        immunizationDatasource1, immunizationDatasource2, allergyDatasource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readByRequest_isNotEnforceSelfRead_createsAccessLog() {
        ReadMedicalResourcesInitialRequest readRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                PhrPageTokenWrapper.from(readRequest.toParcel()),
                readRequest.getPageSize(),
                DATA_SOURCE_PACKAGE_NAME,
                /* enforceSelfRead= */ false);

        AccessLog expected =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_READ,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readByRequest_isEnforceSelfRead_doesNotCreateAccessLog() {
        ReadMedicalResourcesInitialRequest readRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                PhrPageTokenWrapper.from(readRequest.toParcel()),
                readRequest.getPageSize(),
                DATA_SOURCE_PACKAGE_NAME,
                /* enforceSelfRead= */ true);

        List<AccessLog> accessLogs = mAccessLogsHelper.queryAccessLogs();

        assertThat(accessLogs).hasSize(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readByRequest_isNotEnforceSelfRead_immunizationFilter_canReadAllImmunizations() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        // In total inserts 8 resources, among which 6 are Immunizations to be read in 3 pages.
        List<MedicalResource> immunizationsDataSource1 =
                mUtil.upsertResources(
                        PhrDataFactory::createImmunizationMedicalResources,
                        /* numOfResources= */ 4,
                        dataSource1);
        List<MedicalResource> immunizationsDataSource2 =
                mUtil.upsertResources(
                        PhrDataFactory::createImmunizationMedicalResources,
                        /* numOfResources= */ 2,
                        dataSource2);
        List<MedicalResource> allergyDatasource1 =
                mUtil.upsertResources(
                        PhrDataFactory::createAllergyMedicalResources,
                        /* numOfResources= */ 2,
                        dataSource1);
        List<MedicalResource> resources =
                joinLists(immunizationsDataSource1, immunizationsDataSource2, allergyDatasource1);

        ReadMedicalResourcesInitialRequest initialRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(2)
                        .build();
        PhrPageTokenWrapper initialPageTokenWrapper =
                PhrPageTokenWrapper.from(initialRequest.toParcel());
        ReadMedicalResourcesInternalResponse initialResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        initialPageTokenWrapper,
                        initialRequest.getPageSize(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* enforceSelfRead= */ false);
        String pageToken1 = initialResult.getPageToken();
        assertThat(initialResult.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(0), resources.get(1)));
        assertThat(pageToken1).isNotEmpty();
        assertThat(pageToken1)
                .isEqualTo(
                        initialPageTokenWrapper.cloneWithNewLastRowId(/* lastRowId= */ 2).encode());
        assertThat(initialResult.getRemainingCount()).isEqualTo(4);

        ReadMedicalResourcesPageRequest pageRequest1 =
                new ReadMedicalResourcesPageRequest.Builder(pageToken1).setPageSize(2).build();
        PhrPageTokenWrapper pageTokenWrapper1 = PhrPageTokenWrapper.from(pageRequest1.toParcel());
        ReadMedicalResourcesInternalResponse pageResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        pageTokenWrapper1,
                        pageRequest1.getPageSize(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* enforceSelfRead= */ false);
        String pageToken2 = pageResult.getPageToken();
        assertThat(pageResult.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(2), resources.get(3)));
        assertThat(pageToken2).isNotEmpty();
        assertThat(pageToken2)
                .isEqualTo(pageTokenWrapper1.cloneWithNewLastRowId(/* lastRowId= */ 4).encode());
        assertThat(pageResult.getRemainingCount()).isEqualTo(2);

        ReadMedicalResourcesPageRequest pageRequest2 =
                new ReadMedicalResourcesPageRequest.Builder(pageToken2).setPageSize(2).build();
        PhrPageTokenWrapper pageTokenWrapper2 = PhrPageTokenWrapper.from(pageRequest2.toParcel());
        ReadMedicalResourcesInternalResponse pageResult2 =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        pageTokenWrapper2,
                        pageRequest2.getPageSize(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* enforceSelfRead= */ false);
        String pageToken3 = pageResult2.getPageToken();
        assertThat(pageResult2.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(4), resources.get(5)));
        assertThat(pageToken3).isNull();
        assertThat(pageResult2.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void readByRequest_enforceSelfRead_immunizationFilter_canReadOnlySelfImmunization() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        // In total inserts 8 resources, among which 4 are Immunizations from data source 1 to be
        // read in 2 pages.
        List<MedicalResource> immunizationsDataSource1 =
                mUtil.upsertResources(
                        PhrDataFactory::createImmunizationMedicalResources,
                        /* numOfResources= */ 4,
                        dataSource1);
        List<MedicalResource> immunizationsDataSource2 =
                mUtil.upsertResources(
                        PhrDataFactory::createImmunizationMedicalResources,
                        /* numOfResources= */ 2,
                        dataSource2);
        List<MedicalResource> allergyDatasource1 =
                mUtil.upsertResources(
                        PhrDataFactory::createAllergyMedicalResources,
                        /* numOfResources= */ 2,
                        dataSource1);
        List<MedicalResource> resources =
                joinLists(immunizationsDataSource1, immunizationsDataSource2, allergyDatasource1);

        ReadMedicalResourcesInitialRequest initialRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(2)
                        .build();
        PhrPageTokenWrapper initialPageTokenWrapper =
                PhrPageTokenWrapper.from(initialRequest.toParcel());
        ReadMedicalResourcesInternalResponse initialResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        initialPageTokenWrapper,
                        initialRequest.getPageSize(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* enforceSelfRead= */ true);
        String pageToken1 = initialResult.getPageToken();
        assertThat(initialResult.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(0), resources.get(1)));
        assertThat(pageToken1).isNotEmpty();
        assertThat(pageToken1)
                .isEqualTo(
                        initialPageTokenWrapper.cloneWithNewLastRowId(/* lastRowId= */ 2).encode());
        assertThat(initialResult.getRemainingCount()).isEqualTo(2);

        ReadMedicalResourcesPageRequest pageRequest =
                new ReadMedicalResourcesPageRequest.Builder(pageToken1).setPageSize(2).build();
        PhrPageTokenWrapper pageTokenWrapper = PhrPageTokenWrapper.from(pageRequest.toParcel());
        ReadMedicalResourcesInternalResponse pageResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithPermissionChecks(
                        pageTokenWrapper,
                        pageRequest.getPageSize(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* enforceSelfRead= */ true);
        String pageToken2 = pageResult.getPageToken();
        assertThat(pageResult.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(2), resources.get(3)));
        assertThat(pageToken2).isNull();
        assertThat(pageResult.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertMultipleMedicalResourcesWithSameDataSource_readMultipleResources() {
        // TODO(b/351992434): Create test utilities to make these large repeated code blocks
        // clearer.
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource1 = getFhirResource();
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource1)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(resource1);
        FhirResource fhirResource2 = getFhirResourceAllergy();
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
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
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertMedicalResourcesOfSameType_createsAccessLog_success() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResources(
                PhrDataFactory::createImmunizationMedicalResources,
                /* numOfResources= */ 6,
                dataSource);

        AccessLog expected =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_UPSERT,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertMedicalResourcesOfDifferentTypes_createsAccessLog_success() {
        String dataSource = mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
        MedicalResource immunization = createImmunizationMedicalResource(dataSource);
        MedicalResource allergy = createAllergyMedicalResource(dataSource);
        mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME,
                createUpsertMedicalResourceRequests(List.of(immunization, allergy), dataSource));

        AccessLog expected =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_UPSERT,
                        /* medicalResourceTypes= */ Set.of(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(expected);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertAndUpdateMedicalResources_createsAccessLog_success() throws JSONException {
        String dataSource = mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME).getId();
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

        AccessLog insertAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_UPSERT,
                        /* medicalResourceTypes= */ Set.of(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES),
                        /* isMedicalDataSourceAccessed= */ false);
        AccessLog updateAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_UPSERT,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .containsAtLeast(insertAccessLog, updateAccessLog);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD, Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void insertMultipleMedicalResourcesWithDifferentDataSources_readMultipleResources() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource1.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(resource1);
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
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
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS, MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void updateSingleMedicalResource_success() throws JSONException {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource originalFhirResource = getFhirResource();
        FhirResource fhirResourceUpdated =
                getFhirResourceBuilder()
                        .setData(addCompletedStatus(getFhirResource().getData()))
                        .build();
        UpsertMedicalResourceInternalRequest originalUpsertRequest =
                makeUpsertRequest(
                        originalFhirResource,
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        FHIR_VERSION_R4,
                        dataSource.getId());
        MedicalResource expectedUpdatedMedicalResource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
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
        assertThat(indicesResult).containsExactly(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_updateSingleMedicalResource_success()
            throws JSONException {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource originalResource =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(originalResource);
        FhirResource updatedFhirResource = getUpdatedImmunizationFhirResource();
        MedicalResource updatedResource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                updatedFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated1 =
                makeUpsertRequest(updatedResource1);
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
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
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_updateMultipleMedicalResources_success()
            throws JSONException {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        FhirResource fhirResource = getFhirResource();
        MedicalResource resource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                fhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest1 =
                makeUpsertRequest(resource1);
        FhirResource updatedFhirResource = getUpdatedImmunizationFhirResource();
        MedicalResource updatedResource1 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                updatedFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequestUpdated1 =
                makeUpsertRequest(updatedResource1);
        FhirResource allergyFhirResource = getFhirResourceAllergy();
        MedicalResource resource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                allergyFhirResource)
                        .build();
        UpsertMedicalResourceInternalRequest upsertMedicalResourceInternalRequest2 =
                makeUpsertRequest(resource2);
        FhirResource updatedAllergyFhirResource = getUpdatedAllergyFhirResource();
        MedicalResource updatedResource2 =
                new MedicalResource.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
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
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResources_readByRequest_success() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        List<MedicalResource> resources =
                createImmunizationMedicalResources(/* numOfResources= */ 6, dataSource.getId());
        List<UpsertMedicalResourceInternalRequest> upsertRequests =
                createUpsertMedicalResourceRequests(resources, dataSource.getId());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, upsertRequests);
        assertThat(upsertedMedicalResources).containsExactlyElementsIn(resources);

        ReadMedicalResourcesInitialRequest initialRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(2)
                        .build();
        PhrPageTokenWrapper pageTokenWrapper = PhrPageTokenWrapper.from(initialRequest.toParcel());
        ReadMedicalResourcesInternalResponse initialResult =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        pageTokenWrapper, initialRequest.getPageSize());
        String pageToken1 = initialResult.getPageToken();
        assertThat(initialResult.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(0), resources.get(1)));
        assertThat(pageToken1).isNotEmpty();
        assertThat(pageToken1)
                .isEqualTo(pageTokenWrapper.cloneWithNewLastRowId(/* lastRowId= */ 2).encode());
        assertThat(initialResult.getRemainingCount()).isEqualTo(4);

        ReadMedicalResourcesPageRequest pageRequest1 =
                new ReadMedicalResourcesPageRequest.Builder(pageToken1).setPageSize(2).build();
        PhrPageTokenWrapper pageTokenWrapper1 = PhrPageTokenWrapper.from(pageRequest1.toParcel());
        ReadMedicalResourcesInternalResponse pageResult1 =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        pageTokenWrapper1, pageRequest1.getPageSize());
        String pageToken2 = pageResult1.getPageToken();
        assertThat(pageResult1.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(2), resources.get(3)));
        assertThat(pageToken2).isNotEmpty();
        assertThat(pageToken2)
                .isEqualTo(pageTokenWrapper1.cloneWithNewLastRowId(/* lastRowId= */ 4).encode());
        assertThat(pageResult1.getRemainingCount()).isEqualTo(2);

        ReadMedicalResourcesPageRequest pageRequest2 =
                new ReadMedicalResourcesPageRequest.Builder(pageToken2).setPageSize(2).build();
        PhrPageTokenWrapper pageTokenWrapper2 = PhrPageTokenWrapper.from(pageRequest2.toParcel());
        ReadMedicalResourcesInternalResponse result2 =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        pageTokenWrapper2, pageRequest2.getPageSize());
        String pageToken3 = result2.getPageToken();
        assertThat(result2.getMedicalResources())
                .containsExactlyElementsIn(List.of(resources.get(4), resources.get(5)));
        assertThat(pageToken3).isNull();
        assertThat(result2.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void insertMultipleMedicalResourcesReadByRequest_pageSizeLargerThanResources_success() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        List<MedicalResource> resources =
                createImmunizationMedicalResources(/* numOfResources= */ 6, dataSource.getId());
        List<UpsertMedicalResourceInternalRequest> requests =
                createUpsertMedicalResourceRequests(resources, dataSource.getId());

        List<MedicalResource> upsertedMedicalResources =
                mMedicalResourceHelper.upsertMedicalResources(DATA_SOURCE_PACKAGE_NAME, requests);
        ReadMedicalResourcesInitialRequest readRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(10)
                        .build();
        ReadMedicalResourcesInternalResponse result =
                mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                        PhrPageTokenWrapper.from(readRequest.toParcel()),
                        readRequest.getPageSize());

        assertThat(upsertedMedicalResources).containsExactlyElementsIn(resources);
        assertThat(result.getMedicalResources()).containsExactlyElementsIn(resources);
        assertThat(result.getPageToken()).isEqualTo(null);
        assertThat(result.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void readMedicalResourcedByRequest_invalidPageToken_throws() {
        ReadMedicalResourcesPageRequest request =
                new ReadMedicalResourcesPageRequest.Builder(INVALID_PAGE_TOKEN).build();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mMedicalResourceHelper.readMedicalResourcesByRequestWithoutPermissionChecks(
                                PhrPageTokenWrapper.from(request.toParcel()),
                                request.getPageSize()));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_appIdDoesNotExist_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithPermissionChecks(
                                List.of(getMedicalResourceId()), "fake.package.com"));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_withPackageName_noDataDeleted_noDeleteAccessLogs() {
        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithPermissionChecks(
                List.of(getMedicalResourceId()), DATA_SOURCE_PACKAGE_NAME);

        assertThat(mAccessLogsHelper.queryAccessLogs()).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIdsWithoutPermissionChecks_noDeleteAccessLogs() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource resource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithoutPermissionChecks(
                List.of(resource1.getId()));

        AccessLog deleteAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_DELETE,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .doesNotContain(deleteAccessLog);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteByIdsWithPermissionChecks_resourcesWithDifferentPackages_correctAccessLogs() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationPackage1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource unknownResourcePackage2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunizationPackage1.getId(), unknownResourcePackage2.getId()),
                /* packageName= */ DATA_SOURCE_PACKAGE_NAME);

        // In this test, we have inserted two different resource types from different packages.
        // When the calling app, calls the delete API, we expect access log to be created only
        // for the deleted resource type. In this case it would be: immunizationPackage1
        AccessLog deleteAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_DELETE,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(deleteAccessLog);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteByIds_withPackageName_resourcesWithSamePackages_correctAccessLogs() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationPackage1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource unknownResourcePackage1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);
        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithPermissionChecks(
                List.of(immunizationPackage1.getId(), unknownResourcePackage1.getId()),
                /* packageName= */ DATA_SOURCE_PACKAGE_NAME);

        // In this test, we have inserted two different resource types from the same package.
        // When the calling app, calls the delete API, we expect access log to be created
        // for the deleted resource types. In this case it would be: immunizationPackage1,
        // allergyResourcePackage1
        AccessLog deleteAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_DELETE,
                        /* medicalResourceTypes= */ Set.of(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(deleteAccessLog);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_noId_fails() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithoutPermissionChecks(
                                List.of()));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneIdNotPresent_succeeds() {
        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithoutPermissionChecks(
                List.of(
                        new MedicalResourceId(
                                DATA_SOURCE_ID,
                                FHIR_RESOURCE_TYPE_IMMUNIZATION,
                                FHIR_RESOURCE_ID_IMMUNIZATION)));
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneIdPresent_succeedsDeleting() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource medicalResource1 =
                mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource);

        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithoutPermissionChecks(
                List.of(medicalResource1.getId()));

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResource1.getId()));
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        assertThat(result).isEmpty();
        assertThat(indicesResult).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneOfTwoSpecified_onlySpecifiedDeleted() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource medicalResource1 =
                mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource);
        MedicalResource medicalResource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource);

        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithoutPermissionChecks(
                List.of(medicalResource1.getId()));
        List<Integer> indicesResult = readEntriesInMedicalResourceIndicesTable();

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResource1.getId(), medicalResource2.getId()));
        assertThat(result).containsExactly(medicalResource2);
        assertThat(indicesResult).containsExactly(MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneOfTwoSpecifiedWrongPackage_nothingDeleted() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource medicalResource1 =
                mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource);
        MedicalResource medicalResource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource);

        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithPermissionChecks(
                List.of(medicalResource1.getId()), DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResource1.getId(), medicalResource2.getId()));
        assertThat(result).containsExactly(medicalResource1, medicalResource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_oneOfTwoSpecifiedRightPackage_oneOfTwo() {
        MedicalDataSource dataSource =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource medicalResource1 =
                mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource);
        MedicalResource medicalResource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource);

        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithPermissionChecks(
                List.of(medicalResource1.getId()), DATA_SOURCE_PACKAGE_NAME);

        List<MedicalResource> result =
                mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(medicalResource1.getId(), medicalResource2.getId()));
        assertThat(result).containsExactly(medicalResource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByIds_multipleIdsFromDifferentPackages_succeeds() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalResource expectedResource1Source1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource expectedResource1Source2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource expectedResource2Source1 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        MedicalResource expectedResource2Source2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);

        mMedicalResourceHelper.deleteMedicalResourcesByIdsWithoutPermissionChecks(
                List.of(expectedResource1Source1.getId(), expectedResource2Source2.getId()));

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
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByDataSources_singleDataSource_succeeds() {
        // Create two datasources, with one resource each.
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource dataSource1Resource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource dataSource2Resource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource dataSource1Resource2 =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);

        // Delete all of the data for just the first datasource
        mMedicalResourceHelper.deleteMedicalResourcesByRequestWithoutPermissionChecks(
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(dataSource1.getId())
                        .build());

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

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteByDataSources_expectAccessLogsWhenBothDataSourcesAreFromCallingPackage() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);

        // Both created dataSources are from the same calling app.
        // So when the calling app deletes medicalResources from both those dataSources,
        // resourceTypes for both should be included in the accessLogs.
        mMedicalResourceHelper.deleteMedicalResourcesByRequestWithPermissionChecks(
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(dataSource1.getId())
                        .addDataSourceId(dataSource2.getId())
                        .build(),
                /* callingPackageName= */ DATA_SOURCE_PACKAGE_NAME);

        AccessLog deleteAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_DELETE,
                        /* medicalResourceTypes= */ Set.of(
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(deleteAccessLog);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByRequest_singleResourceType_succeeds() {
        // Create two datasources, with one resource each.
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationResourceDataSource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource immunizationResourceDataSource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyResource =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);

        // Delete all of the data for just the first datasource
        mMedicalResourceHelper.deleteMedicalResourcesByRequestWithoutPermissionChecks(
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build());

        // Test that the data for the immunizations are gone, but the allergy is still present
        assertThat(
                        mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                                List.of(
                                        immunizationResourceDataSource1.getId(),
                                        immunizationResourceDataSource2.getId())))
                .hasSize(0);
        assertThat(
                        mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                                List.of(allergyResource.getId())))
                .hasSize(1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteByRequest_expectAccessLogsWhenDataSourcesFromDifferentPackages() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);

        // The created dataSources are from different calling apps.
        // When the first calling app tries to delete resources given both dataSources,
        // only the resources belonging to the dataSource of the calling app will
        // be deleted. So accessLogs are added only for the deleted resourceTypes which would
        // be resourceTypes belonging to dataSource1.
        mMedicalResourceHelper.deleteMedicalResourcesByRequestWithPermissionChecks(
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(dataSource1.getId())
                        .addDataSourceId(dataSource2.getId())
                        .build(),
                /* callingPackageName= */ DATA_SOURCE_PACKAGE_NAME);

        AccessLog deleteAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_DELETE,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .contains(deleteAccessLog);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteByDataSources_noDataDeleted_noAccessLogsCreated() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);

        mMedicalResourceHelper.deleteMedicalResourcesByRequestWithPermissionChecks(
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(dataSource1.getId())
                        .build(),
                /* callingPackageName= */ DATA_SOURCE_PACKAGE_NAME);

        AccessLog deleteAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_DELETE,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .doesNotContain(deleteAccessLog);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteByDataSources_withoutPackageRestriction_noAccessLogsCreated() {
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);

        mMedicalResourceHelper.deleteMedicalResourcesByRequestWithoutPermissionChecks(
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(dataSource1.getId())
                        .build());

        AccessLog deleteAccessLog =
                new AccessLog(
                        DATA_SOURCE_PACKAGE_NAME,
                        INSTANT_NOW.toEpochMilli(),
                        OPERATION_TYPE_DELETE,
                        /* medicalResourceTypes= */ Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS),
                        /* isMedicalDataSourceAccessed= */ false);

        assertThat(mAccessLogsHelper.queryAccessLogs())
                .comparingElementsUsing(ACCESS_LOG_EQUIVALENCE)
                .doesNotContain(deleteAccessLog);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void deleteMedicalResourcesByRequest_singleResourceTypeSingleDataSource_succeeds() {
        // Create two datasources, with one resource each.
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        MedicalResource immunizationResourceDataSource1 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        MedicalResource immunizationResourceDataSource2 =
                mUtil.upsertResource(
                        PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        MedicalResource allergyResource =
                mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);

        // Delete all of the data for just the first datasource
        mMedicalResourceHelper.deleteMedicalResourcesByRequestWithoutPermissionChecks(
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(dataSource1.getId())
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build());

        // Test that the data for the immunizations are gone, but the allergy is still present
        assertThat(
                        mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                                List.of(immunizationResourceDataSource1.getId())))
                .hasSize(0);
        assertThat(
                        mMedicalResourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                                List.of(
                                        allergyResource.getId(),
                                        immunizationResourceDataSource2.getId())))
                .hasSize(2);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void testGetMedicalResourceTypeToContributingDataSourcesMap_success() {
        // Create some data sources with data: ds1 contains [immunization, differentImmunization,
        // allergy], ds2 contains [immunization], and ds3 contains [allergy].
        MedicalDataSource dataSource1 =
                mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertR4MedicalDataSource("ds2", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource3 =
                mUtil.insertR4MedicalDataSource("ds3", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        mUtil.upsertResource(
                PhrDataFactory::createDifferentImmunizationMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource3);

        Map<Integer, Set<MedicalDataSource>> response =
                mMedicalResourceHelper.getMedicalResourceTypeToContributingDataSourcesMap();

        assertThat(response).hasSize(2);
        assertThat(response.keySet())
                .containsExactly(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                        MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES);
        assertThat(response.get(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS))
                .containsExactly(dataSource1, dataSource2);
        assertThat(response.get(MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES))
                .containsExactly(dataSource1, dataSource3);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void testGetMedicalResourceTypeToContributingDataSourcesMap_noDataSources_success() {
        Map<Integer, Set<MedicalDataSource>> response =
                mMedicalResourceHelper.getMedicalResourceTypeToContributingDataSourcesMap();

        assertThat(response).isEmpty();
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void
            testGetMedicalResourceTypeToContributingDataSourcesMap_noMedicalResources_success() {
        mUtil.insertR4MedicalDataSource("ds1", DATA_SOURCE_PACKAGE_NAME);

        Map<Integer, Set<MedicalDataSource>> response =
                mMedicalResourceHelper.getMedicalResourceTypeToContributingDataSourcesMap();

        assertThat(response).isEmpty();
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

    private int getMedicalResourcesTableRowCount() {
        ReadTableRequest readTableRequest =
                new ReadTableRequest(MedicalResourceHelper.getMainTableName());
        try (Cursor cursor = mTransactionManager.read(readTableRequest)) {
            return cursor.getCount();
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
