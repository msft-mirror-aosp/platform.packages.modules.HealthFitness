/**
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
package com.android.healthconnect.controller.dataentries

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry
import com.android.healthconnect.controller.shared.recyclerview.SimpleViewBinder
import com.android.healthconnect.controller.utils.logging.DataEntriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
/** ViewBinder for PlannedExerciseSessionEntry. */
class PlannedExerciseSessionItemViewBinder(
    private val showSecondAction: Boolean = true,
    private val onItemClickedListener: OnClickEntryListener?,
    private val onDeleteEntryClicked: OnDeleteEntryListener?,
) : SimpleViewBinder<FormattedEntry.PlannedExerciseSessionEntry, View> {
    private lateinit var logger: HealthConnectLogger

    override fun newView(parent: ViewGroup): View {
        val context = parent.context.applicationContext
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(context, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
        return LayoutInflater.from(parent.context).inflate(R.layout.item_data_entry, parent, false)
    }

    override fun bind(view: View, data: FormattedEntry.PlannedExerciseSessionEntry, index: Int) {
        // TODO(b/332538555) Add implementation for telemetry
        val container = view.findViewById<LinearLayout>(R.id.item_data_entry_container)
        val header = view.findViewById<TextView>(R.id.item_data_entry_header)
        val title = view.findViewById<TextView>(R.id.item_data_entry_title)
        val deleteButton = view.findViewById<ImageButton>(R.id.item_data_entry_delete)
        logger.logImpression(DataEntriesElement.PLANNED_EXERCISE_SESSION_ENTRY_BUTTON)
        if (showSecondAction) {
            logger.logImpression(DataEntriesElement.DATA_ENTRY_DELETE_BUTTON)
        }
        title.text = data.title
        title.contentDescription = data.titleA11y
        header.text = data.header
        header.contentDescription = data.headerA11y
        deleteButton.isVisible = showSecondAction

        deleteButton.contentDescription =
            view.resources.getString(
                R.string.data_point_action_content_description,
                data.headerA11y,
            )
        deleteButton.setOnClickListener {
            logger.logInteraction(DataEntriesElement.DATA_ENTRY_DELETE_BUTTON)
            onDeleteEntryClicked?.onDeleteEntry(data.uuid, data.dataType, index)
        }
        if (showSecondAction) {
            container.setOnClickListener {
                logger.logInteraction(DataEntriesElement.PLANNED_EXERCISE_SESSION_ENTRY_BUTTON)
                onItemClickedListener?.onItemClicked(data.uuid, index)
            }
        } else {
            container.isClickable = false
        }
    }
}
