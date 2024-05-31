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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.deletion.FailedDialogFragment
import com.android.healthconnect.controller.deletion.SuccessDialogFragment
import com.android.healthconnect.controller.selectabledeletion.DeletionConstants.CONFIRMATION_KEY
import com.android.healthconnect.controller.selectabledeletion.DeletionConstants.START_DELETION_KEY
import com.android.healthconnect.controller.selectabledeletion.DeletionConstants.TRY_AGAIN_EVENT
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint(Fragment::class)
class DeletionFragment : Hilt_DeletionFragment() {
    private val viewModel: DeletionViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        parentFragmentManager.setFragmentResultListener(START_DELETION_KEY, this) { _, _ ->
            showConfirmationDialog()
        }

        childFragmentManager.setFragmentResultListener(TRY_AGAIN_EVENT, this) { _, _ ->
            showConfirmationDialog()
        }

        childFragmentManager.setFragmentResultListener(CONFIRMATION_KEY, this) { _, _ ->
            viewModel.delete()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_deletion, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.deletionProgress.observe(viewLifecycleOwner) { deletion ->
            when (deletion) {
                DeletionViewModel.DeletionProgress.NOT_STARTED -> {
                    dismissLoadingDialog()
                }
                DeletionViewModel.DeletionProgress.PROGRESS_INDICATOR_CAN_START -> {
                    showLoadingDialog()
                }
                DeletionViewModel.DeletionProgress.COMPLETED -> {
                    showSuccessDialog()
                }
                DeletionViewModel.DeletionProgress.PROGRESS_INDICATOR_CAN_END -> {
                    dismissLoadingDialog()
                }
                DeletionViewModel.DeletionProgress.FAILED -> {
                    showFailedDialog()
                }
                else -> {
                    // do nothing
                }
            }
        }
    }

    private fun showFailedDialog() {
        dismissLoadingDialog()
        FailedDialogFragment().show(childFragmentManager, FailedDialogFragment.TAG)
    }

    private fun dismissLoadingDialog() {
        val loadingDialog =
            childFragmentManager.findFragmentByTag(DeletionLoadingDialogFragment.TAG)
        if (loadingDialog != null) {
            (loadingDialog as DeletionLoadingDialogFragment).dismiss()
        }
    }

    private fun showLoadingDialog() {
        if (childFragmentManager.findFragmentByTag(DeletionLoadingDialogFragment.TAG) == null) {
            DeletionLoadingDialogFragment()
                .show(childFragmentManager, DeletionLoadingDialogFragment.TAG)
        }
    }

    private fun showConfirmationDialog() {
        NewDeletionConfirmationDialogFragment()
            .show(childFragmentManager, NewDeletionConfirmationDialogFragment.TAG)
    }

    private fun showSuccessDialog() {
        SuccessDialogFragment().show(childFragmentManager, SuccessDialogFragment.TAG)
    }
}
