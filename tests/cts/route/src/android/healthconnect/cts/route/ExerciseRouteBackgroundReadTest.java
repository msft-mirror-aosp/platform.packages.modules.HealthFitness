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

package android.healthconnect.cts.route;

import static android.healthconnect.cts.route.ExerciseRouteTestHelper.ROUTES_READER_WRITER_APP;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.ROUTE_WRITER_APP;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.assertCorrectHealthPermissions;
import static android.healthconnect.cts.route.ExerciseRouteTestHelper.getExerciseSessionWithRoute;
import static android.healthconnect.cts.utils.DataFactory.getEmptyMetadata;
import static android.healthconnect.cts.utils.TestUtils.connectAppsWithGrantedPermissions;
import static android.healthconnect.cts.utils.TestUtils.deleteAllStagedRemoteData;

import static com.android.compatibility.common.util.SystemUtil.eventually;

import static com.google.common.truth.Truth.assertThat;

import static java.util.Objects.requireNonNull;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.changelog.ChangeLogTokenRequest;
import android.health.connect.changelog.ChangeLogsRequest;
import android.health.connect.changelog.ChangeLogsResponse;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.healthconnect.cts.lib.TestAppProxy;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExerciseRouteBackgroundReadTest {
    private static final TestAppProxy ROUTES_READER_WRITER_BACKGROUND_APP =
            TestAppProxy.forPackageNameInBackground(ROUTES_READER_WRITER_APP.getPackageName());

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHealthConnectFullySupported,
                    "Tests should run on supported hardware only.");

    @Before
    public void setUp() throws Exception {
        // Ensure that App Ops considers the test app to be in the background. This may take a few
        // seconds if another test has recently launched it in the foreground.
        Context context = ApplicationProvider.getApplicationContext();
        PackageManager packageManager = context.getPackageManager();
        AppOpsManager appOpsManager = requireNonNull(context.getSystemService(AppOpsManager.class));
        String packageName = ROUTES_READER_WRITER_BACKGROUND_APP.getPackageName();
        int uid = packageManager.getPackageUid(packageName, /* flags= */ 0);
        eventually(
                () ->
                        assertThat(
                                        appOpsManager.unsafeCheckOp(
                                                AppOpsManager.OPSTR_FINE_LOCATION,
                                                uid,
                                                packageName))
                                .isEqualTo(AppOpsManager.MODE_IGNORED));

        assertCorrectHealthPermissions();
        connectAppsWithGrantedPermissions();

        deleteAllStagedRemoteData();
    }

    @After
    public void tearDown() throws InterruptedException {
        deleteAllStagedRemoteData();
    }

    @Test
    public void readRecords_usingFilters_cannotAccessOtherAppRoute() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        ROUTE_WRITER_APP.insertRecords(sessionWithRoute);

        List<ExerciseSessionRecord> records =
                ROUTES_READER_WRITER_BACKGROUND_APP.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void readRecords_usingFilters_canAccessOwnRoute() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        ROUTES_READER_WRITER_BACKGROUND_APP.insertRecords(sessionWithRoute);

        List<ExerciseSessionRecord> records =
                ROUTES_READER_WRITER_BACKGROUND_APP.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void readRecords_usingFilters_mixedOwnAndOtherAppSession() throws Exception {
        ExerciseSessionRecord otherAppSession = getExerciseSessionWithRoute(getEmptyMetadata());
        String otherAppSessionId = ROUTE_WRITER_APP.insertRecords(otherAppSession).get(0);
        ExerciseSessionRecord ownSession = getExerciseSessionWithRoute(getEmptyMetadata());
        String ownSessionId =
                ROUTES_READER_WRITER_BACKGROUND_APP.insertRecords(List.of(ownSession)).get(0);

        List<ExerciseSessionRecord> records =
                ROUTES_READER_WRITER_BACKGROUND_APP.readRecords(
                        new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                                .build());

        Map<String, ExerciseSessionRecord> idToRecordMap =
                records.stream()
                        .collect(
                                Collectors.toMap(
                                        record -> record.getMetadata().getId(),
                                        Function.identity()));
        assertThat(records).hasSize(2);
        assertThat(idToRecordMap.get(otherAppSessionId).hasRoute()).isTrue();
        assertThat(idToRecordMap.get(otherAppSessionId).getRoute()).isNull();
        assertThat(idToRecordMap.get(ownSessionId).hasRoute()).isTrue();
        assertThat(idToRecordMap.get(ownSessionId).getRoute()).isEqualTo(ownSession.getRoute());
    }

    @Test
    public void readRecords_usingIds_cannotAccessOtherAppRoute() throws Exception {
        ExerciseSessionRecord otherAppSession = getExerciseSessionWithRoute(getEmptyMetadata());
        String sessionId = ROUTE_WRITER_APP.insertRecords(otherAppSession).get(0);

        List<ExerciseSessionRecord> records =
                ROUTES_READER_WRITER_BACKGROUND_APP.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(ExerciseSessionRecord.class)
                                .addId(sessionId)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void readRecords_usingIds_canAccessOwnRoute() throws Exception {
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        String sessionId =
                ROUTES_READER_WRITER_BACKGROUND_APP.insertRecords(sessionWithRoute).get(0);

        List<ExerciseSessionRecord> records =
                ROUTES_READER_WRITER_BACKGROUND_APP.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(ExerciseSessionRecord.class)
                                .addId(sessionId)
                                .build());

        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void readRecords_usingIds_mixedOwnAndOtherAppSession() throws Exception {
        ExerciseSessionRecord otherAppSession = getExerciseSessionWithRoute(getEmptyMetadata());
        String otherAppSessionId = ROUTE_WRITER_APP.insertRecords(otherAppSession).get(0);
        ExerciseSessionRecord ownSession = getExerciseSessionWithRoute(getEmptyMetadata());
        String ownSessionId = ROUTES_READER_WRITER_BACKGROUND_APP.insertRecords(ownSession).get(0);

        List<ExerciseSessionRecord> records =
                ROUTES_READER_WRITER_BACKGROUND_APP.readRecords(
                        new ReadRecordsRequestUsingIds.Builder<>(ExerciseSessionRecord.class)
                                .addId(otherAppSessionId)
                                .addId(ownSessionId)
                                .build());

        Map<String, ExerciseSessionRecord> idToRecordMap =
                records.stream()
                        .collect(
                                Collectors.toMap(
                                        record -> record.getMetadata().getId(),
                                        Function.identity()));
        assertThat(records).hasSize(2);
        assertThat(idToRecordMap.get(otherAppSessionId).hasRoute()).isTrue();
        assertThat(idToRecordMap.get(otherAppSessionId).getRoute()).isNull();
        assertThat(idToRecordMap.get(ownSessionId).hasRoute()).isTrue();
        assertThat(idToRecordMap.get(ownSessionId).getRoute()).isEqualTo(ownSession.getRoute());
    }

    @Test
    public void getChangelogs_cannotAccessOtherAppRoute() throws Exception {
        String token =
                ROUTES_READER_WRITER_BACKGROUND_APP.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(ExerciseSessionRecord.class)
                                .build());
        ExerciseSessionRecord otherAppSession = getExerciseSessionWithRoute(getEmptyMetadata());
        ROUTE_WRITER_APP.insertRecords(List.of(otherAppSession));

        ChangeLogsResponse response =
                ROUTES_READER_WRITER_BACKGROUND_APP.getChangeLogs(
                        new ChangeLogsRequest.Builder(token).build());

        List<ExerciseSessionRecord> records =
                response.getUpsertedRecords().stream()
                        .map(ExerciseSessionRecord.class::cast)
                        .toList();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isNull();
    }

    @Test
    public void getChangelogs_canAccessOwnRoute() throws Exception {
        String token =
                ROUTES_READER_WRITER_BACKGROUND_APP.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(ExerciseSessionRecord.class)
                                .build());
        ExerciseSessionRecord sessionWithRoute = getExerciseSessionWithRoute(getEmptyMetadata());
        ROUTES_READER_WRITER_BACKGROUND_APP.insertRecords(sessionWithRoute);

        ChangeLogsResponse response =
                ROUTES_READER_WRITER_BACKGROUND_APP.getChangeLogs(
                        new ChangeLogsRequest.Builder(token).build());

        List<ExerciseSessionRecord> records =
                response.getUpsertedRecords().stream()
                        .map(ExerciseSessionRecord.class::cast)
                        .toList();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).hasRoute()).isTrue();
        assertThat(records.get(0).getRoute()).isEqualTo(sessionWithRoute.getRoute());
    }

    @Test
    public void getChangelogs_mixedOwnAndOtherAppSession() throws Exception {
        String token =
                ROUTES_READER_WRITER_BACKGROUND_APP.getChangeLogToken(
                        new ChangeLogTokenRequest.Builder()
                                .addRecordType(ExerciseSessionRecord.class)
                                .build());
        ExerciseSessionRecord otherAppSession = getExerciseSessionWithRoute(getEmptyMetadata());
        String otherAppSessionId = ROUTE_WRITER_APP.insertRecords(otherAppSession).get(0);
        ExerciseSessionRecord ownSession = getExerciseSessionWithRoute(getEmptyMetadata());
        String ownSessionId = ROUTES_READER_WRITER_BACKGROUND_APP.insertRecords(ownSession).get(0);

        ChangeLogsResponse response =
                ROUTES_READER_WRITER_BACKGROUND_APP.getChangeLogs(
                        new ChangeLogsRequest.Builder(token).build());

        Map<String, ExerciseSessionRecord> idToRecordMap =
                response.getUpsertedRecords().stream()
                        .map(ExerciseSessionRecord.class::cast)
                        .collect(
                                Collectors.toMap(
                                        record -> record.getMetadata().getId(),
                                        Function.identity()));
        assertThat(response.getUpsertedRecords()).hasSize(2);
        assertThat(idToRecordMap.get(otherAppSessionId).hasRoute()).isTrue();
        assertThat(idToRecordMap.get(otherAppSessionId).getRoute()).isNull();
        assertThat(idToRecordMap.get(ownSessionId).hasRoute()).isTrue();
        assertThat(idToRecordMap.get(ownSessionId).getRoute()).isEqualTo(ownSession.getRoute());
    }
}
