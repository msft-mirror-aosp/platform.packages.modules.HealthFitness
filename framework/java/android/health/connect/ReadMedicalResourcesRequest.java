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

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.Constants.MAXIMUM_PAGE_SIZE;
import static android.health.connect.Constants.MINIMUM_PAGE_SIZE;
import static android.health.connect.datatypes.MedicalResource.validateMedicalResourceType;
import static android.health.connect.datatypes.validation.ValidationUtils.requireInRange;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.health.connect.datatypes.MedicalResource.MedicalResourceType;
import android.os.Parcel;
import android.os.Parcelable;

/** A class to represent a read request for {@link HealthConnectManager#readMedicalResources}. */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class ReadMedicalResourcesRequest implements Parcelable {
    @MedicalResourceType private final int mMedicalResourceType;
    private final int mPageSize;
    private final long mPageToken;

    /**
     * @param pageSize The maximum number of {@code MedicalResource}s to be returned by the read
     *     operation.
     * @param pageToken The page token to read the current page of the result.
     */
    private ReadMedicalResourcesRequest(
            @MedicalResourceType int medicalResourceType,
            @IntRange(from = MINIMUM_PAGE_SIZE, to = MAXIMUM_PAGE_SIZE) int pageSize,
            long pageToken) {
        validateMedicalResourceType(medicalResourceType);
        requireInRange(pageSize, MINIMUM_PAGE_SIZE, MAXIMUM_PAGE_SIZE, "pageSize");

        mMedicalResourceType = medicalResourceType;
        mPageSize = pageSize;
        mPageToken = pageToken;
    }

    /**
     * Constructs this object with the data present in {@code parcel}. It should be in the same
     * order as {@link ReadMedicalResourcesRequest#writeToParcel}.
     */
    private ReadMedicalResourcesRequest(@NonNull Parcel in) {
        requireNonNull(in);
        mMedicalResourceType = in.readInt();
        mPageSize = in.readInt();
        mPageToken = in.readLong();
    }

    @NonNull
    public static final Creator<ReadMedicalResourcesRequest> CREATOR =
            new Creator<>() {
                @Override
                public ReadMedicalResourcesRequest createFromParcel(Parcel in) {
                    return new ReadMedicalResourcesRequest(in);
                }

                @Override
                public ReadMedicalResourcesRequest[] newArray(int size) {
                    return new ReadMedicalResourcesRequest[size];
                }
            };

    /** Returns the medical resource type. */
    @MedicalResourceType
    public int getMedicalResourceType() {
        return mMedicalResourceType;
    }

    /**
     * Returns maximum number of {@code MedicalResource}s to be returned by the read operation if
     * set, 1000 otherwise.
     */
    @IntRange(from = MINIMUM_PAGE_SIZE, to = MAXIMUM_PAGE_SIZE)
    public int getPageSize() {
        return mPageSize;
    }

    /**
     * Returns page token to read the current page of the result if set, -1 otherwise, which means
     * the first page.
     */
    public long getPageToken() {
        return mPageToken;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Populates a {@link Parcel} with the self information. */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mMedicalResourceType);
        dest.writeInt(mPageSize);
        dest.writeLong(mPageToken);
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadMedicalResourcesRequest that)) return false;
        return getMedicalResourceType() == that.getMedicalResourceType()
                && getPageSize() == that.getPageSize()
                && getPageToken() == that.getPageToken();
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getMedicalResourceType(), getPageSize(), getPageToken());
    }

    /** Returns a string representation of this {@link ReadMedicalResourcesRequest}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{");
        sb.append("medicalResourceType=").append(getMedicalResourceType());
        sb.append(",pageSize=").append(getPageSize());
        sb.append(",pageToken=").append(getPageToken());
        sb.append("}");
        return sb.toString();
    }

    /** Builder class for {@link ReadMedicalResourcesRequest} */
    public static final class Builder {
        @MedicalResourceType private int mMedicalResourceType;
        private int mPageSize = DEFAULT_PAGE_SIZE;
        private long mPageToken = DEFAULT_LONG;

        /**
         * @param medicalResourceType The medical resource type.
         */
        public Builder(@MedicalResourceType int medicalResourceType) {
            validateMedicalResourceType(medicalResourceType);
            mMedicalResourceType = medicalResourceType;
        }

        /**
         * @param original The other {@link ReadMedicalResourcesRequest.Builder} to provide data to
         *     construct this new instance from.
         */
        public Builder(@NonNull Builder original) {
            requireNonNull(original);
            mMedicalResourceType = original.mMedicalResourceType;
            mPageSize = original.mPageSize;
            mPageToken = original.mPageToken;
        }

        /**
         * @param original The other {@link ReadMedicalResourcesRequest} instance to provide data to
         *     construct this new instance from.
         */
        public Builder(@NonNull ReadMedicalResourcesRequest original) {
            requireNonNull(original);
            mMedicalResourceType = original.getMedicalResourceType();
            mPageSize = original.getPageSize();
            mPageToken = original.getPageToken();
        }

        /** Sets the medical resource type. */
        @NonNull
        public ReadMedicalResourcesRequest.Builder setMedicalResourceType(
                @MedicalResourceType int medicalResourceType) {
            validateMedicalResourceType(medicalResourceType);
            mMedicalResourceType = medicalResourceType;
            return this;
        }

        /**
         * Sets the maximum number of {@code MedicalResource}s to be returned by the read operation.
         * The number must be in the range of [1, 5000].
         *
         * <p>If not set, default to 1000.
         */
        @NonNull
        public Builder setPageSize(
                @IntRange(from = MINIMUM_PAGE_SIZE, to = MAXIMUM_PAGE_SIZE) int pageSize) {
            requireInRange(pageSize, MINIMUM_PAGE_SIZE, MAXIMUM_PAGE_SIZE, "pageSize");
            mPageSize = pageSize;
            return this;
        }

        /**
         * Sets page token to read the requested page of the result.
         *
         * <p>If not set, default to -1, which means the first page.
         */
        @NonNull
        public Builder setPageToken(long pageToken) {
            mPageToken = pageToken;
            return this;
        }

        /**
         * Returns a new instance of {@link ReadMedicalResourcesRequest} with the specified
         * parameters.
         */
        @NonNull
        public ReadMedicalResourcesRequest build() {
            return new ReadMedicalResourcesRequest(mMedicalResourceType, mPageSize, mPageToken);
        }
    }
}
