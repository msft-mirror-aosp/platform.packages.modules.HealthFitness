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

package com.android.server.healthconnect.storage.utils;

import static java.util.Objects.requireNonNull;

import android.health.connect.datatypes.RecordTypeIdentifier;
import android.util.ArrayMap;

import com.android.healthfitness.flags.Flags;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.logging.HealthConnectServiceLogger;
import com.android.server.healthconnect.storage.datatypehelpers.RecordHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** @hide */
public class InternalHealthConnectMappings {

    private final Map<Integer, InternalDataTypeDescriptor> mRecordTypeIdToDescriptor;
    private final List<RecordHelper<?>> mAllRecordHelpers;

    public InternalHealthConnectMappings() {
        this(InternalDataTypeDescriptors.getAllInternalDataTypeDescriptors());
    }

    @VisibleForTesting
    InternalHealthConnectMappings(List<InternalDataTypeDescriptor> descriptors) {
        mRecordTypeIdToDescriptor = new ArrayMap<>(descriptors.size());
        mAllRecordHelpers = new ArrayList<>(descriptors.size());
        for (var descriptor : descriptors) {
            mRecordTypeIdToDescriptor.put(descriptor.getRecordTypeIdentifier(), descriptor);
            mAllRecordHelpers.add(descriptor.getRecordHelper());
        }
    }

    /** Maps the internal record type to a special record type for UUIDs. */
    @RecordTypeIdForUuid.Type
    public int getRecordTypeIdForUuid(@RecordTypeIdentifier.RecordType int recordTypeId) {
        if (!Flags.healthConnectMappings()) {
            return RecordTypeForUuidMappings.getRecordTypeIdForUuid(recordTypeId);
        }

        return requireNonNull(
                        mRecordTypeIdToDescriptor.get(recordTypeId),
                        "No mapping for " + recordTypeId)
                .getRecordTypeIdForUuid();
    }

    /** Returns a collection of all supported record helpers. */
    public Collection<RecordHelper<?>> getRecordHelpers() {
        if (!Flags.healthConnectMappings()) {
            return RecordHelperProvider.getRecordHelpers().values();
        }
        return mAllRecordHelpers;
    }

    /** Returns a {@link RecordHelper} for given record type id. */
    public RecordHelper<?> getRecordHelper(@RecordTypeIdentifier.RecordType int recordTypeId) {
        if (!Flags.healthConnectMappings()) {
            return RecordHelperProvider.getRecordHelper(recordTypeId);
        }

        return getDescriptorFor(recordTypeId).getRecordHelper();
    }

    /** Returns a logging enum for given record type id. */
    public int getLoggingEnumForRecordTypeId(@RecordTypeIdentifier.RecordType int recordTypeId) {
        if (!Flags.healthConnectMappings()) {
            return HealthConnectServiceLogger.Builder.getDataTypeEnumFromRecordType(recordTypeId);
        }
        return getDescriptorFor(recordTypeId).getLoggingEnum();
    }

    private InternalDataTypeDescriptor getDescriptorFor(
            @RecordTypeIdentifier.RecordType int recordTypeId) {
        return requireNonNull(
                mRecordTypeIdToDescriptor.get(recordTypeId), "No mapping for " + recordTypeId);
    }
}
