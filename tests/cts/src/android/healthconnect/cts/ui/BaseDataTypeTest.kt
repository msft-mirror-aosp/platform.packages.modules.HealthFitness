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

package android.healthconnect.cts.ui

import android.health.connect.datatypes.Record
import android.healthconnect.cts.lib.ActivityLauncher.launchMainActivity
import android.healthconnect.cts.lib.ActivityLauncher.launchRequestPermissionActivity
import android.healthconnect.cts.lib.UiTestUtils.findDesc
import android.healthconnect.cts.lib.UiTestUtils.findDescAndClick
import android.healthconnect.cts.lib.UiTestUtils.findObject
import android.healthconnect.cts.lib.UiTestUtils.findText
import android.healthconnect.cts.lib.UiTestUtils.findTextAndClick
import android.healthconnect.cts.lib.UiTestUtils.revokePermissionViaPackageManager
import android.healthconnect.cts.lib.UiTestUtils.scrollDownTo
import android.healthconnect.cts.lib.UiTestUtils.scrollDownToAndClick
import android.healthconnect.cts.lib.UiTestUtils.scrollToEnd
import android.healthconnect.cts.lib.UiTestUtils.verifyTextNotFound
import android.healthconnect.cts.lib.UiTestUtils.waitForObjectNotFound
import android.healthconnect.cts.utils.PermissionHelper.getGrantedHealthPermissions
import android.healthconnect.cts.utils.TestUtils
import android.healthconnect.cts.utils.TestUtils.readAllRecords
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.uiautomator.By
import com.google.common.truth.Truth.assertThat
import java.time.Duration.ofSeconds
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

abstract class BaseDataTypeTest<T : Record> : HealthConnectBaseTest() {
    abstract val dataTypeString: String
    abstract val dataCategoryString: String
    abstract val permissionString: String
    abstract val permissions: List<String>
    abstract val sameCategoryDataTypeString: String?
    abstract val anotherCategoryString: String
    abstract val anotherCategoryDataTypeString: String

    abstract fun createRecord(): T

    abstract val expectedRecordHeader: String
    abstract val expectedRecordTitle: String
    abstract val expectedRecordSubtitle: String?

    abstract fun createRecordToBeDeleted(): Record

    abstract val expectedRecordToBeDeletedHeader: String
    abstract val expectedRecordToBeDeletedTitle: String

    abstract fun createSameCategoryRecord(): Record?

    abstract fun createAnotherCategoryRecord(): Record

    @get:Rule val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    companion object {
        const val APP_WITH_READ_WRITE_PERMISSIONS =
            "android.healthconnect.cts.testapp.readWritePerms.A"
        const val APP_WITH_READ_WRITE_PERMISSIONS_LABEL =
            "CtsHealthConnectTestAppAWithNormalReadWritePermission"
    }

    private lateinit var record: Record

    @Before
    fun setup() {
        assertThat(getGrantedHealthPermissions(context.packageName))
            .containsAtLeastElementsIn(permissions)
        assertThat(getGrantedHealthPermissions(APP_WITH_READ_WRITE_PERMISSIONS))
            .containsAtLeastElementsIn(permissions)

        TestUtils.deleteAllStagedRemoteData()

        val record = createRecord()

        val recordToBeDeleted = createRecordToBeDeleted()

        val insertedRecords = TestUtils.insertRecords(record, recordToBeDeleted)
        this.record = insertedRecords[0]
    }

    @After
    fun tearDown() {
        TestUtils.deleteAllStagedRemoteData()
    }

    @Test
    fun dataAndAccess_showsEntries_deletesEntry() {
        context.launchMainActivity {
            scrollDownToAndClick(By.text("Data and access"))
            scrollDownTo(By.text(dataCategoryString))
            scrollDownToAndClick(By.text(dataTypeString))

            findText("No data")
            findDescAndClick("Previous day")
            waitForObjectNotFound(By.text("No data"), timeout = ofSeconds(1))
            scrollToEnd()

            findText(expectedRecordHeader)
            findText(expectedRecordTitle)
            expectedRecordSubtitle?.let { findText(it) }

            findText(expectedRecordToBeDeletedHeader)
            // Check that clicking on the entry does not open the details screen.
            findTextAndClick(expectedRecordToBeDeletedTitle)
            findText(expectedRecordTitle)
            findDesc("Previous day")

            findDescAndClick("Enter deletion")
            findTextAndClick(expectedRecordToBeDeletedTitle)
            findDescAndClick("Delete data")
            findTextAndClick("Delete")
            findObject(By.text("Done"), timeout = ofSeconds(3))
            findTextAndClick("Done")
            verifyTextNotFound(expectedRecordToBeDeletedTitle)
            findText(expectedRecordTitle)

            assertThat(readAllRecords(record::class.java)).containsExactly(record)
        }
    }

    @Test
    fun dataAndAccess_deleteData_deletesDataType_doesNotDeleteOtherDataTypes() {
        val sameCategoryRecord = createSameCategoryRecord()
        val anotherCategoryRecord = createAnotherCategoryRecord()
        TestUtils.insertRecords(listOfNotNull(sameCategoryRecord, anotherCategoryRecord))

        context.launchMainActivity {
            scrollDownToAndClick(By.text("Data and access"))
            scrollToEnd()

            findDescAndClick("Enter deletion")
            findTextAndClick(dataTypeString)
            findDescAndClick("Delete data")
            findTextAndClick("Delete")
            findObject(By.text("Done"), timeout = ofSeconds(3))
            findTextAndClick("Done")

            assertThat(readAllRecords(record::class.java)).isEmpty()
            assertThat(readAllRecords(anotherCategoryRecord::class.java)).hasSize(1)

            verifyTextNotFound(dataTypeString)
            findText(anotherCategoryString)

            sameCategoryRecord?.let {
                assertThat(readAllRecords(it::class.java)).hasSize(1)
                findText(dataCategoryString)
                findText(sameCategoryDataTypeString!!)
            }

            if (sameCategoryRecord == null) {
                verifyTextNotFound(dataCategoryString)
            }
        }
    }

    @Test
    fun seeAllRecentAccess_showsDataCategory() {
        context.launchMainActivity {
            findTextAndClick("See all recent access")
            findText("Write: ${dataCategoryString}")
        }
    }

    @Test
    fun appPermissions_allowAll_grantsPermissions() {
        permissions.forEach {
            revokePermissionViaPackageManager(context, APP_WITH_READ_WRITE_PERMISSIONS, it)
            assertPermissionDenied(it, APP_WITH_READ_WRITE_PERMISSIONS)
        }

        context.launchMainActivity {
            scrollDownToAndClick(By.text("App permissions"))
            findTextAndClick(APP_WITH_READ_WRITE_PERMISSIONS_LABEL)
            findTextAndClick("Fitness and wellness")
            findTextAndClick("Allow all")
        }

        permissions.forEach { assertPermissionGranted(it, APP_WITH_READ_WRITE_PERMISSIONS) }
    }

    @Test
    fun requestPermission_showsPermission() {
        permissions.forEach {
            revokePermissionViaPackageManager(context, APP_WITH_READ_WRITE_PERMISSIONS, it)
            assertPermissionDenied(it, APP_WITH_READ_WRITE_PERMISSIONS)
        }

        context.launchRequestPermissionActivity(
            packageName = APP_WITH_READ_WRITE_PERMISSIONS,
            permissions = permissions,
        ) {
            scrollDownTo(By.text(permissionString))
            findTextAndClick("Allow all")
            findTextAndClick("Allow")
        }

        permissions.forEach { assertPermissionGranted(it, APP_WITH_READ_WRITE_PERMISSIONS) }
    }
}
