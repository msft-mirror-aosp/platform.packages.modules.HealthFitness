/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.deletion

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.shared.dialog.AlertDialogBuilder
import com.android.healthconnect.controller.utils.logging.SuccessDialogElement
import dagger.hilt.android.AndroidEntryPoint

/** A deletion {@link DialogFragment} notifying user about a successful deletion. */
@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
@AndroidEntryPoint(DialogFragment::class)
class SuccessDialogFragment : Hilt_SuccessDialogFragment() {

    private val viewModel: DeletionViewModel by activityViewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Get the navigation action depending on the deletion type
        val deletionType = viewModel.deletionParameters.value!!.deletionType
        val navAction =
            when (deletionType) {
                is DeletionType.DeleteDataEntry -> {
                    R.id.action_dataEntriesFragment_to_connectedApps
                }
                is DeletionType.DeletionTypeAppData -> {
                    R.id.action_connectedAppFragment_to_connectedApps
                }
                is DeletionType.DeletionTypeAllData -> {
                    R.id.action_healthDataCategories_to_connectedApps
                }
                is DeletionType.DeletionTypeCategoryData -> {
                    R.id.action_healthPermissionTypes_to_connectedApps
                }
                is DeletionType.DeletionTypeHealthPermissionTypeData -> {
                    R.id.action_healthDataAccessFragment_to_connectedApps
                }
                is DeletionType.DeletionTypeHealthPermissionTypeFromApp -> {
                    // Under data access
                    R.id.action_healthDataAccessFragment_to_connectedApps
                }
            }

        return AlertDialogBuilder(this, SuccessDialogElement.DELETION_DIALOG_SUCCESS_CONTAINER)
            .setIcon(R.attr.successIcon)
            .setTitle(R.string.delete_dialog_success_title)
            .setMessage(R.string.delete_dialog_success_message)
            .setNegativeButton(
                R.string.delete_dialog_see_connected_apps_button,
                // TODO (b/352023091) new log
                SuccessDialogElement.DELETION_DIALOG_SUCCESS_DONE_BUTTON,
                onClickListener = { _, _ ->
                    this.dismiss()
                    findNavController().navigate(navAction)
                },
            )
            .setPositiveButton(
                R.string.delete_dialog_success_got_it_button,
                SuccessDialogElement.DELETION_DIALOG_SUCCESS_DONE_BUTTON,
            )
            .create()
    }

    companion object {
        const val TAG = "SuccessDialogFragment"
    }
}
