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

/** @hide */
public final class MedicalResourceTypePermissionCategoryMapper {

    private static final SparseIntArray sMedicalResourceTypeToPermissionCategoryMap =
            new SparseIntArray();
    private static final SparseIntArray sMedicalResourcePermissionCategoryToTypeMap =
            new SparseIntArray();

    private MedicalResourceTypePermissionCategoryMapper() {}

    private static void populateMedicalResourceTypeToPermissionCategoryMap() {
        if (!personalHealthRecord()) {
            throw new UnsupportedOperationException(
                    "populateMedicalResourceTypeToPermissionCategoryMap is not supported");
        }

        if (sMedicalResourceTypeToPermissionCategoryMap.size() != 0) {
            return;
        }

        sMedicalResourceTypeToPermissionCategoryMap.put(
                MEDICAL_RESOURCE_TYPE_IMMUNIZATION, MedicalPermissionCategory.IMMUNIZATION);
    }

    private static void populateMedicalResourcePermissionCategoryToTypeMap() {
        if (sMedicalResourcePermissionCategoryToTypeMap.size() != 0) {
            return;
        }

        populateMedicalResourceTypeToPermissionCategoryMap();
        for (int i = 0; i < sMedicalResourceTypeToPermissionCategoryMap.size(); i++) {
            sMedicalResourcePermissionCategoryToTypeMap.put(
                    sMedicalResourceTypeToPermissionCategoryMap.valueAt(i),
                    sMedicalResourceTypeToPermissionCategoryMap.keyAt(i));
        }
    }

    /**
     * Returns {@link MedicalPermissionCategory.Type} for the input {@link
     * MedicalResource.MedicalResourceType}.
     */
    @MedicalPermissionCategory.Type
    public static int getMedicalPermissionCategory(
            @MedicalResource.MedicalResourceType int resourceType) {
        populateMedicalResourceTypeToPermissionCategoryMap();

        int idx = sMedicalResourceTypeToPermissionCategoryMap.indexOfKey(resourceType);
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "Medical Permission Category not found for the Medical Resource Type:"
                            + resourceType);
        }

        return sMedicalResourceTypeToPermissionCategoryMap.valueAt(idx);
    }

    /**
     * Returns {@link MedicalResource.MedicalResourceType} for the input {@link
     * MedicalPermissionCategory.Type}.
     */
    @MedicalResource.MedicalResourceType
    public static int getMedicalResourceType(
            @MedicalPermissionCategory.Type int permissionCategory) {
        populateMedicalResourcePermissionCategoryToTypeMap();

        int idx = sMedicalResourcePermissionCategoryToTypeMap.indexOfKey(permissionCategory);
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "Medical Resource Type not found for the Medical Permission Category:"
                            + permissionCategory);
        }

        return sMedicalResourcePermissionCategoryToTypeMap.valueAt(idx);
    }
}
