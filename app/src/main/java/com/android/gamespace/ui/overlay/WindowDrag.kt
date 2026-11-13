package com.android.gamespace.ui.overlay

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import kotlin.math.hypot

private class DragTouch {
    var startX = 0
    var startY = 0
    var initialX = 0
    var initialY = 0
    var moving = false
    var longPressed = false
    var onLongPress: Runnable = Runnable {}
}

/**
 * Lets what it is put on move a window with a finger, and tells a tap from a drag.
 *
 * A window moves under the finger, so the position that the finger has in the window is no use
 * for this; the position on the screen is what it follows (rawX and rawY of the touch), the same
 * as the touch listener of the bar always did.
 *
 *  - A touch that stays within the touch slop and is let go is a click: [onClick].
 *  - Beyond it, the window follows the finger: [onMove] gets the place it should be at, counted
 *    from [initialPosition] as it was when the touch began; [onComplete] is called when it is let
 *    go.
 *  - Held for the long-press time without moving, it is a long press: nothing follows from it, and
 *    the touch is not a click or a drag any more.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.windowDrag(
    initialPosition: () -> IntOffset,
    onMove: (x: Int, y: Int) -> Unit,
    onComplete: () -> Unit,
    onClick: () -> Unit,
): Modifier {
    val context = LocalContext.current
    val touchSlop = remember { ViewConfiguration.get(context).scaledTouchSlop }
    val longPressTimeout = remember { ViewConfiguration.getLongPressTimeout().toLong() }
    // On the main thread, and nothing to start for it: no thread for each touch.
    val handler = remember { Handler(Looper.getMainLooper()) }
    val touch = remember {
        DragTouch().also { state ->
            state.onLongPress = Runnable {
                if (!state.moving && !state.longPressed) state.longPressed = true
            }
        }
    }
    val initial by rememberUpdatedState(initialPosition)
    val move by rememberUpdatedState(onMove)
    val complete by rememberUpdatedState(onComplete)
    val click by rememberUpdatedState(onClick)
    DisposableEffect(Unit) { onDispose { handler.removeCallbacks(touch.onLongPress) } }

    return pointerInteropFilter { event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touch.startX = event.rawX.toInt()
                touch.startY = event.rawY.toInt()
                val position = initial()
                touch.initialX = position.x
                touch.initialY = position.y
                touch.moving = false
                touch.longPressed = false
                handler.removeCallbacks(touch.onLongPress)
                handler.postDelayed(touch.onLongPress, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touch.longPressed) {
                    val deltaX = event.rawX - touch.startX
                    val deltaY = event.rawY - touch.startY
                    if (touch.moving || hypot(deltaX, deltaY) > touchSlop) {
                        handler.removeCallbacks(touch.onLongPress)
                        move(touch.initialX + deltaX.toInt(), touch.initialY + deltaY.toInt())
                        touch.moving = true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(touch.onLongPress)
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(touch.onLongPress)
                if (!touch.moving && !touch.longPressed) click()
                if (touch.moving && !touch.longPressed) complete()
            }
        }
        true
    }
}
