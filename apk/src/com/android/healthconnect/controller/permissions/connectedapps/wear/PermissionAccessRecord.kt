/**
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.healthconnect.controller.permissions.connectedapps.wear

import com.android.healthconnect.controller.permissions.data.HealthPermission
import com.android.healthconnect.controller.shared.app.AppMetadata
import java.time.Instant

/**
 * Represents the access to a specific health permission by one or more apps.
 *
 * @property permission The health permission that was accessed.
 * @property appAccesses A list of [AppAccess] objects, each element representing an unique app that
 *   accessed this permission and its last access time.
 */
data class PermissionAccess(val permission: HealthPermission, var appAccesses: List<AppAccess>)

/**
 * Represents a single access to health data by an app.
 *
 * @property app The metadata of the app that accessed the data.
 * @property lastAccessTime The timestamp of the last access.
 */
data class AppAccess(val app: AppMetadata, val lastAccessTime: Instant) {}
