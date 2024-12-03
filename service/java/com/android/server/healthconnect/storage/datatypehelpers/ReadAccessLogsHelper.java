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
package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.storage.datatypehelpers.RecordHelper.PRIMARY_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER_NOT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.PRIMARY_AUTOINCREMENT;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NOT_NULL;

import android.util.Pair;

import com.android.server.healthconnect.storage.request.CreateTableRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Table to maintain detailed read access logs.
 *
 * @hide
 */
public class ReadAccessLogsHelper {

    public static final String TABLE_NAME = "read_access_logs_table";
    private static final int NUM_COLS = 6;
    private static final String RECORD_TYPE_COLUMN_NAME = "record_type";
    private static final String READER_APP_ID_COLUMN_NAME = "reader_app_id";
    private static final String WRITER_APP_ID_COLUMN_NAME = "writer_app_id";
    private static final String READ_TIME = "read_time";
    private static final String WRITE_TIME = "write_time";

    protected String getMainTableName() {
        return TABLE_NAME;
    }

    public static CreateTableRequest getCreateTableRequest() {
        return new CreateTableRequest(TABLE_NAME, getColumnInfo())
                .addForeignKey(
                        /* referencedTable= */ AppInfoHelper.TABLE_NAME,
                        /* columnNames= */ List.of(READER_APP_ID_COLUMN_NAME),
                        /* referencedColumnNames= */ List.of(PRIMARY_COLUMN_NAME))
                .addForeignKey(
                        /* referencedTable= */ AppInfoHelper.TABLE_NAME,
                        /* columnNames= */ List.of(WRITER_APP_ID_COLUMN_NAME),
                        /* referencedColumnNames= */ List.of(PRIMARY_COLUMN_NAME));
    }

    private static List<Pair<String, String>> getColumnInfo() {
        List<Pair<String, String>> columnInfo = new ArrayList<>(NUM_COLS);
        columnInfo.add(new Pair<>(PRIMARY_COLUMN_NAME, PRIMARY_AUTOINCREMENT));
        columnInfo.add(new Pair<>(READER_APP_ID_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(WRITER_APP_ID_COLUMN_NAME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(RECORD_TYPE_COLUMN_NAME, TEXT_NOT_NULL));
        columnInfo.add(new Pair<>(READ_TIME, INTEGER_NOT_NULL));
        columnInfo.add(new Pair<>(WRITE_TIME, INTEGER_NOT_NULL));
        return columnInfo;
    }
}
