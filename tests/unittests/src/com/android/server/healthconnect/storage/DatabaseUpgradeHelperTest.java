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

package com.android.server.healthconnect.storage;

import static com.android.server.healthconnect.storage.DatabaseUpgradeHelper.DB_VERSION_UUID_BLOB;
import static com.android.server.healthconnect.storage.DatabaseUpgradeHelper.getDatabaseVersion;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DatabaseUpgradeHelperTest {
    @Mock Context mContext;
    private SQLiteDatabase mSQLiteDatabase;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mContext.getDatabasePath(anyString()))
                .thenReturn(
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getDatabasePath("mock"));
        mSQLiteDatabase = new HealthConnectDatabase(mContext).getWritableDatabase();
    }

    /*
     * If you find that this test is failing, it means that your database upgrade cannot be applied
     * multiple times. Making a database upgrade idempotent can often be easily achieved by
     * specifying e.g. 'IF NOT EXISTS'.
     */
    // TODO(b/338031465): Improve testing, check that schema indeed match.
    @Test
    public void onUpgradeCalledMultipleTimes_eachOneIsIdempotent() {
        // The DB_VERSION_UUID_BLOB upgrade is a special case, and is not idempotent.
        // We are testing from the version above that.
        DatabaseUpgradeHelper.onUpgrade(
                mSQLiteDatabase, DB_VERSION_UUID_BLOB, getDatabaseVersion());
        DatabaseUpgradeHelper.onUpgrade(
                mSQLiteDatabase, DB_VERSION_UUID_BLOB, getDatabaseVersion());
    }
}
