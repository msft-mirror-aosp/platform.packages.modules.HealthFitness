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

import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_ERROR_CLEARING_LOG_TABLES;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_ERROR_NONE;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_ERROR_UNKNOWN;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_LOST_FILE_ACCESS;
import static android.health.connect.exportimport.ScheduledExportStatus.DATA_EXPORT_STARTED;

import static com.android.healthfitness.flags.Flags.exportImportFastFollow;
import static com.android.server.healthconnect.exportimport.ExportImportNotificationSender.NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR;
import static com.android.server.healthconnect.logging.ExportImportLogger.NO_VALUE_RECORDED;

import static java.util.Objects.requireNonNull;

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
    private final File mLocalExportDbFile;
    private final File mLocalExportZipFile;
    private final ExportImportSettingsStorage mExportImportSettingsStorage;

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

    public ExportManager(
            Context context,
            Clock clock,
            ExportImportSettingsStorage exportImportSettingsStorage,
            TransactionManager transactionManager) {
        this(
                context,
                clock,
                ExportImportNotificationSender.createSender(context),
                exportImportSettingsStorage,
                transactionManager);
    }

    public ExportManager(
            Context context,
            Clock clock,
            HealthConnectNotificationSender notificationSender,
            ExportImportSettingsStorage exportImportSettingsStorage,
            TransactionManager transactionManager) {
        requireNonNull(context);
        requireNonNull(clock);
        requireNonNull(notificationSender);

        mUserHandle = context.getUser();
        Context userContext = context.createContextAsUser(mUserHandle, 0);

        mClock = clock;
        mDatabaseContext = DatabaseContext.create(userContext, LOCAL_EXPORT_DIR_NAME, mUserHandle);
        mTransactionManager = transactionManager;
        mNotificationSender = notificationSender;
        mLocalExportDbFile =
                new File(mDatabaseContext.getDatabaseDir(), LOCAL_EXPORT_DATABASE_FILE_NAME);
        mLocalExportZipFile =
                new File(mDatabaseContext.getDatabaseDir(), LOCAL_EXPORT_ZIP_FILE_NAME);
        mExportImportSettingsStorage = exportImportSettingsStorage;
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

        try {
            try {
                exportLocally(mLocalExportDbFile);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to create local file for export", e);
                Slog.d(TAG, "original file size: " + intSizeInKb(mLocalExportDbFile));
                recordError(
                        DATA_EXPORT_ERROR_UNKNOWN,
                        startTimeMillis,
                        intSizeInKb(mLocalExportDbFile),
                        /* Compressed size will be 0, not yet compressed */
                        intSizeInKb(mLocalExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            }

            try {
                deleteLogTablesContent();
            } catch (Exception e) {
                Slog.e(TAG, "Failed to clear log tables in preparation for export", e);
                Slog.d(TAG, "Original file size: " + intSizeInKb(mLocalExportDbFile));
                recordError(
                        DATA_EXPORT_ERROR_CLEARING_LOG_TABLES,
                        startTimeMillis,
                        intSizeInKb(mLocalExportDbFile),
                        /* Compressed size will be 0, not yet compressed */
                        intSizeInKb(mLocalExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            }

            try {
                Compressor.compress(
                        mLocalExportDbFile, LOCAL_EXPORT_DATABASE_FILE_NAME, mLocalExportZipFile);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to compress local file for export", e);
                Slog.d(TAG, "Original file size: " + intSizeInKb(mLocalExportDbFile));
                recordError(
                        DATA_EXPORT_ERROR_UNKNOWN,
                        startTimeMillis,
                        intSizeInKb(mLocalExportDbFile),
                        /* Compressed size will be 0, not yet compressed */
                        intSizeInKb(mLocalExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            }

            Uri destinationUri = mExportImportSettingsStorage.getUri();
            try {
                exportToUri(mLocalExportZipFile, destinationUri);
            } catch (FileNotFoundException e) {
                Slog.e(TAG, "Lost access to export location", e);
                Slog.d(TAG, "Original file size: " + intSizeInKb(mLocalExportDbFile));
                recordError(
                        DATA_EXPORT_LOST_FILE_ACCESS,
                        startTimeMillis,
                        intSizeInKb(mLocalExportDbFile),
                        intSizeInKb(mLocalExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            } catch (Exception e) {
                Slog.e(TAG, "Failed to export to URI", e);
                Slog.d(TAG, "Original file size: " + intSizeInKb(mLocalExportDbFile));
                recordError(
                        DATA_EXPORT_ERROR_UNKNOWN,
                        startTimeMillis,
                        intSizeInKb(mLocalExportDbFile),
                        intSizeInKb(mLocalExportZipFile));
                sendNotificationIfEnabled(NOTIFICATION_TYPE_EXPORT_UNSUCCESSFUL_GENERIC_ERROR);
                return false;
            }
            Slog.i(TAG, "Export completed.");
            Slog.d(TAG, "Original file size: " + intSizeInKb(mLocalExportDbFile));
            recordSuccess(
                    startTimeMillis,
                    intSizeInKb(mLocalExportDbFile),
                    intSizeInKb(mLocalExportZipFile),
                    destinationUri);
            return true;
        } finally {
            deleteLocalExportFiles();
        }
    }

    protected void recordSuccess(
            long startTimeMillis,
            int originalDataSizeKb,
            int compressedDataSizeKb,
            Uri destinationUri) {
        mExportImportSettingsStorage.setLastSuccessfulExport(mClock.instant(), destinationUri);

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
        mExportImportSettingsStorage.setLastExportError(exportStatus, mClock.instant());

        // Convert to int to save on logs storage, int can hold about 68 years
        int timeToErrorMillis = (int) (mClock.millis() - startTimeMillis);
        ExportImportLogger.logExportStatus(
                exportStatus, timeToErrorMillis, originalDataSizeKb, compressedDataSizeKb);
    }

    void deleteLocalExportFiles() {
        Slog.i(TAG, "Delete local export files started.");
        if (mLocalExportDbFile.exists()) {
            SQLiteDatabase.deleteDatabase(mLocalExportDbFile);
        }
        if (mLocalExportZipFile.exists()) {
            mLocalExportZipFile.delete();
        }
        Slog.i(TAG, "Delete local export files completed.");
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
