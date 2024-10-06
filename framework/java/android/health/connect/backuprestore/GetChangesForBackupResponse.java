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
import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

// TODO(b/369798725): Add tests for the parcelable implementation

/** @hide */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
public final class GetChangesForBackupResponse implements Parcelable {

    @NonNull private final List<BackupChange> mChanges;

    // The changeToken to be used for the next call to resume the backup.
    @NonNull private final String mNextChangeToken;

    public GetChangesForBackupResponse(
            @NonNull List<BackupChange> changes, @NonNull String nextChangeToken) {
        mChanges = changes;
        mNextChangeToken = nextChangeToken;
    }

    private GetChangesForBackupResponse(Parcel in) {
        mChanges = in.createTypedArrayList(BackupChange.CREATOR);
        mNextChangeToken = in.readString();
    }

    @NonNull
    public static final Creator<GetChangesForBackupResponse> CREATOR =
            new Creator<GetChangesForBackupResponse>() {
                @Override
                public GetChangesForBackupResponse createFromParcel(Parcel in) {
                    return new GetChangesForBackupResponse(in);
                }

                @Override
                public GetChangesForBackupResponse[] newArray(int size) {
                    return new GetChangesForBackupResponse[size];
                }
            };

    @NonNull
    public List<BackupChange> getChanges() {
        return mChanges;
    }

    @NonNull
    public String getNextChangeToken() {
        return mNextChangeToken;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mNextChangeToken);
    }
}
