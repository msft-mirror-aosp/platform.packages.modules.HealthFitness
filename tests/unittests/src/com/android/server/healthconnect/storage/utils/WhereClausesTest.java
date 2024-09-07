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

package com.android.server.healthconnect.storage.utils;

import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.AND;
import static com.android.server.healthconnect.storage.utils.WhereClauses.LogicalOperator.OR;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WhereClausesTest {

    @Test
    public void testAddNestedWhereClauses_emptyNesting_noBrackets() {
        WhereClauses level1 = new WhereClauses(AND);
        WhereClauses level2 = new WhereClauses(AND);
        WhereClauses level3 = new WhereClauses(AND);
        level3.addWhereInIntsClause("foo", List.of(1, 2, 3));
        level2.addNestedWhereClauses(level3);
        level1.addNestedWhereClauses(level2);
        assertThat(level1.get(/* withWhereKeyword= */ true)).isEqualTo(" WHERE foo IN (1, 2, 3)");
    }

    @Test
    public void testAddNestedWhereClauses_noLogicalTypeChange_noBrackets() {
        WhereClauses level1 = new WhereClauses(AND);
        WhereClauses level2 = new WhereClauses(AND);
        WhereClauses level3 = new WhereClauses(AND);
        level3.addWhereInIntsClause("foo", List.of(1, 2, 3));
        level2.addWhereEqualsClause("bar", "value");
        level2.addNestedWhereClauses(level3);
        level1.addNestedWhereClauses(level2);
        assertThat(level1.get(/* withWhereKeyword= */ true))
                .isEqualTo(" WHERE bar = 'value' AND foo IN (1, 2, 3)");
    }

    @Test
    public void testAddNestedWhereClauses_logicalTypeChange_brackets() {
        WhereClauses level1 = new WhereClauses(AND);
        WhereClauses level2 = new WhereClauses(OR);
        WhereClauses level3 = new WhereClauses(AND);
        level3.addWhereInIntsClause("foo", List.of(1, 2, 3));
        level2.addWhereEqualsClause("bar", "value");
        level2.addNestedWhereClauses(level3);
        level1.addNestedWhereClauses(level2);
        assertThat(level1.get(/* withWhereKeyword= */ true))
                .isEqualTo(" WHERE (bar = 'value' OR (foo IN (1, 2, 3)))");
    }
}
