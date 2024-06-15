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


import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.datatypes.MedicalResource.MedicalResourceType;

import java.util.Objects;
import java.util.UUID;

/**
 * Internal representation of {@link MedicalResource}.
 *
 * @hide
 */
public final class MedicalResourceInternal {
    @Nullable private UUID mUuid;
    @MedicalResourceType private int mType;
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

    /** Returns the medical resource type. */
    @MedicalResourceType
    public int getType() {
        return mType;
    }

    /** Returns this object with the type. */
    @NonNull
    public MedicalResourceInternal setType(@MedicalResourceType int type) {
        mType = type;
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

    /** Converts this object to an external representation. */
    @NonNull
    @SuppressWarnings("FlaggedApi") // this class is internal only
    public MedicalResource toExternalResource() {
        return new MedicalResource.Builder(
                        requireNonNull(getUuid()).toString(),
                        getType(),
                        getDataSourceId(),
                        getData())
                .build();
    }

    /** Converts to this object from an external representation. */
    @NonNull
    @SuppressWarnings("FlaggedApi") // this class is internal only
    public static MedicalResourceInternal fromExternalResource(@NonNull MedicalResource external) {
        requireNonNull(external);
        return new MedicalResourceInternal()
                .setUuid(UUID.fromString(external.getId()))
                .setType(external.getType())
                .setDataSourceId(external.getDataSourceId())
                .setData(external.getData());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalResourceInternal that)) return false;
        return Objects.equals(getUuid(), that.getUuid())
                && getType() == that.getType()
                && getDataSourceId().equals(that.getDataSourceId())
                && getData().equals(that.getData());
    }

    @Override
    public int hashCode() {
        return hash(getUuid(), getType(), getDataSourceId(), getData());
    }
}
