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

import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.MEDICAL_DATA_SOURCE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.MEDICAL_RESOURCE_TYPE_COLUMN_NAME;
import static com.android.server.healthconnect.storage.datatypehelpers.AccessLogsHelper.getAlterTableRequestForPhrAccessLogs;
import static com.android.server.healthconnect.storage.utils.StorageUtils.INTEGER;
import static com.android.server.healthconnect.storage.utils.StorageUtils.TEXT_NULL;

import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;

import com.android.server.healthconnect.storage.request.AlterTableRequest;

import org.junit.Test;

import java.util.List;

public class AccessLogsHelperTest {

    @Test
    public void testGetAlterTableRequestForPhrAccessLogs_success() {
        List<Pair<String, String>> columnInfo =
                List.of(
                        Pair.create(MEDICAL_RESOURCE_TYPE_COLUMN_NAME, TEXT_NULL),
                        Pair.create(MEDICAL_DATA_SOURCE_COLUMN_NAME, INTEGER));
        AlterTableRequest expected = new AlterTableRequest(AccessLogsHelper.TABLE_NAME, columnInfo);

        AlterTableRequest result = getAlterTableRequestForPhrAccessLogs();

        assertThat(result.getAlterTableAddColumnsCommands())
                .isEqualTo(expected.getAlterTableAddColumnsCommands());
    }
}
