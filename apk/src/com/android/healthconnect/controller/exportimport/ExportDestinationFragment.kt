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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.api.DocumentProviders
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import com.android.healthconnect.controller.exportimport.api.ExportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.isLocalFile
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.TimeSource
import com.android.healthconnect.controller.utils.logging.ExportDestinationElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthfitness.flags.Flags.exportImportFastFollow
import com.android.settingslib.widget.LinkTextView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Export destination fragment for Health Connect. */
@AndroidEntryPoint(Fragment::class)
class ExportDestinationFragment : Hilt_ExportDestinationFragment() {
    private val contract = ActivityResultContracts.StartActivityForResult()
    private val saveResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(contract, ::onSave)

    private val viewModel: ExportSettingsViewModel by activityViewModels()
    private val exportStatusViewModel: ExportStatusViewModel by activityViewModels()

    @Inject lateinit var deviceInfoUtils: DeviceInfoUtils
    @Inject lateinit var logger: HealthConnectLogger
    @Inject lateinit var timeSource: TimeSource

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        logger.setPageId(PageName.EXPORT_DESTINATION_PAGE)
        val view = inflater.inflate(R.layout.export_destination_screen, container, false)
        val titleView = view.findViewById<View>(R.id.export_destination_title)
        val footerView = view.findViewById<View>(R.id.export_import_footer)
        val footerIconView = view.findViewById<View>(R.id.export_import_footer_icon)
        val footerTextView = view.findViewById<TextView>(R.id.export_import_footer_text)
        val playStoreView = view.findViewById<LinkTextView>(R.id.export_import_go_to_play_store)
        val backButton = view.findViewById<Button>(R.id.export_import_cancel_button)
        val nextButton = view.findViewById<Button>(R.id.export_import_next_button)

        logger.logImpression(ExportDestinationElement.EXPORT_DESTINATION_BACK_BUTTON)
        logger.logImpression(ExportDestinationElement.EXPORT_DESTINATION_NEXT_BUTTON)

        // Make sure the focus is set to the title rather than on the next button from the previous
        // screen.
        titleView.requestFocus()

        backButton?.text = getString(R.string.export_back_button)
        backButton?.setOnClickListener {
            logger.logInteraction(ExportDestinationElement.EXPORT_DESTINATION_BACK_BUTTON)
            findNavController()
                .navigate(R.id.action_exportDestinationFragment_to_exportFrequencyFragment)
        }

        nextButton.text = getString(R.string.export_next_button)
        nextButton.setEnabled(false)

        if (deviceInfoUtils.isPlayStoreAvailable(requireContext())) {
            playStoreView?.setVisibility(VISIBLE)
            playStoreView?.setOnClickListener {
                findNavController().navigate(R.id.action_exportDestinationFragment_to_playStore)
            }
        }

        val documentProvidersViewBinder = DocumentProvidersViewBinder()
        val documentProvidersList = view.findViewById<ViewGroup>(R.id.export_document_providers)
        viewModel.documentProviders.observe(viewLifecycleOwner) { providers ->
            documentProvidersList.removeAllViews()
            nextButton.setOnClickListener {}
            nextButton.setEnabled(false)

            footerView.setVisibility(GONE)

            when (providers) {
                is DocumentProviders.Loading -> {
                    // Do nothing
                }
                is DocumentProviders.LoadingFailed -> {
                    Toast.makeText(activity, R.string.default_error, Toast.LENGTH_LONG).show()
                }
                is DocumentProviders.WithData -> {
                    documentProvidersViewBinder.bindDocumentProvidersView(
                        providers.providers,
                        viewModel.selectedDocumentProvider.value,
                        viewModel.selectedDocumentProviderRoot.value,
                        viewModel.selectedRootsForDocumentProviders,
                        documentProvidersList,
                        inflater,
                    ) { provider, root ->
                        viewModel.updateSelectedDocumentProvider(provider, root)
                        nextButton.setOnClickListener {
                            logger.logInteraction(
                                ExportDestinationElement.EXPORT_DESTINATION_NEXT_BUTTON
                            )
                            saveResultLauncher.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT)
                                    .addFlags(
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                    .setType("application/zip")
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .putExtra(DocumentsContract.EXTRA_INITIAL_URI, root.uri)
                                    .putExtra(Intent.EXTRA_TITLE, getDefaultFileName())
                            )
                        }
                        nextButton.setEnabled(true)
                    }

                    if (providers.providers.size > 1) {
                        footerView.setVisibility(GONE)
                    } else {
                        footerView.setVisibility(VISIBLE)

                        if (providers.providers.isEmpty()) {
                            footerIconView.setVisibility(GONE)
                            footerTextView.setText(R.string.export_import_no_apps_text)
                        } else {
                            footerIconView.setVisibility(VISIBLE)
                            footerTextView.setText(R.string.export_import_install_apps_text)
                        }
                    }
                }
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        exportStatusViewModel.loadScheduledExportStatus()
        logger.logPageImpression()
    }

    private fun onSave(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val fileUri = result.data?.data ?: return
            if (isLocalFile(fileUri)) {
                Toast.makeText(activity, R.string.export_invalid_storage, Toast.LENGTH_LONG).show()
            } else {
                viewModel.updateExportUriWithSelectedFrequency(fileUri)
                requireActivity().setResult(Activity.RESULT_OK, Intent())
                requireActivity().finish()
            }
        }
    }

    private fun getDefaultFileName(): String {
        val sequentialNumber = exportStatusViewModel.storedNextExportSequentialNumber.value
        if (exportImportFastFollow() && sequentialNumber !== null && sequentialNumber != 0) {
            return getString(
                R.string.export_default_file_name_with_sequence,
                sequentialNumber.toString(),
            ) + ".zip"
        }
        return getString(R.string.export_default_file_name) + ".zip"
    }
}
