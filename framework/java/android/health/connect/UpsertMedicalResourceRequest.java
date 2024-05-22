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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

/**
 * Class used to create requests for {@link HealthConnectManager#insertMedicalResources} and {@link
 * HealthConnectManager#updateMedicalResources}.
 *
 * @hide
 */
public final class UpsertMedicalResourceRequest {
    @NonNull private final String mDataSourceId;
    @NonNull private final String mData;

    /**
     * @param dataSourceId The id associated with the existing {@link MedicalDataSource}.
     * @param data The FHIR resource data in JSON representation.
     */
    private UpsertMedicalResourceRequest(@NonNull String dataSourceId, @NonNull String data) {
        requireNonNull(dataSourceId);
        requireNonNull(data);

        mDataSourceId = dataSourceId;
        mData = data;
    }

    /**
     * Returns the id of the existing {@link MedicalDataSource}, to represent where the data is
     * coming from.
     */
    @NonNull
    public String getDataSourceId() {
        return mDataSourceId;
    }

    /** Returns the FHIR resource data in JSON representation. */
    @NonNull
    public String getData() {
        return mData;
    }

    public static final class Builder {
        @NonNull private String mDataSourceId;
        @NonNull private String mData;

        /**
         * @param dataSourceId The id associated with the existing {@link MedicalDataSource}.
         * @param data The FHIR resource data in JSON representation.
         */
        public Builder(@NonNull String dataSourceId, @NonNull String data) {
            requireNonNull(dataSourceId);
            requireNonNull(data);

            mDataSourceId = dataSourceId;
            mData = data;
        }

        public Builder(@NonNull Builder original) {
            requireNonNull(original);
            mDataSourceId = original.mDataSourceId;
            mData = original.mData;
        }

        public Builder(@NonNull UpsertMedicalResourceRequest original) {
            requireNonNull(original);
            mDataSourceId = original.getDataSourceId();
            mData = original.getData();
        }

        /**
         * @param dataSourceId The id associated with the existing {@link MedicalDataSource}.
         */
        @NonNull
        public Builder setDataSourceId(@NonNull String dataSourceId) {
            requireNonNull(dataSourceId);
            mDataSourceId = dataSourceId;
            return this;
        }

        /**
         * @param data The FHIR resource data in JSON representation.
         */
        @NonNull
        public Builder setData(@NonNull String data) {
            requireNonNull(data);
            mData = data;
            return this;
        }

        /** Returns the Object of {@link UpsertMedicalResourceRequest}. */
        @NonNull
        public UpsertMedicalResourceRequest build() {
            return new UpsertMedicalResourceRequest(mDataSourceId, mData);
        }
    }
}
