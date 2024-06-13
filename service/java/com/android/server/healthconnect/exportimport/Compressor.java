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
import android.util.Slog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class Compressor {

    private static final String TAG = "HealthConnectCompressor";

    /** Compresses the source file */
    static void compress(@NonNull File source, @NonNull File zip) throws IOException {
        try {
            zip.mkdirs();
            zip.delete();
            ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(zip));
            outputStream.putNextEntry(new ZipEntry(source.getName()));
            FileInputStream inputStream = new FileInputStream(source);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = inputStream.read(bytes)) >= 0) {
                outputStream.write(bytes, 0, length);
            }
            outputStream.closeEntry();
            outputStream.close();
            inputStream.close();
            Slog.i(TAG, "File zipped: " + zip.getAbsolutePath());
        } catch (Exception e) {
            Slog.e(TAG, "Failed to compress", e);
            zip.delete();
            throw e;
        }
    }

    /** Decompresses the zip file */
    static void decompress(@NonNull File zip, @NonNull File destination) throws IOException {
        try {
            destination.mkdirs();
            destination.delete();
            ZipInputStream inputStream = new ZipInputStream(new FileInputStream(zip));
            inputStream.getNextEntry();
            FileOutputStream outputStream = new FileOutputStream(destination);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            inputStream.closeEntry();
            inputStream.close();
            outputStream.close();
            Slog.i(TAG, "File unzipped: " + destination.getAbsolutePath());
        } catch (Exception e) {
            Slog.e(TAG, "Failed to decompress", e);
            destination.delete();
            throw e;
        }
    }
}
