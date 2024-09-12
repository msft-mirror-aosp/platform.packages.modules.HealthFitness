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

package com.android.server.healthconnect.storage;

import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_DISPLAY_NAME;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_FHIR_BASE_URI;
import static android.healthconnect.cts.utils.PhrDataFactory.DATA_SOURCE_PACKAGE_NAME;

import android.content.Context;
import android.health.connect.CreateMedicalDataSourceRequest;
import android.health.connect.datatypes.FhirResource;
import android.health.connect.datatypes.FhirVersion;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource;
import android.net.Uri;

import com.android.server.healthconnect.storage.datatypehelpers.MedicalDataSourceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.MedicalResourceHelper;
import com.android.server.healthconnect.storage.request.UpsertMedicalResourceInternalRequest;

import java.util.List;

public class PhrTestUtils {

    private final MedicalDataSourceHelper mMedicalDataSourceHelper;
    private final MedicalResourceHelper mMedicalResourceHelper;
    private final Context mContext;

    public PhrTestUtils(
            Context context,
            MedicalResourceHelper medicalResourceHelper,
            MedicalDataSourceHelper medicalDataSourceHelper) {
        mContext = context;
        mMedicalResourceHelper = medicalResourceHelper;
        mMedicalDataSourceHelper = medicalDataSourceHelper;
    }

    /**
     * Upsert a {@link MedicalResource} using the given {@link MedicalResourceCreator} and the
     * {@code dataSourceId}.
     */
    public MedicalResource upsertResource(MedicalResourceCreator creator, String dataSourceId) {
        MedicalResource medicalResource = creator.create(dataSourceId);
        return mMedicalResourceHelper
                .upsertMedicalResources(
                        DATA_SOURCE_PACKAGE_NAME, List.of(makeUpsertRequest(medicalResource)))
                .get(0);
    }

    /**
     * Upsert a {@link MedicalResource} using the given {@link MedicalResourceCreator} and the
     * {@link MedicalDataSource}.
     */
    public MedicalResource upsertResource(
            MedicalResourceCreator creator, MedicalDataSource dataSource) {
        MedicalResource medicalResource = creator.create(dataSource.getId());
        return mMedicalResourceHelper
                .upsertMedicalResources(
                        dataSource.getPackageName(), List.of(makeUpsertRequest(medicalResource)))
                .get(0);
    }

    /**
     * Upsert {@link MedicalResource}s using the given {@link MedicalResourcesCreator}, the {@code
     * numOfResources} and {@code dataSourceId}.
     */
    public List<MedicalResource> upsertResources(
            MedicalResourcesCreator creator, int numOfResources, String dataSourceId) {
        List<MedicalResource> medicalResources = creator.create(numOfResources, dataSourceId);
        return mMedicalResourceHelper.upsertMedicalResources(
                DATA_SOURCE_PACKAGE_NAME,
                medicalResources.stream().map(PhrTestUtils::makeUpsertRequest).toList());
    }

    /** Returns a request to upsert the given {@link MedicalResource}. */
    public static UpsertMedicalResourceInternalRequest makeUpsertRequest(MedicalResource resource) {
        return makeUpsertRequest(
                resource.getFhirResource(),
                resource.getType(),
                resource.getFhirVersion(),
                resource.getDataSourceId());
    }

    /**
     * Returns a request to upsert the given {@link FhirResource}, along with required source
     * information.
     */
    public static UpsertMedicalResourceInternalRequest makeUpsertRequest(
            FhirResource resource,
            int medicalResourceType,
            FhirVersion fhirVersion,
            String datasourceId) {
        return new UpsertMedicalResourceInternalRequest()
                .setMedicalResourceType(medicalResourceType)
                .setFhirResourceId(resource.getId())
                .setFhirResourceType(resource.getType())
                .setFhirVersion(fhirVersion)
                .setData(resource.getData())
                .setDataSourceId(datasourceId);
    }

    /**
     * Insert and return a {@link MedicalDataSource} where the display name, and URI will contain
     * the given name.
     */
    public MedicalDataSource insertMedicalDataSource(String name, String packageName) {
        Uri uri = Uri.parse(String.format("%s/%s", DATA_SOURCE_FHIR_BASE_URI, name));
        String displayName = String.format("%s %s", DATA_SOURCE_DISPLAY_NAME, name);

        CreateMedicalDataSourceRequest createMedicalDataSourceRequest =
                new CreateMedicalDataSourceRequest.Builder(uri, displayName).build();
        return mMedicalDataSourceHelper.createMedicalDataSource(
                mContext, createMedicalDataSourceRequest, packageName);
    }

    /** Interface for a {@link MedicalResource} creator. */
    public interface MedicalResourceCreator {
        /** Creates a {@link MedicalResource} using the given {@code dataSourceId}. */
        MedicalResource create(String dataSourceId);
    }

    /** Interface for multiple {@link MedicalResource}s creator. */
    public interface MedicalResourcesCreator {
        /**
         * Creates multiple {@link MedicalResource}s based on the {@code num} and the given {@code
         * dataSourceId}.
         */
        List<MedicalResource> create(int num, String dataSourceId);
    }
}
