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

package com.android.server.healthconnect.permission;

import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.health.connect.HealthPermissions.READ_ACTIVE_CALORIES_BURNED;
import static android.health.connect.HealthPermissions.READ_EXERCISE_ROUTE;
import static android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES;
import static android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND;
import static android.health.connect.HealthPermissions.READ_STEPS;
import static android.health.connect.HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED;
import static android.health.connect.HealthPermissions.WRITE_EXERCISE;
import static android.health.connect.HealthPermissions.WRITE_EXERCISE_ROUTE;
import static android.health.connect.HealthPermissions.WRITE_STEPS;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_ACTIVE_CALORIES_BURNED;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_EXERCISE_SESSION;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.permission.PermissionManager.PERMISSION_GRANTED;
import static android.permission.PermissionManager.PERMISSION_HARD_DENIED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import android.content.AttributionSource;
import android.content.Context;
import android.health.connect.internal.datatypes.ExerciseRouteInternal;
import android.health.connect.internal.datatypes.ExerciseSessionRecordInternal;
import android.permission.PermissionManager;
import android.util.ArrayMap;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DataPermissionEnforcerTest {
    @Mock private PermissionManager mPermissionManager;

    @Mock private Context mContext;

    @Mock private HealthConnectDeviceConfigManager mDeviceConfigManager;

    private AttributionSource mAttributionSource;

    private DataPermissionEnforcer mDataPermissionEnforcer;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        HealthConnectDeviceConfigManager.initializeInstance(mContext);

        mAttributionSource = buildAttributionSource();
        mDataPermissionEnforcer =
                new DataPermissionEnforcer(mPermissionManager, mContext, mDeviceConfigManager);
    }

    /** enforceRecordIdsWritePermissions */
    @Test
    public void testEnforceRecordIdsWritePermissions_permissionGranted_doesNotThrow() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        mDataPermissionEnforcer.enforceRecordIdsWritePermissions(
                List.of(RECORD_TYPE_STEPS), mAttributionSource);
    }

    @Test(expected = SecurityException.class)
    public void testEnforceRecordIdsWritePermissions_permissionDenied_throwsSecurityException() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);

        mDataPermissionEnforcer.enforceRecordIdsWritePermissions(
                List.of(RECORD_TYPE_STEPS), mAttributionSource);
    }

    @Test(expected = SecurityException.class)
    public void testEnforceRecordIdsWritePermissions_onePermissionDenied_throwsSecurityException() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_ACTIVE_CALORIES_BURNED, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);

        mDataPermissionEnforcer.enforceRecordIdsWritePermissions(
                List.of(RECORD_TYPE_STEPS, RECORD_TYPE_ACTIVE_CALORIES_BURNED), mAttributionSource);
    }

    /** enforceRecordIdsReadPermissions */
    @Test
    public void testEnforceRecordIdsReadPermissions_permissionGranted_doesNotThrow() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        mDataPermissionEnforcer.enforceRecordIdsReadPermissions(
                List.of(RECORD_TYPE_STEPS), mAttributionSource);
    }

    @Test(expected = SecurityException.class)
    public void testEnforceRecordIdsReadPermissions_permissionDenied_throwsSecurityException() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);

        mDataPermissionEnforcer.enforceRecordIdsReadPermissions(
                List.of(RECORD_TYPE_STEPS), mAttributionSource);
    }

    @Test(expected = SecurityException.class)
    public void testEnforceRecordIdsReadPermissions_onePermissionDenied_throwsSecurityException() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_ACTIVE_CALORIES_BURNED, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);

        mDataPermissionEnforcer.enforceRecordIdsReadPermissions(
                List.of(RECORD_TYPE_STEPS, RECORD_TYPE_ACTIVE_CALORIES_BURNED), mAttributionSource);
    }

    /** enforceReadAccessAndGetEnforceSelfRead */
    @Test
    public void testEnforceReadAccessAndGetEnforceSelfRead_readPermissionGranted_returnsFalse() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        boolean enforceSelfRead =
                mDataPermissionEnforcer.enforceReadAccessAndGetEnforceSelfRead(
                        RECORD_TYPE_STEPS, mAttributionSource);

        assertThat(enforceSelfRead).isFalse();
    }

    @Test
    public void
            testEnforceReadAccessAndGetEnforceSelfRead_onlyWritePermissionGranted_returnsTrue() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        boolean enforceSelfRead =
                mDataPermissionEnforcer.enforceReadAccessAndGetEnforceSelfRead(
                        RECORD_TYPE_STEPS, mAttributionSource);

        assertThat(enforceSelfRead).isTrue();
    }

    @Test(expected = SecurityException.class)
    public void
            testEnforceReadAccessAndGetEnforceSelfRead_permissionsDenied_throwsSecurityException() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);

        mDataPermissionEnforcer.enforceReadAccessAndGetEnforceSelfRead(
                RECORD_TYPE_STEPS, mAttributionSource);
    }

    @Test
    public void
            testEnforceReadAccessAndGetEnforceSelfReadList_oneWritePermissionGranted_returnsTrue() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_STEPS, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_ACTIVE_CALORIES_BURNED, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        boolean enforceSelfRead =
                mDataPermissionEnforcer.enforceReadAccessAndGetEnforceSelfRead(
                        List.of(RECORD_TYPE_STEPS, RECORD_TYPE_ACTIVE_CALORIES_BURNED),
                        mAttributionSource);

        assertThat(enforceSelfRead).isTrue();
    }

    /** enforceRecordsWritePermissions */
    @Test(expected = SecurityException.class)
    public void
            testEnforceRecordsWritePermissions_onlyMainPermissionGranted_throwsSecurityException() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);
        ExerciseRouteInternal.LocationInternal locationInternal =
                new ExerciseRouteInternal.LocationInternal();
        ExerciseRouteInternal route = new ExerciseRouteInternal(List.of(locationInternal));
        ExerciseSessionRecordInternal record = new ExerciseSessionRecordInternal().setRoute(route);

        mDataPermissionEnforcer.enforceRecordsWritePermissions(List.of(record), mAttributionSource);
    }

    @Test(expected = SecurityException.class)
    public void
            testEnforceRecordsWritePermissions_extraPermissionGranted_throwsSecurityException() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        ExerciseSessionRecordInternal record = new ExerciseSessionRecordInternal();
        mDataPermissionEnforcer.enforceRecordsWritePermissions(List.of(record), mAttributionSource);
    }

    @Test
    public void testEnforceRecordsWritePermissions_allPermissionsGranted_doesNotThrow() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        ExerciseSessionRecordInternal record = new ExerciseSessionRecordInternal();
        mDataPermissionEnforcer.enforceRecordsWritePermissions(List.of(record), mAttributionSource);
    }

    /** enforceAnyOfPermissions */
    @Test
    public void testEnforceAnyOfPermissions_onePermissionsGranted_doesNotThrow() {
        when(mContext.checkCallingPermission(READ_STEPS)).thenReturn(PERMISSION_DENIED);
        when(mContext.checkCallingPermission(WRITE_STEPS)).thenReturn(PERMISSION_GRANTED);

        mDataPermissionEnforcer.enforceAnyOfPermissions(READ_STEPS, WRITE_STEPS);
    }

    @Test(expected = SecurityException.class)
    public void testEnforceAnyOfPermissions_allPermissionsDenied_throwsSecurityException() {
        when(mContext.checkCallingPermission(READ_STEPS)).thenReturn(PERMISSION_DENIED);
        when(mContext.checkCallingPermission(WRITE_STEPS)).thenReturn(PERMISSION_DENIED);

        mDataPermissionEnforcer.enforceAnyOfPermissions(READ_STEPS, WRITE_STEPS);
    }

    /** enforceBackgroundReadRestrictions */
    @Test
    public void testEnforceBackgroundReadRestrictions_permissionGranted_doesNotThrow() {
        when(mDeviceConfigManager.isBackgroundReadFeatureEnabled()).thenReturn(true);

        mDataPermissionEnforcer.enforceBackgroundReadRestrictions(1, 2, "error");
    }

    @Test(expected = SecurityException.class)
    public void testEnforceBackgroundReadRestrictions_permissionDenied_throwsSecurityException() {
        int pid = 1;
        int uid = 2;
        String message = "error";
        when(mDeviceConfigManager.isBackgroundReadFeatureEnabled()).thenReturn(true);
        doThrow(SecurityException.class)
                .when(mContext)
                .enforcePermission(READ_HEALTH_DATA_IN_BACKGROUND, pid, uid, message);

        mDataPermissionEnforcer.enforceBackgroundReadRestrictions(uid, pid, message);
    }

    @Test(expected = SecurityException.class)
    public void testEnforceBackgroundReadRestrictions_featureDisabled_throwsSecurityException() {
        when(mDeviceConfigManager.isBackgroundReadFeatureEnabled()).thenReturn(false);

        mDataPermissionEnforcer.enforceBackgroundReadRestrictions(1, 1, "error");
    }

    /** collectGrantedExtraReadPermissions */
    @Test
    public void testCollectGrantedExtraReadPermissions_permissionsGranted_returnsPermissions() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_EXERCISE_ROUTES, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        Set<String> permissions =
                mDataPermissionEnforcer.collectGrantedExtraReadPermissions(
                        Set.of(RECORD_TYPE_EXERCISE_SESSION), mAttributionSource);

        assertThat(permissions)
                .containsExactly(READ_EXERCISE_ROUTES, READ_EXERCISE_ROUTE, WRITE_EXERCISE_ROUTE);
    }

    @Test
    public void testCollectGrantedExtraReadPermissions_permissionDenied_removesPermission() {
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_EXERCISE_ROUTES, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        READ_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);

        Set<String> permissions =
                mDataPermissionEnforcer.collectGrantedExtraReadPermissions(
                        Set.of(RECORD_TYPE_EXERCISE_SESSION), mAttributionSource);

        assertThat(permissions).containsExactly(READ_EXERCISE_ROUTE);
    }

    /** collectExtraWritePermissionStateMapping */
    @Test
    public void
            testCollectExtraWritePermissionStateMapping_permissionsGranted_permissionsMarkedTrue() {
        ExerciseSessionRecordInternal record = new ExerciseSessionRecordInternal();
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_GRANTED);

        Map<String, Boolean> permissionState =
                mDataPermissionEnforcer.collectExtraWritePermissionStateMapping(
                        List.of(record), mAttributionSource);

        Map<String, Boolean> expected = new ArrayMap<>();
        expected.put(WRITE_EXERCISE_ROUTE, true);
        assertThat(permissionState).containsExactlyEntriesIn(expected);
    }

    @Test
    public void
            testCollectExtraWritePermissionStateMapping_permissionDenied_permissionsMarkedFalse() {
        ExerciseSessionRecordInternal record = new ExerciseSessionRecordInternal();
        when(mPermissionManager.checkPermissionForDataDelivery(
                        WRITE_EXERCISE_ROUTE, mAttributionSource, null))
                .thenReturn(PERMISSION_HARD_DENIED);

        Map<String, Boolean> permissionState =
                mDataPermissionEnforcer.collectExtraWritePermissionStateMapping(
                        List.of(record), mAttributionSource);

        Map<String, Boolean> expected = new ArrayMap<>();
        expected.put(WRITE_EXERCISE_ROUTE, false);
        assertThat(permissionState).containsExactlyEntriesIn(expected);
    }

    private AttributionSource buildAttributionSource() {
        int uid = 123;
        return new AttributionSource.Builder(uid)
                .setPackageName("package")
                .setAttributionTag("tag")
                .build();
    }
}
