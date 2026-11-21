package com.android.gamespace.widget.compose.panel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.gamespace.widget.NowPlayingController
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.cos

private val AccelerateDecelerateEasing = Easing { fraction ->
    (cos((fraction.toDouble() + 1.0) * PI) / 2.0 + 0.5).toFloat()
}

@Composable
fun PanelControls(
    controlsState: PanelControlsState,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessDragStart: () -> Unit,
    onBrightnessDragEnd: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeDragStart: () -> Unit,
    onVolumeDragEnd: () -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onCycleRingerMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMedia by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val nowPlaying = remember(context) { NowPlayingController(context) }
    if (showMedia) {
        LifecycleStartEffect(nowPlaying) {
            nowPlaying.start()
            onStopOrDispose { nowPlaying.stop() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        AnimatedContent(
            targetState = showMedia,
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopStart,
            transitionSpec = {
                val enter = slideInHorizontally(
                    animationSpec = tween(400, easing = AccelerateDecelerateEasing),
                ) + fadeIn(
                    animationSpec = tween(400, easing = AccelerateDecelerateEasing),
                )
                val exit = slideOutHorizontally(
                    animationSpec = tween(400, easing = AccelerateDecelerateEasing),
                    targetOffsetX = { it / 2 },
                ) + fadeOut(
                    animationSpec = tween(400, easing = AccelerateDecelerateEasing),
                )
                (enter togetherWith exit).using(
                    SizeTransform(clip = true) { _, _ ->
                        tween(200, easing = AccelerateDecelerateEasing)
                    }
                )
            },
            label = "panel-controls",
        ) { media ->
            if (media) {
                val nowPlayingState by nowPlaying.state.collectAsStateWithLifecycle()
                com.android.gamespace.widget.compose.media.NowPlayingCard(
                    state = nowPlayingState,
                    onPlayPause = nowPlaying::togglePlayPause,
                    onPrevious = nowPlaying::skipToPrevious,
                    onNext = nowPlaying::skipToNext,
                    onSeekStart = nowPlaying::beginSeek,
                    onSeek = nowPlaying::seekTo,
                    onSeekEnd = nowPlaying::endSeek,
                    onLike = nowPlaying::toggleLike,
                    onRepeat = nowPlaying::toggleRepeat,
                )
            } else {
                PanelSliders(
                    state = controlsState,
                    onBrightnessChange = onBrightnessChange,
                    onBrightnessDragStart = onBrightnessDragStart,
                    onBrightnessDragEnd = onBrightnessDragEnd,
                    onVolumeChange = onVolumeChange,
                    onVolumeDragStart = onVolumeDragStart,
                    onVolumeDragEnd = onVolumeDragEnd,
                    onToggleAutoBrightness = onToggleAutoBrightness,
                    onCycleRingerMode = onCycleRingerMode,
                )
            }
        }
        PanelNavigationArrow(
            expanded = showMedia,
            onClick = { showMedia = !showMedia },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 15.dp),
        )
    }
}
