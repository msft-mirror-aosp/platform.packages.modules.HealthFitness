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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public ExportManager(@NonNull Context context) {
        requireNonNull(context);
        mDatabaseContext =
                DatabaseContext.create(context, LOCAL_EXPORT_DATABASE_DIR_NAME, context.getUser());
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
        } catch (IOException e) {
            Slog.e(TAG, "Failed to create local file for export.", e);
            return false;
        }
        try {
            deleteLogTablesContent();
        } catch (IOException e) {
            Slog.e(TAG, "Failed to drop log tables.", e);
            return false;
        }
        try {
            exportToUri(ExportImportSettingsStorage.getUri(), localExportFile.toPath());
        } catch (IOException e) {
            Slog.e(TAG, "Failed to export to URI.", e);
            return false;
        }
        // TODO(b/325599879): Clean local file.
        Slog.i(TAG, "Export completed.");
        return true;
    }

    private File exportLocally() throws IOException {
        Slog.i(TAG, "Local export started.");

        File exportDir = mDatabaseContext.getDatabaseDir();
        if (!exportDir.isDirectory() && !exportDir.mkdir()) {
            throw new IOException("Unable to create directory for local export.");
        }
        // Delete the file if it already exists before writing.
        File exportFile = new File(exportDir, LOCAL_EXPORT_DATABASE_FILE_NAME);
        if ((exportFile.exists() && !exportFile.delete()) || !exportFile.createNewFile()) {
            throw new IOException("Unable to create file for local export.");
        }

        ParcelFileDescriptor pfd =
                ParcelFileDescriptor.open(exportFile, ParcelFileDescriptor.MODE_WRITE_ONLY);
        try (FileOutputStream outputStream = new FileOutputStream(pfd.getFileDescriptor())) {
            // TODO(b/325599879): Add functionality for checking that the copy is not
            //  corrupted. If so, repeat the copy and check again a limited number of times.
            Files.copy(
                    TransactionManager.getInitialisedInstance().getDatabasePath().toPath(),
                    outputStream);
        } catch (SecurityException e) {
            Slog.e(TAG, "Failed to send data for local export.", e);
        } finally {
            try {
                pfd.close();
            } catch (IOException e) {
                Slog.e(TAG, "Failed to close stream for local export.", e);
            }
        }

        Slog.i(TAG, "Local export completed: " + exportFile.toPath().toAbsolutePath());
        return exportFile;
    }

    private void exportToUri(Uri destinationUri, Path originPath) throws IOException {
        Slog.i(TAG, "Export to URI started.");
        try (OutputStream outputStream =
                mDatabaseContext.getContentResolver().openOutputStream(destinationUri)) {
            Files.copy(originPath, outputStream);
            Slog.i(TAG, "Export to URI completed.");
        }
    }

    // TODO(b/325599879): Double check if we need to vacuum the database after clearing the tables.
    private void deleteLogTablesContent() throws IOException {
        Slog.i(TAG, "Drop log tables started.");
        try (HealthConnectDatabase exportDatabase =
                new HealthConnectDatabase(mDatabaseContext, LOCAL_EXPORT_DATABASE_FILE_NAME)) {
            for (String tableName : TABLES_TO_CLEAR) {
                exportDatabase.getWritableDatabase().execSQL("DELETE FROM " + tableName + ";");
            }
        } catch (Exception e) {
            throw new IOException("Unable to drop log tables for export database.");
        }
        Slog.i(TAG, "Drop log tables completed.");
    }
}
