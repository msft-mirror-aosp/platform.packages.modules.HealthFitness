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

import android.database.Cursor;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.AggregateResult;
import android.health.connect.TimeRangeFilter;
import android.health.connect.TimeRangeFilterHelper;
import android.health.connect.aidl.AggregateDataRequestParcel;
import android.health.connect.aidl.AggregateDataResponseParcel;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.internal.datatypes.utils.AggregationTypeIdMapper;
import android.util.ArrayMap;

import com.android.healthfitness.flags.AconfigFlagHelper;
import com.android.healthfitness.flags.Flags;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ReadAccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Refines aggregate request from what the client sent to a format that makes the most sense for the
 * TransactionManager.
 *
 * @hide
 */
public final class AggregateTransactionRequest {
    private final String mCallingPackageName;
    private final List<AggregateTableRequest> mAggregateTableRequests;
    private final Period mPeriod;
    private final Duration mDuration;
    private final TimeRangeFilter mTimeRangeFilter;
    private final AggregationTypeIdMapper mAggregationTypeIdMapper;
    private final TransactionManager mTransactionManager;
    private final AccessLogsHelper mAccessLogsHelper;
    private final ReadAccessLogsHelper mReadAccessLogsHelper;
    private final Set<Integer> mRecordTypeIds = new HashSet<>();
    private final boolean mShouldRecordAccessLog;
    private final long mRequestTime;

    public AggregateTransactionRequest(
            String callingPackageName,
            AggregateDataRequestParcel request,
            TransactionManager transactionManager,
            AppInfoHelper appInfoHelper,
            HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper,
            AccessLogsHelper accessLogsHelper,
            ReadAccessLogsHelper readAccessLogsHelper,
            InternalHealthConnectMappings internalHealthConnectMappings,
            long startDateAccess,
            boolean shouldRecordAccessLog) {
        mCallingPackageName = callingPackageName;
        mAggregateTableRequests = new ArrayList<>(request.getAggregateIds().length);
        mTransactionManager = transactionManager;
        mAccessLogsHelper = accessLogsHelper;
        mReadAccessLogsHelper = readAccessLogsHelper;
        mPeriod = request.getPeriod();
        mDuration = request.getDuration();
        mTimeRangeFilter = request.getTimeRangeFilter();
        mShouldRecordAccessLog = shouldRecordAccessLog;

        mAggregationTypeIdMapper = AggregationTypeIdMapper.getInstance();
        for (int id : request.getAggregateIds()) {
            AggregationType<?> aggregationType = mAggregationTypeIdMapper.getAggregationTypeFor(id);
            int recordTypeId = aggregationType.getApplicableRecordTypeId();
            mRecordTypeIds.add(recordTypeId);
            RecordHelper<?> recordHelper =
                    internalHealthConnectMappings.getRecordHelper(recordTypeId);
            AggregateTableRequest aggregateTableRequest =
                    recordHelper.getAggregateTableRequest(
                            aggregationType,
                            callingPackageName,
                            request.getPackageFilters(),
                            healthDataCategoryPriorityHelper,
                            internalHealthConnectMappings,
                            appInfoHelper,
                            transactionManager,
                            request.getStartTime(),
                            request.getEndTime(),
                            startDateAccess,
                            request.useLocalTimeFilter());

            if (mDuration != null || mPeriod != null) {
                aggregateTableRequest.setGroupBy(
                        recordHelper.getDurationGroupByColumnName(),
                        mPeriod,
                        mDuration,
                        mTimeRangeFilter);
            }
            mAggregateTableRequests.add(aggregateTableRequest);
        }

        mRequestTime = Instant.now().toEpochMilli();
    }

    /**
     * @return Compute and return aggregations
     */
    public AggregateDataResponseParcel getAggregateDataResponseParcel() {
        Map<AggregationType<?>, List<AggregateResult<?>>> results = new ArrayMap<>();
        for (AggregateTableRequest aggregateTableRequest : mAggregateTableRequests) {
            populateWithAggregation(aggregateTableRequest);
            results.put(
                    aggregateTableRequest.getAggregationType(),
                    aggregateTableRequest.getAggregateResults());
        }

        // Convert DB friendly results to aggregateRecordsResponses
        int responseSize =
                mAggregateTableRequests.isEmpty()
                        ? 0
                        : mAggregateTableRequests.get(0).getAggregateResults().size();
        List<AggregateRecordsResponse<?>> aggregateRecordsResponses = new ArrayList<>(responseSize);
        for (int i = 0; i < responseSize; i++) {
            Map<Integer, AggregateResult<?>> aggregateResultMap = new ArrayMap<>();
            for (AggregationType<?> aggregationType : results.keySet()) {
                aggregateResultMap.put(
                        (mAggregationTypeIdMapper.getIdFor(aggregationType)),
                        Objects.requireNonNull(results.get(aggregationType)).get(i));
            }
            aggregateRecordsResponses.add(new AggregateRecordsResponse<>(aggregateResultMap));
        }

        // Create and return parcel
        AggregateDataResponseParcel aggregateDataResponseParcel =
                new AggregateDataResponseParcel(aggregateRecordsResponses);
        if (mPeriod != null) {
            aggregateDataResponseParcel.setPeriod(mPeriod, mTimeRangeFilter);
        } else if (mDuration != null) {
            aggregateDataResponseParcel.setDuration(mDuration, mTimeRangeFilter);
        }

        return aggregateDataResponseParcel;
    }

    // Compute aggregations and record read access log
    private void populateWithAggregation(AggregateTableRequest aggregateTableRequest) {
        mTransactionManager.runWithoutTransaction(
                db -> {
                    try (Cursor cursor =
                                    db.rawQuery(
                                            aggregateTableRequest.getAggregationCommand(), null);
                            Cursor metaDataCursor =
                                    db.rawQuery(
                                            aggregateTableRequest
                                                    .getCommandToFetchAggregateMetadata(),
                                            null)) {
                        // processResultsAndReturnContributingPackages stores the aggregation result
                        // in the aggregateTableRequest.
                        List<String> contributingPackages =
                                aggregateTableRequest.processResultsAndReturnContributingPackages(
                                        cursor, metaDataCursor);
                        if (AconfigFlagHelper.isEcosystemMetricsEnabled()
                                && mShouldRecordAccessLog) {
                            mReadAccessLogsHelper.recordAccessLogForAggregationReads(
                                    db,
                                    mCallingPackageName,
                                    /* readTimeStamp= */ mRequestTime,
                                    aggregateTableRequest.getRecordTypeId(),
                                    /* endTimeStamp= */ TimeRangeFilterHelper
                                            .getFilterEndTimeMillis(mTimeRangeFilter),
                                    contributingPackages);
                        }
                    }
                    if (Flags.addMissingAccessLogs() && mShouldRecordAccessLog) {
                        mAccessLogsHelper.recordReadAccessLog(
                                db, mCallingPackageName, mRecordTypeIds);
                    }
                });
    }
}
