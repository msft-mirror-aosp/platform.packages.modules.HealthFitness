/**
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.entrydetails

import android.health.connect.datatypes.MenstruationPeriodRecord
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.EntriesViewModel
import com.android.healthconnect.controller.data.entries.FormattedEntry.ExercisePerformanceGoalEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.ExerciseSessionEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedSectionContent
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedSectionTitle
import com.android.healthconnect.controller.data.entries.FormattedEntry.FormattedSessionDetail
import com.android.healthconnect.controller.data.entries.FormattedEntry.ItemDataEntrySeparator
import com.android.healthconnect.controller.data.entries.FormattedEntry.PlannedExerciseBlockEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.PlannedExerciseSessionEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.PlannedExerciseStepEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.ReverseSessionDetail
import com.android.healthconnect.controller.data.entries.FormattedEntry.SeriesDataEntry
import com.android.healthconnect.controller.data.entries.FormattedEntry.SessionHeader
import com.android.healthconnect.controller.data.entries.FormattedEntry.SleepSessionEntry
import com.android.healthconnect.controller.dataentries.ExerciseSessionItemViewBinder
import com.android.healthconnect.controller.dataentries.OnDeleteEntryListener
import com.android.healthconnect.controller.dataentries.PlannedExerciseSessionItemViewBinder
import com.android.healthconnect.controller.dataentries.SeriesDataItemViewBinder
import com.android.healthconnect.controller.dataentries.SleepSessionItemViewBinder
import com.android.healthconnect.controller.deletion.DeletionConstants.DELETION_TYPE
import com.android.healthconnect.controller.deletion.DeletionConstants.END_TIME
import com.android.healthconnect.controller.deletion.DeletionConstants.START_DELETION_EVENT
import com.android.healthconnect.controller.deletion.DeletionConstants.START_TIME
import com.android.healthconnect.controller.deletion.DeletionType
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsViewModel.DateEntryFragmentState
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsViewModel.DateEntryFragmentState.Loading
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsViewModel.DateEntryFragmentState.LoadingFailed
import com.android.healthconnect.controller.entrydetails.DataEntryDetailsViewModel.DateEntryFragmentState.WithData
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.permissiontypes.HealthPermissionTypesFragment.Companion.PERMISSION_TYPE_KEY
import com.android.healthconnect.controller.shared.DataType
import com.android.healthconnect.controller.shared.recyclerview.RecyclerViewAdapter
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.PageName
import com.android.healthconnect.controller.utils.logging.ToolbarElement
import com.android.healthconnect.controller.utils.setupSharedMenu
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint(Fragment::class)
class DataEntryDetailsFragment : Hilt_DataEntryDetailsFragment() {

    companion object {
        private const val ENTRY_ID_KEY = "entry_id_key"
        const val SHOW_DATA_ORIGIN_KEY = "show_data_origin_key"

        fun createBundle(
            permissionType: FitnessPermissionType,
            entryId: String,
            showDataOrigin: Boolean,
        ): Bundle {
            return bundleOf(
                PERMISSION_TYPE_KEY to permissionType,
                ENTRY_ID_KEY to entryId,
                SHOW_DATA_ORIGIN_KEY to showDataOrigin,
            )
        }
    }

    @Inject lateinit var logger: HealthConnectLogger

    private val viewModel: DataEntryDetailsViewModel by viewModels()
    private val entriesViewModel: EntriesViewModel by viewModels()

    private lateinit var permissionType: FitnessPermissionType
    private lateinit var recyclerView: RecyclerView
    private lateinit var entryId: String
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var detailsAdapter: RecyclerViewAdapter
    private var showDataOrigin: Boolean = false
    private val onDeleteEntryListener by lazy {
        object : OnDeleteEntryListener {
            override fun onDeleteEntry(
                id: String,
                dataType: DataType,
                index: Int,
                startTime: Instant?,
                endTime: Instant?,
            ) {
                deleteEntry(id, dataType, index, startTime, endTime)
            }
        }
    }
    private val sleepSessionViewBinder by lazy {
        SleepSessionItemViewBinder(
            showSecondAction = false,
            onItemClickedListener = null,
            onDeleteEntryListenerClicked = onDeleteEntryListener,
        )
    }
    private val plannedExerciseSessionViewBinder by lazy {
        PlannedExerciseSessionItemViewBinder(
            showSecondAction = false,
            onItemClickedListener = null,
            onDeleteEntryClicked = null,
        )
    }
    private val plannedExerciseBlockViewBinder by lazy { PlannedExerciseBlockViewBinder() }
    private val plannedExerciseStepViewBinder by lazy { PlannedExerciseStepViewBinder() }
    private val exercisePerformanceGoalViewBinder by lazy { ExercisePerformanceGoalViewBinder() }
    private val formattedSectionContentViewBinder by lazy { FormattedSectionContentViewBinder() }
    private val exerciseSessionItemViewBinder by lazy {
        ExerciseSessionItemViewBinder(
            showSecondAction = false,
            onItemClickedListener = null,
            onDeleteEntryClicked = onDeleteEntryListener,
        )
    }
    private val heartRateItemViewBinder by lazy {
        SeriesDataItemViewBinder(
            showSecondAction = false,
            onItemClickedListener = null,
            onDeleteEntryClicked = onDeleteEntryListener,
        )
    }
    private val itemDataEntrySeparatorViewBinder by lazy { ItemDataEntrySeparatorViewBinder() }
    private val sessionDetailViewBinder by lazy { SessionDetailViewBinder() }
    private val sessionHeaderViewBinder by lazy { SessionHeaderViewBinder() }
    private val reverseSessionDetailViewBinder by lazy { ReverseSessionDetailViewBinder() }
    private val formattedSectionTitleViewBinder by lazy { FormattedSectionTitleViewBinder() }

    private val pageName = PageName.ENTRY_DETAILS_PAGE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.setPageId(pageName)
    }

    override fun onResume() {
        super.onResume()
        logger.setPageId(pageName)
        logger.logPageImpression()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        logger.setPageId(pageName)
        val view = inflater.inflate(R.layout.fragment_data_entry_details, container, false)
        permissionType =
            requireArguments()
                .getSerializable(PERMISSION_TYPE_KEY, FitnessPermissionType::class.java)
                ?: throw IllegalArgumentException("PERMISSION_TYPE_KEY can't be null!")

        entryId =
            requireArguments().getString(ENTRY_ID_KEY)
                ?: throw IllegalArgumentException("ENTRY_ID_KEY can't be null!")

        showDataOrigin =
            requireArguments().getBoolean(SHOW_DATA_ORIGIN_KEY)
                ?: throw IllegalArgumentException("SHOW_DATA_ORIGIN_KEY can't be null!")
        errorView = view.findViewById(R.id.error_view)
        loadingView = view.findViewById(R.id.loading)
        detailsAdapter =
            RecyclerViewAdapter.Builder()
                .setViewBinder(SleepSessionEntry::class.java, sleepSessionViewBinder)
                .setViewBinder(ExerciseSessionEntry::class.java, exerciseSessionItemViewBinder)
                .setViewBinder(SeriesDataEntry::class.java, heartRateItemViewBinder)
                .setViewBinder(FormattedSessionDetail::class.java, sessionDetailViewBinder)
                .setViewBinder(SessionHeader::class.java, sessionHeaderViewBinder)
                .setViewBinder(ReverseSessionDetail::class.java, reverseSessionDetailViewBinder)
                .setViewBinder(FormattedSectionTitle::class.java, formattedSectionTitleViewBinder)
                .setViewBinder(
                    FormattedSectionContent::class.java,
                    formattedSectionContentViewBinder,
                )
                .setViewBinder(
                    PlannedExerciseSessionEntry::class.java,
                    plannedExerciseSessionViewBinder,
                )
                .setViewBinder(
                    PlannedExerciseBlockEntry::class.java,
                    plannedExerciseBlockViewBinder,
                )
                .setViewBinder(PlannedExerciseStepEntry::class.java, plannedExerciseStepViewBinder)
                .setViewBinder(
                    ExercisePerformanceGoalEntry::class.java,
                    exercisePerformanceGoalViewBinder,
                )
                .setViewBinder(ItemDataEntrySeparator::class.java, itemDataEntrySeparatorViewBinder)
                .setViewModel(entriesViewModel) // Added to adjust to the new RecyclerViewAdapter
                .build()
        recyclerView =
            view.findViewById<RecyclerView?>(R.id.data_entries_list).apply {
                layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
                adapter = detailsAdapter
            }
        viewModel.loadEntryData(permissionType, entryId, showDataOrigin)
        setupSharedMenu(viewLifecycleOwner, logger)
        logger.logImpression(ToolbarElement.TOOLBAR_SETTINGS_BUTTON)
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.sessionData.observe(viewLifecycleOwner) { state -> updateUI(state) }
    }

    private fun updateUI(state: DateEntryFragmentState) {
        when (state) {
            is Loading -> {
                loadingView.isVisible = true
                errorView.isVisible = false
                recyclerView.isVisible = false
            }
            is LoadingFailed -> {
                errorView.isVisible = true
                loadingView.isVisible = false
                recyclerView.isVisible = false
            }
            is WithData -> {
                recyclerView.isVisible = true
                detailsAdapter.updateData(state.data)
                errorView.isVisible = false
                loadingView.isVisible = false
            }
        }
    }

    private fun deleteEntry(
        uuid: String,
        dataType: DataType,
        index: Int,
        startTime: Instant?,
        endTime: Instant?,
    ) {
        val deletionType = DeletionType.DeleteDataEntry(uuid, dataType, index)

        if (deletionType.dataType == MenstruationPeriodRecord::class) {
            childFragmentManager.setFragmentResult(
                START_DELETION_EVENT,
                bundleOf(
                    DELETION_TYPE to deletionType,
                    START_TIME to startTime,
                    END_TIME to endTime,
                ),
            )
        } else {
            childFragmentManager.setFragmentResult(
                START_DELETION_EVENT,
                bundleOf(DELETION_TYPE to deletionType),
            )
        }
    }
}
