/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.health.connect.internal.datatypes.utils;

import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;

import static com.android.healthfitness.flags.Flags.personalHealthRecord;

import android.health.connect.MedicalPermissionCategory;
import android.health.connect.datatypes.MedicalResource;
import android.util.SparseIntArray;

import java.util.Objects;

/** @hide */
public final class MedicalResourceTypePermissionCategoryMapper {

    private static SparseIntArray sMedicalResourceTypeHealthPermissionCategoryMap =
            new SparseIntArray();

    private MedicalResourceTypePermissionCategoryMapper() {}

    private static void populateMedicalResourceTypeHealthPermissionCategoryMap() {
        if (!personalHealthRecord()) {
            throw new UnsupportedOperationException(
                    "populateMedicalResourceTypeHealthPermissionCategoryMap is not supported");
        }

        if (sMedicalResourceTypeHealthPermissionCategoryMap.size() != 0) {
            return;
        }

        sMedicalResourceTypeHealthPermissionCategoryMap.put(
                MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MedicalPermissionCategory.IMMUNIZATION);
    }

    /**
     * Returns {@link MedicalPermissionCategory.Type} for the input {@link
     * MedicalResource.MedicalResourceType}.
     */
    @MedicalPermissionCategory.Type
    public static int getMedicalPermissionCategory(
            @MedicalResource.MedicalResourceType int resourceType) {
        populateMedicalResourceTypeHealthPermissionCategoryMap();

        @MedicalPermissionCategory.Type
        Integer resourceCategory =
                sMedicalResourceTypeHealthPermissionCategoryMap.get(resourceType);
        Objects.requireNonNull(
                resourceCategory,
                "Medical Permission Category not found for resource type:" + resourceType);
        return resourceCategory;
    }
}
