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

import android.health.connect.exportimport.ExportImportDocumentProvider;

import java.util.List;

/**
 * Manages querying the document providers available to use for export/import.
 *
 * @hide
 */
public final class DocumentProvidersManager {
    /** Returns the document providers available to be used for export/import. */
    public static List<ExportImportDocumentProvider> queryDocumentProviders() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
