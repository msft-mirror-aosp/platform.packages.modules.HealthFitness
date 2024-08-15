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

package com.android.server.healthconnect.permission;

import static android.health.connect.HealthPermissions.READ_MEDICAL_DATA_IMMUNIZATION;
import static android.health.connect.HealthPermissions.WRITE_MEDICAL_DATA;
import static android.health.connect.datatypes.MedicalResource.MEDICAL_RESOURCE_TYPE_IMMUNIZATION;
import static android.permission.PermissionManager.PERMISSION_GRANTED;
import static android.permission.PermissionManager.PERMISSION_HARD_DENIED;

import static com.android.healthfitness.flags.Flags.FLAG_PERSONAL_HEALTH_RECORD;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.permission.PermissionManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

public class MedicalDataPermissionEnforcerTest {
    @Mock private PermissionManager mPermissionManager;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private AttributionSource mAttributionSource;

    private MedicalDataPermissionEnforcer mMedicalDataPermissionEnforcer;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mAttributionSource = buildAttributionSource();
        mMedicalDataPermissionEnforcer = new MedicalDataPermissionEnforcer(mPermissionManager);
    }

    /** enforceWriteMedicalDataPermission */
    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testEnforceWriteMedicalDataPermission_permissionGranted_doesNotThrow() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_MEDICAL_DATA, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        mMedicalDataPermissionEnforcer.enforceWriteMedicalDataPermission(mAttributionSource);
    }

    @Test(expected = SecurityException.class)
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testEnforceWriteMedicalDataPermission_permissionDenied_throwsException() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_MEDICAL_DATA, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);

        mMedicalDataPermissionEnforcer.enforceWriteMedicalDataPermission(mAttributionSource);
    }

    /** enforceMedicalReadAccessAndGetEnforceSelfRead */
    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testEnforceMedicalReadAccessAndGetEnforceSelfRead_permissionGranted_returnsFalse() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_MEDICAL_DATA_IMMUNIZATION, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        boolean selfRead =
                mMedicalDataPermissionEnforcer.enforceMedicalReadAccessAndGetEnforceSelfRead(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, mAttributionSource);

        assertThat(selfRead).isFalse();
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testEnforceMedicalReadAccessAndGetEnforceSelfRead_onlyWriteGranted_returnsTrue() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_MEDICAL_DATA_IMMUNIZATION, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_MEDICAL_DATA, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        boolean selfRead =
                mMedicalDataPermissionEnforcer.enforceMedicalReadAccessAndGetEnforceSelfRead(
                        MEDICAL_RESOURCE_TYPE_IMMUNIZATION, mAttributionSource);

        assertThat(selfRead).isTrue();
    }

    /** getGrantedMedicalPermissions */
    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetGrantedMedicalPermissions_permissionGranted_returnsPermissions() {
        when(mPermissionManager.checkPermissionForPreflight(
                        READ_MEDICAL_DATA_IMMUNIZATION, mAttributionSource))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForPreflight(WRITE_MEDICAL_DATA, mAttributionSource))
                .thenReturn(PERMISSION_GRANTED);

        Set<String> permissions =
                mMedicalDataPermissionEnforcer.getGrantedMedicalPermissionsForPreflight(
                        mAttributionSource);

        assertThat(permissions).containsExactly(READ_MEDICAL_DATA_IMMUNIZATION, WRITE_MEDICAL_DATA);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetGrantedMedicalPermissions_onePermissionDenied_returnsOnePermission() {
        when(mPermissionManager.checkPermissionForPreflight(
                        READ_MEDICAL_DATA_IMMUNIZATION, mAttributionSource))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForPreflight(WRITE_MEDICAL_DATA, mAttributionSource))
                .thenReturn(PERMISSION_HARD_DENIED);

        Set<String> permissions =
                mMedicalDataPermissionEnforcer.getGrantedMedicalPermissionsForPreflight(
                        mAttributionSource);

        assertThat(permissions).containsExactly(READ_MEDICAL_DATA_IMMUNIZATION);
    }

    @Test
    @EnableFlags(FLAG_PERSONAL_HEALTH_RECORD)
    public void testGetGrantedMedicalPermissions_permissionDenied_returnsEmpty() {
        when(mPermissionManager.checkPermissionForPreflight(
                        anyString(), any(AttributionSource.class)))
                .thenReturn(PERMISSION_HARD_DENIED);

        Set<String> permissions =
                mMedicalDataPermissionEnforcer.getGrantedMedicalPermissionsForPreflight(
                        mAttributionSource);

        assertThat(permissions).isEmpty();
    }

    private static AttributionSource buildAttributionSource() {
        int uid = 123;
        return new AttributionSource.Builder(uid)
                .setPackageName("package")
                .setAttributionTag("tag")
                .build();
    }
}
