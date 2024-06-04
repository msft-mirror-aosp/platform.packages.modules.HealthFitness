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
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.api.DocumentProviders
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import com.android.healthconnect.controller.exportimport.api.isLocalFile
import dagger.hilt.android.AndroidEntryPoint

/** Export destination fragment for Health Connect. */
@AndroidEntryPoint(Fragment::class)
class ExportDestinationFragment : Hilt_ExportDestinationFragment() {

    private val contract = ActivityResultContracts.StartActivityForResult()
    private val saveResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(contract, ::onSave)

    private val viewModel: ExportSettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.export_destination_screen, container, false)
        val backButton = view.findViewById<Button>(R.id.export_import_cancel_button)
        val nextButton = view.findViewById<Button>(R.id.export_import_next_button)

        backButton?.text = getString(R.string.export_back_button)
        backButton?.setOnClickListener {
            findNavController()
                .navigate(R.id.action_exportDestinationFragment_to_exportFrequencyFragment)
        }

        nextButton.text = getString(R.string.export_next_button)
        nextButton.setEnabled(false)

        val documentProvidersViewBinder = DocumentProvidersViewBinder()
        val documentProvidersList = view.findViewById<ViewGroup>(R.id.export_document_providers)
        viewModel.documentProviders.observe(viewLifecycleOwner) { providers ->
            documentProvidersList.removeAllViews()
            nextButton.setOnClickListener {}
            nextButton.setEnabled(false)

            when (providers) {
                is DocumentProviders.Loading -> {
                    // Do nothing
                }
                is DocumentProviders.LoadingFailed -> {
                    Toast.makeText(activity, R.string.default_error, Toast.LENGTH_LONG).show()
                }
                is DocumentProviders.WithData -> {
                    // TODO: b/339189778 - Handle no document providers.
                    documentProvidersViewBinder.bindDocumentProvidersView(
                        providers.providers, documentProvidersList, inflater) { root ->
                            nextButton.setOnClickListener {
                                saveResultLauncher.launch(
                                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                                        .addFlags(
                                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                        .setType("application/zip")
                                        .addCategory(Intent.CATEGORY_OPENABLE)
                                        .putExtra(DocumentsContract.EXTRA_INITIAL_URI, root.uri))
                            }
                            nextButton.setEnabled(true)
                        }
                }
            }
        }

        return view
    }

    private fun onSave(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val fileUri = result.data?.data ?: return
            if (isLocalFile(fileUri)) {
                Toast.makeText(activity, R.string.export_invalid_storage, Toast.LENGTH_LONG).show()
            } else {
                viewModel.updateExportUri(fileUri)
                requireActivity().finish()
            }
        }
    }
}
