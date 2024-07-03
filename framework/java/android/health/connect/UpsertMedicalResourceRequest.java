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
import android.health.connect.datatypes.MedicalDataSource;
import android.os.Parcel;
import android.os.Parcelable;

/** Class used to create requests for {@link HealthConnectManager#upsertMedicalResources}. */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class UpsertMedicalResourceRequest implements Parcelable {
    private final long mDataSourceId;
    @NonNull private final String mData;
    private long mDataSize;

    @NonNull
    public static final Creator<UpsertMedicalResourceRequest> CREATOR =
            new Creator<>() {
                @Override
                public UpsertMedicalResourceRequest createFromParcel(Parcel in) {
                    return new UpsertMedicalResourceRequest(in);
                }

                @Override
                public UpsertMedicalResourceRequest[] newArray(int size) {
                    return new UpsertMedicalResourceRequest[size];
                }
            };

    /**
     * @param dataSourceId The id associated with the existing {@link MedicalDataSource}.
     * @param data The FHIR resource data in JSON representation.
     */
    private UpsertMedicalResourceRequest(long dataSourceId, @NonNull String data) {
        requireNonNull(data);

        mDataSourceId = dataSourceId;
        mData = data;
    }

    private UpsertMedicalResourceRequest(@NonNull Parcel in) {
        requireNonNull(in);
        mDataSize = in.dataSize();
        mDataSourceId = in.readLong();
        mData = requireNonNull(in.readString());
    }

    /**
     * Returns the id of the existing {@link MedicalDataSource}, to represent where the data is
     * coming from.
     */
    public long getDataSourceId() {
        return mDataSourceId;
    }

    /** Returns the FHIR resource data in JSON representation. */
    @NonNull
    public String getData() {
        return mData;
    }

    /**
     * Returns the size of the parcel when the class was created from Parcel.
     *
     * @hide
     */
    public long getDataSize() {
        return mDataSize;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Populates a {@link Parcel} with the self information. */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        requireNonNull(dest);
        dest.writeLong(mDataSourceId);
        dest.writeString(mData);
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getDataSourceId(), getData());
    }

    /** Returns whether an object is equal to the current one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpsertMedicalResourceRequest that)) return false;
        return getDataSourceId() == that.getDataSourceId() && getData().equals(that.getData());
    }

    /** Builder class for {@link UpsertMedicalResourceRequest}. */
    public static final class Builder {
        private long mDataSourceId;
        private String mData;

        /**
         * @param dataSourceId The id associated with the existing {@link MedicalDataSource}.
         * @param data The FHIR resource data in JSON representation.
         */
        public Builder(long dataSourceId, @NonNull String data) {
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
        public Builder setDataSourceId(long dataSourceId) {
            mDataSourceId = dataSourceId;
            return this;
        }

        /**
         * @param data represents the FHIR resource data in JSON format.
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
