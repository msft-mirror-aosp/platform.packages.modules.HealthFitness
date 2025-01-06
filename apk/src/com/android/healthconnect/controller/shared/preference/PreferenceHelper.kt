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
package com.android.healthconnect.controller.shared.preference

import android.content.Context
import android.os.Build
import android.view.View.OnClickListener
import androidx.preference.Preference
import com.android.settingslib.widget.SettingsThemeHelper
import com.android.settingslib.widget.TopIntroPreference

fun topIntroPreference(
    context: Context,
    preferenceKey: String? = null,
    preferenceTitle: String? = null,
    learnMoreText: String? = null,
    learnMoreAction: OnClickListener? = null,
    preferenceOrder: Int = 0,
): Preference {
    // TODO(b/378469065): Remove isExpressive check once TopIntroPreference is fixed. At the moment
    // it does not display the learn more link when expressive theming is off.
    return if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            SettingsThemeHelper.isExpressiveTheme(context)
    ) {
        TopIntroPreference(context).apply {
            preferenceKey?.let { key = it }
            preferenceTitle?.let { title = it }
            learnMoreText?.let { setLearnMoreText(it) }
            learnMoreAction?.let { setLearnMoreAction(it) }
            order = preferenceOrder
        }
    } else {
        LegacyTopIntroPreference(context).apply {
            preferenceKey?.let { key = it }
            preferenceTitle?.let { setTitle(it) }
            learnMoreText?.let { setLearnMoreText(it) }
            learnMoreAction?.let { setLearnMoreAction(it) }
            order = preferenceOrder
        }
    }
}
