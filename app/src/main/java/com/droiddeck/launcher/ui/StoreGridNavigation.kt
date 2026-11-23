package com.droiddeck.launcher.ui

import android.view.KeyEvent
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun storeGridNeighbor(index: Int, count: Int, columns: Int, direction: Int, split: Int = 0): Int? {
    if (index !in 0 until count || columns < 1 || direction !in listOf(-1, 1)) return null
    if (split !in 1 until count) return (index + columns * direction).takeIf { it in 0 until count }
    val inFirst = index < split
    val start = if (inFirst) 0 else split
    val end = if (inFirst) split else count
    val local = index - start
    val within = start + local + columns * direction
    if (within in start until end) return within
    val column = local % columns
    return when {
        inFirst && direction > 0 -> split + minOf(column, count - split - 1)
        !inFirst && direction < 0 && local < columns -> ((split - 1) / columns) * columns + column.coerceAtMost((split - 1) % columns)
        else -> null
    }
}

/** The item up or down from [index]; down from the row above a shorter last row lands on its last item. */
internal fun gridStep(index: Int, count: Int, columns: Int, direction: Int): Int? {
    storeGridNeighbor(index, count, columns, direction)?.let { return it }
    return if (direction > 0 && columns > 0 && index in 0 until count && index / columns < (count - 1) / columns) count - 1 else null
}

/**
 * Up and down between the items of a plain lazy grid. Compose's own search lands in the first column, or nowhere,
 * once the next row has to be laid out first.
 */
internal class LazyGridNav(private val state: LazyGridState, private val scope: CoroutineScope, private val gapPx: Float) {
    internal val items = HashMap<Int, FocusRequester>()
    internal var focused = -1

    internal fun step(index: Int, count: Int, direction: Int): Int? {
        val columns = (state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
        return gridStep(index, count, columns, direction)
    }

    internal fun move(from: Int, to: Int, direction: Int) {
        val shown = state.layoutInfo.visibleItemsInfo.any { it.index == to }
        val direct = items[to]
        if (shown && direct != null && runCatching { direct.requestFocus() }.isSuccess) return
        scope.launch {
            launch { state.revealRow(from, to, direction, gapPx) }
            focusWithinFrames({ focused == to }) { items[to] ?: FocusRequester.Default }
        }
    }
}

@Composable
internal fun rememberLazyGridNav(state: LazyGridState, gap: Dp): LazyGridNav {
    val scope = rememberCoroutineScope()
    val gapPx = with(LocalDensity.current) { gap.toPx() }
    return remember(state, scope, gapPx) { LazyGridNav(state, scope, gapPx) }
}

/** Put before the item's focus target; [count] is the number of items in the grid. */
@Composable
internal fun Modifier.lazyGridItem(nav: LazyGridNav, index: Int, count: Int, requester: FocusRequester): Modifier {
    DisposableEffect(nav, index, requester) {
        nav.items[index] = requester
        onDispose { if (nav.items[index] === requester) nav.items.remove(index) }
    }
    return focusRequester(requester)
        .onFocusChanged { if (it.isFocused) nav.focused = index else if (nav.focused == index) nav.focused = -1 }
        .onPreviewKeyEvent { event ->
            val key = event.nativeKeyEvent
            val direction = when (key.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> 1
                KeyEvent.KEYCODE_DPAD_UP -> -1
                else -> return@onPreviewKeyEvent false
            }
            val target = nav.step(index, count, direction) ?: return@onPreviewKeyEvent false
            if (key.action == KeyEvent.ACTION_DOWN) nav.move(index, target, direction)
            true
        }
}
