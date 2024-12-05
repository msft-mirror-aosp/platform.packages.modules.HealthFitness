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

package com.android.server.healthconnect.migration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.health.connect.HealthConnectDataState;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.migration.MigrationStateManager.StateChangedListener;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.MigrationEntityHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class MigrationCleanerTest {

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(TransactionManager.class).build();

    @Mock private TransactionManager mTransactionManager;
    @Mock private MigrationStateManager mMigrationStateManager;

    // TODO(b/373322447): Remove the mock FirstGrantTimeManager
    @Mock private FirstGrantTimeManager mFirstGrantTimeManager;
    // TODO(b/373322447): Remove the mock HealthPermissionIntentAppsTracker
    @Mock private HealthPermissionIntentAppsTracker mPermissionIntentAppsTracker;

    private MigrationCleaner mCleaner;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        // needed for now as some classes call it directly and not via constructor.
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(
                                InstrumentationRegistry.getInstrumentation().getContext())
                        .setTransactionManager(mTransactionManager)
                        .setFirstGrantTimeManager(mFirstGrantTimeManager)
                        .setHealthPermissionIntentAppsTracker(mPermissionIntentAppsTracker)
                        .setMigrationStateManager(mMigrationStateManager)
                        .build();

        mCleaner =
                new MigrationCleaner(
                        mTransactionManager,
                        healthConnectInjector.getPriorityMigrationHelper(),
                        healthConnectInjector.getMigrationEntityHelper());
    }

    @Test
    public void testAttachTo_addsListenerToMigrationStateManager() {
        mCleaner.attachTo(mMigrationStateManager);

        verify(mMigrationStateManager).addStateChangedListener(any());
    }

    @Test
    public void testStateChanged_complete_tablesCleared() {
        mCleaner.attachTo(mMigrationStateManager);
        final ArgumentCaptor<StateChangedListener> captor =
                ArgumentCaptor.forClass(StateChangedListener.class);
        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_COMPLETE);

        verifyMigrationEntityTableCleared();
        verifyPriorityMigrationTableCleared();
    }

    @Test
    public void testStateChanged_notComplete_noTablesCleared() {
        mCleaner.attachTo(mMigrationStateManager);
        final ArgumentCaptor<StateChangedListener> captor =
                ArgumentCaptor.forClass(StateChangedListener.class);
        verify(mMigrationStateManager).addStateChangedListener(captor.capture());
        captor.getValue().onChanged(HealthConnectDataState.MIGRATION_STATE_IN_PROGRESS);

        verify(mTransactionManager, never()).delete(any());
    }

    private void verifyMigrationEntityTableCleared() {
        //noinspection ConstantConditions
        verify(mTransactionManager)
                .delete(
                        argThat(
                                request ->
                                        request.getTableName()
                                                .equals(MigrationEntityHelper.TABLE_NAME)));
    }

    private void verifyPriorityMigrationTableCleared() {
        //noinspection ConstantConditions
        verify(mTransactionManager)
                .delete(
                        argThat(
                                request ->
                                        request.getTableName()
                                                .equals(
                                                        PriorityMigrationHelper
                                                                .PRE_MIGRATION_TABLE_NAME)));
    }
}
