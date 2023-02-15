/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.healthconnect.cts.ui.testing

import android.content.Context
import android.content.Intent
import android.healthconnect.cts.ui.testing.UiTestUtils.skipOnboardingIfAppears
import com.android.compatibility.common.util.UiAutomatorUtils2.getUiDevice

/** A class that provides a way to launch the Health Connect [MainActivity] in tests. */
object ActivityLauncher {
    /** Launches the Main activity and exits it once [block] completes. */
    fun Context.launchMainActivity(block: () -> Unit) {
        val intent = Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        executeBlockAndExit(block) {
            startActivity(intent)
            skipOnboardingIfAppears()
        }
    }

    private fun executeBlockAndExit(block: () -> Unit, launchActivity: () -> Unit) {
        val uiDevice = getUiDevice()
        uiDevice.waitForIdle()
        launchActivity()
        uiDevice.waitForIdle()
        block()
        uiDevice.pressBack()
        uiDevice.waitForIdle()
    }
}