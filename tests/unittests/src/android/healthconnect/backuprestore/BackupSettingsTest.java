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

package android.healthconnect.backuprestore;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.backuprestore.BackupSettings;
import android.os.Parcel;

import org.junit.Test;

public class BackupSettingsTest {

    @Test
    public void backupSettingsParcel_propertiesAreIdentical() {
        BackupSettings original = new BackupSettings(123, new byte[] {1, 2, 3});

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BackupSettings restoredParcel = BackupSettings.CREATOR.createFromParcel(parcel);

        assertThat(restoredParcel.getVersion()).isEqualTo(123);
        assertThat(restoredParcel.getData()).isEqualTo(new byte[] {1, 2, 3});
        parcel.recycle();
    }
}
