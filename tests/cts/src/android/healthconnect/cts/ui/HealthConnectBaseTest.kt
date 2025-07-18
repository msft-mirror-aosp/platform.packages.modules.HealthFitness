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

package android.healthconnect.cts.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.healthconnect.cts.utils.AssumptionCheckerRule
import android.healthconnect.cts.utils.TestUtils
import androidx.test.core.app.ApplicationProvider
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.FreezeRotationRule
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Rule

open class HealthConnectBaseTest {
    @get:Rule val disableAnimationRule = DisableAnimationRule()

    @get:Rule val freezeRotationRule = FreezeRotationRule()

    @get:Rule
    var mSupportedHardwareRule =
        AssumptionCheckerRule(
            { TestUtils.isHealthConnectFullySupported() },
            "Tests should run on supported hardware only.",
        )

    protected val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpClass() {
        // Collapse notifications
        runShellCommandOrThrow("cmd statusbar collapse")

        unlockDevice()
    }

    private fun unlockDevice() {
        runShellCommandOrThrow("input keyevent KEYCODE_WAKEUP")
        if ("false".equals(runShellCommandOrThrow("cmd lock_settings get-disabled"))) {
            // Unlock screen only when it's lock settings enabled to prevent showing "wallpaper
            // picker" which may cover another UI elements on freeform window configuration.
            runShellCommandOrThrow("input keyevent 82")
        }
        runShellCommandOrThrow("wm dismiss-keyguard")

        val keyguardManager = context.getSystemService(KeyguardManager::class.java)

        eventually {
            assertWithMessage("device is locked").that(keyguardManager.isDeviceLocked).isFalse()
            assertWithMessage("keyguard is locked").that(keyguardManager.isKeyguardLocked).isFalse()
        }
    }

    fun assertPermissionGranted(permission: String, packageName: String) {
        assertWithMessage("$permission for $packageName is not granted")
            .that(context.packageManager.checkPermission(permission, packageName))
            .isEqualTo(PackageManager.PERMISSION_GRANTED)
    }

    fun assertPermissionDenied(permission: String, packageName: String) {
        assertWithMessage("$permission for $packageName is not denied")
            .that(context.packageManager.checkPermission(permission, packageName))
            .isEqualTo(PackageManager.PERMISSION_DENIED)
    }
}
