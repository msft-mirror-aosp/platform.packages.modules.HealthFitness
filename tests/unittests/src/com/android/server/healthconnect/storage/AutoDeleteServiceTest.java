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

package healthconnect.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.exportimport.ExportManager;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.AutoDeleteService;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class AutoDeleteServiceTest {
    private static final String AUTO_DELETE_DURATION_RECORDS_KEY =
            "auto_delete_duration_records_key";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(PreferenceHelper.class)
                    .mockStatic(TransactionManager.class)
                    .mockStatic(AppInfoHelper.class)
                    .mockStatic(ActivityDateHelper.class)
                    .build();

    @Mock private PreferenceHelper mPreferenceHelper;
    @Mock private TransactionManager mTransactionManager;
    @Mock private ExportManager mExportManager;

    @Mock private AppInfoHelper mAppInfoHelper;
    @Mock private HealthDataCategoryPriorityHelper mHealthDataCategoryPriorityHelper;
    @Mock private ActivityDateHelper mActivityDateHelper;
    @Mock Context mContext;

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    private HealthConnectInjector mHealthConnectInjector;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getUser()).thenReturn(Process.myUserHandle());
        mHealthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(mContext)
                        .setHealthDataCategoryPriorityHelper(mHealthDataCategoryPriorityHelper)
                        .setPreferenceHelper(mPreferenceHelper)
                        .setExportManager(mExportManager)
                        .setTransactionManager(mTransactionManager)
                        .setAppInfoHelper(mAppInfoHelper)
                        .setActivityDateHelper(mActivityDateHelper)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .build();
    }

    @Test
    public void testSetRecordRetentionPeriodInDays() {
        AutoDeleteService.setRecordRetentionPeriodInDays(
                30, mHealthConnectInjector.getPreferenceHelper());

        verify(mPreferenceHelper)
                .insertOrReplacePreference(
                        Mockito.eq(AUTO_DELETE_DURATION_RECORDS_KEY),
                        Mockito.eq(String.valueOf(30)));
    }

    @Test
    public void testStartAutoDelete_getPreferenceReturnNull() {
        when(TransactionManager.getInitialisedInstance()).thenReturn(mTransactionManager);
        when(mPreferenceHelper.getPreference(AUTO_DELETE_DURATION_RECORDS_KEY)).thenReturn(null);

        AutoDeleteService.startAutoDelete(
                mContext,
                mHealthConnectInjector.getHealthDataCategoryPriorityHelper(),
                mHealthConnectInjector.getPreferenceHelper(),
                mHealthConnectInjector.getAppInfoHelper(),
                mHealthConnectInjector.getTransactionManager(),
                mHealthConnectInjector.getAccessLogsHelper(),
                mHealthConnectInjector.getActivityDateHelper());

        verify(mTransactionManager, Mockito.times(2))
                .deleteWithoutChangeLogs(
                        Mockito.argThat(this::checkTableNames_getPreferenceReturnNull));
        verify(mAppInfoHelper).syncAppInfoRecordTypesUsed();
        verify(mHealthDataCategoryPriorityHelper).reSyncHealthDataPriorityTable(mContext);
        verify(mActivityDateHelper, times(1)).reSyncForAllRecords();
    }

    @Test
    public void testStartAutoDelete_getPreferenceReturnNonNull() {
        when(TransactionManager.getInitialisedInstance()).thenReturn(mTransactionManager);

        when(mPreferenceHelper.getPreference(AUTO_DELETE_DURATION_RECORDS_KEY))
                .thenReturn(String.valueOf(30));

        AutoDeleteService.startAutoDelete(
                mContext,
                mHealthConnectInjector.getHealthDataCategoryPriorityHelper(),
                mHealthConnectInjector.getPreferenceHelper(),
                mHealthConnectInjector.getAppInfoHelper(),
                mHealthConnectInjector.getTransactionManager(),
                mHealthConnectInjector.getAccessLogsHelper(),
                mHealthConnectInjector.getActivityDateHelper());

        verify(mTransactionManager, Mockito.times(2))
                .deleteWithoutChangeLogs(
                        Mockito.argThat(this::checkTableNames_getPreferenceReturnNonNull));
        verify(mTransactionManager)
                .deleteAll(
                        Mockito.argThat(
                                request ->
                                        checkTableNames_getPreferenceReturnNonNull(
                                                request.getDeleteTableRequests())),
                        Mockito.booleanThat(
                                shouldRecordDeleteAccessLog -> !shouldRecordDeleteAccessLog),
                        any());
        verify(mAppInfoHelper).syncAppInfoRecordTypesUsed();
        verify(mHealthDataCategoryPriorityHelper).reSyncHealthDataPriorityTable(mContext);
        verify(mActivityDateHelper, times(1)).reSyncForAllRecords();
    }

    private boolean checkTableNames_getPreferenceReturnNull(List<DeleteTableRequest> list) {
        Set<String> tableNames = new HashSet<>();
        for (DeleteTableRequest request : list) {
            tableNames.add(request.getTableName());
        }
        return (tableNames.equals(getTableNamesForDeletingStaleChangeLogEntries())
                || tableNames.equals(getTableNamesForDeletingStaleAccessLogsEntries()));
    }

    private boolean checkTableNames_getPreferenceReturnNonNull(List<DeleteTableRequest> list) {
        Set<String> tableNames = new HashSet<>();
        for (DeleteTableRequest request : list) {
            tableNames.add(request.getTableName());
        }
        return (tableNames.equals(getTableNamesForDeletingStaleChangeLogEntries())
                || tableNames.equals(getTableNamesForDeletingStaleAccessLogsEntries())
                || tableNames.equals(getTableNamesForDeletingStaleRecordEntries()));
    }

    List<DeleteTableRequest> getDeleteTableRequests(int recordAutoDeletePeriod) {
        List<DeleteTableRequest> deleteTableRequests = new ArrayList<>();

        mHealthConnectInjector
                .getInternalHealthConnectMappings()
                .getRecordHelpers()
                .forEach(
                        (recordHelper) -> {
                            DeleteTableRequest request =
                                    recordHelper.getDeleteRequestForAutoDelete(
                                            recordAutoDeletePeriod);
                            deleteTableRequests.add(request);
                        });

        return deleteTableRequests;
    }

    Set<String> getTableNamesForDeletingStaleRecordEntries() {
        Set<String> tableNames = new HashSet<>();

        for (DeleteTableRequest deleteTableRequest : getDeleteTableRequests(30)) {
            tableNames.add(deleteTableRequest.getTableName());
        }

        return tableNames;
    }

    Set<String> getTableNamesForDeletingStaleChangeLogEntries() {
        Set<String> tableNames = new HashSet<>();

        tableNames.add(ChangeLogsHelper.getDeleteRequestForAutoDelete().getTableName());
        tableNames.add(ChangeLogsRequestHelper.getDeleteRequestForAutoDelete().getTableName());

        return tableNames;
    }

    Set<String> getTableNamesForDeletingStaleAccessLogsEntries() {
        Set<String> tableNames = new HashSet<>();

        tableNames.add(AccessLogsHelper.getDeleteRequestForAutoDelete().getTableName());

        return tableNames;
    }
}
