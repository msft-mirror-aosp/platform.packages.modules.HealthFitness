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
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAllPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokePermission;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;
import static android.healthconnect.cts.utils.PhrDataFactory.getMedicalResourceId;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.HealthConnectException;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.lib.TestAppProxy;
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
public class ReadMedicalResourcesByIdsCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private static final String PHR_TEST_APP_1 = "android.healthconnect.cts.phr.testhelper.app1";
    private static final String PHR_TEST_APP_2 = "android.healthconnect.cts.phr.testhelper.app2";
    static final TestAppProxy PHR_BACKGROUND_APP =
            TestAppProxy.forPackageNameInBackground(PHR_TEST_APP_1);
    static final TestAppProxy PHR_FOREGROUND_APP = TestAppProxy.forPackageName(PHR_TEST_APP_2);

    @Before
    public void before() throws InterruptedException {
        // To make sure we don't leave any state behind after running each test.
        revokeAllPermissions(PHR_BACKGROUND_APP.getPackageName(), "to test specific permissions");
        revokeAllPermissions(PHR_FOREGROUND_APP.getPackageName(), "to test specific permissions");
        TestUtils.deleteAllStagedRemoteData();
        TestUtils.deleteAllMedicalData();
    }

    @After
    public void after() throws InterruptedException {
        TestUtils.deleteAllMedicalData();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundWithNoPerms_throws() {
        // App has not been granted any permissions.
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                PHR_FOREGROUND_APP.readMedicalResources(
                                        List.of(getMedicalResourceId())));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains("Caller doesn't have permission to read or write medical data");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundWithReadPermNoWritePerm_onlyReturnsResourcesWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization and one allergy
        // each and the calling app only has READ_MEDICAL_DATA_IMMUNIZATIONS permissions
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
        MedicalResource backgroundAppAllergy =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_ALLERGY);

        revokePermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        // When the app tries to read all resources by id from the foreground
        List<MedicalResource> responseResources =
                PHR_FOREGROUND_APP.readMedicalResources(
                        List.of(
                                foregroundAppImmunization.getId(), foregroundAppAllergy.getId(),
                                backgroundAppImmunization.getId(), backgroundAppAllergy.getId()));

        // Then it receives all immunization resources
        assertThat(responseResources)
                .containsExactly(foregroundAppImmunization, backgroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundHasWritePermNoReadPerms_onlyReturnsDataFromOwnDataSources()
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
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        // When the app reads all immunization resources from the foreground
        List<MedicalResource> resourcesResponse =
                PHR_FOREGROUND_APP.readMedicalResources(
                        List.of(
                                foregroundAppImmunization.getId(),
                                backgroundAppImmunization.getId()));

        // Then it only receives the immunization resources written by itself
        assertThat(resourcesResponse).containsExactly(foregroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inForegroundHasWriteAndReadPerms_returnsSelfDataAndOtherDataWithReadPerms()
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
        MedicalResource backgroundAppAllergy =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_ALLERGY);

        // When the app tries to read all resources from the foreground
        List<MedicalResource> resourcesResponse =
                PHR_FOREGROUND_APP.readMedicalResources(
                        List.of(
                                foregroundAppImmunization.getId(), foregroundAppAllergy.getId(),
                                backgroundAppImmunization.getId(), backgroundAppAllergy.getId()));

        // Then it receives all immunization resources, but only the allergy resources written by
        // itself
        assertThat(resourcesResponse)
                .containsExactly(
                        foregroundAppImmunization, backgroundAppImmunization, foregroundAppAllergy);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithBgReadNoOtherPerms_throws() {
        // App has background read permissions, but no other permissions.
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), READ_HEALTH_DATA_IN_BACKGROUND);

        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                PHR_BACKGROUND_APP.readMedicalResources(
                                        List.of(getMedicalResourceId())));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains("Caller doesn't have permission to read or write medical data");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithBgReadHasReadPermNoWritePerm_onlyReturnsResourcesWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization and one allergy
        // each and the calling app has READ_HEALTH_DATA_IN_BACKGROUND and
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
        MedicalResource foregroundAppAllergy =
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

        revokePermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        // When the app tries to read all resources from the background
        List<MedicalResource> resourcesResponse =
                PHR_BACKGROUND_APP.readMedicalResources(
                        List.of(
                                foregroundAppImmunization.getId(), foregroundAppAllergy.getId(),
                                backgroundAppImmunization.getId(), backgroundAppAllergy.getId()));

        // Then it receives only immunization resources
        assertThat(resourcesResponse)
                .containsExactly(foregroundAppImmunization, backgroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithBgReadHasWritePermNoReadPerms_onlyReturnsDataFromOwnDataSources()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization each and the
        // calling app only has READ_HEALTH_DATA_IN_BACKGROUND and WRITE_MEDICAL_DATA permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_HEALTH_DATA_IN_BACKGROUND));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

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
        MedicalResource backgroundAppAllergy =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_ALLERGY);

        // When the app tries to read all resources from the background
        List<MedicalResource> resourcesResponse =
                PHR_BACKGROUND_APP.readMedicalResources(
                        List.of(
                                foregroundAppImmunization.getId(), foregroundAppAllergy.getId(),
                                backgroundAppImmunization.getId(), backgroundAppAllergy.getId()));

        // Then it only receives the resources written by itself
        assertThat(resourcesResponse)
                .containsExactly(backgroundAppImmunization, backgroundAppAllergy);
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
        MedicalResource foregroundAppAllergy =
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

        // When the app tries to read all resources from the background
        List<MedicalResource> responseResources =
                PHR_BACKGROUND_APP.readMedicalResources(
                        List.of(
                                foregroundAppImmunization.getId(), foregroundAppAllergy.getId(),
                                backgroundAppImmunization.getId(), backgroundAppAllergy.getId()));

        // Then it receives all immunization resources, but only the allergy resources written by
        // itself
        assertThat(responseResources)
                .containsExactly(
                        foregroundAppImmunization, backgroundAppImmunization, backgroundAppAllergy);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBackgoundWithNoPerms_throws() {
        // App has not been granted any permissions.
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () ->
                                PHR_BACKGROUND_APP.readMedicalResources(
                                        List.of(getMedicalResourceId())));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
        assertThat(exception)
                .hasMessageThat()
                .contains("Caller doesn't have permission to read or write medical data");
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithoutBgReadOnlyWritePerm_onlyReturnsDataFromOwnDataSources()
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
        MedicalResource backgroundAppImmunization =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_IMMUNIZATION);

        // When the app tries to read all resources from the background
        List<MedicalResource> responseResources =
                PHR_BACKGROUND_APP.readMedicalResources(
                        List.of(
                                foregroundAppImmunization.getId(),
                                backgroundAppImmunization.getId()));

        // Then it only receives its own resources
        assertThat(responseResources).containsExactly(backgroundAppImmunization);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testRead_inBgWithoutBgReadOnlyReadPerm_onlyReturnsDataWithReadPerms()
            throws Exception {
        // Given that we have two data sources from two apps with one immunization and one allergy
        // each and the
        // and the calling app only has READ_MEDICAL_DATA_IMMUNIZATIONS permissions
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

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
        MedicalResource backgroundAppAllergy =
                PHR_BACKGROUND_APP.upsertMedicalResource(
                        backgroundAppDataSource.getId(), FHIR_DATA_ALLERGY);

        revokePermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);

        // When the app tries to read all resources from the background
        List<MedicalResource> responseResources =
                PHR_BACKGROUND_APP.readMedicalResources(
                        List.of(
                                foregroundAppImmunization.getId(), foregroundAppAllergy.getId(),
                                backgroundAppImmunization.getId(), backgroundAppAllergy.getId()));

        // Then it only receives its own immunization resources
        assertThat(responseResources).containsExactly(backgroundAppImmunization);
    }
}
