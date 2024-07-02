/**
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.healthconnect.controller.tests.utils

import android.provider.Settings.System
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.UserSettings
import com.android.compatibility.common.util.UserSettings.Namespace
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Test Rule that clears the user time format setting so the locale-defined time format is used. */
class ClearTimeFormatRule : TestWatcher() {
    private lateinit var systemSettings: UserSettings
    private var previousTimeFormat: String? = null

    override fun starting(description: Description) {
        super.starting(description)

        val context = InstrumentationRegistry.getInstrumentation().context
        systemSettings = UserSettings(context, Namespace.SYSTEM)
        previousTimeFormat = systemSettings.get(System.TIME_12_24)
        if (previousTimeFormat != null) {
            // Clear setting so locale-defined time format is used.
            systemSettings.syncSet(System.TIME_12_24, null)
        }
    }

    override fun finished(description: Description) {
        super.finished(description)

        if (previousTimeFormat != null) {
            systemSettings.syncSet(System.TIME_12_24, previousTimeFormat)
        }
    }
}