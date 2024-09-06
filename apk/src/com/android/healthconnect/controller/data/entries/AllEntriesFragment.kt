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
package com.android.healthconnect.controller.data.entries

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.VERTICAL
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.appdata.AppDataFragment.Companion.PERMISSION_TYPE_NAME_KEY
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.Empty
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.Loading
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.LoadingFailed
import com.android.healthconnect.controller.data.entries.EntriesViewModel.EntriesFragmentState.With
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationPeriod
import com.android.healthconnect.controller.data.entries.datenavigation.DateNavigationView
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsFragment
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissions.data.HealthPermissionType
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
import com.android.healthconnect.controller.permissions.data.fromPermissionTypeName
import com.android.healthconnect.controller.selectabledeletion.DeletionConstants
import com.android.healthconnect.controller.selectabledeletion.DeletionFragment
import com.android.healthconnect.controller.selectabledeletion.DeletionViewModel
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.recyclerview.RecyclerViewAdapter
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setTitle
import com.android.healthconnect.controller.utils.setupSharedMenu
import com.android.healthconnect.controller.utils.setupMenu
import com.android.healthconnect.controller.utils.setupMenu
import com.android.settingslib.widget.AppHeaderPreference
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

/** Fragment to show health data entries. */
@AndroidEntryPoint(Fragment::class)
class AllEntriesFragment : Hilt_AllEntriesFragment() {

    companion object {
        private const val DELETION_TAG = "DeletionTag"
    }

    @Inject lateinit var logger: HealthConnectLogger
    // TODO(b/291249677): Add logging.

    private lateinit var permissionType: HealthPermissionType
    private val entriesViewModel: EntriesViewModel by activityViewModels()
    private val deletionViewModel : DeletionViewModel by activityViewModels()
    private lateinit var header: AppHeaderPreference
    private lateinit var dateNavigationView: DateNavigationView
    private lateinit var entriesRecyclerView: RecyclerView
    private lateinit var noDataView: TextView
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var adapter: RecyclerViewAdapter

    private val onDeleteEntryListener by lazy {
        object: OnDeleteEntryListener{
            override fun onDeleteEntry(id: String, dataType: DataType, index: Int, startTime: Instant?, endTime: Instant?) {
                val entriesToDelete = entriesViewModel.setOfEntriesToBeDeleted.value.orEmpty()

                if (id in entriesToDelete) {
                    entriesViewModel.removeFromDeleteSet(id)
                } else {
                    entriesViewModel.addToDeleteSet(id)
                    if (entriesViewModel.getDataType() == null) {
                        entriesViewModel.setDataType(dataType)
                    }
                }
                updateMenu(isDeletionState = true)
            }
        }
    }
    private val onClickEntryListener by lazy {
        object : OnClickEntryListener {
            override fun onItemClicked(id: String, index: Int) {
                findNavController()
                    .navigate(
                        R.id.action_entriesAndAccessFragment_to_dataEntryDetailsFragment,
                        DataEntryDetailsFragment.createBundle(
                            permissionType as FitnessPermissionType, id, showDataOrigin = true))
            }
        }
    }
    private val aggregationViewBinder by lazy { AggregationViewBinder() }
    private val entryViewBinder by lazy { EntryItemViewBinder(onDeleteEntryListener = onDeleteEntryListener) }
    private val medicalEntryViewBinder by lazy { MedicalEntryItemViewBinder() }
    private val sectionTitleViewBinder by lazy { SectionTitleViewBinder() }
    private val sleepSessionViewBinder by lazy {
        SleepSessionItemViewBinder(onItemClickedListener = onClickEntryListener, onDeleteEntryListener = onDeleteEntryListener)
    }
    private val exerciseSessionItemViewBinder by lazy {
        ExerciseSessionItemViewBinder(onItemClickedListener = onClickEntryListener, onDeleteEntryListener = onDeleteEntryListener)
    }
    private val seriesDataItemViewBinder by lazy {
        SeriesDataItemViewBinder(onItemClickedListener = onClickEntryListener, onDeleteEntryListener = onDeleteEntryListener)
    }

    // Not in deletion state
    private val onMenuSetup: (MenuItem) -> Boolean = { menuItem ->
        when (menuItem.itemId) {
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_entries, container, false)
        if (requireArguments().containsKey(PERMISSION_TYPE_NAME_KEY)) {
            val permissionTypeName =
                arguments?.getString(PERMISSION_TYPE_NAME_KEY)
                    ?: throw IllegalArgumentException("PERMISSION_TYPE_NAME_KEY can't be null!")
            permissionType = fromPermissionTypeName(permissionTypeName)
        }
        setTitle(permissionType.upperCaseLabel())
        logger.logImpression(ToolbarElement.TOOLBAR_SETTINGS_BUTTON)

        dateNavigationView = view.findViewById(R.id.date_navigation_view)
        if (permissionType is MedicalPermissionType) {
            dateNavigationView.isVisible = false
        }
        noDataView = view.findViewById(R.id.no_data_view)
        errorView = view.findViewById(R.id.error_view)
        loadingView = view.findViewById(R.id.loading)
        adapter =
            RecyclerViewAdapter.Builder()
                .setViewBinder(FormattedEntry.FormattedDataEntry::class.java, entryViewBinder)
                .setViewBinder(
                    FormattedEntry.FormattedMedicalDataEntry::class.java, medicalEntryViewBinder)
                .setViewBinder(FormattedEntry.SleepSessionEntry::class.java, sleepSessionViewBinder)
                .setViewBinder(
                    FormattedEntry.ExerciseSessionEntry::class.java, exerciseSessionItemViewBinder)
                .setViewBinder(FormattedEntry.SeriesDataEntry::class.java, seriesDataItemViewBinder)
                .setViewBinder(
                    FormattedEntry.FormattedAggregation::class.java, aggregationViewBinder)
                .setViewBinder(
                    FormattedEntry.EntryDateSectionHeader::class.java, sectionTitleViewBinder)
                .setViewModel(entriesViewModel)
                .build()
        entriesRecyclerView =
            view.findViewById<RecyclerView?>(R.id.data_entries_list).also {
                it.adapter = adapter
                it.layoutManager = LinearLayoutManager(context, VERTICAL, false)
            }

        if(childFragmentManager.findFragmentByTag(DELETION_TAG) == null){
            childFragmentManager.commitNow { add(DeletionFragment(), DELETION_TAG) }
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dateNavigationView.setDateChangedListener(
            object : DateNavigationView.OnDateChangedListener {
                override fun onDateChanged(
                    displayedStartDate: Instant,
                    period: DateNavigationPeriod
                ) {
                    entriesViewModel.loadEntries(permissionType, displayedStartDate, period)
                }
            })

        deletionViewModel.entriesReloadNeeded.observe(viewLifecycleOwner) { isReloadNeeded
            ->
            if (isReloadNeeded) {
                entriesViewModel.setIsDeletionState(false)
                entriesViewModel.loadEntries(
                        permissionType, dateNavigationView.getDate(), dateNavigationView.getPeriod())
                deletionViewModel.resetEntriesReloadNeeded()
            }
        }

        header = AppHeaderPreference(requireContext())
        observeEntriesUpdates()
    }

    override fun onResume() {
        super.onResume()
        setTitle(permissionType.upperCaseLabel())
        if (entriesViewModel.currentSelectedDate.value != null &&
            entriesViewModel.period.value != null) {
            val date = entriesViewModel.currentSelectedDate.value!!
            val selectedPeriod = entriesViewModel.period.value!!
            dateNavigationView.setDate(date)
            dateNavigationView.setPeriod(selectedPeriod)
            entriesViewModel.loadEntries(permissionType, date, selectedPeriod)
        } else {
            entriesViewModel.loadEntries(
                permissionType, dateNavigationView.getDate(), dateNavigationView.getPeriod())
        }
        //
        //        logger.setPageId(pageName)
        //        logger.logPageImpression()
    }
    private fun updateMenu(isDeletionState: Boolean, hasData: Boolean = true) {
        if (!hasData) {
            setupSharedMenu(viewLifecycleOwner, logger)
            return
        }

        if (!isDeletionState) {
            setupMenu(R.menu.all_entries_menu, viewLifecycleOwner, logger, onMenuSetup)
            return
        }

        if (entriesViewModel.setOfEntriesToBeDeleted.value.orEmpty().isEmpty()) {
            setupMenu(
                    R.menu.all_data_delete_menu, viewLifecycleOwner, logger, onEmptyDeleteSetSetup)
            return
        }

        setupMenu(R.menu.deletion_state_menu, viewLifecycleOwner, logger, onEnterDeletionState)
    }

    @VisibleForTesting
    fun triggerDeletionState(isDeletionState: Boolean){
        updateMenu(isDeletionState)
        adapter.showCheckBox(isDeletionState)
        entriesViewModel.setIsDeletionState(isDeletionState)
        if(entriesViewModel.getDateNavigationText()== null){
            dateNavigationView.getDateNavigationText()?.let { entriesViewModel.setDateNavigationText(it) }
        }
        entriesViewModel.getDateNavigationText()?.let { dateSpinnerText -> dateNavigationView.disableDateNavigationView(isEnabled = !isDeletionState, dateSpinnerText) }

    }

    private fun deleteData(){
        entriesViewModel.getDataType()?.let { deletionViewModel.setEntriesDeleteSet(entriesViewModel.setOfEntriesToBeDeleted.value.orEmpty(), it) }
        childFragmentManager.setFragmentResult(DeletionConstants.START_DELETION_KEY, bundleOf())
    }

    private fun observeEntriesUpdates() {
        entriesViewModel.entries.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Loading -> {
                    loadingView.isVisible = true
                    noDataView.isVisible = false
                    errorView.isVisible = false
                    entriesRecyclerView.isVisible = false
                }
                is Empty -> {
                    noDataView.isVisible = true
                    loadingView.isVisible = false
                    errorView.isVisible = false
                    entriesRecyclerView.isVisible = false
                    updateMenu(isDeletionState = false, hasData = false)
                    entriesViewModel.getDateNavigationText()?.let { dateSpinnerText -> dateNavigationView.disableDateNavigationView(isEnabled = true, dateSpinnerText) }
                }
                is With -> {
                    entriesRecyclerView.isVisible = true
                    adapter.updateData(state.entries)
                    entriesRecyclerView.scrollToPosition(0)
                    errorView.isVisible = false
                    noDataView.isVisible = false
                    loadingView.isVisible = false
                    triggerDeletionState(isDeletionState = entriesViewModel.isDeletionState.value ?: false)
                }
                is LoadingFailed -> {
                    errorView.isVisible = true
                    loadingView.isVisible = false
                    noDataView.isVisible = false
                    entriesRecyclerView.isVisible = false
                }
            }
        }
    }
}
