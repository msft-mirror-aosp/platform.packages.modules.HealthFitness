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
package com.android.healthconnect.controller.shared.recyclerview

import android.content.res.Resources
import android.view.View
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.EntriesElement

interface DeletionViewBinder<T, V : View> : ViewBinder<T, V> {
    val logNameWithCheckbox: ElementName
        get() = EntriesElement.ENTRY_BUTTON_WITH_CHECKBOX

    val logNameWithoutCheckbox: ElementName
        get() = EntriesElement.ENTRY_BUTTON_NO_CHECKBOX

    /** Populate a view with data. */
    fun bind(
        view: View,
        data: T,
        index: Int,
        isDeletionState: Boolean = false,
        isChecked: Boolean = false,
    )

    /**
     * Content description which includes the information about the checked state of the checkbox
     */
    fun getUpdatedContentDescription(
        resources: Resources,
        a11yTitle: String,
        isDeletionState: Boolean,
        isChecked: Boolean,
    ): String {
        val separator = resources.getString(R.string.separator)
        val checkedState =
            if (isDeletionState) {
                if (isChecked) {
                    separator + resources.getString(R.string.a11y_checked)
                } else {
                    separator + resources.getString(R.string.a11y_unchecked)
                }
            } else ""
        return a11yTitle + checkedState
    }
}
