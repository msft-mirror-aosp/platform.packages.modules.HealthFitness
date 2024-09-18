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
import android.health.connect.MedicalResourceId;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

/**
 * Captures the user's medical data. This is the class used for all medical resource types, and the
 * type is specified via {@link MedicalResourceType}.
 *
 * <p>The data representation follows the <a href="https://hl7.org/fhir/">Fast Healthcare
 * Interoperability Resources (FHIR)</a> standard.
 */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class MedicalResource implements Parcelable {
    /** Unknown medical resource type. */
    public static final int MEDICAL_RESOURCE_TYPE_UNKNOWN = 0;

    /** Medical resource type to capture the Immunization data. */
    public static final int MEDICAL_RESOURCE_TYPE_IMMUNIZATION = 1;

    /** Medical resource type to capture the AllergyIntolerance data. */
    public static final int MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE = 2;

    /** Medical resource type labelling data as to do with pregnancy. */
    public static final int MEDICAL_RESOURCE_TYPE_PREGNANCY = 3;

    /** Medical resource type labelling data as social history. */
    public static final int MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY = 4;

    /** Medical resource type labelling data as vital signs. */
    public static final int MEDICAL_RESOURCE_TYPE_VITAL_SIGNS = 5;

    /** Medical resource type labelling data as results (Laboratory or pathology). */
    public static final int MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS = 6;

    /**
     * Medical resource type labelling data as problems (clinical condition, problem, diagnosis
     * etc).
     */
    public static final int MEDICAL_RESOURCE_TYPE_PROBLEMS = 7;

    /** Medical resource type labelling data as procedures (actions taken on or for a patient). */
    public static final int MEDICAL_RESOURCE_TYPE_PROCEDURES = 8;

    /** @hide */
    @IntDef({
        MEDICAL_RESOURCE_TYPE_UNKNOWN,
        MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
        MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE,
        MEDICAL_RESOURCE_TYPE_PREGNANCY,
        MEDICAL_RESOURCE_TYPE_PROBLEMS,
        MEDICAL_RESOURCE_TYPE_PROCEDURES,
        MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY,
        MEDICAL_RESOURCE_TYPE_VITAL_SIGNS,
        MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MedicalResourceType {}

    @MedicalResourceType private final int mType;
    @NonNull private final MedicalResourceId mId;
    @NonNull private final String mDataSourceId;
    @NonNull private final FhirVersion mFhirVersion;
    @NonNull private final FhirResource mFhirResource;

    /**
     * @param type The medical resource type assigned by the Android Health Platform at insertion
     *     time.
     * @param dataSourceId Where the data comes from.
     * @param fhirVersion The {@link FhirVersion} object that represents the FHIR version.
     * @param fhirResource The enclosed {@link FhirResource} object.
     */
    private MedicalResource(
            @MedicalResourceType int type,
            @NonNull String dataSourceId,
            @NonNull FhirVersion fhirVersion,
            @NonNull FhirResource fhirResource) {
        requireNonNull(dataSourceId);
        requireNonNull(fhirVersion);
        requireNonNull(fhirResource);
        validateMedicalResourceType(type);

        mType = type;
        mDataSourceId = dataSourceId;
        mFhirVersion = fhirVersion;
        mFhirResource = fhirResource;
        mId = new MedicalResourceId(dataSourceId, fhirResource.getType(), fhirResource.getId());
    }

    /**
     * Constructs this object with the data present in {@code parcel}. It should be in the same
     * order as {@link MedicalResource#writeToParcel}.
     */
    private MedicalResource(@NonNull Parcel in) {
        requireNonNull(in);
        mType = in.readInt();
        validateMedicalResourceType(mType);
        mDataSourceId = requireNonNull(in.readString());
        mFhirVersion =
                requireNonNull(
                        in.readParcelable(FhirVersion.class.getClassLoader(), FhirVersion.class));
        mFhirResource =
                requireNonNull(
                        in.readParcelable(FhirResource.class.getClassLoader(), FhirResource.class));
        mId = new MedicalResourceId(mDataSourceId, mFhirResource.getType(), mFhirResource.getId());
    }

    @NonNull
    public static final Creator<MedicalResource> CREATOR =
            new Creator<>() {
                /**
                 * Reading from the {@link Parcel} should have the same order as {@link
                 * MedicalResource#writeToParcel}.
                 */
                @Override
                public MedicalResource createFromParcel(Parcel in) {
                    return new MedicalResource(in);
                }

                @Override
                public MedicalResource[] newArray(int size) {
                    return new MedicalResource[size];
                }
            };

    /** Returns the medical resource type. */
    @MedicalResourceType
    public int getType() {
        return mType;
    }

    /** Returns the ID of this {@link MedicalResource} as {@link MedicalResourceId}. */
    @NonNull
    public MedicalResourceId getId() {
        return mId;
    }

    /** Returns The data source ID where the data comes from. */
    @NonNull
    public String getDataSourceId() {
        return mDataSourceId;
    }

    /** Returns the {@link FhirVersion} object that represents the FHIR version. */
    @NonNull
    public FhirVersion getFhirVersion() {
        return mFhirVersion;
    }

    /** Returns the enclosed {@link FhirResource} object. */
    @NonNull
    public FhirResource getFhirResource() {
        return mFhirResource;
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
        dest.writeString(getDataSourceId());
        dest.writeParcelable(getFhirVersion(), 0);
        dest.writeParcelable(getFhirResource(), 0);
    }

    /**
     * Valid set of values for this IntDef. Update this set when add new type or deprecate existing
     * type.
     *
     * @hide
     */
    public static final Set<Integer> VALID_TYPES =
            Set.of(
                    MEDICAL_RESOURCE_TYPE_UNKNOWN,
                    MEDICAL_RESOURCE_TYPE_IMMUNIZATION,
                    MEDICAL_RESOURCE_TYPE_ALLERGY_INTOLERANCE,
                    MEDICAL_RESOURCE_TYPE_PREGNANCY,
                    MEDICAL_RESOURCE_TYPE_PROBLEMS,
                    MEDICAL_RESOURCE_TYPE_PROCEDURES,
                    MEDICAL_RESOURCE_TYPE_SOCIAL_HISTORY,
                    MEDICAL_RESOURCE_TYPE_VITAL_SIGNS,
                    MEDICAL_RESOURCE_TYPE_LABORATORY_RESULTS);

    /**
     * Validates the provided {@code medicalResourceType} is in the {@link
     * MedicalResource#VALID_TYPES} set.
     *
     * <p>Throws {@link IllegalArgumentException} if not.
     *
     * @hide
     */
    public static void validateMedicalResourceType(@MedicalResourceType int medicalResourceType) {
        validateIntDefValue(
                medicalResourceType, VALID_TYPES, MedicalResourceType.class.getSimpleName());
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalResource that)) return false;
        return getType() == that.getType()
                && getDataSourceId().equals(that.getDataSourceId())
                && getFhirVersion().equals(that.getFhirVersion())
                && getFhirResource().equals(that.getFhirResource());
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getType(), getDataSourceId(), getFhirVersion(), getFhirResource());
    }

    /** Returns a string representation of this {@link MedicalResource}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{");
        sb.append("type=").append(getType());
        sb.append(",dataSourceId=").append(getDataSourceId());
        sb.append(",fhirVersion=").append(getFhirVersion());
        sb.append(",fhirResource=").append(getFhirResource());
        sb.append("}");
        return sb.toString();
    }

    /** Builder class for {@link MedicalResource} */
    public static final class Builder {
        @MedicalResourceType private int mType;
        @NonNull private String mDataSourceId;
        @NonNull private FhirVersion mFhirVersion;
        @NonNull private FhirResource mFhirResource;

        /**
         * @param type The medical resource type assigned by the Android Health Platform at
         *     insertion time.
         * @param dataSourceId Where the data comes from.
         * @param fhirVersion The enclosed {@link FhirVersion} object.
         * @param fhirResource The enclosed {@link FhirResource} object.
         */
        public Builder(
                @MedicalResourceType int type,
                @NonNull String dataSourceId,
                @NonNull FhirVersion fhirVersion,
                @NonNull FhirResource fhirResource) {
            requireNonNull(dataSourceId);
            requireNonNull(fhirVersion);
            requireNonNull(fhirResource);
            validateMedicalResourceType(type);

            mType = type;
            mDataSourceId = dataSourceId;
            mFhirVersion = fhirVersion;
            mFhirResource = fhirResource;
        }

        /**
         * @param original The other {@link MedicalResource.Builder} to provide data to construct
         *     this new instance from.
         */
        public Builder(@NonNull Builder original) {
            requireNonNull(original);
            mType = original.mType;
            mDataSourceId = original.mDataSourceId;
            mFhirVersion = original.mFhirVersion;
            mFhirResource = original.mFhirResource;
        }

        /**
         * @param original The other {@link MedicalResource} instance to provide data to construct
         *     this new instance from.
         */
        public Builder(@NonNull MedicalResource original) {
            requireNonNull(original);
            mType = original.getType();
            mDataSourceId = original.getDataSourceId();
            mFhirVersion = original.getFhirVersion();
            mFhirResource = original.getFhirResource();
        }

        /**
         * Sets the medical resource type, assigned by the Android Health Platform at insertion
         * time.
         */
        @NonNull
        public Builder setType(@MedicalResourceType int type) {
            validateMedicalResourceType(type);
            mType = type;
            return this;
        }

        /** Sets the data source ID where the data comes from. */
        @NonNull
        public Builder setDataSourceId(@NonNull String dataSourceId) {
            requireNonNull(dataSourceId);
            mDataSourceId = dataSourceId;
            return this;
        }

        /** Sets the {@link FhirVersion} object that represents the FHIR version. */
        @NonNull
        public Builder setFhirVersion(@NonNull FhirVersion fhirVersion) {
            requireNonNull(fhirVersion);
            mFhirVersion = fhirVersion;
            return this;
        }

        /** Sets the enclosed {@link FhirResource} object */
        @NonNull
        public Builder setFhirResource(@NonNull FhirResource fhirResource) {
            requireNonNull(fhirResource);
            mFhirResource = fhirResource;
            return this;
        }

        /** Returns a new instance of {@link MedicalResource} with the specified parameters. */
        @NonNull
        public MedicalResource build() {
            return new MedicalResource(mType, mDataSourceId, mFhirVersion, mFhirResource);
        }
    }
}
