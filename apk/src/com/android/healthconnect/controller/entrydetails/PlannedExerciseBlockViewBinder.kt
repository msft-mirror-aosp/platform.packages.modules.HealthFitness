/**
 * Copyright (C) 2024 The Android Open Source Project
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

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.data.entries.FormattedEntry.PlannedExerciseBlockEntry
import com.android.healthconnect.controller.shared.recyclerview.ViewBinder

class PlannedExerciseBlockViewBinder : ViewBinder<PlannedExerciseBlockEntry, View> {
    override fun newView(parent: ViewGroup): View {
        return LayoutInflater.from(parent.context)
            .inflate(R.layout.item_planned_exercise_block_entry, parent, false)
    }

    override fun bind(view: View, data: PlannedExerciseBlockEntry, index: Int) {
        // TODO(b/332538555) Add implementation for telemetry
        val title = view.findViewById<TextView>(R.id.planned_exercise_block_title)

        title.text = data.title
        title.contentDescription = data.titleA11y
    }
}
