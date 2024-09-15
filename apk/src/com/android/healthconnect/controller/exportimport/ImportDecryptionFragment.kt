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
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.android.healthconnect.controller.R
import dagger.hilt.android.AndroidEntryPoint
import com.android.healthconnect.controller.utils.convertTextViewIntoLink

/**
 * Fragment to collect user's password to decrypt the imported backup file.
 */
@AndroidEntryPoint(Fragment::class)
class ImportDecryptionFragment : Hilt_ImportDecryptionFragment() {
    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.import_decryption_screen, container, false)
        val pageHeaderView = view.findViewById<TextView>(R.id.page_header_text)
        val pageHeaderIconView = view.findViewById<ImageView>(R.id.page_header_icon)
        val cancelButton = view.findViewById<Button>(R.id.export_import_cancel_button)
        val nextButton = view.findViewById<Button>(R.id.export_import_next_button)

        val forgottenPasswordLink = view.findViewById<TextView>(R.id.import_decryption_forgotten_password_link)

        pageHeaderView.text = getString(R.string.import_decryption_title)
        pageHeaderIconView.setImageResource(R.drawable.ic_password)
        cancelButton.text = getString(R.string.import_cancel_button)
        nextButton.text = getString(R.string.import_next_button)

        val forgottenPasswordLinkText = view.context.getString(R.string.import_decryption_forgotten_password_link_text)
        val forgottenPasswordLinkAction: View.OnClickListener? = null
        convertTextViewIntoLink(
                forgottenPasswordLink,
                forgottenPasswordLinkText,
                0,
                forgottenPasswordLinkText.length,
                forgottenPasswordLinkAction)

        // TODO(b/325917291): update import state to show appropriate loading notifications on return
        nextButton.setOnClickListener { requireActivity().finish() }
        cancelButton.setOnClickListener { requireActivity().finish() }

        return view

    }
}