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

import static android.health.connect.datatypes.validation.ValidationUtils.validateIntDefValue;

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

/**
 * Captures the user's medical data. This is the class used for all medical resource types, and the
 * type is specified via {@link MedicalResourceType}.
 *
 * @hide
 */
// TODO (b/335382791) Use FlaggedApi with auto-generated PHR java flag.
public final class MedicalResource {
    /** Unknown medical resource type. */
    public static final int MEDICAL_RESOURCE_TYPE_UNKNOWN = 0;

    /** @hide */
    @IntDef({MEDICAL_RESOURCE_TYPE_UNKNOWN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface MedicalResourceType {}

    @MedicalResourceType private final int mType;

    /**
     * @param type The medical resource type assigned by the Android Health Platform at insertion
     *     time. When {@link MedicalResource} is created before insertion, this takes a sentinel
     *     value, any assigned value will be ignored.
     */
    private MedicalResource(@MedicalResourceType int type) {
        validateIntDefValue(type, VALID_TYPES, MedicalResourceType.class.getSimpleName());

        mType = type;
    }

    /**
     * Returns the medical resource type if set, {@code MEDICAL_RESOURCE_TYPE_UNKNOWN} otherwise.
     */
    @MedicalResourceType
    public int getType() {
        return mType;
    }

    /**
     * Valid set of values for this IntDef. Update this set when add new type or deprecate existing
     * type.
     *
     * @hide
     */
    public static final Set<Integer> VALID_TYPES = Set.of(MEDICAL_RESOURCE_TYPE_UNKNOWN);

    /** Builder class for {@link MedicalResource} */
    public static final class Builder {
        @MedicalResourceType private int mType = MEDICAL_RESOURCE_TYPE_UNKNOWN;

        public Builder() {}

        public Builder(@NonNull MedicalResource original) {
            requireNonNull(original);
            mType = original.getType();
        }

        /**
         * Sets the medical resource type. If not set, default to {@code
         * MEDICAL_RESOURCE_TYPE_UNKNOWN}.
         */
        @NonNull
        public Builder setType(@MedicalResourceType int type) {
            mType = type;
            return this;
        }

        /** Returns a new instance of {@link MedicalResource} with the specified parameters. */
        @NonNull
        public MedicalResource build() {
            return new MedicalResource(mType);
        }
    }
}
