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
package android.healthconnect.cts.lib

import android.Manifest
import android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.health.connect.datatypes.*
import android.health.connect.datatypes.units.Length
import android.os.SystemClock
import android.util.Log
import androidx.test.uiautomator.*
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UiAutomatorUtils2.*
import com.android.compatibility.common.util.UiDumpUtils
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

/** UI testing helper. */
object UiTestUtils {

    /** The label of the rescan button. */
    const val RESCAN_BUTTON_LABEL = "Scan device"

    private val WAIT_TIMEOUT = Duration.ofSeconds(5)
    private val NOT_DISPLAYED_TIMEOUT = Duration.ofMillis(500)
    private val FIND_OBJECT_TIMEOUT = Duration.ofMillis(500)
    private val NEW_WINDOW_TIMEOUT_MILLIS = 3000L
    private val RETRY_TIMEOUT_MILLIS = 5000L

    private val TAG = UiTestUtils::class.java.simpleName

    private val TEST_DEVICE: Device =
        Device.Builder().setManufacturer("google").setModel("Pixel").setType(1).build()

    private val PACKAGE_NAME = "android.healthconnect.cts.ui"

    const val TEST_APP_PACKAGE_NAME = "android.healthconnect.cts.app"

    const val TEST_APP_2_PACKAGE_NAME = "android.healthconnect.cts.app2"

    const val TEST_APP_NAME = "Health Connect cts test app"

    private const val MASK_PERMISSION_FLAGS =
        (PackageManager.FLAG_PERMISSION_USER_SET or
            PackageManager.FLAG_PERMISSION_USER_FIXED or
            PackageManager.FLAG_PERMISSION_AUTO_REVOKED)

    /**
     * Waits for the given [selector] to be displayed and performs the given [uiObjectAction] on it.
     *
     * If the object is not visible attempts to find the object by scrolling down while possible. If
     * scrolling reached the bottom attempts to find the object by scrolling up.
     *
     * @throws AssertionError if the object can't be found within [waitTimeout]
     */
    fun waitDisplayed(
        selector: BySelector,
        waitTimeout: Duration = WAIT_TIMEOUT,
        uiObjectAction: (UiObject2) -> Unit = {},
    ) {
        waitFor("$selector to be displayed", waitTimeout) {
            uiObjectAction(waitFindObject(selector, it.toMillis()))
            true
        }
    }

    /**
     * Returns an object if it's visible on the screen or returns null otherwise.
     *
     * This method does _not_ scroll in an attempt to find the object.
     */
    private fun findObjectOrNull(
        selector: BySelector,
        timeout: Duration = FIND_OBJECT_TIMEOUT,
    ): UiObject2? {
        return getUiDevice().wait(Until.findObject(selector), timeout.toMillis())
    }

    /**
     * Returns an object if it's visible on the screen or throws otherwise.
     *
     * Use this if the object is expected to be visible on the screen without scrolling.
     */
    fun findObject(selector: BySelector, timeout: Duration = FIND_OBJECT_TIMEOUT): UiObject2 {
        return findObjectOrNull(selector, timeout)
            ?: throw objectNotFoundExceptionWithDump("Object not found $selector")
    }

    /**
     * Clicks on an object if it's visible on the screen or throws otherwise.
     *
     * Use this if the object is expected to be visible on the screen without scrolling.
     */
    fun findObjectAndClick(selector: BySelector) {
        findObject(selector).click()
        getUiDevice().waitForIdle()
    }

    fun clickOnDescAndWaitForNewWindow(text: String) {
        findDesc(text).clickAndWait(Until.newWindow(), NEW_WINDOW_TIMEOUT_MILLIS)
    }

    fun clickOnTextAndWaitForNewWindow(text: String) {
        findText(text).clickAndWait(Until.newWindow(), NEW_WINDOW_TIMEOUT_MILLIS)
    }

    fun navigateToNewPage(text: String) {
        scrollDownToAndFindText(text)
        clickOnTextAndWaitForNewWindow(text)
    }

    fun navigateToSeeAppData(appName: String) {
        navigateToAppPermissions(appName)
        navigateToNewPage("See app data")
    }

    fun navigateToAppPermissions(appName: String) {
        navigateToNewPage("App permissions")
        navigateToNewPage(appName)
    }

    /**
     * Returns an object with given text if it's visible on the screen or throws otherwise.
     *
     * Use this if the text label is expected to be visible on the screen without scrolling.
     */
    fun findText(text: String): UiObject2 {
        return findObject(By.text(text))
    }

    /**
     * Returns an object that contains given text if it's visible on the screen or throws otherwise.
     *
     * Use this if the text label is expected to be visible on the screen without scrolling.
     */
    fun findTextContains(text: String): UiObject2 {
        return findObject(By.textContains(text))
    }

    /**
     * Clicks on a text label if it's visible on the screen or throws otherwise.
     *
     * Use this if the text label is expected to be visible on the screen without scrolling.
     */
    fun findTextAndClick(text: String) {
        findObjectAndClick(By.text(text))
    }

    /**
     * Returns an object with given content description if it's visible on the screen.
     *
     * Throws if the object is not visible.
     *
     * Use this if the text label is expected to be visible on the screen without scrolling.
     */
    fun findDesc(desc: String): UiObject2 {
        return findObject(By.desc(desc))
    }

    /**
     * Clicks on an object with give content description if it's visible on the screen.
     *
     * Throws if the object is not visible.
     *
     * Use this if the object is expected to be visible on the screen without scrolling.
     */
    fun findDescAndClick(desc: String) {
        findObjectAndClick(By.desc(desc))
    }

    /** Throws an exception if given object is visible on the screen. */
    fun verifyObjectNotFound(selector: BySelector) {
        if (findObjectOrNull(selector) != null) {
            throw AssertionError("assertObjectNotFound: did not expect object $selector")
        }
    }

    /** Throws an exception if given text label is visible on the screen. */
    fun verifyTextNotFound(text: String) {
        verifyObjectNotFound(By.text(text))
    }

    /**
     * Waits for given object to become non visible on the screen.
     *
     * @throws TimeoutException if the object is visible on the screen after [timeout].
     */
    fun waitForObjectNotFound(selector: BySelector, timeout: Duration = NOT_DISPLAYED_TIMEOUT) {
        waitFor("$selector not to be found", timeout) { findObjectOrNull(selector) == null }
    }

    /** Quickly scrolls down to the bottom. */
    fun scrollToEnd() {
        val scrollable = UiScrollable(UiSelector().scrollable(true))
        if (!scrollable.waitForExists(FIND_OBJECT_TIMEOUT.toMillis())) {
            // Scrollable either doesn't exist or the view fully fits inside the screen.
            return
        }
        scrollable.flingToEnd(Integer.MAX_VALUE)
    }

    fun scrollDownTo(selector: BySelector) {
        val scrollable = waitFindObjectOrNull(By.scrollable(true), FIND_OBJECT_TIMEOUT.toMillis())

        scrollable?.scrollUntil(Direction.DOWN, Until.findObject(selector))
        findObject(selector)
    }

    fun scrollUpTo(selector: BySelector) {
        waitFindObject(By.scrollable(true)).scrollUntil(Direction.UP, Until.findObject(selector))
    }

    fun scrollDownToAndClick(selector: BySelector) {
        try {
            waitDisplayed(selector) { it.click() }
        } catch (e: Exception) {
            val scrollable = getUiDevice().findObject(By.scrollable(true))

            if (scrollable == null) {
                throw objectNotFoundExceptionWithDump(
                    "Scrollable not found while trying to find $selector"
                )
            }

            val obj = scrollable.scrollUntil(Direction.DOWN, Until.findObject(selector))

            findObject(selector)

            obj.click()
        }
        getUiDevice().waitForIdle()
    }

    fun scrollDownToAndFindText(text: String) {
        scrollDownTo(By.text(text))
        findText(text)
    }

    fun scrollDownToAndFindTextContains(text: String) {
        scrollDownTo(By.textContains(text))
        findTextContains(text)
    }

    fun skipOnboardingIfAppears() {
        getUiDevice().waitForIdle()

        val getStartedButton =
            findObjectWithRetry({ _ -> findObjectOrNull(By.text("Get started")) })
        if (getStartedButton != null) {
            clickOnTextAndWaitForNewWindow("Get started")
        } else {
            val getStartedButton2 =
                findObjectWithRetry({ _ -> findObjectOrNull(By.text("GET STARTED")) })
            if (getStartedButton2 != null) {
                clickOnTextAndWaitForNewWindow("GET STARTED")
            } else {
                Log.i(TAG, "No onboarding button found!")
            }
        }
    }

    /** Clicks on [UiObject2] with given [text]. */
    fun clickOnText(string: String) {
        waitDisplayed(By.text(string)) { it.click() }
    }

    fun navigateBackToHomeScreen() {
        while (isNotDisplayed("Permissions and data")) {
            try {
                waitDisplayed(By.desc("Navigate up"))
                clickOnContentDescription("Navigate up")
            } catch (e: Exception) {
                break
            }
        }
    }

    private fun isNotDisplayed(text: String): Boolean {
        try {
            waitNotDisplayed(By.text(text))
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Clicks on [UiObject2] with given [string] content description. */
    fun clickOnContentDescription(string: String) {
        waitDisplayed(By.desc(string)) { it.click() }
    }

    /** Waits for the given [selector] not to be displayed. */
    fun waitNotDisplayed(selector: BySelector, timeout: Duration = NOT_DISPLAYED_TIMEOUT) {
        waitFor("$selector not to be displayed", timeout) {
            waitFindObjectOrNull(selector, it.toMillis()) == null
        }
    }

    fun UiDevice.rotate() {
        unfreezeRotation()
        if (isNaturalOrientation) {
            setOrientationLeft()
        } else {
            setOrientationNatural()
        }
        freezeRotation()
        waitForIdle()
    }

    private fun findObjectWithRetry(
        automatorMethod: (timeoutMillis: Long) -> UiObject2?,
        timeoutMillis: Long = RETRY_TIMEOUT_MILLIS,
    ): UiObject2? {
        val startTime = SystemClock.elapsedRealtime()
        return try {
            automatorMethod(timeoutMillis)
        } catch (e: StaleObjectException) {
            val remainingTime = timeoutMillis - (SystemClock.elapsedRealtime() - startTime)
            if (remainingTime <= 0) {
                throw e
            }
            automatorMethod(remainingTime)
        }
    }

    private fun waitFor(
        message: String,
        uiAutomatorConditionTimeout: Duration,
        uiAutomatorCondition: (Duration) -> Boolean,
    ) {
        val elapsedStartMillis = SystemClock.elapsedRealtime()
        while (true) {
            getUiDevice().waitForIdle()
            val durationSinceStart =
                Duration.ofMillis(SystemClock.elapsedRealtime() - elapsedStartMillis)
            if (durationSinceStart >= uiAutomatorConditionTimeout) {
                break
            }
            val remainingTime = uiAutomatorConditionTimeout - durationSinceStart
            val uiAutomatorTimeout = minOf(uiAutomatorConditionTimeout, remainingTime)
            try {
                if (uiAutomatorCondition(uiAutomatorTimeout)) {
                    return
                } else {
                    Log.d(TAG, "Failed condition for $message, will retry if within timeout")
                }
            } catch (e: StaleObjectException) {
                Log.d(TAG, "StaleObjectException for $message, will retry if within timeout", e)
            }
        }

        throw TimeoutException("Timed out waiting for $message")
    }

    private fun objectNotFoundExceptionWithDump(message: String): Exception {
        return UiDumpUtils.wrapWithUiDump(UiObjectNotFoundException(message))
    }

    fun stepsRecordFromTestApp(): StepsRecord {
        return stepsRecord(TEST_APP_PACKAGE_NAME, /* stepCount= */ 10)
    }

    fun stepsRecordFromTestApp(stepCount: Long): StepsRecord {
        return stepsRecord(TEST_APP_PACKAGE_NAME, stepCount)
    }

    fun stepsRecordFromTestApp(startTime: Instant): StepsRecord {
        return stepsRecord(
            TEST_APP_PACKAGE_NAME,
            /* stepCount= */ 10,
            startTime,
            startTime.plusSeconds(100),
        )
    }

    fun stepsRecordFromTestApp(stepCount: Long, startTime: Instant): StepsRecord {
        return stepsRecord(TEST_APP_PACKAGE_NAME, stepCount, startTime, startTime.plusSeconds(100))
    }

    fun stepsRecordFromTestApp2(): StepsRecord {
        return stepsRecord(TEST_APP_2_PACKAGE_NAME, /* stepCount= */ 10)
    }

    fun distanceRecordFromTestApp(): DistanceRecord {
        return distanceRecord(TEST_APP_PACKAGE_NAME)
    }

    fun distanceRecordFromTestApp(startTime: Instant): DistanceRecord {
        return distanceRecord(TEST_APP_PACKAGE_NAME, startTime, startTime.plusSeconds(100))
    }

    fun distanceRecordFromTestApp2(): DistanceRecord {
        return distanceRecord(TEST_APP_2_PACKAGE_NAME)
    }

    private fun stepsRecord(packageName: String, stepCount: Long): StepsRecord {
        return stepsRecord(packageName, stepCount, Instant.now().minusMillis(1000), Instant.now())
    }

    private fun stepsRecord(
        packageName: String,
        stepCount: Long,
        startTime: Instant,
        endTime: Instant,
    ): StepsRecord {
        val dataOrigin: DataOrigin = DataOrigin.Builder().setPackageName(packageName).build()
        val testMetadataBuilder: Metadata.Builder = Metadata.Builder()
        testMetadataBuilder.setDevice(TEST_DEVICE).setDataOrigin(dataOrigin)
        testMetadataBuilder.setClientRecordId("SR" + Math.random())
        return StepsRecord.Builder(testMetadataBuilder.build(), startTime, endTime, stepCount)
            .build()
    }

    private fun distanceRecord(
        packageName: String,
        startTime: Instant,
        endTime: Instant,
    ): DistanceRecord {
        val dataOrigin: DataOrigin = DataOrigin.Builder().setPackageName(packageName).build()
        val testMetadataBuilder: Metadata.Builder = Metadata.Builder()
        testMetadataBuilder.setDevice(TEST_DEVICE).setDataOrigin(dataOrigin)
        testMetadataBuilder.setClientRecordId("SR" + Math.random())
        return DistanceRecord.Builder(
                testMetadataBuilder.build(),
                startTime,
                endTime,
                Length.fromMeters(500.0),
            )
            .build()
    }

    private fun distanceRecord(packageName: String): DistanceRecord {
        val dataOrigin: DataOrigin = DataOrigin.Builder().setPackageName(packageName).build()
        val testMetadataBuilder: Metadata.Builder = Metadata.Builder()
        testMetadataBuilder.setDevice(TEST_DEVICE).setDataOrigin(dataOrigin)
        testMetadataBuilder.setClientRecordId("SR" + Math.random())
        return DistanceRecord.Builder(
                testMetadataBuilder.build(),
                Instant.now().minusMillis(1000),
                Instant.now(),
                Length.fromMeters(500.0),
            )
            .build()
    }

    fun grantPermissionViaPackageManager(context: Context, packageName: String, permName: String) {
        val pm = context.packageManager
        if (pm.checkPermission(permName, packageName) == PERMISSION_GRANTED) {
            return
        }
        runWithShellPermissionIdentity(
            { pm.grantRuntimePermission(packageName, permName, context.user) },
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
        )
    }

    fun revokePermissionViaPackageManager(context: Context, packageName: String, permName: String) {
        val pm = context.packageManager

        if (pm.checkPermission(permName, packageName) == PERMISSION_DENIED) {
            runWithShellPermissionIdentity(
                {
                    pm.updatePermissionFlags(
                        permName,
                        packageName,
                        MASK_PERMISSION_FLAGS,
                        PackageManager.FLAG_PERMISSION_USER_SET,
                        context.user,
                    )
                },
                REVOKE_RUNTIME_PERMISSIONS,
            )
            return
        }
        runWithShellPermissionIdentity(
            { pm.revokeRuntimePermission(packageName, permName, context.user, /* reason= */ "") },
            REVOKE_RUNTIME_PERMISSIONS,
        )
    }

    fun setFont(device: UiDevice) {
        with(device) { executeShellCommand("shell settings put system font_scale 0.85") }
    }
}
