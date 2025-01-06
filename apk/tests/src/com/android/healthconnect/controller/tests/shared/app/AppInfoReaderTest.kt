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
 *
 *
 */
package com.android.healthconnect.controller.tests.shared.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.PackageManager.NameNotFoundException
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppMetadata
import com.android.healthconnect.controller.shared.app.IGetContributorAppInfoUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub

private const val PACKAGE_NAME = "com.example.test"
private const val STORED_LABEL = "Stored label"
private const val PACKAGE_MANAGER_LABEL = "PackageManager label"

class AppInfoReaderTest {

    private val mockPackageManager = mock<PackageManager>()
    private val mockContext =
        mock<Context>() { on { getPackageManager() } doReturn mockPackageManager }
    private val getContributorAppInfoUseCase = FakeGetContributorAppInfoUseCase()
    private val appInfoReader = AppInfoReader(mockContext, getContributorAppInfoUseCase)

    @Test
    fun uninstalledApp_returnsMetadataFromStorage() = runBlocking {
        mockPackageManager.stub {
            on { getApplicationInfo(eq(PACKAGE_NAME), any<ApplicationInfoFlags>()) } doThrow
                    NameNotFoundException()
            on { getApplicationIcon(PACKAGE_NAME) } doThrow NameNotFoundException()
        }

        val appMetadata = appInfoReader.getAppMetadata(PACKAGE_NAME)
        assertThat(appMetadata.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(appMetadata.appName).isEqualTo(STORED_LABEL)
    }

    @Test
    fun enabledApp_returnsMetadataFromPackageManager() = runBlocking {
        val applicationInfo =
            ApplicationInfo().apply() {
                packageName = PACKAGE_NAME
                enabled = true
            }
        mockPackageManager.stub {
            on { getApplicationInfo(eq(PACKAGE_NAME), any<ApplicationInfoFlags>()) } doReturn
                    applicationInfo
            on { getApplicationLabel(applicationInfo) } doReturn PACKAGE_MANAGER_LABEL
        }

        val appMetadata = appInfoReader.getAppMetadata(PACKAGE_NAME)
        assertThat(appMetadata.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(appMetadata.appName).isEqualTo(PACKAGE_MANAGER_LABEL)
    }

    @Test
    fun disabledApp_returnsMetadataFromPackageManager() = runBlocking {
        val applicationInfo =
            ApplicationInfo().apply() {
                packageName = PACKAGE_NAME
                enabled = false
            }
        mockPackageManager.stub {
            on { getApplicationInfo(eq(PACKAGE_NAME), any<ApplicationInfoFlags>()) } doReturn
                    applicationInfo
            on { getApplicationLabel(applicationInfo) } doReturn PACKAGE_MANAGER_LABEL
        }

        val appMetadata = appInfoReader.getAppMetadata(PACKAGE_NAME)
        assertThat(appMetadata.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(appMetadata.appName).isEqualTo(PACKAGE_MANAGER_LABEL)
    }
}

private class FakeGetContributorAppInfoUseCase : IGetContributorAppInfoUseCase {
    override suspend fun invoke(): Map<String, AppMetadata> {
        return mapOf(
            PACKAGE_NAME to
                    AppMetadata(packageName = PACKAGE_NAME, appName = STORED_LABEL, icon = null)
        )
    }
}
