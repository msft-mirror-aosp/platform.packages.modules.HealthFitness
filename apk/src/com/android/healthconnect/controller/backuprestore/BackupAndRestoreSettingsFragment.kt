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

package com.android.healthconnect.controller.backuprestore

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.export.api.ExportFrequency
import com.android.healthconnect.controller.export.api.ExportSettings
import com.android.healthconnect.controller.export.api.ExportSettingsViewModel
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import dagger.hilt.android.AndroidEntryPoint

/** Fragment displaying backup and restore settings. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class BackupAndRestoreSettingsFragment : Hilt_BackupAndRestoreSettingsFragment() {
    // TODO: b/330169060 - Add proper logging for the backup and restore settings fragment.

    companion object {
        const val EXPORT_AUTOMATICALLY_PREFERENCE_KEY = "export_automatically"
    }

    private val exportSettingsViewModel: ExportSettingsViewModel by viewModels()

    private val exportAutomaticallyPreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(EXPORT_AUTOMATICALLY_PREFERENCE_KEY)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.backup_and_restore_settings_screen, rootKey)

        exportAutomaticallyPreference?.setOnPreferenceClickListener {
            findNavController()
                .navigate(R.id.action_backupAndRestoreSettingsFragment_to_exportSetupActivity)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        exportSettingsViewModel.storedExportSettings.observe(viewLifecycleOwner) { exportSettings ->
            when (exportSettings) {
                is ExportSettings.WithData ->
                    exportAutomaticallyPreference?.summary = buildSummary(exportSettings.frequency)
                is ExportSettings.LoadingFailed ->
                    Toast.makeText(activity, R.string.default_error, Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exportSettingsViewModel.loadExportSettings()
    }

    private fun buildSummary(frequency: ExportFrequency): String {
        val on = getString(R.string.automatic_export_on)
        val automaticExportStatusId = R.string.automatic_export_status
        return when (frequency) {
            ExportFrequency.EXPORT_FREQUENCY_NEVER -> getString(R.string.automatic_export_off)
            ExportFrequency.EXPORT_FREQUENCY_DAILY ->
                getString(automaticExportStatusId, on, getString(R.string.frequency_daily))
            ExportFrequency.EXPORT_FREQUENCY_WEEKLY ->
                getString(automaticExportStatusId, on, getString(R.string.frequency_weekly))
            ExportFrequency.EXPORT_FREQUENCY_MONTHLY ->
                getString(automaticExportStatusId, on, getString(R.string.frequency_monthly))
        }
    }
}
