/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.healthconnect.controller.data.alldata.medical

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.categories.HealthDataCategoriesFragment.Companion.CATEGORY_KEY
import com.android.healthconnect.controller.data.appdata.AppDataFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.data.appdata.PermissionTypesPerCategory
import com.android.healthconnect.controller.permissions.data.FitnessPermissionStrings
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionStrings
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.selectabledeletion.DeletionConstants.START_DELETION_KEY
import com.android.healthconnect.controller.selectabledeletion.DeletionFragment
import com.android.healthconnect.controller.selectabledeletion.DeletionPermissionTypesPreference
import com.android.healthconnect.controller.selectabledeletion.DeletionViewModel
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.HealthPreference
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.NoDataPreference
import com.android.healthconnect.controller.utils.logging.CategoriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.setupMenu
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for medical permission types. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
open class MedicalAllDataFragment : Hilt_MedicalAllDataFragment() {

    companion object {
        private const val TAG = "MedicalAllDataFragmentTag"
        private const val DELETION_TAG = "DeletionTag"
    }

    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: MedicalAllDataViewModel by activityViewModels()

    private val onMenuSetup: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.menu_enter_deletion_state -> {
                //TODO(): Display deletion dialog.
                true
            }
            else -> false
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.app_data_screen, rootKey)
        if (childFragmentManager.findFragmentByTag(DELETION_TAG) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), DELETION_TAG) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.loadAllMedicalData()

        viewModel.allData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is MedicalAllDataViewModel.AllDataState.Loading -> {
                    setLoading(isLoading = true)
                }
                is MedicalAllDataViewModel.AllDataState.Error -> {
                    setError(hasError = true)
                }
                is MedicalAllDataViewModel.AllDataState.WithData -> {
                    setLoading(isLoading = false)
                    setError(hasError = false)
                    updatePreferenceScreen(state.dataMap)
                }
            }
        }
    }

    private fun updatePreferenceScreen(
        medicalPermissionTypes: List<MedicalPermissionType>
    ) {
        preferenceScreen?.removeAll()

        setupMenu(R.menu.all_data_menu, viewLifecycleOwner, logger, onMenuSetup)

        if (medicalPermissionTypes.isEmpty()) {
            preferenceScreen.addPreference(NoDataPreference(requireContext()))
            preferenceScreen.addPreference(
                FooterPreference(requireContext()).also { it.setTitle(R.string.no_data_footer) })
            return
        }

        medicalPermissionTypes
                .sortedBy {
                    getString(MedicalPermissionStrings.fromPermissionType(it).uppercaseLabel)
                }
                .forEach { permissionType ->
                    preferenceScreen.addPreference(
                        HealthPreference(requireContext()).also {
                            it.setTitle(getString(MedicalPermissionStrings.fromPermissionType(permissionType).uppercaseLabel))
                            // TODO(b/343148212): Add icon.
                            // TODO(b/342159144): Add logName
                            it.setOnPreferenceClickListener {
                                findNavController()
                                        .navigate(
                                                R.id.action_medicalAllData_to_entriesAndAccess,
                                                bundleOf(PERMISSION_TYPE_KEY to permissionType.name))
                                true
                            }
                        })
                }

    }
}
