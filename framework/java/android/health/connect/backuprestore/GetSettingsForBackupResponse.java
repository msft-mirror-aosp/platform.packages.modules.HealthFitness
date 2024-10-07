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

// TODO(b/369798725): Add tests for the parcelable implementation

/** @hide */
@FlaggedApi(FLAG_CLOUD_BACKUP_AND_RESTORE)
public final class GetSettingsForBackupResponse implements Parcelable {

    @NonNull private final BackupSettings mSettings;

    public GetSettingsForBackupResponse(@NonNull BackupSettings settings) {
        mSettings = settings;
    }

    private GetSettingsForBackupResponse(Parcel in) {
        mSettings = in.readParcelable(BackupSettings.class.getClassLoader());
    }

    @NonNull
    public static final Creator<GetSettingsForBackupResponse> CREATOR =
            new Creator<GetSettingsForBackupResponse>() {
                @Override
                public GetSettingsForBackupResponse createFromParcel(Parcel in) {
                    return new GetSettingsForBackupResponse(in);
                }

                @Override
                public GetSettingsForBackupResponse[] newArray(int size) {
                    return new GetSettingsForBackupResponse[size];
                }
            };

    @NonNull
    public BackupSettings getSettings() {
        return mSettings;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(mSettings, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
