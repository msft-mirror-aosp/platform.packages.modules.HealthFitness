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

package com.android.server.healthconnect.storage.datatypehelpers;

import static android.health.connect.Constants.DEFAULT_PAGE_SIZE;
import static android.health.connect.Constants.DELETE;
import static android.health.connect.datatypes.RecordTypeIdentifier.RECORD_TYPE_STEPS;

import static com.android.server.healthconnect.storage.datatypehelpers.ChangeLogsHelper.UUIDS_COLUMN_NAME;
import static com.android.server.healthconnect.storage.utils.StorageUtils.bytesToUuids;

import static com.google.common.truth.Truth.assertThat;

import com.android.server.healthconnect.storage.request.UpsertTableRequest;

import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ChangeLogsHelperTest {
    @Test
    public void changeLogs_getUpsertTableRequests_listLessThanDefaultPageSize() {
        ChangeLogsHelper.ChangeLogs changeLogs =
                new ChangeLogsHelper.ChangeLogs(DELETE, Instant.now().toEpochMilli());
        UUID uuid = UUID.randomUUID();
        changeLogs.addUUID(RECORD_TYPE_STEPS, 0, uuid);
        List<UpsertTableRequest> requests = changeLogs.getUpsertTableRequests();

        assertThat(requests).hasSize(1);
        List<UUID> uuidList =
                bytesToUuids((byte[]) requests.get(0).getContentValues().get(UUIDS_COLUMN_NAME));
        assertThat(uuidList).containsExactly(uuid);
    }

    @Test
    public void changeLogs_getUpsertTableRequests_listMoreThanDefaultPageSize() {
        ChangeLogsHelper.ChangeLogs changeLogs =
                new ChangeLogsHelper.ChangeLogs(DELETE, Instant.now().toEpochMilli());
        for (int i = 0; i <= DEFAULT_PAGE_SIZE; i++) {
            UUID uuid = UUID.randomUUID();
            changeLogs.addUUID(RECORD_TYPE_STEPS, 0, uuid);
        }
        List<UpsertTableRequest> requests = changeLogs.getUpsertTableRequests();

        assertThat(requests).hasSize(2);
        List<UUID> uuidList1 =
                bytesToUuids((byte[]) requests.get(0).getContentValues().get(UUIDS_COLUMN_NAME));
        assertThat(uuidList1).hasSize(DEFAULT_PAGE_SIZE);
        List<UUID> uuidList2 =
                bytesToUuids((byte[]) requests.get(1).getContentValues().get(UUIDS_COLUMN_NAME));
        assertThat(uuidList2).hasSize(1);
    }
}
