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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.categories.HealthDataCategoriesFragment.Companion.CATEGORY_KEY
import com.android.healthconnect.controller.data.alldata.AllDataViewModel.AllDataDeletionScreenState.DELETE
import com.android.healthconnect.controller.data.alldata.AllDataViewModel.AllDataDeletionScreenState.VIEW
import com.android.healthconnect.controller.data.appdata.AppDataFragment.Companion.PERMISSION_TYPE_NAME_KEY
import com.android.healthconnect.controller.data.appdata.PermissionTypesPerCategory
import com.android.healthconnect.controller.data.entries.EntriesViewModel
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.selectabledeletion.DeletionConstants.START_DELETION_KEY
import com.android.healthconnect.controller.selectabledeletion.DeletionFragment
import com.android.healthconnect.controller.selectabledeletion.DeletionPermissionTypesPreference
import com.android.healthconnect.controller.selectabledeletion.DeletionType
import com.android.healthconnect.controller.selectabledeletion.DeletionViewModel
import com.android.healthconnect.controller.selectabledeletion.SelectAllCheckboxPreference
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.shared.HealthDataCategoryInt
import com.android.healthconnect.controller.shared.children
import com.android.healthconnect.controller.shared.preference.EmptyPreferenceCategory
import com.android.healthconnect.controller.shared.preference.HealthPreferenceFragment
import com.android.healthconnect.controller.shared.preference.NoDataPreference
import com.android.healthconnect.controller.utils.logging.AllDataElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.pref
import com.android.healthconnect.controller.utils.setupMenu
import com.android.healthconnect.controller.utils.setupSharedMenu
import com.android.settingslib.widget.FooterPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Fragment for fitness permission types. */
@AndroidEntryPoint(HealthPreferenceFragment::class)
open class AllDataFragment : Hilt_AllDataFragment() {

    companion object {
        private const val TAG = "AllDataFragmentTag"
        private const val DELETION_TAG = "DeletionTag"
        private const val KEY_SELECT_ALL = "key_select_all"
        private const val KEY_PERMISSION_TYPE = "key_permission_type"
        private const val KEY_NO_DATA = "no_data_preference"
        private const val KEY_FOOTER = "key_footer"
        const val IS_BROWSE_MEDICAL_DATA_SCREEN = "key_is_browse_medical_data_screen"
    }

    init {
        this.setPageName(PageName.ALL_DATA_PAGE)
    }

    @Inject lateinit var logger: HealthConnectLogger

    /** Decides whether this screen is supposed to display Fitness data or Medical data. */
    private var showMedicalData = false

    @HealthDataCategoryInt private var category: Int = 0

    private val viewModel: AllDataViewModel by viewModels()

    private val deletionViewModel: DeletionViewModel by activityViewModels()

    private val selectAllCheckboxPreference: SelectAllCheckboxPreference by pref(KEY_SELECT_ALL)

    private val permissionTypesListGroup: PreferenceCategory by pref(KEY_PERMISSION_TYPE)

    private val noDataPreference: NoDataPreference by pref(KEY_NO_DATA)

    private val footerPreference: FooterPreference by pref(KEY_FOOTER)

    private val entriesViewModel: EntriesViewModel by activityViewModels()

    // Empty state
    private val onDataSourcesClick: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.menu_data_sources -> {
                logger.logInteraction(ToolbarElement.TOOLBAR_DATA_SOURCES_BUTTON)
                // TODO (b/368600140) data sources will no longer need category once old IA is
                // phased out
                findNavController()
                    .navigate(
                        R.id.action_allDataFragment_to_dataSourcesFragment,
                        bundleOf(CATEGORY_KEY to category),
                    )
                true
            }

            else -> false
        }
    }

    // Not in deletion state
    private val onMenuSetup: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.menu_data_sources -> {
                logger.logInteraction(ToolbarElement.TOOLBAR_DATA_SOURCES_BUTTON)
                findNavController()
                    .navigate(
                        R.id.action_allDataFragment_to_dataSourcesFragment,
                        bundleOf(CATEGORY_KEY to category),
                    )
                true
            }

            R.id.menu_enter_deletion_state -> {
                logger.logInteraction(ToolbarElement.TOOLBAR_ENTER_DELETION_STATE_BUTTON)
                // enter deletion state
                triggerDeletionState(DELETE)
                true
            }

            else -> false
        }
    }

    // In deletion state with data selected
    private val onEnterDeletionState: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
            R.id.delete -> {
                logger.logInteraction(ToolbarElement.TOOLBAR_DELETE_BUTTON)
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
                logger.logInteraction(ToolbarElement.TOOLBAR_EXIT_DELETION_STATE_BUTTON)
                // exit deletion state
                triggerDeletionState(VIEW)
                true
            }

            else -> false
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.all_data_screen, rootKey)
        val hasBrowseMedicalDataKey = arguments?.containsKey(IS_BROWSE_MEDICAL_DATA_SCREEN) ?: false
        if (hasBrowseMedicalDataKey) {
            showMedicalData =
                arguments?.getBoolean(IS_BROWSE_MEDICAL_DATA_SCREEN)
                    ?: throw IllegalArgumentException(
                        "IS_BROWSE_MEDICAL_DATA_SCREEN can't be null!"
                    )
        }
        if (childFragmentManager.findFragmentByTag(DELETION_TAG) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), DELETION_TAG) }
        }
        selectAllCheckboxPreference.logName = AllDataElement.SELECT_ALL_BUTTON
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAllData()

        viewModel.allData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AllDataViewModel.AllDataState.Loading -> {
                    setLoading(isLoading = true)
                    if (viewModel.getScreenState() == VIEW) {
                        updateMenu(screenState = VIEW)
                        triggerDeletionState(screenState = VIEW)
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
                viewModel.setScreenState(VIEW)
                loadAllData()
                deletionViewModel.resetPermissionTypesReloadNeeded()
            }
        }
    }

    private fun loadAllData() {
        if (showMedicalData) {
            viewModel.loadAllMedicalData()
        } else {
            viewModel.loadAllFitnessData()
        }
    }

    private fun updatePreferenceScreen(
        permissionTypesPerCategoryList: List<PermissionTypesPerCategory>
    ) {
        permissionTypesListGroup.removeAll()

        val populatedCategories =
            permissionTypesPerCategoryList
                .filter { it.data.isNotEmpty() }
                .sortedBy { getString(it.category.uppercaseTitle()) }

        if (populatedCategories.isEmpty()) {
            setupEmptyState()
            return
        }

        setupSelectAllPreference(screenState = viewModel.getScreenState())

        updateMenu(screenState = viewModel.getScreenState())
        noDataPreference.isVisible = false
        footerPreference.isVisible = false

        populatedCategories.forEach { permissionTypesPerCategory ->
            val category = permissionTypesPerCategory.category

            val preferenceCategory =
                if (showMedicalData) {
                    EmptyPreferenceCategory(requireContext())
                } else {
                    PreferenceCategory(requireContext()).also {
                        it.setTitle(category.uppercaseTitle())
                    }
                }
            permissionTypesListGroup.addPreference(preferenceCategory)

            permissionTypesPerCategory.data
                .sortedBy { getString(it.upperCaseLabel()) }
                .forEach { permissionType ->
                    val icon = permissionType.icon(requireContext())
                    preferenceCategory.addPreference(
                        getPermissionTypePreference(permissionType, icon)
                    )
                }
        }
    }

    private fun onDeletionMethod(preference: DeletionPermissionTypesPreference): () -> Unit {
        return {
            if (
                preference.getHealthPermissionType() !in
                    viewModel.setOfPermissionTypesToBeDeleted.value.orEmpty()
            ) {
                viewModel.addToDeleteSet(preference.getHealthPermissionType())
            } else {
                viewModel.removeFromDeleteSet(preference.getHealthPermissionType())
            }
            updateMenu(screenState = DELETE)
        }
    }

    private fun updateMenu(
        screenState: AllDataViewModel.AllDataDeletionScreenState,
        hasData: Boolean = true,
    ) {
        if (!hasData && showMedicalData) {
            setupSharedMenu(viewLifecycleOwner, logger)
            return
        }

        if (!hasData) {
            setupMenu(
                R.menu.all_data_empty_state_menu,
                viewLifecycleOwner,
                logger,
                onDataSourcesClick,
            )
            return
        }

        if (screenState == VIEW && showMedicalData) {
            setupMenu(
                R.menu.all_data_menu_without_data_sources,
                viewLifecycleOwner,
                logger,
                onMenuSetup,
            )
            return
        }

        if (screenState == VIEW) {
            setupMenu(R.menu.all_data_menu, viewLifecycleOwner, logger, onMenuSetup)
            return
        }

        if (viewModel.setOfPermissionTypesToBeDeleted.value.orEmpty().isEmpty()) {
            setupMenu(
                R.menu.all_data_delete_menu,
                viewLifecycleOwner,
                logger,
                onEmptyDeleteSetSetup,
            )
            return
        }

        setupMenu(R.menu.deletion_state_menu, viewLifecycleOwner, logger, onEnterDeletionState)
    }

    @VisibleForTesting
    fun triggerDeletionState(screenState: AllDataViewModel.AllDataDeletionScreenState) {
        viewModel.setScreenState(screenState)
        setupSelectAllPreference(screenState)
        updateMenu(screenState)

        iterateThroughPreferenceGroup { permissionTypePreference ->
            permissionTypePreference.showCheckbox(screenState == DELETE)
        }

        // scroll to top to show Select all preference when triggered
        if (screenState == DELETE) {
            scrollToPreference(KEY_SELECT_ALL)
        }
    }

    private fun setupEmptyState() {
        noDataPreference.isVisible = true
        footerPreference.isVisible = true
        updateMenu(screenState = VIEW, hasData = false)
    }

    private fun deleteData() {
        deletionViewModel.setDeletionType(
            DeletionType.DeleteHealthPermissionTypes(
                viewModel.setOfPermissionTypesToBeDeleted.value.orEmpty(),
                viewModel.getNumOfPermissionTypes(),
            )
        )
        childFragmentManager.setFragmentResult(START_DELETION_KEY, bundleOf())
    }

    private fun getPermissionTypePreference(
        permissionType: HealthPermissionType,
        categoryIcon: Drawable?,
    ): Preference {
        val pref =
            DeletionPermissionTypesPreference(requireContext()).also { preference ->
                preference.setShowCheckbox(viewModel.getScreenState() == DELETE)
                preference.setLogNameCheckbox(AllDataElement.PERMISSION_TYPE_BUTTON_WITH_CHECKBOX)
                preference.setLogNameNoCheckbox(AllDataElement.PERMISSION_TYPE_BUTTON_NO_CHECKBOX)

                viewModel.setOfPermissionTypesToBeDeleted.observe(viewLifecycleOwner) { deleteSet ->
                    preference.setIsChecked(permissionType in deleteSet)
                }

                preference.icon = categoryIcon
                preference.setTitle(permissionType.upperCaseLabel())
                preference.setHealthPermissionType(permissionType)

                preference.setOnPreferenceClickListener(onDeletionMethod(preference)) {
                    entriesViewModel.setScreenState(
                        EntriesViewModel.EntriesDeletionScreenState.VIEW
                    )
                    entriesViewModel.setAllEntriesSelectedValue(false)
                    entriesViewModel.currentSelectedDate.value = null
                    findNavController()
                        .navigate(
                            navigationDestination(permissionType),
                            bundleOf(PERMISSION_TYPE_NAME_KEY to permissionType.name),
                        )
                    true
                }
            }

        return pref
    }

    private fun navigationDestination(permissionType: HealthPermissionType): Int {
        return if (permissionType is FitnessPermissionType) R.id.action_allData_to_entriesAndAccess
        else R.id.action_medicalAllData_to_entriesAndAccess
    }

    private fun setupSelectAllPreference(screenState: AllDataViewModel.AllDataDeletionScreenState) {
        selectAllCheckboxPreference.isVisible = screenState == DELETE
        if (screenState == DELETE) {
            viewModel.allPermissionTypesSelected.observe(viewLifecycleOwner) {
                allPermissionTypesSelected ->
                selectAllCheckboxPreference.removeOnPreferenceClickListener()
                selectAllCheckboxPreference.setIsChecked(allPermissionTypesSelected)
                selectAllCheckboxPreference.setOnPreferenceClickListenerWithCheckbox(
                    onSelectAllPermissionTypes()
                )
            }
            selectAllCheckboxPreference.setOnPreferenceClickListenerWithCheckbox(
                onSelectAllPermissionTypes()
            )
        }
    }

    private fun onSelectAllPermissionTypes(): () -> Unit {
        return {
            iterateThroughPreferenceGroup { permissionTypePreference ->
                if (selectAllCheckboxPreference.getIsChecked()) {
                    viewModel.addToDeleteSet(permissionTypePreference.getHealthPermissionType())
                } else {
                    viewModel.removeFromDeleteSet(
                        permissionTypePreference.getHealthPermissionType()
                    )
                }
            }
            updateMenu(DELETE)
        }
    }

    private fun iterateThroughPreferenceGroup(method: (DeletionPermissionTypesPreference) -> Unit) {
        permissionTypesListGroup.children.forEach { preference ->
            if (preference is PreferenceCategory) {
                preference.children.forEach { permissionTypePreference ->
                    if (permissionTypePreference is DeletionPermissionTypesPreference) {
                        method(permissionTypePreference)
                    }
                }
            }
        }
    }
}
