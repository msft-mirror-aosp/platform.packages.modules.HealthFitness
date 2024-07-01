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

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.TransactionManager;

import org.junit.rules.ExternalResource;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.File;

/** A test rule that deals with ground work of setting up a mock Health Connect database. */
public class HealthConnectDatabaseTestRule extends ExternalResource {
    private static final String TAG = "HealthConnectDatabaseTestRule";
    private MockitoSession mStaticMockSession;
    private HealthConnectUserContext mContext;
    private TransactionManager mTransactionManager;

    @Override
    public void before() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Environment.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        mContext =
                new HealthConnectUserContext(
                        InstrumentationRegistry.getInstrumentation().getContext(), TEST_USER);
        File mockDataDirectory = mContext.getDir("mock_data", Context.MODE_PRIVATE);
        when(Environment.getDataDirectory()).thenReturn(mockDataDirectory);
        TransactionManager.cleanUpForTest();
        mTransactionManager = TransactionManager.getInstance(mContext);
    }

    @Override
    public void after() {
        try {
            DatabaseHelper.clearAllData(mTransactionManager);
            TransactionManager.cleanUpForTest();
        } finally {
            mStaticMockSession.finishMocking();
        }
    }

    public HealthConnectUserContext getUserContext() {
        return mContext;
    }

    public TransactionManager getTransactionManager() {
        return mTransactionManager;
    }
}
