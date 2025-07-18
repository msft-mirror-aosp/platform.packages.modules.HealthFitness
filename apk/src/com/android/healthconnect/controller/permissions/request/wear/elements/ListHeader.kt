/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.healthconnect.controller.permissions.request.wear.elements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.MaterialTheme

/**
 * A slot based composable for creating a list header item. [ListHeader]s are typically expected to
 * be a few words of text on a single line. The contents will be start and end padded.
 *
 * @param modifier The modifier for the [ListHeader].
 * @param backgroundColor The background color to apply - typically Color.Transparent
 * @param contentColor The color to apply to content.
 * @param content Slot for [ListHeader] content, expected to be a single line of text.
 */

// Styling updated to match with wear material title
// Ref:
// https://source.corp.google.com/h/googleplex-android/platform/superproject/main/+/main:vendor/google_clockwork_partners/libs/ClockworkCommonLibs/common/wearable/wearmaterial/preference/res/layout/wear_title_preference.xml;l=1;drc=8ebd53cbba588e8e9aa964522fb05f4f5224609e;bpv=1;bpt=0
@Composable
fun ListHeader(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colors.onBackground,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier =
            modifier.wrapContentSize().background(backgroundColor).semantics(
                mergeDescendants = true
            ) {
                heading()
            }
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides
                MaterialTheme.typography.title3.copy(
                    fontWeight = FontWeight.W600,
                    hyphens = Hyphens.Auto
                ),
        ) {
            content()
        }
    }
}

/**
 * A two slot based composable for creating a list subheader item. [ListSubheader]s offer slots for
 * an icon and for a text label. The contents will be start and end padded.
 *
 * @param modifier The modifier for the [ListSubheader].
 * @param backgroundColor The background color to apply - typically Color.Transparent
 * @param contentColor The color to apply to content.
 * @param icon A slot for providing icon to the [ListSubheader].
 * @param label A slot for providing label to the [ListSubheader].
 */
@Composable
fun ListSubheader(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colors.onBackground,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    label: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier =
            modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth()
                .wrapContentSize(align = Alignment.CenterStart)
                .background(backgroundColor)
                .semantics(mergeDescendants = true) { heading() }
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides MaterialTheme.typography.caption1,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier.wrapContentSize(align = Alignment.CenterStart),
                    content = icon
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            label()
        }
    }
}
