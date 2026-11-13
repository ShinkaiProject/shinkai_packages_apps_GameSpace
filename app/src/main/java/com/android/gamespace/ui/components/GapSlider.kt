package com.android.gamespace.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [GapSlider]. */
class GapSliderStyle(
    val trackHeight: Dp = 16.dp,
    val handleWidth: Dp = 4.dp,
    val handleHeight: Dp = 44.dp,
    /** Between the handle and each side of the track. */
    val gap: Dp = 4.dp,
    /** The ends of the track are round; the sides next to the handle only a little. */
    val outerCorner: Dp = 8.dp,
    val innerCorner: Dp = 2.dp,
    /** The dot at the end of the track, or null for none: its size and its distance from the end. */
    val stopSize: Dp? = 4.dp,
    val stopInset: Dp = 8.dp,
) {
    companion object {
        /** The sliders of the preset editor. */
        val Editor = GapSliderStyle()

        /** The brightness and volume sliders of the panel. */
        val Panel = GapSliderStyle(
            trackHeight = 16.dp,
            handleHeight = 28.dp,
            gap = 3.dp,
            stopSize = null,
        )

        /** The sliders that stand in a row of a settings list. */
        val Setting = GapSliderStyle(
            trackHeight = 16.dp,
            handleHeight = 36.dp,
            gap = 3.dp,
            stopSize = null,
        )
    }
}

/**
 * A slider like the expressive Material 3 one: a thick track that is cut in two around the handle,
 * the side behind the handle in [activeColor], the side ahead of it in [inactiveColor]. The only
 * feedback while it is dragged is the handle itself: no ripple, no halo.
 *
 * [fraction] is 0..1. The caller maps it to its own range and rounds it if it has steps.
 */
@Composable
fun GapSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFractionChangeFinished: () -> Unit = {},
    style: GapSliderStyle = GapSliderStyle.Editor,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    handleColor: Color = activeColor,
) {
    val onChange by rememberUpdatedState(onFractionChange)
    val onFinished by rememberUpdatedState(onFractionChangeFinished)
    val shown = fraction.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(style.handleHeight)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { target ->
                    if (enabled) onChange(target.coerceIn(0f, 1f))
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val handleWidth = style.handleWidth.toPx()
                // The middle of the handle goes from half its width to half its width short of
                // the far end, so that it is never cut off.
                fun toFraction(x: Float): Float {
                    val travel = size.width - handleWidth
                    return if (travel <= 0f) 0f else ((x - handleWidth / 2f) / travel).coerceIn(0f, 1f)
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    }
                    if (slop != null) {
                        onChange(toFraction(slop.position.x))
                        horizontalDrag(slop.id) { change ->
                            onChange(toFraction(change.position.x))
                            change.consume()
                        }
                        onFinished()
                    } else {
                        // Let go before moving: a tap on the track.
                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (up != null && !up.pressed) {
                            onChange(toFraction(down.position.x))
                            onFinished()
                        }
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawGapSlider(shown, style, activeColor, inactiveColor, handleColor)
        }
    }
}

private fun DrawScope.drawGapSlider(
    fraction: Float,
    style: GapSliderStyle,
    activeColor: Color,
    inactiveColor: Color,
    handleColor: Color,
) {
    val width = size.width
    val height = size.height
    val trackHeight = style.trackHeight.toPx()
    val handleWidth = style.handleWidth.toPx()
    val handleHeight = style.handleHeight.toPx()
    val gap = style.gap.toPx()
    val top = (height - trackHeight) / 2f
    val bottom = top + trackHeight
    val outer = minOf(style.outerCorner.toPx(), trackHeight / 2f)
    val inner = minOf(style.innerCorner.toPx(), outer)

    val handleX = handleWidth / 2f + fraction * (width - handleWidth)
    val activeEnd = handleX - handleWidth / 2f - gap
    val inactiveStart = handleX + handleWidth / 2f + gap

    if (activeEnd > 0f) {
        drawSegment(0f, top, activeEnd, bottom, outer, inner, activeColor)
    }
    if (inactiveStart < width) {
        drawSegment(inactiveStart, top, width, bottom, inner, outer, inactiveColor)
        val stop = style.stopSize
        if (stop != null) {
            val radius = stop.toPx() / 2f
            val stopX = width - style.stopInset.toPx()
            // The dot marks the end of the track. It goes when the handle gets to it.
            if (stopX - radius >= inactiveStart) {
                drawCircle(activeColor, radius, Offset(stopX, height / 2f))
            }
        }
    }
    drawRoundRect(
        color = handleColor,
        topLeft = Offset(handleX - handleWidth / 2f, (height - handleHeight) / 2f),
        size = Size(handleWidth, handleHeight),
        cornerRadius = CornerRadius(handleWidth / 2f),
    )
}

private fun DrawScope.drawSegment(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    leftCorner: Float,
    rightCorner: Float,
    color: Color,
) {
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = CornerRadius(leftCorner),
                topRightCornerRadius = CornerRadius(rightCorner),
                bottomRightCornerRadius = CornerRadius(rightCorner),
                bottomLeftCornerRadius = CornerRadius(leftCorner),
            )
        )
    }
    drawPath(path, color)
}
