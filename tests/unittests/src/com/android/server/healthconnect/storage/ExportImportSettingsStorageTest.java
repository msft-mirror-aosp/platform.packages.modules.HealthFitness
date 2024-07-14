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

package com.android.server.healthconnect.storage;

import static android.health.connect.exportimport.ImportStatus.DATA_IMPORT_ERROR_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.health.connect.HealthConnectManager;
import android.health.connect.exportimport.ImportStatus;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.net.Uri;
import android.os.RemoteException;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.FakePreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public final class ExportImportSettingsStorageTest {
    private static final String EXPORT_URI_PREFERENCE_KEY = "export_uri_key";
    private static final String EXPORT_PERIOD_PREFERENCE_KEY = "export_period_key";
    private static final String IMPORT_ONGOING_PREFERENCE_KEY = "import_ongoing_key";
    private static final String LAST_EXPORT_ERROR_PREFERENCE_KEY = "last_export_error_key";
    private static final String LAST_IMPORT_ERROR_PREFERENCE_KEY = "last_import_error_key";
    public static final String LAST_EXPORT_FILE_NAME_KEY = "last_export_file_name_key";
    public static final String LAST_EXPORT_APP_NAME_KEY = "last_export_app_name_key";
    public static final String NEXT_EXPORT_FILE_NAME_KEY = "next_export_file_name_key";
    public static final String NEXT_EXPORT_APP_NAME_KEY = "next_export_app_name_key";
    private static final String TEST_URI = "content://com.android.server.healthconnect/testuri";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(PreferenceHelper.class).build();

    @Mock Context mContext;
    @Mock ContentResolver mContentResolver;
    @Mock ContentProviderClient mContentProviderClient;
    @Mock Cursor mCursor;

    private final PreferenceHelper mFakePreferenceHelper = new FakePreferenceHelper();

    @Before
    public void setUp() throws RemoteException {
        when(PreferenceHelper.getInstance()).thenReturn(mFakePreferenceHelper);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContentResolver.acquireUnstableContentProviderClient(any(Uri.class)))
                .thenReturn(mContentProviderClient);
        when(mContentProviderClient.query(any(Uri.class), any(), any(), any(), any()))
                .thenReturn(mCursor);
    }

    @Test
    public void testConfigure_uri() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY))
                .isEqualTo(TEST_URI);
    }

    @Test
    public void testConfigure_uri_keepsOtherSettings() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        ExportImportSettingsStorage.configure(ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(7));
    }

    @Test
    public void testConfigure_uri_removeExportLostFileAccessError() {
        ExportImportSettingsStorage.setLastExportError(
                HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS);
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        assertThat(mFakePreferenceHelper.getPreference(LAST_EXPORT_ERROR_PREFERENCE_KEY))
                .isEqualTo(null);
    }

    @Test
    public void testConfigure_uri_removeUnknownError() {
        ExportImportSettingsStorage.setLastExportError(
                HealthConnectManager.DATA_EXPORT_ERROR_UNKNOWN);
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        assertThat(mFakePreferenceHelper.getPreference(LAST_EXPORT_ERROR_PREFERENCE_KEY))
                .isEqualTo(null);
    }

    @Test
    public void testConfigure_periodInDays() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(7));
    }

    @Test
    public void testConfigure_periodInDays_keepsOtherSettings() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY))
                .isEqualTo(TEST_URI);
    }

    @Test
    public void testConfigure_clear() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        ExportImportSettingsStorage.configure(null);

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY)).isNull();
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY)).isNull();
    }

    @Test
    public void testGetScheduledExportPeriodInDays() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(1));

        assertThat(ExportImportSettingsStorage.getScheduledExportPeriodInDays()).isEqualTo(1);
    }

    @Test
    public void getUri_returnsUri() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withUri(Uri.parse(TEST_URI)));

        assertThat(ExportImportSettingsStorage.getUri()).isEqualTo(Uri.parse(TEST_URI));
    }

    @Test
    public void testConfigure_importStatus() {
        ExportImportSettingsStorage.setImportOngoing(true);
        ExportImportSettingsStorage.setLastImportError(DATA_IMPORT_ERROR_NONE);

        assertThat(mFakePreferenceHelper.getPreference(LAST_IMPORT_ERROR_PREFERENCE_KEY))
                .isEqualTo(Integer.toString(DATA_IMPORT_ERROR_NONE));
        assertThat(mFakePreferenceHelper.getPreference(IMPORT_ONGOING_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(true));

        ImportStatus importStatus = ExportImportSettingsStorage.getImportStatus();

        assertThat(importStatus.getDataImportError()).isEqualTo(DATA_IMPORT_ERROR_NONE);
        assertThat(mFakePreferenceHelper.getPreference(IMPORT_ONGOING_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(true));
    }

    @Test
    public void
            testSetLastSuccessfulExportTime_callsGetScheduledExportStatus_returnsLastExportTime() {
        Instant now = Instant.now();
        ExportImportSettingsStorage.setLastSuccessfulExport(now);

        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getLastSuccessfulExportTime())
                .isEqualTo(Instant.ofEpochMilli(now.toEpochMilli()));
    }

    @Test
    public void testSetLastExportError_callsGetScheduledExportStatus_returnsExportError() {
        ExportImportSettingsStorage.setLastExportError(
                HealthConnectManager.DATA_EXPORT_ERROR_UNKNOWN);

        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getDataExportError())
                .isEqualTo(HealthConnectManager.DATA_EXPORT_ERROR_UNKNOWN);
    }

    @Test
    public void
            testSetLastExportFileName_callsGetScheduledExportStatus_returnsLastExportFileName() {
        ExportImportSettingsStorage.setExportFileName(
                mContext, Uri.parse(TEST_URI), LAST_EXPORT_FILE_NAME_KEY);

        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getLastExportFileName())
                .isEqualTo("testuri");
    }

    @Test
    public void
            testSetNextExportFileName_callsGetScheduledExportStatus_returnsNextExportFileName() {
        ExportImportSettingsStorage.setExportFileName(
                mContext, Uri.parse(TEST_URI), NEXT_EXPORT_FILE_NAME_KEY);

        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getNextExportFileName())
                .isEqualTo("testuri");
    }

    @Test
    public void testSetLastExportAppName_callsGetScheduledExportStatus_returnsLastExportAppName() {
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getString(anyInt())).thenReturn("Drive");
        ExportImportSettingsStorage.setExportAppName(
                mContext, Uri.parse(TEST_URI), LAST_EXPORT_APP_NAME_KEY);

        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getLastExportAppName())
                .isEqualTo("Drive");
    }

    @Test
    public void testSetNextExportAppName_callsGetScheduledExportStatus_returnsNextExportAppName() {
        when(mCursor.moveToFirst()).thenReturn(true);
        when(mCursor.getString(anyInt())).thenReturn("Dropbox");
        ExportImportSettingsStorage.setExportAppName(
                mContext, Uri.parse(TEST_URI), NEXT_EXPORT_APP_NAME_KEY);

        assertThat(
                        ExportImportSettingsStorage.getScheduledExportStatus(mContext)
                                .getNextExportAppName())
                .isEqualTo("Dropbox");
    }
}
