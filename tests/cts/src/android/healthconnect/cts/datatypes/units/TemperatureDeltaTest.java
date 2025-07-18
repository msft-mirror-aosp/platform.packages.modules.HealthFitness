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

package android.healthconnect.cts.datatypes.units;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.units.TemperatureDelta;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class TemperatureDeltaTest {
    @Test
    public void testCreate() {
        assertThat(TemperatureDelta.fromCelsius(1.2)).isInstanceOf(TemperatureDelta.class);
        assertThat(TemperatureDelta.fromCelsius(1.2).getInCelsius()).isEqualTo(1.2);
    }

    @Test
    public void testEquals() {
        TemperatureDelta TemperatureDelta1 = TemperatureDelta.fromCelsius(-1.1);
        TemperatureDelta TemperatureDelta2 = TemperatureDelta.fromCelsius(-1.1);
        TemperatureDelta TemperatureDelta3 = TemperatureDelta.fromCelsius(1.1);

        assertThat(TemperatureDelta1.equals(TemperatureDelta2)).isEqualTo(true);
        assertThat(TemperatureDelta1.equals(TemperatureDelta3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        TemperatureDelta TemperatureDelta1 = TemperatureDelta.fromCelsius(-0.5);
        TemperatureDelta TemperatureDelta2 = TemperatureDelta.fromCelsius(-0.5);
        TemperatureDelta TemperatureDelta3 = TemperatureDelta.fromCelsius(0.5);

        assertThat(TemperatureDelta1.compareTo(TemperatureDelta2)).isEqualTo(0);
        assertThat(TemperatureDelta1.compareTo(TemperatureDelta3)).isEqualTo(-1);
        assertThat(TemperatureDelta3.compareTo(TemperatureDelta1)).isEqualTo(1);
    }
}
