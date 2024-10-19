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
package com.android.server.healthconnect.backuprestore;

import static android.health.connect.Constants.DEFAULT_LONG;
import static android.health.connect.PageTokenWrapper.EMPTY_PAGE_TOKEN;

import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.backuprestore.BackupChange;
import android.health.connect.datatypes.Record;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.HealthConnectMappings;
import android.util.Pair;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.request.ReadTransactionRequest;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Performs various operations on the Health Connect database for cloud backup and restore.
 *
 * @hide
 */
public class BackupRestoreDatabaseHelper {
    private final AppInfoHelper mAppInfoHelper;
    private final TransactionManager mTransactionManager;
    private final AccessLogsHelper mAccessLogsHelper;

    private final DeviceInfoHelper mDeviceInfoHelper;
    private final HealthConnectMappings mHealthConnectMappings;
    private final InternalHealthConnectMappings mInternalHealthConnectMappings;

    // TODO: b/369799948 - maybe also allow client passes its own page size.
    @VisibleForTesting static final int MAXIMUM_PAGE_SIZE = 5000;
    private static final String TAG = "BackupRestoreDatabaseHelper";

    public BackupRestoreDatabaseHelper(
            TransactionManager transactionManager,
            AppInfoHelper appInfoHelper,
            AccessLogsHelper accessLogsHelper,
            DeviceInfoHelper deviceInfoHelper,
            HealthConnectMappings healthConnectMappings,
            InternalHealthConnectMappings internalHealthConnectMappings) {
        mTransactionManager = transactionManager;
        mAppInfoHelper = appInfoHelper;
        mAccessLogsHelper = accessLogsHelper;
        mDeviceInfoHelper = deviceInfoHelper;
        mHealthConnectMappings = healthConnectMappings;
        mInternalHealthConnectMappings = internalHealthConnectMappings;
    }

    /** Retrieve backup changes from the data tables. */
    List<BackupChange> getChangesFromDataTables() {
        //  TODO: b/369799948 - find a better approach to force the dependent data type orders
        List<Integer> recordTypes =
                mHealthConnectMappings.getRecordIdToExternalRecordClassMap().keySet().stream()
                        .toList();
        List<BackupChange> backupChanges = new ArrayList<>();

        // TODO: b/369799948 - this is wrong and only returns first 5000 records for now. Use page
        // token to resume from the previous backup point and limit the page size.
        for (var recordType : recordTypes) {
            RecordHelper<?> recordHelper =
                    mInternalHealthConnectMappings.getRecordHelper(recordType);
            Set<String> grantedExtraReadPermissions =
                    Set.copyOf(recordHelper.getExtraReadPermissions());
            ReadRecordsRequestUsingFilters<? extends Record> readRecordsRequest =
                    new ReadRecordsRequestUsingFilters.Builder<>(
                                    mHealthConnectMappings
                                            .getRecordIdToExternalRecordClassMap()
                                            .get(recordType))
                            .setPageSize(MAXIMUM_PAGE_SIZE)
                            .setPageToken(EMPTY_PAGE_TOKEN.encode())
                            .build();
            ReadTransactionRequest readTransactionRequest =
                    new ReadTransactionRequest(
                            mAppInfoHelper,
                            // TODO: b/369799948 - revisit what should be passed.
                            /* callingPackageName= */ "",
                            readRecordsRequest.toReadRecordsRequestParcel(),
                            /* startDateAccessMillis= */ DEFAULT_LONG,
                            /* enforceSelfRead= */ false,
                            grantedExtraReadPermissions,
                            // TODO: b/369799948 - copied, revisit what this means.
                            /* isInForeground= */ true);
            Pair<List<RecordInternal<?>>, PageTokenWrapper> readResult =
                    mTransactionManager.readRecordsAndPageToken(
                            readTransactionRequest,
                            mAppInfoHelper,
                            mAccessLogsHelper,
                            mDeviceInfoHelper,
                            /* shouldRecordDeleteAccessLogs= */ false);
            backupChanges.addAll(convertRecordsToBackupChange(readResult.first));
        }
        return backupChanges;
    }

    private List<BackupChange> convertRecordsToBackupChange(List<RecordInternal<?>> records) {
        return records.stream()
                .map(
                        record -> {
                            if (record.getUuid() == null) {
                                throw new IllegalStateException(
                                        "Record does not have a UUID, this should not happen");
                            }
                            return new BackupChange(
                                    record.getUuid().toString(),
                                    // TODO: b/369799948 - add proper encryption version.
                                    /* version= */ 0,
                                    /* isDeletion= */ false,
                                    serializeRecordInternal(record));
                        })
                .toList();
    }

    private static byte[] serializeRecordInternal(RecordInternal<?> recordInternal) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream =
                        new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(recordInternal);
            objectOutputStream.flush();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Slog.e(TAG, "Failed to serialize an internal record", e);
            return new byte[0];
        }
    }
}
