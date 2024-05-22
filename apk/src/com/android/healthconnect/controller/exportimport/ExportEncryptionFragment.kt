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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.api.ExportSettingsViewModel
import com.android.healthconnect.controller.utils.ScryptKeySpec
import dagger.hilt.android.AndroidEntryPoint
import java.lang.CharSequence as JavaCharSequence
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory

/** Export encryption fragment for Health Connect. */
// TODO: b/325917283 - Save the user created password.
@AndroidEntryPoint(Fragment::class)
class ExportEncryptionFragment : Hilt_ExportEncryptionFragment() {

    private val viewModel: ExportSettingsViewModel by viewModels()

    companion object {
        private const val TAG = "ExportEncryptionFragment"
        private const val SALT_SIZE = 16
        private const val SCRYPT_ALGORITHM = "SCRYPT"
        // See go/crypto-password-hash#option-1 for the costParameter, blockSize and
        // parallelizationParameter. The saltSize and keyLength are common practices.
        private const val COST_PARAMETER = 32768
        private const val BLOCK_SIZE = 8
        private const val PARALLELIZATION_PARAMETER = 1
        private const val KEY_LENGTH = 32 * 8
    }

    // TODO: b/325917283 - Add proper logging for export encryption fragment.
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.export_encryption_screen, container, false)

        // TODO: b/325917283 - Add proper navigation to the next screen.
        val backButton = view.findViewById<Button>(R.id.export_import_cancel_button)
        val nextButton = view.findViewById<Button>(R.id.export_import_next_button)

        nextButton.text = getString(R.string.export_next_button)
        backButton.text = getString(R.string.export_back_button)

        backButton?.setOnClickListener {
            findNavController()
                .navigate(R.id.action_exportEncryptionFragment_to_exportDestinationFragment)
        }

        val password = view.findViewById<EditText>(R.id.export_password)
        val repeatedPassword = view.findViewById<EditText>(R.id.export_repeat_password)
        val mismatchError = view.findViewById<TextView>(R.id.password_mismatch_error)

        nextButton?.setOnClickListener(createOnClickListener(password))
        // Only shows the error and enable the next button when passwords match.
        if (password !== null &&
            repeatedPassword !== null &&
            password.text.length > 0 &&
            JavaCharSequence.compare(password.text, repeatedPassword.text) == 0) {
            nextButton?.isEnabled = true
            mismatchError?.visibility = View.INVISIBLE
        } else {
            nextButton?.isEnabled = false
            if (password !== null && password.text.length > 0) {
                mismatchError?.visibility = View.VISIBLE
            } else {
                // If user has not entered the password, we shouldn't show the error.
                mismatchError?.visibility = View.INVISIBLE
            }
        }
        val passwordTextChangedListener =
            createTextChangeWatcher(repeatedPassword, nextButton, mismatchError)
        password?.addTextChangedListener(passwordTextChangedListener)
        val repeatedPasswordTextChangedListener =
            createTextChangeWatcher(password, nextButton, mismatchError)
        repeatedPassword?.addTextChangedListener(repeatedPasswordTextChangedListener)
        return view
    }

    private fun createOnClickListener(password: EditText): View.OnClickListener {
        return View.OnClickListener {
            try {
                val salt = ByteArray(SALT_SIZE)
                SecureRandom().nextBytes(salt)
                val passwordText = password.text
                val passwordArray = CharArray(passwordText.length)
                for (i in passwordText.indices) {
                    passwordArray[i] = passwordText[i]
                }
                val scryptKeySpec =
                    ScryptKeySpec(
                        passwordArray,
                        salt,
                        COST_PARAMETER,
                        BLOCK_SIZE,
                        PARALLELIZATION_PARAMETER,
                        KEY_LENGTH)
                val secretKey =
                    SecretKeyFactory.getInstance(SCRYPT_ALGORITHM).generateSecret(scryptKeySpec)
                for (i in passwordText.indices) {
                    passwordArray[i] = '0'
                }
                viewModel.updateExportSecretKey(secretKey.encoded, salt)
                requireActivity().finish()
            } catch (exception: GeneralSecurityException) {
                Log.i(TAG, "Error during key encryption", exception)
                Toast.makeText(requireActivity(), R.string.default_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun createTextChangeWatcher(
        passwordToBeCompared: EditText?,
        nextButton: Button?,
        mismatchError: TextView?
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
                    passwordToBeCompared != null &&
                    JavaCharSequence.compare(s, passwordToBeCompared.text) == 0) {
                    nextButton?.isEnabled = true
                    mismatchError?.visibility = View.INVISIBLE
                } else {
                    nextButton?.isEnabled = false
                    if (passwordToBeCompared?.text !== null &&
                        passwordToBeCompared.text.isEmpty()) {
                        // If a user has only entered one field, the error should be invisible.
                        mismatchError?.visibility = View.INVISIBLE
                    } else {
                        mismatchError?.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
