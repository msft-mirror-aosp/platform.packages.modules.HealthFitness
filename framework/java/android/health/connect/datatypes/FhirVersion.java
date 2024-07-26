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

package android.health.connect.datatypes;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the FHIR version. This is designed according to <a
 * href="https://build.fhir.org/versions.html#versions">the official FHIR versions</a>, while the
 * "label" is not supported for now, which represents a 'working' version.
 */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
public final class FhirVersion implements Parcelable {
    private final int mMajor;
    private final int mMinor;
    private final int mPatch;

    private static final String VERSION_REGEX = "(\\d+)\\.(\\d+)\\.(\\d+)";

    private FhirVersion(int major, int minor, int patch) {
        mMajor = major;
        mMinor = minor;
        mPatch = patch;
    }

    /**
     * Constructs this object with the data present in {@code parcel}. It should be in the same
     * order as {@link FhirVersion#writeToParcel}.
     */
    private FhirVersion(@NonNull Parcel in) {
        requireNonNull(in);
        mMajor = in.readInt();
        mMinor = in.readInt();
        mPatch = in.readInt();
    }

    @NonNull
    public static final Creator<FhirVersion> CREATOR =
            new Creator<>() {
                @Override
                public FhirVersion createFromParcel(Parcel in) {
                    return new FhirVersion(in);
                }

                @Override
                public FhirVersion[] newArray(int size) {
                    return new FhirVersion[size];
                }
            };

    /**
     * Creates a {@link FhirVersion} object with the version of string format.
     *
     * <p>The format should look like "4.0.1" which contains 3 numbers - major, minor and patch,
     * separated by ".". This aligns with <a
     * href="https://build.fhir.org/versions.html#versions">the official FHIR versions</a>. Note
     * that the "label" is not supported for now, which represents a 'working' version.
     */
    @NonNull
    public static FhirVersion parseFhirVersion(@NonNull String fhirVersionString) {
        requireNonNull(fhirVersionString);
        Pattern pattern = Pattern.compile(VERSION_REGEX);
        Matcher matcher = pattern.matcher(fhirVersionString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid FHIR version string: " + fhirVersionString);
        }
        return new FhirVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    /** Returns the major version. */
    public int getMajor() {
        return mMajor;
    }

    /** Returns the minor version. */
    public int getMinor() {
        return mMinor;
    }

    /** Returns the patch version. */
    public int getPatch() {
        return mPatch;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /** Populates a {@link Parcel} with the self information. */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        requireNonNull(dest);
        dest.writeInt(getMajor());
        dest.writeInt(getMinor());
        dest.writeInt(getPatch());
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FhirVersion that)) return false;
        return getMajor() == that.getMajor()
                && getMinor() == that.getMinor()
                && getPatch() == that.getPatch();
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getMajor(), getMinor(), getPatch());
    }

    /** Returns the string representation of the FHIR version. */
    public String toString() {
        return String.format("%d.%d.%d", mMajor, mMinor, mPatch);
    }
}
