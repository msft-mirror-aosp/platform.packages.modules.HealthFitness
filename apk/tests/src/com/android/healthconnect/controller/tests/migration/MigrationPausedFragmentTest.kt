package com.android.healthconnect.controller.tests.migration

import android.content.Context
import android.os.Bundle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.android.healthconnect.controller.R
import com.android.healthconnect.controller.migration.MigrationPausedFragment
import com.android.healthconnect.controller.tests.utils.launchFragment
import com.android.healthconnect.controller.tests.utils.whenever
import com.android.healthconnect.controller.utils.NavigationUtils
import com.google.common.truth.Truth
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@HiltAndroidTest
class MigrationPausedFragmentTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @BindValue val navigationUtils: NavigationUtils = Mockito.mock(NavigationUtils::class.java)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun migrationPausedFragment_displaysCorrectly() {
        launchFragment<MigrationPausedFragment>()

        onView(withText("Integration paused")).check(matches(isDisplayed()))
        onView(
                withText(
                    "The Health Connect app closed while it was being integrated " +
                        "with the Android system.\n\nClick resume to reopen the app and continue " +
                        "transferring your data and permissions."))
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).check(matches(isDisplayed()))
        onView(withText("Resume")).check(matches(isDisplayed()))
    }

    @Test
    fun migrationPausedFragment_whenCancelButtonPressed_setsSharedPreferences() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        val scenario = launchFragment<MigrationPausedFragment>(Bundle())
        onView(withText("Cancel")).check(matches(isDisplayed()))
        onView(withText("Cancel")).perform(ViewActions.click())

        scenario.onActivity { activity ->
            val preferences =
                activity.getSharedPreferences("USER_ACTIVITY_TRACKER", Context.MODE_PRIVATE)
            Truth.assertThat(preferences.getBoolean("integration_paused_seen", false)).isTrue()
        }
    }

    @Test
    fun migrationPausedFragment_whenResumeButtonPressed_navigatesToMigratorApk() {
        Mockito.doNothing().whenever(navigationUtils).navigate(any(), any())
        launchFragment<MigrationPausedFragment>(Bundle())
        onView(withText("Resume")).check(matches(isDisplayed()))
        onView(withText("Resume")).perform(ViewActions.click())

        verify(navigationUtils, times(1))
            .navigate(any(), eq(R.id.action_migrationPausedFragment_to_migrationApk))
    }

    @Test
    fun migrationPausedFragment_whenNavigateToMigratorApkFails_displaysCorrectly() {
        whenever(navigationUtils.navigate(any(), any())).thenThrow(RuntimeException("Exception"))
        launchFragment<MigrationPausedFragment>(Bundle())
        onView(withText("Resume")).check(matches(isDisplayed()))
        onView(withText("Resume")).perform(ViewActions.click())

        onView(withText("Integration paused")).check(matches(isDisplayed()))
        onView(
                withText(
                    "The Health Connect app closed while it was being integrated " +
                        "with the Android system.\n\nClick resume to reopen the app and continue " +
                        "transferring your data and permissions."))
            .check(matches(isDisplayed()))
        onView(withText("Cancel")).check(matches(isDisplayed()))
        onView(withText("Resume")).check(matches(isDisplayed()))
    }
}
