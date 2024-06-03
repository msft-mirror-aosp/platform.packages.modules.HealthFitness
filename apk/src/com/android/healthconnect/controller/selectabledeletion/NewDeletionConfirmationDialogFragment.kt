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
package com.android.healthconnect.controller.selectabledeletion

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.selectabledeletion.DeletionConstants.CONFIRMATION_KEY
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.AttributeResolver
import com.android.healthconnect.controller.utils.logging.DeletionDialogConfirmationElement

class NewDeletionConfirmationDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View = layoutInflater.inflate(R.layout.dialog_custom_layout, null)
        val title: TextView = view.findViewById(R.id.dialog_title)
        val message: TextView = view.findViewById(R.id.dialog_custom_message)
        val icon: ImageView = view.findViewById(R.id.dialog_icon)
        val iconDrawable = AttributeResolver.getNullableDrawable(view.context, R.attr.deleteIcon)

        title.setText(R.string.some_data_selected_deletion_confirmation_dialog)
        message.setText(R.string.confirming_question_message)
        iconDrawable?.let {
            icon.setImageDrawable(it)
            icon.visibility = View.VISIBLE
        }

        val alertDialogBuilder =
            AlertDialogBuilder(
                    this, DeletionDialogConfirmationElement.DELETION_DIALOG_CONFIRMATION_CONTAINER)
                .setView(view)
                .setPositiveButton(
                    R.string.confirming_question_delete_button,
                    // TODO: create new log elements for new IA dialogs
                    DeletionDialogConfirmationElement.DELETION_DIALOG_CONFIRMATION_DELETE_BUTTON) {
                        _,
                        _ ->
                        setFragmentResult(CONFIRMATION_KEY, Bundle())
                    }
                .setNeutralButton(
                    android.R.string.cancel,
                    DeletionDialogConfirmationElement.DELETION_DIALOG_CONFIRMATION_CANCEL_BUTTON)

        return alertDialogBuilder.create()
    }

    companion object {
        const val TAG = "NewDeletionConfirmationDialog"
    }
}
