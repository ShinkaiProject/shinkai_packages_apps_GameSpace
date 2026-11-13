package com.android.gamespace.ui.overlay

import android.media.AudioManager
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.ui.components.GapSlider
import com.android.gamespace.ui.components.GapSliderStyle
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The first page of the controls: the brightness and the volume of the music. */
@Composable
fun PanelSliders(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
        BrightnessRow(Modifier.padding(top = 2.dp))
        // The second row goes 4dp up into the first.
        VolumeRow(Modifier.pullUp(4.dp))
    }
}

/** Takes [amount] off the height of what it wraps, and moves it up by as much. */
private fun Modifier.pullUp(amount: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val pull = amount.roundToPx()
    layout(placeable.width, placeable.height - pull) { placeable.place(0, -pull) }
}

@Composable
private fun SliderRow(
    icon: Int,
    iconAlpha: Float,
    onIconClick: () -> Unit,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    onFractionChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalOverlayColors.current
    Row(
        modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            // The whole button is dimmed when it is off, not only the icon.
            Modifier
                .padding(start = 8.dp)
                .size(32.dp)
                .alpha(iconAlpha)
                .clip(CircleShape)
                .background(colors.sliderIcon)
                .clickable(onClick = onIconClick)
                .padding(8.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.onSliderIcon,
                modifier = Modifier.fillMaxSize(),
            )
        }
        GapSlider(
            fraction = fraction,
            onFractionChange = onFractionChange,
            onFractionChangeFinished = onFractionChangeFinished,
            modifier = Modifier.weight(1f),
            style = GapSliderStyle.Panel,
            activeColor = colors.accent,
            inactiveColor = colors.tile,
        )
    }
}

// ---- Brightness

@Composable
private fun BrightnessRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val level = rememberLive(
        context.observing(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)),
    ) { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128) }
    val auto = rememberLive(
        context.observing(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE)),
    ) {
        Settings.System.getInt(
            resolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }

    // While the handle is held the slider shows the finger, and not what the system reports back.
    var dragged by remember { mutableStateOf<Float?>(null) }
    val fraction = dragged ?: linearToGamma((level.value.coerceIn(1, 255) - 1) / 254f)

    SliderRow(
        icon = R.drawable.ic_brightness,
        iconAlpha = if (auto.value) 1f else 0.55f,
        onIconClick = {
            val next = if (auto.value) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            }
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, next)
            auto.value = next == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        },
        fraction = fraction,
        onFractionChange = { gamma ->
            // The same 0..1000 steps of the old slider.
            val stepped = (gamma * GAMMA_STEPS).roundToInt().coerceIn(0, GAMMA_STEPS) / GAMMA_STEPS.toFloat()
            dragged = stepped
            val linear = gammaToLinear(stepped)
            val value = (linear * 254f + 1f).roundToInt().coerceIn(1, 255)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
            level.value = value
        },
        onFractionChangeFinished = { dragged = null },
        modifier = modifier,
    )
}

private const val GAMMA_STEPS = 1000

// Hybrid Log Gamma (HLG) constants per AOSP Display BrightnessUtils
private const val HLG_R = 0.5f
private const val HLG_A = 0.17883277f
private const val HLG_B = 0.28466892f
private const val HLG_C = 0.55991073f

private fun gammaToLinear(gamma: Float): Float {
    val ret = if (gamma <= HLG_R) {
        (gamma / HLG_R) * (gamma / HLG_R)
    } else {
        exp(((gamma - HLG_C) / HLG_A).toDouble()).toFloat() + HLG_B
    }
    return ret.coerceIn(0f, 12f) / 12f
}

private fun linearToGamma(linear: Float): Float {
    val norm = linear * 12f
    return if (norm <= 1f) {
        sqrt(norm.toDouble()).toFloat() * HLG_R
    } else {
        HLG_A * ln((norm - HLG_B).toDouble()).toFloat() + HLG_C
    }.coerceIn(0f, 1f)
}

// ---- Volume

@Composable
private fun VolumeRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val max = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val volume = rememberLive(context.broadcasts(VOLUME_CHANGED_ACTION)) {
        audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    }
    val ringer = rememberLive(context.broadcasts(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
        audio.ringerMode
    }

    var dragged by remember { mutableStateOf<Float?>(null) }
    val fraction = dragged ?: (volume.value.toFloat() / max)

    SliderRow(
        icon = when (ringer.value) {
            AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_volume_off
            AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_vibration
            else -> R.drawable.ic_volume
        },
        iconAlpha = 1f,
        onIconClick = {
            audio.ringerMode = when (audio.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_SILENT
                AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            ringer.value = audio.ringerMode
        },
        fraction = fraction,
        onFractionChange = { value ->
            val step = (value * max).roundToInt().coerceIn(0, max)
            dragged = step.toFloat() / max
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
            volume.value = step
        },
        onFractionChangeFinished = { dragged = null },
        modifier = modifier,
    )
}

private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
