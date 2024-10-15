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

import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PhrDataFactory.MAX_ALLOWED_MEDICAL_DATA_SOURCES;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;
import static android.healthconnect.cts.utils.TestUtils.finishMigrationWithShellPermissionIdentity;
import static android.healthconnect.cts.utils.TestUtils.startMigrationWithShellPermissionIdentity;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;
import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.datatypes.MedicalDataSource;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class CreateMedicalDataSourceCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private HealthConnectManager mManager;
    private PhrCtsTestUtils mUtil;

    private static final String APP_PACKAGE_NAME = "android.healthconnect.cts.phr";

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
    public void testCreateMedicalDataSource_migrationInProgress_apiBlocked()
            throws InterruptedException {
        startMigrationWithShellPermissionIdentity();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();

        mManager.createMedicalDataSource(getCreateMedicalDataSourceRequest(), executor, receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_DATA_SYNC_IN_PROGRESS);

        finishMigrationWithShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testCreateMedicalDataSource_hasDataManagementPermission_throws()
            throws InterruptedException {
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();

        runWithShellPermissionIdentity(
                () -> {
                    mManager.createMedicalDataSource(
                            getCreateMedicalDataSourceRequest(),
                            Executors.newSingleThreadExecutor(),
                            receiver);
                    assertThat(receiver.assertAndGetException().getErrorCode())
                            .isEqualTo(HealthConnectException.ERROR_SECURITY);
                },
                MANAGE_HEALTH_DATA);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testCreateMedicalDataSource_succeeds() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();
        CreateMedicalDataSourceRequest request = getCreateMedicalDataSourceRequest();

        mManager.createMedicalDataSource(request, executor, receiver);

        MedicalDataSource responseDataSource = receiver.getResponse();
        assertThat(responseDataSource).isInstanceOf(MedicalDataSource.class);
        assertThat(responseDataSource.getId()).isNotEmpty();
        assertThat(responseDataSource.getFhirBaseUri()).isEqualTo(request.getFhirBaseUri());
        assertThat(responseDataSource.getDisplayName()).isEqualTo(request.getDisplayName());
        assertThat(responseDataSource.getFhirVersion()).isEqualTo(request.getFhirVersion());
        assertThat(responseDataSource.getPackageName()).isEqualTo(APP_PACKAGE_NAME);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testCreateMedicalDataSource_maxNumberOfSources_succeeds()
            throws InterruptedException {
        for (int i = 0; i < MAX_ALLOWED_MEDICAL_DATA_SOURCES - 1; i++) {
            String suffix = String.valueOf(i);
            mUtil.createDataSource(getCreateMedicalDataSourceRequest(suffix));
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(getCreateMedicalDataSourceRequest(), executor, receiver);

        receiver.verifyNoExceptionOrThrow();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_PERSONAL_HEALTH_RECORD, FLAG_PERSONAL_HEALTH_RECORD_DATABASE})
    public void testCreateMedicalDataSource_moreThanAllowedMax_throws()
            throws InterruptedException {
        for (int i = 0; i < MAX_ALLOWED_MEDICAL_DATA_SOURCES; i++) {
            String suffix = String.valueOf(i);
            mUtil.createDataSource(getCreateMedicalDataSourceRequest(suffix));
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        HealthConnectReceiver<MedicalDataSource> receiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(getCreateMedicalDataSourceRequest(), executor, receiver);

        assertThat(receiver.assertAndGetException().getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_INVALID_ARGUMENT);
    }
}
