/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.android.healthconnect.controller.tests.permissions.additionalaccess

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState.DECLARED
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState.GRANTED
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState.NOT_DECLARED
import com.android.healthconnect.controller.permissions.additionalaccess.ExerciseRouteState.REVOKED
import com.android.healthconnect.controller.permissions.additionalaccess.LoadDeclaredHealthPermissionUseCase
import com.android.healthconnect.controller.permissions.additionalaccess.LoadExerciseRoutePermissionUseCase
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.shared.usecase.UseCaseResults
import com.android.healthconnect.controller.tests.utils.TEST_APP_PACKAGE_NAME
import com.android.healthconnect.controller.tests.utils.safeEq
import com.android.healthconnect.controller.tests.utils.whenever
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@HiltAndroidTest
class LoadExerciseRoutePermissionUseCaseTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @BindValue val loadDeclaredHealthPermissionUseCase: LoadDeclaredHealthPermissionUseCase = mock()
    @BindValue val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase = mock()
    @BindValue val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase = mock()

    @Inject lateinit var useCase: LoadExerciseRoutePermissionUseCase

    @Before
    fun setup() {
        hiltRule.inject()
        whenever(getGrantedHealthPermissionsUseCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))).then {
            emptyList<String>()
        }
        whenever(loadDeclaredHealthPermissionUseCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))).then {
            listOf(HealthPermissions.READ_EXERCISE_ROUTES)
        }

        whenever(
                getHealthPermissionsFlagsUseCase.invoke(
                    safeEq(TEST_APP_PACKAGE_NAME),
                    safeEq(listOf(HealthPermissions.READ_EXERCISE_ROUTES))))
            .then {
                mapOf(
                    HealthPermissions.READ_EXERCISE_ROUTES to
                        PackageManager.FLAG_PERMISSION_USER_SET)
            }
    }

    @Test
    fun execute_exerciseRoutePermissionGranted_returnGrantedState() = runTest {
        whenever(getGrantedHealthPermissionsUseCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))).then {
            listOf(HealthPermissions.READ_EXERCISE_ROUTES)
        }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        assertThat(state).isEqualTo(UseCaseResults.Success(GRANTED))
    }

    @Test
    fun execute_exerciseRoutePermissionDeclared_returnDeclaredState() = runTest {
        val state = useCase.invoke(safeEq(TEST_APP_PACKAGE_NAME))

        assertThat(state).isEqualTo(UseCaseResults.Success(DECLARED))
    }

    @Test
    fun execute_exerciseRoutePermissionRevoked_returnRevokedState() = runTest {
        val flags =
            mapOf(
                HealthPermissions.READ_EXERCISE_ROUTES to PackageManager.FLAG_PERMISSION_USER_FIXED)
        whenever(
                getHealthPermissionsFlagsUseCase.invoke(
                    safeEq(TEST_APP_PACKAGE_NAME),
                    safeEq(listOf(HealthPermissions.READ_EXERCISE_ROUTES))))
            .then { flags }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        assertThat(state).isEqualTo(UseCaseResults.Success(REVOKED))
    }

    @Test
    fun execute_emptyFlags_returnNotDeclaredState() = runTest {
        whenever(
                getHealthPermissionsFlagsUseCase.invoke(
                    safeEq(TEST_APP_PACKAGE_NAME),
                    safeEq(listOf(HealthPermissions.READ_EXERCISE_ROUTES))))
            .then { mapOf<String, Int>() }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        assertThat(state).isEqualTo(UseCaseResults.Success(NOT_DECLARED))
    }

    @Test
    fun execute_noFlagsForExerciseRoutes_returnNotDeclaredState() = runTest {
        val flags =
            mapOf(HealthPermissions.READ_EXERCISE to PackageManager.FLAG_PERMISSION_USER_FIXED)
        whenever(
                getHealthPermissionsFlagsUseCase.invoke(
                    safeEq(TEST_APP_PACKAGE_NAME),
                    safeEq(listOf(HealthPermissions.READ_EXERCISE_ROUTES))))
            .then { flags }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        assertThat(state).isEqualTo(UseCaseResults.Success(NOT_DECLARED))
    }

    @Test
    fun execute_exerciseRoutePermissionNotDeclared_returnNotDeclaredState() = runTest {
        whenever(loadDeclaredHealthPermissionUseCase.invoke(TEST_APP_PACKAGE_NAME)).then {
            listOf<String>()
        }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        assertThat(state).isEqualTo(UseCaseResults.Success(NOT_DECLARED))
    }

    @Test
    fun execute_onException_returnFailedResults() = runTest {
        val ex = IllegalStateException()
        whenever(loadDeclaredHealthPermissionUseCase.invoke(TEST_APP_PACKAGE_NAME)).then {
            throw ex
        }

        val state = useCase.invoke(TEST_APP_PACKAGE_NAME)

        assertThat(state).isEqualTo(UseCaseResults.Failed(ex))
    }
}
