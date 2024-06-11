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

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.health.connect.HealthConnectManager;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.ExportImportSettingsStorage;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * Class that manages export related tasks. In this context, export means to make an encrypted copy
 * of Health Connect data that the user can store in some online storage solution.
 *
 * @hide
 */
public class ExportManager {

    @VisibleForTesting static final String LOCAL_EXPORT_DATABASE_DIR_NAME = "export_import";

    @VisibleForTesting
    static final String LOCAL_EXPORT_DATABASE_FILE_NAME = "health_connect_export.db";

    private static final String TAG = "HealthConnectExportImport";

    private Clock mClock;

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

    public ExportManager(@NonNull Context context, Clock clock) {
        requireNonNull(context);
        requireNonNull(clock);
        mDatabaseContext =
                DatabaseContext.create(context, LOCAL_EXPORT_DATABASE_DIR_NAME, context.getUser());
        mClock = clock;
    }

    /**
     * Makes a local copy of the HC database, deletes the unnecessary data for export and sends the
     * data to a cloud provider.
     */
    public synchronized boolean runExport() {
        Slog.i(TAG, "Export started.");
        File localExportFile;
        try {
            localExportFile = exportLocally();
        } catch (Exception e) {
            Slog.e(TAG, "Failed to create local file for export", e);
            ExportImportSettingsStorage.setLastExportError(
                    HealthConnectManager.DATA_EXPORT_ERROR_UNKNOWN);
            deleteLocalExportFile(
                    new File(mDatabaseContext.getDatabaseDir(), LOCAL_EXPORT_DATABASE_FILE_NAME));
            return false;
        }

        try {
            try {
                deleteLogTablesContent(LOCAL_EXPORT_DATABASE_FILE_NAME);
            } catch (Exception e) {
                Slog.e(TAG, "Failed to prepare local file for export", e);
                ExportImportSettingsStorage.setLastExportError(
                        HealthConnectManager.DATA_EXPORT_ERROR_UNKNOWN);
                return false;
            }

            try {
                exportToUri(ExportImportSettingsStorage.getUri(), localExportFile.toPath());
            } catch (FileNotFoundException e) {
                Slog.e(TAG, "Lost access to export location", e);
                ExportImportSettingsStorage.setLastExportError(
                        HealthConnectManager.DATA_EXPORT_LOST_FILE_ACCESS);
                return false;
            } catch (Exception e) {
                Slog.e(TAG, "Failed to export to URI", e);
                ExportImportSettingsStorage.setLastExportError(
                        HealthConnectManager.DATA_EXPORT_ERROR_UNKNOWN);
                return false;
            }

            Slog.i(TAG, "Export completed.");
            ExportImportSettingsStorage.setLastSuccessfulExport(mClock.instant());
            return true;
        } finally {
            deleteLocalExportFile(localExportFile);
        }
    }

    private File exportLocally() throws IOException {
        Slog.i(TAG, "Local export started.");

        File exportFile = getExportFile(LOCAL_EXPORT_DATABASE_FILE_NAME);

        try (ParcelFileDescriptor pfd =
                ParcelFileDescriptor.open(exportFile, ParcelFileDescriptor.MODE_WRITE_ONLY)) {
            if (pfd == null) {
                throw new IOException("Unable to copy data to local file for export");
            }
            try (FileOutputStream outputStream = new FileOutputStream(pfd.getFileDescriptor())) {
                // TODO(b/325599879): Replace with SQLite Online Backup API
                Files.copy(
                        TransactionManager.getInitialisedInstance().getDatabasePath().toPath(),
                        outputStream);
            }
        }

        Slog.i(TAG, "Local export completed: " + exportFile.toPath().toAbsolutePath());
        return exportFile;
    }

    private File getExportFile(String dbName) throws IOException {
        File exportDir = mDatabaseContext.getDatabaseDir();
        if (!exportDir.isDirectory() && !exportDir.mkdir()) {
            throw new IOException("Unable to create directory for local export.");
        }
        // Delete the file if it already exists before writing.
        File exportFile = new File(exportDir, dbName);
        if ((exportFile.exists() && !exportFile.delete()) || !exportFile.createNewFile()) {
            throw new IOException("Unable to create file for local export.");
        }
        Slog.i(TAG, "Local export completed: " + exportFile.toPath().toAbsolutePath());
        return exportFile;
    }

    private void exportToUri(Uri destinationUri, Path originPath) throws IOException {
        Slog.i(TAG, "Export to URI started.");
        try (OutputStream outputStream =
                mDatabaseContext.getContentResolver().openOutputStream(destinationUri)) {
            if (outputStream == null) {
                throw new IOException("Unable to copy data to URI for export.");
            }
            Files.copy(originPath, outputStream);
            Slog.i(TAG, "Export to URI completed.");
        }
    }

    // TODO(b/325599879): Double check if we need to vacuum the database after clearing the tables.

    private void deleteLogTablesContent(String dbName) throws IOException {
        try (HealthConnectDatabase exportDatabase =
                new HealthConnectDatabase(mDatabaseContext, dbName)) {
            for (String tableName : TABLES_TO_CLEAR) {
                exportDatabase.getWritableDatabase().execSQL("DELETE FROM " + tableName + ";");
            }
        } catch (Exception e) {
            // This exception is not passed up the stack for error handling, because it has no
            // user visible effect other than the data being larger.
            Slog.e(TAG, "Unable to drop log tables for export database.");
        }
        Slog.i(TAG, "Drop log tables completed.");
    }

    private void deleteLocalExportFile(File localExportFile) {
        Slog.i(TAG, "Delete local export file started.");
        if (localExportFile.exists()) {
            SQLiteDatabase.deleteDatabase(localExportFile);
        }
        Slog.i(TAG, "Delete local export file completed.");
    }
}
