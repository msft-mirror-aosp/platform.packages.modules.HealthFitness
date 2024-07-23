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

import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.APP_INFO_ID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.DATA_SOURCE_UUID_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.DISPLAY_NAME_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.FHIR_BASE_URI_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.MEDICAL_DATA_SOURCE_TABLE_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.getCreateTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.getReadTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.getReadTableRequestJoinWithAppInfo;
import static com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper.getUpsertTableRequest;
import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BLOB_UNIQUE_NON_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getHexString;

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
import android.graphics.drawable.Drawable;
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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.Mock;

import java.util.List;
import java.util.UUID;

public class MedicalDataSourceHelperTest {
    private final HealthConnectDatabaseTestRule mHealthConnectDatabaseTestRule =
            new HealthConnectDatabaseTestRule(this);

    // See b/344587256 for more context.
    @Rule
    public TestRule chain =
            RuleChain.outerRule(new SetFlagsRule()).around(mHealthConnectDatabaseTestRule);

    private MedicalDataSourceHelper mMedicalDataSourceHelper;
    private TransactionTestUtils mTransactionTestUtils;
    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private Drawable mDrawable;
    private static final long APP_INFO_ID = 123;

    @Before
    public void setup() throws NameNotFoundException {
        TransactionManager mTransactionManager =
                mHealthConnectDatabaseTestRule.getTransactionManager();
        mMedicalDataSourceHelper =
                new MedicalDataSourceHelper(mTransactionManager, AppInfoHelper.getInstance());
        // We set the context to null, because we only use insertApp in this set of tests and
        // we don't need context for that.
        mTransactionTestUtils = new TransactionTestUtils(/* context= */ null, mTransactionManager);
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
                        Pair.create(PRIMARY_COLUMN_NAME, PRIMARY),
                        Pair.create(APP_INFO_ID_COLUMN_NAME, INTEGER_NOT_NULL),
                        Pair.create(DISPLAY_NAME_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(FHIR_BASE_URI_COLUMN_NAME, TEXT_NOT_NULL),
                        Pair.create(DATA_SOURCE_UUID_COLUMN_NAME, BLOB_UNIQUE_NON_NULL));
        CreateTableRequest expected =
                new CreateTableRequest(MEDICAL_DATA_SOURCE_TABLE_NAME, columnInfo)
                        .addForeignKey(
                                AppInfoHelper.getInstance().getMainTableName(),
                                List.of(APP_INFO_ID_COLUMN_NAME),
                                List.of(PRIMARY_COLUMN_NAME));

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
                getUpsertTableRequest(uuid, createMedicalDataSourceRequest, APP_INFO_ID);
        ContentValues contentValues = upsertRequest.getContentValues();

        assertThat(upsertRequest.getTable()).isEqualTo(MEDICAL_DATA_SOURCE_TABLE_NAME);
        assertThat(upsertRequest.getUniqueColumnsCount()).isEqualTo(1);
        assertThat(contentValues.size()).isEqualTo(4);
        assertThat(contentValues.get(FHIR_BASE_URI_COLUMN_NAME))
                .isEqualTo(DATA_SOURCE_FHIR_BASE_URI);
        assertThat(contentValues.get(DISPLAY_NAME_COLUMN_NAME)).isEqualTo(DATA_SOURCE_DISPLAY_NAME);
        assertThat(contentValues.get(DATA_SOURCE_UUID_COLUMN_NAME))
                .isEqualTo(StorageUtils.convertUUIDToBytes(uuid));
        assertThat(contentValues.get(APP_INFO_ID_COLUMN_NAME)).isEqualTo(APP_INFO_ID);
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
    public void getReadTableRequestJoinWithAppInfo_usingMedicalDataSourceId_correctQuery() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        List<String> hexValues = List.of(getHexString(uuid1), getHexString(uuid2));

        ReadTableRequest readRequest =
                getReadTableRequestJoinWithAppInfo(List.of(uuid1.toString(), uuid2.toString()));

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
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void createAndGetSingleMedicalDataSource_packageDoesNotExist_success()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource expected =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        mContext, createMedicalDataSourceRequest, DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(List.of(expected.getId()));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result.get(0)).isEqualTo(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void createAndGetSingleMedicalDataSource_packageAlreadyExists_success() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest1 =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource dataSource1 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        mContext, createMedicalDataSourceRequest1, DATA_SOURCE_PACKAGE_NAME);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(List.of(dataSource1.getId()));

        assertThat(result.size()).isEqualTo(1);
        assertThat(result).containsExactlyElementsIn(List.of(dataSource1));
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void createAndGetMultipleMedicalDataSources_bothPackagesAlreadyExist_success() {
        mTransactionTestUtils.insertApp(DATA_SOURCE_PACKAGE_NAME);
        mTransactionTestUtils.insertApp(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
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
                        mContext, createMedicalDataSourceRequest1, DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        mContext, createMedicalDataSourceRequest2, DATA_SOURCE_PACKAGE_NAME);
        List<MedicalDataSource> expected = List.of(dataSource1, dataSource2);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(
                        List.of(dataSource1.getId(), dataSource2.getId()));

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void createAndGetMultipleMedicalDataSourcesWithSamePackage_packageDoesNotExist_success()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
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
                        mContext, createMedicalDataSourceRequest1, DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        mContext, createMedicalDataSourceRequest2, DATA_SOURCE_PACKAGE_NAME);
        List<MedicalDataSource> expected = List.of(dataSource1, dataSource2);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(
                        List.of(dataSource1.getId(), dataSource2.getId()));

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void
            createAndGetMultipleMedicalDataSourcesWithDifferentPackages_packagesDoNotExist_success()
                    throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
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
                        mContext, createMedicalDataSourceRequest1, DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        mContext,
                        createMedicalDataSourceRequest2,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
        List<MedicalDataSource> expected = List.of(dataSource1, dataSource2);

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(
                        List.of(dataSource1.getId(), dataSource2.getId()));

        assertThat(result.size()).isEqualTo(2);
        assertThat(result).containsExactlyElementsIn(expected);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_badId_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    MedicalDataSourceHelper.deleteMedicalDataSource("foo");
                });
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_badId_leavesRecordsUnchanged() throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource existing =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        mContext, createMedicalDataSourceRequest, DATA_SOURCE_PACKAGE_NAME);

        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    MedicalDataSourceHelper.deleteMedicalDataSource("foo");
                });

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(List.of(existing.getId()));
        assertThat(result).containsExactly(existing);
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_oneId_existingDataDeleted() throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(
                                DATA_SOURCE_FHIR_BASE_URI, DATA_SOURCE_DISPLAY_NAME)
                        .build();
        MedicalDataSource existing =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        mContext, createMedicalDataSourceRequest, DATA_SOURCE_PACKAGE_NAME);

        MedicalDataSourceHelper.deleteMedicalDataSource(existing.getId());

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(List.of(existing.getId()));
        assertThat(result).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void delete_multiplePresentOneIdRequested_onlyRequestedDeleted()
            throws NameNotFoundException {
        setUpMocksForAppInfo(DATA_SOURCE_PACKAGE_NAME);
        setUpMocksForAppInfo(DIFFERENT_DATA_SOURCE_PACKAGE_NAME);
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
                        mContext, createMedicalDataSourceRequest1, DATA_SOURCE_PACKAGE_NAME);
        MedicalDataSource dataSource2 =
                mMedicalDataSourceHelper.createMedicalDataSource(
                        mContext,
                        createMedicalDataSourceRequest2,
                        DIFFERENT_DATA_SOURCE_PACKAGE_NAME);

        MedicalDataSourceHelper.deleteMedicalDataSource(dataSource1.getId());

        List<MedicalDataSource> result =
                mMedicalDataSourceHelper.getMedicalDataSources(
                        List.of(dataSource1.getId(), dataSource2.getId()));
        assertThat(result).containsExactly(dataSource2);
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

    // TODO: b/351166557 - add unit tests that deleting datasource deletes associated records
}
