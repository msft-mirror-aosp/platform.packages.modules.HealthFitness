/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.healthconnect.storage;

import android.content.Context;
import android.util.Slog;

import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ActivityDateHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsRequestHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTransactionRequest;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

import java.util.ArrayList;
import java.util.List;

/**
 * A service that is run periodically to handle deletion of stale entries in HC DB.
 *
 * @hide
 */
// TODO(b/368073286): Add a constructor here (and remove service from the name).
public class AutoDeleteService {
    private static final String AUTO_DELETE_DURATION_RECORDS_KEY =
            "auto_delete_duration_records_key";
    private static final String TAG = "HealthConnectAutoDelete";

    /** Gets auto delete period for automatically deleting record entries */
    public static int getRecordRetentionPeriodInDays(PreferenceHelper preferenceHelper) {
        String result = preferenceHelper.getPreference(AUTO_DELETE_DURATION_RECORDS_KEY);

        if (result == null) return 0;
        return Integer.parseInt(result);
    }

    /** Sets auto delete period for automatically deleting record entries */
    public static void setRecordRetentionPeriodInDays(int days, PreferenceHelper preferenceHelper) {
        preferenceHelper.insertOrReplacePreference(
                AUTO_DELETE_DURATION_RECORDS_KEY, String.valueOf(days));
    }

    /** Starts the Auto Deletion process. */
    public static void startAutoDelete(
            Context context,
            HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper,
            PreferenceHelper preferenceHelper,
            AppInfoHelper appInfoHelper,
            TransactionManager transactionManager,
            AccessLogsHelper accessLogsHelper,
            ActivityDateHelper activityDateHelper) {
        try {
            // Only do transactional operations here - as this job might get cancelled for several
            // reasons, such as: User switch, low battery etc.
            deleteStaleRecordEntries(preferenceHelper, transactionManager, accessLogsHelper);
            deleteStaleChangeLogEntries(transactionManager);
            deleteStaleAccessLogEntries(transactionManager);
            // Update the recordTypesUsed by packages if required after the deletion of records.
            appInfoHelper.syncAppInfoRecordTypesUsed();
            // Re-sync activity dates table
            activityDateHelper.reSyncForAllRecords();
            // Sync health data priority list table
            healthDataCategoryPriorityHelper.reSyncHealthDataPriorityTable();
        } catch (Exception e) {
            Slog.e(TAG, "Auto delete run failed", e);
            // Don't rethrow as that will crash system_server
        }
    }

    private static void deleteStaleRecordEntries(
            PreferenceHelper preferenceHelper,
            TransactionManager transactionManager,
            AccessLogsHelper accessLogsHelper) {
        String recordAutoDeletePeriodString =
                preferenceHelper.getPreference(AUTO_DELETE_DURATION_RECORDS_KEY);
        int recordAutoDeletePeriod =
                recordAutoDeletePeriodString == null
                        ? 0
                        : Integer.parseInt(recordAutoDeletePeriodString);
        if (recordAutoDeletePeriod != 0) {
            // 0 represents that no period is set,to delete only if not 0 else don't do anything
            List<DeleteTableRequest> deleteTableRequests = new ArrayList<>();
            InternalHealthConnectMappings.getInstance()
                    .getRecordHelpers()
                    .forEach(
                            (recordHelper) -> {
                                DeleteTableRequest request =
                                        recordHelper.getDeleteRequestForAutoDelete(
                                                recordAutoDeletePeriod);
                                deleteTableRequests.add(request);
                            });
            try {
                transactionManager.deleteAll(
                        new DeleteTransactionRequest(deleteTableRequests),
                        /* shouldRecordDeleteAccessLogs= */ false,
                        accessLogsHelper);
            } catch (Exception exception) {
                Slog.e(TAG, "Auto delete for records failed", exception);
                // Don't rethrow as that will crash system_server
            }
        }
    }

    private static void deleteStaleChangeLogEntries(TransactionManager transactionManager) {
        try {
            transactionManager.deleteWithoutChangeLogs(
                    List.of(
                            ChangeLogsHelper.getDeleteRequestForAutoDelete(),
                            ChangeLogsRequestHelper.getDeleteRequestForAutoDelete()));
        } catch (Exception exception) {
            Slog.e(TAG, "Auto delete for Change logs failed", exception);
            // Don't rethrow as that will crash system_server
        }
    }

    private static void deleteStaleAccessLogEntries(TransactionManager transactionManager) {
        try {
            transactionManager.deleteWithoutChangeLogs(
                    List.of(AccessLogsHelper.getDeleteRequestForAutoDelete()));
        } catch (Exception exception) {
            Slog.e(TAG, "Auto delete for Access logs failed", exception);
            // Don't rethrow as that will crash system_server
        }
    }
}
