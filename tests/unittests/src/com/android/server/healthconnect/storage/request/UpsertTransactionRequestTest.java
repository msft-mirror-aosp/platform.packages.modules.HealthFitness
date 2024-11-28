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

import android.content.Context;
import android.health.connect.HealthConnectManager;
import android.health.connect.internal.datatypes.RecordInternal;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.EnvironmentFixture;
import com.android.server.healthconnect.SQLiteDatabaseFixture;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.datatypehelpers.AppInfoHelper;
import com.android.server.healthconnect.storage.datatypehelpers.DeviceInfoHelper;
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
                    .addStaticMockFixtures(EnvironmentFixture::new, SQLiteDatabaseFixture::new)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private AppInfoHelper mAppInfoHelper;
    private DeviceInfoHelper mDeviceInfoHelper;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setFirstGrantTimeManager(mock(FirstGrantTimeManager.class))
                        .setHealthPermissionIntentAppsTracker(
                                mock(HealthPermissionIntentAppsTracker.class))
                        .build();
        mAppInfoHelper = healthConnectInjector.getAppInfoHelper();
        mDeviceInfoHelper = healthConnectInjector.getDeviceInfoHelper();

        TransactionTestUtils transactionTestUtils = new TransactionTestUtils(healthConnectInjector);
        transactionTestUtils.insertApp("package.name");
    }

    @Test
    public void getPackageName_expectCorrectName() {
        UpsertTransactionRequest request1 =
                UpsertTransactionRequest.createForInsert(
                        "package.name.1",
                        List.of(),
                        mDeviceInfoHelper,
                        mAppInfoHelper,
                        /* extraPermsStateMap= */ Collections.emptyMap());
        assertThat(request1.getPackageName()).isEqualTo("package.name.1");

        UpsertTransactionRequest request2 =
                UpsertTransactionRequest.createForUpdate(
                        "package.name.2",
                        List.of(),
                        mDeviceInfoHelper,
                        mAppInfoHelper,
                        /* extraPermsStateMap= */ Collections.emptyMap());
        assertThat(request2.getPackageName()).isEqualTo("package.name.2");

        UpsertTransactionRequest request3 =
                UpsertTransactionRequest.createForRestore(
                        List.of(), mDeviceInfoHelper, mAppInfoHelper);
        assertThat(request3.getPackageName()).isNull();
    }

    @Test
    public void getRecordTypeIds_expectCorrectRecordTypeIds() {
        List<RecordInternal<?>> records =
                List.of(
                        getStepsRecord().toRecordInternal(),
                        getBasalMetabolicRateRecord().toRecordInternal());
        UpsertTransactionRequest request =
                UpsertTransactionRequest.createForUpdate(
                        "package.name",
                        records,
                        mDeviceInfoHelper,
                        mAppInfoHelper,
                        /* extraPermsStateMap= */ Collections.emptyMap());

        assertThat(request.getRecordTypeIds())
                .containsExactly(RECORD_TYPE_STEPS, RECORD_TYPE_BASAL_METABOLIC_RATE);
    }
}
