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

package android.healthconnect.cts;

import static android.healthconnect.cts.utils.DataFactory.getTestRecords;
import static android.healthconnect.cts.utils.HealthConnectReceiver.callAndGetResponseWithShellPermissionIdentity;
import static android.healthconnect.cts.utils.HealthConnectReceiver.outcomeExecutor;
import static android.healthconnect.cts.utils.PermissionHelper.MANAGE_HEALTH_DATA;

import static com.android.compatibility.common.util.SystemUtil.getEventually;

import static com.google.common.truth.Truth.assertThat;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.health.connect.ApplicationInfoResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.datatypes.AppInfo;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.HealthConnectReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.platform.test.annotations.AppModeFull;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class GetApplicationInfoTest {

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private Context mContext;
    private HealthConnectManager mManager;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mManager = requireNonNull(mContext.getSystemService(HealthConnectManager.class));
    }

    /** TODO(b/257796081): Cleanup the database after each test. */
    @Test
    public void testEmptyApplicationInfo() throws InterruptedException {
        ApplicationInfoResponse response =
                callAndGetResponseWithShellPermissionIdentity(
                        mManager::getContributorApplicationsInfo, MANAGE_HEALTH_DATA);

        /** TODO(b/257796081): Test the response size after database clean up is implemented */
        // assertThat(response.getApplicationInfoList()).hasSize(0);
    }

    @Test
    public void testEmptyApplicationInfo_no_perm() throws InterruptedException {
        HealthConnectReceiver<ApplicationInfoResponse> receiver = new HealthConnectReceiver<>();
        mManager.getContributorApplicationsInfo(outcomeExecutor(), receiver);
        HealthConnectException healthConnectException = receiver.assertAndGetException();
        assertThat(healthConnectException.getErrorCode())
                .isEqualTo(HealthConnectException.ERROR_SECURITY);
    }

    @Test
    public void testGetApplicationInfo() throws Exception {
        TestUtils.insertRecords(getTestRecords());
        // App info table will be updated in the background, so might take some additional time.
        ApplicationInfoResponse eventualResponse =
                getEventually(
                        () -> {
                            ApplicationInfoResponse response =
                                    callAndGetResponseWithShellPermissionIdentity(
                                            mManager::getContributorApplicationsInfo,
                                            MANAGE_HEALTH_DATA);
                            assertThat(response.getApplicationInfoList()).hasSize(1);
                            return response;
                        });

        AppInfo appInfo = eventualResponse.getApplicationInfoList().get(0);
        ApplicationInfo applicationInfo = mContext.getApplicationInfo();
        assertThat(appInfo.getPackageName()).isEqualTo(applicationInfo.packageName);
        assertThat(appInfo.getName())
                .isEqualTo(
                        mContext.getPackageManager()
                                .getApplicationLabel(applicationInfo)
                                .toString());
        assertThat(appInfo.getIcon()).isNotNull();
    }
}
