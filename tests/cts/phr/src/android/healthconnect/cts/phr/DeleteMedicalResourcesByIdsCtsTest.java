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

import static android.health.connect.datatypes.FhirResource.FHIR_RESOURCE_TYPE_IMMUNIZATION;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.healthconnect.cts.utils.DataFactory.MAXIMUM_PAGE_SIZE;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PhrDataFactory.DIFFERENT_FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_RESOURCE_ID_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.HealthConnectManager;
import android.health.connect.MedicalResourceId;
import android.health.connect.ReadMedicalResourcesInitialRequest;
import android.health.connect.ReadMedicalResourcesResponse;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class DeleteMedicalResourcesByIdsCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private HealthConnectManager mManager;
    private PhrCtsTestUtils mUtil;

    @Before
    public void setUp() throws Exception {
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
    public void testDeleteMedicalResourcesByIds_idsNonExistent_succeeds()
            throws InterruptedException {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        List<MedicalResourceId> ids = new ArrayList<>(MAXIMUM_PAGE_SIZE + 1);
        for (int i = 0; i < MAXIMUM_PAGE_SIZE + 1; i++) {
            ids.add(
                    new MedicalResourceId(
                            dataSource.getId(),
                            FHIR_RESOURCE_TYPE_IMMUNIZATION,
                            FHIR_RESOURCE_ID_IMMUNIZATION + "." + i));
        }
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();

        mManager.deleteMedicalResources(ids, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByIds_managementPermissionNoData_succeeds()
            throws InterruptedException {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        List<MedicalResourceId> ids = new ArrayList<>(MAXIMUM_PAGE_SIZE + 1);
        for (int i = 0; i < MAXIMUM_PAGE_SIZE + 1; i++) {
            ids.add(
                    new MedicalResourceId(
                            dataSource.getId(),
                            FHIR_RESOURCE_TYPE_IMMUNIZATION,
                            FHIR_RESOURCE_ID_IMMUNIZATION + "." + i));
        }
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();

        runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                ids, Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        receiver.verifyNoExceptionOrThrow();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByIds_anIdMissing_succeeds() throws InterruptedException {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();
        MedicalResourceId id =
                new MedicalResourceId(
                        dataSource.getId(),
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);

        mManager.deleteMedicalResources(List.of(id), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResourcesByIds_emptyIds_succeeds() throws InterruptedException {
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();

        mManager.deleteMedicalResources(List.of(), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByIds_managementPermissionAMissingId_succeeds()
            throws InterruptedException {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();
        MedicalResourceId id =
                new MedicalResourceId(
                        dataSource.getId(),
                        FHIR_RESOURCE_TYPE_IMMUNIZATION,
                        FHIR_RESOURCE_ID_IMMUNIZATION);

        runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                List.of(id), Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        receiver.verifyNoExceptionOrThrow();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testDeleteMedicalResourcesByIds_managementPermissionEmptyIds_succeeds()
            throws InterruptedException {
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();

        runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                List.of(), Executors.newSingleThreadExecutor(), receiver),
                MANAGE_HEALTH_DATA);

        assertThat(receiver.getResponse()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByIds_managementPermissionCreate2Delete1_succeeds()
            throws InterruptedException {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        // Insert some data
        MedicalResource resource1 =
                mUtil.upsertMedicalData(dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource resource2 =
                mUtil.upsertMedicalData(dataSource.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                List.of(resource1.getId()),
                                Executors.newSingleThreadExecutor(),
                                callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.getResponse()).isNull();
        // Test resource2 is still present
        HealthConnectReceiver<ReadMedicalResourcesResponse> readReceiver =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build(),
                Executors.newSingleThreadExecutor(),
                readReceiver);
        assertThat(readReceiver.getResponse().getMedicalResources()).containsExactly(resource2);
    }
}
