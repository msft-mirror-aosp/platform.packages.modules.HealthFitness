/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.healthconnect.controller.shared.app

import android.graphics.drawable.Drawable

/** Represents an app being displayed in Health Connect. */
data class AppMetadata(val packageName: String, val appName: String, val icon: Drawable?) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false

        other as AppMetadata

        if (packageName != other.packageName) return false
        if (appName != other.appName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packageName.hashCode()
        result = 31 * result + appName.hashCode()
        return result
    }
}
