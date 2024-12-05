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

package android.healthconnect.internal.datatypes.utils;

import static android.health.connect.datatypes.BloodPressureRecord.BloodPressureMeasurementLocation.BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_WRIST;
import static android.health.connect.datatypes.BloodPressureRecord.BodyPosition.BODY_POSITION_LYING_DOWN;
import static android.health.connect.datatypes.Device.DEVICE_TYPE_PHONE;
import static android.health.connect.datatypes.Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.internal.datatypes.utils.RecordProtoConverter;
import android.health.connect.proto.backuprestore.BloodPressure;
import android.health.connect.proto.backuprestore.InstantRecord;
import android.health.connect.proto.backuprestore.IntervalRecord;
import android.health.connect.proto.backuprestore.Record;
import android.health.connect.proto.backuprestore.Steps;

import org.junit.Test;

import java.util.UUID;

public class RecordProtoConverterTest {

    // TODO: b/369800543 - Once all data types are implemented add a test that checks whether all
    // types defined in HealthConnectMappings are covered.

    RecordProtoConverter mConverter = new RecordProtoConverter();

    @Test
    public void convertSetValues_intervalRecord() throws Exception {
        IntervalRecord intervalRecord =
                IntervalRecord.newBuilder()
                        .setStartTime(12345)
                        .setStartZoneOffset(3600)
                        .setEndTime(54321)
                        .setEndZoneOffset(3600)
                        .setSteps(Steps.newBuilder().setCount(123))
                        .build();
        Record recordProto =
                Record.newBuilder()
                        .setUuid(UUID.randomUUID().toString())
                        .setPackageName("packageName")
                        .setAppName("appName")
                        .setLastModifiedTime(123456)
                        .setClientRecordId("clientId")
                        .setClientRecordVersion(3)
                        .setManufacturer("manufacturer")
                        .setModel("model")
                        .setDeviceType(DEVICE_TYPE_PHONE)
                        .setRecordingMethod(RECORDING_METHOD_AUTOMATICALLY_RECORDED)
                        .setIntervalRecord(intervalRecord)
                        .build();
        RecordInternal<?> recordInternal = mConverter.toRecordInternal(recordProto);

        assertThat(recordInternal.toProto()).isEqualTo(recordProto);
    }

    @Test
    public void convertSetValues_instantRecord() throws Exception {
        InstantRecord instantRecord =
                InstantRecord.newBuilder()
                        .setTime(12345)
                        .setZoneOffset(3600)
                        .setBloodPressure(
                                BloodPressure.newBuilder()
                                        .setMeasurementLocation(
                                                BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_WRIST)
                                        .setSystolic(120)
                                        .setDiastolic(80)
                                        .setBodyPosition(BODY_POSITION_LYING_DOWN))
                        .build();
        Record recordProto =
                Record.newBuilder()
                        .setUuid(UUID.randomUUID().toString())
                        .setPackageName("packageName")
                        .setAppName("appName")
                        .setLastModifiedTime(123456)
                        .setClientRecordId("clientId")
                        .setClientRecordVersion(3)
                        .setManufacturer("manufacturer")
                        .setModel("model")
                        .setDeviceType(DEVICE_TYPE_PHONE)
                        .setRecordingMethod(RECORDING_METHOD_AUTOMATICALLY_RECORDED)
                        .setInstantRecord(instantRecord)
                        .build();
        RecordInternal<?> recordInternal = mConverter.toRecordInternal(recordProto);

        assertThat(recordInternal.toProto()).isEqualTo(recordProto);
    }

    @Test
    public void convertDefaultValues_intervalRecord() throws Exception {
        IntervalRecord intervalRecord =
                IntervalRecord.newBuilder()
                        .setStartTime(12345)
                        .setStartZoneOffset(3600)
                        .setEndTime(54321)
                        .setEndZoneOffset(3600)
                        .setSteps(Steps.newBuilder().setCount(123))
                        .build();
        Record recordProto = Record.newBuilder().setIntervalRecord(intervalRecord).build();
        RecordInternal<?> recordInternal = mConverter.toRecordInternal(recordProto);

        assertThat(recordInternal.toProto()).isEqualTo(recordProto);
    }

    @Test
    public void convertDefaultValues_instantRecord() throws Exception {
        InstantRecord instantRecord =
                InstantRecord.newBuilder()
                        .setTime(12345)
                        .setZoneOffset(3600)
                        .setBloodPressure(
                                BloodPressure.newBuilder()
                                        .setMeasurementLocation(
                                                BLOOD_PRESSURE_MEASUREMENT_LOCATION_RIGHT_WRIST)
                                        .setSystolic(120)
                                        .setDiastolic(80)
                                        .setBodyPosition(BODY_POSITION_LYING_DOWN))
                        .build();
        Record recordProto = Record.newBuilder().setInstantRecord(instantRecord).build();
        RecordInternal<?> recordInternal = mConverter.toRecordInternal(recordProto);

        assertThat(recordInternal.toProto()).isEqualTo(recordProto);
    }
}
