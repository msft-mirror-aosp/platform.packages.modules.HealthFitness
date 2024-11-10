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

package com.android.server.healthconnect.storage.request;

import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_BASAL_METABOLIC_RATE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;
import static android.healthconnect.cts.utils.DataFactory.getBasalMetabolicRateRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.health.connect.HealthConnectManager;
import android.health.connect.internal.datatypes.RecordInternal;
import android.os.Environment;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.StorageContext;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class UpsertTransactionRequestTest {
    @Rule(order = 1)
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(HealthConnectManager.class)
                    .mockStatic(Environment.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Rule(order = 2)
    public final HealthConnectDatabaseTestRule mHealthConnectDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    private StorageContext mContext;
    private AppInfoHelper mAppInfoHelper;
    private DeviceInfoHelper mDeviceInfoHelper;

    @Before
    public void setup() {
        mContext = mHealthConnectDatabaseTestRule.getDatabaseContext();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(mContext)
                        .setFirstGrantTimeManager(mock(FirstGrantTimeManager.class))
                        .setHealthPermissionIntentAppsTracker(
                                mock(HealthPermissionIntentAppsTracker.class))
                        .build();
        TransactionTestUtils transactionTestUtils =
                new TransactionTestUtils(mContext, healthConnectInjector);
        transactionTestUtils.insertApp("package.name");

        mAppInfoHelper = healthConnectInjector.getAppInfoHelper();
        mDeviceInfoHelper = healthConnectInjector.getDeviceInfoHelper();
    }

    @Test
    public void getPackageName_expectCorrectName() {
        UpsertTransactionRequest request1 =
                new UpsertTransactionRequest(
                        "package.name.1",
                        List.of(),
                        mDeviceInfoHelper,
                        mContext,
                        /* isInsertRequest= */ false,
                        /* extraPermsStateMap= */ Collections.emptyMap(),
                        mAppInfoHelper);
        assertThat(request1.getPackageName()).isEqualTo("package.name.1");

        UpsertTransactionRequest request2 =
                new UpsertTransactionRequest(
                        "package.name.2",
                        List.of(),
                        mDeviceInfoHelper,
                        mContext,
                        /* isInsertRequest= */ false,
                        /* useProvidedUuid= */ false,
                        /* skipPackageNameAndLogs= */ false,
                        mAppInfoHelper);
        assertThat(request2.getPackageName()).isEqualTo("package.name.2");
    }

    @Test
    public void getRecordTypeIds_expectCorrectRecordTypeIds() {
        List<RecordInternal<?>> records =
                List.of(
                        getStepsRecord().toRecordInternal(),
                        getBasalMetabolicRateRecord().toRecordInternal());
        UpsertTransactionRequest request =
                new UpsertTransactionRequest(
                        "package.name",
                        records,
                        mDeviceInfoHelper,
                        mContext,
                        /* isInsertRequest= */ false,
                        /* extraPermsStateMap= */ Collections.emptyMap(),
                        mAppInfoHelper);

        assertThat(request.getRecordTypeIds())
                .containsExactly(RECORD_TYPE_STEPS, RECORD_TYPE_BASAL_METABOLIC_RATE);
    }
}
