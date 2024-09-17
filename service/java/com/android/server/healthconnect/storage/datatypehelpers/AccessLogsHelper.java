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

import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_DELETE;
import static android.health.connect.accesslog.AccessLog.OperationType.OPERATION_TYPE_READ;

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BOOLEAN_FALSE_VALUE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.BOOLEAN_TRUE_VALUE;
import static com.android.server.healthconnect.storage.utils.StorageUtils.DELIMITER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorIntegerList;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorLong;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.accesslog.AccessLog;
import android.health.connect.accesslog.AccessLog.OperationType;
import android.health.connect.datatypes.MedicalResource.MedicalResourceType;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.util.Pair;

import com.android.healthfitness.flags.AconfigFlagHelper;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.request.AlterTableRequest;
import com.android.server.healthconnect.storage.request.CreateTableRequest;
import com.android.server.healthconnect.storage.request.DeleteTableRequest;
import com.android.server.healthconnect.storage.request.ReadTableRequest;
import com.android.server.healthconnect.storage.request.UpsertTableRequest;
import com.android.server.healthconnect.storage.utils.OrderByClause;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A helper class to fetch and store the access logs.
 *
 * @hide
 */
public final class AccessLogsHelper extends DatabaseHelper {
    public static final String TABLE_NAME = "access_logs_table";
    private static final String RECORD_TYPE_COLUMN_NAME = "record_type";
    private static final String APP_ID_COLUMN_NAME = "app_id";
    private static final String ACCESS_TIME_COLUMN_NAME = "access_time";
    private static final String OPERATION_TYPE_COLUMN_NAME = "operation_type";

    @VisibleForTesting
    static final String MEDICAL_RESOURCE_TYPE_COLUMN_NAME = "medical_resource_type";

    @VisibleForTesting
    static final String MEDICAL_DATA_SOURCE_ACCESSED_COLUMN_NAME = "medical_data_source_accessed";

    private static final int NUM_COLS = 5;
    private static final int DEFAULT_ACCESS_LOG_TIME_PERIOD_IN_DAYS = 7;

    public static CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(TABLE_NAME, getColumnInfo());
    }

    /**
     * @return AccessLog list
     */
    public static List<AccessLog> queryAccessLogs() {
        final ReadTableRequest readTableRequest = new ReadTableRequest(TABLE_NAME);

        List<AccessLog> accessLogsList = new ArrayList<>();
        final AppInfoHelper appInfoHelper = AppInfoHelper.getInstance();
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor = transactionManager.read(readTableRequest)) {
            while (cursor.moveToNext()) {
                String packageName =
                        appInfoHelper.getPackageName(getCursorLong(cursor, APP_ID_COLUMN_NAME));
                @RecordTypeIdentifier.RecordType
                List<Integer> recordTypes =
                        getCursorIntegerList(cursor, RECORD_TYPE_COLUMN_NAME, DELIMITER);
                long accessTime = getCursorLong(cursor, ACCESS_TIME_COLUMN_NAME);
                @OperationType.OperationTypes
                int operationType = getCursorInt(cursor, OPERATION_TYPE_COLUMN_NAME);
                if (!recordTypes.isEmpty()) {
                    accessLogsList.add(
                            new AccessLog(packageName, recordTypes, accessTime, operationType));
                }
                if (AconfigFlagHelper.isPersonalHealthRecordEnabled()) {
                    @MedicalResourceType
                    List<Integer> medicalResourceTypes =
                            getCursorIntegerList(
                                    cursor, MEDICAL_RESOURCE_TYPE_COLUMN_NAME, DELIMITER);
                    boolean isMedicalDataSource =
                            getCursorInt(cursor, MEDICAL_DATA_SOURCE_ACCESSED_COLUMN_NAME)
                                    == BOOLEAN_TRUE_VALUE;
                    if (!medicalResourceTypes.isEmpty() || isMedicalDataSource) {
                        accessLogsList.add(
                                new AccessLog(
                                        packageName,
                                        accessTime,
                                        operationType,
                                        new HashSet<>(medicalResourceTypes),
                                        isMedicalDataSource));
                    }
                }
            }
        }

        return accessLogsList;
    }

    /**
     * Returns the timestamp of the latest access log and {@link Long#MIN_VALUE} if there is no
     * access log.
     */
    public static long getLatestAccessLogTimeStamp() {

        final ReadTableRequest readTableRequest =
                new ReadTableRequest(TABLE_NAME)
                        .setOrderBy(
                                new OrderByClause()
                                        .addOrderByClause(ACCESS_TIME_COLUMN_NAME, false))
                        .setLimit(1);

        long mostRecentAccessTime = Long.MIN_VALUE;
        final TransactionManager transactionManager = TransactionManager.getInitialisedInstance();
        try (Cursor cursor = transactionManager.read(readTableRequest)) {
            while (cursor.moveToNext()) {
                long accessTime = getCursorLong(cursor, ACCESS_TIME_COLUMN_NAME);
                mostRecentAccessTime = Math.max(mostRecentAccessTime, accessTime);
            }
        }
        return mostRecentAccessTime;
    }

    /**
     * Adds an entry into the {@link AccessLogsHelper#TABLE_NAME} for every insert or read operation
     * request for record datatypes.
     */
    public static void addAccessLog(
            String packageName,
            @RecordTypeIdentifier.RecordType List<Integer> recordTypeList,
            @OperationType.OperationTypes int operationType) {
        UpsertTableRequest request =
                getUpsertTableRequest(packageName, recordTypeList, operationType);
        TransactionManager.getInitialisedInstance().insert(request);
    }

    /**
     * Adds an entry into the {@link AccessLogsHelper#TABLE_NAME} for every upsert/read/delete
     * operation request for medicalResourceTypes.
     */
    public static void addAccessLog(
            SQLiteDatabase db,
            String packageName,
            @MedicalResourceType Set<Integer> medicalResourceTypes,
            @OperationType.OperationTypes int operationType,
            boolean accessedMedicalDataSource) {
        UpsertTableRequest request =
                getUpsertTableRequestForPhr(
                        packageName,
                        medicalResourceTypes,
                        operationType,
                        accessedMedicalDataSource);
        TransactionManager.getInitialisedInstance().insert(db, request);
    }

    private static UpsertTableRequest getUpsertTableRequestForPhr(
            String packageName,
            Set<Integer> medicalResourceTypes,
            @OperationType.OperationTypes int operationType,
            boolean isMedicalDataSource) {
        // We need to populate RECORD_TYPE_COLUMN_NAME with an empty list, as the column is set
        // to NOT_NULL.
        ContentValues contentValues =
                populateCommonColumns(packageName, /* recordTypeList= */ List.of(), operationType);
        contentValues.put(
                MEDICAL_RESOURCE_TYPE_COLUMN_NAME, concatDataTypeIds(medicalResourceTypes));
        contentValues.put(
                MEDICAL_DATA_SOURCE_ACCESSED_COLUMN_NAME,
                isMedicalDataSource ? BOOLEAN_TRUE_VALUE : BOOLEAN_FALSE_VALUE);
        return new UpsertTableRequest(TABLE_NAME, contentValues);
    }

    public static UpsertTableRequest getUpsertTableRequest(
            String packageName,
            List<Integer> recordTypeList,
            @OperationType.OperationTypes int operationType) {
        ContentValues contentValues =
                populateCommonColumns(packageName, recordTypeList, operationType);
        return new UpsertTableRequest(TABLE_NAME, contentValues);
    }

    /** Adds an entry of read type into the {@link AccessLogsHelper#TABLE_NAME} */
    public static void recordReadAccessLog(
            SQLiteDatabase db, String packageName, Set<Integer> recordTypeIds) {
        recordAccessLog(db, packageName, recordTypeIds, OPERATION_TYPE_READ);
    }

    /** Adds an entry of delete type into the {@link AccessLogsHelper#TABLE_NAME} */
    public static void recordDeleteAccessLog(
            SQLiteDatabase db, String packageName, Set<Integer> recordTypeIds) {
        recordAccessLog(db, packageName, recordTypeIds, OPERATION_TYPE_DELETE);
    }

    private static void recordAccessLog(
            SQLiteDatabase db,
            String packageName,
            Set<Integer> recordTypeIds,
            @OperationType.OperationTypes int operationType) {
        ContentValues contentValues =
                populateCommonColumns(packageName, recordTypeIds.stream().toList(), operationType);
        UpsertTableRequest request = new UpsertTableRequest(TABLE_NAME, contentValues);
        TransactionManager.getInitialisedInstance().insertRecord(db, request);
    }

    private static ContentValues populateCommonColumns(
            String packageName,
            List<Integer> recordTypeList,
            @OperationType.OperationTypes int operationType) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(
                APP_ID_COLUMN_NAME, AppInfoHelper.getInstance().getAppInfoId(packageName));
        contentValues.put(ACCESS_TIME_COLUMN_NAME, Instant.now().toEpochMilli());
        contentValues.put(OPERATION_TYPE_COLUMN_NAME, operationType);
        contentValues.put(
                RECORD_TYPE_COLUMN_NAME, concatDataTypeIds(new HashSet<>(recordTypeList)));
        return contentValues;
    }

    private static String concatDataTypeIds(Set<Integer> dataTypes) {
        return dataTypes.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * Returns an instance of {@link DeleteTableRequest} to delete entries in access logs table
     * older than a week.
     */
    public static DeleteTableRequest getDeleteRequestForAutoDelete() {
        return new DeleteTableRequest(TABLE_NAME)
                .setTimeFilter(
                        ACCESS_TIME_COLUMN_NAME,
                        Instant.EPOCH.toEpochMilli(),
                        Instant.now()
                                .minus(DEFAULT_ACCESS_LOG_TIME_PERIOD_IN_DAYS, ChronoUnit.DAYS)
                                .toEpochMilli());
    }

    /**
     * Creates an {@link AlterTableRequest} for adding PHR specific columns, {@link
     * AccessLogsHelper#MEDICAL_RESOURCE_TYPE_COLUMN_NAME} and {@link
     * AccessLogsHelper#MEDICAL_DATA_SOURCE_ACCESSED_COLUMN_NAME} to the access_logs_table.
     */
    public static AlterTableRequest getAlterTableRequestForPhrAccessLogs() {
        return new AlterTableRequest(TABLE_NAME, getPhrAccessLogsColumnInfo());
    }

    private static List<Pair<String, String>> getColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>(NUM_COLS);
        columnInfo.add(new Pair<>(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT));
        columnInfo.add(new Pair<>(APP_ID_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(RECORD_TYPE_COLUMN_NAME, TEXT_NOT_NULL));
        columnInfo.add(new Pair<>(ACCESS_TIME_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(OPERATION_TYPE_COLUMN_NAME, INTEGER_NOT_NULL));
        return columnInfo;
    }

    /** Gets the columns to add for an {@link AlterTableRequest} for adding PHR specific columns, */
    public static List<Pair<String, String>> getPhrAccessLogsColumnInfo() {
        return List.of(
                // This is list of comma separated integers that represent
                // the medicalResourceTypes accessed.
                Pair.create(MEDICAL_RESOURCE_TYPE_COLUMN_NAME, TEXT_NULL),
                // This represents a boolean, which tells us whether
                // the MedicalDataSource data is accessed.
                Pair.create(MEDICAL_DATA_SOURCE_ACCESSED_COLUMN_NAME, INTEGER));
    }

    @Override
    protected String getMainTableName() {
        return TABLE_NAME;
    }
}
