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

package android.health.connect;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * A class to represent a unique identifier of a medical resource.
 *
 * <p>This class contains a set of properties that together represent a unique identifier of a
 * medical resource.
 */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class MedicalResourceId implements Parcelable {
    @NonNull private final String mDataSourceId;
    // TODO(b/343451409): Maybe create IntDef for FHIR resource types. Taking a string for now.
    @NonNull private final String mFhirResourceType;
    @NonNull private final String mFhirResourceId;

    /**
     * @param dataSourceId The unique identifier of where the data comes from.
     * @param fhirResourceType The FHIR resource type. This is the "resourceType" field from a JSON
     *     representation of FHIR resource data.
     * @param fhirResourceId The FHIR resource ID. This is the "id" field from a JSON representation
     *     of FHIR resource data.
     */
    public MedicalResourceId(
            @NonNull String dataSourceId,
            @NonNull String fhirResourceType,
            @NonNull String fhirResourceId) {
        requireNonNull(dataSourceId);
        requireNonNull(fhirResourceType);
        requireNonNull(fhirResourceId);
        mDataSourceId = dataSourceId;
        mFhirResourceType = fhirResourceType;
        mFhirResourceId = fhirResourceId;
    }

    /**
     * Constructs this object with the data present in {@code parcel}. It should be in the same
     * order as {@link MedicalResourceId#writeToParcel}.
     */
    private MedicalResourceId(@NonNull Parcel in) {
        requireNonNull(in);
        mDataSourceId = requireNonNull(in.readString());
        mFhirResourceType = requireNonNull(in.readString());
        mFhirResourceId = requireNonNull(in.readString());
    }

    @NonNull
    public static final Creator<MedicalResourceId> CREATOR =
            new Creator<>() {
                /**
                 * Reading from the {@link Parcel} should have the same order as {@link
                 * MedicalResourceId#writeToParcel}.
                 */
                @Override
                public MedicalResourceId createFromParcel(Parcel in) {
                    return new MedicalResourceId(in);
                }

                @Override
                public MedicalResourceId[] newArray(int size) {
                    return new MedicalResourceId[size];
                }
            };

    /** Returns the unique identifier of where the data comes from. */
    @NonNull
    public String getDataSourceId() {
        return mDataSourceId;
    }

    /** Returns the FHIR resource type. */
    @NonNull
    public String getFhirResourceType() {
        return mFhirResourceType;
    }

    /** Returns the FHIR resource ID. */
    @NonNull
    public String getFhirResourceId() {
        return mFhirResourceId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Populates a {@link Parcel} with the self information. */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        requireNonNull(dest);
        dest.writeString(getDataSourceId());
        dest.writeString(getFhirResourceType());
        dest.writeString(getFhirResourceId());
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalResourceId that)) return false;
        return getDataSourceId().equals(that.getDataSourceId())
                && getFhirResourceType().equals(that.getFhirResourceType())
                && getFhirResourceId().equals(that.getFhirResourceId());
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getDataSourceId(), getFhirResourceType(), getFhirResourceId());
    }

    /** Returns a string representation of this {@link MedicalResourceId}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{");
        sb.append("dataSourceId=").append(getDataSourceId());
        sb.append(",fhirResourceType=").append(getFhirResourceType());
        sb.append(",fhirResourceId=").append(getFhirResourceId());
        sb.append("}");
        return sb.toString();
    }
}
