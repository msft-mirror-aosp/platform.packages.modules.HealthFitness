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
package com.android.healthconnect.controller.dataentries

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry.SeriesDataEntry
import com.android.healthconnect.controller.shared.recyclerview.SimpleViewBinder
import com.android.healthconnect.controller.shared.recyclerview.ViewBinder
import com.android.healthconnect.controller.utils.logging.DataEntriesElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Deprecated("This won't be used once the NEW_INFORMATION_ARCHITECTURE feature is enabled.")
/** ViewBinder for SeriesDataEntry. */
class SeriesDataItemViewBinder(
    private val showSecondAction: Boolean = true,
    private val onItemClickedListener: OnClickEntryListener?,
    private val onDeleteEntryClicked: OnDeleteEntryListener?,
) : SimpleViewBinder<SeriesDataEntry, View> {

    private lateinit var logger: HealthConnectLogger

    override fun newView(parent: ViewGroup): View {
        val context = parent.context.applicationContext
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext, HealthConnectLoggerEntryPoint::class.java)
        logger = hiltEntryPoint.logger()
        return LayoutInflater.from(parent.context)
            .inflate(R.layout.item_series_data_entry, parent, false)
    }

    override fun bind(view: View, data: SeriesDataEntry, index: Int) {
        val container = view.findViewById<RelativeLayout>(R.id.item_data_entry_container)
        val divider = view.findViewById<LinearLayout>(R.id.item_data_entry_divider)
        val header = view.findViewById<TextView>(R.id.item_data_entry_header)
        val title = view.findViewById<TextView>(R.id.item_data_entry_title)
        val deleteButton = view.findViewById<ImageButton>(R.id.item_data_entry_delete)

        logger.logImpression(DataEntriesElement.DATA_ENTRY_VIEW)
        logger.logImpression(DataEntriesElement.DATA_ENTRY_DELETE_BUTTON)
        title.text = data.title
        title.contentDescription = data.titleA11y
        header.text = data.header
        header.contentDescription = data.headerA11y
        deleteButton.isVisible = showSecondAction
        divider.isVisible = showSecondAction

        deleteButton.contentDescription =
            view.resources.getString(
                R.string.data_point_action_content_description, data.headerA11y)
        deleteButton.setOnClickListener {
            logger.logInteraction(DataEntriesElement.DATA_ENTRY_DELETE_BUTTON)
            onDeleteEntryClicked?.onDeleteEntry(data.uuid, data.dataType, index)
        }
        // if the delete button is not showing, we are in the Entry details screen and this item
        // should not be clickable
        if (showSecondAction) {
            container.setOnClickListener {
                logger.logInteraction(DataEntriesElement.DATA_ENTRY_VIEW)
                onItemClickedListener?.onItemClicked(data.uuid, index)
            }
        } else {
            container.isClickable = false
        }
    }
}
