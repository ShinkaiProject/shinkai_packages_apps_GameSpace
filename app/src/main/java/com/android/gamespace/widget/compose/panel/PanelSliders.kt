package com.android.gamespace.widget.compose.panel

import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.widget.compose.components.GameSpaceSlider
import kotlin.math.roundToInt

@Composable
fun PanelSliders(
    state: PanelControlsState,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SliderIcon(
                iconRes = R.drawable.ic_brightness,
                contentDescription = null,
                alpha = if (state.autoBrightness) 1f else 0.55f,
                onClick = onToggleAutoBrightness,
            )
            GameSpaceSlider(
                value = state.brightness,
                onValueChange = {
                    onBrightnessDragStart()
                    onBrightnessChange(it)
                },
                onValueChangeFinished = onBrightnessDragEnd,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SliderIcon(
                iconRes = R.drawable.ic_volume,
                contentDescription = null,
                onClick = onCycleRingerMode,
            ) {
                when (state.ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_volume_off
                    AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_vibration
                    else -> R.drawable.ic_volume
                }
            }
            GameSpaceSlider(
                value = state.volume.toFloat(),
                onValueChange = {
                    onVolumeDragStart()
                    onVolumeChange(it.roundToInt())
                },
                onValueChangeFinished = onVolumeDragEnd,
                valueRange = 0f..state.volumeMax.toFloat().coerceAtLeast(1f),
                steps = (state.volumeMax - 1).coerceAtLeast(0),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun SliderIcon(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    alpha: Float = 1f,
    dynamicIcon: (() -> Int)? = null,
) {
    val icon = dynamicIcon?.invoke() ?: iconRes
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(18.dp)
                    .alpha(alpha),
            )
        }
    }
}
