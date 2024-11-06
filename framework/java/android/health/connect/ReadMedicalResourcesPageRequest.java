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

import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.Constants.MAXIMUM_PAGE_SIZE;
import static android.health.connect.Constants.MINIMUM_PAGE_SIZE;
import static android.health.connect.datatypes.validation.ValidationUtils.requireInRange;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.health.connect.aidl.ReadMedicalResourcesRequestParcel;

/**
 * Class to represent a page read request with specified {@code pageToken} for {@link
 * HealthConnectManager#readMedicalResources}.
 *
 * <p>When making a new initial request with new filters, use {@link
 * ReadMedicalResourcesInitialRequest}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * ReadMedicalResourcesInitialRequest initialRequest
 *     = new ReadMedicalResourcesInitialRequest.Builder(...).build();
 * ReadMedicalResourcesResponse response = makeRequest(initialRequest);
 * String pageToken = response.getNextPageToken();
 *
 * while (pageToken != null) {
 *     ReadMedicalResourcesPageRequest pageRequest = new ReadMedicalResourcesPageRequest(pageToken);
 *     response = makeRequest(pageRequest);
 *     pageToken = response.getNextPageToken();
 * }
 * }</pre>
 */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class ReadMedicalResourcesPageRequest extends ReadMedicalResourcesRequest {
    @NonNull private final String mPageToken;

    /**
     * @param pageToken The page token to read the requested page of the result.
     * @param pageSize The maximum number of {@code MedicalResource}s to be returned by the read
     *     operation.
     * @throws IllegalArgumentException if {@code pageSize} is less than 1 or more than 5000.
     * @throws NullPointerException if {@code pageToken} is null.
     */
    private ReadMedicalResourcesPageRequest(
            @NonNull String pageToken,
            @IntRange(from = MINIMUM_PAGE_SIZE, to = MAXIMUM_PAGE_SIZE) int pageSize) {
        super(pageSize);
        requireNonNull(pageToken);
        mPageToken = pageToken;
    }

    /** Returns the page token to read the requested page. */
    @NonNull
    public String getPageToken() {
        return mPageToken;
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadMedicalResourcesPageRequest that)) return false;
        return getPageToken().equals(that.getPageToken()) && getPageSize() == that.getPageSize();
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getPageToken(), getPageSize());
    }

    /** Returns a string representation of this {@link ReadMedicalResourcesPageRequest}. */
    @Override
    public String toString() {
        return this.getClass().getSimpleName()
                + "{"
                + "pageToken="
                + getPageToken()
                + ",pageSize="
                + getPageSize()
                + "}";
    }

    /**
     * Returns an instance of {@link ReadMedicalResourcesRequestParcel} to carry the request.
     *
     * @hide
     */
    public ReadMedicalResourcesRequestParcel toParcel() {
        return new ReadMedicalResourcesRequestParcel(this);
    }

    /** Builder class for {@link ReadMedicalResourcesPageRequest} */
    public static final class Builder {
        @NonNull private String mPageToken;
        private int mPageSize = DEFAULT_PAGE_SIZE;

        /**
         * @param pageToken The page token to read the requested page of the result, from the
         *     previous {@link ReadMedicalResourcesResponse#getNextPageToken()}.
         * @throws IllegalArgumentException if the provided {@code pageToken} is null.
         */
        public Builder(@NonNull String pageToken) {
            requireNonNull(pageToken);
            mPageToken = pageToken;
        }

        /**
         * @param original The other {@link Builder} to provide data to construct this new instance
         *     from.
         */
        public Builder(@NonNull Builder original) {
            mPageToken = original.mPageToken;
            mPageSize = original.mPageSize;
        }

        /**
         * @param original The other {@link ReadMedicalResourcesPageRequest} instance to provide
         *     data to construct this new instance from.
         */
        public Builder(@NonNull ReadMedicalResourcesPageRequest original) {
            mPageToken = original.getPageToken();
            mPageSize = original.getPageSize();
        }

        /**
         * Sets page token to read the requested page of the result, from the previous {@link
         * ReadMedicalResourcesResponse#getNextPageToken()}.
         *
         * @throws NullPointerException if {@code pageToken} is null.
         */
        @NonNull
        public Builder setPageToken(@NonNull String pageToken) {
            requireNonNull(pageToken);
            mPageToken = pageToken;
            return this;
        }

        /**
         * Sets the maximum number of {@code MedicalResource}s to be returned by the read operation.
         *
         * <p>If not set, default to 1000.
         *
         * @throws IllegalArgumentException if the provided {@code pageSize} is less than 1 or more
         *     than 5000.
         */
        @NonNull
        public Builder setPageSize(
                @IntRange(from = MINIMUM_PAGE_SIZE, to = MAXIMUM_PAGE_SIZE) int pageSize) {
            requireInRange(pageSize, MINIMUM_PAGE_SIZE, MAXIMUM_PAGE_SIZE, "pageSize");
            mPageSize = pageSize;
            return this;
        }

        /**
         * Returns a new instance of {@link ReadMedicalResourcesPageRequest} with the specified
         * parameters.
         */
        @NonNull
        public ReadMedicalResourcesPageRequest build() {
            return new ReadMedicalResourcesPageRequest(mPageToken, mPageSize);
        }
    }
}
