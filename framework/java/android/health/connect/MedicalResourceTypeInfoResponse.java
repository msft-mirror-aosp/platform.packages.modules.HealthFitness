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

import static android.health.connect.MedicalPermissionCategory.validateMedicalPermissionCategoryType;
import static android.health.connect.datatypes.MedicalResource.validateMedicalResourceType;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.health.connect.datatypes.MedicalDataSource;
import android.health.connect.datatypes.MedicalResource.MedicalResourceType;
import android.health.connect.internal.ParcelUtils;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A class to hold the following information for a specific {@link MedicalResourceType}, used in the
 * response for {@link HealthConnectManager#queryAllMedicalResourceTypesInfo}:
 *
 * <ul>
 *   <li>The {@link MedicalResourceType}.
 *   <li>{@link MedicalPermissionCategory.Type} for the above {@link MedicalResourceType}.
 *   <li>Contributing {@link MedicalDataSource}s of the above {@link MedicalResourceType}.
 * </ul>
 *
 * @hide
 */
@SystemApi
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class MedicalResourceTypeInfoResponse implements Parcelable {
    @MedicalResourceType private final int mMedicalResourceType;
    @MedicalPermissionCategory.Type private final int mPermissionCategoryType;
    @NonNull private final Set<MedicalDataSource> mContributingDataSources;

    /**
     * @param medicalResourceType The {@link MedicalResourceType}.
     * @param permissionCategoryType The {@link MedicalPermissionCategory.Type} for the {@code
     *     medicalResourceType}.
     * @param contributingDataSources The contributing {@link MedicalDataSource}s of the {@code
     *     medicalResourceType}.
     */
    public MedicalResourceTypeInfoResponse(
            @MedicalResourceType int medicalResourceType,
            @MedicalPermissionCategory.Type int permissionCategoryType,
            @NonNull Set<MedicalDataSource> contributingDataSources) {
        validateMedicalResourceType(medicalResourceType);
        validateMedicalPermissionCategoryType(permissionCategoryType);
        requireNonNull(contributingDataSources);
        mMedicalResourceType = medicalResourceType;
        mPermissionCategoryType = permissionCategoryType;
        mContributingDataSources = contributingDataSources;
    }

    private MedicalResourceTypeInfoResponse(@NonNull Parcel in) {
        requireNonNull(in);
        in = ParcelUtils.getParcelForSharedMemoryIfRequired(in);
        mMedicalResourceType = in.readInt();
        mPermissionCategoryType = in.readInt();
        List<MedicalDataSource> contributingDataSources = new ArrayList<>();
        in.readParcelableList(
                contributingDataSources,
                MedicalDataSource.class.getClassLoader(),
                MedicalDataSource.class);
        mContributingDataSources = Set.copyOf(contributingDataSources);
    }

    @NonNull
    public static final Creator<MedicalResourceTypeInfoResponse> CREATOR =
            new Creator<>() {
                @Override
                public MedicalResourceTypeInfoResponse createFromParcel(Parcel in) {
                    return new MedicalResourceTypeInfoResponse(in);
                }

                @Override
                public MedicalResourceTypeInfoResponse[] newArray(int size) {
                    return new MedicalResourceTypeInfoResponse[size];
                }
            };

    /** Returns the {@link MedicalResourceType}. */
    @MedicalResourceType
    public int getMedicalResourceType() {
        return mMedicalResourceType;
    }

    /** Returns {@link MedicalPermissionCategory.Type} for the {@code mMedicalResourceType}. */
    @MedicalPermissionCategory.Type
    public int getPermissionCategoryType() {
        return mPermissionCategoryType;
    }

    /** Returns contributing {@link MedicalDataSource}s of the {@code mMedicalResourceType}. */
    @NonNull
    public Set<MedicalDataSource> getContributingDataSources() {
        return mContributingDataSources;
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
        dest.writeInt(mMedicalResourceType);
        dest.writeInt(mPermissionCategoryType);
        dest.writeParcelableList(mContributingDataSources.stream().toList(), 0);
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MedicalResourceTypeInfoResponse that)) return false;
        return getMedicalResourceType() == that.getMedicalResourceType()
                && getPermissionCategoryType() == that.getPermissionCategoryType()
                && getContributingDataSources().equals(that.getContributingDataSources());
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(
                getMedicalResourceType(),
                getPermissionCategoryType(),
                getContributingDataSources());
    }
}
