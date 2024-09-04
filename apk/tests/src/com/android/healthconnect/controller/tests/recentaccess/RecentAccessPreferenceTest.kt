/*
 *
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.android.healthconnect.controller.tests.recentaccess

import android.content.Context
import android.health.connect.HealthDataCategory
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import androidx.preference.PreferenceViewHolder
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.recentaccess.RecentAccessEntry
import com.android.healthconnect.controller.recentaccess.RecentAccessPreference
import com.android.healthconnect.controller.shared.HealthDataCategoryExtensions.uppercaseTitle
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TestTimeSource
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.Instant
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class RecentAccessPreferenceTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    private lateinit var context: Context
    private lateinit var holder: PreferenceViewHolder
    private val timeSource = TestTimeSource

    @Before
    fun setup() {
        hiltRule.inject()
        context = ContextThemeWrapper(getApplicationContext(), R.style.Theme_HealthConnect)
        holder =
            PreferenceViewHolder.createInstanceForTests(
                View.inflate(context, R.layout.widget_recent_access_timeline, /* parent= */ null))
    }

    @Test
    fun testRecentAccessPreference_showsWriteActivity() {
        val recentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = recentAccessEntry,
                timeSource = timeSource,
                showCategories = true)
        recentAccessPreference.onBindViewHolder(holder)

        assertThat(holder.findViewById(R.id.data_types_written)?.isVisible).isTrue()
        assertThat((holder.findViewById(R.id.data_types_written) as TextView).text)
            .isEqualTo("Write: Activity, Vitals")
    }

    @Test
    fun testRecentAccessPreference_noWrittenActivity_hidesWriteActivity() {
        val recentAccessEntry =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten = mutableSetOf(),
                dataTypesRead = mutableSetOf())
        val recentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = recentAccessEntry,
                timeSource = timeSource,
                showCategories = true)

        recentAccessPreference.onBindViewHolder(holder)

        assertThat(holder.findViewById(R.id.data_types_written)?.isVisible).isFalse()
    }

    @Test
    fun testRecentAccessPreference_noReadActivity_hidesReadActivity() {
        val recentAccessEntry =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten = mutableSetOf(),
                dataTypesRead = mutableSetOf())
        val recentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = recentAccessEntry,
                timeSource = timeSource,
                showCategories = true)

        recentAccessPreference.onBindViewHolder(holder)

        assertThat(holder.findViewById(R.id.data_types_read)?.isVisible).isFalse()
    }

    @Test
    fun testRecentAccessPreference_onBindViewHolder_showsReadActivity() {
        val noWriteEntry =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten = mutableSetOf(),
                dataTypesRead = mutableSetOf())

        val withWriteEntry =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten = mutableSetOf(HealthDataCategory.ACTIVITY.uppercaseTitle()),
                dataTypesRead = mutableSetOf())
        val recentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = noWriteEntry,
                timeSource = timeSource,
                showCategories = true)

        recentAccessPreference.onBindViewHolder(holder)
        assertThat(holder.findViewById(R.id.data_types_written)?.isVisible).isFalse()

        val updatedRecentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = withWriteEntry,
                timeSource = timeSource,
                showCategories = true)

        updatedRecentAccessPreference.onBindViewHolder(holder)

        assertThat(holder.findViewById(R.id.data_types_written)?.isVisible).isTrue()
        assertThat((holder.findViewById(R.id.data_types_written) as TextView).text)
            .isEqualTo("Write: Activity")
    }

    @Test
    fun testRecentAccessPreference_onBindViewHolder_hidesReadActivity() {
        val noWriteEntry =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten = mutableSetOf(),
                dataTypesRead = mutableSetOf())

        val withWriteEntry =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten = mutableSetOf(HealthDataCategory.ACTIVITY.uppercaseTitle()),
                dataTypesRead = mutableSetOf())

        val recentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = withWriteEntry,
                timeSource = timeSource,
                showCategories = true)
        recentAccessPreference.onBindViewHolder(holder)

        assertThat(holder.findViewById(R.id.data_types_written)?.isVisible).isTrue()
        assertThat((holder.findViewById(R.id.data_types_written) as TextView).text)
            .isEqualTo("Write: Activity")

        val updatedRecentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = noWriteEntry,
                timeSource = timeSource,
                showCategories = true)

        updatedRecentAccessPreference.onBindViewHolder(holder)
        assertThat(holder.findViewById(R.id.data_types_written)?.isVisible).isFalse()
    }

    @Test
    fun testRecentAccessPreference_showsReadActivity() {
        val recentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = recentAccessEntry,
                timeSource = timeSource,
                showCategories = true)
        recentAccessPreference.onBindViewHolder(holder)

        assertThat(holder.findViewById(R.id.data_types_read)?.isVisible).isTrue()
        assertThat((holder.findViewById(R.id.data_types_read) as TextView).text)
            .isEqualTo("Read: Nutrition, Sleep")
    }

    @Test
    fun testRecentAccessPreference_showsAppName() {
        val recentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = recentAccessEntry,
                timeSource = timeSource,
                showCategories = true)
        recentAccessPreference.onBindViewHolder(holder)

        assertThat(holder.findViewById(R.id.title)?.isVisible).isTrue()
        assertThat((holder.findViewById(R.id.title) as TextView).text)
            .isEqualTo("Health Connect test app")
    }

    @Test
    fun testRecentAccessPreference_hideCategories() {
        val recentAccessPreference =
            RecentAccessPreference(
                context = context,
                recentAccessEntry = recentAccessEntry,
                timeSource = timeSource,
                showCategories = false)
        recentAccessPreference.onBindViewHolder(holder)

        assertThat(holder.findViewById(R.id.data_types_read)?.isVisible).isFalse()
        assertThat(holder.findViewById(R.id.data_types_written)?.isVisible).isFalse()
    }

    companion object {
        val recentAccessEntry =
            RecentAccessEntry(
                metadata = TEST_APP,
                instantTime = Instant.parse("2022-10-20T18:40:13.00Z"),
                isToday = true,
                isInactive = false,
                dataTypesWritten =
                    mutableSetOf(
                        HealthDataCategory.ACTIVITY.uppercaseTitle(),
                        HealthDataCategory.VITALS.uppercaseTitle()),
                dataTypesRead =
                    mutableSetOf(
                        HealthDataCategory.SLEEP.uppercaseTitle(),
                        HealthDataCategory.NUTRITION.uppercaseTitle()))
    }
}
