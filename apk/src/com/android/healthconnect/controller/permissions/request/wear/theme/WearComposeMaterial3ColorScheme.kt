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
package com.android.healthconnect.controller.permissions.request.wear.theme

import android.content.Context
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

/**
 * Creates a dynamic color maps that can be overlaid. In wear we only support dark theme for the
 * time being. If the device supports dynamic color generation these resources are updated with the
 * generated colors
 */
internal object WearComposeMaterial3ColorScheme {

    private fun Color.updatedColor(context: Context, @ColorRes colorRes: Int): Color {
        return ResourceHelper.getColor(context, colorRes) ?: this
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun dynamicColorScheme(context: Context): ColorScheme {
        val defaultColorScheme = ColorScheme()
        return ColorScheme(
            primary =
                defaultColorScheme.primary.updatedColor(
                    context,
                    android.R.color.system_primary_fixed,
                ),
            primaryDim =
                defaultColorScheme.primaryDim.updatedColor(
                    context,
                    android.R.color.system_primary_fixed_dim,
                ),
            primaryContainer =
                defaultColorScheme.primaryContainer.updatedColor(
                    context,
                    android.R.color.system_primary_container_dark,
                ),
            onPrimary =
                defaultColorScheme.onPrimary.updatedColor(
                    context,
                    android.R.color.system_on_primary_fixed,
                ),
            onPrimaryContainer =
                defaultColorScheme.onPrimaryContainer.updatedColor(
                    context,
                    android.R.color.system_on_primary_container_dark,
                ),
            secondary =
                defaultColorScheme.secondary.updatedColor(
                    context,
                    android.R.color.system_secondary_fixed,
                ),
            secondaryDim =
                defaultColorScheme.secondaryDim.updatedColor(
                    context,
                    android.R.color.system_secondary_fixed_dim,
                ),
            secondaryContainer =
                defaultColorScheme.secondaryContainer.updatedColor(
                    context,
                    android.R.color.system_secondary_container_dark,
                ),
            onSecondary =
                defaultColorScheme.onSecondary.updatedColor(
                    context,
                    android.R.color.system_on_secondary_fixed,
                ),
            onSecondaryContainer =
                defaultColorScheme.onSecondaryContainer.updatedColor(
                    context,
                    android.R.color.system_on_secondary_container_dark,
                ),
            tertiary =
                defaultColorScheme.tertiary.updatedColor(
                    context,
                    android.R.color.system_tertiary_fixed,
                ),
            tertiaryDim =
                defaultColorScheme.tertiaryDim.updatedColor(
                    context,
                    android.R.color.system_tertiary_fixed_dim,
                ),
            tertiaryContainer =
                defaultColorScheme.tertiaryContainer.updatedColor(
                    context,
                    android.R.color.system_tertiary_container_dark,
                ),
            onTertiary =
                defaultColorScheme.onTertiary.updatedColor(
                    context,
                    android.R.color.system_on_tertiary_fixed,
                ),
            onTertiaryContainer =
                defaultColorScheme.onTertiaryContainer.updatedColor(
                    context,
                    android.R.color.system_on_tertiary_container_dark,
                ),
            surfaceContainerLow =
                defaultColorScheme.surfaceContainerLow.updatedColor(
                    context,
                    android.R.color.system_surface_container_low_dark,
                ),
            surfaceContainer =
                defaultColorScheme.surfaceContainer.updatedColor(
                    context,
                    android.R.color.system_surface_container_dark,
                ),
            surfaceContainerHigh =
                defaultColorScheme.surfaceContainerHigh.updatedColor(
                    context,
                    android.R.color.system_surface_container_high_dark,
                ),
            onSurface =
                defaultColorScheme.onSurface.updatedColor(
                    context,
                    android.R.color.system_on_surface_dark,
                ),
            onSurfaceVariant =
                defaultColorScheme.onSurfaceVariant.updatedColor(
                    context,
                    android.R.color.system_on_surface_variant_dark,
                ),
            outline =
                defaultColorScheme.outline.updatedColor(
                    context,
                    android.R.color.system_outline_dark,
                ),
            outlineVariant =
                defaultColorScheme.outlineVariant.updatedColor(
                    context,
                    android.R.color.system_outline_variant_dark,
                ),
            background =
                defaultColorScheme.background.updatedColor(
                    context,
                    android.R.color.system_background_dark,
                ),
            onBackground =
                defaultColorScheme.onBackground.updatedColor(
                    context,
                    android.R.color.system_on_background_dark,
                ),
            error =
                defaultColorScheme.error.updatedColor(context, android.R.color.system_error_dark),
            onError =
                defaultColorScheme.onError.updatedColor(
                    context,
                    android.R.color.system_on_error_dark,
                ),
            errorContainer =
                defaultColorScheme.errorContainer.updatedColor(
                    context,
                    android.R.color.system_error_container_dark,
                ),
            onErrorContainer =
                defaultColorScheme.onErrorContainer.updatedColor(
                    context,
                    android.R.color.system_on_error_container_dark,
                ),
        )
    }
}
