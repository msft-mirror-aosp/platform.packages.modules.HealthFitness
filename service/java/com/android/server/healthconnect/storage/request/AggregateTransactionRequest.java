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

import android.health.connect.AggregateRecordsResponse;
import android.health.connect.AggregateResult;
import android.health.connect.TimeRangeFilter;
import android.health.connect.aidl.AggregateDataRequestParcel;
import android.health.connect.aidl.AggregateDataResponseParcel;
import android.health.connect.datatypes.AggregationType;
import android.health.connect.internal.datatypes.utils.AggregationTypeIdMapper;
import android.util.ArrayMap;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthDataCategoryPriorityHelper;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

import java.time.Duration;
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
    private final String mPackageName;
    private final List<AggregateTableRequest> mAggregateTableRequests;
    private final Period mPeriod;
    private final Duration mDuration;
    private final TimeRangeFilter mTimeRangeFilter;
    private final AggregationTypeIdMapper mAggregationTypeIdMapper;
    private final TransactionManager mTransactionManager;
    private final Set<Integer> mRecordTypeIds = new HashSet<>();

    public AggregateTransactionRequest(
            AppInfoHelper appInfoHelper,
            String packageName,
            AggregateDataRequestParcel request,
            HealthDataCategoryPriorityHelper healthDataCategoryPriorityHelper,
            InternalHealthConnectMappings internalHealthConnectMappings,
            TransactionManager transactionManager,
            long startDateAccess) {
        mPackageName = packageName;
        mAggregateTableRequests = new ArrayList<>(request.getAggregateIds().length);
        mPeriod = request.getPeriod();
        mDuration = request.getDuration();
        mTimeRangeFilter = request.getTimeRangeFilter();
        mAggregationTypeIdMapper = AggregationTypeIdMapper.getInstance();
        mTransactionManager = transactionManager;
        for (int id : request.getAggregateIds()) {
            AggregationType<?> aggregationType = mAggregationTypeIdMapper.getAggregationTypeFor(id);
            int recordTypeId = aggregationType.getApplicableRecordTypeId();
            mRecordTypeIds.add(recordTypeId);
            RecordHelper<?> recordHelper =
                    internalHealthConnectMappings.getRecordHelper(recordTypeId);
            AggregateTableRequest aggregateTableRequest =
                    recordHelper.getAggregateTableRequest(
                            aggregationType,
                            packageName,
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
    }

    public String getPackageName() {
        return mPackageName;
    }

    /**
     * @return Compute and return aggregations
     */
    public AggregateDataResponseParcel getAggregateDataResponseParcel(
            AccessLogsHelper accessLogsHelper, boolean shouldRecordAccessLog) {
        Map<AggregationType<?>, List<AggregateResult<?>>> results = new ArrayMap<>();
        for (AggregateTableRequest aggregateTableRequest : mAggregateTableRequests) {
            // Compute aggregations and record read access log
            mTransactionManager.populateWithAggregation(
                    aggregateTableRequest,
                    mPackageName,
                    mRecordTypeIds,
                    accessLogsHelper,
                    shouldRecordAccessLog);
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
}
