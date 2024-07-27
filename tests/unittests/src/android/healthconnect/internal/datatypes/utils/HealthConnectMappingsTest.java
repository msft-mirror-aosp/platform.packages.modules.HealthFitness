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

package android.healthconnect.internal.datatypes.utils;

import static android.health.connect.Constants.DEFAULT_INT;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_UNKNOWN;
import static android.health.connect.internal.datatypes.utils.DataTypeDescriptors.getAllDataTypeDescriptors;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;

import android.health.connect.HealthDataCategory;
import android.health.connect.HealthPermissionCategory;
import android.health.connect.HealthPermissions;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.DataTypeDescriptor;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.health.connect.internal.datatypes.utils.RecordMapper;
import android.health.connect.internal.datatypes.utils.RecordTypePermissionCategoryMapper;
import android.health.connect.internal.datatypes.utils.RecordTypeRecordCategoryMapper;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.healthfitness.flags.Flags;

import org.junit.Rule;
import org.junit.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@EnableFlags({Flags.FLAG_HEALTH_CONNECT_MAPPINGS})
public class HealthConnectMappingsTest {

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void getAllRecordTypeIdentifiers() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();

        Set<Integer> recordTypeIds = healthConnectMappings.getAllRecordTypeIdentifiers();

        assertThat(recordTypeIds).doesNotContain(RECORD_TYPE_UNKNOWN);
        assertThat(recordTypeIds).containsNoDuplicates();
        assertThat(recordTypeIds).hasSize(getAllDataTypeDescriptors().size());
        // UNKNOWN is not actually a valid record type id. Not removing it from VALID_TYPES to keep
        // the existing implementation intact. The new implementation does not return it.
        assertThat(recordTypeIds)
                .containsExactlyElementsIn(
                        RecordTypeIdentifier.VALID_TYPES.stream()
                                .filter(not(isEqual(RECORD_TYPE_UNKNOWN)))
                                .toList());
    }

    @Test
    public void getHealthReadPermission() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();
        for (DataTypeDescriptor descriptor : getAllDataTypeDescriptors()) {
            assertThat(
                            healthConnectMappings.getHealthReadPermission(
                                    descriptor.getPermissionCategory()))
                    .isEqualTo(descriptor.getReadPermission());
            assertThat(
                            healthConnectMappings.getHealthReadPermission(
                                    descriptor.getPermissionCategory()))
                    .isEqualTo(
                            HealthPermissions.getHealthReadPermission(
                                    descriptor.getPermissionCategory()));
        }
    }

    @Test
    public void getHealthWritePermission() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();
        for (DataTypeDescriptor descriptor : getAllDataTypeDescriptors()) {
            assertWithMessage(descriptor.getRecordClass().getSimpleName())
                    .that(
                            healthConnectMappings.getHealthWritePermission(
                                    descriptor.getPermissionCategory()))
                    .isEqualTo(descriptor.getWritePermission());
            assertWithMessage(descriptor.getRecordClass().getSimpleName())
                    .that(
                            healthConnectMappings.getHealthWritePermission(
                                    descriptor.getPermissionCategory()))
                    .isEqualTo(
                            HealthPermissions.getHealthWritePermission(
                                    descriptor.getPermissionCategory()));
        }
    }

    @Test
    public void isWritePermission() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();
        for (DataTypeDescriptor descriptor : getAllDataTypeDescriptors()) {
            assertWithMessage(descriptor.getWritePermission())
                    .that(healthConnectMappings.isWritePermission(descriptor.getWritePermission()))
                    .isTrue();
            assertWithMessage(descriptor.getWritePermission())
                    .that(healthConnectMappings.isWritePermission(descriptor.getWritePermission()))
                    .isEqualTo(
                            HealthPermissions.isWritePermission(descriptor.getWritePermission()));
            assertWithMessage(descriptor.getReadPermission())
                    .that(healthConnectMappings.isWritePermission(descriptor.getReadPermission()))
                    .isFalse();
            assertWithMessage(descriptor.getReadPermission())
                    .that(healthConnectMappings.isWritePermission(descriptor.getReadPermission()))
                    .isEqualTo(HealthPermissions.isWritePermission(descriptor.getReadPermission()));
        }
    }

    @Test
    public void getHealthDataCategoryForWritePermission() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();
        for (DataTypeDescriptor descriptor : getAllDataTypeDescriptors()) {
            String writePermission = descriptor.getWritePermission();
            String readPermission = descriptor.getReadPermission();

            assertWithMessage(writePermission)
                    .that(
                            healthConnectMappings.getHealthDataCategoryForWritePermission(
                                    writePermission))
                    .isEqualTo(descriptor.getDataCategory());

            assertWithMessage(writePermission)
                    .that(
                            healthConnectMappings.getHealthDataCategoryForWritePermission(
                                    writePermission))
                    .isEqualTo(
                            HealthPermissions.getHealthDataCategoryForWritePermission(
                                    writePermission));
            assertWithMessage(readPermission)
                    .that(
                            healthConnectMappings.getHealthDataCategoryForWritePermission(
                                    readPermission))
                    .isEqualTo(DEFAULT_INT);
        }

        assertThat(healthConnectMappings.getHealthDataCategoryForWritePermission(null))
                .isEqualTo(DEFAULT_INT);
        assertThat(healthConnectMappings.getHealthDataCategoryForWritePermission("foo.bar"))
                .isEqualTo(DEFAULT_INT);
    }

    @EnableFlags(Flags.FLAG_MINDFULNESS)
    @Test
    public void getWriteHealthPermissionsFor() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();

        for (DataTypeDescriptor descriptor : getAllDataTypeDescriptors()) {
            String[] permissions =
                    healthConnectMappings.getWriteHealthPermissionsFor(
                            descriptor.getDataCategory());

            assertThat(permissions).isNotEmpty();
            assertThat(permissions).asList().containsNoDuplicates();
            for (String permission : permissions) {
                assertThat(healthConnectMappings.isWritePermission(permission)).isTrue();
                assertThat(
                                healthConnectMappings.getHealthDataCategoryForWritePermission(
                                        permission))
                        .isEqualTo(descriptor.getDataCategory());
            }
            // The HealthPermissions implementation uses static fields and because WELLNESS is
            // flagged it doesn't respect @EnableFlags and can't be made consistent.
            if (descriptor.getDataCategory() != HealthDataCategory.WELLNESS) {
                assertThat(permissions)
                        .asList()
                        .containsExactlyElementsIn(
                                HealthPermissions.getWriteHealthPermissionsFor(
                                        descriptor.getDataCategory()));
            }
        }

        assertThat(
                        healthConnectMappings.getWriteHealthPermissionsFor(
                                HealthPermissionCategory.UNKNOWN))
                .isEmpty();
        assertThat(healthConnectMappings.getWriteHealthPermissionsFor(100)).isEmpty();
    }

    @Test
    public void getRecordIdToExternalRecordClassMap() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();

        Map<Integer, Class<? extends Record>> map =
                healthConnectMappings.getRecordIdToExternalRecordClassMap();

        assertThat(map).hasSize(getAllDataTypeDescriptors().size());
        assertThat(map.keySet()).isEqualTo(healthConnectMappings.getAllRecordTypeIdentifiers());
        assertThat(map.values()).containsNoDuplicates();
        assertThat(map)
                .containsExactlyEntriesIn(
                        RecordMapper.getInstance().getRecordIdToExternalRecordClassMap());
    }

    @Test
    public void getRecordIdToInternalRecordClassMap() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();

        Map<Integer, Class<? extends RecordInternal<?>>> map =
                healthConnectMappings.getRecordIdToInternalRecordClassMap();

        assertThat(map).hasSize(getAllDataTypeDescriptors().size());
        assertThat(map.keySet()).isEqualTo(healthConnectMappings.getAllRecordTypeIdentifiers());
        assertThat(map.values()).containsNoDuplicates();
        assertThat(map)
                .containsExactlyEntriesIn(
                        RecordMapper.getInstance().getRecordIdToInternalRecordClassMap());
    }

    @Test
    public void getRecordType() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();
        for (var descriptor : getAllDataTypeDescriptors()) {
            assertWithMessage(descriptor.getRecordClass().getSimpleName())
                    .that(healthConnectMappings.getRecordType(descriptor.getRecordClass()))
                    .isEqualTo(descriptor.getRecordTypeIdentifier());
            assertWithMessage(descriptor.getRecordClass().getSimpleName())
                    .that(healthConnectMappings.getRecordType(descriptor.getRecordClass()))
                    .isEqualTo(
                            RecordMapper.getInstance().getRecordType(descriptor.getRecordClass()));
        }
    }

    @Test
    public void hasRecordType() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();
        for (var descriptor : getAllDataTypeDescriptors()) {
            assertWithMessage(descriptor.getRecordClass().getSimpleName())
                    .that(healthConnectMappings.hasRecordType(descriptor.getRecordClass()))
                    .isTrue();
            assertWithMessage(descriptor.getRecordClass().getSimpleName())
                    .that(healthConnectMappings.hasRecordType(descriptor.getRecordClass()))
                    .isEqualTo(
                            RecordMapper.getInstance().hasRecordType(descriptor.getRecordClass()));
        }
    }

    @Test
    public void getHealthPermissionCategoryForRecordType() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();
        for (var descriptor : getAllDataTypeDescriptors()) {
            int permissionCategory =
                    healthConnectMappings.getHealthPermissionCategoryForRecordType(
                            descriptor.getRecordTypeIdentifier());

            assertWithMessage(descriptor.getRecordClass().getSimpleName())
                    .that(permissionCategory)
                    .isEqualTo(descriptor.getPermissionCategory());
            assertWithMessage(descriptor.getRecordClass().getSimpleName())
                    .that(permissionCategory)
                    .isEqualTo(
                            RecordTypePermissionCategoryMapper
                                    .getHealthPermissionCategoryForRecordType(
                                            descriptor.getRecordTypeIdentifier()));
        }
    }

    @Test
    public void getRecordCategoryForRecordType() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();
        for (var descriptor : getAllDataTypeDescriptors()) {
            int dataCategory =
                    healthConnectMappings.getRecordCategoryForRecordType(
                            descriptor.getRecordTypeIdentifier());

            assertThat(dataCategory).isEqualTo(descriptor.getDataCategory());
            assertThat(dataCategory)
                    .isEqualTo(
                            RecordTypeRecordCategoryMapper.getRecordCategoryForRecordType(
                                    descriptor.getRecordTypeIdentifier()));
        }
    }

    @Test
    public void getAllHealthDataCategories() {
        HealthConnectMappings healthConnectMappings = new HealthConnectMappings();

        Set<Integer> categories = healthConnectMappings.getAllHealthDataCategories();

        for (var descriptor : getAllDataTypeDescriptors()) {
            assertThat(categories).contains(descriptor.getDataCategory());
        }
        assertThat(categories)
                .containsExactlyElementsIn(
                        getAllDataTypeDescriptors().stream()
                                .map(DataTypeDescriptor::getDataCategory)
                                .collect(Collectors.toSet()));
    }
}
