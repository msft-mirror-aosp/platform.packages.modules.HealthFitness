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

package android.health.connect.datatypes;

import static android.health.connect.datatypes.validation.ValidationUtils.validateIntDefValue;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

/**
 * Captures the FHIR resource data. This is the class used for all FHIR resource types, and the type
 * is specified via {@link FhirResourceType}, which is a subset of the resource list in <a
 * href="https://build.fhir.org/resourcelist.html">the official FHIR website</a>.
 */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class FhirResource implements Parcelable {
    /** Unknown FHIR resource type. */
    public static final int FHIR_RESOURCE_TYPE_UNKNOWN = 0;

    /** FHIR resource type for Immunization. */
    public static final int FHIR_RESOURCE_TYPE_IMMUNIZATION = 1;

    /** @hide */
    @IntDef({FHIR_RESOURCE_TYPE_UNKNOWN, FHIR_RESOURCE_TYPE_IMMUNIZATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FhirResourceType {}

    @FhirResourceType private final int mType;
    @NonNull private final String mId;
    @NonNull private final String mData;

    private FhirResource(@FhirResourceType int type, @NonNull String id, @NonNull String data) {
        validateFhirResourceType(type);
        requireNonNull(id);
        requireNonNull(data);

        mType = type;
        mId = id;
        mData = data;
    }

    /**
     * Constructs this object with the data present in {@code parcel}. It should be in the same
     * order as {@link FhirResource#writeToParcel}.
     */
    private FhirResource(@NonNull Parcel in) {
        requireNonNull(in);
        mType = in.readInt();
        mId = requireNonNull(in.readString());
        mData = requireNonNull(in.readString());
    }

    @NonNull
    public static final Creator<FhirResource> CREATOR =
            new Creator<>() {
                @Override
                public FhirResource createFromParcel(Parcel in) {
                    return new FhirResource(in);
                }

                @Override
                public FhirResource[] newArray(int size) {
                    return new FhirResource[size];
                }
            };

    /**
     * Returns the FHIR resource type. This is extracted from the "resourceType" field in {@code
     * data}.
     */
    @FhirResourceType
    public int getType() {
        return mType;
    }

    /**
     * Returns the FHIR resource ID. This is extracted from the "id" field in {@code data}. This is
     * NOT a unique identifier among all {@link FhirResource}s.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the FHIR resource data in JSON representation. */
    @NonNull
    public String getData() {
        return mData;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Populates a {@link Parcel} with the self information. */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        requireNonNull(dest);
        dest.writeInt(getType());
        dest.writeString(getId());
        dest.writeString(getData());
    }

    /**
     * Valid set of values for this IntDef. Update this set when add new type or deprecate existing
     * type.
     */
    private static final Set<Integer> VALID_TYPES =
            Set.of(FHIR_RESOURCE_TYPE_UNKNOWN, FHIR_RESOURCE_TYPE_IMMUNIZATION);

    /**
     * Validates the provided {@code fhirResourceType} is in the {@link FhirResource#VALID_TYPES}
     * set.
     *
     * <p>Throws {@link IllegalArgumentException} if not.
     *
     * @hide
     */
    public static void validateFhirResourceType(@FhirResourceType int fhirResourceType) {
        validateIntDefValue(fhirResourceType, VALID_TYPES, FhirResourceType.class.getSimpleName());
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FhirResource that)) return false;
        return getType() == that.getType()
                && getId().equals(that.getId())
                && getData().equals(that.getData());
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getType(), getId(), getData());
    }

    /** Returns a string representation of this {@link FhirResource}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{");
        sb.append("type=").append(getType());
        sb.append(",id=").append(getId());
        sb.append(",data=").append(getData());
        sb.append("}");
        return sb.toString();
    }

    /** Builder class for {@link FhirResource} */
    public static final class Builder {
        @FhirResourceType private int mType;
        @NonNull private String mId;
        @NonNull private String mData;

        /**
         * @param type The FHIR resource type extracted from the "resourceType" field in {@code
         *     data}.
         * @param id The FHIR resource ID extracted from the "id" field in {@code data}.
         * @param data The FHIR resource data in JSON representation.
         */
        public Builder(@FhirResourceType int type, @NonNull String id, @NonNull String data) {
            validateFhirResourceType(type);
            requireNonNull(id);
            requireNonNull(data);

            mType = type;
            mId = id;
            mData = data;
        }

        /**
         * @param original The other {@link FhirResource.Builder} to provide data to construct this
         *     new instance from.
         */
        public Builder(@NonNull Builder original) {
            requireNonNull(original);
            mType = original.mType;
            mId = original.mId;
            mData = original.mData;
        }

        /**
         * @param original The other {@link FhirResource} instance to provide data to construct this
         *     new instance from.
         */
        public Builder(@NonNull FhirResource original) {
            requireNonNull(original);
            mType = original.getType();
            mId = original.getId();
            mData = original.getData();
        }

        /**
         * Sets the FHIR resource type. This is extracted from the "resourceType" field in {@code
         * data}.
         */
        @NonNull
        public Builder setType(@FhirResourceType int type) {
            validateFhirResourceType(type);
            mType = type;
            return this;
        }

        /**
         * Sets the FHIR resource ID. This is extracted from the "id" field in {@code data}. This is
         * NOT a unique identifier among all {@link FhirResource}s.
         */
        @NonNull
        public Builder setId(@NonNull String id) {
            requireNonNull(id);
            mId = id;
            return this;
        }

        /** Sets the FHIR resource data in JSON representation. */
        @NonNull
        public Builder setData(@NonNull String data) {
            requireNonNull(data);
            mData = data;
            return this;
        }

        /** Returns a new instance of {@link FhirResource} with the specified parameters. */
        @NonNull
        public FhirResource build() {
            return new FhirResource(mType, mId, mData);
        }
    }
}
