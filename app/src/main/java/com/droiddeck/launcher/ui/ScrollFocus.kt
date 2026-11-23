package com.droiddeck.launcher.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Up from the topmost visible entry of a scrolled column reaches the entry above it. Without this, the focus search
 * prefers a header that is nearer on screen than an entry scrolled out of view.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.focusStaysInScroll(state: ScrollState): Modifier {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val step = with(LocalDensity.current) { 160.dp.toPx() }
    return focusProperties {
        exit = { direction ->
            if (direction == FocusDirection.Up && state.value > 0) {
                scope.launch {
                    state.animateScrollBy(-step)
                    androidx.compose.runtime.withFrameNanos { }
                    focus.moveFocus(FocusDirection.Up)
                }
                FocusRequester.Cancel
            } else FocusRequester.Default
        }
    }.focusGroup()
}
