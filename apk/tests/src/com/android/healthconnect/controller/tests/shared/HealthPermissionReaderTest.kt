package com.android.healthconnect.controller.tests.shared

import android.content.Context
import android.health.connect.HealthPermissions
import android.health.connect.HealthPermissions.READ_PLANNED_EXERCISE
import android.health.connect.HealthPermissions.READ_SKIN_TEMPERATURE
import android.health.connect.HealthPermissions.WRITE_PLANNED_EXERCISE
import android.health.connect.HealthPermissions.WRITE_SKIN_TEMPERATURE
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.DataTypePermission
import com.android.healthconnect.controller.shared.HealthPermissionReader
import com.android.healthconnect.controller.tests.utils.OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME_2
import com.android.healthconnect.controller.tests.utils.UNSUPPORTED_TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.di.FakeFeatureUtils
import com.android.healthconnect.controller.utils.FeatureUtils
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class HealthPermissionReaderTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var permissionReader: HealthPermissionReader
    @Inject lateinit var fakeFeatureUtils: FeatureUtils
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().context
    }

    @Test
    fun getDeclaredPermissions_hidesSessionTypesIfDisabled() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSessionTypesEnabled(false)

        assertThat(permissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.WRITE_EXERCISE_ROUTE.toHealthPermission(),
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED.toHealthPermission())
    }

    @Test
    fun getDeclaredPermissions_filtersOutAdditionalPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)

        assertThat(permissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.WRITE_EXERCISE_ROUTE.toHealthPermission(),
                HealthPermissions.READ_EXERCISE.toHealthPermission(),
                HealthPermissions.WRITE_EXERCISE.toHealthPermission(),
                HealthPermissions.WRITE_SLEEP.toHealthPermission(),
                HealthPermissions.READ_SLEEP.toHealthPermission(),
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED.toHealthPermission())
    }

    @Test
    fun getDeclaredPermissions_returnsAllPermissions_exceptHiddenPermissions() = runTest {
        assertThat(permissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.WRITE_EXERCISE_ROUTE.toHealthPermission(),
                HealthPermissions.READ_EXERCISE.toHealthPermission(),
                HealthPermissions.WRITE_EXERCISE.toHealthPermission(),
                HealthPermissions.WRITE_SLEEP.toHealthPermission(),
                HealthPermissions.READ_SLEEP.toHealthPermission(),
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED.toHealthPermission())
    }

    @Test
    fun getHealthPermissions_returnsAllPermissions() {
        assertThat(permissionReader.getHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.WRITE_EXERCISE_ROUTE,
                HealthPermissions.READ_EXERCISE,
                HealthPermissions.READ_EXERCISE_ROUTES,
                HealthPermissions.WRITE_EXERCISE,
                HealthPermissions.WRITE_SLEEP,
                HealthPermissions.READ_SLEEP,
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED,
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED)
    }

    @Test
    fun isRationalIntentDeclared_withIntent_returnsTrue() {
        assertThat(permissionReader.isRationaleIntentDeclared(TEST_APP_PACKAGE_NAME)).isTrue()
    }

    @Test
    fun isRationalIntentDeclared_noIntent_returnsTrue() {
        assertThat(permissionReader.isRationaleIntentDeclared(UNSUPPORTED_TEST_APP_PACKAGE_NAME))
            .isFalse()
    }

    @Test
    fun getAppsWithHealthPermissions_returnsSupportedApps() = runTest {
        assertThat(permissionReader.getAppsWithHealthPermissions())
            .containsAtLeast(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2)
    }

    @Test
    fun getAppsWithHealthPermissions_returnsDistinctApps() = runTest {
        val apps = permissionReader.getAppsWithHealthPermissions()
        assertThat(apps).isEqualTo(apps.distinct())
    }

    @Test
    fun getAppsWithHealthPermissions_doesNotReturnUnsupportedApps() = runTest {
        assertThat(permissionReader.getAppsWithHealthPermissions())
            .doesNotContain(UNSUPPORTED_TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun getAppsWithOldHealthPermissions_returnsOldSupportedApps() = runTest {
        assertThat(permissionReader.getAppsWithOldHealthPermissions())
            .containsExactly(OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun getAppsWithOldHealthPermissions_returnsDistinctApps() = runTest {
        val apps = permissionReader.getAppsWithOldHealthPermissions()
        assertThat(apps).isEqualTo(apps.distinct())
    }

    @Test
    fun shouldHidePermission_whenFeatureNotEnabled_returnsTrue() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(false)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPlannedExerciseEnabled(false)
        assertThat(permissionReader.shouldHidePermission(READ_SKIN_TEMPERATURE)).isTrue()
        assertThat(permissionReader.shouldHidePermission(WRITE_SKIN_TEMPERATURE)).isTrue()

        assertThat(permissionReader.shouldHidePermission(READ_PLANNED_EXERCISE)).isTrue()
        assertThat(permissionReader.shouldHidePermission(WRITE_PLANNED_EXERCISE)).isTrue()
    }

    @Test
    fun shouldHidePermission_whenFeatureEnabled_returnsFalse() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSkinTemperatureEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPlannedExerciseEnabled(true)
        assertThat(permissionReader.shouldHidePermission(READ_SKIN_TEMPERATURE)).isFalse()
        assertThat(permissionReader.shouldHidePermission(WRITE_SKIN_TEMPERATURE)).isFalse()

        assertThat(permissionReader.shouldHidePermission(READ_PLANNED_EXERCISE)).isFalse()
        assertThat(permissionReader.shouldHidePermission(WRITE_PLANNED_EXERCISE)).isFalse()
    }

    @Test
    fun isAdditionalPermission_returnsTrue() = runTest {
        val perm = HealthPermission.AdditionalPermission(HealthPermissions.READ_EXERCISE_ROUTES)
        assertThat(permissionReader.isAdditionalPermission(perm.toString())).isTrue()
    }

    private fun String.toHealthPermission(): DataTypePermission {
        return DataTypePermission.fromPermissionString(this)
    }
}
