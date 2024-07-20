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

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.HealthConnectManager;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class ScheduledExportStatusTest {

    private static final String TEST_LAST_EXPORT_APP_NAME = "Dropbox";
    private static final String TEST_LAST_EXPORT_FILE_NAME = "healthconnect.zip";
    private static final String TEST_NEXT_EXPORT_APP_NAME = "Drive";
    private static final String TEST_NEXT_EXPORT_FILE_NAME = "exportimport.zip";

    @Test
    public void testDeserialize() {
        ScheduledExportStatus scheduledExportStatus =
                new ScheduledExportStatus(
                        Instant.ofEpochMilli(100),
                        HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS,
                        7,
                        TEST_LAST_EXPORT_FILE_NAME,
                        TEST_LAST_EXPORT_APP_NAME,
                        TEST_NEXT_EXPORT_FILE_NAME,
                        TEST_NEXT_EXPORT_APP_NAME);

        Parcel statusParcel = writeToParcel(scheduledExportStatus);
        statusParcel.setDataPosition(0);
        ScheduledExportStatus deserializedStatus =
                statusParcel.readTypedObject(ScheduledExportStatus.CREATOR);

        assertThat(deserializedStatus.getLastSuccessfulExportTime())
                .isEqualTo(Instant.ofEpochMilli(100));
        assertThat(deserializedStatus.getDataExportError())
                .isEqualTo(HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS);
        assertThat(deserializedStatus.getPeriodInDays()).isEqualTo(7);
        assertThat(deserializedStatus.getLastExportFileName())
                .isEqualTo(TEST_LAST_EXPORT_FILE_NAME);
        assertThat(deserializedStatus.getLastExportAppName()).isEqualTo(TEST_LAST_EXPORT_APP_NAME);
        assertThat(deserializedStatus.getNextExportFileName())
                .isEqualTo(TEST_NEXT_EXPORT_FILE_NAME);
        assertThat(deserializedStatus.getNextExportAppName()).isEqualTo(TEST_NEXT_EXPORT_APP_NAME);
    }

    @Test
    public void testDeserialize_noSuccessfulExport() {
        ScheduledExportStatus scheduledExportStatus =
                new ScheduledExportStatus(
                        null,
                        HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS,
                        7,
                        null,
                        null,
                        null,
                        null);

        Parcel statusParcel = writeToParcel(scheduledExportStatus);
        statusParcel.setDataPosition(0);
        ScheduledExportStatus deserializedStatus =
                statusParcel.readTypedObject(ScheduledExportStatus.CREATOR);

        assertThat(deserializedStatus.getLastSuccessfulExportTime()).isNull();
        assertThat(deserializedStatus.getDataExportError())
                .isEqualTo(HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS);
        assertThat(deserializedStatus.getPeriodInDays()).isEqualTo(7);
        assertThat(deserializedStatus.getLastExportAppName()).isNull();
        assertThat(deserializedStatus.getLastExportFileName()).isNull();
        assertThat(deserializedStatus.getNextExportAppName()).isNull();
        assertThat(deserializedStatus.getNextExportFileName()).isNull();
    }

    private static Parcel writeToParcel(ScheduledExportStatus scheduledExportStatus) {
        Parcel statusParcel = Parcel.obtain();
        statusParcel.writeTypedObject(scheduledExportStatus, 0);
        return statusParcel;
    }
}
