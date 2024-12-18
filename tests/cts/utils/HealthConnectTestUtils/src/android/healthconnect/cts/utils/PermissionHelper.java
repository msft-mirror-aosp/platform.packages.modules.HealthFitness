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

package android.healthconnect.cts.utils;

import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.content.pm.PackageManager.GET_PERMISSIONS;
import static android.health.connect.HealthPermissions.MANAGE_HEALTH_PERMISSIONS;
import static android.healthconnect.cts.utils.TestUtils.getHealthConnectManager;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.base.Preconditions.checkArgument;

import android.annotation.SuppressLint;
import android.app.UiAutomation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;
import com.android.compatibility.common.util.ThrowingSupplier;

import com.google.common.collect.Sets;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class PermissionHelper {

    /** Copy of hidden {@link android.health.connect.HealthPermissions#READ_EXERCISE_ROUTE}. */
    public static final String READ_EXERCISE_ROUTE_PERMISSION =
            "android.permission.health.READ_EXERCISE_ROUTE";

    /** Returns valid Health permissions declared in the Manifest of the given package. */
    public static List<String> getDeclaredHealthPermissions(String packageName) {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo packageInfo = getAppPackageInfo(context, packageName);
        String[] requestedPermissions = packageInfo.requestedPermissions;

        if (requestedPermissions == null) {
            return List.of();
        }

        return Arrays.stream(requestedPermissions)
                .filter(permission -> HealthConnectManager.isHealthPermission(context, permission))
                .toList();
    }

    /** Returns all Health permissions that are granted to the specified package. */
    public static List<String> getGrantedHealthPermissions(String packageName) {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo packageInfo = getAppPackageInfo(context, packageName);
        String[] requestedPermissions = packageInfo.requestedPermissions;
        int[] requestedPermissionsFlags = packageInfo.requestedPermissionsFlags;

        if (requestedPermissions == null || requestedPermissionsFlags == null) {
            return List.of();
        }

        final List<String> permissions = new ArrayList<>();

        for (int i = 0; i < requestedPermissions.length; i++) {
            if ((requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    && HealthConnectManager.isHealthPermission(context, requestedPermissions[i])) {
                permissions.add(requestedPermissions[i]);
            }
        }

        return permissions;
    }

    private static PackageInfo getAppPackageInfo(Context context, String packageName) {
        return getAppPackageInfo(context.getPackageManager(), packageName);
    }

    private static PackageInfo getAppPackageInfo(
            PackageManager packageManager, String packageName) {
        return runWithShellPermissionIdentity(
                () ->
                        packageManager.getPackageInfo(
                                packageName, PackageManager.PackageInfoFlags.of(GET_PERMISSIONS)));
    }

    /**
     * Grants the specified health permission to the app specified by {@code packageName}.
     *
     * @see HealthConnectManager#grantHealthPermission(String, String)
     */
    @SuppressLint("MissingPermission")
    public static void grantHealthPermission(String packageName, String permission) {
        HealthConnectManager service = getHealthConnectManager();
        runWithShellPermissionIdentity(
                () ->
                        service.getClass()
                                .getMethod("grantHealthPermission", String.class, String.class)
                                .invoke(service, packageName, permission),
                MANAGE_HEALTH_PERMISSIONS);
    }

    /**
     * Grants the specified health permissions to the app specified by {@code packageName}.
     *
     * @see HealthConnectManager#grantHealthPermission(String, String)
     */
    public static void grantHealthPermissions(String packageName, Collection<String> permissions) {
        for (String permission : permissions) {
            grantHealthPermission(packageName, permission);
        }
    }

    /**
     * Revokes the specified health permission from the app specified by {@code packageName}.
     *
     * @see HealthConnectManager#revokeHealthPermission(String, String, String)
     */
    @SuppressLint("MissingPermission")
    public static void revokeHealthPermission(String packageName, String permission) {
        HealthConnectManager service = getHealthConnectManager();
        runWithShellPermissionIdentity(
                () ->
                        service.getClass()
                                .getMethod(
                                        "revokeHealthPermission",
                                        String.class,
                                        String.class,
                                        String.class)
                                .invoke(service, packageName, permission, null),
                MANAGE_HEALTH_PERMISSIONS);
    }

    /**
     * Utility method to call {@link HealthConnectManager#revokeAllHealthPermissions(String,
     * String)}.
     */
    @SuppressLint("MissingPermission")
    public static void revokeAllHealthPermissions(String packageName, @Nullable String reason) {
        HealthConnectManager service = getHealthConnectManager();
        runWithShellPermissionIdentity(
                () ->
                        service.getClass()
                                .getMethod("revokeAllHealthPermissions", String.class, String.class)
                                .invoke(service, packageName, reason),
                MANAGE_HEALTH_PERMISSIONS);
    }

    /**
     * Same as {@link #revokeAllHealthPermissions(String, String)} but with a delay to wait for
     * grant time to be updated.
     */
    public static void revokeAllHealthPermissionsWithDelay(
            String packageName, @Nullable String reason) throws InterruptedException {
        revokeAllHealthPermissions(packageName, reason);
        // TODO(b/381409385): Replace with wait for grant time update.
        Thread.sleep(500);
    }

    /** Revokes all granted Health permissions and re-grants them back. */
    public static void revokeAndThenGrantHealthPermissions(String packageName) {
        List<String> healthPerms = getGrantedHealthPermissions(packageName);

        revokeHealthPermissions(packageName);

        for (String perm : healthPerms) {
            grantHealthPermission(packageName, perm);
        }
    }

    /** Revokes all granted Health permissions from the specified package. */
    @SuppressLint("MissingPermission")
    public static void revokeHealthPermissions(String packageName) {
        Context context = ApplicationProvider.getApplicationContext();
        PackageManager packageManager = context.getPackageManager();
        UserHandle user = context.getUser();

        PackageInfo packageInfo = getAppPackageInfo(packageManager, packageName);
        String[] permissions = packageInfo.requestedPermissions;
        if (permissions == null) {
            return;
        }

        runWithShellPermissionIdentity(
                () -> {
                    for (String permission : permissions) {
                        if (HealthConnectManager.isHealthPermission(context, permission)) {
                            packageManager.revokeRuntimePermission(packageName, permission, user);
                        }
                    }
                },
                REVOKE_RUNTIME_PERMISSIONS);
    }

    /**
     * Utility method to call {@link
     * HealthConnectManager#getHealthDataHistoricalAccessStartDate(String)}.
     */
    @SuppressLint("MissingPermission")
    public static Instant getHealthDataHistoricalAccessStartDate(String packageName) {
        HealthConnectManager service = getHealthConnectManager();
        return (Instant)
                runWithShellPermissionIdentity(
                        () ->
                                service.getClass()
                                        .getMethod(
                                                "getHealthDataHistoricalAccessStartDate",
                                                String.class)
                                        .invoke(service, packageName),
                        MANAGE_HEALTH_PERMISSIONS);
    }

    /** Revokes permission for the package for the duration of the runnable. */
    public static void runWithRevokedPermissions(
            String packageName, String permission, ThrowingRunnable runnable) throws Exception {
        runWithRevokedPermissions(
                (ThrowingSupplier<Void>)
                        () -> {
                            runnable.run();
                            return null;
                        },
                packageName,
                permission);
    }

    /** Revokes permission for the package for the duration of the supplier. */
    public static <T> T runWithRevokedPermission(
            String packageName, String permission, ThrowingSupplier<T> supplier) throws Exception {
        return runWithRevokedPermissions(supplier, packageName, permission);
    }

    /** Revokes permission for the package for the duration of the supplier. */
    public static <T> T runWithRevokedPermissions(
            ThrowingSupplier<T> supplier, String packageName, String... permissions)
            throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        checkArgument(
                !context.getPackageName().equals(packageName),
                "Can not be called on self, only on other apps");

        UiAutomation uiAutomation =
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        .getUiAutomation();

        var grantedPermissions =
                Sets.intersection(
                        Set.copyOf(getGrantedHealthPermissions(packageName)), Set.of(permissions));

        try {
            grantedPermissions.forEach(
                    permission -> uiAutomation.revokeRuntimePermission(packageName, permission));
            return supplier.get();
        } finally {
            grantedPermissions.forEach(
                    permission -> uiAutomation.grantRuntimePermission(packageName, permission));
        }
    }

    /** Flags the permission as USER_FIXED for the duration of the supplier. */
    public static <T> T runWithUserFixedPermission(
            String packageName, String permission, ThrowingSupplier<T> supplier) throws Exception {
        SystemUtil.runShellCommand(
                String.format("pm set-permission-flags %s %s user-fixed", packageName, permission));
        try {
            return supplier.get();
        } finally {
            SystemUtil.runShellCommand(
                    String.format(
                            "pm clear-permission-flags %s %s user-fixed", packageName, permission));
        }
    }
}
