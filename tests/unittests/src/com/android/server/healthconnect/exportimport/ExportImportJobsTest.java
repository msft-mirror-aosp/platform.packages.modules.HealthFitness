/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.healthconnect.exportimport;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.health.connect.exportimport.ScheduledExportSettings;
import android.net.Uri;
import android.os.PersistableBundle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.healthfitness.flags.Flags;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.FakePreferenceHelper;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.datatypehelpers.PreferenceHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;

public class ExportImportJobsTest {

    private static final String ANDROID_SERVER_PACKAGE_NAME = "com.android.server";

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(PreferenceHelper.class).build();

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock Context mContext;
    @Mock private JobScheduler mJobScheduler;
    @Mock private ExportManager mExportManager;

    @Captor ArgumentCaptor<JobInfo> mJobInfoCaptor;
    private final PreferenceHelper mFakePreferenceHelper = new FakePreferenceHelper();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(PreferenceHelper.getInstance()).thenReturn(mFakePreferenceHelper);
        when(mJobScheduler.forNamespace(ExportImportJobs.NAMESPACE)).thenReturn(mJobScheduler);
        when(mContext.getSystemService(JobScheduler.class)).thenReturn(mJobScheduler);
        when(mContext.getPackageName()).thenReturn(ANDROID_SERVER_PACKAGE_NAME);
    }

    @Test
    public void schedulePeriodicExportJob_withPeriodZero_doesNotScheduleExportJob() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(0));

        ExportImportJobs.schedulePeriodicExportJob(mContext, 0);

        verify(mJobScheduler, times(0)).schedule(any());
    }

    @Test
    public void schedulePeriodicExportJob_withPeriodGreaterThanZero_schedulesExportJob() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(1));

        ExportImportJobs.schedulePeriodicExportJob(mContext, 0);
        verify(mJobScheduler, times(1)).schedule(any());

        ExportImportJobs.schedulePeriodicExportJob(mContext, 1);
        verify(mJobScheduler, times(2)).schedule(any());
    }

    @Test
    public void schedulePeriodicExportJob_noExportYet_schedulesHourlyJob() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(7));

        ExportImportJobs.schedulePeriodicExportJob(mContext, 0);
        verify(mJobScheduler, times(1)).schedule(mJobInfoCaptor.capture());
        assertThat(mJobInfoCaptor.getValue().getIntervalMillis())
                .isEqualTo(Duration.ofHours(1).toMillis());
        assertThat(
                        mJobInfoCaptor
                                .getValue()
                                .getExtras()
                                .getBoolean(ExportImportJobs.IS_FIRST_EXPORT))
                .isTrue();
    }

    @Test
    public void schedulePeriodicExportJob_exportAlreadyDone_schedulesJobWithPeriod() {
        Uri uri = Uri.parse("abc");
        ExportImportSettingsStorage.setLastSuccessfulExport(Instant.now(), uri);
        ExportImportSettingsStorage.configure(
                ScheduledExportSettings.withUriAndPeriodInDays(uri, 7));

        ExportImportJobs.schedulePeriodicExportJob(mContext, 0);
        verify(mJobScheduler, times(1)).schedule(mJobInfoCaptor.capture());
        assertThat(mJobInfoCaptor.getValue().getIntervalMillis())
                .isEqualTo(Duration.ofDays(7).toMillis());
        assertThat(
                        mJobInfoCaptor
                                .getValue()
                                .getExtras()
                                .getBoolean(ExportImportJobs.IS_FIRST_EXPORT))
                .isFalse();
    }

    @Test
    public void schedulePeriodicExportJob_exportAlreadyDone_newUri_schedulesJobWithPeriod() {
        ExportImportSettingsStorage.setLastSuccessfulExport(Instant.now(), Uri.parse("last_uri"));
        ExportImportSettingsStorage.configure(
                ScheduledExportSettings.withUriAndPeriodInDays(Uri.parse("new_uri"), 7));

        ExportImportJobs.schedulePeriodicExportJob(mContext, 0);
        verify(mJobScheduler, times(1)).schedule(mJobInfoCaptor.capture());
        assertThat(mJobInfoCaptor.getValue().getIntervalMillis())
                .isEqualTo(Duration.ofHours(1).toMillis());
        assertThat(
                        mJobInfoCaptor
                                .getValue()
                                .getExtras()
                                .getBoolean(ExportImportJobs.IS_FIRST_EXPORT))
                .isTrue();
    }

    @Test
    @EnableFlags({Flags.FLAG_EXPORT_IMPORT})
    public void executePeriodicExportJob_withPeriodZero_doesNotRunExport() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(0));

        ExportImportJobs.executePeriodicExportJob(
                mContext, 0, new PersistableBundle(), mExportManager);

        verify(mExportManager, times(0)).runExport();
    }

    @Test
    @EnableFlags({Flags.FLAG_EXPORT_IMPORT})
    public void executePeriodicExportJob_withPeriodZero_returnsTrue() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(0));

        boolean isExportSuccessful =
                ExportImportJobs.executePeriodicExportJob(
                        mContext, 0, new PersistableBundle(), mExportManager);

        assertThat(isExportSuccessful).isTrue();
    }

    @Test
    @EnableFlags({Flags.FLAG_EXPORT_IMPORT})
    public void executePeriodicExportJob_withPeriodGreaterThanZero_runsExport() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(1));

        ExportImportJobs.executePeriodicExportJob(
                mContext, 0, new PersistableBundle(), mExportManager);

        verify(mExportManager, times(1)).runExport();
    }

    @Test
    @EnableFlags({Flags.FLAG_EXPORT_IMPORT})
    public void executePeriodicExportJob_successfulExport_returnsTrue() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(1));
        when(mExportManager.runExport()).thenReturn(true);

        boolean isExportSuccessful =
                ExportImportJobs.executePeriodicExportJob(
                        mContext, 0, new PersistableBundle(), mExportManager);

        assertThat(isExportSuccessful).isTrue();
        verify(mJobScheduler, times(0)).schedule(any());
    }

    @Test
    @EnableFlags({Flags.FLAG_EXPORT_IMPORT})
    public void executePeriodicExportJob_failedExport_returnsFalse() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(1));
        when(mExportManager.runExport()).thenReturn(false);

        boolean isExportSuccessful =
                ExportImportJobs.executePeriodicExportJob(
                        mContext, 0, new PersistableBundle(), mExportManager);

        assertThat(isExportSuccessful).isFalse();
    }

    @Test
    @EnableFlags({Flags.FLAG_EXPORT_IMPORT})
    public void executePeriodicExportJob_successfulFirstExport_reschedulesJob() {
        ExportImportSettingsStorage.configure(ScheduledExportSettings.withPeriodInDays(1));
        when(mExportManager.runExport()).thenReturn(true);

        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean(ExportImportJobs.IS_FIRST_EXPORT, true);
        boolean isExportSuccessful =
                ExportImportJobs.executePeriodicExportJob(mContext, 0, extras, mExportManager);

        assertThat(isExportSuccessful).isTrue();
        verify(mJobScheduler, times(1)).schedule(any());
    }
}
