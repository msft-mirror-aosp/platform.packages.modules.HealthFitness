/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.healthconnect.controller.shared.preference

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.SwitchPreferenceCompat
import com.android.healthconnect.controller.utils.logging.ElementName
import com.android.healthconnect.controller.utils.logging.HealthConnectLogger
import com.android.healthconnect.controller.utils.logging.HealthConnectLoggerEntryPoint
import com.android.healthconnect.controller.utils.logging.UIAction
import com.android.healthconnect.controller.utils.logging.UnknownGenericElement
import dagger.hilt.android.EntryPointAccessors

/** A [SwitchPreferenceCompat] that allows logging. */
open class HealthSwitchPreference
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) :
    SwitchPreferenceCompat(context, attrs) {

    private var logger: HealthConnectLogger
    var logNameActive: ElementName = UnknownGenericElement.UNKNOWN_SWITCH_ACTIVE_PREFERENCE
    var logNameInactive: ElementName = UnknownGenericElement.UNKNOWN_SWITCH_INACTIVE_PREFERENCE
    private var loggingClickListener: OnPreferenceChangeListener? = null

    init {
        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                HealthConnectLoggerEntryPoint::class.java,
            )
        logger = hiltEntryPoint.logger()
    }

    override fun onAttached() {
        super.onAttached()
        if (isChecked) {
            logger.logImpression(logNameActive)
        } else {
            logger.logImpression(logNameInactive)
        }
    }

    override fun setOnPreferenceChangeListener(
        onPreferenceChangeListener: OnPreferenceChangeListener?
    ) {
        loggingClickListener = OnPreferenceChangeListener { preference, newValue ->
            if (newValue is Boolean && newValue) {
                logger.logInteraction(logNameInactive, UIAction.ACTION_TOGGLE_ON)
            } else if (newValue is Boolean) {
                logger.logInteraction(logNameActive, UIAction.ACTION_TOGGLE_OFF)
            }
            onPreferenceChangeListener?.onPreferenceChange(preference, newValue)!!
        }
        super.setOnPreferenceChangeListener(loggingClickListener)
    }
}
