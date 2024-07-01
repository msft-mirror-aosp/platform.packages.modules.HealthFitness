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

import static com.android.server.healthconnect.storage.HealthConnectDatabase.createTable;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorString;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.MindfulnessSessionRecordInternal;
import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Helper class for {@link android.health.connect.datatypes.MindfulnessSessionRecord}.
 *
 * @hide
 */
public class MindfulnessSessionRecordHelper
        extends IntervalRecordHelper<MindfulnessSessionRecordInternal> {

    private static final String TABLE_NAME = "mindfulness_session_record_table";
    private static final String TYPE_COLUMN_NAME = "type";
    private static final String TITLE_COLUMN_NAME = "title";
    private static final String NOTES_COLUMN_NAME = "notes";

    public MindfulnessSessionRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_MINDFULNESS_SESSION);
    }

    @Override
    void populateSpecificRecordValue(
            @NonNull Cursor cursor, @NonNull MindfulnessSessionRecordInternal recordInternal) {
        recordInternal.setMindfulnessSessionType(getCursorInt(cursor, TYPE_COLUMN_NAME));
        recordInternal.setTitle(getCursorString(cursor, TITLE_COLUMN_NAME));
        recordInternal.setNotes(getCursorString(cursor, NOTES_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            @NonNull ContentValues contentValues,
            @NonNull MindfulnessSessionRecordInternal recordInternal) {
        contentValues.put(TYPE_COLUMN_NAME, recordInternal.getMindfulnessSessionType());
        contentValues.put(TITLE_COLUMN_NAME, recordInternal.getTitle());
        contentValues.put(NOTES_COLUMN_NAME, recordInternal.getNotes());
    }

    @NonNull
    @Override
    List<Pair<String, String>> getIntervalRecordColumnInfo() {
        return List.of(
                Pair.create(TYPE_COLUMN_NAME, INTEGER),
                Pair.create(TITLE_COLUMN_NAME, TEXT_NULL),
                Pair.create(NOTES_COLUMN_NAME, TEXT_NULL));
    }

    @NonNull
    @Override
    public String getMainTableName() {
        return TABLE_NAME;
    }

    /** Creates the mindfulness session table. */
    public void applyMindfulnessSessionUpgrade(@NonNull SQLiteDatabase db) {
        createTable(db, getCreateTableRequest());
    }
}
