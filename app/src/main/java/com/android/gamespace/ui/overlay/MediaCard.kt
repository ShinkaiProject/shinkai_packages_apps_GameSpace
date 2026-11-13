package com.android.gamespace.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.gamespace.R
import kotlin.math.PI
import kotlin.math.sin

/** The media page of the controls: what plays, with the buttons for it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(state: NowPlayingState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The accent of the system colours the card, and decides the colour of what is on it.
    val accent = remember { Color(context.getColor(android.R.color.system_accent1_500)) }
    val accentLight = remember { Color(context.getColor(android.R.color.system_accent1_50)) }
    val onAccent = if (0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue > 0.5f) {
        Color.Black
    } else {
        Color.White
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 6.dp)
            .height(118.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A)),
    ) {
        val art = state.art
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // A scrim, so that the text can be read over any cover, and the accent over it.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x26000000)))),
        )
        Box(Modifier.fillMaxSize().background(accent.copy(alpha = 60f / 255f)))

        // No padding at the bottom: the two rows are 88dp, and they get 90dp of the 118.
        Column(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 28.dp)) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = state.title ?: stringResource(R.string.media_no_session),
                        color = Color.White,
                        style = overlayText(13.sp, FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                    )
                    Text(
                        text = state.artist,
                        color = Color(0xCCFFFFFF),
                        style = overlayText(11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent)
                        .tap(state::togglePlayPause)
                        .padding(10.dp),
                ) {
                    Icon(
                        painter = painterResource(
                            if (state.playing) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                        ),
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MediaIcon(R.drawable.ic_media_prev, state::previous)
                SquigglySeekBar(
                    progress = state.progress,
                    animate = state.playing,
                    thumbColor = accentLight,
                    onSeekStart = state::beginSeek,
                    onSeek = state::seekTo,
                    onSeekEnd = state::endSeek,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(24.dp),
                )
                MediaIcon(R.drawable.ic_media_next, state::next)
                Spacer(Modifier.width(8.dp))
                if (state.canLike) {
                    MediaIcon(
                        if (state.liked) R.drawable.ic_media_like_filled else R.drawable.ic_media_like_border,
                        state::toggleLike,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (state.canRepeat) {
                    MediaIcon(
                        R.drawable.ic_media_repeat,
                        state::toggleRepeat,
                        Modifier.alpha(if (state.repeatOn) 1f else 0.5f),
                    )
                }
            }
        }

        val source = state.sourceIcon
        if (source != null) {
            Image(
                bitmap = source.image,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 8.dp)
                    .size(30.dp),
                contentScale = ContentScale.Inside,
                colorFilter = if (source.monochrome) ColorFilter.tint(accentLight) else null,
            )
        }
    }
}

/** A click with no ripple: the buttons of the card are plain images, the same as they were. */
private fun Modifier.tap(onClick: () -> Unit) = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

@Composable
private fun MediaIcon(icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = Color.White,
        modifier = modifier.size(16.dp).tap(onClick),
    )
}

private const val WAVE_STROKE = 5f
private const val TRACK_STROKE = 3f
private const val WAVE_LENGTH = 64f
private const val WAVE_HEIGHT = 5f
private const val RAMP_DISTANCE = 32f
private const val WAVE_STEP = 2f

// The wave moved 0.35px on every frame of a 60Hz screen. Here it moves with time, so it goes
// the same on any refresh rate.
private const val WAVE_SPEED = 0.35f * 60f

/**
 * The seekbar of the media card: a wave that moves while it plays, up to the thumb, and a plain
 * line after it. [progress] is 0..1. Dragging it, or tapping on it, seeks.
 */
@Composable
private fun SquigglySeekBar(
    progress: Float,
    animate: Boolean,
    thumbColor: Color,
    onSeekStart: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = remember { mutableStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            // One wavelength is a full turn, so keeping it under that loses nothing.
            phase.value = (phase.value + (now - last) / 1_000_000_000f * WAVE_SPEED) % WAVE_LENGTH
            last = now
        }
    }
    val start by rememberUpdatedState(onSeekStart)
    val seek by rememberUpdatedState(onSeek)
    val end by rememberUpdatedState(onSeekEnd)
    val path = remember { Path() }

    Canvas(
        modifier.pointerInput(Unit) {
            val thumbWidth = 3.dp.toPx()
            fun toFraction(x: Float): Float {
                val travel = size.width - thumbWidth
                return if (travel <= 0f) 0f else ((x - thumbWidth / 2f) / travel).coerceIn(0f, 1f)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                }
                if (slop != null) {
                    start()
                    seek(toFraction(slop.position.x))
                    horizontalDrag(slop.id) { change ->
                        seek(toFraction(change.position.x))
                        change.consume()
                    }
                    end()
                } else {
                    // Let go before moving: a tap on the bar.
                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                    if (up != null && !up.pressed) {
                        start()
                        seek(toFraction(down.position.x))
                        end()
                    }
                }
            }
        },
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val thumbWidth = 3.dp.toPx()
        val thumbHeight = 12.dp.toPx()
        val thumbCenter = thumbWidth / 2f + progress * (width - thumbWidth)
        val activeWidth = if (progress <= 0f) 0f else thumbCenter

        if (activeWidth > 0f) {
            path.reset()
            path.moveTo(0f, centerY)
            val shift = phase.value
            var x = 0f
            while (x <= activeWidth) {
                val startDamping = (x / RAMP_DISTANCE).coerceIn(0f, 1f)
                val endDamping = ((activeWidth - x) / RAMP_DISTANCE).coerceIn(0f, 1f)
                val amplitude = WAVE_HEIGHT * minOf(startDamping, endDamping)
                val y = if (animate) {
                    centerY + sin((x - shift) / WAVE_LENGTH * 2f * PI.toFloat()) * amplitude
                } else {
                    centerY
                }
                path.lineTo(x, y)
                x += WAVE_STEP
            }
            drawPath(path, Color.White, style = Stroke(width = WAVE_STROKE, cap = StrokeCap.Round))
        }
        if (activeWidth < width) {
            drawLine(
                color = Color(0x4DFFFFFF),
                start = Offset(activeWidth, centerY),
                end = Offset(width, centerY),
                strokeWidth = TRACK_STROKE,
                cap = StrokeCap.Round,
            )
        }
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(thumbCenter - thumbWidth / 2f, centerY - thumbHeight / 2f),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
    }
}
