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

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;
import static android.healthconnect.cts.utils.TestUtils.setFieldValueUsingReflection;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.DeleteMedicalResourcesRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class DeleteMedicalResourcesByRequestCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private HealthConnectManager mManager;

    @Before
    public void setUp() throws Exception {
        TestUtils.deleteAllStagedRemoteData();
        TestUtils.deleteAllMedicalData();
        mManager = TestUtils.getHealthConnectManager();
    }

    @After
    public void after() throws InterruptedException {
        TestUtils.deleteAllMedicalData();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByRequest_managementPermissionCreate2Delete1_succeeds()
            throws InterruptedException {
        // Create the datasource
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert some data
        MedicalResource resource1 = upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource resource2 = upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);

        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(dataSource1.getId())
                        .build();
        runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                request, Executors.newSingleThreadExecutor(), callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.getResponse()).isNull();
        // Test resource2 is still present
        HealthConnectReceiver<List<MedicalResource>> readReceiver2 = new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                List.of(resource1.getId(), resource2.getId()),
                Executors.newSingleThreadExecutor(),
                readReceiver2);
        assertThat(readReceiver2.getResponse()).containsExactly(resource2);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByRequest_badDataSourceId_doesntDeleteAll()
            throws InterruptedException, NoSuchFieldException, IllegalAccessException {
        // Create the datasource
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        // Insert some data
        MedicalResource resource1 = upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        HealthConnectReceiver<Void> deleteCallback = new HealthConnectReceiver<>();
        HealthConnectReceiver<List<MedicalResource>> readReceiver = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest deleteRequest =
                new DeleteMedicalResourcesRequest.Builder().addDataSourceId(DATA_SOURCE_ID).build();
        // Change the delete request to use an illegal id for this test.
        setFieldValueUsingReflection(deleteRequest, "mDataSourceIds", Set.of("illegal id"));
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);

        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            mManager.deleteMedicalResources(
                                    deleteRequest,
                                    Executors.newSingleThreadExecutor(),
                                    deleteCallback));
            // Test resource is still present.
            mManager.readMedicalResources(
                    List.of(resource1.getId()), Executors.newSingleThreadExecutor(), readReceiver);
            assertThat(readReceiver.getResponse()).containsExactly(resource1);
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByRequest_resourceTypesMatch_succeeds()
            throws InterruptedException {
        // Create the datasource
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert some data
        MedicalResource imm1 = upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource imm2 = upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);

        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                request, Executors.newSingleThreadExecutor(), callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.getResponse()).isNull();
        // Test resource2 is still present
        HealthConnectReceiver<List<MedicalResource>> readReceiver2 = new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                List.of(imm1.getId(), imm2.getId()),
                Executors.newSingleThreadExecutor(),
                readReceiver2);
        assertThat(readReceiver2.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByRequest_resourceTypesMismatch_succeeds()
            throws InterruptedException {
        // Create the datasource
        MedicalDataSource dataSource1 = createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 = createDataSource(getCreateMedicalDataSourceRequest("2"));
        // Insert some data
        MedicalResource imm1 = upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource imm2 = upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);

        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addMedicalResourceType(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS + 1)
                        .build();
        runWithShellPermissionIdentity(
                () ->
                        mManager.deleteMedicalResources(
                                request, Executors.newSingleThreadExecutor(), callback),
                MANAGE_HEALTH_DATA);

        assertThat(callback.getResponse()).isNull();
        // Test resource2 is still present
        HealthConnectReceiver<List<MedicalResource>> readReceiver2 = new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                List.of(imm1.getId(), imm2.getId()),
                Executors.newSingleThreadExecutor(),
                readReceiver2);
        assertThat(readReceiver2.getResponse()).containsExactly(imm1, imm2);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalResourcesByRequest_nothingPresent_succeeds() throws Exception {
        // Insert a data source to ensure we have an appInfoId.
        createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<Void> receiver = new HealthConnectReceiver<>();
        DeleteMedicalResourcesRequest request =
                new DeleteMedicalResourcesRequest.Builder()
                        .addDataSourceId(UUID.randomUUID().toString())
                        .build();

        mManager.deleteMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isNull();
    }

    private MedicalDataSource createDataSource(CreateMedicalDataSourceRequest createRequest)
            throws InterruptedException {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                createRequest, Executors.newSingleThreadExecutor(), createReceiver);
        return createReceiver.getResponse();
    }

    private MedicalResource upsertMedicalData(String dataSourceId, String data)
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> dataReceiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(dataSourceId, FHIR_VERSION_R4, data)
                        .build();
        mManager.upsertMedicalResources(
                List.of(request), Executors.newSingleThreadExecutor(), dataReceiver);
        // Make sure something got inserted.
        return Iterables.getOnlyElement(dataReceiver.getResponse());
    }
}
