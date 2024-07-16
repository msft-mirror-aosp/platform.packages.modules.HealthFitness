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
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.api.ExportFrequency
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import com.android.healthconnect.controller.utils.logging.ExportFrequencyElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Export frequency fragment for Health Connect. */
@AndroidEntryPoint(Fragment::class)
class ExportFrequencyFragment : Hilt_ExportFrequencyFragment() {

    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: ExportSettingsViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        logger.setPageId(PageName.EXPORT_FREQUENCY_PAGE)
        val view = inflater.inflate(R.layout.export_frequency_screen, container, false)

        val backButton = view.findViewById<Button>(R.id.export_import_cancel_button)
        val nextButton = view.findViewById<Button>(R.id.export_import_next_button)

        backButton.text = getString(R.string.export_cancel_button)
        nextButton.text = getString(R.string.export_next_button)

        logger.logImpression(ExportFrequencyElement.EXPORT_FREQUENCY_BACK_BUTTON)
        logger.logImpression(ExportFrequencyElement.EXPORT_FREQUENCY_NEXT_BUTTON)
        logger.logImpression(ExportFrequencyElement.EXPORT_FREQUENCY_DAILY_BUTTON)
        logger.logImpression(ExportFrequencyElement.EXPORT_FREQUENCY_WEEKLY_BUTTON)
        logger.logImpression(ExportFrequencyElement.EXPORT_FREQUENCY_MONTHLY_BUTTON)

        backButton.setOnClickListener {
            logger.logInteraction(ExportFrequencyElement.EXPORT_FREQUENCY_BACK_BUTTON)
            requireActivity().finish()
        }

        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_frequency)

        nextButton.setOnClickListener {
            logger.logInteraction(ExportFrequencyElement.EXPORT_FREQUENCY_NEXT_BUTTON)
            val checkedId = radioGroup.checkedRadioButtonId
            when (checkedId) {
                R.id.radio_button_daily ->
                    logger.logInteraction(ExportFrequencyElement.EXPORT_FREQUENCY_DAILY_BUTTON)
                R.id.radio_button_weekly ->
                    logger.logInteraction(ExportFrequencyElement.EXPORT_FREQUENCY_WEEKLY_BUTTON)
                R.id.radio_button_monthly ->
                    logger.logInteraction(ExportFrequencyElement.EXPORT_FREQUENCY_MONTHLY_BUTTON)
            }
            findNavController()
                .navigate(R.id.action_exportFrequencyFragment_to_exportDestinationFragment)
        }

        viewModel.selectedExportFrequency.observe(viewLifecycleOwner) {
            exportFrequency: ExportFrequency? ->
            when (exportFrequency) {
                ExportFrequency.EXPORT_FREQUENCY_WEEKLY ->
                    radioGroup?.check(R.id.radio_button_weekly)
                ExportFrequency.EXPORT_FREQUENCY_MONTHLY ->
                    radioGroup?.check(R.id.radio_button_monthly)
                ExportFrequency.EXPORT_FREQUENCY_DAILY -> radioGroup?.check(R.id.radio_button_daily)
                ExportFrequency.EXPORT_FREQUENCY_NEVER -> radioGroup?.check(R.id.radio_button_daily)
                else -> radioGroup?.check(R.id.radio_button_daily)
            }
        }

        radioGroup?.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_button_daily ->
                    viewModel.updateSelectedFrequency(ExportFrequency.EXPORT_FREQUENCY_DAILY)
                R.id.radio_button_weekly ->
                    viewModel.updateSelectedFrequency(ExportFrequency.EXPORT_FREQUENCY_WEEKLY)
                R.id.radio_button_monthly ->
                    viewModel.updateSelectedFrequency(ExportFrequency.EXPORT_FREQUENCY_MONTHLY)
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        logger.logPageImpression()
    }
}
