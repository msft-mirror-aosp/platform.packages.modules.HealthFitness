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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.ExportSettings
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

/** Export frequency fragment for Health Connect. */
@AndroidEntryPoint(Fragment::class)
class ExportFrequencyFragment : Hilt_ExportFrequencyFragment() {

    private val viewModel: ExportSettingsViewModel by viewModels()

    // TODO: b/325917283 - Add proper logging for the export frequency fragment.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.export_frequency_screen, container, false)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_frequency)
        val backButton = view.findViewById<Button>(R.id.export_import_cancel_button)
        val nextButton = view.findViewById<Button>(R.id.export_import_next_button)

        backButton.text = getString(R.string.export_back_button)
        nextButton.text = getString(R.string.export_next_button)

        backButton.setOnClickListener { requireActivity().finish() }
        nextButton.setOnClickListener {
            val checkedId = radioGroup.checkedRadioButtonId
            when (checkedId) {
                R.id.radio_button_daily ->
                    viewModel.updateExportFrequency(ExportFrequency.EXPORT_FREQUENCY_DAILY)
                R.id.radio_button_weekly ->
                    viewModel.updateExportFrequency(ExportFrequency.EXPORT_FREQUENCY_WEEKLY)
                R.id.radio_button_monthly ->
                    viewModel.updateExportFrequency(ExportFrequency.EXPORT_FREQUENCY_MONTHLY)
            }
            findNavController()
                .navigate(R.id.action_exportFrequencyFragment_to_exportDestinationFragment)
        }

        viewModel.storedExportSettings.observe(viewLifecycleOwner) { exportSettings ->
            when (exportSettings) {
                is ExportSettings.WithData ->
                    when (exportSettings.frequency) {
                        ExportFrequency.EXPORT_FREQUENCY_WEEKLY ->
                            radioGroup?.check(R.id.radio_button_weekly)
                        ExportFrequency.EXPORT_FREQUENCY_MONTHLY ->
                            radioGroup?.check(R.id.radio_button_monthly)
                        ExportFrequency.EXPORT_FREQUENCY_DAILY ->
                            radioGroup?.check(R.id.radio_button_daily)
                        ExportFrequency.EXPORT_FREQUENCY_NEVER ->
                            radioGroup?.check(R.id.radio_button_daily)
                    }
                else -> {}
            }
        }
        return view
    }
}
