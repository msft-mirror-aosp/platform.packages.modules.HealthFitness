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

package android.healthconnect.cts.backgroundread;

import static android.health.connect.HealthConnectException.ERROR_SECURITY;
import static android.health.connect.HealthDataCategory.ACTIVITY;
import static android.health.connect.HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND;
import static android.healthconnect.cts.utils.DataFactory.NOW;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecord;
import static android.healthconnect.cts.utils.DataFactory.getStepsRecordWithEmptyMetaData;
import static android.healthconnect.cts.utils.TestUtils.aggregateStepsCount;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;
import static android.healthconnect.cts.utils.TestUtils.getRecordIds;
import static android.healthconnect.cts.utils.TestUtils.insertStepsRecordViaTestApp;
import static android.healthconnect.cts.utils.TestUtils.readStepsRecordsUsingFiltersViaTestApp;
import static android.healthconnect.cts.utils.TestUtils.readStepsRecordsUsingRecordIdsViaTestApp;
import static android.healthconnect.cts.utils.TestUtils.sendCommandToTestAppReceiver;
import static android.healthconnect.cts.utils.TestUtils.setupAggregation;
import static android.healthconnect.test.app.TestAppReceiver.ACTION_GET_CHANGE_LOGS;
import static android.healthconnect.test.app.TestAppReceiver.ACTION_GET_CHANGE_LOG_TOKEN;
import static android.healthconnect.test.app.TestAppReceiver.EXTRA_RECORD_COUNT;
import static android.healthconnect.test.app.TestAppReceiver.EXTRA_RECORD_IDS;
import static android.healthconnect.test.app.TestAppReceiver.EXTRA_RECORD_VALUE;
import static android.healthconnect.test.app.TestAppReceiver.EXTRA_TOKEN;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.content.pm.PackageManager;
import android.health.connect.HealthConnectManager;
import android.health.connect.InsertRecordsResponse;
import android.health.connect.datatypes.Record;
import android.health.connect.datatypes.StepsRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestReceiver;
import android.healthconnect.cts.utils.TestUtils;
import android.healthconnect.test.app.DefaultOutcomeReceiver;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class BackgroundReadTest {

    private static final String PKG_TEST_APP = "android.healthconnect.test.app";

    private Context mContext;
    private PackageManager mPackageManager;
    private HealthConnectManager mManager;

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mPackageManager = mContext.getPackageManager();
        mManager = requireNonNull(mContext.getSystemService(HealthConnectManager.class));

        deleteAllStagedRemoteData();
        TestReceiver.reset();
    }

    @After
    public void tearDown() throws Exception {
        deleteAllStagedRemoteData();
    }

    @Test
    public void testReadRecordsByFilters_inBackgroundWithoutPermission_cannotReadOtherAppsData()
            throws Exception {
        revokeBackgroundReadPermissionForTestApp();
        insertStepsRecordsDirectly(List.of(getStepsRecordWithEmptyMetaData()));

        // test app will try to read the step record inserted by this test
        readStepsRecordsUsingFiltersViaTestApp(mContext, List.of(mContext.getPackageName()));

        assertThat(TestReceiver.getResult()).isNull();
        assertThat(TestReceiver.getErrorCode()).isEqualTo(ERROR_SECURITY);
    }

    @Test
    public void testReadRecordsByFilters_inBackgroundWithoutPermission_canReadOwnData()
            throws Exception {
        revokeBackgroundReadPermissionForTestApp();
        String insertedId = insertStepsRecordViaTestApp(mContext, NOW, NOW.plus(1, MINUTES), 10);

        // test app will try to read the step record inserted by itself
        readStepsRecordsUsingFiltersViaTestApp(mContext, List.of(PKG_TEST_APP));

        Bundle result = TestReceiver.getResult();
        assertThat(result).isNotNull();
        assertThat(result.getInt(EXTRA_RECORD_COUNT)).isEqualTo(1);
        assertThat(result.getStringArrayList(EXTRA_RECORD_IDS)).containsExactly(insertedId);
    }

    @Test
    public void
            testReadRecordsByFilters_inBackgroundWithPermission_canReadBothOwnAndOtherAppsData() {
        grantBackgroundReadPermissionForTestApp();
        String idInsertedByThisTest =
                insertStepsRecordsDirectly(List.of(getStepsRecordWithEmptyMetaData())).get(0);
        String idInsertedByTestApp =
                insertStepsRecordViaTestApp(mContext, NOW, NOW.plus(1, MINUTES), 10);

        // test app will try to read the step record inserted by both this test and the test app
        readStepsRecordsUsingFiltersViaTestApp(
                mContext, List.of(mContext.getPackageName(), PKG_TEST_APP));

        Bundle result = TestReceiver.getResult();
        assertThat(result).isNotNull();
        assertThat(result.getInt(EXTRA_RECORD_COUNT)).isEqualTo(2);
        assertThat(result.getStringArrayList(EXTRA_RECORD_IDS))
                .containsExactly(idInsertedByThisTest, idInsertedByTestApp);
    }

    @Test
    public void testReadRecordsByIds_inBackgroundWithoutPermission_canReadOnlyOwnData()
            throws InterruptedException {
        revokeBackgroundReadPermissionForTestApp();
        String idInsertedByThisTest =
                insertStepsRecordsDirectly(List.of(getStepsRecordWithEmptyMetaData())).get(0);
        String idInsertedByTestApp =
                insertStepsRecordViaTestApp(mContext, NOW, NOW.plus(1, MINUTES), 10);

        // test app will try to read the step record inserted by both this test and the test app
        readStepsRecordsUsingRecordIdsViaTestApp(
                mContext, List.of(idInsertedByTestApp, idInsertedByThisTest));

        Bundle result = TestReceiver.getResult();
        assertThat(result).isNotNull();
        assertThat(result.getInt(EXTRA_RECORD_COUNT)).isEqualTo(1);
        assertThat(result.getStringArrayList(EXTRA_RECORD_IDS))
                .containsExactly(idInsertedByTestApp);
    }

    @Test
    public void testReadRecordsByIds_inBackgroundWithPermission_canReadBothOwnAndOtherAppsData() {
        grantBackgroundReadPermissionForTestApp();
        String idInsertedByThisTest =
                insertStepsRecordsDirectly(List.of(getStepsRecordWithEmptyMetaData())).get(0);
        String idInsertedByTestApp =
                insertStepsRecordViaTestApp(mContext, NOW, NOW.plus(1, MINUTES), 10);

        // test app will try to read the step record inserted by both this test and the test app
        readStepsRecordsUsingRecordIdsViaTestApp(
                mContext, List.of(idInsertedByTestApp, idInsertedByThisTest));

        Bundle result = TestReceiver.getResult();
        assertThat(result).isNotNull();
        assertThat(result.getInt(EXTRA_RECORD_COUNT)).isEqualTo(2);
        assertThat(result.getStringArrayList(EXTRA_RECORD_IDS))
                .containsExactly(idInsertedByThisTest, idInsertedByTestApp);
    }

    // TODO(b/309776578): once this bug b/309776578 is fixed, this test should be broken down into
    // two tests, one for aggregating own data which should succeed, and one for aggregating other
    // apps' data which should fail.
    @Test
    public void testAggregate_inBackgroundWithoutPermission_expectSecurityError() throws Exception {
        revokeBackgroundReadPermissionForTestApp();
        insertStepsRecordsDirectly(List.of(getStepsRecordWithEmptyMetaData())).get(0);
        insertStepsRecordViaTestApp(mContext, NOW, NOW.plus(1, MINUTES), 10);

        aggregateStepsCount(mContext, List.of(mContext.getPackageName(), PKG_TEST_APP));

        assertSecurityError();
    }

    @Test
    public void testAggregate_inBackgroundWithPermission_canAggregateBothOwnAndOtherAppsData()
            throws Exception {
        grantBackgroundReadPermissionForTestApp();
        long value1 = 10;
        long value2 = 5;
        StepsRecord stepsRecord1 = getStepsRecord(value1);
        insertStepsRecordsDirectly(List.of(stepsRecord1)).get(0);
        insertStepsRecordViaTestApp(
                mContext,
                stepsRecord1.getStartTime().minus(10, HOURS),
                stepsRecord1.getEndTime().minus(10, HOURS),
                value2);
        setupAggregation(List.of(mContext.getPackageName(), PKG_TEST_APP), ACTIVITY);

        aggregateStepsCount(mContext, List.of(mContext.getPackageName(), PKG_TEST_APP));

        assertSuccess();
        Bundle result = TestReceiver.getResult();
        assertThat(result).isNotNull();
        assertThat(result.getLong(EXTRA_RECORD_VALUE)).isEqualTo(value1 + value2);
    }

    @Test
    public void testGetChangeLogs_inBackgroundWithoutPermission_securityError() throws Exception {
        revokeBackgroundReadPermissionForTestApp();

        final Bundle extras = new Bundle();
        extras.putString(EXTRA_TOKEN, "token");
        sendCommandToTestAppReceiver(mContext, ACTION_GET_CHANGE_LOGS, extras);

        assertSecurityError();
    }

    @Test
    public void testGetChangeLogs_inBackgroundWithPermission_success() throws Exception {
        revokeBackgroundReadPermissionForTestApp();
        sendCommandToTestAppReceiver(mContext, ACTION_GET_CHANGE_LOG_TOKEN);
        final String token = requireNonNull(TestReceiver.getResult()).getString(EXTRA_TOKEN);
        grantBackgroundReadPermissionForTestApp();

        final Bundle extras = new Bundle();
        extras.putString(EXTRA_TOKEN, token);
        sendCommandToTestAppReceiver(mContext, ACTION_GET_CHANGE_LOGS, extras);

        assertSuccess();
    }

    private List<String> insertStepsRecordsDirectly(List<Record> recordsToInsert) {
        DefaultOutcomeReceiver<InsertRecordsResponse> outcomeReceiver =
                new DefaultOutcomeReceiver<>();

        mManager.insertRecords(
                recordsToInsert, Executors.newSingleThreadExecutor(), outcomeReceiver);

        if (outcomeReceiver.getError() != null) {
            throw new IllegalStateException("Insert steps record failed!");
        }
        return getRecordIds(outcomeReceiver.getResult().getRecords());
    }

    private void grantBackgroundReadPermissionForTestApp() {
        runWithShellPermissionIdentity(
                () ->
                        mPackageManager.grantRuntimePermission(
                                PKG_TEST_APP, READ_HEALTH_DATA_IN_BACKGROUND, mContext.getUser()));
    }

    private void revokeBackgroundReadPermissionForTestApp() throws InterruptedException {
        runWithShellPermissionIdentity(
                () ->
                        mPackageManager.revokeRuntimePermission(
                                PKG_TEST_APP, READ_HEALTH_DATA_IN_BACKGROUND, mContext.getUser()));

        // Wait a bit for the process to be killed
        Thread.sleep(500);
    }

    private void assertSecurityError() {
        assertThat(TestReceiver.getErrorCode()).isEqualTo(ERROR_SECURITY);
        assertThat(TestReceiver.getErrorMessage()).contains(READ_HEALTH_DATA_IN_BACKGROUND);
    }

    private Bundle assertSuccess() {
        assertThat(TestReceiver.getErrorCode()).isNull();
        assertThat(TestReceiver.getErrorMessage()).isNull();
        return TestReceiver.getResult();
    }
}
