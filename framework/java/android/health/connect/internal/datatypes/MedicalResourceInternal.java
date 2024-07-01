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

package android.health.connect.internal.datatypes;

import static com.android.healthfitness.flags.Flags.personalHealthRecord;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.MedicalResource;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.UUID;

/**
 * Internal representation of {@link MedicalResource}.
 *
 * @hide
 */
public final class MedicalResourceInternal {
    private static final String FHIR_RESOURCE_TYPE_FIELD_NAME = "resourceType";
    private static final String FHIR_RESOURCE_ID_FIELD_NAME = "id";
    @Nullable private UUID mUuid;
    @NonNull private String mDataSourceId = "";
    @NonNull private String mData = "";
    @NonNull private String mFhirResourceType = "";
    @NonNull private String mFhirResourceId = "";

    /** Returns the unique identifier of this data. */
    @Nullable
    public UUID getUuid() {
        return mUuid;
    }

    /** Returns this object with the identifier. */
    @NonNull
    public MedicalResourceInternal setUuid(@Nullable UUID uuid) {
        requireNonNull(uuid);
        mUuid = uuid;
        return this;
    }

    /** Returns The data source ID where the data comes from. */
    @NonNull
    public String getDataSourceId() {
        return mDataSourceId;
    }

    /** Returns this object with the data source ID. */
    @NonNull
    public MedicalResourceInternal setDataSourceId(@NonNull String dataSourceId) {
        requireNonNull(dataSourceId);
        mDataSourceId = dataSourceId;
        return this;
    }

    /** Returns the FHIR resource data in JSON representation. */
    @NonNull
    public String getData() {
        return mData;
    }

    /** Returns this object with the FHIR resource data in JSON representation. */
    @NonNull
    public MedicalResourceInternal setData(@NonNull String data) {
        requireNonNull(data);
        mData = data;
        return this;
    }

    /** Returns the FHIR resource type extracted from the FHIR JSON. */
    @NonNull
    public String getFhirResourceType() {
        return mFhirResourceType;
    }

    /** Returns this object with the FHIR resource type. */
    @NonNull
    public MedicalResourceInternal setFhirResourceType(@NonNull String fhirResourceType) {
        requireNonNull(fhirResourceType);
        mFhirResourceType = fhirResourceType;
        return this;
    }

    /** Returns the FHIR resource id extracted from the FHIR JSON. */
    @NonNull
    public String getFhirResourceId() {
        return mFhirResourceId;
    }

    /** Returns this object with the FHIR resource id. */
    @NonNull
    public MedicalResourceInternal setFhirResourceId(@NonNull String fhirResourceId) {
        requireNonNull(fhirResourceId);
        mFhirResourceId = fhirResourceId;
        return this;
    }

    /** Converts to this object from an upsert request. */
    @NonNull
    public static MedicalResourceInternal fromUpsertRequest(
            @NonNull UpsertMedicalResourceRequest request) throws JSONException {
        if (!personalHealthRecord()) {
            throw new UnsupportedOperationException(
                    "Convert from UpsertMedicalResourceRequest is not supported");
        }
        requireNonNull(request);
        String fhirJson = request.getData();
        JSONObject fhirJsonObj = new JSONObject(fhirJson);
        String resourceType = fhirJsonObj.getString(FHIR_RESOURCE_TYPE_FIELD_NAME);
        String resourceId = fhirJsonObj.getString(FHIR_RESOURCE_ID_FIELD_NAME);
        String dataSourceId = String.valueOf(request.getDataSourceId());
        return new MedicalResourceInternal()
                .setFhirResourceId(resourceId)
                .setFhirResourceType(resourceType)
                .setDataSourceId(dataSourceId)
                .setData(fhirJson);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalResourceInternal that)) return false;
        return Objects.equals(getUuid(), that.getUuid())
                && getDataSourceId().equals(that.getDataSourceId())
                && getFhirResourceType().equals(that.getFhirResourceType())
                && getFhirResourceId().equals(that.getFhirResourceId())
                && getData().equals(that.getData());
    }

    @Override
    public int hashCode() {
        return hash(
                getUuid(),
                getDataSourceId(),
                getFhirResourceType(),
                getFhirResourceId(),
                getData());
    }
}
