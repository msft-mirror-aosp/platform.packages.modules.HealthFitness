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
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Request to read medical data sources using {@link
 * HealthConnectManager#getMedicalDataSources(GetMedicalDataSourcesRequest, Executor,
 * OutcomeReceiver)}.
 *
 * <p>If no package names are set requests all data sources from all packages. Otherwise the request
 * is limited to the requested package names.
 */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class GetMedicalDataSourcesRequest implements Parcelable {

    @NonNull
    public static final Creator<GetMedicalDataSourcesRequest> CREATOR =
            new Creator<>() {
                @Override
                public GetMedicalDataSourcesRequest createFromParcel(Parcel in) {
                    return new GetMedicalDataSourcesRequest(in);
                }

                @Override
                public GetMedicalDataSourcesRequest[] newArray(int size) {
                    return new GetMedicalDataSourcesRequest[size];
                }
            };

    @NonNull private final Set<String> mPackageNames = new ArraySet<>();

    private GetMedicalDataSourcesRequest(@NonNull Set<String> packageNames) {
        Objects.requireNonNull(packageNames);
        mPackageNames.addAll(packageNames);
    }

    /**
     * Constructs this object with the data present in {@code parcel}. It should be in the same
     * order as {@link #writeToParcel}.
     */
    private GetMedicalDataSourcesRequest(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        mPackageNames.addAll(in.createStringArrayList());
    }

    /**
     * Gets the package names for which datasources are being requested. If data sources for all
     * apps are requested, the result will be empty..
     */
    @NonNull
    public Set<String> getPackageNames() {
        return new ArraySet<>(mPackageNames);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringList(new ArrayList<>(mPackageNames));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GetMedicalDataSourcesRequest that)) return false;
        return mPackageNames.equals(that.mPackageNames);
    }

    @Override
    public int hashCode() {
        return hash(mPackageNames);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{");
        sb.append("packageNames=").append(mPackageNames);
        sb.append("}");
        return sb.toString();
    }

    /** Builder for a {@link GetMedicalDataSourcesRequest}. */
    public static final class Builder {
        private final Set<String> mPackageNames = new ArraySet<>();

        public Builder() {}

        /**
         * Add a package name to limit this request to.
         *
         * <p>If the list of package names is empty, medical data sources for all packages will be
         * requested. Otherwise only data sources for the added package names are requested.
         */
        @NonNull
        public Builder addPackageName(@NonNull String packageName) {
            Objects.requireNonNull(packageName);
            mPackageNames.add(packageName);
            return this;
        }

        /** Returns a {@link GetMedicalDataSourcesRequest} as specified by this builder. */
        @NonNull
        public GetMedicalDataSourcesRequest build() {
            return new GetMedicalDataSourcesRequest(mPackageNames);
        }
    }
}
