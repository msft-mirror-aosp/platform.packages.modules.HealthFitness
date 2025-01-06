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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.health.connect.HealthPermissions;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class HealthConnectPermissionHelperTest {
    private static final String TEST_PACKAGE_NAME = "com.android.healthconnect.testapp";
    private static final String HC_PACKAGE_NAME = "com.android.healthconnect";
    private static final UserHandle CURRENT_USER = Process.myUserHandle();

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private HealthConnectPermissionHelper mPermissionHelper;
    private HealthConnectMappings healthConnectMappings = new HealthConnectMappings();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private HealthPermissionIntentAppsTracker mTracker;
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    @Mock private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    @Mock private AppInfoHelper mAppInfoHelper;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        mPermissionHelper =
                new HealthConnectPermissionHelper(
                        mContext,
                        mPackageManager,
                        mTracker,
                        mFirstGrantTimeManager,
                        mHealthDataCategoryPriorityHelper,
                        mAppInfoHelper,
                        healthConnectMappings);
        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mContext);
        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        PermissionGroupInfo info = new PermissionGroupInfo();
        info.packageName = HC_PACKAGE_NAME;
        when(mPackageManager.getPermissionGroupInfo(
                        eq(android.health.connect.HealthPermissions.HEALTH_PERMISSION_GROUP),
                        eq(0)))
                .thenReturn(info);
        setUpHealthPermissions();
    }

    @Test
    @DisableFlags({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void shouldEnforcePermissionUsageIntent_flagDisabled_shouldEnforce() {
        assertTrue(
                mPermissionHelper.shouldEnforcePermissionUsageIntent(
                        TEST_PACKAGE_NAME, CURRENT_USER));
    }

    @Test
    @EnableFlags({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void shouldEnforcePermissionUsageIntent_watchDevice_shouldNotEnforce()
            throws PackageManager.NameNotFoundException {
        when(mPackageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)).thenReturn(true);
        PackageInfo mockPackageInfo = new PackageInfo();
        mockPackageInfo.requestedPermissions =
                new String[] {HealthPermissions.READ_SKIN_TEMPERATURE};
        when(mPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), any()))
                .thenReturn(mockPackageInfo);

        assertFalse(
                mPermissionHelper.shouldEnforcePermissionUsageIntent(
                        TEST_PACKAGE_NAME, CURRENT_USER));
    }

    @Test
    @EnableFlags({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void shouldEnforcePermissionUsageIntent_noReadHrRequested_shouldEnforce()
            throws Exception {
        PackageInfo mockPackageInfo = new PackageInfo();
        mockPackageInfo.requestedPermissions =
                new String[] {HealthPermissions.READ_SKIN_TEMPERATURE};
        when(mPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), any()))
                .thenReturn(mockPackageInfo);

        assertTrue(
                mPermissionHelper.shouldEnforcePermissionUsageIntent(
                        TEST_PACKAGE_NAME, CURRENT_USER));
    }

    @Test
    @EnableFlags({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void
            shouldEnforcePermissionUsageIntent_multipleHealthPermissionsRequested_shouldEnforce()
                    throws Exception {
        PackageInfo mockPackageInfo = new PackageInfo();
        mockPackageInfo.requestedPermissions =
                new String[] {
                    HealthPermissions.READ_HEART_RATE, HealthPermissions.READ_SKIN_TEMPERATURE
                };
        when(mPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), any()))
                .thenReturn(mockPackageInfo);

        assertTrue(
                mPermissionHelper.shouldEnforcePermissionUsageIntent(
                        TEST_PACKAGE_NAME, CURRENT_USER));
    }

    @Test
    @EnableFlags({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void shouldEnforcePermissionUsageIntent_hrNotFromSplitPermission_shouldEnforce()
            throws Exception {
        PackageInfo mockPackageInfo = new PackageInfo();
        mockPackageInfo.requestedPermissions = new String[] {HealthPermissions.READ_HEART_RATE};
        when(mPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), any()))
                .thenReturn(mockPackageInfo);
        when(mPackageManager.getPermissionFlags(
                        HealthPermissions.READ_HEART_RATE, TEST_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(PackageManager.FLAG_PERMISSION_USER_SET);

        assertTrue(
                mPermissionHelper.shouldEnforcePermissionUsageIntent(
                        TEST_PACKAGE_NAME, CURRENT_USER));
    }

    @Test
    @EnableFlags({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void shouldEnforcePermissionUsageIntent_hrFromSplitPermission_shouldNotEnforce()
            throws Exception {
        PackageInfo mockPackageInfo = new PackageInfo();
        mockPackageInfo.requestedPermissions = new String[] {HealthPermissions.READ_HEART_RATE};
        when(mPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), any()))
                .thenReturn(mockPackageInfo);
        when(mPackageManager.getPermissionFlags(
                        HealthPermissions.READ_HEART_RATE, TEST_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED);

        assertFalse(
                mPermissionHelper.shouldEnforcePermissionUsageIntent(
                        TEST_PACKAGE_NAME, CURRENT_USER));
    }

    @Test
    @EnableFlags({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void
            shouldEnforcePermissionUsageIntent_backgroundPermissionNotFromSplitPermission_shouldEnforce()
                    throws Exception {
        PackageInfo mockPackageInfo = new PackageInfo();
        mockPackageInfo.requestedPermissions =
                new String[] {
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND
                };
        when(mPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), any()))
                .thenReturn(mockPackageInfo);
        when(mPackageManager.getPermissionFlags(
                        HealthPermissions.READ_HEART_RATE, TEST_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED);
        when(mPackageManager.getPermissionFlags(
                        HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                        TEST_PACKAGE_NAME,
                        CURRENT_USER))
                .thenReturn(PackageManager.FLAG_PERMISSION_USER_SET);

        assertTrue(
                mPermissionHelper.shouldEnforcePermissionUsageIntent(
                        TEST_PACKAGE_NAME, CURRENT_USER));
    }

    @Test
    @EnableFlags({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void
            shouldEnforcePermissionUsageIntent_backgroundPermissionFromSplitPermission_shouldNotEnforce()
                    throws Exception {
        PackageInfo mockPackageInfo = new PackageInfo();
        mockPackageInfo.requestedPermissions =
                new String[] {
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND
                };
        when(mPackageManager.getPackageInfo(eq(TEST_PACKAGE_NAME), any()))
                .thenReturn(mockPackageInfo);
        when(mPackageManager.getPermissionFlags(
                        HealthPermissions.READ_HEART_RATE, TEST_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED);
        when(mPackageManager.getPermissionFlags(
                        HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                        TEST_PACKAGE_NAME,
                        CURRENT_USER))
                .thenReturn(PackageManager.FLAG_PERMISSION_REVOKE_WHEN_REQUESTED);

        assertFalse(
                mPermissionHelper.shouldEnforcePermissionUsageIntent(
                        TEST_PACKAGE_NAME, CURRENT_USER));
    }

    private void setUpHealthPermissions()  throws PackageManager.NameNotFoundException {
        PackageInfo mockPackageInfo = new PackageInfo();
        // For now add a few of the HealthPermissions just for the test.
        mockPackageInfo.permissions =
                new PermissionInfo[] {
                    createPermissionInfo(HealthPermissions.READ_HEART_RATE),
                    createPermissionInfo(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND),
                    createPermissionInfo(HealthPermissions.READ_SKIN_TEMPERATURE),
                    createPermissionInfo(HealthPermissions.READ_OXYGEN_SATURATION),
                };
        when(mPackageManager.getPackageInfo(eq(HC_PACKAGE_NAME), any()))
                .thenReturn(mockPackageInfo);
    }

    private PermissionInfo createPermissionInfo(String permissionName) {
        PermissionInfo permissionInfo = new PermissionInfo();
        permissionInfo.name = permissionName;
        permissionInfo.group = HealthPermissions.HEALTH_PERMISSION_GROUP;
        return permissionInfo;
    }
}
