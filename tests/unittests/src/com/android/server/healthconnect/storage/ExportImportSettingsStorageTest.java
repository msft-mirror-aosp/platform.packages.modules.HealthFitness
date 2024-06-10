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

import static org.mockito.Mockito.when;

import android.health.connect.exportimport.ImportStatus;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.net.Uri;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.FakePreferenceHelper;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ExportImportSettingsStorageTest {
    private static final String EXPORT_URI_PREFERENCE_KEY = "export_uri_key";
    private static final String EXPORT_PERIOD_PREFERENCE_KEY = "export_period_key";
    private static final String IMPORT_ONGOING_PREFERENCE_KEY = "import_ongoing_key";
    private static final String LAST_IMPORT_ERROR_PREFERENCE_KEY = "last_import_error_key";
    private static final String TEST_URI = "content://com.android.server.healthconnect/testuri";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(PreferenceHelper.class).build();

    private final PreferenceHelper mFakePreferenceHelper = new FakePreferenceHelper();

    @Before
    public void setUp() {
        when(PreferenceHelper.getInstance()).thenReturn(mFakePreferenceHelper);
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
}
