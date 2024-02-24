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

package com.android.healthconnect.controller.permissions.additionalaccess

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions.READ_EXERCISE_ROUTES
import com.android.healthconnect.controller.permissions.api.GetGrantedHealthPermissionsUseCase
import com.android.healthconnect.controller.permissions.api.GetHealthPermissionsFlagsUseCase
import com.android.healthconnect.controller.service.IoDispatcher
import com.android.healthconnect.controller.shared.usecase.BaseUseCase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
class LoadExerciseRoutePermissionUseCase
@Inject
constructor(
    private val loadDeclaredHealthPermissionUseCase: LoadDeclaredHealthPermissionUseCase,
    private val getHealthPermissionsFlagsUseCase: GetHealthPermissionsFlagsUseCase,
    private val getGrantedHealthPermissionsUseCase: GetGrantedHealthPermissionsUseCase,
    @IoDispatcher dispatcher: CoroutineDispatcher
) : BaseUseCase<String, ExerciseRouteState>(dispatcher) {

    override suspend fun execute(input: String): ExerciseRouteState {

        val grantedPermissions = getGrantedHealthPermissionsUseCase(input)
        if (grantedPermissions.contains(READ_EXERCISE_ROUTES)) {
            return ExerciseRouteState.GRANTED
        }

        val appPermissions = loadDeclaredHealthPermissionUseCase(input)
        if (!appPermissions.contains(READ_EXERCISE_ROUTES)) {
            return ExerciseRouteState.NOT_DECLARED
        }

        val permissionFlags = getHealthPermissionsFlagsUseCase(input, listOf(READ_EXERCISE_ROUTES))

        val exerciseRoutePermissionFlag =
            permissionFlags[READ_EXERCISE_ROUTES] ?: return ExerciseRouteState.NOT_DECLARED
        if (exerciseRoutePermissionFlag.and(PackageManager.FLAG_PERMISSION_USER_FIXED) != 0) {
            return ExerciseRouteState.REVOKED
        }

        return ExerciseRouteState.DECLARED
    }
}

enum class ExerciseRouteState {
    NOT_DECLARED,
    DECLARED,
    GRANTED,
    REVOKED
}
