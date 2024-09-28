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

package com.android.server.healthconnect.storage.request;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ReadTableRequestTest {

    @Test
    public void testGetReadCommand_simpleQuery() {
        ReadTableRequest request = new ReadTableRequest("tableName");

        assertThat(request.getReadCommand()).isEqualTo("SELECT * FROM tableName");
    }

    @Test
    public void testGetCountCommand_simpleQuery() {
        ReadTableRequest request = new ReadTableRequest("tableName");

        assertThat(request.getCountCommand()).isEqualTo("SELECT COUNT(*) FROM tableName");
    }

    @Test
    public void testGetReadCommand_simpleQueryLimit() {
        ReadTableRequest request = new ReadTableRequest("tableName").setLimit(5);

        assertThat(request.getReadCommand()).isEqualTo("SELECT * FROM tableName LIMIT 5");
    }

    @Test
    public void testGetCountCommand_simpleQueryLimit() {
        ReadTableRequest request = new ReadTableRequest("tableName").setLimit(5);

        assertThat(request.getCountCommand()).isEqualTo("SELECT COUNT(*) FROM tableName LIMIT 5");
    }

    @Test
    public void testGetReadCommand_oneColumn() {
        ReadTableRequest request = new ReadTableRequest("tableName").setColumnNames(List.of("col"));

        assertThat(request.getReadCommand()).isEqualTo("SELECT col FROM tableName");
    }

    @Test
    public void testGetCountCommand_oneColumn() {
        ReadTableRequest request = new ReadTableRequest("tableName").setColumnNames(List.of("col"));

        // SELECT COUNT(col) would only return a count of non-null rows, whereas the select would
        // return null rows, so COUNT(*) is needed.
        assertThat(request.getCountCommand()).isEqualTo("SELECT COUNT(*) FROM tableName");
    }

    @Test
    public void testGetReadCommand_multipleColumns() {
        ReadTableRequest request =
                new ReadTableRequest("tableName").setColumnNames(List.of("col1", "col2", "col3"));

        assertThat(request.getReadCommand()).isEqualTo("SELECT col1,col2,col3 FROM tableName");
    }

    @Test
    public void testGetCountCommand_multipleColumns() {
        ReadTableRequest request =
                new ReadTableRequest("tableName").setColumnNames(List.of("col1", "col2", "col3"));

        assertThat(request.getCountCommand()).isEqualTo("SELECT COUNT(*) FROM tableName");
    }

    @Test
    public void testGetReadCommand_distinctOneColumn() {
        ReadTableRequest request =
                new ReadTableRequest("tableName")
                        .setColumnNames(List.of("col"))
                        .setDistinctClause(true);

        assertThat(request.getReadCommand()).isEqualTo("SELECT DISTINCT col FROM tableName");
    }

    @Test
    public void testGetCountCommand_distinctOneColumn() {
        ReadTableRequest request =
                new ReadTableRequest("tableName")
                        .setColumnNames(List.of("col"))
                        .setDistinctClause(true);

        assertThat(request.getCountCommand())
                .isEqualTo("SELECT COUNT(DISTINCT col) FROM tableName");
    }

    @Test
    public void testGetReadCommand_distinctMultipleColumns() {
        ReadTableRequest request =
                new ReadTableRequest("tableName")
                        .setColumnNames(List.of("col1", "col2", "col3"))
                        .setDistinctClause(true);

        assertThat(request.getReadCommand())
                .isEqualTo("SELECT DISTINCT col1,col2,col3 FROM tableName");
    }

    @Test
    public void testGetCountCommand_distinctMultipleColumn() {
        ReadTableRequest request =
                new ReadTableRequest("tableName")
                        .setColumnNames(List.of("col1", "col2", "col3"))
                        .setDistinctClause(true);

        assertThat(request.getCountCommand())
                .isEqualTo("SELECT COUNT(DISTINCT col1,col2,col3) FROM tableName");
    }

    @Test
    public void testGetReadCommand_unionQuery() {
        ReadTableRequest request = new ReadTableRequest("tableName").setColumnNames(List.of("col"));
        ReadTableRequest unionRequest =
                new ReadTableRequest("otherTableName").setColumnNames(List.of("col"));
        request.setUnionReadRequests(List.of(unionRequest));

        assertThat(request.getReadCommand())
                .isEqualTo(
                        "SELECT * FROM (SELECT col FROM otherTableName) UNION ALL SELECT col FROM"
                                + " tableName");
    }

    @Test
    public void testGetCountCommand_unionQuery() {
        ReadTableRequest request = new ReadTableRequest("tableName").setColumnNames(List.of("col"));
        ReadTableRequest unionRequest =
                new ReadTableRequest("otherTableName").setColumnNames(List.of("col"));
        request.setUnionReadRequests(List.of(unionRequest));

        assertThat(request.getCountCommand())
                .isEqualTo(
                        "SELECT COUNT(*) FROM (SELECT * FROM (SELECT col FROM otherTableName) UNION"
                                + " ALL SELECT col FROM tableName)");
    }
}
