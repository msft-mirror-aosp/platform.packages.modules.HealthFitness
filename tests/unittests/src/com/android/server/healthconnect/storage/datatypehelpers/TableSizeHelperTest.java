/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.FhirVersion;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.phr.utils.ImmunizationBuilder;
import android.net.Uri;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.EnvironmentFixture;
import com.android.server.healthconnect.SQLiteDatabaseFixture;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
public class TableSizeHelperTest {

    @Rule(order = 1)
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule(order = 2)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .addStaticMockFixtures(EnvironmentFixture::new, SQLiteDatabaseFixture::new)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private static final String PACKAGE_NAME = "com.my.package";
    private static final Instant INSTANT_NOW = Instant.now();

    private TransactionTestUtils mTransactionTestUtils;
    private Context mContext;
    private HealthConnectInjector mHealthConnectInjector;

    private TableSizeHelper mTableSizeHelper;

    @Before
    public void setup() {
        mContext = ApplicationProvider.getApplicationContext();
        FakeTimeSource mFakeTimeSource = new FakeTimeSource(INSTANT_NOW);
        mHealthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(mContext)
                        .setFirstGrantTimeManager(mock(FirstGrantTimeManager.class))
                        .setHealthPermissionIntentAppsTracker(
                                mock(HealthPermissionIntentAppsTracker.class))
                        .setTimeSource(mFakeTimeSource)
                        .build();
        mTransactionTestUtils = new TransactionTestUtils(mHealthConnectInjector);

        mTableSizeHelper = new TableSizeHelper(mHealthConnectInjector.getTransactionManager());
    }

    @Test
    public void testGetFileBytes_noData_small() {
        assertThat(
                        mTableSizeHelper.getFileBytes(
                                List.of(MedicalDataSourceHelper.getMainTableName())))
                .isLessThan(10_000L);
    }

    @Test
    public void testGetFileBytes_nonExistentTable_zero() {
        assertThat(mTableSizeHelper.getFileBytes(List.of("foo"))).isEqualTo(0);
    }

    @Test
    public void testGetFileBytes_noTables_zero() {
        assertThat(mTableSizeHelper.getFileBytes(List.of())).isEqualTo(0);
    }

    @Test
    public void testGetFileBytes_manyResources_plausibleSize() {
        mTransactionTestUtils.insertApp(PACKAGE_NAME);
        // Insert a data source.
        MedicalDataSourceHelper dataSourceHelper =
                mHealthConnectInjector.getMedicalDataSourceHelper();
        FhirVersion fhirVersion = FhirVersion.parseFhirVersion("4.0.1");
        MedicalDataSource dataSource =
                dataSourceHelper.createMedicalDataSource(
                        mContext,
                        new CreateMedicalDataSourceRequest.Builder(
                                        Uri.parse("http://fakebaseuri.com/"),
                                        "Hospital",
                                        fhirVersion)
                                .build(),
                        PACKAGE_NAME);
        // Insert 100 resources into that data source
        ArrayList<UpsertMedicalResourceInternalRequest> resourceRequests = new ArrayList<>();
        ImmunizationBuilder builder = new ImmunizationBuilder();
        for (int i = 0; i < 100; i++) {
            String resourceId = "imm" + i;
            builder.setId(resourceId);
            resourceRequests.add(
                    new UpsertMedicalResourceInternalRequest()
                            .setMedicalResourceType(MedicalResource.MEDICAL_RESOURCE_TYPE_VACCINES)
                            .setFhirResourceType(FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION)
                            .setFhirResourceId(resourceId)
                            .setDataSourceId(dataSource.getId())
                            .setFhirVersion(fhirVersion)
                            .setData(builder.toJson()));
        }
        MedicalResourceHelper resourceHelper = mHealthConnectInjector.getMedicalResourceHelper();
        resourceHelper.upsertMedicalResources(PACKAGE_NAME, resourceRequests);

        long tableSizeBytes =
                mTableSizeHelper.getFileBytes(List.of(MedicalResourceHelper.getMainTableName()));
        // We don't want to be too prescriptive about size, but the size should be of
        // the order of 1-200k for these resources.
        assertThat(tableSizeBytes).isGreaterThan(100_000);
        assertThat(tableSizeBytes).isLessThan(1_000_000);
    }

    @Test
    public void testGetFileBytes_multipleTables_sumOfTables() {
        long dataSourceTableBytes =
                mTableSizeHelper.getFileBytes(List.of(MedicalDataSourceHelper.getMainTableName()));
        long resourceTableBytes =
                mTableSizeHelper.getFileBytes(List.of(MedicalResourceHelper.getMainTableName()));

        assertThat(
                        mTableSizeHelper.getFileBytes(
                                List.of(
                                        MedicalResourceHelper.getMainTableName(),
                                        MedicalDataSourceHelper.getMainTableName())))
                .isEqualTo(dataSourceTableBytes + resourceTableBytes);
    }
}
