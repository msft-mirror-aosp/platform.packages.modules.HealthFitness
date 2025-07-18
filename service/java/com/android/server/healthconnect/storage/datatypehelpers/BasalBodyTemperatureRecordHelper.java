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
import static com.android.server.healthconnect.storage.utils.StorageUtils.REAL;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorDouble;
import static com.android.server.healthconnect.storage.utils.StorageUtils.getCursorInt;

import android.content.ContentValues;
import android.database.Cursor;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.BasalBodyTemperatureRecordInternal;
import android.util.Pair;

import java.util.Arrays;
import java.util.List;

/**
 * Helper class for BasalBodyTemperatureRecord.
 *
 * @hide
 */
public final class BasalBodyTemperatureRecordHelper
        extends InstantRecordHelper<BasalBodyTemperatureRecordInternal> {
    private static final String BASAL_BODY_TEMPERATURE_RECORD_TABLE_NAME =
            "basal_body_temperature_record_table";
    private static final String MEASUREMENT_LOCATION_COLUMN_NAME = "measurement_location";
    private static final String TEMPERATURE_COLUMN_NAME = "temperature";

    public BasalBodyTemperatureRecordHelper() {
        super(RecordTypeIdentifier.RECORD_TYPE_BASAL_BODY_TEMPERATURE);
    }

    @Override
    public String getMainTableName() {
        return BASAL_BODY_TEMPERATURE_RECORD_TABLE_NAME;
    }

    @Override
    void populateSpecificRecordValue(
            Cursor cursor, BasalBodyTemperatureRecordInternal basalBodyTemperatureRecord) {
        basalBodyTemperatureRecord.setMeasurementLocation(
                getCursorInt(cursor, MEASUREMENT_LOCATION_COLUMN_NAME));
        basalBodyTemperatureRecord.setTemperature(getCursorDouble(cursor, TEMPERATURE_COLUMN_NAME));
    }

    @Override
    void populateSpecificContentValues(
            ContentValues contentValues,
            BasalBodyTemperatureRecordInternal basalBodyTemperatureRecord) {
        contentValues.put(
                MEASUREMENT_LOCATION_COLUMN_NAME,
                basalBodyTemperatureRecord.getMeasurementLocation());
        contentValues.put(TEMPERATURE_COLUMN_NAME, basalBodyTemperatureRecord.getTemperature());
    }

    @Override
    protected List<Pair<String, String>> getInstantRecordColumnInfo() {
        return Arrays.asList(
                new Pair<>(MEASUREMENT_LOCATION_COLUMN_NAME, INTEGER),
                new Pair<>(TEMPERATURE_COLUMN_NAME, REAL));
    }
}
