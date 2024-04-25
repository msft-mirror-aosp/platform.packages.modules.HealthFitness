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

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

/**
 * Captures the user's medical data. This is the class used for all medical resource types, and the
 * type is specified via {@link MedicalResourceType}.
 *
 * @hide
 */
// TODO (b/335382791) Use FlaggedApi with auto-generated PHR java flag.
public final class MedicalResource {
    /** Unknown medical resource type. */
    public static final int MEDICAL_RESOURCE_TYPE_UNKNOWN = 0;

    /** @hide */
    @IntDef({MEDICAL_RESOURCE_TYPE_UNKNOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MedicalResourceType {}

    @NonNull private final String mId;
    @MedicalResourceType private final int mType;
    @NonNull private final String mDataSourceId;
    @NonNull private final String mDisplayName;

    /**
     * @param id The unique identifier of this data, assigned by the Android Health Platform at
     *     insertion time.
     * @param type The medical resource type assigned by the Android Health Platform at insertion
     *     time.
     * @param dataSourceId Where the data comes from.
     * @param displayName The display name assigned by the Android Health Platform at insertion
     *     time.
     */
    private MedicalResource(
            @NonNull String id,
            @MedicalResourceType int type,
            @NonNull String dataSourceId,
            @NonNull String displayName) {
        validateIntDefValue(type, VALID_TYPES, MedicalResourceType.class.getSimpleName());

        mId = id;
        mType = type;
        mDataSourceId = dataSourceId;
        mDisplayName = displayName;
    }

    /** Returns the unique identifier of this data. */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the medical resource type. */
    @MedicalResourceType
    public int getType() {
        return mType;
    }

    /** Returns The data source ID where the data comes from. */
    @NonNull
    public String getDataSourceId() {
        return mDataSourceId;
    }

    /** Returns the display name. */
    @NonNull
    public String getDisplayName() {
        return mDisplayName;
    }

    /**
     * Valid set of values for this IntDef. Update this set when add new type or deprecate existing
     * type.
     *
     * @hide
     */
    public static final Set<Integer> VALID_TYPES = Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN);

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalResource that)) return false;
        return getId().equals(that.getId())
                && getType() == that.getType()
                && getDataSourceId().equals(that.getDataSourceId())
                && getDisplayName().equals(that.getDisplayName());
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getId(), getType(), getDataSourceId(), getDisplayName());
    }

    /** Returns a string representation of this {@link MedicalResource}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{");
        sb.append("id=").append(getId());
        sb.append(",type=").append(getType());
        sb.append(",dataSourceId=").append(getDataSourceId());
        sb.append(",displayName=").append(getDisplayName());
        sb.append("}");
        return sb.toString();
    }

    /** Builder class for {@link MedicalResource} */
    public static final class Builder {
        @NonNull private String mId;
        @MedicalResourceType private int mType;
        @NonNull private String mDataSourceId;
        @NonNull private String mDisplayName;

        public Builder(
                @NonNull String id,
                @MedicalResourceType int type,
                @NonNull String dataSourceId,
                @NonNull String displayName) {
            requireNonNull(id);
            requireNonNull(dataSourceId);
            requireNonNull(displayName);

            mId = id;
            mType = type;
            mDataSourceId = dataSourceId;
            mDisplayName = displayName;
        }

        public Builder(@NonNull Builder original) {
            requireNonNull(original);
            mId = original.mId;
            mType = original.mType;
            mDataSourceId = original.mDataSourceId;
            mDisplayName = original.mDisplayName;
        }

        public Builder(@NonNull MedicalResource original) {
            requireNonNull(original);
            mId = original.getId();
            mType = original.getType();
            mDataSourceId = original.getDataSourceId();
            mDisplayName = original.getDisplayName();
        }

        /**
         * Sets the unique identifier of this data, assigned by the Android Health Platform at
         * insertion time.
         */
        @NonNull
        public Builder setId(@NonNull String id) {
            requireNonNull(id);
            mId = id;
            return this;
        }

        /**
         * Sets the medical resource type, assigned by the Android Health Platform at insertion
         * time.
         */
        @NonNull
        public Builder setType(@MedicalResourceType int type) {
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

        /** Sets the display name, assigned by the Android Health Platform at insertion time. */
        @NonNull
        public Builder setDisplayName(@NonNull String displayName) {
            requireNonNull(displayName);
            mDisplayName = displayName;
            return this;
        }

        /** Returns a new instance of {@link MedicalResource} with the specified parameters. */
        @NonNull
        public MedicalResource build() {
            return new MedicalResource(mId, mType, mDataSourceId, mDisplayName);
        }
    }
}
