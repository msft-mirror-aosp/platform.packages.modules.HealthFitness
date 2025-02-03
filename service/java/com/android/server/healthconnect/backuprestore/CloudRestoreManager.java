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

import static com.android.healthfitness.flags.Flags.FLAG_CLOUD_BACKUP_AND_RESTORE;
import static com.android.server.healthconnect.backuprestore.RecordProtoConverter.PROTO_VERSION;

import android.annotation.FlaggedApi;
import android.health.connect.backuprestore.BackupSettings;
import android.health.connect.backuprestore.RestoreChange;
import android.health.connect.internal.datatypes.RecordInternal;
import android.util.Slog;

import com.android.server.healthconnect.proto.backuprestore.BackupData;
import com.android.server.healthconnect.proto.backuprestore.Record;
import com.android.server.healthconnect.proto.backuprestore.Settings;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;
import com.android.server.healthconnect.storage.request.UpsertTransactionRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages Cloud Restore operations.
 *
 * @hide
 */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
public class CloudRestoreManager {

    private static final String TAG = "CloudRestoreManager";

    private final TransactionManager mTransactionManager;
    private final DeviceInfoHelper mDeviceInfoHelper;
    private final AppInfoHelper mAppInfoHelper;
    private final RecordProtoConverter mRecordProtoConverter;
    private final HealthDataCategoryPriorityHelper mPriorityHelper;
    private final PreferenceHelper mPreferenceHelper;

    public CloudRestoreManager(
            TransactionManager transactionManager,
            DeviceInfoHelper deviceInfoHelper,
            AppInfoHelper appInfoHelper,
            HealthDataCategoryPriorityHelper priorityHelper,
            PreferenceHelper preferenceHelper) {
        mTransactionManager = transactionManager;
        mDeviceInfoHelper = deviceInfoHelper;
        mAppInfoHelper = appInfoHelper;
        mRecordProtoConverter = new RecordProtoConverter();
        mPriorityHelper = priorityHelper;
        mPreferenceHelper = preferenceHelper;
    }

    /** Takes the serialized user settings and overwrites existing settings. */
    public void pushSettingsForRestore(BackupSettings newSettings) {
        Slog.i(TAG, "Restoring user settings.");
        BackupSettingsHelper backupSettingsHelper =
                new BackupSettingsHelper(mPriorityHelper, mPreferenceHelper, mAppInfoHelper);

        byte[] data = newSettings.getData();
        try {
            backupSettingsHelper.restoreUserSettings(Settings.parseFrom(data));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to parse BackupSettings object back into"
                            + "Settings Record. Details: ",
                    e.getCause());
        }
    }

    /** Checks whether data with a certain version could be restored. */
    public boolean canRestore(int dataVersion) {
        return dataVersion <= PROTO_VERSION;
    }

    /** Restores backup data changes. */
    public void pushChangesForRestore(List<RestoreChange> changes) {
        List<Record> records = changes.stream().map(this::toRecord).toList();
        UpsertTransactionRequest upsertRequest =
                UpsertTransactionRequest.createForRestore(
                        records.stream().map(this::toRecordInternal).toList(),
                        mDeviceInfoHelper,
                        mAppInfoHelper);
        mTransactionManager.insertAllRecords(mAppInfoHelper, null, upsertRequest);

        records.stream()
                .collect(
                        Collectors.groupingBy(
                                Record::getPackageName,
                                LinkedHashMap::new,
                                Collectors.mapping(
                                        mRecordProtoConverter::getRecordTypeId,
                                        Collectors.toSet())))
                .forEach(
                        (packageName, recordTypes) ->
                                mAppInfoHelper.updateAppInfoRecordTypesUsedOnInsert(
                                        recordTypes, packageName));
    }

    private Record toRecord(RestoreChange backupChange) {
        try {
            return BackupData.parseFrom(backupChange.getData()).getRecord();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private RecordInternal<?> toRecordInternal(Record record) {
        try {
            return mRecordProtoConverter.toRecordInternal(record);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
