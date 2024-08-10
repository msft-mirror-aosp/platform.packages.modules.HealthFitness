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

package com.android.healthfitness.flags;

import static com.android.healthfitness.flags.AconfigFlagHelper.DB_VERSION_TO_DB_FLAG_MAP;
import static com.android.healthfitness.flags.AconfigFlagHelper.LAST_ROLLED_OUT_DB_VERSION;
import static com.android.healthfitness.flags.AconfigFlagHelper.getDbVersion;
import static com.android.healthfitness.flags.AconfigFlagHelper.isPersonalHealthRecordEnabled;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class AconfigFlagHelperTest {
    @ClassRule public static final SetFlagsRule.ClassRule mClassRule = new SetFlagsRule.ClassRule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setup() {
        DB_VERSION_TO_DB_FLAG_MAP.clear();
    }

    @Test
    @DisableFlags({Flags.FLAG_INFRA_TO_GUARD_DB_CHANGES})
    public void infraToGuardDbChangesDisabled() {
        // putting a very high DB version mapping to true to the map
        DB_VERSION_TO_DB_FLAG_MAP.put(1000_000, true);

        // since FLAG_INFRA_TO_GUARD_DB_CHANGES is disabled, that very high version shouldn't be
        // taken into account.
        assertThat(getDbVersion()).isEqualTo(LAST_ROLLED_OUT_DB_VERSION);
    }

    @Test
    @EnableFlags({Flags.FLAG_INFRA_TO_GUARD_DB_CHANGES})
    public void infraToGuardDbChangesEnabled() {
        assertThat(getDbVersion()).isEqualTo(LAST_ROLLED_OUT_DB_VERSION);
    }

    @Test
    @EnableFlags({Flags.FLAG_INFRA_TO_GUARD_DB_CHANGES})
    public void readDbVersionToDbFlagMap_expectNoDbVersionSmallerThanBaseline() {
        // The baseline is the DB version when go/hc-aconfig-and-db is first introduced, which is
        // LAST_ROLLED_OUT_DB_VERSION.
        int baseline = LAST_ROLLED_OUT_DB_VERSION;

        // Initialize the map, it won't be empty after this method is called.
        getDbVersion();

        for (int version : DB_VERSION_TO_DB_FLAG_MAP.keySet()) {
            assertThat(version).isGreaterThan(baseline);
        }
    }

    @Test
    @EnableFlags({Flags.FLAG_INFRA_TO_GUARD_DB_CHANGES})
    public void testGetDbVersion_true_true_true() {
        // initialize DB_VERSION_TO_DB_FLAG_MAP, so it won't be empty when getDbVersion() is called,
        // so the entries created in this test will be used.
        DB_VERSION_TO_DB_FLAG_MAP.put(1, true);
        DB_VERSION_TO_DB_FLAG_MAP.put(2, true);
        DB_VERSION_TO_DB_FLAG_MAP.put(3, true);

        assertThat(getDbVersion()).isEqualTo(3);
    }

    @Test
    @EnableFlags({Flags.FLAG_INFRA_TO_GUARD_DB_CHANGES})
    public void testGetDbVersion_true_false_true() {
        // initialize DB_VERSION_TO_DB_FLAG_MAP, so it won't be empty when getDbVersion() is called,
        // so the entries created in this test will be used.
        DB_VERSION_TO_DB_FLAG_MAP.put(1, true);
        DB_VERSION_TO_DB_FLAG_MAP.put(2, false);
        DB_VERSION_TO_DB_FLAG_MAP.put(3, true);

        assertThat(getDbVersion()).isEqualTo(1);
    }

    @Test
    @EnableFlags({Flags.FLAG_INFRA_TO_GUARD_DB_CHANGES})
    public void testGetDbVersion_true_false_false() {
        // initialize DB_VERSION_TO_DB_FLAG_MAP, so it won't be empty when getDbVersion() is called,
        // so the entries created in this test will be used.
        DB_VERSION_TO_DB_FLAG_MAP.put(1, true);
        DB_VERSION_TO_DB_FLAG_MAP.put(2, false);
        DB_VERSION_TO_DB_FLAG_MAP.put(3, false);

        assertThat(getDbVersion()).isEqualTo(1);
    }

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void phr_featureFlagTrueAndDbFlagTrue_expectTrue() {
        assertThat(isPersonalHealthRecordEnabled()).isTrue();
    }

    @Test
    @DisableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void phr_featureFlagFalseAndDbFlagFalse_expectFalse() {
        assertThat(isPersonalHealthRecordEnabled()).isFalse();
    }

    @Test
    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void phr_featureFlagFalseAndDbTrue_expectFalse() {
        assertThat(isPersonalHealthRecordEnabled()).isFalse();
    }

    @Ignore("TODO(b/357062401): enabled this test when PHR schemas are finalized")
    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void phr_featureFlagTrueAndDbFalse_expectFalse() {
        assertThat(isPersonalHealthRecordEnabled()).isFalse();
    }
}
