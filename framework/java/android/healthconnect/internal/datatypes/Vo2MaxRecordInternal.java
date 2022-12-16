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
package android.healthconnect.internal.datatypes;

import android.healthconnect.datatypes.Identifier;
import android.healthconnect.datatypes.RecordTypeIdentifier;
import android.healthconnect.datatypes.Vo2MaxRecord;
import android.healthconnect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod;
import android.os.Parcel;

import androidx.annotation.NonNull;

/**
 * @see Vo2MaxRecord
 * @hide
 */
@Identifier(recordIdentifier = RecordTypeIdentifier.RECORD_TYPE_VO2_MAX)
public final class Vo2MaxRecordInternal extends InstantRecordInternal<Vo2MaxRecord> {
    private int mMeasurementMethod;
    private double mVo2MillilitersPerMinuteKilogram;

    @Vo2MaxMeasurementMethod.Vo2MaxMeasurementMethodTypes
    public int getMeasurementMethod() {
        return mMeasurementMethod;
    }

    /** returns this object with the specified measurementMethod */
    @NonNull
    public Vo2MaxRecordInternal setMeasurementMethod(int measurementMethod) {
        this.mMeasurementMethod = measurementMethod;
        return this;
    }

    public double getVo2MillilitersPerMinuteKilogram() {
        return mVo2MillilitersPerMinuteKilogram;
    }

    /** returns this object with the specified vo2MillilitersPerMinuteKilogram */
    @NonNull
    public Vo2MaxRecordInternal setVo2MillilitersPerMinuteKilogram(
            double vo2MillilitersPerMinuteKilogram) {
        this.mVo2MillilitersPerMinuteKilogram = vo2MillilitersPerMinuteKilogram;
        return this;
    }

    @NonNull
    @Override
    public Vo2MaxRecord toExternalRecord() {
        return new Vo2MaxRecord.Builder(
                        buildMetaData(),
                        getTime(),
                        getMeasurementMethod(),
                        getVo2MillilitersPerMinuteKilogram())
                .setZoneOffset(getZoneOffset())
                .build();
    }

    @Override
    void populateInstantRecordFrom(@NonNull Parcel parcel) {
        mMeasurementMethod = parcel.readInt();
        mVo2MillilitersPerMinuteKilogram = parcel.readDouble();
    }

    @Override
    void populateInstantRecordFrom(@NonNull Vo2MaxRecord vo2MaxRecord) {
        mMeasurementMethod = vo2MaxRecord.getMeasurementMethod();
        mVo2MillilitersPerMinuteKilogram = vo2MaxRecord.getVo2MillilitersPerMinuteKilogram();
    }

    @Override
    void populateInstantRecordTo(@NonNull Parcel parcel) {
        parcel.writeInt(mMeasurementMethod);
        parcel.writeDouble(mVo2MillilitersPerMinuteKilogram);
    }
}