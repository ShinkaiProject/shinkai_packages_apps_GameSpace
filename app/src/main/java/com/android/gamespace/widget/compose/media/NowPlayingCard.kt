package com.android.gamespace.widget.compose.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.widget.NowPlayingState

// Play/pause button: 44dp rounded square. Raise the radius for a rounder look (22.dp = circle).
private val PlayPauseButtonShape = RoundedCornerShape(14.dp)

@Composable
fun NowPlayingCard(
    state: NowPlayingState,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    onLike: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(118.dp),
        shape = MaterialTheme.shapes.large,
        color = Color(0xFF1A1A1A),
    ) {
        Box {
            MediaArtwork(state = state, modifier = Modifier.fillMaxSize())
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                    ) {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.artist,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(PlayPauseButtonShape)
                            .background(Color(state.accentColor))
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state.isPlaying) {
                                    R.drawable.ic_media_pause
                                } else {
                                    R.drawable.ic_media_play
                                }
                            ),
                            contentDescription = null,
                            tint = Color(state.onAccentColor),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                MediaPlaybackControls(
                    state = state,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeekStart = onSeekStart,
                    onSeek = onSeek,
                    onSeekEnd = onSeekEnd,
                    onLike = onLike,
                    onRepeat = onRepeat,
                )
            }
        }
    }
}
