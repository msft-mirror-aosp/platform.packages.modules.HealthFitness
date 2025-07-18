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

import static com.android.server.healthconnect.TestUtils.waitForAllScheduledTasksToComplete;
import static com.android.server.healthconnect.permission.FirstGrantTimeDatastore.DATA_TYPE_CURRENT;
import static com.android.server.healthconnect.permission.FirstGrantTimeDatastore.DATA_TYPE_STAGED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthPermissions;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Pair;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.HealthConnectThreadScheduler;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.migration.MigrationStateManager;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

// TODO(b/261432978): add test for sharedUser backup
public class FirstGrantTimeUnitTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(HealthConnectThreadScheduler.class)
                    .spyStatic(UserHandle.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private static final String SELF_PACKAGE_NAME = "com.android.healthconnect.unittests";
    private static final int SELF_PACKAGE_UID = 123;
    private static final UserHandle CURRENT_USER = Process.myUserHandle();

    private static final int DEFAULT_VERSION = 1;

    @Mock private HealthPermissionIntentAppsTracker mTracker;
    @Mock private MigrationStateManager mMigrationStateManager;
    @Mock private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    @Mock private PackageManager mPackageManager;
    @Mock private UserManager mUserManager;
    @Mock private Context mContext;
    @Mock private FirstGrantTimeDatastore mDatastore;

    private FirstGrantTimeManager mFirstGrantTimeManager;

    @Before
    public void setUp() throws PackageManager.NameNotFoundException {
        MockitoAnnotations.initMocks(this);

        when(mMigrationStateManager.isMigrationInProgress()).thenReturn(false);
        when(mTracker.supportsPermissionUsageIntent(anyString(), any())).thenReturn(true);

        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT))
                .thenReturn(new UserGrantTimeState(DEFAULT_VERSION));
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_STAGED))
                .thenReturn(new UserGrantTimeState(DEFAULT_VERSION));

        when(mContext.createContextAsUser(any(), anyInt())).thenReturn(mContext);
        when(mContext.getApplicationContext()).thenReturn(mContext);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mContext.getUser()).thenReturn(CURRENT_USER);

        when(mUserManager.isUserUnlocked(any())).thenReturn(true);
        ExtendedMockito.when(UserHandle.getUserHandleForUid(SELF_PACKAGE_UID))
                .thenReturn(CURRENT_USER);

        when(mPackageManager.getPackageUid(eq(SELF_PACKAGE_NAME), any()))
                .thenReturn(SELF_PACKAGE_UID);
        when(mPackageManager.getPackagesForUid(SELF_PACKAGE_UID))
                .thenReturn(new String[] {SELF_PACKAGE_NAME});

        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = SELF_PACKAGE_NAME;
        packageInfo.requestedPermissions = new String[] {HealthPermissions.WRITE_STEPS};
        packageInfo.requestedPermissionsFlags =
                new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};
        when(mPackageManager.getPackageInfo(eq(SELF_PACKAGE_NAME), any())).thenReturn(packageInfo);
        when(mPackageManager.getInstalledPackages(any())).thenReturn(List.of(packageInfo));

        ExtendedMockito.when(
                        HealthConnectManager.isHealthPermission(
                                mContext, HealthPermissions.WRITE_STEPS))
                .thenReturn(true);
        ExtendedMockito.when(HealthConnectManager.getHealthPermissions(mContext))
                .thenReturn(Set.of(HealthPermissions.WRITE_STEPS));

        mFirstGrantTimeManager =
                HealthConnectInjectorImpl.newBuilderForTest(mContext)
                        .setMigrationStateManager(mMigrationStateManager)
                        .setFirstGrantTimeDatastore(mDatastore)
                        .setHealthPermissionIntentAppsTracker(mTracker)
                        .setHealthDataCategoryPriorityHelper(mHealthDataCategoryPriorityHelper)
                        .build()
                        .getFirstGrantTimeManager();
    }

    @After
    public void tearDown() throws Exception {
        waitForAllScheduledTasksToComplete();
    }

    @Test
    public void testSetFirstGrantTimeForAnApp_expectOtherAppsGrantTimesRemained()
            throws PackageManager.NameNotFoundException {
        Instant instant1 = Instant.parse("2023-02-11T10:00:00Z");
        Instant instant2 = Instant.parse("2023-02-12T10:00:00Z");
        Instant instant3 = Instant.parse("2023-02-13T10:00:00Z");
        String anotherPackage = "another.package";

        // mock packageManager
        List<Pair<String, Integer>> packageNameAndUidPairs =
                Arrays.asList(new Pair<>(SELF_PACKAGE_NAME, 123), new Pair<>(anotherPackage, 456));
        List<PackageInfo> packageInfos = new ArrayList<>();
        for (Pair<String, Integer> pair : packageNameAndUidPairs) {
            String packageName = pair.first;
            int uid = pair.second;
            PackageInfo packageInfo = new PackageInfo();
            packageInfo.packageName = packageName;
            packageInfo.requestedPermissions = new String[] {HealthPermissions.WRITE_STEPS};
            packageInfo.requestedPermissionsFlags =
                    new int[] {PackageInfo.REQUESTED_PERMISSION_GRANTED};
            packageInfos.add(packageInfo);
            when(mPackageManager.getPackageUid(eq(packageName), any())).thenReturn(uid);
            when(mPackageManager.getPackagesForUid(uid)).thenReturn(new String[] {packageName});
            when(mPackageManager.getPackageInfo(eq(packageName), any())).thenReturn(packageInfo);

            ExtendedMockito.when(UserHandle.getUserHandleForUid(uid)).thenReturn(CURRENT_USER);
        }
        when(mPackageManager.getInstalledPackages(any())).thenReturn(packageInfos);

        // Mock Datastore.
        UserGrantTimeState currentGrantTimeState = new UserGrantTimeState(DEFAULT_VERSION);
        currentGrantTimeState.setPackageGrantTime(SELF_PACKAGE_NAME, instant1);
        currentGrantTimeState.setPackageGrantTime(anotherPackage, instant2);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT))
                .thenReturn(currentGrantTimeState);

        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .hasValue(instant1);
        assertThat(mFirstGrantTimeManager.getFirstGrantTime(anotherPackage, CURRENT_USER))
                .hasValue(instant2);

        ArgumentCaptor<UserGrantTimeState> captor =
                ArgumentCaptor.forClass(UserGrantTimeState.class);
        mFirstGrantTimeManager.setFirstGrantTime(SELF_PACKAGE_NAME, instant3, CURRENT_USER);
        verify(mDatastore).writeForUser(captor.capture(), eq(CURRENT_USER), eq(DATA_TYPE_CURRENT));

        UserGrantTimeState newUserGrantTimeState = captor.getValue();
        assertThat(newUserGrantTimeState.getPackageGrantTimes().keySet()).hasSize(2);
        assertThat(newUserGrantTimeState.getPackageGrantTimes().get(SELF_PACKAGE_NAME))
                .isEqualTo(instant3);
        assertThat(newUserGrantTimeState.getPackageGrantTimes().get(anotherPackage))
                .isEqualTo(instant2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownPackage_throwsException() throws PackageManager.NameNotFoundException {
        String unknownPackage = "android.unknown_package";
        when(mPackageManager.getPackageUid(eq(unknownPackage), any()))
                .thenThrow(new PackageManager.NameNotFoundException());
        mFirstGrantTimeManager.getFirstGrantTime(unknownPackage, CURRENT_USER);
    }

    @Test
    public void testCurrentPackage_intentNotSupported_grantTimeIsNull() {
        when(mTracker.supportsPermissionUsageIntent(SELF_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(false);
        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isEmpty();
    }

    @Test
    public void testOnPermissionsChangedCalledWhileDeviceIsLocked_getGrantTimeNotNullAfterUnlock()
            throws TimeoutException {
        // before device is unlocked
        when(mUserManager.isUserUnlocked(any())).thenReturn(false);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT)).thenReturn(null);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_STAGED)).thenReturn(null);
        mFirstGrantTimeManager.onPermissionsChanged(SELF_PACKAGE_UID);
        waitForAllScheduledTasksToComplete();
        // after device is unlocked
        when(mUserManager.isUserUnlocked(any())).thenReturn(true);
        UserGrantTimeState currentGrantTimeState = new UserGrantTimeState(DEFAULT_VERSION);
        Instant now = Instant.parse("2023-02-14T10:00:00Z");
        currentGrantTimeState.setPackageGrantTime(SELF_PACKAGE_NAME, now);
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT))
                .thenReturn(currentGrantTimeState);

        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .hasValue(now);
    }

    @Test
    public void testOnPermissionsChanged_withHealthPermissionsUid_expectBackgroundTaskAdded() {
        mFirstGrantTimeManager.onPermissionsChanged(SELF_PACKAGE_UID);

        ExtendedMockito.verify(
                () -> HealthConnectThreadScheduler.scheduleInternalTask(any()), times(1));
    }

    @Test
    public void testOnPermissionsChanged_withNoHealthPermissionsUid_expectNoBackgroundTaskAdded() {
        when(mTracker.supportsPermissionUsageIntent(SELF_PACKAGE_NAME, CURRENT_USER))
                .thenReturn(false);

        mFirstGrantTimeManager.onPermissionsChanged(SELF_PACKAGE_UID);

        ExtendedMockito.verify(
                () -> HealthConnectThreadScheduler.scheduleInternalTask(any()), times(0));
    }

    @Test
    public void testCurrentPackage_intentSupported_grantTimeIsNotNull() {
        // Calling getFirstGrantTime will set grant time for the package
        Optional<Instant> firstGrantTime =
                mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER);
        assertThat(firstGrantTime).isPresent();

        assertThat(firstGrantTime.get()).isGreaterThan(Instant.now().minusSeconds((long) 1e3));
        assertThat(firstGrantTime.get()).isLessThan(Instant.now().plusSeconds((long) 1e3));
        firstGrantTime.ifPresent(
                grantTime -> {
                    assertThat(grantTime).isGreaterThan(Instant.now().minusSeconds((long) 1e3));
                    assertThat(grantTime).isLessThan(Instant.now().plusSeconds((long) 1e3));
                });
        verify(mDatastore)
                .writeForUser(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(CURRENT_USER),
                        ArgumentMatchers.eq(DATA_TYPE_CURRENT));
        verify(mDatastore)
                .readForUser(
                        ArgumentMatchers.eq(CURRENT_USER), ArgumentMatchers.eq(DATA_TYPE_CURRENT));
    }

    @Test
    public void testCurrentPackage_noGrantTimeBackupBecameAvailable_grantTimeEqualToStaged() {
        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .isPresent();
        Instant backupTime = Instant.now().minusSeconds((long) 1e5);
        UserGrantTimeState stagedState = setupGrantTimeState(null, backupTime);
        mFirstGrantTimeManager.applyAndStageGrantTimeStateForUser(CURRENT_USER, stagedState);
        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .hasValue(backupTime);
    }

    @Test
    public void testCurrentPackage_noBackup_useRecordedTime() {
        Instant stateTime = Instant.now().minusSeconds((long) 1e5);
        UserGrantTimeState stagedState = setupGrantTimeState(stateTime, null);

        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .hasValue(stateTime);
        mFirstGrantTimeManager.applyAndStageGrantTimeStateForUser(CURRENT_USER, stagedState);
        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .hasValue(stateTime);
    }

    @Test
    public void testCurrentPackage_noBackup_grantTimeEqualToStaged() {
        Instant backupTime = Instant.now().minusSeconds((long) 1e5);
        Instant stateTime = backupTime.plusSeconds(10);
        UserGrantTimeState stagedState = setupGrantTimeState(stateTime, backupTime);

        mFirstGrantTimeManager.applyAndStageGrantTimeStateForUser(CURRENT_USER, stagedState);
        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .hasValue(backupTime);
    }

    @Test
    public void testCurrentPackage_backupDataLater_stagedDataSkipped() {
        Instant stateTime = Instant.now().minusSeconds((long) 1e5);
        UserGrantTimeState stagedState = setupGrantTimeState(stateTime, stateTime.plusSeconds(1));

        mFirstGrantTimeManager.applyAndStageGrantTimeStateForUser(CURRENT_USER, stagedState);
        assertThat(mFirstGrantTimeManager.getFirstGrantTime(SELF_PACKAGE_NAME, CURRENT_USER))
                .hasValue(stateTime);
    }

    @Test
    public void testWriteStagedData_getStagedStateForCurrentPackage_returnsCorrectState() {
        Instant stateTime = Instant.now().minusSeconds((long) 1e5);
        setupGrantTimeState(stateTime, null);

        UserGrantTimeState state = mFirstGrantTimeManager.getGrantTimeStateForUser(CURRENT_USER);
        assertThat(state.getSharedUserGrantTimes()).isEmpty();
        assertThat(state.getPackageGrantTimes().containsKey(SELF_PACKAGE_NAME)).isTrue();
        assertThat(state.getPackageGrantTimes().get(SELF_PACKAGE_NAME)).isEqualTo(stateTime);
    }

    private UserGrantTimeState setupGrantTimeState(Instant currentTime, Instant stagedTime) {
        if (currentTime != null) {
            UserGrantTimeState state = new UserGrantTimeState(DEFAULT_VERSION);
            state.setPackageGrantTime(SELF_PACKAGE_NAME, currentTime);
            when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_CURRENT)).thenReturn(state);
        }

        UserGrantTimeState backupState = new UserGrantTimeState(DEFAULT_VERSION);
        if (stagedTime != null) {
            backupState.setPackageGrantTime(SELF_PACKAGE_NAME, stagedTime);
        }
        when(mDatastore.readForUser(CURRENT_USER, DATA_TYPE_STAGED)).thenReturn(backupState);
        return backupState;
    }
}
