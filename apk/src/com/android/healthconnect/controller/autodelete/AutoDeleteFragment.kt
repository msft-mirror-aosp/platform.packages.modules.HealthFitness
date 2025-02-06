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
package com.android.healthconnect.controller.autodelete

import android.icu.text.MessageFormat
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.autodelete.AutoDeleteConfirmationDialogFragment.Companion.AUTO_DELETE_CANCELLED_EVENT
import com.android.healthconnect.controller.autodelete.AutoDeleteConfirmationDialogFragment.Companion.AUTO_DELETE_CONFIRMATION_DIALOG_EVENT
import com.android.healthconnect.controller.autodelete.AutoDeleteConfirmationDialogFragment.Companion.AUTO_DELETE_SAVED_EVENT
import com.android.healthconnect.controller.autodelete.AutoDeleteConfirmationDialogFragment.Companion.NEW_AUTO_DELETE_RANGE_BUNDLE
import com.android.healthconnect.controller.autodelete.AutoDeleteConfirmationDialogFragment.Companion.OLD_AUTO_DELETE_RANGE_BUNDLE
import com.android.healthconnect.controller.autodelete.AutoDeleteRangePickerPreference.Companion.AUTO_DELETE_RANGE_PICKER_PREFERENCE_KEY
import com.android.healthconnect.controller.autodelete.AutoDeleteRangePickerPreference.Companion.SET_TO_NEVER_EVENT
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.topIntroPreference
import com.android.healthconnect.controller.utils.DeviceInfoUtilsImpl
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment displaying auto delete settings. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
class AutoDeleteFragment : Hilt_AutoDeleteFragment() {

    init {
        this.setPageName(PageName.AUTO_DELETE_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: AutoDeleteViewModel by activityViewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.auto_delete_screen, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferenceScreen.addPreference(
            topIntroPreference(
                context = requireContext(),
                preferenceTitle = getString(R.string.auto_delete_header),
                learnMoreText = getString(R.string.auto_delete_learn_more),
                learnMoreAction = { DeviceInfoUtilsImpl().openHCGetStartedLink(requireActivity()) },
            )
        )

        viewModel.storedAutoDeleteRange.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AutoDeleteViewModel.AutoDeleteState.Loading -> {
                    // do nothing
                }
                is AutoDeleteViewModel.AutoDeleteState.LoadingFailed -> {
                    Toast.makeText(activity, R.string.default_error, Toast.LENGTH_LONG).show()
                }
                is AutoDeleteViewModel.AutoDeleteState.WithData -> {
                    if (
                        preferenceScreen.findPreference<Preference>(
                            AUTO_DELETE_RANGE_PICKER_PREFERENCE_KEY
                        ) == null
                    ) {
                        val autoDeletePreference =
                            AutoDeleteRangePickerPreference(
                                requireContext(),
                                childFragmentManager,
                                state.autoDeleteRange,
                                logger,
                            )
                        autoDeletePreference.order = 1
                        preferenceScreen.addPreference(autoDeletePreference)
                    } else {
                        val autoDeletePreference =
                            preferenceScreen.findPreference<Preference>(
                                AUTO_DELETE_RANGE_PICKER_PREFERENCE_KEY
                            ) as AutoDeleteRangePickerPreference
                        autoDeletePreference.updateAutoDeleteRange(state.autoDeleteRange)
                    }
                }
            }
        }

        childFragmentManager.setFragmentResultListener(SET_TO_NEVER_EVENT, this) { _, _ ->
            viewModel.updateAutoDeleteRange(AutoDeleteRange.AUTO_DELETE_RANGE_NEVER)
            Toast.makeText(requireContext(), R.string.auto_delete_off_toast, Toast.LENGTH_LONG)
                .show()
        }

        childFragmentManager.setFragmentResultListener(
            AUTO_DELETE_CONFIRMATION_DIALOG_EVENT,
            this,
        ) { _, bundle ->
            bundle.getSerializable(NEW_AUTO_DELETE_RANGE_BUNDLE)?.let { newAutoDeleteRange ->
                bundle.getSerializable(OLD_AUTO_DELETE_RANGE_BUNDLE)?.let { oldAutoDeleteRange ->
                    viewModel.updateAutoDeleteDialogArguments(
                        newAutoDeleteRange as AutoDeleteRange,
                        oldAutoDeleteRange as AutoDeleteRange,
                    )
                    AutoDeleteConfirmationDialogFragment()
                        .show(childFragmentManager, AutoDeleteConfirmationDialogFragment.TAG)
                }
            }
        }

        childFragmentManager.setFragmentResultListener(AUTO_DELETE_SAVED_EVENT, this) { _, bundle ->
            bundle.getSerializable(AUTO_DELETE_SAVED_EVENT)?.let {
                viewModel.updateAutoDeleteRange(it as AutoDeleteRange)
            }
            viewModel.newAutoDeleteRange.value?.let {
                Toast.makeText(requireContext(), buildMessage(it), Toast.LENGTH_LONG).show()
            }
        }

        childFragmentManager.setFragmentResultListener(AUTO_DELETE_CANCELLED_EVENT, this) {
            _,
            bundle ->
            bundle.getSerializable(AUTO_DELETE_CANCELLED_EVENT)?.let {
                viewModel.updateAutoDeleteRange(it as AutoDeleteRange)
            }
        }
    }

    private fun buildMessage(autoDeleteRange: AutoDeleteRange): String {
        val count = autoDeleteRange.numberOfMonths
        return MessageFormat.format(
            requireContext().getString(R.string.auto_delete_confirmation_toast),
            mapOf("count" to count),
        )
    }
}
