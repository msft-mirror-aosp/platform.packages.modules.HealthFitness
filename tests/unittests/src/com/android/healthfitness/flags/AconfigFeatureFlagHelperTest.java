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

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import org.junit.Rule;
import org.junit.Test;

public class AconfigFeatureFlagHelperTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    @EnableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void phr_featureFlagTrueAndDbFlagTrue_expectTrue() {
        assertThat(AconfigFeatureFlagHelper.isPersonalHealthRecordEnabled()).isTrue();
    }

    @Test
    @DisableFlags({Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE, Flags.FLAG_PERSONAL_HEALTH_RECORD})
    public void phr_featureFlagFalseAndDbFlagFalse_expectFalse() {
        assertThat(AconfigFeatureFlagHelper.isPersonalHealthRecordEnabled()).isFalse();
    }

    @Test
    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void phr_featureFlagFalseAndDbTrue_expectFalse() {
        assertThat(AconfigFeatureFlagHelper.isPersonalHealthRecordEnabled()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD)
    @DisableFlags(Flags.FLAG_PERSONAL_HEALTH_RECORD_DATABASE)
    public void phr_featureFlagTrueAndDbFalse_expectFalse() {
        assertThat(AconfigFeatureFlagHelper.isPersonalHealthRecordEnabled()).isFalse();
    }
}
