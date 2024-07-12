package com.android.healthconnect.controller.tests

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.platform.test.flag.junit.SetFlagsRule
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario.launchActivityForResult
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.healthconnect.controller.MainActivity
import com.android.healthconnect.controller.exportimport.api.ExportStatusViewModel
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiState
import com.android.healthconnect.controller.exportimport.api.ScheduledExportUiStatus
import com.android.healthconnect.controller.migration.MigrationViewModel
import com.android.healthconnect.controller.migration.MigrationViewModel.MigrationFragmentState.WithData
import com.android.healthconnect.controller.migration.api.MigrationRestoreState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiError
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.DataRestoreUiState
import com.android.healthconnect.controller.migration.api.MigrationRestoreState.MigrationUiState
import com.android.healthconnect.controller.shared.Constants
import com.android.healthconnect.controller.tests.utils.NOW
import com.android.healthconnect.controller.tests.utils.showOnboarding
import com.android.healthconnect.controller.tests.utils.whenever
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

@HiltAndroidTest
@OptIn(ExperimentalCoroutinesApi::class)
class MainActivityTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val setFlagsRule = SetFlagsRule()

    @BindValue val viewModel: MigrationViewModel = Mockito.mock(MigrationViewModel::class.java)
    @BindValue
    val exportStatusViewModel: ExportStatusViewModel =
        Mockito.mock(ExportStatusViewModel::class.java)

    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        // Required for aconfig flag reading for tests run on pre V devices
        InstrumentationRegistry.getInstrumentation()
            .getUiAutomation()
            .adoptShellPermissionIdentity(Manifest.permission.READ_DEVICE_CONFIG)
        context = InstrumentationRegistry.getInstrumentation().context
        whenever(viewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IDLE,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(viewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }
        whenever(exportStatusViewModel.storedScheduledExportStatus).then {
            MutableLiveData(
                ScheduledExportUiStatus.WithData(
                    ScheduledExportUiState(
                        NOW,
                        ScheduledExportUiState.DataExportError.DATA_EXPORT_ERROR_NONE,
                        1,
                        null,
                        null,
                        null,
                        null)))
        }
    }

    @Test
    fun homeSettingsIntent_launchesMainActivity() = runTest {
        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        launchActivityForResult<MainActivity>(startActivityIntent)

        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("Permissions and data")).check(matches(isDisplayed()))
    }

    @Test
    fun homeSettingsIntent_migrationInProgress_redirectsToMigrationInProgress() = runTest {
        showOnboarding(context, false)
        whenever(viewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IN_PROGRESS,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(viewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IN_PROGRESS,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        launchActivityForResult<MainActivity>(startActivityIntent)

        onView(withText("Integration in progress")).check(matches(isDisplayed()))
    }

    @Test
    fun homeSettingsIntent_dataRestoreInProgress_redirectsToRestoreInProgress() = runTest {
        showOnboarding(context, false)
        whenever(viewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.IDLE,
                dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(viewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.IDLE,
                        dataRestoreState = DataRestoreUiState.IN_PROGRESS,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        launchActivityForResult<MainActivity>(startActivityIntent)

        onView(withText("Restore in progress")).check(matches(isDisplayed()))
    }

    @Test
    fun homeSettingsIntent_migrationPending_moduleUpdateSeen_launchesMainActivity() = runTest {
        showOnboarding(context, false)
        setModuleUpdateNeededSeen(context, true)
        whenever(viewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.MODULE_UPGRADE_REQUIRED,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(viewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.MODULE_UPGRADE_REQUIRED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        launchActivityForResult<MainActivity>(startActivityIntent)

        onView(withText("Resume integration")).check(matches(isDisplayed()))
        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("Permissions and data")).check(matches(isDisplayed()))
    }

    @Test
    fun homeSettingsIntent_migrationPending_appUpgradeSeen_launchesMainActivity() = runTest {
        showOnboarding(context, false)
        setAppUpgradeNeededSeen(context, true)
        whenever(viewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(viewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.APP_UPGRADE_REQUIRED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        launchActivityForResult<MainActivity>(startActivityIntent)

        onView(withText("Resume integration")).check(matches(isDisplayed()))
        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("Permissions and data")).check(matches(isDisplayed()))
    }

    @Test
    fun homeSettingsIntent_migrationPending_IntegrationPausedSeen_launchesMainActivity() = runTest {
        showOnboarding(context, false)
        setIntegrationPausedSeen(context, true)
        whenever(viewModel.getCurrentMigrationUiState()).then {
            MigrationRestoreState(
                migrationUiState = MigrationUiState.ALLOWED_PAUSED,
                dataRestoreState = DataRestoreUiState.IDLE,
                dataRestoreError = DataRestoreUiError.ERROR_NONE)
        }
        whenever(viewModel.migrationState).then {
            MutableLiveData(
                WithData(
                    MigrationRestoreState(
                        migrationUiState = MigrationUiState.ALLOWED_PAUSED,
                        dataRestoreState = DataRestoreUiState.IDLE,
                        dataRestoreError = DataRestoreUiError.ERROR_NONE)))
        }

        val startActivityIntent =
            Intent.makeMainActivity(ComponentName(context, MainActivity::class.java))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        launchActivityForResult<MainActivity>(startActivityIntent)

        onView(withText("Resume integration")).check(matches(isDisplayed()))
        onView(withText("Recent access")).check(matches(isDisplayed()))
        onView(withText("Permissions and data")).check(matches(isDisplayed()))
    }

    @After
    fun tearDown() {
        showOnboarding(context, false)
        setAppUpgradeNeededSeen(context, false)
        setModuleUpdateNeededSeen(context, false)
    }

    private fun setModuleUpdateNeededSeen(context: Context, seen: Boolean) {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.MODULE_UPDATE_NEEDED_SEEN, seen)
        editor.apply()
    }

    private fun setAppUpgradeNeededSeen(context: Context, seen: Boolean) {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.APP_UPDATE_NEEDED_SEEN, seen)
        editor.apply()
    }

    private fun setIntegrationPausedSeen(context: Context, seen: Boolean) {
        val sharedPreference =
            context.getSharedPreferences(Constants.USER_ACTIVITY_TRACKER, Context.MODE_PRIVATE)
        val editor = sharedPreference.edit()
        editor.putBoolean(Constants.INTEGRATION_PAUSED_SEEN_KEY, seen)
        editor.apply()
    }
}
