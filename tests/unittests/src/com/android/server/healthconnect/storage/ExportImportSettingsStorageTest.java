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
import android.health.connect.exportimport.ImportStatus;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.health.connect.exportimport.ScheduledExportStatus;
import android.net.Uri;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
    private static final String LAST_SUCCESSFUL_EXPORT_URI_PREFERENCE_KEY =
            "last_successful_export_uri_key";
    private static final String TEST_URI = "content://com.android.server.healthconnect/testuri";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(PreferenceHelper.class).build();

    @Mock Context mContext;
    @Mock ContentResolver mContentResolver;
    @Mock ContentProviderClient mContentProviderClient;
    @Mock Cursor mAppNameCursor;
    @Mock Cursor mFileNameCursor;

    private ExportImportSettingsStorage mExportImportSettingsStorage;

    private final PreferenceHelper mFakePreferenceHelper = new FakePreferenceHelper();
    private final Instant mInstant = Instant.ofEpochMilli(12345678);

    @Before
    public void setUp() throws RemoteException {
        mExportImportSettingsStorage = new ExportImportSettingsStorage(mFakePreferenceHelper);

        when(PreferenceHelper.getInstance()).thenReturn(mFakePreferenceHelper);
        when(mContext.getContentResolver()).thenReturn(mContentResolver);
        when(mContentResolver.acquireUnstableContentProviderClient(any(Uri.class)))
                .thenReturn(mContentProviderClient);
        when(mContentResolver.query(any(Uri.class), any(), any(), any(), any()))
                .thenReturn(mFileNameCursor);
        when(mContentProviderClient.query(any(Uri.class), any(), any(), any(), any()))
                .thenReturn(mAppNameCursor);
    }

    @Test
    public void testConfigure_uri() {
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY))
                .isEqualTo(TEST_URI);
    }

    @Test
    public void testConfigure_uri_keepsOtherSettings() {
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setPeriodInDays(7).build());

        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(7));
    }

    @Test
    public void testConfigure_uri_removeExportLostFileAccessError() {
        mExportImportSettingsStorage.setLastExportError(
                ScheduledExportStatus.DATA_EXPORT_LOST_FILE_ACCESS, mInstant);
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(mFakePreferenceHelper.getPreference(LAST_EXPORT_ERROR_PREFERENCE_KEY))
                .isEqualTo(null);
    }

    @Test
    public void testConfigure_uri_removeUnknownError() {
        mExportImportSettingsStorage.setLastExportError(
                ScheduledExportStatus.DATA_EXPORT_ERROR_UNKNOWN, mInstant);
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(mFakePreferenceHelper.getPreference(LAST_EXPORT_ERROR_PREFERENCE_KEY))
                .isEqualTo(null);
    }

    @Test
    public void testConfigure_periodInDays() {
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setPeriodInDays(7).build());

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(7));
    }

    @Test
    public void testConfigure_periodInDays_keepsOtherSettings() {
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setPeriodInDays(7).build());

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY))
                .isEqualTo(TEST_URI);
    }

    @Test
    public void testConfigure_clear() {
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setPeriodInDays(7).build());

        mExportImportSettingsStorage.configure(null);

        assertThat(mFakePreferenceHelper.getPreference(EXPORT_URI_PREFERENCE_KEY)).isNull();
        assertThat(mFakePreferenceHelper.getPreference(EXPORT_PERIOD_PREFERENCE_KEY)).isNull();
    }

    @Test
    public void testGetScheduledExportPeriodInDays() {
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setPeriodInDays(1).build());

        assertThat(mExportImportSettingsStorage.getScheduledExportPeriodInDays()).isEqualTo(1);
    }

    @Test
    public void getUri_returnsUri() {
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(mExportImportSettingsStorage.getUri()).isEqualTo(Uri.parse(TEST_URI));
    }

    @Test
    public void testConfigure_importStatus() {
        mExportImportSettingsStorage.setImportOngoing(true);
        mExportImportSettingsStorage.setLastImportError(DATA_IMPORT_ERROR_NONE);

        assertThat(mFakePreferenceHelper.getPreference(LAST_IMPORT_ERROR_PREFERENCE_KEY))
                .isEqualTo(Integer.toString(DATA_IMPORT_ERROR_NONE));
        assertThat(mFakePreferenceHelper.getPreference(IMPORT_ONGOING_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(true));

        ImportStatus importStatus = mExportImportSettingsStorage.getImportStatus();

        assertThat(importStatus.getDataImportError()).isEqualTo(DATA_IMPORT_ERROR_NONE);
        assertThat(mFakePreferenceHelper.getPreference(IMPORT_ONGOING_PREFERENCE_KEY))
                .isEqualTo(String.valueOf(true));
    }

    @Test
    public void
            testSetLastSuccessfulExportTime_callsGetScheduledExportStatus_returnsLastExportTime() {
        Instant now = Instant.now();
        mExportImportSettingsStorage.setLastSuccessfulExport(now, Uri.parse(TEST_URI));

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastSuccessfulExportTime())
                .isEqualTo(Instant.ofEpochMilli(now.toEpochMilli()));
    }

    @Test
    public void testSetLastExportError_callsGetScheduledExportStatus_returnsExportError() {
        mExportImportSettingsStorage.setLastExportError(
                ScheduledExportStatus.DATA_EXPORT_ERROR_UNKNOWN, mInstant);
        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getDataExportError())
                .isEqualTo(ScheduledExportStatus.DATA_EXPORT_ERROR_UNKNOWN);
        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastFailedExportTime())
                .isEqualTo(Instant.ofEpochMilli(mInstant.toEpochMilli()));
    }

    @Test
    public void testLastExportFileName_callsGetScheduledExportStatus_returnsLastExportFileName() {
        when(mFileNameCursor.moveToFirst()).thenReturn(true);
        when(mFileNameCursor.getString(anyInt())).thenReturn("healthconnect.zip");
        mExportImportSettingsStorage.setLastSuccessfulExport(Instant.now(), Uri.parse(TEST_URI));

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastExportFileName())
                .isEqualTo("healthconnect.zip");
    }

    @Test
    public void testLastExportFileName_withNoLastSuccessfulExportUri_returnsNull() {
        mFakePreferenceHelper.removeKey(LAST_SUCCESSFUL_EXPORT_URI_PREFERENCE_KEY);

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastExportFileName())
                .isNull();
    }

    @Test
    public void testLastExportFileName_exceptionThrown_returnsNull() {
        when(mFileNameCursor.moveToFirst()).thenReturn(true);
        when(mFileNameCursor.getString(anyInt()))
                .thenThrow(new IllegalArgumentException("Cannot find the file name"));
        mExportImportSettingsStorage.setLastSuccessfulExport(Instant.now(), Uri.parse(TEST_URI));

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastExportFileName())
                .isNull();
    }

    @Test
    public void testNextExportFileName_withNoConfiguredUri_returnsNull() {
        mFakePreferenceHelper.removeKey(EXPORT_URI_PREFERENCE_KEY);

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getNextExportFileName())
                .isNull();
    }

    @Test
    public void testNextExportFileName_exceptionThrown_returnsNull() {
        when(mFileNameCursor.moveToFirst()).thenReturn(true);
        when(mFileNameCursor.getString(anyInt()))
                .thenThrow(new IllegalArgumentException("Cannot find the file name"));
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastExportFileName())
                .isNull();
    }

    @Test
    public void testNextExportFileName_callsGetScheduledExportStatus_returnsNextExportFileName() {
        when(mFileNameCursor.moveToFirst()).thenReturn(true);
        when(mFileNameCursor.getString(anyInt())).thenReturn("hc.zip");
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getNextExportFileName())
                .isEqualTo("hc.zip");
    }

    @Test
    public void testLastExportAppName_withNoSuccessfulExportUri_returnsNull() {
        when(mAppNameCursor.moveToFirst()).thenReturn(true);
        when(mAppNameCursor.getString(anyInt())).thenReturn("Drive");
        mFakePreferenceHelper.removeKey(LAST_SUCCESSFUL_EXPORT_URI_PREFERENCE_KEY);

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastExportAppName())
                .isNull();
    }

    @Test
    public void testLastExportAppName_exceptionThrown_returnsNull() {
        when(mAppNameCursor.moveToFirst()).thenReturn(true);
        when(mAppNameCursor.getString(anyInt()))
                .thenThrow(new IllegalArgumentException("Cannot find the app name"));
        mExportImportSettingsStorage.setLastSuccessfulExport(Instant.now(), Uri.parse(TEST_URI));

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastExportAppName())
                .isNull();
    }

    @Test
    public void testLastExportAppName_withLastSuccessfulExportUri_returnsLastExportAppName() {
        when(mAppNameCursor.moveToFirst()).thenReturn(true);
        when(mAppNameCursor.getString(anyInt())).thenReturn("Drive");
        mExportImportSettingsStorage.setLastSuccessfulExport(Instant.now(), Uri.parse(TEST_URI));

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastExportAppName())
                .isEqualTo("Drive");
    }

    @Test
    public void testNextExportAppName_withNoUriConfigured_returnsNull() {
        when(mAppNameCursor.moveToFirst()).thenReturn(true);
        when(mAppNameCursor.getString(anyInt())).thenReturn("Dropbox");
        mFakePreferenceHelper.removeKey(EXPORT_URI_PREFERENCE_KEY);

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getLastExportAppName())
                .isNull();
    }

    @Test
    public void testNextExportAppName_exceptionThrown_returnsNull() {
        when(mAppNameCursor.moveToFirst()).thenReturn(true);
        when(mAppNameCursor.getString(anyInt()))
                .thenThrow(new IllegalArgumentException("Cannot find the app name"));
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getNextExportAppName())
                .isNull();
    }

    @Test
    public void testNextExportAppName_callsGetScheduledExportStatus_returnsNextExportAppName() {
        when(mAppNameCursor.moveToFirst()).thenReturn(true);
        when(mAppNameCursor.getString(anyInt())).thenReturn("Dropbox");
        mExportImportSettingsStorage.configure(
                new ScheduledExportSettings.Builder().setUri(Uri.parse(TEST_URI)).build());

        assertThat(
                        mExportImportSettingsStorage
                                .getScheduledExportStatus(mContext)
                                .getNextExportAppName())
                .isEqualTo("Dropbox");
    }
}
