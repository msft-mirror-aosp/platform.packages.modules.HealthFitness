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
package com.android.healthconnect.controller.categories

import android.icu.text.MessageFormat
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.autodelete.AutoDeleteRange
import com.android.healthconnect.controller.autodelete.AutoDeleteViewModel
import com.android.healthconnect.controller.autodelete.numberOfMonths
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.FRAGMENT_TAG_DELETION
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionFragment
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.deletion.DeletionViewModel
import com.android.healthconnect.controller.utils.SendFeedbackAndHelpMenu.setupMenu
import com.android.healthconnect.controller.utils.setTitle
import dagger.hilt.android.AndroidEntryPoint

/** Fragment for health data categories. */
@AndroidEntryPoint(PreferenceFragmentCompat::class)
class HealthDataCategoriesFragment : Hilt_HealthDataCategoriesFragment() {

    companion object {
        const val CATEGORY_NAME_KEY = "category_name_key"
        private const val BROWSE_DATA_CATEGORY = "browse_data_category"
        private const val AUTO_DELETE_BUTTON = "auto_delete_button"
        private const val DELETE_ALL_DATA_BUTTON = "delete_all_data"
    }

    private val categoriesViewModel: HealthDataCategoryViewModel by viewModels()
    private val autoDeleteViewModel: AutoDeleteViewModel by activityViewModels()
    private val deletionViewModel: DeletionViewModel by activityViewModels()

    private val mBrowseDataCategory: PreferenceGroup? by lazy {
        preferenceScreen.findPreference(BROWSE_DATA_CATEGORY)
    }
    private val mAutoDelete: Preference? by lazy {
        preferenceScreen.findPreference(AUTO_DELETE_BUTTON)
    }
    private val mDeleteAllData: Preference? by lazy {
        preferenceScreen.findPreference(DELETE_ALL_DATA_BUTTON)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.health_data_categories_screen, rootKey)

        if (childFragmentManager.findFragmentByTag(FRAGMENT_TAG_DELETION) == null) {
            childFragmentManager.commitNow { add(DeletionFragment(), FRAGMENT_TAG_DELETION) }
        }
        mAutoDelete?.setOnPreferenceClickListener {
            findNavController().navigate(R.id.action_healthDataCategories_to_autoDelete)
            true
        }
        mDeleteAllData?.setOnPreferenceClickListener {
            val deletionType = DeletionType.DeletionTypeAllData()
            childFragmentManager.setFragmentResult(
                START_DELETION_EVENT, bundleOf(DELETION_TYPE to deletionType))
            true
        }
        mDeleteAllData?.isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        setTitle(R.string.data_title)
    }

    private fun buildSummary(autoDeleteRange: AutoDeleteRange): String {
        return when (autoDeleteRange) {
            AutoDeleteRange.AUTO_DELETE_RANGE_NEVER -> getString(R.string.range_never)
            AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS -> {
                val count = numberOfMonths(AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS)
                MessageFormat.format(
                    getString(R.string.range_after_x_months), mapOf("count" to count))
            }
            AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS -> {
                val count = numberOfMonths(AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS)
                MessageFormat.format(
                    getString(R.string.range_after_x_months), mapOf("count" to count))
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu(this, viewLifecycleOwner)

        categoriesViewModel.categoriesData.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HealthDataCategoryViewModel.CategoriesFragmentState.Loading -> {}
                is HealthDataCategoryViewModel.CategoriesFragmentState.WithData -> {
                    updateDataList(state.categories)
                }
            }
        }

        autoDeleteViewModel.storedAutoDeleteRange.observe(viewLifecycleOwner) { state ->
            when (state) {
                AutoDeleteViewModel.AutoDeleteState.Loading -> {
                    mAutoDelete?.summary = ""
                }
                is AutoDeleteViewModel.AutoDeleteState.LoadingFailed -> {
                    mAutoDelete?.summary = ""
                }
                is AutoDeleteViewModel.AutoDeleteState.WithData -> {
                    mAutoDelete?.summary = buildSummary(state.autoDeleteRange)
                }
            }
        }

        deletionViewModel.categoriesReloadNeeded.observe(viewLifecycleOwner) { isReloadNeeded ->
            if (isReloadNeeded) {
                categoriesViewModel.loadCategories()
            }
        }
    }

    private fun updateDataList(categoriesList: List<HealthDataCategory>) {
        val sortedCategoriesList: List<HealthDataCategory> =
            categoriesList.sortedBy { getString(it.uppercaseTitle) }
        mBrowseDataCategory?.removeAll()
        if (sortedCategoriesList.isEmpty()) {
            mBrowseDataCategory?.addPreference(
                Preference(requireContext()).also { it.setSummary(R.string.no_categories) })
        } else {
            sortedCategoriesList.forEach { category ->
                mBrowseDataCategory?.addPreference(
                    Preference(requireContext()).also {
                        it.setTitle(category.uppercaseTitle)
                        it.setIcon(category.icon)
                        it.onPreferenceClickListener =
                            Preference.OnPreferenceClickListener {
                                findNavController()
                                    .navigate(
                                        R.id.action_healthDataCategories_to_healthPermissionTypes,
                                        bundleOf(CATEGORY_NAME_KEY to category.name))
                                true
                            }
                    })
            }
        }

        categoriesViewModel.allCategoriesData.observe(viewLifecycleOwner) { allCategoriesList ->
            if (sortedCategoriesList.size < allCategoriesList.size) {
                mBrowseDataCategory?.addPreference(
                    Preference(requireContext()).also {
                        it.setTitle(R.string.see_all_categories)
                        it.setIcon(R.drawable.ic_arrow_forward)
                        it.onPreferenceClickListener =
                            Preference.OnPreferenceClickListener {
                                findNavController()
                                    .navigate(
                                        R.id.action_healthDataCategories_to_healthDataAllCategories)
                                true
                            }
                    })
            }
        }

        mDeleteAllData?.isEnabled = categoriesList.isNotEmpty()
    }
}
