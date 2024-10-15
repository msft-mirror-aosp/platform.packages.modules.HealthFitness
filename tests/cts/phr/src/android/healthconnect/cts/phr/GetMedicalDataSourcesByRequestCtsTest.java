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

package android.healthconnect.cts.phr;

import static android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND;
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES;
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_IMMUNIZATIONS;
import static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_BACKGROUND_APP;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_BACKGROUND_APP_PKG;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_FOREGROUND_APP;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_FOREGROUND_APP_PKG;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAllPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokePermission;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.MEDICAL_DATA_SOURCE_EQUIVALENCE;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.GetMedicalDataSourcesRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.utils.DataFactory;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

public class GetMedicalDataSourcesByRequestCtsTest {

    private HealthConnectManager mManager;
    private PhrCtsTestUtils mUtil;

    @Before
    public void before() throws InterruptedException {
        // To make sure we don't leave any state behind after running each test.
        revokeAllPermissions(PHR_BACKGROUND_APP_PKG, "to test specific permissions");
        revokeAllPermissions(PHR_FOREGROUND_APP_PKG, "to test specific permissions");
        TestUtils.deleteAllStagedRemoteData();
        mManager = TestUtils.getHealthConnectManager();
        mUtil = new PhrCtsTestUtils(mManager);
        mUtil.deleteAllMedicalData();
    }

    @After
    public void after() throws InterruptedException {
        mUtil.deleteAllMedicalData();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesByRequest_nothingPresent_returnsEmpty() throws Exception {
        HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();

        runWithShellPermissionIdentity(
                () ->
                        mManager.getMedicalDataSources(
                                request, Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesByRequest_onePresentNoData_returnsItAndNullUpdateTime()
            throws Exception {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                getCreateMedicalDataSourceRequest("ds/1"),
                Executors.newSingleThreadExecutor(),
                createReceiver);
        MedicalDataSource dataSource = createReceiver.getResponse();
        HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();

        mManager.getMedicalDataSources(request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).containsExactly(dataSource);
        assertThat(dataSource.getLastDataUpdateTime()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void
            testGetMedicalDataSourcesByRequest_onePresentWithData_returnsCorrectLastDataUpdateTime()
                    throws Exception {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                getCreateMedicalDataSourceRequest(),
                Executors.newSingleThreadExecutor(),
                createReceiver);
        MedicalDataSource dataSource = createReceiver.getResponse();
        Instant insertTime = DataFactory.NOW;
        mUtil.upsertMedicalData(dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();

        mManager.getMedicalDataSources(request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).hasSize(1);
        Instant lastDataUpdateTime = receiver.getResponse().get(0).getLastDataUpdateTime();
        assertThat(lastDataUpdateTime).isAtLeast(insertTime);
        assertThat(lastDataUpdateTime).isAtMost(Instant.now());
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalResourcesByRequest_deletedResource_notCountedInLastDataUpdateTime()
            throws InterruptedException {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource resource =
                mUtil.upsertMedicalData(dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
        mManager.deleteMedicalResources(
                List.of(resource.getId()), Executors.newSingleThreadExecutor(), callback);
        callback.verifyNoExceptionOrThrow();
        HealthConnectReceiver<List<MedicalDataSource>> readReceiver = new HealthConnectReceiver<>();

        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();
        mManager.getMedicalDataSources(request, Executors.newSingleThreadExecutor(), readReceiver);

        // The last data update time of dataSource is expected to be null because we have deleted
        // all data.
        assertThat(readReceiver.getResponse().get(0).getLastDataUpdateTime()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesByRequest_InBackgroundNoPermission_throws() {
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();

        // App has not been granted any permissions.
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_BACKGROUND_APP.getMedicalDataSources(request));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesByRequest_InForegroundNoPermission_throws() {
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();

        // App has not been granted any permissions.
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_FOREGROUND_APP.getMedicalDataSources(request));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetByPackage_packageFilterEmpty_inBgWithBgPermHasWriteAndReadPerm()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background with background read perm, has write permission and
        // has immunization read permissions.
        // The packageName set in the request is empty so no filtering based on packageNames.
        // App can read dataSources they wrote themselves and dataSources belonging to
        // immunization resource types.
        List<MedicalDataSource> result =
                PHR_BACKGROUND_APP.getMedicalDataSources(
                        new GetMedicalDataSourcesRequest.Builder().build());

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(
                        dataSource1Background, dataSource2Background, dataSource1Foreground);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetByPackage_packageFilterEmpty_inForegroundHasWriteAndReadPerm()
            throws Exception {
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in foreground, has write permission and immunization read permissions.
        // The packageName set in the request is empty so no filtering based on packageNames.
        // App can read dataSources they wrote themselves and dataSources belonging to
        // immunization resource types.
        List<MedicalDataSource> result =
                PHR_FOREGROUND_APP.getMedicalDataSources(
                        new GetMedicalDataSourcesRequest.Builder().build());

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(
                        dataSource1Foreground, dataSource2Foreground, dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetByPackage_withPackageFilterSelfIncluded_inFgHasWriteAndReadPerm()
            throws Exception {
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .build();

        // App is in foreground, has write permission and has immunization read permissions.
        // The app's package name is included in the list of packages.
        // App can read dataSources they wrote themselves and dataSources belonging to
        // immunization resource types written by any of the given packages.
        List<MedicalDataSource> result = PHR_FOREGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(
                        dataSource1Foreground, dataSource2Foreground, dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetByPackage_withPackageFilterSelfIncluded_inBgWithPermHasWriteAndReadPerm()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .build();

        // App is in background with background read, has write permission and has immunization
        // read permissions.
        // The app's package name is included in the list of packages.
        // App can read dataSources they wrote themselves and dataSources belonging to
        // immunization resource types written by any of the given packages.
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(
                        dataSource1Background, dataSource2Background, dataSource1Foreground);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetByPackage_withPackageFilterSelfNotIncluded_inBgWithPermHasWriteAndReadPerm()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .build();

        // App is in background with background read perm, has write permission and
        // has immunization read permissions.
        // The app's package name is not included in the list of packages.
        // App can read dataSources belonging to immunization resource types written by any of
        // the given packages.
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Foreground);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetByPackage_withPackageFilterSelfNotIncluded_inForegroundWriteAndReadPerm()
            throws Exception {
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in foreground, has write permission and has immunization read permissions.
        // The app's package name is not included in the list of packages.
        // App can read dataSources belonging to immunization resource types written by any of
        // the given packages.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .build();
        List<MedicalDataSource> result = PHR_FOREGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetByPackage_emptyPackageFilter_inBgWithoutBgPermHasWritePermNoReadPerms()
            throws Exception {
        grantPermissions(PHR_FOREGROUND_APP_PKG, List.of(WRITE_MEDICAL_DATA));
        grantPermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background without background read perm, has write permission and
        // no read permissions.
        // The packageNames filter is empty so no filter is applied.
        // App can read dataSources they wrote themself.
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_withPackageFilterSelfIncluded_inBgWithoutBgPermHasWritePermButNoRead()
            throws Exception {
        grantPermissions(PHR_FOREGROUND_APP_PKG, List.of(WRITE_MEDICAL_DATA));
        grantPermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background without background read perm, has write permission and
        // no read permissions.
        // The app's package name is included in the list of packages.
        // App can read dataSources they wrote themself.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_withPackageFilterSelfNotIncluded_inBgWithoutBgPermHasWritePermNoRead()
            throws Exception {
        grantPermissions(PHR_FOREGROUND_APP_PKG, List.of(WRITE_MEDICAL_DATA));
        grantPermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        // App is in background without background read perm, has write permission and
        // no read permissions.
        // The app's package name is not included in the list of packages.
        // App can read no dataSources.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .build();
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_BACKGROUND_APP.getMedicalDataSources(request));
        assertThat(exception.getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_emptyPackageFilter_inBgWithoutBgPermHasWritePermAndReadPerms()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP_PKG,
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background without background read perm, has write permission and
        // has read immunization permission.
        // The packageNames is empty so no filtering is applied.
        // App can read dataSources they wrote themselves.
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_withPackageFilterSelfIncluded_inBgWithoutBgPermHasWriteAndReadPerm()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP_PKG,
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background without background read perm, has write permission and
        // has read immunization permission.
        // The app's package name is included in the list of packages.
        // App can read dataSources they wrote themselves.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_noPackageFilter_inBgWithoutBgPermHasReadPermNoWritePerm()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP_PKG,
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);
        // Revoke the write permission that was granted before.
        revokePermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        // App is in background without background read perm, has no write permission and
        // has read immunization permission.
        // The packageNames is empty so no filtering based on packageNames.
        // App can read dataSources belonging to immunizations the app wrote itself.
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_withPackageFilterSelfIncluded_inBgWithoutBgPermHasReadPermNoWrite()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP_PKG,
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);
        // Revoke the write permission that was granted before.
        revokePermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        // App is in background without background read perm, has no write permission and
        // has read immunization permission.
        // The app's package name is included in the list of packages.
        // App can read dataSources belonging to immunizations the app wrote itself.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_emptyPackageFilter_inBgWithoutBgPermHasMultipleReadPermsNoWritePerm()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP_PKG,
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_MEDICAL_DATA_IMMUNIZATIONS,
                        READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES));
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);
        // Revoke the write permission that was granted before.
        revokePermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        // App is in background without background read perm, no write permission but has
        // immunization and allergy read permission.
        // PackageNames is empty so no filtering based on packageNames is applied.
        // App can read dataSources belonging to
        // immunizations and allergy resource types that the app wrote itself.
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_filterOnPackageWithSelf_inBgWithoutBgPermHasMultipleReadPermsNoWrite()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP_PKG,
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_MEDICAL_DATA_IMMUNIZATIONS,
                        READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES));
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);
        // Revoke the write permission that was granted before.
        revokePermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        // App is in background without background read perm, no write permission but has
        // immunization and allergy read permission. App can read dataSources belonging to
        // immunizations and allergy resource types that the app wrote itself.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_noPackageFilter_inForegroundHasWritePermNoReadPerm()
            throws Exception {
        grantPermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in foreground or background with background read perm, has write permission but
        // no read permission for any resource types.
        // The packageNames is empty so no filtering is applied based on packageNames.
        // App can read only read dataSources they wrote themselves.
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();
        List<MedicalDataSource> result = PHR_FOREGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Foreground, dataSource2Foreground);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_noPackageFilter_inBgWithBgPermHasWritePermNoReadPerm()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP_PKG,
                List.of(WRITE_MEDICAL_DATA, READ_HEALTH_DATA_IN_BACKGROUND));
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background with background read perm, has write permission but
        // no read permission for any resource types.
        // The packageNames is empty so no filtering is applied based on packageNames.
        // App can read only read dataSources they wrote themselves.
        GetMedicalDataSourcesRequest request = new GetMedicalDataSourcesRequest.Builder().build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_packageFilterWithSelf_inFgHasWritePermNoReadPerm() throws Exception {
        grantPermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in foreground or background with background read perm, has write permission but
        // no read permission for any resource types.
        // The packageNames is empty so no filtering is applied based on packageNames.
        // App can read only read dataSources they wrote themselves.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .build();
        List<MedicalDataSource> result = PHR_FOREGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Foreground, dataSource2Foreground);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_packageFilterWithSelf_inBgWithBgPermHasWritePermNoReadPerm()
            throws Exception {
        grantPermissions(
                PHR_BACKGROUND_APP_PKG,
                List.of(WRITE_MEDICAL_DATA, READ_HEALTH_DATA_IN_BACKGROUND));
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);

        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_FOREGROUND_APP.upsertMedicalResource(dataSource2Foreground.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background with background read perm, has write permission but
        // no read permission for any resource types.
        // The packageNames is empty so no filtering is applied based on packageNames.
        // App can read only read dataSources they wrote themselves.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .build();
        List<MedicalDataSource> result = PHR_BACKGROUND_APP.getMedicalDataSources(request);

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void
            getByPackages_withPackageFilterSelfNotIncluded_inForegroundHasWritePermNoReadPerms() {
        grantPermissions(PHR_FOREGROUND_APP_PKG, List.of(WRITE_MEDICAL_DATA));

        // App is in foreground, has write permission but no read permission for any resource types.
        // App package name is not included in the set of given packageNames.
        // App can not read any dataSources.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_BACKGROUND_APP_PKG)
                        .build();
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_FOREGROUND_APP.getMedicalDataSources(request));
        assertThat(exception.getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void getByPackages_withPackageFilterSelfNotIncluded_inBgWithBgReadHasWritePermNoRead() {
        grantPermissions(PHR_BACKGROUND_APP_PKG, List.of(WRITE_MEDICAL_DATA));

        // App is in background with background read permission, has write permission but no read
        // permission for any resource types.
        // App package name is not included in the set of given packageNames.
        // App can not read any dataSources.
        GetMedicalDataSourcesRequest request =
                new GetMedicalDataSourcesRequest.Builder()
                        .addPackageName(PHR_FOREGROUND_APP_PKG)
                        .build();
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_BACKGROUND_APP.getMedicalDataSources(request));
        assertThat(exception.getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }
}
