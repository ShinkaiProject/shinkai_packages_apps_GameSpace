package com.android.gamespace.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * What the notification overlay shows. The service moves it with its animators, and so these are
 * the values that they set: the window itself stays where it is and only the text goes up and
 * down inside it.
 */
@Stable
class DanmakuState {
    var text by mutableStateOf("")
    var alpha by mutableStateOf(0f)

    /** How far the text is from the top of the window, in pixels. */
    var translationY by mutableStateOf(0f)

    /** The room under the text that the slide moves it into, in pixels. */
    var slideTravelPx by mutableStateOf(0)
    var textSizePx by mutableStateOf(60f)
}

/** A line of a notification, white and centred, that slides in at the top of the screen. */
@Composable
fun DanmakuOverlay(state: DanmakuState) {
    val density = LocalDensity.current
    // The text moves into the padding, which is where the room for the slide is.
    Box(Modifier.fillMaxWidth().padding(bottom = with(density) { state.slideTravelPx.toDp() })) {
        Text(
            text = state.text,
            color = Color.White,
            // This stands alone, with nothing from the theme: the line height is the font's.
            style = TextStyle(fontSize = with(density) { state.textSizePx.toSp() }),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = state.alpha
                    translationY = state.translationY
                },
        )
    }
}
