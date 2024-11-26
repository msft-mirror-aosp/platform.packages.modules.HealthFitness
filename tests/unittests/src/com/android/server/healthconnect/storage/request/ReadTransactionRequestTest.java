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

import static android.healthconnect.cts.utils.DataFactory.getDataOrigin;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.content.Context;
import android.health.connect.HealthConnectManager;
import android.health.connect.PageTokenWrapper;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsRequestUsingIds;
import android.health.connect.datatypes.RecordTypeIdentifier;
import android.health.connect.datatypes.StepsRecord;
import android.os.Environment;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.injector.HealthConnectInjector;
import com.android.server.healthconnect.injector.HealthConnectInjectorImpl;
import com.android.server.healthconnect.permission.FirstGrantTimeManager;
import com.android.server.healthconnect.permission.HealthPermissionIntentAppsTracker;
import com.android.server.healthconnect.storage.datatypehelpers.HealthConnectDatabaseTestRule;
import com.android.server.healthconnect.storage.datatypehelpers.TransactionTestUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ReadTransactionRequestTest {
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

    private TransactionTestUtils mTransactionTestUtils;

    @Before
    public void setup() {
        Context context = ApplicationProvider.getApplicationContext();
        HealthConnectInjector healthConnectInjector =
                HealthConnectInjectorImpl.newBuilderForTest(context)
                        .setFirstGrantTimeManager(mock(FirstGrantTimeManager.class))
                        .setHealthPermissionIntentAppsTracker(
                                mock(HealthPermissionIntentAppsTracker.class))
                        .build();
        mTransactionTestUtils = new TransactionTestUtils(healthConnectInjector);
    }

    @Test
    public void createReadByFilterRequest_noPageToken_correctPaginationInfo() {
        PageTokenWrapper expectedToken = PageTokenWrapper.ofAscending(false);
        ReadRecordsRequestUsingFilters<StepsRecord> readRecordsRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setAscending(false)
                        .setPageSize(500)
                        .build();

        ReadTransactionRequest request =
                mTransactionTestUtils.getReadTransactionRequest(
                        readRecordsRequest.toReadRecordsRequestParcel());

        assertThat(request.getReadRequests()).hasSize(1);
        assertThat(request.getPageToken()).isEqualTo(expectedToken);
        assertThat(request.getPageSize()).isEqualTo(Optional.of(500));
    }

    @Test
    public void createReadByFilterRequest_hasPageToken_correctPaginationInfo() {
        PageTokenWrapper expectedToken = PageTokenWrapper.of(true, 9876, 2);
        ReadRecordsRequestUsingFilters<StepsRecord> readRecordsRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .setPageToken(expectedToken.encode())
                        .setPageSize(500)
                        .build();
        ReadTransactionRequest request =
                mTransactionTestUtils.getReadTransactionRequest(
                        readRecordsRequest.toReadRecordsRequestParcel());

        assertThat(request.getReadRequests()).hasSize(1);
        assertThat(request.getPageToken()).isEqualTo(expectedToken);
        assertThat(request.getPageSize()).isEqualTo(Optional.of(500));
    }

    @Test
    public void createReadByIdRequest_singleType_noPaginationInfo() {
        ReadRecordsRequestUsingIds<StepsRecord> readRecordsRequest =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                        .addClientRecordId("id")
                        .build();
        ReadTransactionRequest request =
                mTransactionTestUtils.getReadTransactionRequest(
                        readRecordsRequest.toReadRecordsRequestParcel());

        assertThat(request.getReadRequests()).hasSize(1);
        assertThat(request.getPageToken()).isNull();
        assertThat(request.getPageSize()).isEqualTo(Optional.empty());
    }

    @Test
    public void createReadByIdRequest_multipleType_noPaginationInfo() {
        List<UUID> randomUuids = ImmutableList.of(UUID.randomUUID());
        ReadTransactionRequest request =
                mTransactionTestUtils.getReadTransactionRequest(
                        ImmutableMap.of(
                                RecordTypeIdentifier.RECORD_TYPE_STEPS, randomUuids,
                                RecordTypeIdentifier.RECORD_TYPE_BLOOD_PRESSURE, randomUuids));

        assertThat(request.getReadRequests()).hasSize(2);
        assertThat(request.getPageToken()).isNull();
        assertThat(request.getPageSize()).isEqualTo(Optional.empty());
    }

    @Test
    public void getPackageName_readByRequestConstructor_returnsCorrectValue() {
        ReadRecordsRequestUsingIds<StepsRecord> readByIdRequest =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                        .addClientRecordId("id")
                        .build();
        ReadTransactionRequest readByIdTransactionRequest =
                mTransactionTestUtils.getReadTransactionRequest(
                        "read.by.id.package", readByIdRequest.toReadRecordsRequestParcel());
        assertThat(readByIdTransactionRequest.getPackageName()).isEqualTo("read.by.id.package");

        ReadRecordsRequestUsingFilters<StepsRecord> readByFilterRequest =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build();
        ReadTransactionRequest readTransactionRequest =
                mTransactionTestUtils.getReadTransactionRequest(
                        "read.by.filter.package", readByFilterRequest.toReadRecordsRequestParcel());
        assertThat(readTransactionRequest.getPackageName()).isEqualTo("read.by.filter.package");
    }

    @Test
    public void getPackageName_readByTypeToIdMapConstructor_returnsCorrectValue() {
        List<UUID> randomUuids = ImmutableList.of(UUID.randomUUID());
        ReadTransactionRequest request =
                mTransactionTestUtils.getReadTransactionRequest(
                        "test.package.name",
                        ImmutableMap.of(RecordTypeIdentifier.RECORD_TYPE_STEPS, randomUuids));
        assertThat(request.getPackageName()).isEqualTo("test.package.name");
    }

    @Test
    public void isReadingSelfData_readByIdRequest_returnsTrue() {
        ReadRecordsRequestUsingIds<StepsRecord> readByIdRequest =
                new ReadRecordsRequestUsingIds.Builder<>(StepsRecord.class)
                        .addClientRecordId("id")
                        .build();
        ReadTransactionRequest request =
                mTransactionTestUtils.getReadTransactionRequest(
                        readByIdRequest.toReadRecordsRequestParcel());

        // TODO(b/366149374): Consider the case of read by id from other apps
        assertThat(request.isReadingSelfData()).isTrue();
    }

    @Test
    public void isReadingSelfData_readByFilterRequest_filterSelfPackage_returnsTrue() {
        String testPackageName = "package.name";
        mTransactionTestUtils.insertApp(testPackageName);
        ReadRecordsRequestUsingFilters<StepsRecord> request =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .addDataOrigins(getDataOrigin(testPackageName))
                        .build();
        ReadTransactionRequest readTransactionRequest =
                mTransactionTestUtils.getReadTransactionRequest(
                        testPackageName, request.toReadRecordsRequestParcel());
        assertThat(readTransactionRequest.isReadingSelfData()).isTrue();
    }

    @Test
    public void isReadingSelfData_readByFilterRequest_notFilterSelfPackage_returnsFalse() {
        String testPackageName = "my.package";
        mTransactionTestUtils.insertApp(testPackageName);
        ReadRecordsRequestUsingFilters<StepsRecord> requestWithDataOriginFilter =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class)
                        .addDataOrigins(getDataOrigin(testPackageName))
                        .addDataOrigins(getDataOrigin("some.other.package"))
                        .build();
        ReadTransactionRequest readTransactionRequest =
                mTransactionTestUtils.getReadTransactionRequest(
                        testPackageName, requestWithDataOriginFilter.toReadRecordsRequestParcel());
        assertThat(readTransactionRequest.isReadingSelfData()).isFalse();

        ReadRecordsRequestUsingFilters<StepsRecord> requestWithoutDataOriginFilter =
                new ReadRecordsRequestUsingFilters.Builder<>(StepsRecord.class).build();
        ReadTransactionRequest readTransactionRequest2 =
                mTransactionTestUtils.getReadTransactionRequest(
                        testPackageName,
                        requestWithoutDataOriginFilter.toReadRecordsRequestParcel());
        assertThat(readTransactionRequest2.isReadingSelfData()).isFalse();
    }

    @Test
    public void isReadingSelfData_readByTypeToIdMapConstructor_returnsCorrectValue() {
        List<UUID> randomUuids = ImmutableList.of(UUID.randomUUID());
        ReadTransactionRequest requestNotReadingSelfData =
                mTransactionTestUtils.getReadTransactionRequest(
                        "package.name",
                        ImmutableMap.of(RecordTypeIdentifier.RECORD_TYPE_STEPS, randomUuids),
                        /* isReadingSelfData= */ false);
        assertThat(requestNotReadingSelfData.isReadingSelfData()).isFalse();

        ReadTransactionRequest requestReadingSelfData =
                mTransactionTestUtils.getReadTransactionRequest(
                        "package.name",
                        ImmutableMap.of(RecordTypeIdentifier.RECORD_TYPE_STEPS, randomUuids),
                        /* isReadingSelfData= */ true);
        assertThat(requestReadingSelfData.isReadingSelfData()).isTrue();
    }
}
