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

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.exportimport.api.DocumentProvider
import com.android.healthconnect.controller.exportimport.api.DocumentProviderRoot
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.logging.ErrorPageElement

/** Adds the views for the documents provider list when setting up export and import. */
class DocumentProvidersViewBinder {
    fun bindDocumentProvidersView(
        documentProviders: List<DocumentProvider>,
        documentProvidersView: ViewGroup,
        inflater: LayoutInflater,
        onSelectionChanged: (root: DocumentProviderRoot) -> Unit
    ) {
        for (documentProvider in documentProviders) {
            val documentProviderView =
                inflater.inflate(R.layout.item_document_provider, documentProvidersView, false)

            val radioButtonView =
                documentProviderView.findViewById<RadioButton>(
                    R.id.item_document_provider_radio_button)
            val iconView =
                documentProviderView.findViewById<ImageView>(R.id.item_document_provider_icon)
            val titleView =
                documentProviderView.findViewById<TextView>(R.id.item_document_provider_title)
            val summaryView =
                documentProviderView.findViewById<TextView>(R.id.item_document_provider_summary)

            iconView.setImageDrawable(
                loadPackageIcon(
                    documentProvidersView.context,
                    documentProvider.info.authority,
                    documentProvider.info.iconResource))
            titleView.setText(documentProvider.info.title)

            if (documentProvider.roots.size == 1) {
                val root = documentProvider.roots[0]

                summaryView.setText(root.summary)
                summaryView.setVisibility(VISIBLE)

                if (documentProviders.size == 1) {
                    radioButtonView.setVisibility(GONE)

                    onSelectionChanged(root)
                } else {
                    documentProviderView.setOnClickListener {
                        uncheckRadioButtons(documentProvidersView)
                        radioButtonView.setChecked(true)

                        onSelectionChanged(root)
                    }
                }
            } else {
                if (documentProviders.size == 1) {
                    radioButtonView.setVisibility(GONE)

                    summaryView.setText(R.string.export_import_tap_to_choose_account)
                } else {
                    summaryView.setText("")
                    summaryView.setVisibility(GONE)
                }

                documentProviderView.setOnClickListener {
                    showChooseAccountDialog(inflater, documentProvider.roots) { root ->
                        uncheckRadioButtons(documentProvidersView)
                        radioButtonView.setChecked(true)

                        summaryView.setText(root.summary)
                        summaryView.setVisibility(VISIBLE)

                        onSelectionChanged(root)
                    }
                }
            }

            documentProvidersView.addView(documentProviderView)
        }
    }

    private fun showChooseAccountDialog(
        inflater: LayoutInflater,
        roots: List<DocumentProviderRoot>,
        onSelectionChanged: (root: DocumentProviderRoot) -> Unit
    ) {
        val view = inflater.inflate(R.layout.dialog_export_import_account, null)

        val radioGroup = view.findViewById<RadioGroup>(R.id.radio_group_account)

        for (i in roots.indices) {
            val radioButton =
                inflater.inflate(R.layout.item_document_provider_account, radioGroup, false)
                    as RadioButton
            radioButton.text = roots[i].summary
            radioButton.id = i
            if (i == 0) {
                radioButton.isChecked = true
            }
            radioGroup.addView(radioButton)
        }

        // TODO: b/339189778 - Add proper logging for the account picker dialog.
        AlertDialogBuilder(inflater.context, ErrorPageElement.UNKNOWN_ELEMENT)
            .setView(view)
            .setNegativeButton(
                R.string.export_import_choose_account_cancel_button,
                ErrorPageElement.UNKNOWN_ELEMENT)
            .setPositiveButton(
                R.string.export_import_choose_account_done_button,
                ErrorPageElement.UNKNOWN_ELEMENT) { _, _ ->
                    onSelectionChanged(roots[radioGroup.checkedRadioButtonId])
                }
            .create()
            .show()
    }

    private fun loadPackageIcon(context: Context, authority: String, icon: Int): Drawable? {
        val info = context.packageManager.resolveContentProvider(authority, 0)
        if (info != null) {
            return context.packageManager.getDrawable(info.packageName, icon, info.applicationInfo)
        }

        return null
    }

    private fun uncheckRadioButtons(view: ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val childRadioButton =
                child.findViewById<RadioButton>(R.id.item_document_provider_radio_button)
            childRadioButton?.setChecked(false)
        }
    }
}
