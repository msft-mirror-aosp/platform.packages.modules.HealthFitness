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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.android.healthconnect.controller.permissions.request.wear.elements.layout.ScalingLazyColumnDefaults
import com.android.healthconnect.controller.permissions.request.wear.elements.layout.ScalingLazyColumnState
import com.android.healthconnect.controller.permissions.request.wear.elements.layout.rememberColumnState

/**
 * This component is an alternative to [AlertContent], providing the following:
 * - a convenient way of passing a title and a message;
 * - additional content can be specified between the message and the buttons
 * - default positive and negative buttons;
 * - wrapped in a [Dialog];
 */
@Composable
fun AlertDialog(
    message: String,
    iconRes: Int? = null,
    okButtonIcon: Any = Icons.Default.Check,
    cancelButtonIcon: Any = Icons.Default.Close,
    onCancelButtonClick: () -> Unit,
    onOKButtonClick: () -> Unit,
    showDialog: Boolean,
    scalingLazyListState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    title: String? = null,
    okButtonContentDescription: String = stringResource(android.R.string.ok),
    cancelButtonContentDescription: String = stringResource(android.R.string.cancel)
) {
    val focusManager = LocalFocusManager.current
    Dialog(
        showDialog = showDialog,
        onDismissRequest = {
            focusManager.clearFocus()
            onCancelButtonClick()
        },
        scrollState = scalingLazyListState,
        modifier = modifier
    ) {
        AlertContent(
            title = title,
            icon = { AlertIcon(iconRes) },
            message = message,
            okButtonIcon = okButtonIcon,
            cancelButtonIcon = cancelButtonIcon,
            onCancel = onCancelButtonClick,
            onOk = onOKButtonClick,
            okButtonContentDescription = okButtonContentDescription,
            cancelButtonContentDescription = cancelButtonContentDescription
        )
    }
}

/**
 * This component is an alternative to [Alert], providing the following:
 * - a convenient way of passing a title and a message;
 * - default one button;
 * - wrapped in a [Dialog];
 */
@Composable
fun SingleButtonAlertDialog(
    message: String,
    iconRes: Int? = null,
    okButtonIcon: Any = Icons.Default.Check,
    onButtonClick: () -> Unit,
    showDialog: Boolean,
    scalingLazyListState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    title: String? = null,
    buttonContentDescription: String = stringResource(android.R.string.ok)
) {
    Dialog(
        showDialog = showDialog,
        onDismissRequest = {},
        scrollState = scalingLazyListState,
        modifier = modifier
    ) {
        AlertContent(
            title = title,
            icon = { AlertIcon(iconRes) },
            message = message,
            okButtonIcon = okButtonIcon,
            onOk = onButtonClick,
            okButtonContentDescription = buttonContentDescription
        )
    }
}

@Composable
fun AlertContent(
    onCancel: (() -> Unit)? = null,
    onOk: (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    message: String? = null,
    okButtonIcon: Any = Icons.Default.Check,
    cancelButtonIcon: Any = Icons.Default.Close,
    okButtonContentDescription: String = stringResource(android.R.string.ok),
    cancelButtonContentDescription: String = stringResource(android.R.string.cancel),
    state: ScalingLazyColumnState =
        rememberColumnState(
            ScalingLazyColumnDefaults.responsive(
                additionalPaddingAtBottom = 0.dp,
            ),
        ),
    showPositionIndicator: Boolean = true,
    content: (ScalingLazyListScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val maxScreenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    ResponsiveDialogContent(
        icon = icon,
        title =
            title?.let {
                {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = it,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        message =
            message?.let {
                {
                    // Should message be start or center aligned?
                    val textMeasurer = rememberTextMeasurer()
                    val textStyle = LocalTextStyle.current
                    val totalPaddingPercentage =
                        globalHorizontalPadding + messageExtraHorizontalPadding
                    val lineCount =
                        remember(it, density, textStyle, textMeasurer) {
                            textMeasurer
                                .measure(
                                    text = it,
                                    style = textStyle,
                                    constraints =
                                        Constraints(
                                            // Available width is reduced by responsive dialog
                                            // horizontal
                                            // padding.
                                            maxWidth =
                                                (maxScreenWidthPx *
                                                        (1f - totalPaddingPercentage * 2f / 100f))
                                                    .toInt(),
                                        ),
                                )
                                .lineCount
                        }
                    val textAlign = if (lineCount <= 3) TextAlign.Center else TextAlign.Start
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = it,
                        color = MaterialTheme.colors.onBackground,
                        textAlign = textAlign,
                    )
                }
            },
        content = content,
        onOk = onOk,
        onCancel = onCancel,
        okButtonIcon = okButtonIcon,
        cancelButtonIcon = cancelButtonIcon,
        okButtonContentDescription = okButtonContentDescription,
        cancelButtonContentDescription = cancelButtonContentDescription,
        state = state,
        showPositionIndicator = showPositionIndicator,
    )
}

@Composable
private fun AlertIcon(iconRes: Int?) =
    if (iconRes != null && iconRes != 0) {
        Icon(painter = painterResource(iconRes), contentDescription = null)
    } else {
        null
    }
