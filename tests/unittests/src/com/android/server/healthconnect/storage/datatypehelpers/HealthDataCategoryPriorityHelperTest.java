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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper.APP_ID_PRIORITY_ORDER_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper.HEALTH_DATA_CATEGORY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.flattenLongList;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.health.connect.HealthConnectManager;
import android.health.connect.HealthDataCategory;
import android.health.connect.HealthPermissions;
import android.health.connect.datatypes.RecordTypeIdentifier;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.TestUtils;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.permission.PackageInfoUtils;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.StorageUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class HealthDataCategoryPriorityHelperTest {

    private static final long APP_PACKAGE_ID = 1;
    private static final long APP_PACKAGE_ID_2 = 2;
    private static final long APP_PACKAGE_ID_3 = 3;
    private static final long APP_PACKAGE_ID_4 = 4;

    private static final String APP_PACKAGE_NAME = "android.healthconnect.mocked.app";
    private static final String APP_PACKAGE_NAME_2 = "android.healthconnect.mocked.app2";
    private static final String APP_PACKAGE_NAME_3 = "android.healthconnect.mocked.app3";
    private static final String APP_PACKAGE_NAME_4 = "android.healthconnect.mocked.app4";
    private static final int APP_ID_PRIORITY_ORDER_COLUMN_INDEX = 2;
    private static final int HEALTH_DATA_CATEGORY_COLUMN_INDEX = 1;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(TransactionManager.class)
                    .mockStatic(AppInfoHelper.class)
                    .mockStatic(PackageInfoUtils.class)
                    .mockStatic(PreferenceHelper.class)
                    .mockStatic(HealthConnectManager.class)
                    .build();

    @Mock private Cursor mCursor;
    @Mock private TransactionManager mTransactionManager;
    @Mock private AppInfoHelper mAppInfoHelper;
    @Mock private PackageInfoUtils mPackageInfoUtils;
    @Mock private PackageInfo mPackageInfo1;
    @Mock private PackageInfo mPackageInfo2;
    @Mock private PreferenceHelper mPreferenceHelper;

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mTransactionManager.read(any())).thenReturn(mCursor);
        when(mAppInfoHelper.getOrInsertAppInfoId(eq(APP_PACKAGE_NAME))).thenReturn(APP_PACKAGE_ID);
        when(mAppInfoHelper.getOrInsertAppInfoId(eq(APP_PACKAGE_NAME_2)))
                .thenReturn(APP_PACKAGE_ID_2);
        when(mAppInfoHelper.getOrInsertAppInfoId(eq(APP_PACKAGE_NAME_3)))
                .thenReturn(APP_PACKAGE_ID_3);
        when(mAppInfoHelper.getOrInsertAppInfoId(eq(APP_PACKAGE_NAME_4)))
                .thenReturn(APP_PACKAGE_ID_4);
        when(mAppInfoHelper.getAppInfoId(APP_PACKAGE_NAME)).thenReturn(APP_PACKAGE_ID);
        when(mAppInfoHelper.getAppInfoId(APP_PACKAGE_NAME_2)).thenReturn(APP_PACKAGE_ID_2);
        when(mAppInfoHelper.getAppInfoId(APP_PACKAGE_NAME_3)).thenReturn(APP_PACKAGE_ID_3);
        when(mAppInfoHelper.getAppInfoId(APP_PACKAGE_NAME_4)).thenReturn(APP_PACKAGE_ID_4);
        when(mAppInfoHelper.getPackageName(APP_PACKAGE_ID)).thenReturn(APP_PACKAGE_NAME);
        when(mAppInfoHelper.getPackageName(APP_PACKAGE_ID_2)).thenReturn(APP_PACKAGE_NAME_2);
        when(mAppInfoHelper.getPackageName(APP_PACKAGE_ID_3)).thenReturn(APP_PACKAGE_NAME_3);
        when(mAppInfoHelper.getPackageName(APP_PACKAGE_ID_4)).thenReturn(APP_PACKAGE_NAME_4);
        when(mCursor.getColumnIndex(eq(HEALTH_DATA_CATEGORY_COLUMN_NAME)))
                .thenReturn(HEALTH_DATA_CATEGORY_COLUMN_INDEX);
        when(mCursor.getColumnIndex(eq(APP_ID_PRIORITY_ORDER_COLUMN_NAME)))
                .thenReturn(APP_ID_PRIORITY_ORDER_COLUMN_INDEX);

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .setAppInfoHelper(mAppInfoHelper)
                        .setTransactionManager(mTransactionManager)
                        .setPreferenceHelper(mPreferenceHelper)
                        .setPackageInfoUtils(mPackageInfoUtils)
                        .build();
        mHealthDataCategoryPriorityHelper =
                healthConnectInjector.getHealthDataCategoryPriorityHelper();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.waitForAllScheduledTasksToComplete();
    }

    @Test
    public void testAppendToPriorityList_ifAppInList_doesNotAddToList() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        when(mAppInfoHelper.getOrInsertAppInfoId(any(), anyString())).thenReturn(APP_PACKAGE_ID);
        mHealthDataCategoryPriorityHelper.appendToPriorityList(
                APP_PACKAGE_NAME, HealthDataCategory.BODY_MEASUREMENTS, true);

        verify(mTransactionManager, never())
                .insertOrReplaceOnConflict(any(UpsertTableRequest.class));
    }

    @Test
    public void testAppendToPriorityList_activeDefaultApp_addsToTopOfList() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(true).when(spy).isDefaultApp(APP_PACKAGE_NAME_4);
        when(mAppInfoHelper.getOrInsertAppInfoId(any(), anyString())).thenReturn(APP_PACKAGE_ID_4);
        spy.appendToPriorityList(APP_PACKAGE_NAME_4, HealthDataCategory.BODY_MEASUREMENTS, false);

        List<Long> expectedPriorityOrder =
                List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID, APP_PACKAGE_ID_2);
        verify(mTransactionManager)
                .insertOrReplaceOnConflict(
                        argThat(new UpsertRequestMatcher(expectedPriorityOrder)));
        assertThat(spy.getAppIdPriorityOrder(HealthDataCategory.BODY_MEASUREMENTS))
                .isEqualTo(expectedPriorityOrder);
    }

    @Test
    public void testAppendToPriorityList_ifNonDefaultApp_addsToBottomOfList() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(false).when(spy).isDefaultApp(APP_PACKAGE_NAME_4);
        when(mAppInfoHelper.getOrInsertAppInfoId(any(), anyString())).thenReturn(APP_PACKAGE_ID_4);
        spy.appendToPriorityList(APP_PACKAGE_NAME_4, HealthDataCategory.BODY_MEASUREMENTS, false);

        List<Long> expectedPriorityOrder =
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_4);
        verify(mTransactionManager)
                .insertOrReplaceOnConflict(
                        argThat(new UpsertRequestMatcher(expectedPriorityOrder)));
        assertThat(spy.getAppIdPriorityOrder(HealthDataCategory.BODY_MEASUREMENTS))
                .isEqualTo(expectedPriorityOrder);
    }

    @Test
    public void testAppendToPriorityList_inactiveDefaultApp_addsToBottomOfList() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(true).when(spy).isDefaultApp(APP_PACKAGE_NAME_4);
        when(mAppInfoHelper.getOrInsertAppInfoId(any(), anyString())).thenReturn(APP_PACKAGE_ID_4);
        spy.appendToPriorityList(APP_PACKAGE_NAME_4, HealthDataCategory.BODY_MEASUREMENTS, true);

        List<Long> expectedPriorityOrder =
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_4);
        verify(mTransactionManager)
                .insertOrReplaceOnConflict(
                        argThat(new UpsertRequestMatcher(expectedPriorityOrder)));
        assertThat(spy.getAppIdPriorityOrder(HealthDataCategory.BODY_MEASUREMENTS))
                .isEqualTo(expectedPriorityOrder);
    }

    @Test
    public void testMaybeRemoveAppFromPriorityList_ifWritePermissionsForApp_doesNotRemoveApp() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID));
        setupPriorityList(priorityList);
        setupPackageInfoWithWritePermissionGranted();

        mHealthDataCategoryPriorityHelper.maybeRemoveAppFromPriorityList(
                APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);

        verify(mTransactionManager, never())
                .insertOrReplaceOnConflict(any(UpsertTableRequest.class));
    }

    @Test
    public void testMaybeRemoveAppFromPriorityList_ifDataForApp_doesNotRemoveApp() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID));
        setupPriorityList(priorityList);
        setupPackageInfoWithWritePermissionNotGranted();

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(true)
                .when(spy)
                .appHasDataInCategory(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        spy.maybeRemoveAppFromPriorityList(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);

        verify(mTransactionManager, never())
                .insertOrReplaceOnConflict(any(UpsertTableRequest.class));
    }

    @Test
    public void testMaybeRemoveAppFromPriorityList_ifNoDataForApp_removesApp() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID));
        setupPriorityList(priorityList);
        setupPackageInfoWithWritePermissionNotGranted();

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(false)
                .when(spy)
                .appHasDataInCategory(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        spy.maybeRemoveAppFromPriorityList(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);

        List<Long> expectedPriorityOrder = List.of(APP_PACKAGE_ID_3);
        verify(mTransactionManager)
                .insertOrReplaceOnConflict(
                        argThat(new UpsertRequestMatcher(expectedPriorityOrder)));
        assertThat(spy.getAppIdPriorityOrder(HealthDataCategory.ACTIVITY))
                .isEqualTo(expectedPriorityOrder);
    }

    @Test
    public void
            testMaybeRemoveAppFromPriorityList_allCategories_ifWritePermissionsForApp_doesNotRemoveApp() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID));
        setupPriorityList(priorityList);
        setupPackageInfoWithWritePermissionGranted();

        mHealthDataCategoryPriorityHelper.maybeRemoveAppFromPriorityList(APP_PACKAGE_NAME);

        List<Long> expectedPriorityOrder = List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID);
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.ACTIVITY))
                .isEqualTo(expectedPriorityOrder);
    }

    @Test
    public void testMaybeRemoveAppFromPriorityList_allCategories_ifDataForApp_doesNotRemoveApp() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID));
        setupPriorityList(priorityList);
        setupPackageInfoWithWritePermissionNotGranted();

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(true)
                .when(spy)
                .appHasDataInCategory(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        spy.maybeRemoveAppFromPriorityList(APP_PACKAGE_NAME);

        verify(mTransactionManager, never())
                .insertOrReplaceOnConflict(any(UpsertTableRequest.class));
    }

    @Test
    public void testMaybeRemoveAppFromPriorityList_allCategories_ifNoDataForApp_removesApp() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID));
        setupPriorityList(priorityList);
        setupPackageInfoWithWritePermissionNotGranted();

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(false)
                .when(spy)
                .appHasDataInCategory(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);

        spy.maybeRemoveAppFromPriorityList(APP_PACKAGE_NAME);
        List<Long> expectedPriorityOrder = List.of(APP_PACKAGE_ID_3);
        verify(mTransactionManager)
                .insertOrReplaceOnConflict(
                        argThat(new UpsertRequestMatcher(expectedPriorityOrder)));
        assertThat(spy.getAppIdPriorityOrder(HealthDataCategory.ACTIVITY))
                .isEqualTo(expectedPriorityOrder);
    }

    @Test
    public void
            testMaybeRemoveAppWithoutWritePermissionsFromPriorityList_ifDataForApp_doesNotRemoveApp() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID));
        setupPriorityList(priorityList);

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(true)
                .when(spy)
                .appHasDataInCategory(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        spy.maybeRemoveAppWithoutWritePermissionsFromPriorityList(APP_PACKAGE_NAME);

        verify(mTransactionManager, never())
                .insertOrReplaceOnConflict(any(UpsertTableRequest.class));
    }

    @Test
    public void
            testMaybeRemoveAppWithoutWritePermissionsFromPriorityList_ifNoDataForApp_removesApp() {
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID));
        setupPriorityList(priorityList);

        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doReturn(false)
                .when(spy)
                .appHasDataInCategory(APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY);
        spy.maybeRemoveAppWithoutWritePermissionsFromPriorityList(APP_PACKAGE_NAME);

        List<Long> expectedPriorityOrder = List.of(APP_PACKAGE_ID_3);
        verify(mTransactionManager)
                .insertOrReplaceOnConflict(
                        argThat(new UpsertRequestMatcher(expectedPriorityOrder)));
        assertThat(spy.getAppIdPriorityOrder(HealthDataCategory.ACTIVITY))
                .isEqualTo(expectedPriorityOrder);
    }

    @Test
    public void testGetPriorityOrder_callsReSyncPriority() {
        when(mAppInfoHelper.getPackageNames(any()))
                .thenReturn(List.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2));
        HealthDataCategoryPriorityHelper spy = Mockito.spy(mHealthDataCategoryPriorityHelper);
        doNothing().when(spy).reSyncHealthDataPriorityTable();

        spy.syncAndGetPriorityOrder(HealthDataCategory.ACTIVITY);
        verify(spy, times(1)).reSyncHealthDataPriorityTable();
    }

    @Test
    public void testSetPriority_additionalPackages_addsToPriorityList() {
        List<String> newPriorityOrder =
                List.of(
                        APP_PACKAGE_NAME_4,
                        APP_PACKAGE_NAME_3,
                        APP_PACKAGE_NAME_2,
                        APP_PACKAGE_NAME);

        List<Long> newPriorityOrderId = new ArrayList<>();
        newPriorityOrderId.add(APP_PACKAGE_ID_4);
        newPriorityOrderId.add(APP_PACKAGE_ID_3);
        newPriorityOrderId.add(APP_PACKAGE_ID_2);
        newPriorityOrderId.add(APP_PACKAGE_ID);

        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS, List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        when(mAppInfoHelper.getAppInfoIds(eq(newPriorityOrder))).thenReturn(newPriorityOrderId);
        mHealthDataCategoryPriorityHelper.setPriorityOrder(
                HealthDataCategory.BODY_MEASUREMENTS, newPriorityOrder);

        verifyPriorityUpdate(
                List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID_3, APP_PACKAGE_ID_2, APP_PACKAGE_ID),
                HealthDataCategory.BODY_MEASUREMENTS);
    }

    @Test
    public void testSetPriority_fewerPackages_removesFromPriorityList() {
        List<String> newPriorityOrder = List.of(APP_PACKAGE_NAME_2, APP_PACKAGE_NAME);

        List<Long> newPriorityOrderId = new ArrayList<>();
        newPriorityOrderId.add(APP_PACKAGE_ID_2);
        newPriorityOrderId.add(APP_PACKAGE_ID);
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS,
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        setupPriorityList(priorityList);

        when(mAppInfoHelper.getAppInfoIds(eq(newPriorityOrder))).thenReturn(newPriorityOrderId);

        mHealthDataCategoryPriorityHelper.setPriorityOrder(
                HealthDataCategory.BODY_MEASUREMENTS, newPriorityOrder);

        verifyPriorityUpdate(
                List.of(APP_PACKAGE_ID_2, APP_PACKAGE_ID), HealthDataCategory.BODY_MEASUREMENTS);
    }

    @Test
    public void testSetPriority_samePackages_reordersPriorityList() {
        List<String> newPriorityOrder =
                List.of(
                        APP_PACKAGE_NAME_3,
                        APP_PACKAGE_NAME_2,
                        APP_PACKAGE_NAME,
                        APP_PACKAGE_NAME_4);

        List<Long> newPriorityOrderId = new ArrayList<>();
        newPriorityOrderId.add(APP_PACKAGE_ID_3);
        newPriorityOrderId.add(APP_PACKAGE_ID_2);
        newPriorityOrderId.add(APP_PACKAGE_ID);
        newPriorityOrderId.add(APP_PACKAGE_ID_4);

        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS,
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        setupPriorityList(priorityList);

        when(mAppInfoHelper.getAppInfoIds(eq(newPriorityOrder))).thenReturn(newPriorityOrderId);
        mHealthDataCategoryPriorityHelper.setPriorityOrder(
                HealthDataCategory.BODY_MEASUREMENTS, newPriorityOrder);

        verifyPriorityUpdate(
                List.of(APP_PACKAGE_ID_3, APP_PACKAGE_ID_2, APP_PACKAGE_ID, APP_PACKAGE_ID_4),
                HealthDataCategory.BODY_MEASUREMENTS);
    }

    @Test
    public void testReSyncHealthDataPriorityTable_emptyListAndNoContributingApps_remainsEmpty() {
        // Setup current empty priority list
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of());
        setupPriorityList(priorityList);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    0,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(any(), any()))
                .thenReturn(List.of(mPackageInfo1, mPackageInfo2));

        mHealthDataCategoryPriorityHelper.reSyncHealthDataPriorityTable();
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.ACTIVITY))
                .isEqualTo(List.of());
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.SLEEP))
                .isEqualTo(List.of());
    }

    @Test
    public void testReSyncHealthDataPriorityTable_emptyList_populateContributingApps() {
        // Setup current empty priority list
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of());
        setupPriorityList(priorityList);

        // Setup contributor apps
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3, APP_PACKAGE_NAME_2));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    0,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(any(), any()))
                .thenReturn(List.of(mPackageInfo1, mPackageInfo2));

        mHealthDataCategoryPriorityHelper.reSyncHealthDataPriorityTable();
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.ACTIVITY))
                .isEqualTo(List.of(APP_PACKAGE_ID_2, APP_PACKAGE_ID_3));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.SLEEP))
                .isEqualTo(List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3));
    }

    @Test
    public void testReSyncHealthDataPriorityTable_oneEmptyList_leaveNonEmptyListsUnchanged() {
        // Setup current empty priority list
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.SLEEP, List.of(APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        // Setup contributor apps
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3, APP_PACKAGE_NAME_2));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    0,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(any(), any()))
                .thenReturn(List.of(mPackageInfo1, mPackageInfo2));

        mHealthDataCategoryPriorityHelper.reSyncHealthDataPriorityTable();
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.ACTIVITY))
                .isEqualTo(List.of(APP_PACKAGE_ID_2, APP_PACKAGE_ID_3));
        // Not populated by contributing apps.
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.SLEEP))
                .isEqualTo(List.of(APP_PACKAGE_ID_2));
    }

    @Test
    public void testReSyncHealthDataPriorityTable_newWritePermission_doesNotUpdateTable() {
        // Setup current priority list
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID));
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS,
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        priorityList.put(HealthDataCategory.SLEEP, List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        // Setup contributor apps
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3, APP_PACKAGE_NAME_2));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    0,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(any(), any()))
                .thenReturn(List.of(mPackageInfo1, mPackageInfo2));

        mHealthDataCategoryPriorityHelper.reSyncHealthDataPriorityTable();
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.VITALS))
                .isEqualTo(List.of());
    }

    @Test
    public void testReSyncHealthDataPriorityTable_someAppsNoDataNoPermission_removesApps() {
        // Setup current priority list
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID));
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS,
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        priorityList.put(HealthDataCategory.SLEEP, List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        // Setup contributor apps
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3, APP_PACKAGE_NAME_2));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(any(), any()))
                .thenReturn(List.of(mPackageInfo1, mPackageInfo2));

        mHealthDataCategoryPriorityHelper.reSyncHealthDataPriorityTable();
        verify(mTransactionManager)
                .delete(argThat(new DeleteRequestMatcher(HealthDataCategory.ACTIVITY)));
        // This would have been empty hence not populating with contributing apps.
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.ACTIVITY))
                .isEqualTo(List.of(APP_PACKAGE_ID_2, APP_PACKAGE_ID_3));
        verify(mTransactionManager)
                .delete(argThat(new DeleteRequestMatcher(HealthDataCategory.BODY_MEASUREMENTS)));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.BODY_MEASUREMENTS))
                .isEqualTo(List.of());
        // This was non-empty hence not populated with contributing apps.
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.SLEEP))
                .isEqualTo(List.of(APP_PACKAGE_ID_2));
    }

    @Test
    public void testReSyncHealthDataPriorityTable_noDataNorPermission_removesApps() {
        // Setup current priority list
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID));
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS,
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        priorityList.put(HealthDataCategory.SLEEP, List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    0,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(any(), any()))
                .thenReturn(List.of(mPackageInfo1, mPackageInfo2));

        mHealthDataCategoryPriorityHelper.reSyncHealthDataPriorityTable();
        verify(mTransactionManager)
                .delete(argThat(new DeleteRequestMatcher(HealthDataCategory.ACTIVITY)));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.ACTIVITY))
                .isEqualTo(List.of());

        verify(mTransactionManager)
                .delete(argThat(new DeleteRequestMatcher(HealthDataCategory.BODY_MEASUREMENTS)));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.BODY_MEASUREMENTS))
                .isEqualTo(List.of());

        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.SLEEP))
                .isEqualTo(List.of(APP_PACKAGE_ID_2));
    }

    @Test
    public void testNewReSyncHealthDataPriorityTable_ifDataForApps_doesNotRemoveApps() {
        // Setup current priority list
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID));
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS,
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        priorityList.put(HealthDataCategory.SLEEP, List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID_2));
        setupPriorityList(priorityList);

        // Setup contributor apps
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3, APP_PACKAGE_NAME_2));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    0,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(any(), any()))
                .thenReturn(List.of(mPackageInfo1, mPackageInfo2));

        mHealthDataCategoryPriorityHelper.reSyncHealthDataPriorityTable();
        verify(mTransactionManager, never())
                .insertOrReplaceOnConflict(
                        argThat(new UpsertRequestMatcher(List.of(APP_PACKAGE_ID))));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.ACTIVITY))
                .isEqualTo(List.of(APP_PACKAGE_ID));

        verify(mTransactionManager)
                .delete(argThat(new DeleteRequestMatcher(HealthDataCategory.BODY_MEASUREMENTS)));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.BODY_MEASUREMENTS))
                .isEqualTo(List.of());

        assertThat(
                        mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(
                                HealthDataCategory.SLEEP))
                .isEqualTo(List.of(APP_PACKAGE_ID_2));
    }

    @Test
    public void testMaybeAddInactiveAppsToPriorityList_ifPreferenceNotSet_addsToList() {
        when(mPreferenceHelper.getPreference(any())).thenReturn(null);

        // Setup contributor apps
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_HEART_RATE,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    0,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackagesHoldingHealthPermissions(any(), any()))
                .thenReturn(List.of(mPackageInfo1, mPackageInfo2));
        // Inactive apps {
        //   Activity -> {APP_PACKAGE_NAME, APP_PACKAGE_NAME_2, APP_PACKAGE_NAME_3}
        //   Vitals -> {APP_PACKAGE_NAME, APP_PACKAGE_NAME_3}

        // Setup current priority list
        Map<Integer, List<Long>> priorityList = new HashMap<>();
        priorityList.put(
                HealthDataCategory.BODY_MEASUREMENTS,
                List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3, APP_PACKAGE_ID_4));
        priorityList.put(HealthDataCategory.ACTIVITY, List.of(APP_PACKAGE_ID_4));
        priorityList.put(HealthDataCategory.SLEEP, List.of(APP_PACKAGE_ID_4));
        setupPriorityList(priorityList);

        mHealthDataCategoryPriorityHelper
                .addInactiveAppsWhenFirstMigratingToNewAggregationControl();
        verifyPriorityUpdate(
                List.of(APP_PACKAGE_ID_4, APP_PACKAGE_ID, APP_PACKAGE_ID_2, APP_PACKAGE_ID_3),
                HealthDataCategory.ACTIVITY);
        verifyPriorityUpdate(List.of(APP_PACKAGE_ID, APP_PACKAGE_ID_3), HealthDataCategory.VITALS);
    }

    @Test
    public void testMaybeAddInactiveAppsToPriorityList_ifPreferenceExists_doesNotAdd() {
        when(mPreferenceHelper.getPreference(any())).thenReturn(String.valueOf(true));
        mHealthDataCategoryPriorityHelper
                .addInactiveAppsWhenFirstMigratingToNewAggregationControl();
        verify(mPreferenceHelper, never()).insertOrReplacePreference(any(), any());
        verify(mTransactionManager, never())
                .insertOrReplaceOnConflict(any(UpsertTableRequest.class));
    }

    @Test
    public void testAppHasDataInCategory_forAppsWithDataInCategory_returnsTrue() {
        Map<Integer, Set<String>> recordTypesToContributingPackagesMap = new HashMap<>();
        recordTypesToContributingPackagesMap.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2));
        recordTypesToContributingPackagesMap.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributingPackagesMap.put(
                RecordTypeIdentifier.RECORD_TYPE_SLEEP_SESSION, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributingPackagesMap.put(
                RecordTypeIdentifier.RECORD_TYPE_BODY_FAT, Set.of(APP_PACKAGE_NAME_4));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributingPackagesMap);
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME, HealthDataCategory.ACTIVITY))
                .isTrue();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_2, HealthDataCategory.ACTIVITY))
                .isTrue();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_3, HealthDataCategory.ACTIVITY))
                .isTrue();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_3, HealthDataCategory.SLEEP))
                .isTrue();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_4, HealthDataCategory.BODY_MEASUREMENTS))
                .isTrue();
    }

    @Test
    public void testAppHasDataInCategory_forAppsWithoutDataInCategory_returnsFalse() {
        Map<Integer, Set<String>> recordTypesToContributingPackagesMap = new HashMap<>();
        recordTypesToContributingPackagesMap.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2));
        recordTypesToContributingPackagesMap.put(
                RecordTypeIdentifier.RECORD_TYPE_HYDRATION, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributingPackagesMap.put(
                RecordTypeIdentifier.RECORD_TYPE_HEART_RATE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributingPackagesMap.put(
                RecordTypeIdentifier.RECORD_TYPE_MENSTRUATION_FLOW,
                Set.of(APP_PACKAGE_NAME_4, APP_PACKAGE_NAME_3, APP_PACKAGE_NAME_2));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributingPackagesMap);
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_3, HealthDataCategory.ACTIVITY))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_4, HealthDataCategory.NUTRITION))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME, HealthDataCategory.VITALS))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_2, HealthDataCategory.VITALS))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_4, HealthDataCategory.VITALS))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME, HealthDataCategory.CYCLE_TRACKING))
                .isFalse();
    }

    @Test
    public void testAppHasDataInCategory_ifContributingPackagesMapEmpty_returnsFalse() {
        Map<Integer, Set<String>> recordTypesToContributingPackagesMap = new HashMap<>();

        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributingPackagesMap);
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME, HealthDataCategory.SLEEP))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME, HealthDataCategory.VITALS))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_4, HealthDataCategory.NUTRITION))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME, HealthDataCategory.VITALS))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_2, HealthDataCategory.VITALS))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME_4, HealthDataCategory.VITALS))
                .isFalse();
        assertThat(
                        mHealthDataCategoryPriorityHelper.appHasDataInCategory(
                                APP_PACKAGE_NAME, HealthDataCategory.CYCLE_TRACKING))
                .isFalse();
    }

    @Test
    public void testGetDataCategoriesWithDataForPackage_returnsCorrectCategories() {
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_HEART_RATE,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        assertThat(
                        mHealthDataCategoryPriorityHelper.getDataCategoriesWithDataForPackage(
                                APP_PACKAGE_NAME))
                .isEqualTo(Set.of(HealthDataCategory.ACTIVITY, HealthDataCategory.VITALS));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getDataCategoriesWithDataForPackage(
                                APP_PACKAGE_NAME_2))
                .isEqualTo(Set.of(HealthDataCategory.ACTIVITY, HealthDataCategory.NUTRITION));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getDataCategoriesWithDataForPackage(
                                APP_PACKAGE_NAME_3))
                .isEqualTo(Set.of(HealthDataCategory.VITALS, HealthDataCategory.ACTIVITY));
        assertThat(
                        mHealthDataCategoryPriorityHelper.getDataCategoriesWithDataForPackage(
                                APP_PACKAGE_NAME_4))
                .isEqualTo(Set.of());
    }

    @Test
    public void testGetAllContributorApps_returnsJustAppsWithData() {
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_HEART_RATE,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        Map<Integer, Set<String>> expectedResult = new HashMap<>();
        expectedResult.put(
                HealthDataCategory.ACTIVITY,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2, APP_PACKAGE_NAME_3));
        expectedResult.put(HealthDataCategory.NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        expectedResult.put(HealthDataCategory.VITALS, Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3));
        assertThat(mHealthDataCategoryPriorityHelper.getAllContributorApps())
                .isEqualTo(expectedResult);
    }

    @Test
    public void testGetAllInactiveApps_returnsApps_withDataAndNoWritePermissions() {
        // Setup contributor apps
        Map<Integer, Set<String>> recordTypesToContributorPackages = new HashMap<>();
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_STEPS,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_DISTANCE, Set.of(APP_PACKAGE_NAME_3));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_NUTRITION, Set.of(APP_PACKAGE_NAME_2));
        recordTypesToContributorPackages.put(
                RecordTypeIdentifier.RECORD_TYPE_HEART_RATE,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3));
        when(mAppInfoHelper.getRecordTypesToContributingPackagesMap())
                .thenReturn(recordTypesToContributorPackages);

        // Setup apps with write permissions
        mPackageInfo1.packageName = APP_PACKAGE_NAME;
        mPackageInfo1.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        mPackageInfo1.requestedPermissionsFlags =
                new int[] {
                    0,
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };

        mPackageInfo2.packageName = APP_PACKAGE_NAME_2;
        mPackageInfo2.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_NUTRITION,
                    HealthPermissions.WRITE_HEART_RATE,
                    HealthPermissions.READ_BLOOD_GLUCOSE
                };
        mPackageInfo2.requestedPermissionsFlags =
                new int[] {
                    0,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        when(mPackageInfoUtils.getPackageInfoWithPermissionsAsUser(
                        eq(APP_PACKAGE_NAME), any(), any()))
                .thenReturn(mPackageInfo1);
        when(mPackageInfoUtils.getPackageInfoWithPermissionsAsUser(
                        eq(APP_PACKAGE_NAME_2), any(), any()))
                .thenReturn(mPackageInfo2);

        Map<Integer, Set<String>> expectedResult = new HashMap<>();
        expectedResult.put(
                HealthDataCategory.ACTIVITY,
                Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_2, APP_PACKAGE_NAME_3));
        expectedResult.put(HealthDataCategory.VITALS, Set.of(APP_PACKAGE_NAME, APP_PACKAGE_NAME_3));
        assertThat(mHealthDataCategoryPriorityHelper.getAllInactiveApps())
                .isEqualTo(expectedResult);
    }

    private void verifyPriorityUpdate(List<Long> priorityOrder, int dataCategory) {
        verify(mTransactionManager)
                .insertOrReplaceOnConflict(argThat(new UpsertRequestMatcher(priorityOrder)));

        assertThat(mHealthDataCategoryPriorityHelper.getAppIdPriorityOrder(dataCategory))
                .isEqualTo(priorityOrder);
    }

    private static final class UpsertRequestMatcher implements ArgumentMatcher<UpsertTableRequest> {

        private final List<Long> mPriorityOrder;
        private UpsertTableRequest mRequest;

        UpsertRequestMatcher(List<Long> priorityOrder) {
            mPriorityOrder = priorityOrder;
        }

        @Override
        public boolean matches(UpsertTableRequest request) {
            mRequest = request;
            return requireNonNull(
                            request.getContentValues()
                                    .getAsString(APP_ID_PRIORITY_ORDER_COLUMN_NAME))
                    .equalsIgnoreCase(flattenLongList(mPriorityOrder));
        }

        @Override
        public String toString() {
            return "Expected Priority Order = ["
                    + flattenLongList(mPriorityOrder)
                    + "]"
                    + ", Actual Priority Order = ["
                    + requireNonNull(
                            mRequest.getContentValues()
                                    .getAsString(APP_ID_PRIORITY_ORDER_COLUMN_NAME))
                    + "]";
        }
    }

    private static final class DeleteRequestMatcher implements ArgumentMatcher<DeleteTableRequest> {

        private final int mDataCategory;
        private DeleteTableRequest mRequest;

        DeleteRequestMatcher(int dataCategory) {
            mDataCategory = dataCategory;
        }

        @Override
        public boolean matches(DeleteTableRequest request) {
            mRequest = request;
            return request != null
                    && request.getIdColumnName() != null
                    && request.getIdColumnName().equals(HEALTH_DATA_CATEGORY_COLUMN_NAME)
                    && request.getIds()
                            .equals(
                                    Collections.singletonList(
                                            StorageUtils.getNormalisedString(
                                                    String.valueOf(mDataCategory))));
        }

        @Override
        public String toString() {
            return "Expected Delete Request for category = ["
                    + mDataCategory
                    + "]"
                    + ", Actual category = ["
                    + mRequest.getIds()
                    + "]";
        }
    }

    private void setupPackageInfoWithWritePermissionGranted() {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = APP_PACKAGE_NAME;
        packageInfo.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        packageInfo.requestedPermissionsFlags =
                new int[] {0, PackageInfo.REQUESTED_PERMISSION_GRANTED, 0, 0};

        when(mPackageInfoUtils.getPackageInfoWithPermissionsAsUser(any(), any(), any()))
                .thenReturn(packageInfo);
    }

    private void setupPackageInfoWithWritePermissionNotGranted() {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = APP_PACKAGE_NAME;
        packageInfo.requestedPermissions =
                new String[] {
                    HealthPermissions.WRITE_SLEEP,
                    HealthPermissions.WRITE_STEPS,
                    HealthPermissions.READ_HEART_RATE,
                    HealthPermissions.WRITE_OVULATION_TEST
                };
        when(HealthConnectManager.isHealthPermission(any(), any())).thenReturn(true);
        packageInfo.requestedPermissionsFlags = new int[] {0, 0, 0, 0};
        when(mPackageInfoUtils.getPackageInfoWithPermissionsAsUser(any(), any(), any()))
                .thenReturn(packageInfo);
    }

    private void setupPriorityList(Map<Integer, List<Long>> priorityList) {
        List<Boolean> cursorNext = new ArrayList<>();
        List<Integer> categories = new ArrayList<>();
        List<String> priorities = new ArrayList<>();

        for (Map.Entry<Integer, List<Long>> entry : priorityList.entrySet()) {
            int dataCategory = entry.getKey();
            List<Long> priorityListForCategory = entry.getValue();
            String flattened = flattenLongList(priorityListForCategory);
            cursorNext.add(true);
            categories.add(dataCategory);
            priorities.add(flattened);
        }
        cursorNext.add(false);
        when(mCursor.moveToNext())
                .thenReturn(
                        cursorNext.get(0),
                        cursorNext.subList(1, cursorNext.size()).toArray(new Boolean[] {}));
        when(mCursor.getInt(eq(HEALTH_DATA_CATEGORY_COLUMN_INDEX)))
                .thenReturn(
                        categories.get(0),
                        categories.subList(1, categories.size()).toArray(new Integer[] {}));
        when(mCursor.getString(APP_ID_PRIORITY_ORDER_COLUMN_INDEX))
                .thenReturn(
                        priorities.get(0),
                        priorities.subList(1, priorities.size()).toArray(new String[] {}));
    }
}
