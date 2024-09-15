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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.SystemApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Represents the permission category of a {@link android.health.connect.datatypes.MedicalResource}.
 * A {@link android.health.connect.datatypes.MedicalResource} can only belong to one and only one
 * {@link MedicalPermissionCategory}.
 *
 * @hide
 */
@FlaggedApi(FLAG_PERSONAL_HEALTH_RECORD)
@SystemApi
public final class MedicalPermissionCategory {
    /** Unknown medical permission category */
    public static final int UNKNOWN = 0;

    /** Permission category for all medical data */
    public static final int ALL_MEDICAL_DATA = 1;

    /** Permission category for immunization */
    public static final int IMMUNIZATION = 2;

    private MedicalPermissionCategory() {}

    /** @hide */
    @IntDef({UNKNOWN, ALL_MEDICAL_DATA, IMMUNIZATION})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {}
}
