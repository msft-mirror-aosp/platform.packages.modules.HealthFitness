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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.health.connect.HealthDataCategory;
import android.net.Uri;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

public class PermissionPackageChangesOrchestratorTest {
    private static final String SELF_PACKAGE_NAME = "com.android.healthconnect.unittests";
    private static final UserHandle CURRENT_USER = Process.myUserHandle();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(TransactionManager.class)
                    .mockStatic(HealthDataCategoryPriorityHelper.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private int mCurrentUid;
    private PermissionPackageChangesOrchestrator mOrchestrator;
    private Context mContext;

    @Mock private HealthConnectPermissionHelper mHelper;
    @Mock private HealthPermissionIntentAppsTracker mTracker;
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    @Mock private UserHandle mUserHandle;

    @Mock private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        mContext = ApplicationProvider.getApplicationContext();
        mCurrentUid = mContext.getPackageManager().getPackageUid(SELF_PACKAGE_NAME, 0);
        mOrchestrator =
                new PermissionPackageChangesOrchestrator(
                        mTracker,
                        mFirstGrantTimeManager,
                        mHelper,
                        mUserHandle,
                        mHealthDataCategoryPriorityHelper);
        setIntentIsPresent(/* isIntentPresent= */ true);
    }

    @Test
    public void testPackageAdded_callsTrackerToUpdateState_noGrantTimeOrPermsCalls() {
        mOrchestrator.onReceive(mContext, buildPackageIntent(Intent.ACTION_PACKAGE_ADDED));
        verify(mHelper, never())
                .revokeAllHealthPermissions(eq(SELF_PACKAGE_NAME), anyString(), eq(CURRENT_USER));
        verify(mFirstGrantTimeManager, never())
                .onPackageRemoved(eq(SELF_PACKAGE_NAME), eq(mCurrentUid), eq(CURRENT_USER));
    }

    @Test
    public void testPackageChanged_intentWasRemoved_nonWear_revokesPerms() {
        assumeFalse(FeatureUtil.isWatch());
        setIntentIsPresent(/* isIntentPresent= */ false);
        mOrchestrator.onReceive(mContext, buildPackageIntent(Intent.ACTION_PACKAGE_CHANGED));
        verify(mHelper)
                .revokeAllHealthPermissions(eq(SELF_PACKAGE_NAME), anyString(), eq(CURRENT_USER));
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void testPackageChanged_intentWasRemoved_wear_doesNotRevokePerms() {
        assumeTrue(FeatureUtil.isWatch());
        setIntentIsPresent(/* isIntentPresent= */ false);
        mOrchestrator.onReceive(mContext, buildPackageIntent(Intent.ACTION_PACKAGE_CHANGED));
        verify(mHelper, never())
                .revokeAllHealthPermissions(eq(SELF_PACKAGE_NAME), anyString(), eq(CURRENT_USER));
    }

    @Test
    public void testPackageRemoved_resetsGrantTime() {
        mOrchestrator.onReceive(
                mContext,
                buildPackageIntent(Intent.ACTION_PACKAGE_REMOVED, /* isReplaced= */ false));
        verify(mFirstGrantTimeManager)
                .onPackageRemoved(eq(SELF_PACKAGE_NAME), eq(mCurrentUid), eq(CURRENT_USER));
    }

    @Test
    public void testPackageRemoved_removesFromPriorityList_whenNewAggregationOff() {
        mOrchestrator.onReceive(
                mContext,
                buildPackageIntent(Intent.ACTION_PACKAGE_REMOVED, /* isReplaced= */ false));
        assertThat(
                        mHealthDataCategoryPriorityHelper.syncAndGetPriorityOrder(
                                HealthDataCategory.SLEEP))
                .isEmpty();
    }

    @Test
    public void testPackageReplaced_noGrantTimeResetsOrPermsRevokes() {
        mOrchestrator.onReceive(
                mContext,
                buildPackageIntent(Intent.ACTION_PACKAGE_REMOVED, /* isReplaced= */ true));
        verify(mFirstGrantTimeManager, never())
                .onPackageRemoved(eq(SELF_PACKAGE_NAME), eq(mCurrentUid), eq(CURRENT_USER));
        verify(mHelper, never())
                .revokeAllHealthPermissions(eq(SELF_PACKAGE_NAME), anyString(), eq(CURRENT_USER));
    }

    @Test
    public void testPackageReplaced_intentNotSupported_nonWear_revokesPerms() {
        assumeFalse(FeatureUtil.isWatch());
        setIntentIsPresent(/* isIntentPresent= */ false);
        mOrchestrator.onReceive(
                mContext,
                buildPackageIntent(Intent.ACTION_PACKAGE_REMOVED, /* isReplaced= */ true));
        verify(mHelper)
                .revokeAllHealthPermissions(eq(SELF_PACKAGE_NAME), anyString(), eq(CURRENT_USER));
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED})
    public void testPackageReplaced_intentNotSupported_wear_doesNotRevokePerms() {
        assumeTrue(FeatureUtil.isWatch());
        setIntentIsPresent(/* isIntentPresent= */ false);
        mOrchestrator.onReceive(
                mContext,
                buildPackageIntent(Intent.ACTION_PACKAGE_REMOVED, /* isReplaced= */ true));
        verify(mHelper, never())
                .revokeAllHealthPermissions(eq(SELF_PACKAGE_NAME), anyString(), eq(CURRENT_USER));
    }

    private Intent buildPackageIntent(String action) {
        return buildPackageIntent(action, /* isReplaced= */ false);
    }

    private Intent buildPackageIntent(String action, boolean isReplaced) {
        return new Intent()
                .setAction(action)
                .putExtra(Intent.EXTRA_REPLACING, isReplaced)
                .setData(Uri.parse("package:" + SELF_PACKAGE_NAME))
                .putExtra(Intent.EXTRA_UID, mCurrentUid);
    }

    private void setIntentIsPresent(boolean isIntentPresent) {
        when(mTracker.updateAndGetSupportsPackageUsageIntent(SELF_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(isIntentPresent);
        when(mTracker.updateStateAndGetIfIntentWasRemoved(SELF_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(!isIntentPresent);
    }
}
