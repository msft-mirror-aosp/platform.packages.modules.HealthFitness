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

import static com.android.server.healthconnect.storage.utils.StorageUtils.DELIMITER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.DISTINCT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.FROM;
import static com.android.server.healthconnect.storage.utils.StorageUtils.LIMIT_SIZE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.SELECT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.SELECT_ALL;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringDef;
import android.health.connect.Constants;
import android.util.Slog;

import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;
import com.android.server.healthconnect.storage.utils.OrderByClause;
import com.android.server.healthconnect.storage.utils.SqlJoin;
import com.android.server.healthconnect.storage.utils.WhereClauses;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A request for {@link TransactionManager} to read the DB
 *
 * @hide
 */
public class ReadTableRequest {
    private static final String TAG = "HealthConnectRead";
    public static final String UNION_ALL = " UNION ALL ";
    public static final String UNION = " UNION ";

    /** @hide */
    @StringDef(
            value = {
                UNION, UNION_ALL,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UnionType {}

    private final String mTableName;
    private RecordHelper<?> mRecordHelper;
    private List<String> mColumnNames;
    private SqlJoin mJoinClause;
    private WhereClauses mWhereClauses = new WhereClauses(AND);
    private boolean mDistinct = false;
    private OrderByClause mOrderByClause = new OrderByClause();
    private String mLimitClause = "";
    private List<ReadTableRequest> mExtraReadRequests;
    private List<ReadTableRequest> mUnionReadRequests;
    private String mUnionType = UNION_ALL;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public ReadTableRequest(@NonNull String tableName) {
        Objects.requireNonNull(tableName);

        mTableName = tableName;
    }

    public RecordHelper<?> getRecordHelper() {
        return mRecordHelper;
    }

    public ReadTableRequest setRecordHelper(RecordHelper<?> recordHelper) {
        mRecordHelper = recordHelper;
        return this;
    }

    public ReadTableRequest setColumnNames(@NonNull List<String> columnNames) {
        Objects.requireNonNull(columnNames);

        mColumnNames = columnNames;
        return this;
    }

    public ReadTableRequest setWhereClause(WhereClauses whereClauses) {
        mWhereClauses = whereClauses;
        return this;
    }

    /** Used to set Join Clause for the read query */
    @NonNull
    public ReadTableRequest setJoinClause(SqlJoin joinClause) {
        mJoinClause = joinClause;
        return this;
    }

    /**
     * Use this method to enable the Distinct clause in the read command.
     *
     * <p><b>NOTE: make sure to use the {@link ReadTableRequest#setColumnNames(List)} to set the
     * column names to be used as the selection args.</b>
     */
    @NonNull
    public ReadTableRequest setDistinctClause(boolean isDistinctValuesRequired) {
        mDistinct = isDistinctValuesRequired;
        return this;
    }

    /**
     * Returns this {@link ReadTableRequest} with union type set. If not set, the default uses
     * {@link ReadTableRequest#UNION_ALL}.
     */
    @NonNull
    public ReadTableRequest setUnionType(@NonNull @UnionType String unionType) {
        Objects.requireNonNull(unionType);
        mUnionType = unionType;
        return this;
    }

    /** Returns SQL statement to perform read operation. */
    @NonNull
    public String getReadCommand() {
        String selectStatement = buildSelectStatement();

        String readQuery;
        if (mJoinClause != null) {
            String innerQuery = buildReadQuery(SELECT_ALL);
            readQuery = mJoinClause.getJoinWithQueryCommand(selectStatement, innerQuery);
        } else {
            readQuery = buildReadQuery(selectStatement);
        }

        if (Constants.DEBUG) {
            Slog.d(TAG, "read query: " + readQuery);
        }

        if (mUnionReadRequests != null && !mUnionReadRequests.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            for (ReadTableRequest unionReadRequest : mUnionReadRequests) {
                builder.append("SELECT * FROM (");
                builder.append(unionReadRequest.getReadCommand());
                builder.append(")");
                builder.append(mUnionType);
            }

            builder.append(readQuery);

            return builder.toString();
        }

        return readQuery;
    }

    @NonNull
    private String buildSelectStatement() {
        StringBuilder selectStatement = new StringBuilder(SELECT);
        if (mDistinct) {
            selectStatement.append(DISTINCT);
        }
        selectStatement.append(getColumnsToFetch());
        selectStatement.append(FROM);
        return selectStatement.toString();
    }

    @NonNull
    private String buildReadQuery(@NonNull String selectStatement) {
        return selectStatement
                + mTableName
                + mWhereClauses.get(/* withWhereKeyword */ true)
                + mOrderByClause.getOrderBy()
                + mLimitClause;
    }

    /** Get requests for populating extra data */
    @Nullable
    public List<ReadTableRequest> getExtraReadRequests() {
        return mExtraReadRequests;
    }

    /** Sets requests to populate extra data */
    public ReadTableRequest setExtraReadRequests(List<ReadTableRequest> extraDataReadRequests) {
        mExtraReadRequests = new ArrayList<>(extraDataReadRequests);
        return this;
    }

    /** Get table name of the request */
    public String getTableName() {
        return mTableName;
    }

    /** Sets order by clause for the read query */
    @NonNull
    public ReadTableRequest setOrderBy(OrderByClause orderBy) {
        mOrderByClause = orderBy;
        return this;
    }

    /** Sets LIMIT size for the read query */
    @NonNull
    public ReadTableRequest setLimit(int limit) {
        mLimitClause = LIMIT_SIZE + limit;
        return this;
    }

    private String getColumnsToFetch() {
        if (mColumnNames == null || mColumnNames.isEmpty()) {
            return "*";
        }

        return String.join(DELIMITER, mColumnNames);
    }

    /** Sets union read requests. */
    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    public ReadTableRequest setUnionReadRequests(
            @Nullable List<ReadTableRequest> unionReadRequests) {
        mUnionReadRequests = unionReadRequests;

        return this;
    }
}
