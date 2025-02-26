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

package android.health.connect.backuprestore;

import static com.android.healthfitness.flags.Flags.FLAG_CLOUD_BACKUP_AND_RESTORE;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a record in Health Connect to be backed up.
 *
 * @hide
 */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
@SystemApi
public final class BackupChange implements Parcelable {

    // A record ID that uniquely identifies the specific record this change refers to.
    @NonNull private final String mRecordId;

    private final boolean mIsDeletion;

    // Only present if isDeletion is false.
    // The data is returned as bytes rather than records to keep the data opaque from the client.
    // As long as the client doesn't parse the data, it doesn't know what type of data this is.
    @Nullable private final byte[] mData;

    /**
     * @param recordId A record ID that uniquely identifies the specific record this change refers
     *     to.
     * @param isDeletion Whether this change is a deletion.
     * @param data Only present if isDeletion is false. The data is returned as bytes rather than
     *     records to keep the data opaque from the client. The caller shouldn't make any
     *     assumptions about the format of the data.
     */
    public BackupChange(@NonNull String recordId, boolean isDeletion, @Nullable byte[] data) {
        mRecordId = recordId;
        mIsDeletion = isDeletion;
        mData = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BackupChange that)) return false;
        return mIsDeletion == that.mIsDeletion
                && mRecordId.equals(that.mRecordId)
                && Arrays.equals(mData, that.mData);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(mRecordId, mIsDeletion);
        result = 31 * result + Arrays.hashCode(mData);
        return result;
    }

    private BackupChange(Parcel in) {
        mRecordId = in.readString();
        mIsDeletion = in.readByte() != 0;
        mData = in.readBlob();
    }

    @NonNull
    public static final Creator<BackupChange> CREATOR =
            new Creator<>() {
                @Override
                public BackupChange createFromParcel(Parcel in) {
                    return new BackupChange(in);
                }

                @Override
                public BackupChange[] newArray(int size) {
                    return new BackupChange[size];
                }
            };

    /** Returns the ID of the record that was changed. */
    @NonNull
    public String getRecordId() {
        return mRecordId;
    }

    /** Returns whether this change is a deletion. */
    public boolean isDeletion() {
        return mIsDeletion;
    }

    /**
     * Returns the record that was changed.
     *
     * <p>Only present if isDeletion is false.
     */
    @Nullable
    public byte[] getData() {
        return mData;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mRecordId);
        dest.writeByte((byte) (mIsDeletion ? 1 : 0));
        dest.writeBlob(mData);
    }
}
