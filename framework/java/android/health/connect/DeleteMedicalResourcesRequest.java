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

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Request to delete Medical resources using {@link HealthConnectManager#deleteMedicalResources}.
 */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class DeleteMedicalResourcesRequest implements Parcelable {
    @NonNull private final Set<String> mDataSourceIds;

    private DeleteMedicalResourcesRequest(@NonNull Set<String> dataSourceIds) {
        if (dataSourceIds.isEmpty()) {
            throw new IllegalArgumentException("Data source list is empty");
        }
        mDataSourceIds = dataSourceIds;
    }

    /**
     * Constructs this object with the data present in {@code parcel}. It should be in the same
     * order as {@link DeleteMedicalResourcesRequest#writeToParcel}.
     */
    private DeleteMedicalResourcesRequest(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        ArrayList<String> dataSourceIdList = in.createStringArrayList();
        if (dataSourceIdList.isEmpty()) {
            throw new IllegalArgumentException("Empty datasources in parcel");
        }
        mDataSourceIds = new HashSet<>(dataSourceIdList);
    }

    @NonNull
    public static final Creator<DeleteMedicalResourcesRequest> CREATOR =
            new Creator<>() {
                @Override
                public DeleteMedicalResourcesRequest createFromParcel(Parcel in) {
                    return new DeleteMedicalResourcesRequest(in);
                }

                @Override
                public DeleteMedicalResourcesRequest[] newArray(int size) {
                    return new DeleteMedicalResourcesRequest[size];
                }
            };

    /**
     * Gets the ids for the datasources that are being requested to delete.
     *
     * <p>These ids should core from {@link HealthConnectManager#createMedicalDataSource}, or other
     * {@link HealthConnectManager} datasource methods.
     */
    @NonNull
    public Set<String> getDataSourceIds() {
        return mDataSourceIds;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringList(new ArrayList<>(mDataSourceIds));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeleteMedicalResourcesRequest that)) return false;
        return mDataSourceIds.equals(that.mDataSourceIds);
    }

    @Override
    public int hashCode() {
        return hash(mDataSourceIds);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{");
        sb.append("dataSourceIds=").append(mDataSourceIds);
        sb.append("}");
        return sb.toString();
    }

    /** Builder class for {@link DeleteMedicalResourcesRequest}. */
    public static final class Builder {
        private final Set<String> mDataSourceIds = new HashSet<>();

        /** Constructs a new {@link Builder} with no datasources set. */
        public Builder() {}

        /** Constructs a new {@link Builder} copying all settings from {@code other}. */
        public Builder(@NonNull Builder other) {
            mDataSourceIds.addAll(other.mDataSourceIds);
        }

        /** Add the data source ID to request to delete. */
        @NonNull
        public Builder addDataSourceId(@NonNull String dataSourceId) {
            mDataSourceIds.add(Objects.requireNonNull(dataSourceId));
            return this;
        }

        /** Builds a {@link DeleteMedicalResourcesRequest} from this Builder. */
        @NonNull
        public DeleteMedicalResourcesRequest build() {
            return new DeleteMedicalResourcesRequest(mDataSourceIds);
        }
    }
}
