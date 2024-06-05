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
import static android.health.connect.datatypes.validation.ValidationUtils.requireInRange;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * A class to represent a read request for {@link HealthConnectManager#readMedicalResources}.
 *
 * @hide
 */
public class ReadMedicalResourcesRequest implements Parcelable {
    private final int mPageSize;
    private final long mPageToken;

    /**
     * @param pageSize The maximum number of {@code MedicalResource}s to be returned by the read
     *     operation.
     * @param pageToken The page token to read the current page of the result.
     */
    private ReadMedicalResourcesRequest(
            @IntRange(from = MINIMUM_PAGE_SIZE, to = MAXIMUM_PAGE_SIZE) int pageSize,
            long pageToken) {
        requireInRange(pageSize, MINIMUM_PAGE_SIZE, MAXIMUM_PAGE_SIZE, "pageSize");

        mPageSize = pageSize;
        mPageToken = pageToken;
    }

    @NonNull
    public static final Creator<ReadMedicalResourcesRequest> CREATOR =
            new Creator<>() {
                @Override
                public ReadMedicalResourcesRequest createFromParcel(Parcel in) {
                    return new ReadMedicalResourcesRequest(in.readInt(), in.readLong());
                }

                @Override
                public ReadMedicalResourcesRequest[] newArray(int size) {
                    return new ReadMedicalResourcesRequest[size];
                }
            };

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

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mPageSize);
        dest.writeLong(mPageToken);
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadMedicalResourcesRequest that)) return false;
        return getPageSize() == that.getPageSize() && getPageToken() == that.getPageToken();
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getPageSize(), getPageToken());
    }

    /** Builder class for {@link ReadMedicalResourcesRequest} */
    public static final class Builder {
        private int mPageSize = DEFAULT_PAGE_SIZE;
        private long mPageToken = DEFAULT_LONG;

        public Builder() {}

        public Builder(@NonNull Builder original) {
            requireNonNull(original);
            mPageSize = original.mPageSize;
            mPageToken = original.mPageToken;
        }

        public Builder(@NonNull ReadMedicalResourcesRequest original) {
            requireNonNull(original);
            mPageSize = original.getPageSize();
            mPageToken = original.getPageToken();
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
            return new ReadMedicalResourcesRequest(mPageSize, mPageToken);
        }
    }
}
