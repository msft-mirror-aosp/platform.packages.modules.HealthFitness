package com.android.healthconnect.controller.tests.shared

import android.content.Context
import android.health.connect.HealthPermissions
import android.health.connect.HealthPermissions.READ_PLANNED_EXERCISE
import android.health.connect.HealthPermissions.READ_SKIN_TEMPERATURE
import android.health.connect.HealthPermissions.WRITE_PLANNED_EXERCISE
import android.health.connect.HealthPermissions.WRITE_SKIN_TEMPERATURE
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.permissions.data.HealthPermission.FitnessPermission
import com.android.healthconnect.controller.permissions.data.MedicalPermissionType
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
import com.android.healthconnect.controller.shared.app.AppPermissionsType.COMBINED_PERMISSIONS
import com.android.healthconnect.controller.shared.app.AppPermissionsType.FITNESS_PERMISSIONS_ONLY
import com.android.healthconnect.controller.shared.app.AppPermissionsType.MEDICAL_PERMISSIONS_ONLY
import com.android.healthconnect.controller.tests.utils.MEDICAL_PERMISSIONS_TEST_APP_PACKAGE_NAME

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
    fun getValidHealthPermissions_hidesSessionTypesIfDisabled() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsSessionTypesEnabled(false)

        assertThat(permissionReader.getValidHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.WRITE_EXERCISE_ROUTE.toHealthPermission(),
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermission.AdditionalPermission.READ_EXERCISE_ROUTES)
    }

    @Test
    fun getValidHealthPermissions_returnsDataTypeAndAdditionalPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsHistoryReadEnabled(true)

        assertThat(permissionReader.getValidHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermissions.READ_EXERCISE.toHealthPermission(),
                HealthPermissions.WRITE_EXERCISE.toHealthPermission(),
                HealthPermissions.READ_SLEEP.toHealthPermission(),
                HealthPermissions.WRITE_SLEEP.toHealthPermission(),
                HealthPermission.AdditionalPermission.READ_EXERCISE_ROUTES,
                HealthPermissions.WRITE_EXERCISE_ROUTE.toHealthPermission(),
                HealthPermission.AdditionalPermission.READ_HEALTH_DATA_IN_BACKGROUND,
                HealthPermission.AdditionalPermission.READ_HEALTH_DATA_HISTORY)
    }

    @Test
    fun getValidHealthPermissions_returnsAllPermissions_exceptHiddenPermissions() = runTest {
        assertThat(permissionReader.getValidHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.WRITE_EXERCISE_ROUTE.toHealthPermission(),
                HealthPermissions.READ_EXERCISE.toHealthPermission(),
                HealthPermissions.WRITE_EXERCISE.toHealthPermission(),
                HealthPermissions.WRITE_SLEEP.toHealthPermission(),
                HealthPermissions.READ_SLEEP.toHealthPermission(),
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED.toHealthPermission(),
                HealthPermission.AdditionalPermission.READ_EXERCISE_ROUTES)
    }

    @Test
    fun getDeclaredHealthPermissions_returnsAllPermissions() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        assertThat(permissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED,
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED,
                HealthPermissions.READ_EXERCISE,
                HealthPermissions.WRITE_EXERCISE,
                HealthPermissions.READ_SLEEP,
                HealthPermissions.WRITE_SLEEP,
                HealthPermissions.READ_EXERCISE_ROUTES,
                HealthPermissions.WRITE_EXERCISE_ROUTE,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND)
    }

    @Test
    fun getDeclaredHealthPermissions_medicalFlagOn_returnsAllPermissions() {
        (fakeFeatureUtils as FakeFeatureUtils).setIsBackgroundReadEnabled(true)
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)
        assertThat(permissionReader.getDeclaredHealthPermissions(TEST_APP_PACKAGE_NAME))
            .containsExactly(
                HealthPermissions.READ_ACTIVE_CALORIES_BURNED,
                HealthPermissions.WRITE_ACTIVE_CALORIES_BURNED,
                HealthPermissions.READ_EXERCISE,
                HealthPermissions.WRITE_EXERCISE,
                HealthPermissions.READ_SLEEP,
                HealthPermissions.WRITE_SLEEP,
                HealthPermissions.READ_EXERCISE_ROUTES,
                HealthPermissions.WRITE_EXERCISE_ROUTE,
                HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND,
                HealthPermissions.READ_MEDICAL_DATA_ALLERGY_INTOLERANCE,
                HealthPermissions.READ_MEDICAL_DATA_CONDITIONS,
                HealthPermissions.READ_MEDICAL_DATA_IMMUNIZATION,
                HealthPermissions.READ_MEDICAL_DATA_LABORATORY_RESULTS,
                HealthPermissions.READ_MEDICAL_DATA_MEDICATIONS,
                HealthPermissions.READ_MEDICAL_DATA_PERSONAL_DETAILS,
                HealthPermissions.READ_MEDICAL_DATA_PRACTITIONER_DETAILS,
                HealthPermissions.READ_MEDICAL_DATA_PREGNANCY,
                HealthPermissions.READ_MEDICAL_DATA_PROCEDURES,
                HealthPermissions.READ_MEDICAL_DATA_SOCIAL_HISTORY,
                HealthPermissions.READ_MEDICAL_DATA_VISITS,
                HealthPermissions.READ_MEDICAL_DATA_VITAL_SIGNS,
                HealthPermissions.WRITE_MEDICAL_DATA)
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
    fun getAppsWithFitnessPermissions_returnsSupportedApps() = runTest {
        assertThat(permissionReader.getAppsWithFitnessPermissions())
                .containsAtLeast(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2)
    }

    @Test
    fun getAppsWithFitnessPermissions_returnsDistinctApps() = runTest {
        val apps = permissionReader.getAppsWithFitnessPermissions()
        assertThat(apps).isEqualTo(apps.distinct())
    }

    @Test
    fun getAppsWithFitnessPermissions_doesNotReturnUnsupportedApps() = runTest {
        assertThat(permissionReader.getAppsWithFitnessPermissions())
                .doesNotContain(UNSUPPORTED_TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun getAppsWithFitnessPermissions_doesNotReturnMedicalPermissionApps() = runTest {
        assertThat(permissionReader.getAppsWithFitnessPermissions())
                .doesNotContain(MEDICAL_PERMISSIONS_TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun getAppsWithMedicalPermissions_flagDisabled_returnsEmptyList() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(false)

        assertThat(permissionReader.getAppsWithMedicalPermissions())
                .isEmpty()
    }

    @Test
    fun getAppsWithMedicalPermissions_returnsSupportedApps() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)

        assertThat(permissionReader.getAppsWithMedicalPermissions())
                .containsAtLeast(TEST_APP_PACKAGE_NAME, MEDICAL_PERMISSIONS_TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun getAppsWithMedicalPermissions_returnsDistinctApps() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(false)

        val apps = permissionReader.getAppsWithMedicalPermissions()
        assertThat(apps).isEqualTo(apps.distinct())
    }

    @Test
    fun getAppsWithMedicalPermissions_doesNotReturnUnsupportedApps() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(false)

        assertThat(permissionReader.getAppsWithMedicalPermissions())
                .doesNotContain(UNSUPPORTED_TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun getAppsWithOldHealthPermissions_returnsOldSupportedApps() = runTest {
        assertThat(permissionReader.getAppsWithOldHealthPermissions())
            .contains(OLD_PERMISSIONS_TEST_APP_PACKAGE_NAME)
    }

    @Test
    fun getAppsWithOldHealthPermissions_returnsDistinctApps() = runTest {
        val apps = permissionReader.getAppsWithOldHealthPermissions()
        assertThat(apps).isEqualTo(apps.distinct())
    }

    @Test
    fun getAppsWithOldHealthPermissions_doesNotReturnAppsWithNewPermissions() = runTest {
        assertThat(permissionReader.getAppsWithOldHealthPermissions())
            .containsNoneOf(TEST_APP_PACKAGE_NAME, TEST_APP_PACKAGE_NAME_2)
    }

    @Test
    fun getAppsWithOldHealthPermissions_doesNotReturnUnsupportedApps() = runTest {
        assertThat(permissionReader.getAppsWithOldHealthPermissions())
            .doesNotContain(UNSUPPORTED_TEST_APP_PACKAGE_NAME)
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

    @Test
    fun isMedicalPermission_withMedicalPermission_returnsTrue() = runTest {
        val perm = HealthPermission.MedicalPermission(MedicalPermissionType.ALL_MEDICAL_DATA)
        assertThat(permissionReader.isMedicalPermission(perm.toString())).isTrue()
    }

    @Test
    fun isMedicalPermission_withoutMedicalPermission_returnsFalse() = runTest {
        val perm = HealthPermission.AdditionalPermission(HealthPermissions.READ_EXERCISE_ROUTES)
        assertThat(permissionReader.isMedicalPermission(perm.toString())).isFalse()
    }

    @Test
    fun getAppPermissionsType_flagDisabled_returnsFitnessOnly() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(false)

        assertThat(permissionReader.getAppPermissionsType(TEST_APP_PACKAGE_NAME)).isEqualTo(FITNESS_PERMISSIONS_ONLY)
    }

    @Test
    fun getAppPermissionsType_flagEnabled_returnsCombinedPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)

        assertThat(permissionReader.getAppPermissionsType(TEST_APP_PACKAGE_NAME)).isEqualTo(COMBINED_PERMISSIONS)
    }

    @Test
    fun getAppPermissionsType_medicalPermissionsOnlyApp_returnsMedicalPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(true)

        assertThat(permissionReader.getAppPermissionsType(MEDICAL_PERMISSIONS_TEST_APP_PACKAGE_NAME)).isEqualTo(MEDICAL_PERMISSIONS_ONLY)
    }

    @Test
    fun getAppPermissionsType_noPermissions_returnsFitnessPermissions() = runTest {
        (fakeFeatureUtils as FakeFeatureUtils).setIsPersonalHealthRecordEnabled(false)

        assertThat(permissionReader.getAppPermissionsType(MEDICAL_PERMISSIONS_TEST_APP_PACKAGE_NAME)).isEqualTo(FITNESS_PERMISSIONS_ONLY)
    }

    private fun String.toHealthPermission(): FitnessPermission {
        return FitnessPermission.fromPermissionString(this)
    }
}
