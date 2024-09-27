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
import android.annotation.Nullable;
import android.health.connect.datatypes.MedicalResource;
import android.health.connect.internal.ParcelUtils;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A class to represent a read response for {@link HealthConnectManager#readMedicalResources}. */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class ReadMedicalResourcesResponse implements Parcelable {
    @NonNull private final List<MedicalResource> mMedicalResources;
    @Nullable private final String mNextPageToken;

    /**
     * @param medicalResources List of {@link MedicalResource}s.
     * @param nextPageToken The token value of the read result which can be used as input token for
     *     next read request. {@code null} if there are no more pages available.
     */
    public ReadMedicalResourcesResponse(
            @NonNull List<MedicalResource> medicalResources, @Nullable String nextPageToken) {
        requireNonNull(medicalResources);
        mMedicalResources = medicalResources;
        mNextPageToken = nextPageToken;
    }

    private ReadMedicalResourcesResponse(@NonNull Parcel in) {
        requireNonNull(in);
        in = ParcelUtils.getParcelForSharedMemoryIfRequired(in);
        mMedicalResources = new ArrayList<>();
        in.readParcelableList(
                mMedicalResources, MedicalResource.class.getClassLoader(), MedicalResource.class);
        mNextPageToken = in.readString();
    }

    @NonNull
    public static final Creator<ReadMedicalResourcesResponse> CREATOR =
            new Creator<>() {
                @Override
                public ReadMedicalResourcesResponse createFromParcel(Parcel in) {
                    return new ReadMedicalResourcesResponse(in);
                }

                @Override
                public ReadMedicalResourcesResponse[] newArray(int size) {
                    return new ReadMedicalResourcesResponse[size];
                }
            };

    /** Returns list of {@link MedicalResource}s. */
    @NonNull
    public List<MedicalResource> getMedicalResources() {
        return mMedicalResources;
    }

    /**
     * Returns a page token to read the next page of the result. {@code null} if there are no more
     * pages available.
     */
    @Nullable
    public String getNextPageToken() {
        return mNextPageToken;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        requireNonNull(dest);
        ParcelUtils.putToRequiredMemory(dest, flags, this::writeToParcelInternal);
    }

    private void writeToParcelInternal(@NonNull Parcel dest) {
        requireNonNull(dest);
        dest.writeParcelableList(mMedicalResources, 0);
        dest.writeString(mNextPageToken);
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadMedicalResourcesResponse that)) return false;
        return getMedicalResources().equals(that.getMedicalResources())
                && Objects.equals(getNextPageToken(), that.getNextPageToken());
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getMedicalResources(), getNextPageToken());
    }
}
