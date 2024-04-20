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

import android.annotation.NonNull;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper;
import com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper;
import com.android.server.healthconnect.utils.FilesUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private static final String EXPORT_DATABASE_DIR_NAME = "export_data";
    private static final String EXPORT_DATABASE_FILE_NAME = "healthconnect_export.db";
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

    private final Context mContext;

    // TODO(b/325599879): Discuss if this is going to be a singleton or new instance every export.
    public ExportManager(@NonNull Context context) {
        mContext = context;
    }

    // TODO(b/325599879): Change visibility once there is a wrapper.

    /** Writes the backup data into a local file. */
    public File exportLocally() {
        Slog.d(TAG, "Incoming request to make a local copy for export");

        File exportDir =
                new File(
                        FilesUtil.getDataSystemCeHCDirectoryForUser(
                                mContext.getUser().getIdentifier()),
                        EXPORT_DATABASE_DIR_NAME);
        exportDir.mkdirs();
        File exportFile = new File(exportDir, EXPORT_DATABASE_FILE_NAME);

        ParcelFileDescriptor pfd;
        try {
            exportFile.createNewFile();
            pfd = ParcelFileDescriptor.open(exportFile, ParcelFileDescriptor.MODE_WRITE_ONLY);
            try (FileOutputStream outputStream = new FileOutputStream(pfd.getFileDescriptor())) {
                // TODO(b/325599879): Double check if we want to use a database copy method instead.
                Files.copy(
                        TransactionManager.getInitialisedInstance().getDatabasePath().toPath(),
                        outputStream);
            } catch (IOException | SecurityException e) {
                Slog.e(TAG, "Failed to send data for export", e);
            } finally {
                try {
                    pfd.close();
                } catch (IOException e) {
                    Slog.e(TAG, "Failed to close stream for export", e);
                }
            }
        } catch (IOException e) {
            Slog.e(TAG, "Failed to create file for export", e);
        }

        deleteLogTablesContent();

        return exportFile;
    }

    // TODO(b/325599879): Double check if we need to vacuum the database after clearing the tables.
    private void deleteLogTablesContent() {
        try (HealthConnectDatabase exportDatabase =
                new HealthConnectDatabase(
                        mContext,
                        Path.of(EXPORT_DATABASE_DIR_NAME, EXPORT_DATABASE_FILE_NAME).toString())) {
            for (String tableName : TABLES_TO_CLEAR) {
                exportDatabase.getWritableDatabase().execSQL("DELETE FROM " + tableName + ";");
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to drop log tables for export database", e);
        }
    }
}
