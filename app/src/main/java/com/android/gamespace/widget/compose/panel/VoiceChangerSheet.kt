package com.android.gamespace.widget.compose.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.gamespace.R
import com.android.gamespace.audio.VoiceChanger

@Composable
fun VoiceChangerSheet(
    voiceChanger: VoiceChanger,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by voiceChanger.state.collectAsStateWithLifecycle()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.voice_changer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.voice_changer_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.panel_edit_done),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.voice_changer_enable),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = if (state.enabled) {
                            stringResource(R.string.state_enabled)
                        } else {
                            stringResource(R.string.state_disabled)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.enabled,
                    onCheckedChange = voiceChanger::setEnabled,
                    enabled = state.available,
                )
            }

            if (!state.available) {
                Text(
                    text = stringResource(R.string.voice_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Text(
                text = stringResource(
                    R.string.voice_changer_intensity,
                    state.intensity,
                ),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Slider(
                value = state.intensity.toFloat(),
                onValueChange = { voiceChanger.setIntensity(it.toInt()) },
                valueRange = 0f..100f,
                steps = 9,
                enabled = state.available && state.preset != VoiceChanger.Presets.ORIGINAL,
            )

            Text(
                text = stringResource(R.string.voice_presets_title),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VoiceChanger.Presets.ALL.forEach { preset ->
                    PresetChip(
                        preset = preset,
                        label = voiceChanger.labelOf(preset),
                        selected = state.preset == preset,
                        enabled = state.available,
                        onClick = { voiceChanger.selectPreset(preset) },
                    )
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (state.available) {
                FilledTonalButton(
                    onClick = voiceChanger::togglePreview,
                    enabled = state.enabled,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(
                        text = if (state.previewing) {
                            stringResource(R.string.voice_preview_stop)
                        } else {
                            stringResource(R.string.voice_preview_start)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: Int,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = Modifier
            .width(72.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = container),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Icon(
                painter = painterResource(VoiceChangerIcons.forPreset(preset)),
                contentDescription = label,
                tint = content,
                modifier = Modifier
                    .padding(10.dp)
                    .size(24.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) content else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Icon resource for every preset, kept next to the sheet that renders it. */
object VoiceChangerIcons {
    fun forPreset(preset: Int): Int = when (preset) {
        VoiceChanger.Presets.ORIGINAL -> R.drawable.ic_voice_original
        VoiceChanger.Presets.MALE -> R.drawable.ic_voice_male
        VoiceChanger.Presets.FEMALE -> R.drawable.ic_voice_female
        VoiceChanger.Presets.CHILD -> R.drawable.ic_voice_child
        VoiceChanger.Presets.ROBOT -> R.drawable.ic_voice_robot
        VoiceChanger.Presets.DEEP -> R.drawable.ic_voice_deep
        VoiceChanger.Presets.HIGH -> R.drawable.ic_voice_high
        VoiceChanger.Presets.MONSTER -> R.drawable.ic_voice_monster
        VoiceChanger.Presets.ALIEN -> R.drawable.ic_voice_alien
        VoiceChanger.Presets.CARTOON -> R.drawable.ic_voice_cartoon
        else -> R.drawable.ic_voice_original
    }
}
