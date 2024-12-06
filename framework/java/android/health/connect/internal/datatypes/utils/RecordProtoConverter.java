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

package android.health.connect.internal.datatypes.utils;

import static com.android.healthfitness.flags.Flags.FLAG_CLOUD_BACKUP_AND_RESTORE;

import android.annotation.FlaggedApi;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.internal.datatypes.RecordInternal;
import android.health.connect.proto.backuprestore.InstantRecord;
import android.health.connect.proto.backuprestore.IntervalRecord;
import android.health.connect.proto.backuprestore.Record;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;

/**
 * A helper class used to create {@link RecordInternal} objects using its proto.
 *
 * @hide
 */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
public final class RecordProtoConverter {

    private final Map<Integer, Class<? extends RecordInternal<?>>> mDataTypeClassMap;

    public RecordProtoConverter() {
        mDataTypeClassMap =
                HealthConnectMappings.getInstance().getRecordIdToInternalRecordClassMap();
    }

    /** Creates a {@link RecordInternal} from the {@link Record} */
    public RecordInternal<?> toRecordInternal(Record recordProto)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        int recordId = getRecordId(recordProto);
        Class<? extends RecordInternal<?>> recordClass = mDataTypeClassMap.get(recordId);
        Objects.requireNonNull(recordClass);
        RecordInternal<?> recordInternal = recordClass.getConstructor().newInstance();
        recordInternal.fromRecordProto(recordProto);
        return recordInternal;
    }

    @RecordTypeIdentifier.RecordType
    private int getRecordId(Record protoRecord) {
        if (protoRecord.hasIntervalRecord()) {
            return getIntervalRecordId(protoRecord.getIntervalRecord());
        }
        if (protoRecord.hasInstantRecord()) {
            return getInstantRecordId(protoRecord.getInstantRecord());
        }
        throw new IllegalArgumentException();
    }

    @RecordTypeIdentifier.RecordType
    private int getIntervalRecordId(IntervalRecord protoRecord) {
        if (protoRecord.hasSteps()) {
            return RecordTypeIdentifier.RECORD_TYPE_STEPS;
        }
        throw new IllegalArgumentException();
    }

    @RecordTypeIdentifier.RecordType
    private int getInstantRecordId(InstantRecord protoRecord) {
        if (protoRecord.hasBloodPressure()) {
            return RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE;
        }
        throw new IllegalArgumentException();
    }
}
