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

package com.android.server.healthconnect.storage.request;

import static com.android.healthfitness.flags.Flags.personalHealthRecord;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.UpsertMedicalResourceRequest;
import android.health.connect.datatypes.FhirResource.FhirResourceType;
import android.health.connect.datatypes.FhirVersion;

import com.android.server.healthconnect.phr.FhirJsonExtractor;

import org.json.JSONException;

import java.util.Objects;
import java.util.UUID;

/**
 * Internal representation of {@link UpsertMedicalResourceRequest}.
 *
 * @hide
 */
public final class UpsertMedicalResourceInternalRequest {
    @Nullable private UUID mUuid;
    @NonNull private String mDataSourceId = "";
    @NonNull private String mData = "";
    @FhirResourceType private int mFhirResourceType;
    @NonNull private String mFhirResourceId = "";
    @NonNull private String mFhirVersion = "";

    /** Returns the unique identifier of this data. */
    @Nullable
    public UUID getUuid() {
        return mUuid;
    }

    /** Returns this object with the identifier. */
    @NonNull
    public UpsertMedicalResourceInternalRequest setUuid(@Nullable UUID uuid) {
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
    public UpsertMedicalResourceInternalRequest setDataSourceId(@NonNull String dataSourceId) {
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
    public UpsertMedicalResourceInternalRequest setData(@NonNull String data) {
        requireNonNull(data);
        mData = data;
        return this;
    }

    /**
     * Returns the FHIR resource type. This is extracted from the "resourceType" field in {@code
     * mData}, and mapped into an {@code IntDef} {@link FhirResourceType}.
     */
    @FhirResourceType
    public int getFhirResourceType() {
        return mFhirResourceType;
    }

    /** Returns this object with the FHIR resource type. */
    public UpsertMedicalResourceInternalRequest setFhirResourceType(
            @FhirResourceType int fhirResourceType) {
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
    public UpsertMedicalResourceInternalRequest setFhirResourceId(@NonNull String fhirResourceId) {
        requireNonNull(fhirResourceId);
        mFhirResourceId = fhirResourceId;
        return this;
    }

    /** Returns the FHIR version as string. */
    @NonNull
    public String getFhirVersion() {
        return mFhirVersion;
    }

    /** Returns this object with the FHIR version string. */
    @NonNull
    public UpsertMedicalResourceInternalRequest setFhirVersion(@NonNull FhirVersion fhirVersion) {
        requireNonNull(fhirVersion);
        mFhirVersion = fhirVersion.toString();
        return this;
    }

    /** Converts to this object from an upsert request. */
    // TODO(b/350010200): Refactor this once we check in the request validator code.
    @NonNull
    public static UpsertMedicalResourceInternalRequest fromUpsertRequest(
            @NonNull UpsertMedicalResourceRequest request) throws JSONException {
        if (!personalHealthRecord()) {
            throw new UnsupportedOperationException(
                    "Convert from UpsertMedicalResourceRequest is not supported");
        }
        requireNonNull(request);
        FhirJsonExtractor extractor = new FhirJsonExtractor(request.getData());
        return new UpsertMedicalResourceInternalRequest()
                .setFhirResourceId(extractor.getFhirResourceId())
                .setFhirResourceType(extractor.getFhirResourceType())
                .setDataSourceId(request.getDataSourceId())
                .setFhirVersion(request.getFhirVersion())
                .setData(request.getData());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpsertMedicalResourceInternalRequest that)) return false;
        return Objects.equals(getUuid(), that.getUuid())
                && getDataSourceId().equals(that.getDataSourceId())
                && getFhirResourceType() == that.getFhirResourceType()
                && getFhirResourceId().equals(that.getFhirResourceId())
                && getFhirVersion().equals(that.getFhirVersion())
                && getData().equals(that.getData());
    }

    @Override
    public int hashCode() {
        return hash(
                getUuid(),
                getDataSourceId(),
                getFhirResourceType(),
                getFhirResourceId(),
                getFhirVersion(),
                getData());
    }
}
