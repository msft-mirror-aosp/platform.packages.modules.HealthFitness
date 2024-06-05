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

package com.android.healthconnect.controller.exportimport

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.ExportFrequencyRadioGroupPreference.Companion.EXPORT_FREQUENCY_RADIO_GROUP_PREFERENCE
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.ExportSettings
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import com.android.healthconnect.controller.exportimport.api.ExportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiState
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiStatus
import com.android.healthconnect.controller.shared.preference.HealthMainSwitchPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.utils.LocalDateTimeFormatter
import dagger.hilt.android.AndroidEntryPoint
import java.time.temporal.ChronoUnit

/** Fragment showing the status of configured automatic fragment. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ScheduledExportFragment : Hilt_ScheduledExportFragment() {

    // TODO: b/325917283 - Add proper logging for the automatic export fragment.
    companion object {
        const val SCHEDULED_EXPORT_CONTROL_PREFERENCE_KEY = "scheduled_export_control_preference"
        const val CHOOSE_FREQUENCY_PREFERENCE_KEY = "choose_frequency"
        const val EXPORT_STATUS_PREFERENCE_ORDER = 1
    }

    private val exportSettingsViewModel: ExportSettingsViewModel by viewModels()
    private val exportStatusViewModel: ExportStatusViewModel by viewModels()

    private val scheduledExportControlPreference: HealthMainSwitchPreference? by lazy {
        preferenceScreen.findPreference(SCHEDULED_EXPORT_CONTROL_PREFERENCE_KEY)
    }

    private val chooseFrequencyPreferenceGroup: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(CHOOSE_FREQUENCY_PREFERENCE_KEY)
    }

    private val dateFormatter: LocalDateTimeFormatter by lazy {
        LocalDateTimeFormatter(requireContext())
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.scheduled_export_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        exportStatusViewModel.storedScheduledExportStatus.observe(viewLifecycleOwner) {
            scheduledExportUiStatus ->
            when (scheduledExportUiStatus) {
                is ScheduledExportUiStatus.WithData -> {
                    maybeShowNextExportStatus(scheduledExportUiStatus.scheduledExportUiState)
                }
                else -> {
                    // do nothing
                }
            }
        }

        exportSettingsViewModel.storedExportSettings.observe(viewLifecycleOwner) { exportSettings ->
            when (exportSettings) {
                is ExportSettings.WithData -> {
                    if (exportSettings.frequency != ExportFrequency.EXPORT_FREQUENCY_NEVER) {
                        scheduledExportControlPreference?.isChecked = true
                        scheduledExportControlPreference?.title =
                            getString(R.string.automatic_export_on)
                        chooseFrequencyPreferenceGroup?.setVisible(true)
                    } else {
                        scheduledExportControlPreference?.isChecked = false
                        scheduledExportControlPreference?.title =
                            getString(R.string.automatic_export_off)
                        chooseFrequencyPreferenceGroup?.setVisible(false)
                    }
                    exportSettingsViewModel.updatePreviousExportFrequency(exportSettings.frequency)
                    if (chooseFrequencyPreferenceGroup?.findPreference<Preference>(
                        EXPORT_FREQUENCY_RADIO_GROUP_PREFERENCE) == null) {
                        val exportFrequencyPreference =
                            ExportFrequencyRadioGroupPreference(
                                requireContext(),
                                exportSettings.frequency,
                                exportSettingsViewModel::updateExportFrequency)
                        chooseFrequencyPreferenceGroup?.addPreference(exportFrequencyPreference)
                    }
                }
                is ExportSettings.LoadingFailed ->
                    Toast.makeText(requireActivity(), R.string.default_error, Toast.LENGTH_LONG)
                        .show()
                is ExportSettings.Loading -> {
                    // Do nothing.
                }
            }
        }

        scheduledExportControlPreference?.addOnSwitchChangeListener { _, isChecked ->
            if (isChecked) {
                exportSettingsViewModel.previousExportFrequency.value?.let { previousExportFrequency
                    ->
                    exportSettingsViewModel.updateExportFrequency(previousExportFrequency)
                }
            } else {
                exportSettingsViewModel.updateExportFrequency(
                    ExportFrequency.EXPORT_FREQUENCY_NEVER)
            }
        }
    }

    private fun maybeShowNextExportStatus(scheduledExportUiState: ScheduledExportUiState) {
        val lastSuccessfulExportTime = scheduledExportUiState.lastSuccessfulExportTime
        val periodInDays = scheduledExportUiState.periodInDays
        if (lastSuccessfulExportTime != null) {
            val nextExportTime =
                getString(
                    R.string.next_export_time,
                    dateFormatter.formatLongDate(
                        lastSuccessfulExportTime.plus(periodInDays.toLong(), ChronoUnit.DAYS)))
            preferenceScreen.addPreference(
                ExportStatusPreference(requireContext(), nextExportTime).also {
                    it.order = EXPORT_STATUS_PREFERENCE_ORDER
                })
        }
    }
}
