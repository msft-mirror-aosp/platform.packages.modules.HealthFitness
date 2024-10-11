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
import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_IMMUNIZATIONS;
import static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_BACKGROUND_APP;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_FOREGROUND_APP;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAllPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokePermission;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.HealthConnectException;
import android.health.connect.ReadMedicalResourcesInitialRequest;
import android.health.connect.ReadMedicalResourcesPageRequest;
import android.health.connect.ReadMedicalResourcesResponse;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
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

import java.util.List;

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

    @Before
    public void before() throws InterruptedException {
        // To make sure we don't leave any state behind after running each test.
        revokeAllPermissions(PHR_BACKGROUND_APP.getPackageName(), "to test specific permissions");
        revokeAllPermissions(PHR_FOREGROUND_APP.getPackageName(), "to test specific permissions");
        TestUtils.deleteAllStagedRemoteData();
        mUtil = new PhrCtsTestUtils(TestUtils.getHealthConnectManager());
        mUtil.deleteAllMedicalData();
    }

    @After
    public void after() throws InterruptedException {
        mUtil.deleteAllMedicalData();
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
                            + " android.permission.health.READ_MEDICAL_DATA_IMMUNIZATIONS to read"
                            + " MedicalResource");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundWithReadPermNoWritePerm_canReadResourcesWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // calling app only has READ_MEDICAL_DATA_IMMUNIZATIONS permissions
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));

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
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), READ_MEDICAL_DATA_IMMUNIZATIONS);
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
        // each and the calling app only has WRITE_MEDICAL_DATA and READ_MEDICAL_DATA_IMMUNIZATIONS
        // permissions
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));

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
                            + " android.permission.health.READ_MEDICAL_DATA_IMMUNIZATIONS to read"
                            + " MedicalResource");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithBgReadHasReadPermNoWritePerm_canReadResourcesWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // calling app has READ_HEALTH_DATA_IN_BACKGROUND and READ_MEDICAL_DATA_IMMUNIZATIONS
        // permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        READ_MEDICAL_DATA_IMMUNIZATIONS));
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
                List.of(READ_HEALTH_DATA_IN_BACKGROUND, READ_MEDICAL_DATA_IMMUNIZATIONS));
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
        // READ_MEDICAL_DATA_IMMUNIZATIONS permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        READ_MEDICAL_DATA_IMMUNIZATIONS));
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
                            + " android.permission.health.READ_MEDICAL_DATA_IMMUNIZATIONS to read"
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
        // and the calling app only has READ_MEDICAL_DATA_IMMUNIZATIONS permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
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
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), READ_MEDICAL_DATA_IMMUNIZATIONS);
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
        // and the calling app has READ_MEDICAL_DATA_IMMUNIZATIONS permissions
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_FOREGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));

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
        revokePermission(PHR_FOREGROUND_APP.getPackageName(), READ_MEDICAL_DATA_IMMUNIZATIONS);
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
                                + " android.permission.health.READ_MEDICAL_DATA_IMMUNIZATIONS"
                                + " to read MedicalResource");
    }
}
