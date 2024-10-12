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
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_BACKGROUND_APP;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_BACKGROUND_APP_PKG;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_FOREGROUND_APP;
import static android.healthconnect.cts.phr.PhrCtsTestUtils.PHR_FOREGROUND_APP_PKG;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokeAllPermissions;
import static android.healthconnect.cts.utils.PermissionHelper.revokePermission;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_ID;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.MEDICAL_DATA_SOURCE_EQUIVALENCE;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.health.connect.HealthConnectException;
import android.health.connect.datatypes.MedicalDataSource;
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
public class GetMedicalDataSourcesByIdsCtsTest {
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
        revokeAllPermissions(PHR_BACKGROUND_APP_PKG, "to test specific permissions");
        revokeAllPermissions(PHR_FOREGROUND_APP_PKG, "to test specific permissions");
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
    public void testGetMedicalDataSourcesById_inForegroundWithNoPerms_throws() {
        // App has not been granted any permissions.
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_FOREGROUND_APP.getMedicalDataSources(List.of(DATA_SOURCE_ID)));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inForegroundWithReadPermNoWritePerm()
            throws Exception {
        // To write data from two different apps.
        grantPermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_FOREGROUND_APP_PKG,
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in foreground, no write permission but has
        // immunization read permission. App can read all dataSources belonging to immunizations.
        revokePermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);
        List<MedicalDataSource> result =
                PHR_FOREGROUND_APP.getMedicalDataSources(
                        List.of(
                                dataSource1Foreground.getId(),
                                dataSource1Background.getId(),
                                dataSource2Background.getId()));

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Foreground, dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inForegroundHasWritePermNoReadPerms()
            throws Exception {
        // To write data from two different apps.
        grantPermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);
        grantPermission(PHR_FOREGROUND_APP_PKG, WRITE_MEDICAL_DATA);
        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in foreground, has write permission but
        // no read permission for any resource types.
        // App can only read dataSources they wrote themselves.
        List<MedicalDataSource> result =
                PHR_FOREGROUND_APP.getMedicalDataSources(
                        List.of(
                                dataSource1Foreground.getId(),
                                dataSource1Background.getId(),
                                dataSource2Background.getId()));

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Foreground);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inForegroundHasWriteAndReadPerms() throws Exception {
        // To write data from two different apps.
        grantPermission(PHR_BACKGROUND_APP_PKG, WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_FOREGROUND_APP_PKG,
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
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
        // App can read dataSources they wrote themselves and dataSources belonging to
        // immunization resource types.
        List<MedicalDataSource> result =
                PHR_FOREGROUND_APP.getMedicalDataSources(
                        List.of(
                                dataSource1Foreground.getId(),
                                dataSource2Foreground.getId(),
                                dataSource1Background.getId(),
                                dataSource2Background.getId()));

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(
                        dataSource1Foreground, dataSource2Foreground, dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inBgWithoutBgPermHasWritePermNoReadPerms()
            throws Exception {
        // The app under test.
        grantPermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        // Another app to write some more data.
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());

        // Calling app is running in the background without background read permission.
        // The app has write permission and read immunization permission.
        // The app can read data sources created by itself only.
        List<MedicalDataSource> result =
                PHR_BACKGROUND_APP.getMedicalDataSources(
                        List.of(dataSource1Foreground.getId(), dataSource2Background.getId()));

        assertThat(result).containsExactly(dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inBgWithNoPerms_throws() throws Exception {
        // App has not been granted any permissions.
        HealthConnectException exception =
                assertThrows(
                        HealthConnectException.class,
                        () -> PHR_BACKGROUND_APP.getMedicalDataSources(List.of(DATA_SOURCE_ID)));
        assertThat(exception.getErrorCode()).isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inBgWithoutBgPermNoWritePermOnlyReadPerm()
            throws Exception {
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_MEDICAL_DATA_IMMUNIZATIONS));
        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background without background read perm, no write permission but has
        // immunization read permission. App can read dataSources belonging to immunizations that
        // the app wrote itself.
        revokePermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        List<MedicalDataSource> result =
                PHR_BACKGROUND_APP.getMedicalDataSources(
                        List.of(
                                dataSource1Foreground.getId(),
                                dataSource1Background.getId(),
                                dataSource2Background.getId()));

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inBgWithBgReadPermNoWritePermHasReadPerm()
            throws Exception {
        // To write data from two different apps.
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(
                        WRITE_MEDICAL_DATA,
                        READ_HEALTH_DATA_IN_BACKGROUND,
                        READ_MEDICAL_DATA_IMMUNIZATIONS));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        PHR_FOREGROUND_APP.upsertMedicalResource(
                dataSource1Foreground.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        PHR_BACKGROUND_APP.upsertMedicalResource(
                dataSource1Background.getId(), FHIR_DATA_IMMUNIZATION);
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));
        PHR_BACKGROUND_APP.upsertMedicalResource(dataSource2Background.getId(), FHIR_DATA_ALLERGY);

        // App is in background with background read permission, no write permission but has
        // immunization read permission. App can read all dataSources belonging to immunizations.
        revokePermission(PHR_BACKGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        List<MedicalDataSource> result =
                PHR_BACKGROUND_APP.getMedicalDataSources(
                        List.of(
                                dataSource1Foreground.getId(),
                                dataSource1Background.getId(),
                                dataSource2Background.getId()));

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Foreground, dataSource1Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inBgWithBgReadPermHasWritePermNoReadPerms()
            throws Exception {
        // To write data from two different apps.
        grantPermissions(
                PHR_BACKGROUND_APP.getPackageName(),
                List.of(WRITE_MEDICAL_DATA, READ_HEALTH_DATA_IN_BACKGROUND));
        grantPermission(PHR_FOREGROUND_APP.getPackageName(), WRITE_MEDICAL_DATA);
        MedicalDataSource dataSource1Foreground =
                PHR_FOREGROUND_APP.createMedicalDataSource(getCreateMedicalDataSourceRequest());
        MedicalDataSource dataSource1Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/1"));
        MedicalDataSource dataSource2Background =
                PHR_BACKGROUND_APP.createMedicalDataSource(
                        getCreateMedicalDataSourceRequest("ds/2"));

        // App is in background with background read permission, has write permission but
        // no read permission for any resource types.
        // App can only read dataSources they wrote themselves.
        List<MedicalDataSource> result =
                PHR_BACKGROUND_APP.getMedicalDataSources(
                        List.of(
                                dataSource1Foreground.getId(),
                                dataSource1Background.getId(),
                                dataSource2Background.getId()));

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(dataSource1Background, dataSource2Background);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testGetMedicalDataSourcesById_inBgWithBgPermHasWriteAndReadPerm() throws Exception {
        // To write data from two different apps.
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

        // App is in background, has background read permission,
        // has write permission and immunization read permissions.
        // App can read dataSources they wrote themselves and dataSources belonging to
        // immunization resource types.
        List<MedicalDataSource> result =
                PHR_BACKGROUND_APP.getMedicalDataSources(
                        List.of(
                                dataSource1Foreground.getId(),
                                dataSource2Foreground.getId(),
                                dataSource1Background.getId(),
                                dataSource2Background.getId()));

        assertThat(result)
                .comparingElementsUsing(MEDICAL_DATA_SOURCE_EQUIVALENCE)
                .containsExactly(
                        dataSource1Background, dataSource2Background, dataSource1Foreground);
    }
}


