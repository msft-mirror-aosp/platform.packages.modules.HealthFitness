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

package com.android.server.healthconnect.phr;

import static java.util.Objects.hash;

import android.health.connect.datatypes.MedicalResource;

import java.util.List;

/**
 * Internal class to allow for reading out the whole of medical_resource_table, including the
 * lastModifiedTimestamp for each row which is not part of the {@link MedicalResource} class.
 *
 * <p>This class is only used for the merge flow of D2D and export/import.
 *
 * @hide
 */
public class ReadMedicalResourceRowsResponse {
    long mLastRowId;
    List<MedicalResourceRow> mMedicalResourceRows;

    public ReadMedicalResourceRowsResponse(
            List<MedicalResourceRow> medicalResourceRows, long lastRowId) {
        mMedicalResourceRows = medicalResourceRows;
        mLastRowId = lastRowId;
    }

    /** Returns the lastReadRowId. */
    public long getLastReadRowId() {
        return mLastRowId;
    }

    /** Returns the number of rows read based on the number of {@link MedicalResourceRow}s. */
    public long getNumOfRowsRead() {
        return mMedicalResourceRows.size();
    }

    /** Returns the list of read {@link MedicalResourceRow}s. */
    public List<MedicalResourceRow> getMedicalResourceRows() {
        return mMedicalResourceRows;
    }

    /** Represents a medical_resource_table row. */
    public static class MedicalResourceRow {
        long mLastModifiedTimestamp;
        MedicalResource mMedicalResource;

        public MedicalResourceRow(MedicalResource medicalResource, long lastModifiedTimestamp) {
            mMedicalResource = medicalResource;
            mLastModifiedTimestamp = lastModifiedTimestamp;
        }

        /** Returns the lastModifiedTimestamp of the {@code mMedicalResource}. */
        public long getLastModifiedTimestamp() {
            return mLastModifiedTimestamp;
        }

        /** Returns the {@link MedicalResource} read from the database. */
        public MedicalResource getMedicalResource() {
            return mMedicalResource;
        }

        /** Indicates whether some other object is "equal to" this one. */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MedicalResourceRow that)) return false;
            return mLastModifiedTimestamp == that.mLastModifiedTimestamp
                    && mMedicalResource.equals(that.mMedicalResource);
        }

        /** Returns a hash code value for the object. */
        @Override
        public int hashCode() {
            return hash(getLastModifiedTimestamp(), getMedicalResource());
        }
    }

    /** Indicates whether some other object is "equal to" this one. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadMedicalResourceRowsResponse that)) return false;
        return mLastRowId == that.mLastRowId
                && mMedicalResourceRows.equals(that.mMedicalResourceRows);
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return hash(getLastReadRowId(), getMedicalResourceRows());
    }
}
