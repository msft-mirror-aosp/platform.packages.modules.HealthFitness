/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.data.alldata

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
import com.android.healthconnect.controller.selectabledeletion.DeletionConstants.START_DELETION_KEY
import com.android.healthconnect.controller.selectabledeletion.DeletionFragment
import com.android.healthconnect.controller.selectabledeletion.DeletionPermissionTypesPreference
import com.android.healthconnect.controller.selectabledeletion.DeletionViewModel
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.icon
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.NoDataPreference
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.setupMenu
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for fitness permission types. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
open class AllDataFragment : Hilt_AllDataFragment() {

    companion object {
        private const val TAG = "AllDataFragmentTag"
        private const val DELETION_TAG = "DeletionTag"
    }

    @Inject lateinit var logger: HealthConnectLogger

    @HealthDataCategoryInt private var category: Int = 0

    private val viewModel: AllDataViewModel by activityViewModels()

    private val deletionViewModel: DeletionViewModel by activityViewModels()

    // Empty state
    private val onDataSourcesClick: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.menu_data_sources -> {
                findNavController()
                    .navigate(
                        R.id.action_allDataFragment_to_dataSourcesFragment,
                        bundleOf(CATEGORY_KEY to category))
                true
            }
            else -> false
        }
    }

    // Not in deletion state
    private val onMenuSetup: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.menu_data_sources -> {
                findNavController()
                    .navigate(
                        R.id.action_allDataFragment_to_dataSourcesFragment,
                        bundleOf(CATEGORY_KEY to category))
                true
            }
            R.id.menu_enter_deletion_state -> {
                // enter deletion state
                triggerDeletionState(true)
                true
            }
            else -> false
        }
    }

    // In deletion state with data selected
    private val onEnterDeletionState: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.delete -> {
                deleteData()
                true
            }
            else -> false
        }
    }

    // In deletion state without any data selected
    private val onEmptyDeleteSetSetup: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.menu_exit_deletion_state -> {
                // exit deletion state
                triggerDeletionState(false)
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

        viewModel.loadAllFitnessData()

        viewModel.allData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AllDataViewModel.AllDataState.Loading -> {
                    setLoading(isLoading = true)
                    if (!viewModel.getDeletionState()) {
                        updateMenu(isDeletionState = false)
                        triggerDeletionState(isDeletionState = false)
                    }
                }
                is AllDataViewModel.AllDataState.Error -> {
                    setError(hasError = true)
                }
                is AllDataViewModel.AllDataState.WithData -> {
                    setLoading(isLoading = false)
                    setError(hasError = false)
                    updatePreferenceScreen(state.dataMap)
                }
            }
        }

        deletionViewModel.permissionTypesReloadNeeded.observe(viewLifecycleOwner) { isReloadNeeded
            ->
            if (isReloadNeeded) {
                viewModel.setDeletionState(false)
                viewModel.loadAllFitnessData()
                deletionViewModel.resetPermissionTypesReloadNeeded()
            }
        }
    }

    private fun updatePreferenceScreen(
        permissionTypesPerCategoryList: List<PermissionTypesPerCategory>
    ) {
        preferenceScreen?.removeAll()

        val populatedCategories =
            permissionTypesPerCategoryList
                .filter { it.data.isNotEmpty() }
                .sortedBy { getString(it.category.uppercaseTitle()) }

        if (populatedCategories.isEmpty()) {
            setupEmptyState()
            return
        }

        setupMenu()

        populatedCategories.forEach { permissionTypesPerCategory ->
            val category = permissionTypesPerCategory.category
            val categoryIcon = category.icon(requireContext())

            val preferenceCategory =
                PreferenceCategory(requireContext()).also { it.setTitle(category.uppercaseTitle()) }
            preferenceScreen.addPreference(preferenceCategory)

            permissionTypesPerCategory.data
                .sortedBy {
                    getString(FitnessPermissionStrings.fromPermissionType(it).uppercaseLabel)
                }
                .forEach { permissionType ->
                    preferenceCategory.addPreference(
                        getPermissionTypePreference(permissionType, categoryIcon))
                }
        }
    }

    private fun onDeletionMethod(preference: DeletionPermissionTypesPreference): () -> Unit {
        return {
            if (preference.getHealthPermissionType() !in viewModel.getDeleteSet()) {
                viewModel.addToDeleteSet(preference.getHealthPermissionType())
            } else {
                viewModel.removeFromDeleteSet(preference.getHealthPermissionType())
            }
            updateMenu(isDeletionState = true)
        }
    }

    private fun updateMenu(isDeletionState: Boolean, hasData: Boolean = true) {
        if (!hasData) {
            setupMenu(
                R.menu.all_data_empty_state_menu, viewLifecycleOwner, logger, onDataSourcesClick)
            return
        }

        if (!isDeletionState) {
            setupMenu(R.menu.all_data_menu, viewLifecycleOwner, logger, onMenuSetup)
            return
        }

        if (viewModel.getDeleteSet().isEmpty()) {
            setupMenu(
                R.menu.all_data_delete_menu, viewLifecycleOwner, logger, onEmptyDeleteSetSetup)
            return
        }

        setupMenu(R.menu.deletion_state_menu, viewLifecycleOwner, logger, onEnterDeletionState)
    }

    @VisibleForTesting
    fun triggerDeletionState(isDeletionState: Boolean) {
        viewModel.setDeletionState(isDeletionState)

        preferenceScreen.children.forEach { preference ->
            if (preference is PreferenceCategory) {
                preference.children.forEach { permissionTypePreference ->
                    if (permissionTypePreference is DeletionPermissionTypesPreference) {
                        permissionTypePreference.showCheckbox(isDeletionState)
                    }
                }
            }

            updateMenu(isDeletionState)
        }
    }

    private fun setupMenu() {
        updateMenu(isDeletionState = viewModel.getDeletionState())
    }

    private fun setupEmptyState() {
        preferenceScreen.addPreference(NoDataPreference(requireContext()))
        preferenceScreen.addPreference(
            FooterPreference(requireContext()).also { it.setTitle(R.string.no_data_footer) })
        updateMenu(isDeletionState = false, hasData = false)
    }

    private fun deleteData() {
        deletionViewModel.setDeleteSet(viewModel.getDeleteSet())
        childFragmentManager.setFragmentResult(START_DELETION_KEY, bundleOf())
    }

    private fun getPermissionTypePreference(
        permissionType: FitnessPermissionType,
        categoryIcon: Drawable?
    ): Preference {
        return DeletionPermissionTypesPreference(requireContext()).also { preference ->
            preference.setShowCheckbox(viewModel.getDeletionState())
            if (permissionType in viewModel.getDeleteSet()) {
                preference.setIsChecked(true)
            }

            preference.icon = categoryIcon
            preference.setTitle(
                FitnessPermissionStrings.fromPermissionType(permissionType).uppercaseLabel)

            preference.setHealthPermissionType(permissionType)
            // TODO(b/291249677): Add in upcoming CL.
            // it.logName = AllDataElement.PERMISSION_TYPE_BUTTON

            preference.setOnPreferenceClickListener(onDeletionMethod(preference)) {
                findNavController()
                    .navigate(
                        R.id.action_allData_to_entriesAndAccess,
                        bundleOf(PERMISSION_TYPE_KEY to permissionType))
                true
            }
        }
    }
}
