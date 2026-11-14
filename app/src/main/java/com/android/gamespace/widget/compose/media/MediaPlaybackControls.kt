package com.android.gamespace.widget.compose.media

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.widget.NowPlayingState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Wave geometry (unchanged from the previous implementation, raw px).
private const val WaveLengthPx = 64f
private const val WaveAmplitudePx = 5f
private const val WaveRampPx = 32f
private const val WaveStrokePx = 5f
private const val TrackStrokePx = 3f

// Stock Android (SquigglyProgress) scrolls the wave 8dp/s over a 20dp wavelength,
// i.e. 0.4 wavelengths per second. Expressed per wavelength so it is density independent.
private const val WavePhaseCyclesPerSecond = 0.4f

// Same curves SquigglyProgress uses when the wave grows in / flattens out.
private val WaveEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val WaveExitEasing = CubicBezierEasing(0f, 0f, 0f, 1f)

private val TwoPi = (2.0 * PI).toFloat()

// A report further than this from where the clock expects it is a real seek / track change
// and is followed immediately. Anything smaller is jitter and is only partly absorbed.
private const val SeekResyncThresholdMs = 250f
private const val SeekCorrectionGain = 0.25f

/** Playback position that advances with the frame clock between controller reports. */
private class SeekClock {
    var anchorPositionMs = 0f
    var anchorFrameNanos = -1L

    fun positionAt(frameNanos: Long): Float =
        anchorPositionMs + (frameNanos - anchorFrameNanos) / 1_000_000f
}

@Composable
fun MediaPlaybackControls(
    state: NowPlayingState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    onLike: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlIcon(
            iconRes = R.drawable.ic_media_prev,
            contentDescription = null,
            onClick = onPrevious,
        )
        SquigglySeekBar(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            isPlaying = state.isPlaying,
            onSeekStart = onSeekStart,
            onSeek = onSeek,
            onSeekEnd = onSeekEnd,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        ControlIcon(
            iconRes = R.drawable.ic_media_next,
            contentDescription = null,
            onClick = onNext,
        )
        if (state.likeVisible) {
            ControlIcon(
                iconRes = if (state.isLiked) {
                    R.drawable.ic_media_like_filled
                } else {
                    R.drawable.ic_media_like_border
                },
                contentDescription = null,
                onClick = onLike,
            )
        }
        if (state.repeatVisible) {
            ControlIcon(
                iconRes = R.drawable.ic_media_repeat,
                contentDescription = null,
                onClick = onRepeat,
                alpha = if (state.isRepeatOn) 1f else 0.5f,
            )
        }
    }
}

@Composable
private fun ControlIcon(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    alpha: Float = 1f,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = alpha),
            modifier = Modifier
                .size(16.dp)
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                },
        )
    }
}

@Composable
private fun SquigglySeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbColor = MaterialTheme.colorScheme.primary
    var isSeeking by remember { mutableStateOf(false) }

    // Wave grows in / flattens out like SquigglyProgress (heightFraction).
    val waveAmount by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 800, delayMillis = 60, easing = WaveEnterEasing)
        } else {
            tween(durationMillis = 550, easing = WaveExitEasing)
        },
        label = "media-wave-amount",
    )

    // progress and wavePhase are written once per frame and only read while drawing, so the
    // bar never recomposes for animation.
    val initialProgress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress = remember { mutableFloatStateOf(initialProgress) }
    // Wave offset as a fraction of one wavelength. The wave is periodic, so wrapping at 1
    // is seamless. It only advances while playing and simply freezes on pause.
    val wavePhase = remember { mutableFloatStateOf(0f) }
    val wavePath = remember { Path() }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                // Clamp so a long gap (window hidden, then shown) cannot cause a visible jump.
                val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastFrameNanos = now
                wavePhase.floatValue =
                    (wavePhase.floatValue + dt * WavePhaseCyclesPerSecond) % 1f
            }
        }
    }

    // The controller only reports the position every 500 ms. Instead of tweening to each
    // report (which makes the speed lurch whenever a report is early or late), the progress
    // follows a clock that runs with the frame clock and is only nudged by each report.
    val clock = remember { SeekClock() }
    LaunchedEffect(positionMs, durationMs, isPlaying, isSeeking) {
        val reportedMs = positionMs.toFloat()
        val baseProgress = if (durationMs > 0L) {
            (reportedMs / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        if (isPlaying && !isSeeking && durationMs > 0L) {
            var firstFrame = true
            while (true) {
                withFrameNanos { now ->
                    if (firstFrame) {
                        firstFrame = false
                        val running = clock.anchorFrameNanos >= 0L
                        val expectedMs = if (running) clock.positionAt(now) else reportedMs
                        val errorMs = reportedMs - expectedMs
                        // Small error: just a bit of jitter in when the report was produced,
                        // absorb a fraction of it. Large error: a real seek or track change.
                        clock.anchorPositionMs =
                            if (!running || abs(errorMs) > SeekResyncThresholdMs) {
                                reportedMs
                            } else {
                                expectedMs + errorMs * SeekCorrectionGain
                            }
                        clock.anchorFrameNanos = now
                    }
                    progress.floatValue =
                        (clock.positionAt(now) / durationMs.toFloat()).coerceIn(0f, 1f)
                }
            }
        }
        // Not advancing: the next time playback starts it must re-anchor on the report.
        clock.anchorFrameNanos = -1L
        if (isSeeking || durationMs <= 0L || baseProgress - progress.floatValue < -0.02f) {
            progress.floatValue = baseProgress
        } else {
            animate(
                initialValue = progress.floatValue,
                targetValue = baseProgress,
                animationSpec = tween(durationMillis = 200, easing = LinearEasing),
            ) { value, _ -> progress.floatValue = value }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0L) {
                        onSeekStart()
                        onSeek(((offset.x / size.width) * durationMs).toLong())
                        onSeekEnd()
                    }
                }
            }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = {
                        if (durationMs > 0L) {
                            isSeeking = true
                            onSeekStart()
                        }
                    },
                    onDragEnd = {
                        isSeeking = false
                        onSeekEnd()
                    },
                    onDragCancel = {
                        isSeeking = false
                        onSeekEnd()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (durationMs > 0L) {
                            onSeek(((change.position.x / size.width) * durationMs).toLong())
                        }
                    },
                )
            },
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val activeWidth = width * progress.floatValue
        if (activeWidth > 0f) {
            val phase = wavePhase.floatValue
            val step = WaveLengthPx / 24f
            wavePath.rewind()
            var x = 0f
            while (true) {
                val startDamping = (x / WaveRampPx).coerceIn(0f, 1f)
                val endDamping = ((activeWidth - x) / WaveRampPx).coerceIn(0f, 1f)
                val amplitude = WaveAmplitudePx * minOf(startDamping, endDamping) * waveAmount
                // phase increases -> the wave scrolls towards the start, like stock Android.
                val y = centerY + sin((x / WaveLengthPx + phase) * TwoPi) * amplitude
                if (x == 0f) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                if (x >= activeWidth) break
                // Always end exactly on activeWidth so the tip tracks the thumb without
                // snapping in whole steps.
                x = minOf(x + step, activeWidth)
            }
            drawPath(
                path = wavePath,
                color = Color.White,
                style = Stroke(width = WaveStrokePx, cap = StrokeCap.Round),
            )
        }
        if (activeWidth < width) {
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = Offset(activeWidth, centerY),
                end = Offset(width, centerY),
                strokeWidth = TrackStrokePx,
                cap = StrokeCap.Round,
            )
        }
        val thumbWidth = 4.dp.toPx()
        val thumbHeight = 14.dp.toPx()
        val thumbHalfWidth = thumbWidth / 2f
        val thumbX = activeWidth.coerceIn(
            minimumValue = thumbHalfWidth,
            maximumValue = (width - thumbHalfWidth).coerceAtLeast(thumbHalfWidth),
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbX - thumbHalfWidth, centerY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbHalfWidth),
        )
    }
}
