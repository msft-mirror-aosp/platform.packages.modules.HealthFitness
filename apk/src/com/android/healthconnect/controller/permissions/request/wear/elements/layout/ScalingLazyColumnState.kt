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
 */

@file:Suppress("ObjectLiteralToLambda")
@file:OptIn(ExperimentalWearFoundationApi::class)

package com.android.healthconnect.controller.permissions.request.wear.elements.layout

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults as WearScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingParams
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import com.android.healthconnect.controller.permissions.request.wear.elements.layout.ScalingLazyColumnDefaults.responsiveScalingParams
import com.android.healthconnect.controller.permissions.request.wear.elements.layout.ScalingLazyColumnState.RotaryMode
import com.android.healthconnect.controller.permissions.request.wear.elements.rotaryinput.rememberDisabledHaptic
import com.android.healthconnect.controller.permissions.request.wear.elements.rotaryinput.rememberRotaryHapticHandler
import com.android.healthconnect.controller.permissions.request.wear.elements.rotaryinput.rotaryWithScroll
import com.android.healthconnect.controller.permissions.request.wear.elements.rotaryinput.rotaryWithSnap
import com.android.healthconnect.controller.permissions.request.wear.elements.rotaryinput.toRotaryScrollAdapter

// This file is a copy of ScalingLazyColumnState.kt from Horologist (go/horologist),
// remove it once after wear compose supports large screen dialogs.

/**
 * A Config and State object wrapping up all configuration for a [ScalingLazyColumn]. This allows
 * defaults such as [ScalingLazyColumnDefaults.responsive].
 */
class ScalingLazyColumnState(
    val initialScrollPosition: ScrollPosition = ScrollPosition(1, 0),
    val autoCentering: AutoCenteringParams? =
        AutoCenteringParams(
            initialScrollPosition.index,
            initialScrollPosition.offsetPx,
        ),
    val anchorType: ScalingLazyListAnchorType = ScalingLazyListAnchorType.ItemCenter,
    val contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp),
    val rotaryMode: RotaryMode? = RotaryMode.Scroll,
    val reverseLayout: Boolean = false,
    val verticalArrangement: Arrangement.Vertical =
        Arrangement.spacedBy(
            space = 4.dp,
            alignment = if (!reverseLayout) Alignment.Top else Alignment.Bottom,
        ),
    val horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    val flingBehavior: FlingBehavior? = null,
    val userScrollEnabled: Boolean = true,
    val scalingParams: ScalingParams = WearScalingLazyColumnDefaults.scalingParams(),
    val hapticsEnabled: Boolean = true,
) : ScrollableState {
    private var _state: ScalingLazyListState? = null
    var state: ScalingLazyListState
        get() {
            if (_state == null) {
                _state =
                    ScalingLazyListState(
                        initialScrollPosition.index,
                        initialScrollPosition.offsetPx,
                    )
            }
            return _state!!
        }
        set(value) {
            _state = value
        }

    override val canScrollBackward: Boolean
        get() = state.canScrollBackward

    override val canScrollForward: Boolean
        get() = state.canScrollForward

    override val isScrollInProgress: Boolean
        get() = state.isScrollInProgress

    override fun dispatchRawDelta(delta: Float): Float = state.dispatchRawDelta(delta)

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) {
        state.scroll(scrollPriority, block)
    }

    sealed interface RotaryMode {
        data object Snap : RotaryMode

        data object Scroll : RotaryMode
    }

    data class ScrollPosition(
        val index: Int,
        val offsetPx: Int,
    )

    fun interface Factory {
        @Composable fun create(): ScalingLazyColumnState
    }
}

// @Deprecated("Replaced by rememberResponsiveColumnState")
@Composable
fun rememberColumnState(
    factory: ScalingLazyColumnState.Factory = ScalingLazyColumnDefaults.responsive(),
): ScalingLazyColumnState {
    val columnState = factory.create()

    columnState.state = rememberSaveable(saver = ScalingLazyListState.Saver) { columnState.state }

    return columnState
}

@Composable
fun rememberResponsiveColumnState(
    contentPadding: @Composable () -> PaddingValues =
        ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Unspecified,
            last = ScalingLazyColumnDefaults.ItemType.Unspecified,
        ),
    verticalArrangement: Arrangement.Vertical =
        Arrangement.spacedBy(
            space = 4.dp,
            alignment = Alignment.Top,
        ),
    rotaryMode: RotaryMode? = RotaryMode.Scroll,
    hapticsEnabled: Boolean = true,
    reverseLayout: Boolean = false,
    userScrollEnabled: Boolean = true,
): ScalingLazyColumnState {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenHeightDp = configuration.screenHeightDp.toFloat()

    val scalingParams = responsiveScalingParams(screenWidthDp)

    val contentPaddingCalculated = contentPadding()

    val screenHeightPx = with(density) { screenHeightDp.dp.roundToPx() }
    val topPaddingPx = with(density) { contentPaddingCalculated.calculateTopPadding().roundToPx() }
    val topScreenOffsetPx = screenHeightPx / 2 - topPaddingPx

    val initialScrollPosition =
        ScalingLazyColumnState.ScrollPosition(
            index = 0,
            offsetPx = topScreenOffsetPx,
        )

    val columnState =
        ScalingLazyColumnState(
            initialScrollPosition = initialScrollPosition,
            autoCentering = null,
            anchorType = ScalingLazyListAnchorType.ItemStart,
            rotaryMode = rotaryMode,
            verticalArrangement = verticalArrangement,
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = contentPaddingCalculated,
            scalingParams = scalingParams,
            hapticsEnabled = hapticsEnabled,
            reverseLayout = reverseLayout,
            userScrollEnabled = userScrollEnabled,
        )

    columnState.state = rememberSaveable(saver = ScalingLazyListState.Saver) { columnState.state }

    return columnState
}

@Composable
fun ScalingLazyColumn(
    columnState: ScalingLazyColumnState,
    modifier: Modifier = Modifier,
    content: ScalingLazyListScope.() -> Unit,
) {
    val focusRequester = rememberActiveFocusRequester()

    val rotaryHaptics =
        if (columnState.hapticsEnabled) {
            rememberRotaryHapticHandler(columnState.state)
        } else {
            rememberDisabledHaptic()
        }

    val modifierWithRotary =
        when (columnState.rotaryMode) {
            RotaryMode.Snap ->
                modifier.rotaryWithSnap(
                    focusRequester = focusRequester,
                    rotaryScrollAdapter = columnState.state.toRotaryScrollAdapter(),
                    reverseDirection = columnState.reverseLayout,
                    rotaryHaptics = rotaryHaptics,
                )
            RotaryMode.Scroll ->
                modifier.rotaryWithScroll(
                    focusRequester = focusRequester,
                    scrollableState = columnState.state,
                    reverseDirection = columnState.reverseLayout,
                    rotaryHaptics = rotaryHaptics,
                )
            else -> modifier
        }

    ScalingLazyColumn(
        modifier = modifierWithRotary.fillMaxSize(),
        state = columnState.state,
        contentPadding = columnState.contentPadding,
        reverseLayout = columnState.reverseLayout,
        verticalArrangement = columnState.verticalArrangement,
        horizontalAlignment = columnState.horizontalAlignment,
        flingBehavior = columnState.flingBehavior ?: ScrollableDefaults.flingBehavior(),
        userScrollEnabled = columnState.userScrollEnabled,
        scalingParams = columnState.scalingParams,
        anchorType = columnState.anchorType,
        autoCentering = columnState.autoCentering,
        content = content,
    )
}
