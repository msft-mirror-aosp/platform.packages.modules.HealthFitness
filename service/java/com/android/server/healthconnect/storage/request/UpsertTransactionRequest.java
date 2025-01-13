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

package com.android.server.healthconnect.storage.request;

import static com.android.server.healthconnect.storage.utils.StorageUtils.addNameBasedUUIDTo;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.Nullable;
import android.health.connect.Constants;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;
import com.android.server.healthconnect.storage.utils.StorageUtils;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Refines a request from what the user sent to a format that makes the most sense for the
 * TransactionManager.
 *
 * <p>Notes, This class refines the request as well by replacing the untrusted fields with the
 * platform's trusted sources. As a part of that this class populates uuid and package name for all
 * the entries in {@param records}.
 *
 * @hide
 */
public class UpsertTransactionRequest {
    private static final String TAG = "HealthConnectUTR";

    @Nullable private final String mPackageName;
    private final List<UpsertTableRequest> mUpsertRequests = new ArrayList<>();
    @RecordTypeIdentifier.RecordType Set<Integer> mRecordTypes = new ArraySet<>();
    private final boolean mShouldGenerateAccessLogs;
    private final boolean mShouldPreferNewRecord;

    /** Create an upsert request for insert API calls. */
    public static UpsertTransactionRequest createForInsert(
            String packageName,
            List<? extends RecordInternal<?>> recordInternals,
            DeviceInfoHelper deviceInfoHelper,
            AppInfoHelper appInfoHelper,
            ArrayMap<String, Boolean> extraPermsStateMap) {
        for (RecordInternal<?> recordInternal : recordInternals) {
            // Override each record package to the given package i.e. the API caller package.
            StorageUtils.addPackageNameTo(recordInternal, packageName);
            // For insert, we should generate a fresh UUID. Don't let the client choose it.
            addNameBasedUUIDTo(recordInternal);
        }
        return new UpsertTransactionRequest(
                packageName,
                recordInternals,
                deviceInfoHelper,
                appInfoHelper,
                true /* isInsertRequest */,
                true /* shouldGenerateAccessLogs */,
                true /* shouldPreferNewRecord */,
                extraPermsStateMap);
    }

    /** Create an upsert request for update API calls. */
    public static UpsertTransactionRequest createForUpdate(
            String packageName,
            List<? extends RecordInternal<?>> recordInternals,
            DeviceInfoHelper deviceInfoHelper,
            AppInfoHelper appInfoHelper,
            ArrayMap<String, Boolean> extraPermsStateMap) {
        for (RecordInternal<?> recordInternal : recordInternals) {
            // Override each record package to the given package i.e. the API caller package.
            StorageUtils.addPackageNameTo(recordInternal, packageName);
            // For update requests, generate uuid if the clientRecordID is present, else use the
            // uuid passed as input.
            StorageUtils.updateNameBasedUUIDIfRequired(recordInternal);
        }
        return new UpsertTransactionRequest(
                packageName,
                recordInternals,
                deviceInfoHelper,
                appInfoHelper,
                false /* isInsertRequest */,
                true /* shouldGenerateAccessLogs */,
                true /* shouldPreferNewRecord */,
                extraPermsStateMap);
    }

    /** Create an upsert request for restore operation. */
    public static UpsertTransactionRequest createForRestore(
            List<? extends RecordInternal<?>> recordInternals,
            DeviceInfoHelper deviceInfoHelper,
            AppInfoHelper appInfoHelper) {
        // Ensure each record has a record id set.
        for (RecordInternal<?> recordInternal : recordInternals) {
            Objects.requireNonNull(recordInternal.getUuid());
        }
        return new UpsertTransactionRequest(
                null,
                recordInternals,
                deviceInfoHelper,
                appInfoHelper,
                true /* isInsertRequest */,
                false /* shouldGenerateAccessLogs */,
                false /* shouldPreferNewRecord */,
                null /* extraPermsStateMap */);
    }

    private UpsertTransactionRequest(
            @Nullable String packageName,
            List<? extends RecordInternal<?>> recordInternals,
            DeviceInfoHelper deviceInfoHelper,
            AppInfoHelper appInfoHelper,
            boolean isInsertRequest,
            boolean shouldGenerateAccessLogs,
            boolean shouldPreferNewRecord,
            @Nullable ArrayMap<String, Boolean> extraPermsStateMap) {
        if (shouldGenerateAccessLogs) {
            Objects.requireNonNull(packageName);
        }
        mPackageName = packageName;
        for (RecordInternal<?> recordInternal : recordInternals) {
            appInfoHelper.populateAppInfoId(recordInternal, /* requireAllFields= */ true);
            deviceInfoHelper.populateDeviceInfoId(recordInternal);
            mRecordTypes.add(recordInternal.getRecordType());
            recordInternal.setLastModifiedTime(Instant.now().toEpochMilli());
            addRequest(recordInternal, isInsertRequest, extraPermsStateMap);
        }
        mShouldGenerateAccessLogs = shouldGenerateAccessLogs;
        mShouldPreferNewRecord = shouldPreferNewRecord;
        if (!mRecordTypes.isEmpty() && Constants.DEBUG) {
            Slog.d(
                    TAG,
                    "Upsert transaction for "
                            + packageName
                            + " with size "
                            + recordInternals.size());
        }
    }

    public List<UpsertTableRequest> getUpsertRequests() {
        return mUpsertRequests;
    }

    public List<String> getUUIdsInOrder() {
        return mUpsertRequests.stream()
                .map((request) -> request.getRecordInternal().getUuid().toString())
                .collect(Collectors.toList());
    }

    private WhereClauses generateWhereClausesForUpdate(RecordInternal<?> recordInternal) {
        WhereClauses whereClauseForUpdateRequest = new WhereClauses(AND);
        whereClauseForUpdateRequest.addWhereEqualsClause(
                RecordHelper.UUID_COLUMN_NAME, StorageUtils.getHexString(recordInternal.getUuid()));
        whereClauseForUpdateRequest.addWhereEqualsClause(
                RecordHelper.APP_INFO_ID_COLUMN_NAME,
                /* expected args value */ String.valueOf(recordInternal.getAppInfoId()));
        return whereClauseForUpdateRequest;
    }

    private void addRequest(
            RecordInternal<?> recordInternal,
            boolean isInsertRequest,
            @Nullable ArrayMap<String, Boolean> extraPermsStateMap) {
        RecordHelper<?> recordHelper =
                InternalHealthConnectMappings.getInstance()
                        .getRecordHelper(recordInternal.getRecordType());
        Objects.requireNonNull(recordHelper);

        UpsertTableRequest request =
                recordHelper.getUpsertTableRequest(recordInternal, extraPermsStateMap);
        request.setRecordType(recordHelper.getRecordIdentifier());
        if (!isInsertRequest) {
            request.setUpdateWhereClauses(generateWhereClausesForUpdate(recordInternal));
        }
        request.setRecordInternal(recordInternal);
        mUpsertRequests.add(request);
    }

    // Package name can be null if we don't need to generate access log, like when we are restoring
    // data.
    public @Nullable String getPackageName() {
        return mPackageName;
    }

    public Set<Integer> getRecordTypeIds() {
        return mRecordTypes;
    }

    /** Returns whether we should be generating access logs for this request. */
    public boolean shouldGenerateAccessLogs() {
        return mShouldGenerateAccessLogs;
    }

    /**
     * Returns whether the new record being upserted should be preferred in case of existing
     * duplicate records
     */
    public boolean shouldPreferNewRecord() {
        return mShouldPreferNewRecord;
    }
}
