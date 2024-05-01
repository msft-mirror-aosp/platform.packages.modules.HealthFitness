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

package com.android.server.healthconnect.exportimport;

import static com.android.server.healthconnect.exportimport.ExportManager.EXPORT_DATABASE_DIR_NAME;
import static com.android.server.healthconnect.exportimport.ExportManager.EXPORT_DATABASE_FILE_NAME;

import android.database.Cursor;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.healthconnect.HealthConnectUserContext;
import com.android.server.healthconnect.storage.HealthConnectDatabase;
import com.android.server.healthconnect.storage.TransactionManager;
import com.android.server.healthconnect.storage.datatypehelpers.DatabaseHelper;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExportManagerTest {
    private static final String TEST_PACKAGE_NAME = "package.name";

    @Rule
    public final HealthConnectDatabaseTestRule mDatabaseTestRule =
            new HealthConnectDatabaseTestRule();

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    private HealthConnectUserContext mContext;

    private TransactionManager mTransactionManager;
    private TransactionTestUtils mTransactionTestUtils;
    private ExportManager mExportManager;

    private static final String TAG = "HealthConnectExportImport";

    @Before
    public void setUp() throws Exception {
        mContext = mDatabaseTestRule.getUserContext();
        mTransactionManager = mDatabaseTestRule.getTransactionManager();
        mTransactionTestUtils = new TransactionTestUtils(mContext, mTransactionManager);
        mTransactionTestUtils.insertApp(TEST_PACKAGE_NAME);
        mExportManager = new ExportManager(mContext);
    }

    @After
    public void tearDown() {
        DatabaseHelper.clearAllData(mTransactionManager);
    }

    @Test
    public void exportLocally_deletesAccessLogsTableContent() {
        mTransactionTestUtils.insertAccessLog();
        mTransactionTestUtils.insertAccessLog();
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "access_logs_table", 2);

        mExportManager.exportLocally(mContext.getUser());

        HealthConnectDatabase exportHealthConnectDatabase =
                new HealthConnectDatabase(
                        DatabaseContext.create(
                                mContext, EXPORT_DATABASE_DIR_NAME, mContext.getUser()),
                        EXPORT_DATABASE_FILE_NAME);
        assertTableSize(exportHealthConnectDatabase, "access_logs_table", 0);
    }

    @Test
    public void exportLocally_deletesChangeLogsTableContent() {
        mTransactionTestUtils.insertChangeLog();
        mTransactionTestUtils.insertChangeLog();
        HealthConnectDatabase originalDatabase =
                new HealthConnectDatabase(mContext, "healthconnect.db");
        assertTableSize(originalDatabase, "change_logs_table", 2);

        mExportManager.exportLocally(mContext.getUser());

        HealthConnectDatabase exportHealthConnectDatabase =
                new HealthConnectDatabase(
                        DatabaseContext.create(
                                mContext, EXPORT_DATABASE_DIR_NAME, mContext.getUser()),
                        EXPORT_DATABASE_FILE_NAME);
        assertTableSize(exportHealthConnectDatabase, "change_logs_table", 0);
    }

    private void assertTableSize(HealthConnectDatabase database, String tableName, int tableRows) {
        Cursor cursor =
                database.getWritableDatabase()
                        .rawQuery("SELECT count(*) FROM " + tableName + ";", null);
        cursor.moveToNext();
        Truth.assertThat(cursor.getInt(0)).isEqualTo(tableRows);
    }
}
