/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.healthconnect.controller.utils

import android.content.Context
import android.widget.Toast
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Wrapper for {@link android.widget.Toast} to aid with testing */
interface ToastManager {
    fun showToast(context: Context, messageResId: Int, duration: Int = Toast.LENGTH_LONG)
}

class ToastManagerImpl @Inject constructor() : ToastManager {

    companion object {
        const val TAG: String = "ToastManager"
    }

    private var toast: Toast? = null

    override fun showToast(context: Context, messageResId: Int, duration: Int) {
        toast?.cancel()
        toast = Toast.makeText(context, messageResId, duration).apply { show() }
    }
}

@Module
@InstallIn(SingletonComponent::class)
class ToastManagerModule {
    @Provides
    fun provideToastManager(): ToastManager {
        return ToastManagerImpl()
    }
}
