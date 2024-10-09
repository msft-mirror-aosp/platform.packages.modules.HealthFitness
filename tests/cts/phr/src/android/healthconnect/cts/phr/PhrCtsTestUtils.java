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

import static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_CONDITIONS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_MEDICATIONS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PRACTITIONER_DETAILS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PREGNANCY;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_PROCEDURES;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_VISITS;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_VITAL_SIGNS;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;
import static android.healthconnect.cts.utils.PermissionHelper.grantPermission;
import static android.healthconnect.cts.utils.PermissionHelper.revokePermission;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ALLERGY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_CONDITION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_ENCOUNTER;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_IMMUNIZATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_MEDICATION;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_OBSERVATION_LABS;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_OBSERVATION_PREGNANCY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_OBSERVATION_SOCIAL_HISTORY;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_OBSERVATION_VITAL_SIGNS;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_PRACTITIONER;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_PROCEDURE;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_DATA_Patient;
import static android.healthconnect.cts.utils.PhrDataFactory.FHIR_VERSION_R4;
import static android.healthconnect.cts.utils.PhrDataFactory.getCreateMedicalDataSourceRequest;

import static com.android.healthfitness.flags.AconfigFlagHelper.isPersonalHealthRecordEnabled;

import static com.google.common.base.Preconditions.checkState;

import static java.util.stream.Collectors.toSet;

import android.app.UiAutomation;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.GetMedicalDataSourcesRequest;
import android.health.connect.HealthConnectManager;
import android.health.connect.MedicalResourceId;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.healthconnect.cts.lib.TestAppProxy;
import android.healthconnect.cts.utils.HealthConnectReceiver;

import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.Iterables;

import java.util.List;
import java.util.Set;
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

    static final Set<Integer> MEDICAL_RESOURCE_TYPES_LIST =
            Set.of(
                    MEDICAL_RESOURCE_TYPE_IMMUNIZATIONS,
                    MEDICAL_RESOURCE_TYPE_ALLERGIES_INTOLERANCES,
                    MEDICAL_RESOURCE_TYPE_CONDITIONS,
                    MEDICAL_RESOURCE_TYPE_MEDICATIONS,
                    MEDICAL_RESOURCE_TYPE_PERSONAL_DETAILS,
                    MEDICAL_RESOURCE_TYPE_PRACTITIONER_DETAILS,
                    MEDICAL_RESOURCE_TYPE_VISITS,
                    MEDICAL_RESOURCE_TYPE_PROCEDURES,
                    MEDICAL_RESOURCE_TYPE_PREGNANCY,
                    MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY,
                    MEDICAL_RESOURCE_TYPE_VITAL_SIGNS,
                    MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS);

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

    void deleteResources(List<MedicalResourceId> resourceIds) throws InterruptedException {
        HealthConnectReceiver<Void> deleteReceiver = new HealthConnectReceiver<>();
        mManager.deleteMedicalResources(
                resourceIds, Executors.newSingleThreadExecutor(), deleteReceiver);
        deleteReceiver.verifyNoExceptionOrThrow();
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

    /**
     * Inserts a data source with one resource for each permission category and returns the ids of
     * inserted resources.
     */
    public List<MedicalResourceId> insertSourceAndOneResourcePerPermissionCategory(
            TestAppProxy appProxy) throws Exception {
        grantPermission(appProxy.getPackageName(), WRITE_MEDICAL_DATA);
        String dataSourceId =
                appProxy.createMedicalDataSource(getCreateMedicalDataSourceRequest()).getId();
        List<MedicalResource> insertedMedicalResources =
                List.of(
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_IMMUNIZATION),
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_ALLERGY),
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_CONDITION),
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_MEDICATION),
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_Patient),
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_PRACTITIONER),
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_ENCOUNTER),
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_PROCEDURE),
                        appProxy.upsertMedicalResource(
                                dataSourceId, FHIR_DATA_OBSERVATION_PREGNANCY),
                        appProxy.upsertMedicalResource(
                                dataSourceId, FHIR_DATA_OBSERVATION_SOCIAL_HISTORY),
                        appProxy.upsertMedicalResource(
                                dataSourceId, FHIR_DATA_OBSERVATION_VITAL_SIGNS),
                        appProxy.upsertMedicalResource(dataSourceId, FHIR_DATA_OBSERVATION_LABS));
        revokePermission(appProxy.getPackageName(), WRITE_MEDICAL_DATA);

        checkState(
                insertedMedicalResources.stream()
                        .map(MedicalResource::getType)
                        .collect(toSet())
                        .equals(MEDICAL_RESOURCE_TYPES_LIST));

        return insertedMedicalResources.stream().map(MedicalResource::getId).toList();
    }
}
