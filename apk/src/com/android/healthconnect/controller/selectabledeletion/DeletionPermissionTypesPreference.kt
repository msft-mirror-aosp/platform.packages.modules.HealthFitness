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
package com.android.healthconnect.controller.selectabledeletion

import android.content.Context
import android.view.View.GONE
import android.view.View.OnClickListener
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceViewHolder
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.permissions.data.FitnessPermissionStrings
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.shared.preference.HealthPreference

/** Custom preference for displaying checkboxes where the user can delete their data */
class DeletionPermissionTypesPreference constructor(context: Context) : HealthPreference(context) {
    private var checkboxButtonListener: OnClickListener? = null

    // TODO: b/341886932
    // var logName : ElementName = ErrorPageElement.UNKNOWN_ELEMENT
    private var isShowCheckbox: Boolean = false
    private var widgetFrame: ViewGroup? = null
    private var checkBox: CheckBox? = null
    private var isChecked: Boolean = false
    private lateinit var mFitnessPermissionType: FitnessPermissionType

    init {
        widgetLayoutResource = R.layout.widget_checkbox
        isSelectable = true
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        widgetFrame = holder?.findViewById(android.R.id.widget_frame) as ViewGroup?
        showCheckbox(isShowCheckbox)

        checkBox = holder?.findViewById(R.id.checkbox_button) as CheckBox

        checkBox?.isChecked = this.isChecked

        checkBox?.contentDescription =
            context.getString(
                FitnessPermissionStrings.fromPermissionType(mFitnessPermissionType).uppercaseLabel)

        checkBox?.setOnClickListener(checkboxButtonListener)

        val widgetFrameParent: ViewGroup? = widgetFrame?.parent as ViewGroup?
        widgetFrameParent?.setPaddingRelative(
            widgetFrameParent.paddingStart,
            widgetFrameParent.paddingTop,
            /* end = */ 0,
            widgetFrameParent.paddingBottom)
    }

    /** Set a click listener to check the checkbox */
    fun setOnPreferenceClickListener(
        method: () -> Unit,
        onPreferenceClickListener: OnPreferenceClickListener
    ) {
        val clickListener = OnPreferenceClickListener {
            if (isShowCheckbox) {
                checkBox?.toggle()
                // Set local variable to current value of whether checkBox is checked
                isChecked = checkBox?.isChecked ?: false
                method()
            } else {
                onPreferenceClickListener.onPreferenceClick(it)
            }
            true
        }

        checkboxButtonListener = OnClickListener {
            isChecked = !isChecked
            method()
        }

        super.setOnPreferenceClickListener(clickListener)
    }

    fun setHealthPermissionType(fitnessPermissionType: FitnessPermissionType) {
        this.mFitnessPermissionType = fitnessPermissionType
    }

    fun getHealthPermissionType(): FitnessPermissionType {
        return mFitnessPermissionType
    }

    /** Display or hide checkbox */
    fun showCheckbox(isShowCheckbox: Boolean) {
        setShowCheckbox(isShowCheckbox)
        widgetFrame?.visibility = if (isShowCheckbox) VISIBLE else GONE
        widgetFrame?.tag = if (isShowCheckbox) "checkbox" else ""
    }

    fun setIsChecked(isChecked: Boolean) {
        this.isChecked = isChecked
        checkBox?.isChecked = isChecked
        notifyChanged()
    }

    fun getIsChecked(): Boolean {
        return isChecked
    }

    fun setShowCheckbox(isShowCheckbox: Boolean) {
        this.isShowCheckbox = isShowCheckbox
    }
}
