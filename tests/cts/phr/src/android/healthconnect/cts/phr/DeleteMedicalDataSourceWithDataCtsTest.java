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
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_BACKGROUND_APP;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.MEDICAL_DATA_SOURCE_EQUIVALENCE;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.GetMedicalDataSourcesRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
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

import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class DeleteMedicalDataSourceWithDataCtsTest {
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
    public void testDeleteMedicalDataSource_existsWithoutData_succeedsAndDeletes()
            throws Exception {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(
                dataSource.getId(), Executors.newSingleThreadExecutor(), callback);

        callback.verifyNoExceptionOrThrow();
        // Verifies that data source is deleted.
        HealthConnectReceiver<List<MedicalDataSource>> readReceiver = new HealthConnectReceiver<>();
        runWithShellPermissionIdentity(
                () -> {
                    mManager.getMedicalDataSources(
                            new GetMedicalDataSourcesRequest.Builder().build(),
                            Executors.newSingleThreadExecutor(),
                            readReceiver);
                    assertThat(readReceiver.getResponse()).isEmpty();
                },
                MANAGE_HEALTH_DATA);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalDataSource_existsWithData_succeedsAndDeletes() throws Exception {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        mUtil.upsertMedicalData(dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(
                dataSource.getId(), Executors.newSingleThreadExecutor(), callback);

        callback.verifyNoExceptionOrThrow();
        HealthConnectReceiver<List<MedicalDataSource>> dataSourceReadReceiver =
                new HealthConnectReceiver<>();
        HealthConnectReceiver<ReadMedicalResourcesResponse> resourceReadReceiver =
                new HealthConnectReceiver<>();
        // Verifies that both data source and resource are deleted.
        runWithShellPermissionIdentity(
                () -> {
                    mManager.getMedicalDataSources(
                            new GetMedicalDataSourcesRequest.Builder().build(),
                            Executors.newSingleThreadExecutor(),
                            dataSourceReadReceiver);
                    assertThat(dataSourceReadReceiver.getResponse()).isEmpty();
                    mManager.readMedicalResources(
                            new ReadMedicalResourcesInitialRequest.Builder(
                                            MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                                    .build(),
                            Executors.newSingleThreadExecutor(),
                            resourceReadReceiver);
                    assertThat(resourceReadReceiver.getResponse().getMedicalResources()).isEmpty();
                },
                MANAGE_HEALTH_DATA);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalDataSource_doesntExist_throws() throws Exception {
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(
                DATA_SOURCE_ID, Executors.newSingleThreadExecutor(), callback);

        assertThat(callback.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalDataSource_invalidId_throws() throws Exception {
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(
                "illegal id", Executors.newSingleThreadExecutor(), callback);

        assertThat(callback.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testDeleteMedicalDataSource_differentPackage_throws() throws Exception {
        MedicalDataSource dataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource resource =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();

        mManager.deleteMedicalDataSourceWithData(
                dataSource.getId(), Executors.newSingleThreadExecutor(), callback);

        assertThat(callback.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
        HealthConnectReceiver<List<MedicalDataSource>> dataSourceReadReceiver =
                new HealthConnectReceiver<>();
        HealthConnectReceiver<List<MedicalResource>> resourceReadReceiver =
                new HealthConnectReceiver<>();
        // Verifies that both data source and resource are NOT deleted.
        runWithShellPermissionIdentity(
                () -> {
                    mManager.getMedicalDataSources(
                            List.of(dataSource.getId()),
                            Executors.newSingleThreadExecutor(),
                            dataSourceReadReceiver);
                    assertThat(dataSourceReadReceiver.getResponse())
                            .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                            .containsExactly(dataSource);
                    mManager.readMedicalResources(
                            List.of(resource.getId()),
                            Executors.newSingleThreadExecutor(),
                            resourceReadReceiver);
                    assertThat(resourceReadReceiver.getResponse()).containsExactly(resource);
                },
                MANAGE_HEALTH_DATA);
    }
}
