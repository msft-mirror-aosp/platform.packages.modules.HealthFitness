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
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;

import static com.android.healthfitness.flags.AconfigFlagHelper.isPersonalHealthRecordEnabled;

import android.app.UiAutomation;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.GetMedicalDataSourcesRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.lib.TestAppProxy;
import android.healthconnect.cts.utils.HealthConnectReceiver;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.Iterables;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhrCtsTestUtils {

    static final int MAX_FOREGROUND_READ_CALL_15M = 2000;
    static final String PHR_BACKGROUND_APP_PKG = "android.healthconnect.cts.phr.testhelper.app1";
    static final String PHR_FOREGROUND_APP_PKG = "android.healthconnect.cts.phr.testhelper.app2";
    static final TestAppProxy PHR_BACKGROUND_APP =
            TestAppProxy.forPackageNameInBackground(PHR_BACKGROUND_APP_PKG);
    static final TestAppProxy PHR_FOREGROUND_APP =
            TestAppProxy.forPackageName(PHR_FOREGROUND_APP_PKG);

    int mLimitsAdjustmentForTesting = 1;
    private final HealthConnectManager mManager;

    public PhrCtsTestUtils(HealthConnectManager manager) {
        mManager = manager;
    }

    MedicalDataSource createDataSource(CreateMedicalDataSourceRequest createRequest)
            throws InterruptedException {
        HealthConnectReceiver<MedicalDataSource> createReceiver = new HealthConnectReceiver<>();
        mManager.createMedicalDataSource(
                createRequest, Executors.newSingleThreadExecutor(), createReceiver);
        return createReceiver.getResponse();
    }

    MedicalResource upsertMedicalData(String dataSourceId, String data)
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

    /**
     * Delete all health records (datasources, resources etc) stored in the Health Connect database.
     */
    public void deleteAllMedicalData() throws InterruptedException {
        if (!isPersonalHealthRecordEnabled()) {
            return;
        }
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(MANAGE_HEALTH_DATA);
        try {
            HealthConnectReceiver<List<MedicalDataSource>> receiver = new HealthConnectReceiver<>();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            mManager.getMedicalDataSources(
                    new GetMedicalDataSourcesRequest.Builder().build(), executor, receiver);
            List<MedicalDataSource> dataSources = receiver.getResponse();
            for (MedicalDataSource dataSource : dataSources) {
                HealthConnectReceiver<Void> callback = new HealthConnectReceiver<>();
                mManager.deleteMedicalDataSourceWithData(dataSource.getId(), executor, callback);
                callback.verifyNoExceptionOrThrow();
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
