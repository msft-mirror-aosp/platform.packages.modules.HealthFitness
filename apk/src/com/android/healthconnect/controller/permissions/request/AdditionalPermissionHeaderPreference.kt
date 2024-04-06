/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.healthconnect.controller.permissions.request

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.boldAppName

internal class AdditionalPermissionHeaderPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var title: TextView
    private var appName: String? = null
    private var titleText = 0

    private lateinit var summary: TextView
    private var summaryText = ""

    init {
        layoutResource = R.layout.widget_request_additional_permission_header
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        title = holder.findViewById(R.id.title) as TextView
        summary = holder.findViewById(R.id.summary) as TextView

        updateTitle()
        updateSummary()
    }

    fun bind(titleText: Int, appName: String, summaryText: String) {
        this.appName = appName
        this.titleText = titleText
        this.summaryText = summaryText
        notifyChanged()
    }

    private fun updateTitle() {
        if (titleText != 0) {
            val text = context.getString(titleText, appName)
            title.text = boldAppName(appName, text)
        }
    }

    private fun updateSummary() {
        summary.text = summaryText
    }
}
