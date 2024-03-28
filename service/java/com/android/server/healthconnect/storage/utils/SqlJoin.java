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

package com.android.server.healthconnect.storage.utils;

import static com.android.server.healthconnect.storage.utils.StorageUtils.SELECT_ALL;

import android.annotation.NonNull;
import android.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents SQL join. Default join type is INNER join.
 *
 * @hide
 */
public final class SqlJoin {
    public static final String SQL_JOIN_INNER = "INNER";
    public static final String SQL_JOIN_LEFT = "LEFT";

    private static final String INNER_QUERY_ALIAS = "inner_query_result";

    /** @hide */
    @StringDef(
            value = {
                SQL_JOIN_INNER,
                SQL_JOIN_LEFT,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JoinType {}

    private final String mSelfTableName;
    private final String mTableNameToJoinOn;
    private final String mSelfColumnNameToMatch;
    private final String mJoiningColumnNameToMatch;

    private List<SqlJoin> mAttachedJoins;
    private String mJoinType = SQL_JOIN_INNER;

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private WhereClauses mTableToJoinWhereClause = null;

    @SuppressWarnings("NullAway.Init") // TODO(b/317029272): fix this suppression
    public SqlJoin(
            String selfTableName,
            String tableNameToJoinOn,
            String selfColumnNameToMatch,
            String joiningColumnNameToMatch) {
        mSelfTableName = selfTableName;
        mTableNameToJoinOn = tableNameToJoinOn;
        mSelfColumnNameToMatch = selfColumnNameToMatch;
        mJoiningColumnNameToMatch = joiningColumnNameToMatch;
    }

    /**
     * Sets join type to the current joint, default value is inner join. Returns class with join
     * type set.
     */
    public SqlJoin setJoinType(@NonNull @JoinType String joinType) {
        Objects.requireNonNull(joinType);
        mJoinType = joinType;
        return this;
    }

    /**
     * Returns query by applying JOIN condition on the innerQuery
     *
     * @param innerQuery An inner query to be used for the JOIN
     * @return Final query with JOIN condition
     */
    public String getJoinWithQueryCommand(String innerQuery) {
        if (innerQuery == null) {
            throw new IllegalArgumentException("Inner query cannot be null");
        }
        return SELECT_ALL
                + "( "
                + innerQuery
                + " ) AS "
                + INNER_QUERY_ALIAS
                + " "
                + getJoinCommand(/* withInnerQuery= */ true);
    }

    /** Returns join command. */
    public String getJoinCommand() {
        return getJoinCommand(/* withInnerQuery= */ false);
    }

    /** Attaches another join to this join. Returns this class with another join attached. */
    public SqlJoin attachJoin(@NonNull SqlJoin join) {
        Objects.requireNonNull(join);

        if (mAttachedJoins == null) {
            mAttachedJoins = new ArrayList<>();
        }

        mAttachedJoins.add(join);
        return this;
    }

    public void setSecondTableWhereClause(WhereClauses whereClause) {
        mTableToJoinWhereClause = whereClause;
    }

    private String getJoinCommand(boolean withInnerQuery) {
        String selfColumnPrefix = withInnerQuery ? INNER_QUERY_ALIAS + "." : mSelfTableName + ".";
        return " "
                + mJoinType
                + " JOIN "
                + (mTableToJoinWhereClause == null ? "" : "( " + buildFilterQuery() + ") ")
                + mTableNameToJoinOn
                + " ON "
                + selfColumnPrefix
                + mSelfColumnNameToMatch
                + " = "
                + mTableNameToJoinOn
                + "."
                + mJoiningColumnNameToMatch
                + buildAttachedJoinsCommand(withInnerQuery);
    }

    private String buildFilterQuery() {
        return SELECT_ALL + mTableNameToJoinOn + mTableToJoinWhereClause.get(true);
    }

    private String buildAttachedJoinsCommand(boolean withInnerQuery) {
        if (mAttachedJoins == null) {
            return "";
        }

        StringBuilder command = new StringBuilder();
        for (SqlJoin join : mAttachedJoins) {
            if (withInnerQuery && join.mSelfTableName.equals(mSelfTableName)) {
                // When we're joining from the top level table, and there is an inner query, use the
                // inner query prefix.
                command.append(" ").append(join.getJoinCommand(true));
            } else {
                // Otherwise use the table name itself.
                command.append(" ").append(join.getJoinCommand(false));
            }
        }

        return command.toString();
    }
}
