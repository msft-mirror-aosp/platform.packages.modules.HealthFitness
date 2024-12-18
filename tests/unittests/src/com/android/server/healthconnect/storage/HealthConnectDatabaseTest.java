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

package com.android.server.healthconnect.storage;

import static com.android.healthfitness.flags.DatabaseVersions.LAST_ROLLED_OUT_DB_VERSION;
import static com.android.server.healthconnect.storage.DatabaseTestUtils.NUM_OF_TABLES;
import static com.android.server.healthconnect.storage.DatabaseTestUtils.assertNumberOfTables;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.healthfitness.flags.Flags;

import com.google.common.base.Preconditions;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

public class HealthConnectDatabaseTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Mock Context mContext;
    private HealthConnectDatabase mHealthConnectDatabase;
    private SQLiteDatabase mSQLiteDatabase;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getDatabasePath(anyString()))
                .thenReturn(
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getDatabasePath("mock"));
        mHealthConnectDatabase = new HealthConnectDatabase(mContext);

        // Make sure there is nothing there already.
        File databasePath = mHealthConnectDatabase.getDatabasePath();
        if (databasePath.exists()) {
            Preconditions.checkState(databasePath.delete());
        }
        mSQLiteDatabase = mHealthConnectDatabase.getWritableDatabase();
    }

    @Test
    @DisableFlags(Flags.FLAG_DEVELOPMENT_DATABASE)
    public void onCreate_dbWithLatestSchemaCreated() {
        assertThat(mHealthConnectDatabase).isNotNull();
        assertThat(mSQLiteDatabase).isNotNull();
        assertNumberOfTables(mSQLiteDatabase, NUM_OF_TABLES);
        assertThat(mSQLiteDatabase.getVersion()).isEqualTo(LAST_ROLLED_OUT_DB_VERSION);
    }
}
