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
package android.healthconnect.cts.phr.apis;

import static android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND;
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_CONDITIONS;
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_VACCINES;
import static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.MAX_FOREGROUND_READ_CALL_15M;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.MEDICAL_RESOURCE_TYPES_LIST;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.PHR_BACKGROUND_APP;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.PHR_FOREGROUND_APP;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.DIFFERENT_FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;
import static android.healthconnect.cts.utils.DataFactory.MAXIMUM_PAGE_SIZE;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAllPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokePermission;
import static android.healthconnect.cts.utils.TestUtils.finishMigrationWithShellPermissionIdentity;
import static android.healthconnect.cts.utils.TestUtils.setFieldValueUsingReflection;
import static android.healthconnect.cts.utils.TestUtils.startMigrationWithShellPermissionIdentity;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadMedicalResourcesInitialRequest;
import android.health.connect.ReadMedicalResourcesPageRequest;
import android.health.connect.ReadMedicalResourcesResponse;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.phr.utils.PhrCtsTestUtils;
import android.healthconnect.cts.phr.utils.PhrDataFactory;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class ReadMedicalResourcesByRequestCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private PhrCtsTestUtils mUtil;

    private HealthConnectManager mManager;

    @Before
    public void setUp() throws InterruptedException {
        // To make sure we don't leave any state behind after running each test.
        revokeAllPermissions(PHR_BACKGROUND_APP.getPackageName(), "to test specific permissions");
        revokeAllPermissions(PHR_FOREGROUND_APP.getPackageName(), "to test specific permissions");
        TestUtils.deleteAllStagedRemoteData();
        mUtil = new PhrCtsTestUtils(TestUtils.getHealthConnectManager());
        mUtil.deleteAllMedicalData();
        mManager = TestUtils.getHealthConnectManager();
        if (TestUtils.setLowerRateLimitsForTesting(true)) {
            mUtil.mLimitsAdjustmentForTesting = 10;
        }
    }

    @After
    public void after() throws InterruptedException {
        mUtil.deleteAllMedicalData();
        TestUtils.setLowerRateLimitsForTesting(false);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_migrationInProgress_apiBlocked()
            throws InterruptedException {
        startMigrationWithShellPermissionIdentity();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();

        mManager.readMedicalResources(request, executor, receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);

        finishMigrationWithShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_readLimitExceeded_throws()
            throws InterruptedException {
        MedicalDataSource dataSource =
                mUtil.createDataSource(PhrDataFactory.getCreateMedicalDataSourceRequest());
        mUtil.upsertMedicalData(dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        // Make the maximum number of calls allowed by quota
        int maximumCalls = MAX_FOREGROUND_READ_CALL_15M / mUtil.mLimitsAdjustmentForTesting;
        for (int i = 0; i < maximumCalls; i++) {
            HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                    new HealthConnectReceiver<>();
            mManager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
        }

        // Make 1 extra call and check quota is exceeded
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);

        HealthConnectException exception = receiver.assertAndGetException();
        assertThat(exception.getMessage()).contains("API call quota exceeded");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_unknownTypeInPageRequest_throws()
            throws InterruptedException {
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        // Encode a page token according to PhrPageTokenWrapper#encode(), with medicalResourceType
        // being unknown type 0.
        String pageTokenStringWithUnknownType = "2,0,";
        Base64.Encoder encoder = Base64.getEncoder();
        String pageToken = encoder.encodeToString(pageTokenStringWithUnknownType.getBytes());
        ReadMedicalResourcesPageRequest request =
                new ReadMedicalResourcesPageRequest.Builder(pageToken).build();

        mManager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_emptyPageTokenInPageRequest_throws()
            throws InterruptedException {
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesPageRequest request =
                new ReadMedicalResourcesPageRequest.Builder("").build();

        mManager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_invalidDataSourceIdsByReflection_throws()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();

        setFieldValueUsingReflection(request, "mDataSourceIds", Set.of("invalid id"));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mManager.readMedicalResources(
                                request,
                                Executors.newSingleThreadExecutor(),
                                new HealthConnectReceiver<>()));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_invalidResourceTypeByReflection_throws()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();

        setFieldValueUsingReflection(request, "mMedicalResourceType", 100);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mManager.readMedicalResources(
                                request,
                                Executors.newSingleThreadExecutor(),
                                new HealthConnectReceiver<>()));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_invalidPageSizeByReflection_throws()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesPageRequest request =
                new ReadMedicalResourcesPageRequest.Builder("").build();

        setFieldValueUsingReflection(request, "mPageSize", MAXIMUM_PAGE_SIZE + 1);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mManager.readMedicalResources(
                                request,
                                Executors.newSingleThreadExecutor(),
                                new HealthConnectReceiver<>()));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_nullPageTokenInPageRequestByReflection_throws()
            throws NoSuchFieldException, IllegalAccessException {
        ReadMedicalResourcesPageRequest request =
                new ReadMedicalResourcesPageRequest.Builder("").build();

        setFieldValueUsingReflection(request, "mPageToken", null);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mManager.readMedicalResources(
                                request,
                                Executors.newSingleThreadExecutor(),
                                new HealthConnectReceiver<>()));
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_noData_returnsEmptyList()
            throws InterruptedException {
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();

        mManager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);

        ReadMedicalResourcesResponse response = receiver.getResponse();
        assertThat(response.getMedicalResources()).isEmpty();
        assertThat(response.getNextPageToken()).isNull();
        assertThat(response.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_noDataForResourceTypeAndDataSource_ReturnsEmpty()
            throws InterruptedException {
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(UUID.randomUUID().toString())
                        .build();

        mManager.readMedicalResources(request, Executors.newSingleThreadExecutor(), receiver);

        ReadMedicalResourcesResponse response = receiver.getResponse();
        assertThat(response.getMedicalResources()).isEmpty();
        assertThat(response.getNextPageToken()).isNull();
        assertThat(response.getRemainingCount()).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_filtersByMedicalResourceType()
            throws InterruptedException {
        // Given we have three Immunizations and one Allergy in two data sources
        MedicalDataSource dataSource1 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("2"));
        MedicalResource immunization1 =
                mUtil.upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource immunization2 =
                mUtil.upsertMedicalData(dataSource2.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        MedicalResource immunization3 =
                mUtil.upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource1.getId(), FHIR_DATA_ALLERGY);

        // When we read immunizations with a page size of 2 and use the nextPageToken to read
        // remaining immunizations
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver1 =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest allImmunizationsRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(2)
                        .build();
        mManager.readMedicalResources(
                allImmunizationsRequest, Executors.newSingleThreadExecutor(), receiver1);

        String nextPageToken = receiver1.getResponse().getNextPageToken();
        assertThat(nextPageToken).isNotEmpty();

        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver2 =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                new ReadMedicalResourcesPageRequest.Builder(nextPageToken).build(),
                Executors.newSingleThreadExecutor(),
                receiver2);

        // Then the response gives two pages containing immunizations only, with page token 1
        // linking to page 2
        assertThat(receiver1.getResponse().getMedicalResources())
                .containsExactly(immunization1, immunization2);
        assertThat(receiver1.getResponse().getRemainingCount()).isEqualTo(1);

        assertThat(receiver2.getResponse().getMedicalResources()).containsExactly(immunization3);
        assertThat(receiver2.getResponse().getNextPageToken()).isNull();
        assertThat(receiver2.getResponse().getRemainingCount()).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_filtersByMedicalResourceTypeAndOneDataSource()
            throws InterruptedException {
        // Given we have three Immunizations in two data sources
        MedicalDataSource dataSource1 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("2"));
        MedicalResource immunization1FromDataSource1 =
                mUtil.upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource immunization2FromDataSource1 =
                mUtil.upsertMedicalData(dataSource1.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource2.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);

        // When we read Immunizations only from data source 1 in 2 pages.
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver1 =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest immunizationsFromDataSource1Request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(dataSource1.getId())
                        .setPageSize(1)
                        .build();
        mManager.readMedicalResources(
                immunizationsFromDataSource1Request,
                Executors.newSingleThreadExecutor(),
                receiver1);

        String nextPageToken = receiver1.getResponse().getNextPageToken();
        assertThat(nextPageToken).isNotEmpty();

        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver2 =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                new ReadMedicalResourcesPageRequest.Builder(nextPageToken).build(),
                Executors.newSingleThreadExecutor(),
                receiver2);

        // Then the response gives two pages with results filtered by Immunization and data source 1
        assertThat(receiver1.getResponse().getMedicalResources())
                .containsExactly(immunization1FromDataSource1);
        assertThat(receiver1.getResponse().getRemainingCount()).isEqualTo(1);

        assertThat(receiver2.getResponse().getMedicalResources())
                .containsExactly(immunization2FromDataSource1);
        assertThat(receiver2.getResponse().getNextPageToken()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_filtersByMedicalResourceTypeAndBothDataSources()
            throws InterruptedException {
        // Given we have two Immunizations and one Allergy in two data sources
        MedicalDataSource dataSource1 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("2"));
        MedicalResource immunizationFromDataSource1 =
                mUtil.upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource immunizationFromDataSource2 =
                mUtil.upsertMedicalData(dataSource2.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource1.getId(), FHIR_DATA_ALLERGY);

        // When we read Immunizations only from both data sources in 2 pages
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver1 =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest immunizationsFromBothDataSourcesRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .addDataSourceId(dataSource1.getId())
                        .addDataSourceId(dataSource2.getId())
                        .setPageSize(1)
                        .build();
        mManager.readMedicalResources(
                immunizationsFromBothDataSourcesRequest,
                Executors.newSingleThreadExecutor(),
                receiver1);

        String nextPageToken = receiver1.getResponse().getNextPageToken();
        assertThat(nextPageToken).isNotEmpty();

        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver2 =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                new ReadMedicalResourcesPageRequest.Builder(nextPageToken).build(),
                Executors.newSingleThreadExecutor(),
                receiver2);

        // Then we receive 2 pages containing Immunizations from both data sources with page token
        // 1 linking to page 2
        assertThat(receiver1.getResponse().getMedicalResources())
                .containsExactly(immunizationFromDataSource1);
        assertThat(receiver1.getResponse().getRemainingCount()).isEqualTo(1);

        assertThat(receiver2.getResponse().getMedicalResources())
                .containsExactly(immunizationFromDataSource2);
        assertThat(receiver2.getResponse().getNextPageToken()).isNull();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_hasManagementPermission_succeeds()
            throws InterruptedException {
        // Given we have two Immunizations in one data source
        MedicalDataSource dataSource =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalResource immunization1 =
                mUtil.upsertMedicalData(dataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource immunization2 =
                mUtil.upsertMedicalData(dataSource.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);

        // When we read Immunizations with MANAGE_HEALTH_DATA permission in 2 pages
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver1 =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest immunizationsRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(1)
                        .build();
        runWithShellPermissionIdentity(
                () ->
                        mManager.readMedicalResources(
                                immunizationsRequest,
                                Executors.newSingleThreadExecutor(),
                                receiver1),
                MANAGE_HEALTH_DATA);

        String nextPageToken = receiver1.getResponse().getNextPageToken();
        assertThat(nextPageToken).isNotEmpty();

        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver2 =
                new HealthConnectReceiver<>();
        runWithShellPermissionIdentity(
                () ->
                        mManager.readMedicalResources(
                                new ReadMedicalResourcesPageRequest.Builder(nextPageToken).build(),
                                Executors.newSingleThreadExecutor(),
                                receiver2),
                MANAGE_HEALTH_DATA);

        // The we receive two pages containing all immunizations, with page token 1 linking to page
        // 2
        assertThat(receiver2.getResponse().getMedicalResources()).containsExactly(immunization2);
        assertThat(receiver2.getResponse().getNextPageToken()).isNull();

        assertThat(receiver1.getResponse().getMedicalResources()).containsExactly(immunization1);
        assertThat(receiver1.getResponse().getRemainingCount()).isEqualTo(1);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_setPageSizeOnPageRequest_succeeds()
            throws InterruptedException {
        // Given we have six Immunizations in three data source
        MedicalDataSource dataSource1 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("2"));
        MedicalDataSource dataSource3 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("3"));
        mUtil.upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource1.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource2.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource3.getId(), FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource3.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);

        // When we read all immunizations in two pages and specify a page size on the page request
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver1 =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest immunizationsRequestWithPageSize1 =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(1)
                        .build();
        mManager.readMedicalResources(
                immunizationsRequestWithPageSize1, Executors.newSingleThreadExecutor(), receiver1);

        String nextPageToken = receiver1.getResponse().getNextPageToken();
        assertThat(nextPageToken).isNotEmpty();

        ReadMedicalResourcesPageRequest pageRequestWithPageSize2 =
                new ReadMedicalResourcesPageRequest.Builder(nextPageToken).setPageSize(2).build();
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver2 =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                pageRequestWithPageSize2, Executors.newSingleThreadExecutor(), receiver2);

        // Then we receive two pages, with the correct page size
        assertThat(receiver1.getResponse().getMedicalResources()).hasSize(1);
        assertThat(receiver1.getResponse().getRemainingCount()).isEqualTo(5);

        assertThat(receiver2.getResponse().getMedicalResources()).hasSize(2);
        assertThat(receiver2.getResponse().getNextPageToken()).isNotEmpty();
        assertThat(receiver2.getResponse().getRemainingCount()).isEqualTo(3);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_dataDeletedAfterInitialRequest_correctRemaining()
            throws InterruptedException {
        // Given we have three Immunizations in two data source
        MedicalDataSource dataSource1 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("2"));
        MedicalResource resource1 =
                mUtil.upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource resource2 =
                mUtil.upsertMedicalData(dataSource1.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);
        MedicalResource resource3 =
                mUtil.upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);

        // When we read the first Immunization and then delete all Immunizations
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver1 =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest immunizationsRequestWithPageSize1 =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(1)
                        .build();
        mManager.readMedicalResources(
                immunizationsRequestWithPageSize1, Executors.newSingleThreadExecutor(), receiver1);

        String nextPageToken = receiver1.getResponse().getNextPageToken();
        assertThat(nextPageToken).isNotEmpty();

        mUtil.deleteResources(List.of(resource1.getId(), resource2.getId(), resource3.getId()));

        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver2 =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                new ReadMedicalResourcesPageRequest.Builder(nextPageToken).build(),
                Executors.newSingleThreadExecutor(),
                receiver2);

        // Then the second page read will return no immunizations with 0 remaining
        assertThat(receiver1.getResponse().getMedicalResources()).hasSize(1);
        assertThat(receiver1.getResponse().getRemainingCount()).isEqualTo(2);

        assertThat(receiver2.getResponse().getMedicalResources()).hasSize(0);
        assertThat(receiver2.getResponse().getNextPageToken()).isNull();
        assertThat(receiver2.getResponse().getRemainingCount()).isEqualTo(0);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadMedicalResourcesByRequest_dataAddedAfterInitialRequest_correctRemaining()
            throws InterruptedException {
        // Given we have two Immunizations in two data source
        MedicalDataSource dataSource1 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("1"));
        MedicalDataSource dataSource2 =
                mUtil.createDataSource(getCreateMedicalDataSourceRequest("2"));
        mUtil.upsertMedicalData(dataSource1.getId(), FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource1.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);

        // When we read the first Immunization and then insert two moreImmunizations
        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver1 =
                new HealthConnectReceiver<>();
        ReadMedicalResourcesInitialRequest immunizationsRequestWithPageSize1 =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(1)
                        .build();
        mManager.readMedicalResources(
                immunizationsRequestWithPageSize1, Executors.newSingleThreadExecutor(), receiver1);

        String nextPageToken = receiver1.getResponse().getNextPageToken();
        assertThat(nextPageToken).isNotEmpty();

        mUtil.upsertMedicalData(dataSource2.getId(), FHIR_DATA_IMMUNIZATION);
        mUtil.upsertMedicalData(dataSource2.getId(), DIFFERENT_FHIR_DATA_IMMUNIZATION);

        HealthConnectReceiver<ReadMedicalResourcesResponse> receiver2 =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                new ReadMedicalResourcesPageRequest.Builder(nextPageToken).setPageSize(1).build(),
                Executors.newSingleThreadExecutor(),
                receiver2);

        // Then the second page read will reflect the updated number of immunizations
        assertThat(receiver1.getResponse().getMedicalResources()).hasSize(1);
        assertThat(receiver1.getResponse().getRemainingCount()).isEqualTo(1);

        assertThat(receiver2.getResponse().getMedicalResources()).hasSize(1);
        assertThat(receiver2.getResponse().getNextPageToken()).isNotNull();
        assertThat(receiver2.getResponse().getRemainingCount()).isEqualTo(2);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundWithNoPerms_throws() {
        // App has not been granted any permissions.
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_FOREGROUND_APP.readMedicalResources(request));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Caller doesn't have"
                                + " android.permission.health.READ_MEDICAL_DATA_VACCINES to read"
                                + " MedicalResource");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundWithReadPermNoWritePerm_canReadResourcesWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // calling app only has READ_MEDICAL_DATA_VACCINES permissions
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_VACCINES));

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource foregroundAppImmunization =
                PHR_FOREGROUND_APP.upsertMedicalResource(
                        foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        revokePermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        // When the app reads all immunization resources from the foreground
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesResponse response = PHR_FOREGROUND_APP.readMedicalResources(request);

        // Then it receives all immunization resources
        assertThat(response.getMedicalResources())
                .containsExactly(foregroundAppImmunization, backgroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundWithReadPermNoWritePerm_throwsForResourcesWithoutReadPerms() {
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), READ_MEDICAL_DATA_VACCINES);
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES)
                        .build();

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_FOREGROUND_APP.readMedicalResources(request));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Caller doesn't have"
                            + " android.permission.health.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES"
                            + " to read MedicalResource");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundHasWritePermNoReadPerms_canReadDataFromOwnDataSources()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // calling app only has WRITE_MEDICAL_DATA permissions
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource foregroundAppImmunization =
                PHR_FOREGROUND_APP.upsertMedicalResource(
                        foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        PHR_BACKGROUND_APP.upsertMedicalResource(
                backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        // When the app reads all immunization resources from the foreground
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesResponse response = PHR_FOREGROUND_APP.readMedicalResources(request);

        // Then it only receives the immunization resources written by itself
        assertThat(response.getMedicalResources()).containsExactly(foregroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundHasWriteAndReadPerms_canReadSelfDataAndOtherDataWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization and one allergy
        // each and the calling app only has WRITE_MEDICAL_DATA and READ_MEDICAL_DATA_VACCINES
        // permissions
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_VACCINES));

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource foregroundAppImmunization =
                PHR_FOREGROUND_APP.upsertMedicalResource(
                        foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource foregroundAppAllergy =
                PHR_FOREGROUND_APP.upsertMedicalResource(
                        foregroundAppDataSource.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        PHR_BACKGROUND_APP.upsertMedicalResource(
                backgroundAppDataSource.getId(), FHIR_DATA_ALLERGY);

        // When the app reads immunization resources and allergy resources from the foreground
        ReadMedicalResourcesInitialRequest readImmunizationsRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesInitialRequest readAllergiesRequest =
                new ReadMedicalResourcesInitialRequest.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES)
                        .build();
        ReadMedicalResourcesResponse readImmunizationsResponse =
                PHR_FOREGROUND_APP.readMedicalResources(readImmunizationsRequest);
        ReadMedicalResourcesResponse readAllergiesResponse =
                PHR_FOREGROUND_APP.readMedicalResources(readAllergiesRequest);

        // Then it receives all immunization resources, but only the allergy resources written by
        // itself
        assertThat(readImmunizationsResponse.getMedicalResources())
                .containsExactly(foregroundAppImmunization, backgroundAppImmunization);
        assertThat(readAllergiesResponse.getMedicalResources())
                .containsExactly(foregroundAppAllergy);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithBgReadNoOtherPerms_throws() {
        // App has background read permissions, but no other permissions.
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), READ_HEALTH_DATA_IN_BACKGROUND);
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_BACKGROUND_APP.readMedicalResources(request));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Caller doesn't have"
                                + " android.permission.health.READ_MEDICAL_DATA_VACCINES to read"
                                + " MedicalResource");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithBgReadHasReadPermNoWritePerm_canReadResourcesWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // calling app has READ_HEALTH_DATA_IN_BACKGROUND and READ_MEDICAL_DATA_VACCINES
        // permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        READ_MEDICAL_DATA_VACCINES));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource foregroundAppImmunization =
                PHR_FOREGROUND_APP.upsertMedicalResource(
                        foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        revokePermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        // When the app reads all immunization resources from the background
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesResponse response = PHR_BACKGROUND_APP.readMedicalResources(request);

        // Then it receives all immunization resources
        assertThat(response.getMedicalResources())
                .containsExactly(foregroundAppImmunization, backgroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithBgReadHasReadPermNoWritePerm_throwsForResourceWithoutReadPerms() {
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(READ_HEALTH_DATA_IN_BACKGROUND, READ_MEDICAL_DATA_VACCINES));
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES)
                        .build();

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_BACKGROUND_APP.readMedicalResources(request));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Caller doesn't have"
                            + " android.permission.health.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES"
                            + " to read MedicalResource");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithBgReadHasWritePermNoReadPerms_canReadDataFromOwnDataSources()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // calling app only has READ_HEALTH_DATA_IN_BACKGROUND and WRITE_MEDICAL_DATA permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_HEALTH_DATA_IN_BACKGROUND));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        PHR_FOREGROUND_APP.upsertMedicalResource(
                foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        // When the app reads all immunization resources from the background
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesResponse response = PHR_BACKGROUND_APP.readMedicalResources(request);

        // Then it only receives the immunization resources written by itself
        assertThat(response.getMedicalResources()).containsExactly(backgroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void
            testRead_inBgWithBgReadHasWriteAndReadPerms_canReadSelfDataAndOtherDataWithReadPerms()
                    throws Exception {
        // Given that we have two data sources from two apps with one immunization and one allergy
        // each and the calling app has READ_HEALTH_DATA_IN_BACKGROUND, WRITE_MEDICAL_DATA and
        // READ_MEDICAL_DATA_VACCINES permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        READ_MEDICAL_DATA_VACCINES));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource foregroundAppImmunization =
                PHR_FOREGROUND_APP.upsertMedicalResource(
                        foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        PHR_FOREGROUND_APP.upsertMedicalResource(
                foregroundAppDataSource.getId(), FHIR_DATA_ALLERGY);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalResource backgroundAppAllergy =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_ALLERGY);

        // When the app reads immunization resources and allergy resources from the background
        ReadMedicalResourcesInitialRequest readImmunizationsRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesInitialRequest readAllergiesRequest =
                new ReadMedicalResourcesInitialRequest.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES)
                        .build();
        ReadMedicalResourcesResponse readImmunizationsResponse =
                PHR_BACKGROUND_APP.readMedicalResources(readImmunizationsRequest);
        ReadMedicalResourcesResponse readAllergiesResponse =
                PHR_BACKGROUND_APP.readMedicalResources(readAllergiesRequest);

        // Then it receives all immunization resources, but only the allergy resources written by
        // itself
        assertThat(readImmunizationsResponse.getMedicalResources())
                .containsExactly(foregroundAppImmunization, backgroundAppImmunization);
        assertThat(readAllergiesResponse.getMedicalResources())
                .containsExactly(backgroundAppAllergy);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBackgoundWithNoPerms_throws() {
        // App has not been granted any permissions.
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_BACKGROUND_APP.readMedicalResources(request));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Caller doesn't have"
                                + " android.permission.health.READ_MEDICAL_DATA_VACCINES to read"
                                + " MedicalResource");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithoutBgReadOnlyWritePerm_canReadDataFromOwnDataSources()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // calling app only has WRITE_MEDICAL_DATA permissions
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        PHR_FOREGROUND_APP.upsertMedicalResource(
                foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        // When the app reads all immunization resources from the background
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesResponse response = PHR_BACKGROUND_APP.readMedicalResources(request);

        // Then it only receives the immunization resources written by itself
        assertThat(response.getMedicalResources()).containsExactly(backgroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithoutBgReadOnlyReadPerm_canReadOwnDataWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // and the calling app only has READ_MEDICAL_DATA_VACCINES permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_VACCINES));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        PHR_FOREGROUND_APP.upsertMedicalResource(
                foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        revokePermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        // When the app reads immunization resources from the background
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .build();
        ReadMedicalResourcesResponse response = PHR_BACKGROUND_APP.readMedicalResources(request);

        // Then it receives only receives its own immunization resources
        assertThat(response.getMedicalResources()).containsExactly(backgroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithoutBgReadOnlyReadPerm_throwsForResourcesWithoutReadPerms() {
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), READ_MEDICAL_DATA_VACCINES);
        ReadMedicalResourcesInitialRequest request =
                new ReadMedicalResourcesInitialRequest.Builder(
                                MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES)
                        .build();

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_BACKGROUND_APP.readMedicalResources(request));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Caller doesn't have"
                            + " android.permission.health.READ_MEDICAL_DATA_ALLERGIES_INTOLERANCES"
                            + " to read MedicalResource");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_readPermRemovedBeforePageRequest_throws() throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // and the calling app has READ_MEDICAL_DATA_VACCINES permissions
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_VACCINES));

        MedicalDataSource foregroundAppDataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        PHR_FOREGROUND_APP.upsertMedicalResource(
                foregroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource backgroundAppDataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        PHR_BACKGROUND_APP.upsertMedicalResource(
                backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        revokePermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        // When the app reads the first immunization, but loses read permissions before the second
        // page read
        ReadMedicalResourcesInitialRequest initialRequest =
                new ReadMedicalResourcesInitialRequest.Builder(MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS)
                        .setPageSize(1)
                        .build();
        ReadMedicalResourcesResponse initialResponse =
                PHR_FOREGROUND_APP.readMedicalResources(initialRequest);
        revokePermission(PHR_FOREGROUND_APP.getPackageName(), READ_MEDICAL_DATA_VACCINES);
        String nextPageToken = initialResponse.getNextPageToken();
        assertThat(nextPageToken).isNotNull();
        ReadMedicalResourcesPageRequest pageRequest =
                new ReadMedicalResourcesPageRequest.Builder(nextPageToken).build();

        // Then an exception is thrown on the second page read
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_FOREGROUND_APP.readMedicalResources(pageRequest));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains(
                        "Caller doesn't have"
                                + " android.permission.health.READ_MEDICAL_DATA_VACCINES"
                                + " to read MedicalResource");
    }

    // We are only testing permission mapping for one type here, because testing all permissions
    // in one test leads to presubmit test timeout. The full list of read permission mappings is
    // tested in the ReadMedicalResourcesByIdsCtsTest.
    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testReadPermissionMapping_permission_onlyGivesAccessToSpecificData()
            throws Exception {
        mUtil.insertSourceAndOneResourcePerPermissionCategory(PHR_BACKGROUND_APP);

        grantPermission(PHR_FOREGROUND_APP.getPackageName(), READ_MEDICAL_DATA_CONDITIONS);
        Set<Integer> notPermittedTypes = new HashSet<>(MEDICAL_RESOURCE_TYPES_LIST);
        notPermittedTypes.remove(Integer.valueOf(MEDICAL_RESOURCE_TYPE_CONDITIONS));

        assertThat(
                        PHR_FOREGROUND_APP
                                .readMedicalResources(
                                        new ReadMedicalResourcesInitialRequest.Builder(
                                                        MEDICAL_RESOURCE_TYPE_CONDITIONS)
                                                .build())
                                .getMedicalResources()
                                .get(0)
                                .getType())
                .isEqualTo(MEDICAL_RESOURCE_TYPE_CONDITIONS);
        for (int medicalResourceType : notPermittedTypes) {
            ReadMedicalResourcesInitialRequest request =
                    new ReadMedicalResourcesInitialRequest.Builder(medicalResourceType).build();
            assertThrows(
                    "Reading medicalResourceType: " + medicalResourceType,
                    HealthConnectException.class,
                    () -> PHR_FOREGROUND_APP.readMedicalResources(request));
        }
    }
}
