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
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.ExportStatusPreference
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.ExportSettings
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import com.android.healthconnect.controller.exportimport.api.ExportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.ImportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.ImportUiState
import com.android.healthconnect.controller.exportimport.api.ImportUiStatus
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiState
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiStatus
import com.android.healthconnect.controller.shared.preference.BannerPreference
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.DeviceInfoUtilsImpl
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import com.android.healthconnect.controller.utils.logging.BackupAndRestoreElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.pref
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint

/** Fragment displaying backup and restore settings. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class BackupAndRestoreSettingsFragment : Hilt_BackupAndRestoreSettingsFragment() {

    companion object {
        const val SCHEDULED_EXPORT_PREFERENCE_KEY = "scheduled_export"
        const val IMPORT_DATA_PREFERENCE_KEY = "import_data"
        const val EXPORT_IMPORT_SETTINGS_CATEGORY_PREFERENCE_KEY = "settings_category"
        const val IMPORT_ERROR_BANNER_KEY = "import_error_banner"
        const val IMPORT_ERROR_BANNER_ORDER = 0
        const val PREVIOUS_EXPORT_STATUS_ORDER = 2
    }

    init {
        this.setPageName(PageName.BACKUP_AND_RESTORE_PAGE)
    }

    private val exportSettingsViewModel: ExportSettingsViewModel by viewModels()
    private val exportStatusViewModel: ExportStatusViewModel by viewModels()
    private val importStatusViewModel: ImportStatusViewModel by viewModels()

    private val scheduledExportPreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(SCHEDULED_EXPORT_PREFERENCE_KEY)
    }

    private val importDataPreference: HealthPreference? by lazy {
        preferenceScreen.findPreference(IMPORT_DATA_PREFERENCE_KEY)
    }

    private val settingsCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(EXPORT_IMPORT_SETTINGS_CATEGORY_PREFERENCE_KEY)
    }

    private val dateFormatter: LocalDateTimeFormatter by lazy {
        LocalDateTimeFormatter(requireContext())
    }

    private val footerPreference: FooterPreference by pref("backup_restore_footer")

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.backup_and_restore_settings_screen, rootKey)

        footerPreference.setLearnMoreText(getString(R.string.backup_and_restore_footer_link_text))
        footerPreference.setLearnMoreAction {
            DeviceInfoUtilsImpl().openHCGetStartedLink(requireActivity())
        }

        scheduledExportPreference?.logName = BackupAndRestoreElement.SCHEDULED_EXPORT_BUTTON
        scheduledExportPreference?.setOnPreferenceClickListener {
            findNavController()
                .navigate(R.id.action_backupAndRestoreSettingsFragment_to_exportSetupActivity)
            true
        }

        importDataPreference?.logName = BackupAndRestoreElement.RESTORE_DATA_BUTTON
        importDataPreference?.setOnPreferenceClickListener {
            findNavController()
                .navigate(R.id.action_backupAndRestoreSettingsFragment_to_importFlowActivity)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        importStatusViewModel.storedImportStatus.observe(viewLifecycleOwner) { importUiStatus ->
            when (importUiStatus) {
                is ImportUiStatus.WithData -> {
                    maybeShowImportErrorBanner(importUiStatus.importUiState)
                }
                else -> {
                    // Do nothing.
                }
            }
        }

        exportStatusViewModel.storedScheduledExportStatus.observe(viewLifecycleOwner) {
            scheduledExportUiStatus ->
            when (scheduledExportUiStatus) {
                is ScheduledExportUiStatus.WithData -> {
                    maybeShowPreviousExportStatus(scheduledExportUiStatus.scheduledExportUiState)
                }
                else -> {
                    // do nothing
                }
            }
        }

        exportSettingsViewModel.storedExportSettings.observe(viewLifecycleOwner) { exportSettings ->
            when (exportSettings) {
                is ExportSettings.WithData -> {
                    val frequency = exportSettings.frequency
                    if (frequency == ExportFrequency.EXPORT_FREQUENCY_NEVER) {
                        scheduledExportPreference?.setOnPreferenceClickListener {
                            findNavController()
                                .navigate(
                                    R.id
                                        .action_backupAndRestoreSettingsFragment_to_exportSetupActivity)
                            true
                        }
                    } else {
                        scheduledExportPreference?.setOnPreferenceClickListener {
                            findNavController()
                                .navigate(
                                    R.id
                                        .action_backupAndRestoreSettingsFragment_to_scheduledExportFragment)
                            true
                        }
                    }
                    scheduledExportPreference?.summary = buildSummary(frequency)
                }
                is ExportSettings.LoadingFailed ->
                    Toast.makeText(activity, R.string.default_error, Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        exportSettingsViewModel.loadExportSettings()
        importStatusViewModel.loadImportStatus()
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

    private fun maybeShowPreviousExportStatus(scheduledExportUiState: ScheduledExportUiState) {
        val lastSuccessfulExportTime = scheduledExportUiState.lastSuccessfulExportTime
        if (lastSuccessfulExportTime != null &&
            settingsCategory?.findPreference<Preference>(
                ExportStatusPreference.EXPORT_STATUS_PREFERENCE) == null) {
            val lastExportTime =
                getString(
                    R.string.last_export_time,
                    dateFormatter.formatLongDate(lastSuccessfulExportTime))
            settingsCategory?.addPreference(
                ExportStatusPreference(requireContext(), lastExportTime).also {
                    it.order = PREVIOUS_EXPORT_STATUS_ORDER
                })
        }
    }

    private fun maybeShowImportErrorBanner(importUiState: ImportUiState) {
        val importErrorBanner = preferenceScreen.findPreference<Preference>(IMPORT_ERROR_BANNER_KEY)
        if (importErrorBanner != null) {
            preferenceScreen.removePreferenceRecursively(IMPORT_ERROR_BANNER_KEY)
        }
        when (importUiState.dataImportError) {
            ImportUiState.DataImportError.DATA_IMPORT_ERROR_WRONG_FILE -> {
                preferenceScreen.addPreference(getImportWrongFileErrorBanner())
            }
            ImportUiState.DataImportError.DATA_IMPORT_ERROR_VERSION_MISMATCH -> {
                preferenceScreen.addPreference(getImportVersionMismatchErrorBanner())
            }
            ImportUiState.DataImportError.DATA_IMPORT_ERROR_UNKNOWN -> {
                preferenceScreen.addPreference(getImportOtherErrorBanner())
            }
            ImportUiState.DataImportError.DATA_IMPORT_ERROR_NONE -> {
                // Do nothing.
            }
        }
    }

    private fun getImportWrongFileErrorBanner(): BannerPreference {
        return BannerPreference(
                requireContext(), BackupAndRestoreElement.IMPORT_WRONG_FILE_ERROR_BANNER)
            .also {
                it.setPrimaryButton(
                    getString(R.string.import_wrong_file_error_banner_button),
                    BackupAndRestoreElement.IMPORT_WRONG_FILE_ERROR_BANNER_BUTTON,
                )
                it.title = getString(R.string.import_error_banner_title)
                it.key = IMPORT_ERROR_BANNER_KEY
                it.summary = getString(R.string.import_wrong_file_error_banner_summary)
                it.icon =
                    AttributeResolver.getNullableDrawable(requireContext(), R.attr.warningIcon)
                it.setPrimaryButtonOnClickListener {
                    findNavController()
                        .navigate(
                            R.id.action_backupAndRestoreSettingsFragment_to_importFlowActivity)
                }
                it.order = IMPORT_ERROR_BANNER_ORDER
            }
    }

    private fun getImportVersionMismatchErrorBanner(): BannerPreference {
        return BannerPreference(
                requireContext(), BackupAndRestoreElement.IMPORT_VERSION_MISMATCH_ERROR_BANNER)
            .also {
                it.setPrimaryButton(
                    getString(R.string.import_version_mismatch_error_banner_button),
                    BackupAndRestoreElement.IMPORT_VERSION_MISMATCH_ERROR_BANNER_BUTTON,
                )
                it.title = getString(R.string.import_error_banner_title)
                it.key = IMPORT_ERROR_BANNER_KEY
                it.summary = getString(R.string.import_version_mismatch_error_banner_summary)
                it.icon =
                    AttributeResolver.getNullableDrawable(requireContext(), R.attr.warningIcon)
                it.setPrimaryButtonOnClickListener {
                    findNavController()
                        .navigate(
                            R.id.action_backupAndRestoreSettingsFragment_to_systemUpdateActivity)
                }
                it.order = IMPORT_ERROR_BANNER_ORDER
            }
    }

    private fun getImportOtherErrorBanner(): BannerPreference {
        return BannerPreference(
                requireContext(), BackupAndRestoreElement.IMPORT_GENERAL_ERROR_BANNER)
            .also {
                it.setPrimaryButton(
                    getString(R.string.import_other_error_banner_button),
                    BackupAndRestoreElement.IMPORT_GENERAL_ERROR_BANNER_BUTTON,
                )
                it.title = getString(R.string.import_error_banner_title)
                it.key = IMPORT_ERROR_BANNER_KEY
                it.summary = getString(R.string.import_other_error_banner_summary)
                it.icon =
                    AttributeResolver.getNullableDrawable(requireContext(), R.attr.warningIcon)
                it.setPrimaryButtonOnClickListener {
                    findNavController()
                        .navigate(
                            R.id.action_backupAndRestoreSettingsFragment_to_importFlowActivity)
                }
                it.order = IMPORT_ERROR_BANNER_ORDER
            }
    }
}
