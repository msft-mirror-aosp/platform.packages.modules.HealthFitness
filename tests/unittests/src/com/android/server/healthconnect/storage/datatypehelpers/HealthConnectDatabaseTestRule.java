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

package com.android.server.healthconnect.storage.datatypehelpers;

import static com.android.server.healthconnect.TestUtils.TEST_USER;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Environment;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.healthconnect.HealthConnectDeviceConfigManager;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.TransactionManager;

import org.junit.rules.ExternalResource;

import java.io.File;

/**
 * A test rule that deals with ground work of setting up a mock Health Connect database. To use, add
 * the following to your test class:
 *
 * <p><code>
 * {@literal @} Rule (order = 1)
 * public final ExtendedMockitoRule mExtendedMockitoRule = new ExtendedMockitoRule.Builder(this)
 *     .mockStatic(HealthConnectManager.class)
 *     .mockStatic(Environment.class)
 *     .setStrictness(Strictness.LENIENT)
 *     .build();
 *
 * {@literal @} Rule (order = 2)
 * public final HealthConnectDatabaseTestRule mDatabaseTestRule =
 *     new HealthConnectDatabaseTestRule();
 * </code>
 *
 * <p>Mocking is done in the test class rather than here to avoid interferences for Mockito session
 * handling when multiple test rules are used. It avoids starting multiple sessions in parallel.
 */
public class HealthConnectDatabaseTestRule extends ExternalResource {
    private HealthConnectUserContext mContext;
    private TransactionManager mTransactionManager;

    // Mock Environment using ExtendedMockitoRule in the test using this rule.
    public HealthConnectDatabaseTestRule() {}

    @Override
    public void before() {
        mContext =
                new HealthConnectUserContext(
                        InstrumentationRegistry.getInstrumentation().getContext(), TEST_USER);
        File mockDataDirectory = mContext.getDir("mock_data", Context.MODE_PRIVATE);
        when(Environment.getDataDirectory()).thenReturn(mockDataDirectory);
        TransactionManager.cleanUpForTest();
        mTransactionManager = TransactionManager.initializeInstance(mContext);
        HealthConnectDeviceConfigManager.initializeInstance(mContext);
    }

    @Override
    public void after() {
        DatabaseHelper.clearAllData(mTransactionManager);
        TransactionManager.cleanUpForTest();
    }

    public HealthConnectUserContext getUserContext() {
        return mContext;
    }

    public TransactionManager getTransactionManager() {
        return mTransactionManager;
    }
}
