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

import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.healthconnect.internal.datatypes.InstantRecordInternal;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Parent class for all helper classes for the Instant type records
 *
 * @hide
 */
abstract class InstantRecordHelper<T extends InstantRecordInternal<?>> extends RecordHelper<T> {
    private static final String TIME_COLUMN_NAME = "time";
    private static final String ZONE_OFFSET_COLUMN_NAME = "zone_offset";

    /**
     * This implementation should return the column names with which the table should be created.
     *
     * <p>NOTE: New columns can only be added via onUpgrade. Why? Consider what happens if a table
     * already exists on the device
     *
     * <p>PLEASE DON'T USE THIS METHOD TO ADD NEW COLUMNS
     */
    @Override
    @NonNull
    final List<Pair<String, String>> getSpecificColumnInfo() {
        ArrayList<Pair<String, String>> columnInfo = new ArrayList<>();
        columnInfo.add(new Pair<>(TIME_COLUMN_NAME, INTEGER));
        columnInfo.add(new Pair<>(ZONE_OFFSET_COLUMN_NAME, INTEGER));

        columnInfo.addAll(getInstantRecordColumnInfo());

        return columnInfo;
    }

    @Override
    final void populateContentValues(
            @NonNull ContentValues contentValues, @NonNull T instantRecord) {
        contentValues.put(TIME_COLUMN_NAME, instantRecord.getTimeInMillis());
        contentValues.put(ZONE_OFFSET_COLUMN_NAME, instantRecord.getZoneOffsetInSeconds());

        populateSpecificContentValues(contentValues, instantRecord);
    }

    abstract void populateSpecificContentValues(
            @NonNull ContentValues contentValues, @NonNull T instantRecordInternal);

    /**
     * This implementation should return the column names with which the table should be created.
     *
     * <p>NOTE: New columns can only be added via onUpgrade. Why? Consider what happens if a table
     * already exists on the device
     *
     * <p>PLEASE DON'T USE THIS METHOD TO ADD NEW COLUMNS
     */
    @NonNull
    abstract List<Pair<String, String>> getInstantRecordColumnInfo();
}