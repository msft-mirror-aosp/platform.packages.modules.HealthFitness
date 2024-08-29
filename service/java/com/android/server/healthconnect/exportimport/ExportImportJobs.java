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

import static com.android.healthfitness.flags.Flags.exportImport;
import static com.android.healthfitness.flags.Flags.exportImportFastFollow;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.HealthConnectDailyService;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;

import java.time.Duration;
import java.util.Objects;

/**
 * Defines jobs related to Health Connect export and imports.
 *
 * @hide
 */
public class ExportImportJobs {
    private static final String TAG = "HealthConnectExportJobs";
    private static final int MIN_JOB_ID = ExportImportJobs.class.hashCode();

    @VisibleForTesting static final String SHOULD_SCHEDULE_EARLY = "should_schedule_early";
    @VisibleForTesting static final String NAMESPACE = "HEALTH_CONNECT_IMPORT_EXPORT_JOBS";

    public static final String PERIODIC_EXPORT_JOB_NAME = "periodic_export_job";

    /** Schedule the periodic export job. */
    public static void schedulePeriodicExportJob(Context context, int userId) {
        schedulePeriodicExportJob(context, userId, /* isRescheduled= */ true);
    }

    @VisibleForTesting
    static void schedulePeriodicExportJob(Context context, int userId, boolean isRescheduled) {
        int periodInDays = ExportImportSettingsStorage.getScheduledExportPeriodInDays();
        if (periodInDays <= 0) {
            return;
        }

        PersistableBundle extras = new PersistableBundle();
        extras.putInt(HealthConnectDailyService.EXTRA_USER_ID, userId);
        extras.putString(HealthConnectDailyService.EXTRA_JOB_NAME_KEY, PERIODIC_EXPORT_JOB_NAME);

        long periodInMillis = Duration.ofDays(periodInDays).toMillis();
        long flexInMillis = Duration.ofHours(6).toMillis();
        if (periodInDays >= 7) {
            // For weekly / monthly export, allow export to happen at least one day before.
            flexInMillis = Duration.ofDays(1).toMillis();
        }
        if (ExportImportSettingsStorage.getLastSuccessfulExportTime() == null
                || !ExportImportSettingsStorage.getUri()
                        .equals(ExportImportSettingsStorage.getLastSuccessfulExportUri())
                || (isRescheduled && exportImportFastFollow())) {

            // Shorten the period for the first export to any new URI.
            // This will be changed back once the first export to any new location is done.
            periodInMillis = Duration.ofHours(1).toMillis();
            flexInMillis = periodInMillis;

            extras.putBoolean(SHOULD_SCHEDULE_EARLY, true);
        }
        Slog.i(
                TAG,
                "Scheduling export job with period (millis) = "
                        + periodInMillis
                        + " flex = "
                        + flexInMillis);

        ComponentName componentName = new ComponentName(context, HealthConnectDailyService.class);
        JobInfo.Builder builder =
                new JobInfo.Builder(MIN_JOB_ID + userId, componentName)
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .setPeriodic(
                                periodInMillis,
                                // Flex interval.
                                // The flex period begins after (periodInMillis - flexInMillis)
                                // Flex is the max of the specified time, or 5% of periodInMillis.
                                flexInMillis)
                        .setExtras(extras);

        HealthConnectDailyService.schedule(
                Objects.requireNonNull(context.getSystemService(JobScheduler.class))
                        .forNamespace(NAMESPACE),
                userId,
                builder.build());
    }

    /**
     * Execute the periodic export job. It returns true if the export was successful (no need to
     * reschedule the job). False otherwise.
     */
    // TODO(b/318484778): Use dependency injection instead of passing an instance to the method.
    public static boolean executePeriodicExportJob(
            Context context, int userId, PersistableBundle extras, ExportManager exportManager) {
        if (!exportImport() || ExportImportSettingsStorage.getScheduledExportPeriodInDays() <= 0) {
            // If there is no need to run the export, it counts like a success regarding job
            // reschedule.
            return true;
        }
        boolean exportSuccess = exportManager.runExport();
        boolean shouldScheduleEarly = extras.getBoolean(SHOULD_SCHEDULE_EARLY, false);
        if (exportSuccess && shouldScheduleEarly) {
            schedulePeriodicExportJob(context, userId, /* isRescheduled= */ false);
        }
        return exportSuccess;

        // TODO(b/325599089): Cancel job if flag is off.

        // TODO(b/325599089): Do we need an additional periodic / one-off task to make sure a single
        //  export completes? We need to test if JobScheduler will call the job again if jobFinished
        //  is never called.

        // TODO(b/325599089): Consider if we need to do any checkpointing here in case the job
        //  doesn't complete and we need to pick it up again.
    }
}
