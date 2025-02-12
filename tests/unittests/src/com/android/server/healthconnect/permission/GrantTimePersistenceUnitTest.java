/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.healthconnect.permission;

import static com.android.server.healthconnect.permission.FirstGrantTimeDatastore.DATA_TYPE_CURRENT;
import static com.android.server.healthconnect.permission.FirstGrantTimeDatastore.DATA_TYPE_STAGED;

import static com.google.common.truth.Truth.assertThat;

import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.healthconnect.testing.fixtures.EnvironmentFixture;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.quality.Strictness;

import java.io.File;
import java.time.Instant;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class GrantTimePersistenceUnitTest {
    private static final UserGrantTimeState DEFAULT_STATE =
            new UserGrantTimeState(
                    Map.of("package1", Instant.ofEpochSecond((long) 1e8)),
                    Map.of("shared_user1", Instant.ofEpochSecond((long) 1e7)),
                    1);

    private static final UserGrantTimeState SHARED_USERS_STATE =
            new UserGrantTimeState(
                    new ArrayMap<>(),
                    Map.of(
                            "shared_user1",
                            Instant.ofEpochSecond((long) 1e7),
                            "shared_user2",
                            Instant.ofEpochSecond((long) 1e5)),
                    2);

    private static final UserGrantTimeState PACKAGES_STATE =
            new UserGrantTimeState(
                    Map.of(
                            "package1",
                            Instant.ofEpochSecond((long) 1e7),
                            "package2",
                            Instant.ofEpochSecond((long) 1e5)),
                    new ArrayMap<>(),
                    2);

    private static final UserGrantTimeState EMPTY_STATE =
            new UserGrantTimeState(new ArrayMap<>(), new ArrayMap<>(), 3);

    private final EnvironmentFixture mEnvironmentFixture = new EnvironmentFixture();

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this)
                    .setStrictness(Strictness.LENIENT)
                    .addStaticMockFixtures(() -> mEnvironmentFixture)
                    .build();

    private final UserHandle mUser = UserHandle.of(UserHandle.myUserId());

    @Test
    public void testWriteReadData_packageAndSharedUserState_restoredCorrectly() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        datastore.writeForUser(DEFAULT_STATE, mUser, DATA_TYPE_CURRENT);
        UserGrantTimeState restoredState = datastore.readForUser(mUser, DATA_TYPE_CURRENT);
        assertRestoredStateIsCorrect(restoredState, DEFAULT_STATE);
    }

    @Test
    public void testWriteReadData_multipleSharedUserState_restoredCorrectly() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        datastore.writeForUser(SHARED_USERS_STATE, mUser, DATA_TYPE_CURRENT);
        UserGrantTimeState restoredState = datastore.readForUser(mUser, DATA_TYPE_CURRENT);
        assertRestoredStateIsCorrect(restoredState, SHARED_USERS_STATE);
    }

    @Test
    public void testWriteReadData_multiplePackagesState_restoredCorrectly() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        datastore.writeForUser(PACKAGES_STATE, mUser, DATA_TYPE_CURRENT);
        UserGrantTimeState restoredState = datastore.readForUser(mUser, DATA_TYPE_CURRENT);
        assertRestoredStateIsCorrect(restoredState, PACKAGES_STATE);
    }

    @Test
    public void testWriteReadData_emptyState_restoredCorrectly() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        datastore.writeForUser(EMPTY_STATE, mUser, DATA_TYPE_CURRENT);
        UserGrantTimeState restoredState = datastore.readForUser(mUser, DATA_TYPE_CURRENT);
        assertRestoredStateIsCorrect(restoredState, EMPTY_STATE);
    }

    @Test
    public void testWriteReadData_overwroteState_restoredCorrectly() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        datastore.writeForUser(PACKAGES_STATE, mUser, DATA_TYPE_CURRENT);
        datastore.writeForUser(DEFAULT_STATE, mUser, DATA_TYPE_CURRENT);
        UserGrantTimeState restoredState = datastore.readForUser(mUser, DATA_TYPE_CURRENT);
        assertRestoredStateIsCorrect(restoredState, DEFAULT_STATE);
    }

    @Test
    public void testWriteReadData_writeAllStateTypes_restoredCorrectly() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        datastore.writeForUser(PACKAGES_STATE, mUser, DATA_TYPE_CURRENT);
        datastore.writeForUser(EMPTY_STATE, mUser, DATA_TYPE_STAGED);
        assertRestoredStateIsCorrect(
                datastore.readForUser(mUser, DATA_TYPE_CURRENT), PACKAGES_STATE);
        assertRestoredStateIsCorrect(datastore.readForUser(mUser, DATA_TYPE_STAGED), EMPTY_STATE);
    }

    @Test
    public void testWriteReadData_statesForTwoUsersWritten_restoredCorrectly() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        UserHandle secondUser = UserHandle.of(mUser.getIdentifier() + 10);
        datastore.writeForUser(PACKAGES_STATE, mUser, DATA_TYPE_CURRENT);
        datastore.writeForUser(SHARED_USERS_STATE, secondUser, DATA_TYPE_CURRENT);
        UserGrantTimeState restoredState = datastore.readForUser(mUser, DATA_TYPE_CURRENT);
        assertRestoredStateIsCorrect(restoredState, PACKAGES_STATE);
        UserGrantTimeState restoredState2 = datastore.readForUser(secondUser, DATA_TYPE_CURRENT);
        assertRestoredStateIsCorrect(restoredState2, SHARED_USERS_STATE);
    }

    @Test
    public void testReadData_stateIsNotWritten_nullIsReturned() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        UserGrantTimeState state = datastore.readForUser(mUser, DATA_TYPE_CURRENT);
        assertThat(state).isNull();
    }

    @Test
    public void testParseData_stateIsNotWritten_nullIsReturned() {
        UserGrantTimeState state =
                GrantTimeXmlHelper.parseGrantTime(
                        new File(mEnvironmentFixture.getDataDirectory(), "test_file.xml"));
        assertThat(state).isNull();
    }

    @Test
    public void testWriteData_writeAndReadState_restoredEqualToWritten() {
        File testFile = new File(mEnvironmentFixture.getDataDirectory(), "test_file.xml");
        GrantTimeXmlHelper.serializeGrantTimes(testFile, DEFAULT_STATE);
        UserGrantTimeState state = GrantTimeXmlHelper.parseGrantTime(testFile);
        assertRestoredStateIsCorrect(state, DEFAULT_STATE);
    }

    @Test
    public void testGetFile_getAllTypes_allFilesNonNullAndDifferent() {
        FirstGrantTimeDatastore datastore = FirstGrantTimeDatastore.createInstance();
        File current = datastore.getFile(mUser, DATA_TYPE_CURRENT);
        File staged = datastore.getFile(mUser, DATA_TYPE_STAGED);
        assertThat(current).isNotNull();
        assertThat(staged).isNotNull();
        assertThat(current).isNotEqualTo(staged);
    }

    private static void assertRestoredStateIsCorrect(
            UserGrantTimeState actual, UserGrantTimeState expected) {
        assertThat(actual.getVersion()).isEqualTo(expected.getVersion());
        assertThat(actual.getPackageGrantTimes()).isEqualTo(expected.getPackageGrantTimes());
        assertThat(actual.getSharedUserGrantTimes()).isEqualTo(expected.getSharedUserGrantTimes());
    }
}
