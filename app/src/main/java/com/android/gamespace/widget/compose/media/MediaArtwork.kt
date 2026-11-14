package com.android.gamespace.widget.compose.media

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.gamespace.widget.NowPlayingState

@Composable
fun MediaArtwork(
    state: NowPlayingState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1A1A1A)),
    ) {
        state.artwork?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.15f),
                        0.55f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.72f),
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(state.colorWash)),
        )
        state.sourceIcon?.let { icon ->
            DrawableImage(
                drawable = icon,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 8.dp)
                    .size(30.dp),
            )
        }
    }
}

@Composable
fun DrawableImage(
    drawable: Drawable,
    modifier: Modifier = Modifier,
) {
    val bitmap: Bitmap? = remember(drawable) {
        runCatching { drawable.toBitmap() }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}
