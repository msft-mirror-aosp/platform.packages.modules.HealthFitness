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

package com.android.server.healthconnect.exportimport;

import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_ERROR_NONE;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_ERROR_UNKNOWN;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_LOST_FILE_ACCESS;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_STARTED;

import static com.android.healthfitness.flags.Flags.exportImportFastFollow;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR;
import static com.android.server.healthconnect.logging.ExportImportLogger.NO_VALUE_RECORDED;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.UserHandle;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.logging.ExportImportLogger;
import com.android.server.healthconnect.notifications.HealthConnectNotificationSender;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.List;

/**
 * Class that manages export related tasks. In this context, export means to make an encrypted copy
 * of Health Connect data that the user can store in some online storage solution.
 *
 * @hide
 */
public class ExportManager {

    @VisibleForTesting static final String LOCAL_EXPORT_DIR_NAME = "export_import";

    static final String LOCAL_EXPORT_DATABASE_FILE_NAME = "health_connect_export.db";

    @VisibleForTesting static final String LOCAL_EXPORT_ZIP_FILE_NAME = "health_connect_export.zip";

    private static final String TAG = "HealthConnectExportImport";

    private final Clock mClock;
    private final TransactionManager mTransactionManager;
    private final UserHandle mUserHandle;

    // Tables to drop instead of tables to keep to avoid risk of bugs if new data types are added.

    /**
     * Logs size is non-trivial, exporting them would make the process slower and the upload file
     * would need more storage. Furthermore, logs from a previous device don't provide the user with
     * useful information.
     */
    @VisibleForTesting
    public static final List<String> TABLES_TO_CLEAR =
            List.of(AccessLogsHelper.TABLE_NAME, ChangeLogsHelper.TABLE_NAME);

    private final DatabaseContext mDatabaseContext;
    private final HealthConnectNotificationSender mNotificationSender;

    public ExportManager(@NonNull Context context, Clock clock) {
        this(context, clock, ExportImportNotificationSender.createSender(context));
    }

    public ExportManager(
            @NonNull Context context,
            Clock clock,
            HealthConnectNotificationSender notificationSender) {
        requireNonNull(context);
        requireNonNull(clock);
        requireNonNull(notificationSender);

        mUserHandle = context.getUser();
        Context userContext = context.createContextAsUser(mUserHandle, 0);

        mClock = clock;
        mDatabaseContext = DatabaseContext.create(userContext, LOCAL_EXPORT_DIR_NAME, mUserHandle);
        mTransactionManager = TransactionManager.getInitialisedInstance();
        mNotificationSender = notificationSender;
    }

    /**
     * Makes a local copy of the HC database, deletes the unnecessary data for export and sends the
     * data to a cloud provider.
     */
    public synchronized boolean runExport() {
        long startTimeMillis = mClock.millis();
        ExportImportLogger.logExportStatus(
                DATA_EXPORT_STARTED, NO_VALUE_RECORDED, NO_VALUE_RECORDED, NO_VALUE_RECORDED);
        Slog.i(TAG, "Export started.");

        File localExportDbFile =
                new File(mDatabaseContext.getDatabaseDir(), LOCAL_EXPORT_DATABASE_FILE_NAME);
        File localExportZipFile =
                new File(mDatabaseContext.getDatabaseDir(), LOCAL_EXPORT_ZIP_FILE_NAME);

        try {
            try {
                exportLocally(localExportDbFile);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to create local file for export", e);
                Slog.d(TAG, "original file size: " + intSizeInKb(localExportDbFile));
                recordError(
                        DATA_EXPORT_ERROR_UNKNOWN,
                        startTimeMillis,
                        intSizeInKb(localExportDbFile),
                        /* Compressed size will be 0, not yet compressed */
                        intSizeInKb(localExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            }

            try {
                deleteLogTablesContent();
            } catch (Exception e) {
                Slog.e(TAG, "Failed to prepare local file for export", e);
                Slog.d(TAG, "original file size: " + intSizeInKb(localExportDbFile));
                recordError(
                        DATA_EXPORT_ERROR_UNKNOWN,
                        startTimeMillis,
                        intSizeInKb(localExportDbFile),
                        /* Compressed size will be 0, not yet compressed */
                        intSizeInKb(localExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            }

            try {
                Compressor.compress(
                        localExportDbFile, LOCAL_EXPORT_DATABASE_FILE_NAME, localExportZipFile);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to compress local file for export", e);
                Slog.d(TAG, "original file size: " + intSizeInKb(localExportDbFile));
                recordError(
                        DATA_EXPORT_ERROR_UNKNOWN,
                        startTimeMillis,
                        intSizeInKb(localExportDbFile),
                        /* Compressed size will be 0, not yet compressed */
                        intSizeInKb(localExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            }

            Uri destinationUri = ExportImportSettingsStorage.getUri();
            try {
                exportToUri(localExportZipFile, destinationUri);
            } catch (FileNotFoundException e) {
                Slog.e(TAG, "Lost access to export location", e);
                Slog.d(TAG, "original file size: " + intSizeInKb(localExportDbFile));
                recordError(
                        DATA_EXPORT_LOST_FILE_ACCESS,
                        startTimeMillis,
                        intSizeInKb(localExportDbFile),
                        intSizeInKb(localExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            } catch (Exception e) {
                Slog.e(TAG, "Failed to export to URI", e);
                Slog.d(TAG, "original file size: " + intSizeInKb(localExportDbFile));
                recordError(
                        DATA_EXPORT_ERROR_UNKNOWN,
                        startTimeMillis,
                        intSizeInKb(localExportDbFile),
                        intSizeInKb(localExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            }
            Slog.i(TAG, "Export completed.");
            Slog.d(TAG, "original file size: " + intSizeInKb(localExportDbFile));
            recordSuccess(
                    startTimeMillis,
                    intSizeInKb(localExportDbFile),
                    intSizeInKb(localExportZipFile),
                    destinationUri);
            return true;
        } finally {
            Slog.i(TAG, "Delete local export files started.");
            if (localExportDbFile.exists()) {
                SQLiteDatabase.deleteDatabase(localExportDbFile);
            }
            if (localExportZipFile.exists()) {
                localExportZipFile.delete();
            }
            Slog.i(TAG, "Delete local export files completed.");
        }
    }

    protected void recordSuccess(
            long startTimeMillis,
            int originalDataSizeKb,
            int compressedDataSizeKb,
            Uri destinationUri) {
        ExportImportSettingsStorage.setLastSuccessfulExport(mClock.instant(), destinationUri);

        // The logging proto holds an int32 not an in64 to save on logs storage. The cast makes this
        // explicit. The int can hold 24.855 days worth of milli seconds, which
        // is sufficient because the system would kill the process earlier.
        int timeToSuccessMillis = (int) (mClock.millis() - startTimeMillis);
        ExportImportLogger.logExportStatus(
                DATA_EXPORT_ERROR_NONE,
                timeToSuccessMillis,
                originalDataSizeKb,
                compressedDataSizeKb);
    }

    protected void recordError(
            int exportStatus,
            long startTimeMillis,
            int originalDataSizeKb,
            int compressedDataSizeKb) {
        ExportImportSettingsStorage.setLastExportError(exportStatus, mClock.instant());

        // Convert to int to save on logs storage, int can hold about 68 years
        int timeToErrorMillis = (int) (mClock.millis() - startTimeMillis);
        ExportImportLogger.logExportStatus(
                exportStatus, timeToErrorMillis, originalDataSizeKb, compressedDataSizeKb);
    }

    private void exportLocally(File destination) throws IOException {
        Slog.i(TAG, "Local export started.");

        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Unable to create directory for local export.");
        }

        Files.copy(
                mTransactionManager.getDatabasePath().toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        Slog.i(TAG, "Local export completed: " + destination.toPath().toAbsolutePath());
    }

    private void exportToUri(File source, Uri destination) throws IOException {
        Slog.i(TAG, "Export to URI started.");
        try (OutputStream outputStream =
                mDatabaseContext.getContentResolver().openOutputStream(destination)) {
            if (outputStream == null) {
                throw new IOException("Unable to copy data to URI for export.");
            }
            Files.copy(source.toPath(), outputStream);
            Slog.i(TAG, "Export to URI completed.");
        }
    }

    // TODO(b/325599879): Double check if we need to vacuum the database after clearing the tables.
    private void deleteLogTablesContent() {
        // Throwing a exception when calling this method implies that it was not possible to
        // create a HC database from the file and, therefore, most probably the database was
        // corrupted during the file copy.
        try (HealthConnectDatabase exportDatabase =
                new HealthConnectDatabase(mDatabaseContext, LOCAL_EXPORT_DATABASE_FILE_NAME)) {
            for (String tableName : TABLES_TO_CLEAR) {
                exportDatabase.getWritableDatabase().execSQL("DELETE FROM " + tableName + ";");
            }
        }
        Slog.i(TAG, "Drop log tables completed.");
    }

    /***
     * Returns the size of a file in Kb for logging
     *
     * To keep the log size small, the data type is an int32 rather than a long (int64).
     * Using an int allows logging sizes up to 2TB, which is sufficient for our use cases,
     */
    private int intSizeInKb(File file) {
        return (int) (file.length() / 1024.0);
    }

    /** Sends export status notification if export_import_fast_follow flag enabled. */
    private void sendNotificationIfEnabled(int notificationType) {
        if (exportImportFastFollow()) {
            mNotificationSender.sendNotificationAsUser(notificationType, mUserHandle);
        }
    }
}
