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

package android.healthconnect.exportimport;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.exportimport.ImportStatus;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImportStatusTest {
    @Test
    public void testDeserialize() {
        ImportStatus importStatus =
                new ImportStatus(ImportStatus.DATA_IMPORT_ERROR_VERSION_MISMATCH, false);

        Parcel statusParcel = writeToParcel(importStatus);
        statusParcel.setDataPosition(0);
        ImportStatus deserializedStatus = statusParcel.readTypedObject(ImportStatus.CREATOR);

        assertThat(deserializedStatus.getDataImportError())
                .isEqualTo(ImportStatus.DATA_IMPORT_ERROR_VERSION_MISMATCH);
        assertThat(deserializedStatus.isImportOngoing()).isEqualTo(false);
    }

    @Test
    public void testDeserialize_noSuccessfulImport() {
        ImportStatus importStatus =
                new ImportStatus(ImportStatus.DATA_IMPORT_ERROR_WRONG_FILE, true);

        Parcel statusParcel = writeToParcel(importStatus);
        statusParcel.setDataPosition(0);
        ImportStatus deserializedStatus = statusParcel.readTypedObject(ImportStatus.CREATOR);

        assertThat(deserializedStatus.getDataImportError())
                .isEqualTo(ImportStatus.DATA_IMPORT_ERROR_WRONG_FILE);
        assertThat(deserializedStatus.isImportOngoing()).isEqualTo(true);
    }

    private static Parcel writeToParcel(ImportStatus importStatus) {
        Parcel statusParcel = Parcel.obtain();
        statusParcel.writeTypedObject(importStatus, 0);
        return statusParcel;
    }
}
