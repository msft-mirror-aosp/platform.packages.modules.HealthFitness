/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.healthconnect.controller.autodelete

import android.content.Context
import android.icu.text.MessageFormat
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceCategory
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.logging.AutoDeleteElement
import com.android.healthconnect.controller.utils.logging.ErrorPageElement
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.settingslib.widget.SelectorWithWidgetPreference

/** A custom PreferenceCategory that displays selectable auto-delete range options. */
class AutoDeleteRangePickerPreference(
    context: Context,
    private val childFragmentManager: FragmentManager,
    private var autoDeleteRange: AutoDeleteRange,
    private val logger: HealthConnectLogger,
) : PreferenceCategory(context), SelectorWithWidgetPreference.OnClickListener {

    init {
        key = AUTO_DELETE_RANGE_PICKER_PREFERENCE_KEY
        title = context.getString(R.string.auto_delete_section)
    }

    companion object {
        const val SET_TO_NEVER_EVENT = "SET_TO_NEVER_EVENT"
        const val AUTO_DELETE_RANGE_PICKER_PREFERENCE_KEY = "auto_delete_range_picker"
    }

    override fun onAttached() {
        super.onAttached()

        if (preferenceCount == 0) {
            addSelectorPreference(
                createRangeString(AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS),
                AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS,
                AutoDeleteElement.AUTO_DELETE_3_MONTHS_BUTTON,
            )
            addSelectorPreference(
                createRangeString(AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS),
                AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS,
                AutoDeleteElement.AUTO_DELETE_18_MONTHS_BUTTON,
            )
            addSelectorPreference(
                context.getString(R.string.range_never),
                AutoDeleteRange.AUTO_DELETE_RANGE_NEVER,
                AutoDeleteElement.AUTO_DELETE_NEVER_BUTTON,
            )
        }
        updateSelectedPreference()
    }

    private fun addSelectorPreference(
        preferenceTitle: String,
        range: AutoDeleteRange,
        element: AutoDeleteElement,
    ) {
        val selectorPreference =
            SelectorWithWidgetPreference(context).apply {
                title = preferenceTitle
                key = range.name
                setOnClickListener(this@AutoDeleteRangePickerPreference)
                logger.logImpression(element)
            }
        addPreference(selectorPreference)
    }

    private fun createRangeString(range: AutoDeleteRange): String {
        return MessageFormat.format(
            context.getString(R.string.range_after_x_months),
            mapOf("count" to range.numberOfMonths)
        )
    }

    private fun updateSelectedPreference() {
        for (i in 0 until preferenceCount) {
            val preference = getPreference(i)
            if (preference is SelectorWithWidgetPreference) {
                preference.isChecked = preference.key == autoDeleteRange.name
            }
        }
    }

    private fun setToNeverOrAskConfirmation(newRange: AutoDeleteRange) {
        if (newRange == AutoDeleteRange.AUTO_DELETE_RANGE_NEVER) {
            childFragmentManager.setFragmentResult(SET_TO_NEVER_EVENT, Bundle())
        } else {
            childFragmentManager.setFragmentResult(
                AutoDeleteConfirmationDialogFragment.AUTO_DELETE_CONFIRMATION_DIALOG_EVENT,
                bundleOf(
                    AutoDeleteConfirmationDialogFragment.NEW_AUTO_DELETE_RANGE_BUNDLE to newRange,
                    AutoDeleteConfirmationDialogFragment.OLD_AUTO_DELETE_RANGE_BUNDLE to
                        autoDeleteRange,
                ),
            )
        }
    }

    override fun onRadioButtonClicked(preference: SelectorWithWidgetPreference) {
        val selectedRange = AutoDeleteRange.valueOf(preference.key)
        val element =
            when (selectedRange) {
                AutoDeleteRange.AUTO_DELETE_RANGE_THREE_MONTHS ->
                    AutoDeleteElement.AUTO_DELETE_3_MONTHS_BUTTON
                AutoDeleteRange.AUTO_DELETE_RANGE_EIGHTEEN_MONTHS ->
                    AutoDeleteElement.AUTO_DELETE_18_MONTHS_BUTTON
                AutoDeleteRange.AUTO_DELETE_RANGE_NEVER ->
                    AutoDeleteElement.AUTO_DELETE_NEVER_BUTTON
                else -> ErrorPageElement.UNKNOWN_ELEMENT
            }
        logger.logInteraction(element)

        if (selectedRange != autoDeleteRange) {
            setToNeverOrAskConfirmation(selectedRange)
            autoDeleteRange = selectedRange
            updateSelectedPreference()
        }
    }

    fun updateAutoDeleteRange(newAutoDeleteRange: AutoDeleteRange) {
        autoDeleteRange = newAutoDeleteRange
        updateSelectedPreference()
    }
}
