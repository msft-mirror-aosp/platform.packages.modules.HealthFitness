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

package com.android.server.healthconnect.storage.datatypehelpers;

import android.content.Context;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.utils.InternalHealthConnectMappings;

/**
 * Helper class to collect Health Connect database stats for logging.
 *
 * @hide
 */
public class DatabaseStatsCollector {

    private final TransactionManager mTransactionManager;
    private final Context mContext;
    private final InternalHealthConnectMappings mInternalHealthConnectMappings =
            InternalHealthConnectMappings.getInstance();

    public DatabaseStatsCollector(TransactionManager transactionManager, Context context) {
        mTransactionManager = transactionManager;
        mContext = context;
    }

    /** Get the size of Health Connect database. */
    public long getDatabaseSize() {
        return mTransactionManager.getDatabaseSize(mContext);
    }

    /** Get the number of interval record entries in Health Connect database. */
    public long getNumberOfIntervalRecordRows() {
        long count = 0L;
        for (RecordHelper<?> recordHelper : mInternalHealthConnectMappings.getRecordHelpers()) {
            if (recordHelper instanceof IntervalRecordHelper
                    && !(recordHelper instanceof SeriesRecordHelper)) {
                count +=
                        mTransactionManager.getNumberOfEntriesInTheTable(
                                recordHelper.getMainTableName());
            }
        }
        return count;
    }

    /** Get the number of series record entries in Health Connect database. */
    public long getNumberOfSeriesRecordRows() {
        long count = 0L;
        for (RecordHelper<?> recordHelper : mInternalHealthConnectMappings.getRecordHelpers()) {
            if (recordHelper instanceof SeriesRecordHelper) {
                count +=
                        mTransactionManager.getNumberOfEntriesInTheTable(
                                recordHelper.getMainTableName());
            }
        }
        return count;
    }

    /** Get the number of instant record entries in Health Connect database. */
    public long getNumberOfInstantRecordRows() {
        long count = 0L;
        for (RecordHelper<?> recordHelper : mInternalHealthConnectMappings.getRecordHelpers()) {
            if (recordHelper instanceof InstantRecordHelper) {
                count +=
                        mTransactionManager.getNumberOfEntriesInTheTable(
                                recordHelper.getMainTableName());
            }
        }
        return count;
    }

    /** Get the number of change log entries in Health Connect database. */
    public long getNumberOfChangeLogs() {
        return mTransactionManager.getNumberOfEntriesInTheTable(ChangeLogsHelper.TABLE_NAME);
    }
}
