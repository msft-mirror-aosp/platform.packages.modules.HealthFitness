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
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import dagger.hilt.android.AndroidEntryPoint

/** Export encryption fragment for Health Connect. */
// TODO: b/325917283 - Save the user created password.
@AndroidEntryPoint(Fragment::class)
class ExportEncryptionFragment : Hilt_ExportEncryptionFragment() {

    // TODO: b/325917283 - Add proper logging for export encryption fragment.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.export_encryption_screen, container, false)
        // TODO: b/325917283 - Add proper navigation to the next screen.
        val backButton = view.findViewById<Button>(R.id.export_back_button)

        backButton.setOnClickListener {
            findNavController()
                .navigate(R.id.action_exportEncryptionFragment_to_exportDestinationFragment)
        }

        val password = view.findViewById<EditText>(R.id.export_password)
        val repeatedPassword = view.findViewById<EditText>(R.id.export_repeat_password)

        val passwordTextChangedListener = createTextChangeWatcher(repeatedPassword, password)
        password?.addTextChangedListener(passwordTextChangedListener)

        val repeatedPasswordTextChangedListener =
            createTextChangeWatcher(password, repeatedPassword)
        repeatedPassword?.addTextChangedListener(repeatedPasswordTextChangedListener)
        return view
    }

    private fun createTextChangeWatcher(
        editTextToBeCompared: EditText?,
        currentEditText: EditText?
    ): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Do nothing.
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Do nothing.
            }

            override fun afterTextChanged(s: Editable?) {
                // We should NOT use strings here as strings are immutable and will be in memory
                // until cleared by the garbage collector.
                if (s !== null &&
                    editTextToBeCompared != null &&
                    java.lang.CharSequence.compare(s, editTextToBeCompared.text) == 0) {
                    // Clear all errors.
                    currentEditText?.error = null
                    editTextToBeCompared.error = null
                } else {
                    // TODO: b/325917283 - Disable next button if the passwords do not match
                    // criteria
                    currentEditText?.error = getString(R.string.export_passwords_mismatch_error)
                }
            }
        }
    }
}
