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
import android.widget.ImageView
import android.widget.TextView
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
import com.android.healthconnect.controller.utils.DeviceInfoUtils
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.ImportSourceLocationElement
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.settingslib.widget.LinkTextView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment to allow the user to find and select the backup file to import and restore. */
@AndroidEntryPoint(Fragment::class)
class ImportSourceLocationFragment : Hilt_ImportSourceLocationFragment() {
    private val contract = ActivityResultContracts.StartActivityForResult()
    private val saveResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(contract, ::onSave)

    private val viewModel: ExportSettingsViewModel by viewModels()

    @Inject lateinit var deviceInfoUtils: DeviceInfoUtils
    @Inject lateinit var logger: HealthConnectLogger

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        logger.setPageId(PageName.IMPORT_SOURCE_LOCATION_PAGE)
        val view = inflater.inflate(R.layout.import_source_location_screen, container, false)
        val pageHeaderView = view.findViewById<TextView>(R.id.page_header_text)
        val pageHeaderIconView = view.findViewById<ImageView>(R.id.page_header_icon)
        val footerView = view.findViewById<View>(R.id.export_import_footer)
        val footerIconView = view.findViewById<View>(R.id.export_import_footer_icon)
        val footerTextView = view.findViewById<TextView>(R.id.export_import_footer_text)
        val playStoreView = view.findViewById<LinkTextView>(R.id.export_import_go_to_play_store)
        val cancelButton = view.findViewById<Button>(R.id.export_import_cancel_button)
        val nextButton = view.findViewById<Button>(R.id.export_import_next_button)

        pageHeaderView.text = getString(R.string.import_source_location_title)
        pageHeaderIconView.setImageResource(R.drawable.ic_import_data)
        nextButton.text = getString(R.string.import_next_button)
        cancelButton.text = getString(R.string.import_cancel_button)

        logger.logImpression(ImportSourceLocationElement.IMPORT_SOURCE_LOCATION_CANCEL_BUTTON)
        logger.logImpression(ImportSourceLocationElement.IMPORT_SOURCE_LOCATION_NEXT_BUTTON)

        cancelButton.setOnClickListener {
            logger.logInteraction(ImportSourceLocationElement.IMPORT_SOURCE_LOCATION_CANCEL_BUTTON)
            requireActivity().finish()
        }

        if (deviceInfoUtils.isPlayStoreAvailable(requireContext())) {
            playStoreView?.setVisibility(VISIBLE)
            playStoreView?.setOnClickListener {
                findNavController().navigate(R.id.action_importSourceLocationFragment_to_playStore)
            }
        }

        val documentProvidersViewBinder = DocumentProvidersViewBinder()
        val documentProvidersList = view.findViewById<ViewGroup>(R.id.import_document_providers)
        viewModel.documentProviders.observe(viewLifecycleOwner) { providers: DocumentProviders ->
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
                                ImportSourceLocationElement.IMPORT_SOURCE_LOCATION_NEXT_BUTTON
                            )
                            saveResultLauncher.launch(
                                Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addFlags(
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                    .setType("application/zip")
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .putExtra(DocumentsContract.EXTRA_INITIAL_URI, root.uri)
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
        logger.logPageImpression()
    }

    private fun onSave(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
            val fileUri = result.data?.data ?: return
            if (isLocalFile(fileUri)) {
                Toast.makeText(activity, R.string.import_invalid_storage, Toast.LENGTH_LONG).show()
            } else {
                // TODO: b/339189778 - Add test when import API is done.
                val bundle = Bundle()
                bundle.putString(
                    ImportConfirmationDialogFragment.IMPORT_FILE_URI_KEY,
                    fileUri.toString(),
                )
                val dialogFragment = ImportConfirmationDialogFragment()
                dialogFragment.arguments = bundle
                dialogFragment.show(childFragmentManager, ImportConfirmationDialogFragment.TAG)
            }
        }
    }
}
