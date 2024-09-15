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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_DATA_SOURCE_PACKAGE_NAME;

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.DATA_SOURCE_UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.DISPLAY_NAME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.FHIR_BASE_URI_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.MAX_ALLOWED_MEDICAL_DATA_SOURCES;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.MEDICAL_DATA_SOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.LAST_MODIFIED_TIME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorUUID;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getHexString;
import static com.android.server.healthconnect.storage.utils.StorageUtils.toUuids;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.utils.PhrDataFactory;
import android.net.Uri;
import android.os.Environment;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.storage.PhrTestUtils;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MedicalDataSourceHelperTest {

    private static final long APP_INFO_ID = 123;

    // See b/344587256 for more context.
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

    private static final Instant INSTANT_NOW = Instant.now();

    private MedicalDataSourceHelper mMedicalDataSourceHelper;
    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private AppInfoHelper mAppInfoHelper;
    private PhrTestUtils mUtil;
    private FakeTimeSource mFakeTimeSource;
    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private Drawable mDrawable;

    @Before
    public void setup() throws NameNotFoundException {
        mTransactionManager = mHealthConnectDatabaseTestRule.getTransactionManager();
        mAppInfoHelper = AppInfoHelper.getInstance();
        mFakeTimeSource = new FakeTimeSource(INSTANT_NOW);
        mMedicalDataSourceHelper =
                new MedicalDataSourceHelper(mTransactionManager, mAppInfoHelper, mFakeTimeSource);
        // We set the context to null, because we only use insertApp in this set of tests and
        // we don't need context for that.
        mTransactionTestUtils = new TransactionTestUtils(/* context= */ null, mTransactionManager);
        mUtil =
                new PhrTestUtils(
                        mContext,
                        mTransactionManager,
                        new MedicalResourceHelper(
                                mTransactionManager, mMedicalDataSourceHelper, mFakeTimeSource),
                        mMedicalDataSourceHelper);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
    }

    @After
    public void tearDown() throws Exception {
        reset(mDrawable, mContext, mPackageManager);
    }

    @Test
    public void getCreateTableRequest_correctResult() {
        List<Pair<String, String>> columnInfo =
                List.of(
                        Pair.create(MedicalDataSourceHelper.getPrimaryColumnName(), PRIMARY),
                        Pair.create(
                                MedicalDataSourceHelper.getAppInfoIdColumnName(), INTEGER_NOT_NULL),
                        Pair.create(DISPLAY_NAME_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_BASE_URI_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(DATA_SOURCE_UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL),
                        Pair.create(LAST_MODIFIED_TIME_COLUMN_NAME, INTEGER_NOT_NULL));
        CreateTableRequest expected =
                new CreateTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME, columnInfo)
                        .addForeignKey(
                                AppInfoHelper.getInstance().getMainTableName(),
                                List.of(MedicalDataSourceHelper.getAppInfoIdColumnName()),
                                List.of(PRIMARY_COLUMN_NAME));

        CreateTableRequest result = MedicalDataSourceHelper.getCreateTableRequest();

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
                mMedicalDataSourceHelper.getUpsertTableRequest(
                        uuid, createMedicalDataSourceRequest, APP_INFO_ID, INSTANT_NOW);
        ContentValues contentValues = upsertRequest.getContentValues();

        assertThat(upsertRequest.getTable()).isEqualTo(MEDICAL_DATA_SOURCE_TABLE_NAME);
        assertThat(upsertRequest.getUniqueColumnsCount()).isEqualTo(1);
        assertThat(contentValues.size()).isEqualTo(5);
        assertThat(contentValues.get(FHIR_BASE_URI_COLUMN_NAME))
                .isEqualTo(DATA_SOURCE_FHIR_BASE_URI.toString());
        assertThat(contentValues.get(DISPLAY_NAME_COLUMN_NAME)).isEqualTo(DATA_SOURCE_DISPLAY_NAME);
        assertThat(contentValues.get(DATA_SOURCE_UUID_COLUMN_NAME))
                .isEqualTo(StorageUtils.convertUUIDToBytes(uuid));
        assertThat(contentValues.get(MedicalDataSourceHelper.getAppInfoIdColumnName()))
                .isEqualTo(APP_INFO_ID);
        assertThat(contentValues.get(LAST_MODIFIED_TIME_COLUMN_NAME))
                .isEqualTo(INSTANT_NOW.toEpochMilli());
    }

    @Test
    public void getReadTableRequest_usingMedicalDataSourceId_correctQuery() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        List<String> hexValues = List.of(getHexString(uuid1), getHexString(uuid2));

        ReadTableRequest readRequest =
                MedicalDataSourceHelper.getReadTableRequest(List.of(uuid1, uuid2));

        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_DATA_SOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM medical_data_source_table WHERE data_source_uuid IN ("
                                + String.join(", ", hexValues)
                                + ")");
    }

    @Test
    public void getReadTableRequestJoinWithAppInfo_usingMedicalDataSourceId_correctQuery() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        List<String> hexValues = List.of(getHexString(uuid1), getHexString(uuid2));

        ReadTableRequest readRequest =
                MedicalDataSourceHelper.getReadTableRequestJoinWithAppInfo(List.of(uuid1, uuid2));

        assertThat(readRequest.getTableName()).isEqualTo(MEDICAL_DATA_SOURCE_TABLE_NAME);
        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM ( SELECT * FROM medical_data_source_table WHERE"
                                + " data_source_uuid IN ("
                                + String.join(", ", hexValues)
                                + ") ) AS inner_query_result  INNER JOIN application_info_table ON"
                                + " inner_query_result.app_info_id ="
                                + " application_info_table.row_id");
    }

    @Test
    public void getReadTableRequestForDataSourceWrittenByAppIdFilterOnResourceTypes_correctQuery() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        List<String> hexValues = List.of(getHexString(uuid1), getHexString(uuid2));

        ReadTableRequest readRequest =
                MedicalDataSourceHelper
                        .getReadTableRequestForDataSourceWrittenByAppIdFilterOnResourceTypes(
                                List.of(uuid1, uuid2),
                                Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                                /* appId= */ 123L);

        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT DISTINCT package_name,data_source_uuid,fhir_base_uri,display_name"
                            + " FROM ( SELECT * FROM medical_resource_table ) AS inner_query_result"
                            + "  INNER JOIN ( SELECT * FROM medical_data_source_table WHERE"
                            + " app_info_id = '123' AND data_source_uuid IN ("
                                + String.join(", ", hexValues)
                                + ")) medical_data_source_table ON"
                                + " inner_query_result.data_source_id ="
                                + " medical_data_source_table.medical_data_source_row_id  INNER"
                                + " JOIN application_info_table ON"
                                + " medical_data_source_table.app_info_id ="
                                + " application_info_table.row_id  INNER JOIN ( SELECT * FROM"
                                + " medical_resource_indices_table WHERE medical_resource_type IN"
                                + " (1)) medical_resource_indices_table ON"
                                + " inner_query_result.medical_resource_row_id ="
                                + " medical_resource_indices_table.medical_resource_id");
    }

    @Test
    public void getReadTableRequestForDataSourcesFilterOnResourceTypes_correctQuery() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        List<String> hexValues = List.of(getHexString(uuid1), getHexString(uuid2));

        ReadTableRequest readRequest =
                MedicalDataSourceHelper.getReadTableRequestForDataSourcesFilterOnResourceTypes(
                        List.of(uuid1, uuid2), Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION));

        assertThat(readRequest.getReadCommand())
                .isEqualTo(
                        "SELECT DISTINCT package_name,data_source_uuid,fhir_base_uri,display_name"
                            + " FROM ( SELECT * FROM medical_resource_table ) AS inner_query_result"
                            + "  INNER JOIN ( SELECT * FROM medical_data_source_table WHERE"
                            + " data_source_uuid IN ("
                                + String.join(", ", hexValues)
                                + ")) medical_data_source_table ON"
                                + " inner_query_result.data_source_id ="
                                + " medical_data_source_table.medical_data_source_row_id  INNER"
                                + " JOIN application_info_table ON"
                                + " medical_data_source_table.app_info_id ="
                                + " application_info_table.row_id  INNER JOIN ( SELECT * FROM"
                                + " medical_resource_indices_table WHERE medical_resource_type IN"
                                + " (1)) medical_resource_indices_table ON"
                                + " inner_query_result.medical_resource_row_id ="
                                + " medical_resource_indices_table.medical_resource_id");
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void getReadTableRequest_noRestrictions_success() throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource expected =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);

        ReadTableRequest request =
                MedicalDataSourceHelper.getReadTableRequest(
                        List.of(UUID.fromString(expected.getId())), /* appInfoRestriction= */ null);

        try (Cursor cursor = mTransactionManager.read(request)) {
            assertThat(getIds(cursor)).containsExactly(expected.getId());
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void getReadTableRequest_packageRestrictionMatches_success()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource correctDataSource =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);

        long appInfoRestriction = mAppInfoHelper.getAppInfoId(DATA_SOURCE_PACKAGE_NAME);
        ReadTableRequest request =
                MedicalDataSourceHelper.getReadTableRequest(
                        List.of(UUID.fromString(correctDataSource.getId())), appInfoRestriction);

        try (Cursor cursor = mTransactionManager.read(request)) {
            assertThat(getIds(cursor)).containsExactly(correctDataSource.getId());
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void getReadTableRequest_packageRestrictionDoesNotMatch_noResult()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource correctDataSource =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        createDataSource(
                DIFFERENT_DATA_SOURCE_BASE_URI,
                DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        long appInfoRestriction = mAppInfoHelper.getAppInfoId(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        ReadTableRequest request =
                MedicalDataSourceHelper.getReadTableRequest(
                        List.of(UUID.fromString(correctDataSource.getId())), appInfoRestriction);

        try (Cursor cursor = mTransactionManager.read(request)) {
            assertThat(getIds(cursor)).isEmpty();
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void getReadTableRequest_noIdsPackageRestrictionMatches_succeeds()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        createDataSource(
                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME, DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource otherDataSource =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        long appInfoRestriction = mAppInfoHelper.getAppInfoId(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        ReadTableRequest request =
                MedicalDataSourceHelper.getReadTableRequest(List.of(), appInfoRestriction);

        try (Cursor cursor = mTransactionManager.read(request)) {
            assertThat(getIds(cursor)).containsExactly(otherDataSource.getId());
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void getReadTableRequest_noIdsNoPackageRestrictionMatches_succeeds()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource otherDataSource =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        ReadTableRequest request =
                MedicalDataSourceHelper.getReadTableRequest(
                        List.of(), /* appInfoIdRestriction= */ null);

        try (Cursor cursor = mTransactionManager.read(request)) {
            assertThat(getIds(cursor)).containsExactly(dataSource.getId(), otherDataSource.getId());
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void createAndGetSingleMedicalDataSource_packageDoesNotExist_success()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource expected =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        List.of(UUID.fromString(expected.getId())));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0)).isEqualTo(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void createAndGetSingleMedicalDataSource_packageAlreadyExists_success() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        List.of(UUID.fromString(dataSource1.getId())));

        assertThat(result).containsExactly(dataSource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void createMedicalDataSource_lastModifiedTimeIsPopulated() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        createDataSource(
                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME, DATA_SOURCE_PACKAGE_NAME);

        long lastModifiedTimestamp =
                mUtil.readLastModifiedTimestamp(MEDICAL_DATA_SOURCE_TABLE_NAME);

        assertThat(lastModifiedTimestamp).isEqualTo(INSTANT_NOW.toEpochMilli());
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void createAndGetMultipleMedicalDataSources_bothPackagesAlreadyExist_success() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        mTransactionTestUtils.insertApp(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())));

        assertThat(result).containsExactly(dataSource1, dataSource2);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void createAndGetMultipleMedicalDataSourcesWithSamePackage_packageDoesNotExist_success()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        List<MedicalDataSource> expected = List.of(dataSource1, dataSource2);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())));

        assertThat(result).hasSize(2);
        assertThat(result).containsExactlyElementsIn(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void
            createAndGetMultipleMedicalDataSourcesWithDifferentPackages_packagesDoNotExist_success()
                    throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())));

        assertThat(result).containsExactly(dataSource1, dataSource2);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void createMultipleMedicalDataSources_maxLimitExceeded_throws()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        for (int i = 0; i < MAX_ALLOWED_MEDICAL_DATA_SOURCES; i++) {
            String suffix = String.valueOf(i);
            createDataSource(
                    Uri.withAppendedPath(DATA_SOURCE_FHIR_BASE_URI, "/" + suffix),
                    DATA_SOURCE_DISPLAY_NAME + " " + suffix,
                    DATA_SOURCE_PACKAGE_NAME);
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    createDataSource(
                            DATA_SOURCE_FHIR_BASE_URI,
                            DATA_SOURCE_DISPLAY_NAME,
                            DATA_SOURCE_PACKAGE_NAME);
                });
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void getMedicalDataSourcesByPackage_noPackages_returnsAll() throws Exception {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> dataSources =
                mMedicalDataSourceHelper.getMedicalDataSourcesByPackageWithoutPermissionChecks(
                        Set.of());

        assertThat(dataSources).containsExactly(dataSource1, dataSource2);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void getMedicalDataSourcesByPackage_onePackage_filters() throws Exception {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> dataSources1 =
                mMedicalDataSourceHelper.getMedicalDataSourcesByPackageWithoutPermissionChecks(
                        Set.of(DATA_SOURCE_PACKAGE_NAME));
        List<MedicalDataSource> dataSources2 =
                mMedicalDataSourceHelper.getMedicalDataSourcesByPackageWithoutPermissionChecks(
                        Set.of(DIFFERENT_DATA_SOURCE_PACKAGE_NAME));

        assertThat(dataSources1).containsExactly(dataSource1);
        assertThat(dataSources2).containsExactly(dataSource2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getDataSourcesByIds_noWriteOrReadPerm_throws() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                            /* ids= */ List.of(),
                            /* grantedMedicalResourceTypes= */ Set.of(),
                            DATA_SOURCE_PACKAGE_NAME,
                            /* hasWritePermission= */ false,
                            /* isCalledFromBgWithoutBgRead= */ false);
                });
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getDataSourcesByIds_hasWritePermButNeverWrittenData_noReadPerm_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                            /* ids= */ List.of(),
                            /* grantedMedicalResourceTypes= */ Set.of(),
                            DATA_SOURCE_PACKAGE_NAME,
                            /* hasWritePermission= */ true,
                            /* isCalledFromBgWithoutBgRead= */ false);
                });
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getDataSourcesByIds_inBgWithoutBgPermHasWritePerm_canReadSelfDataSources() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1 =
                mUtil.insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())),
                        /* grantedMedicalResourceTypes= */ Set.of(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(dataSource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getByIds_inBgWithoutBgPermHasWritePermHasReadPerm_canReadSelfDataSources() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1 =
                mUtil.insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())),
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(dataSource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getByIds_inBgWithoutBgPermNoWritePermImmunizationReadPermOnly_correctResult() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1 =
                mUtil.insertMedicalDataSource("ds", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mUtil.insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1);
        mUtil.upsertResource(PhrDataFactory::createImmunizationMedicalResource, dataSource2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2);

        // App is in background without background read perm, no write permission but has
        // immunization read permission. App can read dataSources belonging to immunizations that
        // the app wrote itself.
        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())),
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(dataSource1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void
            getById_inBgWithoutBgPermNoWritePermBothAllergyAndImmunizationReadPerm_correctResult() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1Package1 =
                mUtil.insertMedicalDataSource("ds/1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package1 =
                mUtil.insertMedicalDataSource("ds/2", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1Package2 =
                mUtil.insertMedicalDataSource("ds", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package1);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource1Package2);

        // App is in background without background read perm, no write permission but has
        // immunization read permission. App can read dataSources belonging to immunizations
        // and allergy resource types that the app wrote itself.
        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(
                                List.of(
                                        dataSource1Package1.getId(),
                                        dataSource2Package1.getId(),
                                        dataSource1Package2.getId())),
                        Set.of(
                                MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE,
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ true);

        assertThat(result).containsExactly(dataSource1Package1, dataSource2Package1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getById_inForegroundOrinBgWithBgPermNoWritePermHasImmunizationPerm_correctResult() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1Package1 =
                mUtil.insertMedicalDataSource("ds/1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package1 =
                mUtil.insertMedicalDataSource("ds/2", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1Package2 =
                mUtil.insertMedicalDataSource("ds/3", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package2 =
                mUtil.insertMedicalDataSource("ds/4", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package1);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package2);

        // App is in foreground or background with background read perm, no write permission but has
        // immunization read permission. App can read all dataSources belonging to immunizations.
        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(
                                List.of(
                                        dataSource1Package1.getId(), dataSource2Package1.getId(),
                                        dataSource1Package2.getId(), dataSource2Package2.getId())),
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(dataSource1Package1, dataSource1Package2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void
            getByIds_inForegroundOrBgWithBgPermHasWritePermNoReadResourceTypesPerm_correctResult() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1Package1 =
                mUtil.insertMedicalDataSource("ds/1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package1 =
                mUtil.insertMedicalDataSource("ds/2", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1Package2 =
                mUtil.insertMedicalDataSource("ds/3", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package2 =
                mUtil.insertMedicalDataSource("ds/4", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package1);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package2);

        // App is in foreground or background with background read perm, has write permission but
        // no read permission for any resource types.
        // App can read only read dataSources they wrote themselves.
        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(
                                List.of(
                                        dataSource1Package1.getId(), dataSource2Package1.getId(),
                                        dataSource1Package2.getId(), dataSource2Package2.getId())),
                        /* grantedMedicalResourceTypes= */ Set.of(),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(dataSource1Package1, dataSource2Package1);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getByIds_inForegroundOrBgWithBgPermNoWritePermHasAllergyPerm_correctResult() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1Package1 =
                mUtil.insertMedicalDataSource("ds/1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package1 =
                mUtil.insertMedicalDataSource("ds/2", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1Package2 =
                mUtil.insertMedicalDataSource("ds/3", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package2 =
                mUtil.insertMedicalDataSource("ds/4", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package1);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package2);

        // App is in foreground or background with background read perm, no write permission but
        // has allergy resource type read permission.
        // App can read only read dataSources belonging to the allergy resource types.
        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(
                                List.of(
                                        dataSource1Package1.getId(), dataSource2Package1.getId(),
                                        dataSource1Package2.getId(), dataSource2Package2.getId())),
                        Set.of(MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result).containsExactly(dataSource2Package1, dataSource2Package2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void getByIds_inForegroundOrBgWithBgPermNoWritePermMultipleReadPerms_correctResult() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1Package1 =
                mUtil.insertMedicalDataSource("ds/1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package1 =
                mUtil.insertMedicalDataSource("ds/2", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1Package2 =
                mUtil.insertMedicalDataSource("ds/3", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package2 =
                mUtil.insertMedicalDataSource("ds/4", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package1);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package2);

        // App is in foreground or background with background read perm, no write permission but
        // has allergy resource type and immunization read permissions.
        // App can read dataSources belonging to allergy and immunization resource types.
        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(
                                List.of(
                                        dataSource1Package1.getId(), dataSource2Package1.getId(),
                                        dataSource1Package2.getId(), dataSource2Package2.getId())),
                        Set.of(
                                MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE,
                                MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ false,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result)
                .containsExactly(
                        dataSource1Package1,
                        dataSource2Package1,
                        dataSource1Package2,
                        dataSource2Package2);
    }

    @Test
    @EnableFlags({Flags.FLAG_DEVELOPMENT_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void
            getByIds_inForegroundOrBgWithBgPermHasWritePermHasReadImmunizationPerm_correctResult() {
        insertApps(List.of(DATA_SOURCE_PACKAGE_NAME, DIFFERENT_DATA_SOURCE_PACKAGE_NAME));
        MedicalDataSource dataSource1Package1 =
                mUtil.insertMedicalDataSource("ds/1", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package1 =
                mUtil.insertMedicalDataSource("ds/2", DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1Package2 =
                mUtil.insertMedicalDataSource("ds/3", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2Package2 =
                mUtil.insertMedicalDataSource("ds/4", DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package1);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package1);
        mUtil.upsertResource(
                PhrDataFactory::createImmunizationMedicalResource, dataSource1Package2);
        mUtil.upsertResource(PhrDataFactory::createAllergyMedicalResource, dataSource2Package2);

        // App is in foreground or background with background read perm, has write permission and
        // has immunization read permissions.
        // App can read dataSources they wrote themselves and dataSources belonging to
        // immunization resource types.
        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithPermissionChecks(
                        toUuids(
                                List.of(
                                        dataSource1Package1.getId(), dataSource2Package1.getId(),
                                        dataSource1Package2.getId(), dataSource2Package2.getId())),
                        Set.of(MEDICAL_RESOURCE_TYPE_IMMUNIZATION),
                        DATA_SOURCE_PACKAGE_NAME,
                        /* hasWritePermission= */ true,
                        /* isCalledFromBgWithoutBgRead= */ false);

        assertThat(result)
                .containsExactly(dataSource1Package1, dataSource2Package1, dataSource1Package2);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void delete_badId_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mMedicalDataSourceHelper.deleteMedicalDataSource(
                            UUID.randomUUID(), /* appInfoIdRestriction= */ null);
                });
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void delete_badId_leavesRecordsUnchanged() throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource existing =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mMedicalDataSourceHelper.deleteMedicalDataSource(
                            UUID.randomUUID(), /* appInfoIdRestriction= */ null);
                });

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        List.of(UUID.fromString(existing.getId())));
        assertThat(result).containsExactly(existing);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void delete_oneIdWrongPackage_existingDataUnchanged() throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource existing =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource different =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        long differentAppInfoId = mAppInfoHelper.getAppInfoId(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mMedicalDataSourceHelper.deleteMedicalDataSource(
                                UUID.fromString(existing.getId()), differentAppInfoId));

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        toUuids(List.of(existing.getId(), different.getId())));
        assertThat(result).containsExactly(existing, different);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void delete_oneId_existingDataDeleted() throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource existing =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        UUID existingUuid = UUID.fromString(existing.getId());

        mMedicalDataSourceHelper.deleteMedicalDataSource(
                existingUuid, /* appInfoIdRestriction= */ null);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        List.of(existingUuid));
        assertThat(result).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void delete_multiplePresentOneIdRequestedNoAppRestriction_onlyRequestedDeleted()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        mMedicalDataSourceHelper.deleteMedicalDataSource(
                UUID.fromString(dataSource1.getId()), /* appInfoIdRestriction= */ null);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())));
        assertThat(result).containsExactly(dataSource2);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void delete_multiplePresentOneIdRequestedMatchingAppId_onlyRequestedDeleted()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        mMedicalDataSourceHelper.deleteMedicalDataSource(
                UUID.fromString(dataSource1.getId()),
                mAppInfoHelper.getAppInfoId(DATA_SOURCE_PACKAGE_NAME));

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())));
        assertThat(result).containsExactly(dataSource2);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void delete_multiplePresentOneIdRequestedDifferentAppId_onlyRequestedDeleted()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource1 =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                createDataSource(
                        DIFFERENT_DATA_SOURCE_BASE_URI,
                        DIFFERENT_DATA_SOURCE_DISPLAY_NAME,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mMedicalDataSourceHelper.deleteMedicalDataSource(
                                UUID.fromString(dataSource1.getId()),
                                mAppInfoHelper.getAppInfoId(DIFFERENT_DATA_SOURCE_PACKAGE_NAME)));

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        toUuids(List.of(dataSource1.getId(), dataSource2.getId())));
        assertThat(result).containsExactly(dataSource1, dataSource2);
    }

    @Test
    @EnableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void delete_removesAssociatedResource() throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource =
                createDataSource(
                        DATA_SOURCE_FHIR_BASE_URI,
                        DATA_SOURCE_DISPLAY_NAME,
                        DATA_SOURCE_PACKAGE_NAME);
        MedicalResourceHelper resourceHelper =
                new MedicalResourceHelper(
                        mTransactionManager,
                        mMedicalDataSourceHelper,
                        new FakeTimeSource(INSTANT_NOW));
        MedicalResource medicalResource =
                PhrDataFactory.createImmunizationMedicalResource(dataSource.getId());
        UpsertMedicalResourceInternalRequest upsertRequest =
                new UpsertMedicalResourceInternalRequest()
                        .setMedicalResourceType(medicalResource.getType())
                        .setFhirResourceId(medicalResource.getFhirResource().getId())
                        .setFhirResourceType(medicalResource.getFhirResource().getType())
                        .setFhirVersion(medicalResource.getFhirVersion())
                        .setData(medicalResource.getFhirResource().getData())
                        .setDataSourceId(dataSource.getId());
        MedicalResource resource =
                resourceHelper
                        .upsertMedicalResources(DATA_SOURCE_PACKAGE_NAME, List.of(upsertRequest))
                        .get(0);

        mMedicalDataSourceHelper.deleteMedicalDataSource(
                UUID.fromString(dataSource.getId()), /* appInfoIdRestriction= */ null);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSourcesByIdsWithoutPermissionChecks(
                        toUuids(List.of(dataSource.getId())));
        assertThat(result).isEmpty();
        List<MedicalResource> resourceResult =
                resourceHelper.readMedicalResourcesByIdsWithoutPermissionChecks(
                        List.of(resource.getId()));
        assertThat(resourceResult).isEmpty();
    }

    private void setUpMocksForAppInfo(String packageName) throws NameNotFoundException {
        ApplicationInfo appInfo = getApplicationInfo(packageName);
        when(mPackageManager.getApplicationInfo(eq(packageName), any())).thenReturn(appInfo);
        when(mPackageManager.getApplicationLabel(eq(appInfo))).thenReturn(packageName);
        when(mPackageManager.getApplicationIcon((ApplicationInfo) any())).thenReturn(mDrawable);
        when(mDrawable.getIntrinsicHeight()).thenReturn(200);
        when(mDrawable.getIntrinsicWidth()).thenReturn(200);
    }

    private ApplicationInfo getApplicationInfo(String packageName) {
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.packageName = packageName;
        return appInfo;
    }

    private @NonNull MedicalDataSource createDataSource(
            Uri baseUri, String displayName, String packageName) {
        CreateMedicalDataSourceRequest request =
                new CreateMedicalDataSourceRequest.Builder(baseUri, displayName).build();
        return mMedicalDataSourceHelper.createMedicalDataSource(mContext, request, packageName);
    }

    private void insertApps(List<String> packageNames) {
        for (String packageName : packageNames) {
            mTransactionTestUtils.insertApp(packageName);
        }
    }

    private static List<String> getIds(Cursor cursor) {
        ArrayList<String> result = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                UUID uuid = getCursorUUID(cursor, DATA_SOURCE_UUID_COLUMN_NAME);
                result.add(uuid.toString());
            } while (cursor.moveToNext());
        }
        return result;
    }
}
