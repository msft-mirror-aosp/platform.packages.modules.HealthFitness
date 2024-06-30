package com.android.healthconnect.controller.tests.data.access

import com.android.healthconnect.controller.data.access.AccessViewModel
import com.android.healthconnect.controller.data.access.AppAccessMetadata
import com.android.healthconnect.controller.data.access.AppAccessState
import com.android.healthconnect.controller.permissions.data.FitnessPermissionType
import com.android.healthconnect.controller.shared.app.AppInfoReader
import com.android.healthconnect.controller.shared.app.AppPermissionsType.COMBINED_PERMISSIONS
import com.android.healthconnect.controller.shared.app.AppPermissionsType.MEDICAL_PERMISSIONS_ONLY
import com.android.healthconnect.controller.tests.utils.InstantTaskExecutorRule
import com.android.healthconnect.controller.tests.utils.TEST_APP
import com.android.healthconnect.controller.tests.utils.TEST_APP_2
import com.android.healthconnect.controller.tests.utils.TEST_APP_3
import com.android.healthconnect.controller.tests.utils.TestObserver
import com.android.healthconnect.controller.tests.utils.di.FakeLoadAccessUseCase
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
@HiltAndroidTest
class AccessViewModelTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: AccessViewModel
    private val fakeLoadAccessUseCase = FakeLoadAccessUseCase()
    private val testDispatcher = TestCoroutineDispatcher()

    @Inject lateinit var appInfoReader: AppInfoReader

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        Dispatchers.setMain(testDispatcher)
        hiltRule.inject()
        viewModel = AccessViewModel(fakeLoadAccessUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    @Test
    fun loadAppMetadataMap_returnsCorrectApps() = runTest {
        val expected =
            mapOf(
                AppAccessState.Read to listOf(AppAccessMetadata(TEST_APP), AppAccessMetadata((TEST_APP_2))),
                AppAccessState.Write to listOf(AppAccessMetadata(TEST_APP_2, COMBINED_PERMISSIONS)),
                AppAccessState.Inactive to listOf(AppAccessMetadata(TEST_APP_3, MEDICAL_PERMISSIONS_ONLY)))
        fakeLoadAccessUseCase.updateMap(expected)

        val testObserver = TestObserver<AccessViewModel.AccessScreenState>()
        viewModel.appMetadataMap.observeForever(testObserver)
        viewModel.loadAppMetaDataMap(FitnessPermissionType.STEPS)
        advanceUntilIdle()

        assertThat(testObserver.getLastValue())
            .isEqualTo(AccessViewModel.AccessScreenState.WithData(expected))
    }
}
