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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import com.android.healthconnect.controller.R

/** Export frequency fragment for Health Connect. */
// TODO: b/325917283 - Save the selected frequency preference.
@AndroidEntryPoint(Fragment::class)
class ExportFrequencyFragment: Hilt_ExportFrequencyFragment() {

    // TODO: b/325917283 - Add proper logging for the export frequency fragment.
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.export_frequency_screen, container, false)

        val backButton = view.findViewById<Button>(R.id.export_back_button)
        val nextButton = view.findViewById<Button>(R.id.export_next_button)
        backButton.setOnClickListener {
            requireActivity().finish()
        }
        nextButton.setOnClickListener {
            findNavController().navigate(R.id.action_exportFrequencyFragment_to_exportEncryptionFragment)
        }

        return view
    }
}
