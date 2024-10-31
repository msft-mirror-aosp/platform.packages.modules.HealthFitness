/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.dataentries.formatters.medical

import android.content.Context
import android.health.connect.datatypes.MedicalDataSource
import android.health.connect.datatypes.MedicalResource
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.MedicalDataSourceReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Formatter for medical entries. */
@Singleton
class MedicalEntryFormatter
@Inject
constructor(
    private val medicalDataSourceReader: MedicalDataSourceReader,
    private val appInfoReader: AppInfoReader,
    private val displayNameExtractor: DisplayNameExtractor,
    @ApplicationContext private val context: Context,
) {
    suspend fun formatResource(
        resource: MedicalResource,
        showDataOrigin: Boolean,
    ): FormattedEntry.FormattedMedicalDataEntry {

        val dataSources = medicalDataSourceReader.fromDataSourceId(resource.dataSourceId)
        val dataSource = dataSources.getOrNull(0)
        val dataSourceName = dataSource?.displayName ?: ""
        val appName: String = if (showDataOrigin) getAppName(dataSource) else ""

        val header = getHeader(dataSourceName, appName)
        return FormattedEntry.FormattedMedicalDataEntry(
            header = header,
            headerA11y = header,
            title = displayNameExtractor.getDisplayName(resource.fhirResource.data),
            titleA11y = displayNameExtractor.getDisplayName(resource.fhirResource.data),
            medicalResourceId = resource.id,
        )
    }

    private fun getHeader(dataSourceName: String, appName: String): String {
        if (dataSourceName == "" && appName == "") {
            return ""
        }
        if (dataSourceName == "") {
            return appName
        }
        if (appName == "") {
            return dataSourceName
        }

        return context.getString(
            R.string.data_entry_header_with_source_app,
            appName,
            dataSourceName,
        )
    }

    private suspend fun getAppName(dataSource: MedicalDataSource?): String {
        dataSource?.let {
            return appInfoReader.getAppMetadata(dataSource.packageName).appName
        }
        return ""
    }
}
