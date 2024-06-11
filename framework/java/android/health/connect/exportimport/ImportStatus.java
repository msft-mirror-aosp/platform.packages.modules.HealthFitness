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

package android.health.connect.exportimport;

import static com.android.healthfitness.flags.Flags.FLAG_EXPORT_IMPORT;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Status for a data import.
 *
 * @hide
 */
@FlaggedApi(FLAG_EXPORT_IMPORT)
public final class ImportStatus implements Parcelable {

    /**
     * Unknown error during the last data import.
     *
     * @hide
     */
    @FlaggedApi(FLAG_EXPORT_IMPORT)
    public static final int DATA_IMPORT_ERROR_UNKNOWN = 0;

    /**
     * No error during the last data import.
     *
     * @hide
     */
    @FlaggedApi(FLAG_EXPORT_IMPORT)
    public static final int DATA_IMPORT_ERROR_NONE = 1;

    /**
     * Indicates that the last import failed because the user picked a file that was not exported by
     * Health Connect.
     *
     * @hide
     */
    @FlaggedApi(FLAG_EXPORT_IMPORT)
    public static final int DATA_IMPORT_ERROR_WRONG_FILE = 2;

    /**
     * Indicates that the last import failed because the version of the on device schema does not
     * match the version of the imported database.
     *
     * @hide
     */
    @FlaggedApi(FLAG_EXPORT_IMPORT)
    public static final int DATA_IMPORT_ERROR_VERSION_MISMATCH = 3;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
        DATA_IMPORT_ERROR_UNKNOWN,
        DATA_IMPORT_ERROR_NONE,
        DATA_IMPORT_ERROR_WRONG_FILE,
        DATA_IMPORT_ERROR_VERSION_MISMATCH
    })
    public @interface DataImportError {}

    @NonNull
    public static final Creator<ImportStatus> CREATOR =
            new Creator<>() {
                @Override
                public ImportStatus createFromParcel(Parcel in) {
                    return new ImportStatus(in);
                }

                @Override
                public ImportStatus[] newArray(int size) {
                    return new ImportStatus[size];
                }
            };

    @DataImportError private final int mDataImportError;
    private final boolean mIsImportOngoing;

    public ImportStatus(@DataImportError int dataImportError, boolean isImportOngoing) {
        mDataImportError = dataImportError;
        mIsImportOngoing = isImportOngoing;
    }

    /** Returns the error status of the last import attempt. */
    public int getDataImportError() {
        return mDataImportError;
    }

    /** Returns whether the import is ongoing. */
    public boolean isImportOngoing() {
        return mIsImportOngoing;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private ImportStatus(@NonNull Parcel in) {
        mDataImportError = in.readInt();
        mIsImportOngoing = in.readBoolean();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mDataImportError);
        dest.writeBoolean(mIsImportOngoing);
    }
}
