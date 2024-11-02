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

import static android.health.connect.HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS;
import static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.MAX_FOREGROUND_WRITE_CALL_15M;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.PHR_BACKGROUND_APP;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.PHR_FOREGROUND_APP;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.PHR_FOREGROUND_APP_PKG;
import static android.healthconnect.cts.phr.utils.PhrCtsTestUtils.RECORD_SIZE_LIMIT_IN_BYTES;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_ID_EMPTY;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION_UNSUPPORTED_RESOURCE_TYPE;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_VERSION_R4B;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.FHIR_VERSION_UNSUPPORTED;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.createImmunizationMedicalResource;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.createUpdatedImmunizationMedicalResource;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;
import static android.healthconnect.cts.phr.utils.PhrDataFactory.getUpsertMedicalResourceRequest;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAllPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokePermission;
import static android.healthconnect.cts.utils.TestUtils.finishMigrationWithShellPermissionIdentity;
import static android.healthconnect.cts.utils.TestUtils.setFieldValueUsingReflection;
import static android.healthconnect.cts.utils.TestUtils.startMigrationWithShellPermissionIdentity;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;
import static com.android.healthfitness.flags.Flags.FLAG_PHR_FHIR_STRUCTURAL_VALIDATION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.util.concurrent.Executors.newSingleThreadExecutor;

import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.phr.utils.ImmunizationBuilder;
import android.healthconnect.cts.phr.utils.MedicationsBuilder;
import android.healthconnect.cts.phr.utils.PhrCtsTestUtils;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.base.Strings;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class UpsertMedicalResourcesCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private HealthConnectManager mManager;
    private PhrCtsTestUtils mUtil;

    @Before
    public void before() throws InterruptedException {
        // To make sure we don't leave any state behind after running each test.
        revokeAllPermissions(PHR_BACKGROUND_APP.getPackageName(), "to test specific permissions");
        revokeAllPermissions(PHR_FOREGROUND_APP.getPackageName(), "to test specific permissions");
        TestUtils.deleteAllStagedRemoteData();
        mManager = TestUtils.getHealthConnectManager();
        mUtil = new PhrCtsTestUtils(mManager);
        mUtil.deleteAllMedicalData();
        if (TestUtils.setLowerRateLimitsForTesting(true)) {
            // 10 comes from the setLowerRateLimitsForTesting method in RateLimiter.
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
    public void testUpsertMedicalResources_migrationInProgress_apiBlocked()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        List<UpsertMedicalResourceRequest> requests = List.of(getUpsertMedicalResourceRequest());
        startMigrationWithShellPermissionIdentity();

        mManager.upsertMedicalResources(requests, newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(ERROR_DATA_SYNC_IN_PROGRESS);

        finishMigrationWithShellPermissionIdentity();
    }

    // TODO(b/370731291): Investigate and add tests against rolling memory limit
    // QUOTA_BUCKET_DATA_PUSH_LIMIT_PER_APP_15M and QUOTA_BUCKET_DATA_PUSH_LIMIT_ACROSS_APPS_15M.
    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_writeLimitExceeded_throws() throws Exception {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());

        // Make the maximum number of calls allowed by quota. Minus 1 because of the above call.
        int maximumCalls = MAX_FOREGROUND_WRITE_CALL_15M / mUtil.mLimitsAdjustmentForTesting - 1;
        for (int i = 0; i < maximumCalls; i++) {
            HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
            String resourceData = new ImmunizationBuilder().setId("Immunization" + i).toJson();
            UpsertMedicalResourceRequest request =
                    new UpsertMedicalResourceRequest.Builder(
                                    dataSource.getId(), FHIR_VERSION_R4, resourceData)
                            .build();
            mManager.upsertMedicalResources(
                    List.of(request), Executors.newSingleThreadExecutor(), receiver);
            receiver.verifyNoExceptionOrThrow();
        }

        // Make 1 extra create call and check quota is exceeded.
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        String resourceData =
                new ImmunizationBuilder().setId("Immunization" + maximumCalls).toJson();
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                dataSource.getId(), FHIR_VERSION_R4, resourceData)
                        .build();
        mManager.upsertMedicalResources(
                List.of(request), Executors.newSingleThreadExecutor(), receiver);

        HealthConnectException exception = receiver.assertAndGetException();
        assertThat(exception.getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED);
        assertThat(exception.getMessage()).contains("API call quota exceeded");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_singleRequestSizeLimitExceeded_throws()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        // Divided by 2 because string size is 2 bytes per char.
        int nCharacters = RECORD_SIZE_LIMIT_IN_BYTES / mUtil.mLimitsAdjustmentForTesting / 2;
        String data = Strings.repeat("0", nCharacters + 1);
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(DATA_SOURCE_ID, FHIR_VERSION_R4, data)
                        .build();

        mManager.upsertMedicalResources(
                List.of(request), Executors.newSingleThreadExecutor(), receiver);

        HealthConnectException exception = receiver.assertAndGetException();
        assertThat(exception.getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED);
        assertThat(exception.getMessage())
                .contains("Record size exceeded the single record size limit");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_memoryChunkSizeLimitExceeded_throws()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        // 1000 is just picked after trial and error. See comments in
        // http://ag/27893719/4..8/tests/cts/src/android/healthconnect/cts/ratelimiter/RateLimiterTest.java#b206.
        int nCopies = 1000 / mUtil.mLimitsAdjustmentForTesting;
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, "UpsertMedicalResourceRequest")
                        .build();

        mManager.upsertMedicalResources(
                Collections.nCopies(nCopies, request),
                Executors.newSingleThreadExecutor(),
                receiver);

        HealthConnectException exception = receiver.assertAndGetException();
        assertThat(exception.getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED);
        assertThat(exception.getMessage())
                .contains("Records chunk size exceeded the max chunk limit");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_forOwnDataSource_succeedsAndInserts()
            throws InterruptedException {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        MedicalResource expectedResource = createImmunizationMedicalResource(dataSource.getId());
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                expectedResource.getFhirResource().getData())
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        receiver.verifyNoExceptionOrThrow();
        assertThat(receiver.getResponse()).hasSize(1);
        assertThat(receiver.getResponse()).containsExactly(expectedResource);
        // Verifies the inserted resource exists.
        HealthConnectReceiver<List<MedicalResource>> resourceReadReceiver =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                List.of(receiver.getResponse().get(0).getId()),
                Executors.newSingleThreadExecutor(),
                resourceReadReceiver);
        assertThat(resourceReadReceiver.getResponse()).containsExactly(expectedResource);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_forOwnDataSourceAndExistingData_succeedsAndUpdates()
            throws Exception {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        MedicalResource resourceBeforeUpdate =
                createImmunizationMedicalResource(dataSource.getId());
        MedicalResource expectedUpdatedResource =
                createUpdatedImmunizationMedicalResource(dataSource.getId());
        UpsertMedicalResourceRequest insertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                resourceBeforeUpdate.getFhirResource().getData())
                        .build();
        UpsertMedicalResourceRequest updateRequest =
                new UpsertMedicalResourceRequest.Builder(
                                dataSource.getId(),
                                FHIR_VERSION_R4,
                                expectedUpdatedResource.getFhirResource().getData())
                        .build();

        HealthConnectReceiver<List<MedicalResource>> receiver1 = new HealthConnectReceiver<>();
        mManager.upsertMedicalResources(
                List.of(insertRequest), Executors.newSingleThreadExecutor(), receiver1);
        receiver1.verifyNoExceptionOrThrow();
        HealthConnectReceiver<List<MedicalResource>> receiver2 = new HealthConnectReceiver<>();
        mManager.upsertMedicalResources(
                List.of(updateRequest), Executors.newSingleThreadExecutor(), receiver2);
        receiver2.verifyNoExceptionOrThrow();

        assertThat(receiver2.getResponse()).hasSize(1);
        assertThat(receiver2.getResponse()).containsExactly(expectedUpdatedResource);
        // Verifies the updated resource exists.
        assertThat(receiver1.getResponse()).hasSize(1);
        HealthConnectReceiver<List<MedicalResource>> resourceReadReceiver =
                new HealthConnectReceiver<>();
        mManager.readMedicalResources(
                List.of(receiver1.getResponse().get(0).getId()),
                Executors.newSingleThreadExecutor(),
                resourceReadReceiver);
        assertThat(resourceReadReceiver.getResponse()).containsExactly(expectedUpdatedResource);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    public void testUpsertMedicalResources_validationEnabledUnknownField_throws() throws Exception {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        String immunizationResource =
                new ImmunizationBuilder().set("unknown_field", "value").toJson();
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                dataSource.getId(), FHIR_VERSION_R4, immunizationResource)
                        .build();

        mManager.upsertMedicalResources(
                List.of(request), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_resourceWithPrimitiveTypeExtension_succeeds()
            throws Exception {
        MedicalDataSource dataSource = mUtil.createDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        String immunizationResource =
                new ImmunizationBuilder()
                        .set("_status", new JSONObject("{\"id\": \"1234\"}"))
                        .toJson();
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                dataSource.getId(), FHIR_VERSION_R4, immunizationResource)
                        .build();

        mManager.upsertMedicalResources(
                List.of(request), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).hasSize(1);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_dataSourceOwnedByOtherApp_throws() throws Exception {
        // Create data source with different package name
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);
        MedicalDataSource dataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                dataSource.getId(), FHIR_VERSION_R4, FHIR_DATA_IMMUNIZATION)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_dataSourceDoesNotExist_throws()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest = getUpsertMedicalResourceRequest();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_emptyList_returnsEmptyList()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();

        mManager.upsertMedicalResources(List.of(), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.getResponse()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_invalidDataSourceIdByReflection_throws()
            throws Exception {
        UpsertMedicalResourceRequest request =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, FHIR_DATA_IMMUNIZATION)
                        .build();

        setFieldValueUsingReflection(request, "mDataSourceId", "invalid id");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mManager.upsertMedicalResources(
                                List.of(request),
                                Executors.newSingleThreadExecutor(),
                                new HealthConnectReceiver<>()));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_invalidJson_throws() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                FHIR_DATA_IMMUNIZATION_FIELD_MISSING_INVALID)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_missingResourceId_throws() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                FHIR_DATA_IMMUNIZATION_ID_NOT_EXISTS)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_emptyResourceId_throws() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_R4, FHIR_DATA_IMMUNIZATION_ID_EMPTY)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_missingResourceType_throws()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                FHIR_DATA_IMMUNIZATION_RESOURCE_TYPE_NOT_EXISTS)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_unsupportedResourceType_throws()
            throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                FHIR_DATA_IMMUNIZATION_UNSUPPORTED_RESOURCE_TYPE)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    public void testUpsertMedicalResources_resourceIncludesContainedResource_throws()
            throws InterruptedException {
        String medicationStatementWithContainedResource =
                new MedicationsBuilder.MedicationStatementR4Builder()
                        .setContainedMedication(new MedicationsBuilder.MedicationBuilder())
                        .toJson();
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                medicationStatementWithContainedResource)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PHR_FHIR_STRUCTURAL_VALIDATION})
    public void testUpsertMedicalResources_containedFieldIsNotArray_throws()
            throws InterruptedException, JSONException {
        String medicationStatementWithContainedResource =
                new MedicationsBuilder.MedicationStatementR4Builder()
                        .set("contained", new JSONObject("{}"))
                        .toJson();
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID,
                                FHIR_VERSION_R4,
                                medicationStatementWithContainedResource)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_PERSONAL_HEALTH_RECORD)
    public void testUpsertMedicalResources_unsupportedVersion_throws() throws InterruptedException {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                DATA_SOURCE_ID, FHIR_VERSION_UNSUPPORTED, FHIR_DATA_IMMUNIZATION)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_hasDataManagementPermission_throws() {
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    mManager.upsertMedicalResources(
                            List.of(getUpsertMedicalResourceRequest()),
                            Executors.newSingleThreadExecutor(),
                            receiver);
                    assertThat(receiver.assertAndGetException().getErrorCode())
                            .isEqualTo(HealthConnectException.ERROR_SECURITY);
                },
                MANAGE_HEALTH_DATA);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_fhirVersionNotMatchingDataSource_throws()
            throws InterruptedException {
        MedicalDataSource dataSource =
                mUtil.createDataSource(
                        new CreateMedicalDataSourceRequest.Builder(
                                        DATA_SOURCE_FHIR_BASE_URI,
                                        DATA_SOURCE_DISPLAY_NAME,
                                        FHIR_VERSION_R4)
                                .build());
        HealthConnectReceiver<List<MedicalResource>> receiver = new HealthConnectReceiver<>();
        UpsertMedicalResourceRequest upsertRequest =
                new UpsertMedicalResourceRequest.Builder(
                                dataSource.getId(), FHIR_VERSION_R4B, FHIR_DATA_IMMUNIZATION)
                        .build();

        mManager.upsertMedicalResources(
                List.of(upsertRequest), Executors.newSingleThreadExecutor(), receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_inForegroundNoWritePerms_throws() throws Exception {
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        MedicalDataSource dataSource =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        revokePermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                PHR_FOREGROUND_APP.upsertMedicalResource(
                                        dataSource.getId(), FHIR_DATA_IMMUNIZATION));

        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testUpsertMedicalResources_inBackgroundNoWritePerms_throws() throws Exception {
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        MedicalDataSource dataSource =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        revokePermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                PHR_BACKGROUND_APP.upsertMedicalResource(
                                        dataSource.getId(), FHIR_DATA_IMMUNIZATION));

        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }
}
