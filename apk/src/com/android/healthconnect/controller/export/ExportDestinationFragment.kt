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

package com.android.healthconnect.controller.export

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.export.api.ExportSettingsViewModel
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
        val backButton = view.findViewById<Button>(R.id.export_back_button)

        backButton.setOnClickListener {
            findNavController()
                .navigate(R.id.action_exportDestinationFragment_to_exportFrequencyFragment)
        }

        val nextButton = view.findViewById<Button>(R.id.export_next_button)
        nextButton.setOnClickListener {
            // TODO: b/325917283 - Add proper navigation to the next screen (document UI)
            // and to the encryption fragment.
            findNavController()
                .navigate(R.id.action_exportDestinationFragment_to_exportEncryptionFragment)
        }

        // TODO: b/325917283 - the temporary UI to open the document API for e2e prototype.
        // Replace it once we use proper storage UI APIs.
        val openExportDestinationButton = view.findViewById<Button>(R.id.open_export_destination)
        openExportDestinationButton?.setOnClickListener {
            saveResultLauncher.launch(
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addFlags(
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            .setType("application/zip")
                            .addCategory(Intent.CATEGORY_OPENABLE))
        }
        return view
    }

    private fun onSave(result: ActivityResult) {
        // TODO: b/325917283 - the temporary UI solution to open the document API for e2e prototype.
        // Replace it once we use proper storage UI APIs.
        if (result.resultCode == Activity.RESULT_OK) {
            val fileUri = result.data?.data ?: return
            requireContext()
                .contentResolver
                .takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            viewModel.updateExportUri(fileUri)
            findNavController()
                .navigate(R.id.action_exportDestinationFragment_to_exportEncryptionFragment)
        }
    }
}
