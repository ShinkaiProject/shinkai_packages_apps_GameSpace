package com.android.gamespace.ui.components

import android.graphics.drawable.Drawable
import androidx.annotation.RawRes
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import androidx.compose.runtime.MutableState
import kotlin.math.roundToInt

/**
 * Plays the Lottie animation in [rawRes], endlessly, on a Compose canvas. The animation is a
 * LottieDrawable that is drawn here; no View is involved.
 */
@Composable
fun LottieIllustration(@RawRes rawRes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frame = remember { mutableStateOf(0) }
    val player = remember(rawRes) {
        runCatching { LottieCompositionFactory.fromRawResSync(context, rawRes).value }
            .getOrNull()
            ?.let { LottiePlayer(it, frame) }
    }
    DisposableEffect(player) {
        player?.drawable?.playAnimation()
        onDispose { player?.drawable?.cancelAnimation() }
    }
    Canvas(modifier) {
        // Read here, so that each frame of the animation draws this canvas again.
        frame.value
        val drawable = player?.drawable ?: return@Canvas
        val side = minOf(size.width, size.height).roundToInt()
        val left = ((size.width - side) / 2f).roundToInt()
        val top = ((size.height - side) / 2f).roundToInt()
        drawable.setBounds(left, top, left + side, top + side)
        drawIntoCanvas { canvas -> drawable.draw(canvas.nativeCanvas) }
    }
}

private class LottiePlayer(composition: LottieComposition, frame: MutableState<Int>) {
    // A drawable only keeps a weak reference to its callback, so it is held here.
    private val callback = object : Drawable.Callback {
        override fun invalidateDrawable(who: Drawable) {
            frame.value = frame.value + 1
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
        override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    }

    val drawable = LottieDrawable().apply {
        setComposition(composition)
        repeatCount = LottieDrawable.INFINITE
        callback = this@LottiePlayer.callback
    }
}
