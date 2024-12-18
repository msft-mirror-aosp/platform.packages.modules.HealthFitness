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

package com.android.server.healthconnect.phr;

import android.annotation.Nullable;
import android.health.connect.ReadMedicalResourcesResponse;
import android.health.connect.datatypes.MedicalResource;

import java.util.List;
import java.util.Objects;

/**
 * Internal representation of {@link ReadMedicalResourcesResponse}.
 *
 * @hide
 */
public final class ReadMedicalResourcesInternalResponse {
    @Nullable String mPageToken;
    List<MedicalResource> mMedicalResources;

    public ReadMedicalResourcesInternalResponse(
            List<MedicalResource> medicalResources, @Nullable String pageToken) {
        this.mMedicalResources = medicalResources;
        this.mPageToken = pageToken;
    }

    /** Returns the {@code mPageToken}. */
    @Nullable
    public String getPageToken() {
        return mPageToken;
    }

    /** Returns the list of {@link MedicalResource}s. */
    public List<MedicalResource> getMedicalResources() {
        return mMedicalResources;
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadMedicalResourcesInternalResponse that)) return false;
        return Objects.equals(mPageToken, that.mPageToken)
                && Objects.equals(mMedicalResources, that.mMedicalResources);
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return Objects.hash(mPageToken, mMedicalResources);
    }
}
