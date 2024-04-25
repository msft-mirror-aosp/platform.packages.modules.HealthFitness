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
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.datatypes.MedicalResource.MedicalResourceType;
import android.os.Parcel;


/**
 * Internal representation of {@link MedicalResource}.
 *
 * @hide
 */
public final class MedicalResourceInternal {
    @NonNull private String mId = "";
    @MedicalResourceType private int mType;
    @NonNull private String mDataSourceId = "";
    @NonNull private String mDisplayName = "";

    /** Returns the unique identifier of this data. */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns this object with the identifier. */
    @NonNull
    public MedicalResourceInternal setId(@NonNull String id) {
        requireNonNull(id);
        mId = id;
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

    /** Returns the display name. */
    @NonNull
    public String getDisplayName() {
        return mDisplayName;
    }

    /** Returns this object with the display name. */
    @NonNull
    public MedicalResourceInternal setDisplayName(@NonNull String displayName) {
        requireNonNull(displayName);
        mDisplayName = displayName;
        return this;
    }

    /** Converts this object to an external representation. */
    @NonNull
    public MedicalResource toExternalResource() {
        return new MedicalResource.Builder(getId(), getType(), getDataSourceId(), getDisplayName())
                .build();
    }

    /** Converts to this object from an external representation. */
    @NonNull
    public static MedicalResourceInternal fromExternalResource(@NonNull MedicalResource external) {
        requireNonNull(external);
        return new MedicalResourceInternal()
                .setId(external.getId())
                .setType(external.getType())
                .setDataSourceId(external.getDataSourceId())
                .setDisplayName(external.getDisplayName());
    }

    /**
     * Populates {@code parcel} with the self information, required to reconstructor this object
     * during IPC.
     */
    @NonNull
    public void writeToParcel(@NonNull Parcel parcel) {
        requireNonNull(parcel);
        parcel.writeString(getId());
        parcel.writeInt(getType());
        parcel.writeString(getDataSourceId());
        parcel.writeString(getDisplayName());
    }

    /**
     * Populates this object with the data present in {@code parcel}. Reads should be in the same
     * order as write.
     */
    @NonNull
    public static MedicalResourceInternal readFromParcel(@NonNull Parcel parcel) {
        requireNonNull(parcel);
        return new MedicalResourceInternal()
                .setId(parcel.readString())
                .setType(parcel.readInt())
                .setDataSourceId(parcel.readString())
                .setDisplayName(parcel.readString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalResourceInternal that)) return false;
        return getId().equals(that.getId())
                && getType() == that.getType()
                && getDataSourceId().equals(that.getDataSourceId())
                && getDisplayName().equals(that.getDisplayName());
    }

    @Override
    public int hashCode() {
        return hash(getId(), getType(), getDataSourceId(), getDisplayName());
    }
}
