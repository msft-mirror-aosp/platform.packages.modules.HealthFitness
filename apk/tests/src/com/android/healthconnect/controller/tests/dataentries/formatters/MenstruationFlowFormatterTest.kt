/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * ```
 *      http://www.apache.org/licenses/LICENSE-2.0
 * ```
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.healthconnect.controller.tests.dataentries.formatters

import android.content.Context
import android.health.connect.datatypes.MenstruationFlowRecord
import android.health.connect.datatypes.MenstruationFlowRecord.MenstruationFlowType.FLOW_HEAVY
import android.health.connect.datatypes.MenstruationFlowRecord.MenstruationFlowType.FLOW_LIGHT
import android.health.connect.datatypes.MenstruationFlowRecord.MenstruationFlowType.FLOW_MEDIUM
import android.health.connect.datatypes.MenstruationFlowRecord.MenstruationFlowType.FLOW_UNKNOWN
import android.health.connect.datatypes.MenstruationFlowRecord.MenstruationFlowType.MenstruationFlowTypes
import android.health.connect.datatypes.Vo2MaxRecord.Vo2MaxMeasurementMethod.*
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.dataentries.formatters.MenstruationFlowFormatter
import com.android.healthconnect.controller.dataentries.units.UnitPreferences
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.getMetaData
import com.android.healthconnect.controller.tests.utils.setLocale
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MenstruationFlowFormatterTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var formatter: MenstruationFlowFormatter
    @Inject lateinit var preferences: UnitPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().context
        context.setLocale(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")))

        hiltRule.inject()
    }

    @Test
    fun formatValue_heavy_showsAppearance() = runBlocking {
        val record = getRecord(type = FLOW_HEAVY)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Heavy flow")
    }

    @Test
    fun formatValue_medium_showsAppearance() = runBlocking {
        val record = getRecord(type = FLOW_MEDIUM)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Medium flow")
    }

    @Test
    fun formatValue_light_showsAppearance() = runBlocking {
        val record = getRecord(type = FLOW_LIGHT)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Light flow")
    }

    @Test
    fun formatValue_unknown_showsAppearance() = runBlocking {
        val record = getRecord(type = FLOW_UNKNOWN)

        assertThat(formatter.formatValue(record, preferences)).isEqualTo("Unknown flow")
    }

    private fun getRecord(@MenstruationFlowTypes type: Int): MenstruationFlowRecord {
        return MenstruationFlowRecord.Builder(getMetaData(), NOW, type).build()
    }
}
