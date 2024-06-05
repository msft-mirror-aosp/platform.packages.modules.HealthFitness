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
import android.os.Parcel;
import android.os.Parcelable;

/** Represents a create request for {@link HealthConnectManager#createMedicalDataSource}. */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class CreateMedicalDataSourceRequest implements Parcelable {
    @NonNull private final String mFhirBaseUri;
    @NonNull private final String mDisplayName;

    @NonNull
    public static final Creator<CreateMedicalDataSourceRequest> CREATOR =
            new Creator<CreateMedicalDataSourceRequest>() {
                @NonNull
                @Override
                public CreateMedicalDataSourceRequest createFromParcel(@NonNull Parcel in) {
                    return new CreateMedicalDataSourceRequest(in);
                }

                @NonNull
                @Override
                public CreateMedicalDataSourceRequest[] newArray(int size) {
                    return new CreateMedicalDataSourceRequest[size];
                }
            };

    private CreateMedicalDataSourceRequest(
            @NonNull String fhirBaseUri, @NonNull String displayName) {
        requireNonNull(fhirBaseUri);
        requireNonNull(displayName);

        mFhirBaseUri = fhirBaseUri;
        mDisplayName = displayName;
    }

    private CreateMedicalDataSourceRequest(@NonNull Parcel in) {
        requireNonNull(in);
        mFhirBaseUri = requireNonNull(in.readString());
        mDisplayName = requireNonNull(in.readString());
    }

    /** Returns the fhir base uri. */
    @NonNull
    public String getFhirBaseUri() {
        return mFhirBaseUri;
    }

    /** Returns the display name. */
    @NonNull
    public String getDisplayName() {
        return mDisplayName;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mFhirBaseUri);
        dest.writeString(mDisplayName);
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (!(o instanceof CreateMedicalDataSourceRequest that)) return false;
        return getFhirBaseUri().equals(that.getFhirBaseUri())
                && getDisplayName().equals(that.getDisplayName());
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getFhirBaseUri(), getDisplayName());
    }

    /** Returns a string representation of this {@link CreateMedicalDataSourceRequest}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName()).append("{");
        sb.append("fhirBaseUri=").append(getFhirBaseUri());
        sb.append(",displayName=").append(getDisplayName());
        sb.append("}");
        return sb.toString();
    }

    /** Builder class for {@link CreateMedicalDataSourceRequest} */
    public static final class Builder {
        @NonNull private String mFhirBaseUri;
        @NonNull private String mDisplayName;

        /**
         * @param fhirBaseUri The FHIR base URI of the data source. For data coming from a FHIR
         *     server this should be the base URL.
         * @param displayName The display name that describes the data source.
         */
        public Builder(@NonNull String fhirBaseUri, @NonNull String displayName) {
            requireNonNull(fhirBaseUri);
            requireNonNull(displayName);

            mFhirBaseUri = fhirBaseUri;
            mDisplayName = displayName;
        }

        public Builder(@NonNull Builder original) {
            requireNonNull(original);
            mFhirBaseUri = original.mFhirBaseUri;
            mDisplayName = original.mDisplayName;
        }

        public Builder(@NonNull CreateMedicalDataSourceRequest original) {
            requireNonNull(original);
            mFhirBaseUri = original.getFhirBaseUri();
            mDisplayName = original.getDisplayName();
        }

        /**
         * Sets the fhir base URI. For data coming from a FHIR server this should be the base URL.
         */
        @NonNull
        public Builder setFhirBaseUri(@NonNull String fhirBaseUri) {
            requireNonNull(fhirBaseUri);
            mFhirBaseUri = fhirBaseUri;
            return this;
        }

        /** Sets the display name. */
        @NonNull
        public Builder setDisplayName(@NonNull String displayName) {
            requireNonNull(displayName);
            mDisplayName = displayName;
            return this;
        }

        /**
         * Returns a new instance of {@link CreateMedicalDataSourceRequest} with the specified
         * parameters.
         */
        @NonNull
        public CreateMedicalDataSourceRequest build() {
            return new CreateMedicalDataSourceRequest(mFhirBaseUri, mDisplayName);
        }
    }
}
