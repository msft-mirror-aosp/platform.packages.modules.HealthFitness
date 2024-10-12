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

import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_BACKGROUND_APP;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_BACKGROUND_APP_PKG;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_FOREGROUND_APP;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_FOREGROUND_APP_PKG;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAllPermissions;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
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
}
