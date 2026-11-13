package com.android.gamespace.ui.display

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.display.DisplayEnhancer
import com.android.gamespace.display.DisplayPreset
import com.android.gamespace.ui.components.GameSpaceScaffold
import com.android.gamespace.ui.components.GapSlider
import com.android.gamespace.ui.components.GapSliderStyle
import com.android.gamespace.ui.components.LifecycleEffect
import com.android.gamespace.ui.components.PreferenceGroup
import com.android.gamespace.ui.components.PreferenceRow
import com.android.gamespace.ui.theme.groupItemShape
import kotlin.math.roundToInt

/** The presets of Game Display Enhanced: pick one, make your own, change or delete it. */
@Composable
fun DisplayEnhancerScreen(enhancer: DisplayEnhancer, onBack: () -> Unit) {
    val context = LocalContext.current
    // Goes up when a preset is chosen, saved or deleted, so the list is read again.
    var version by remember { mutableStateOf(0) }
    // The editor that is open: the preset it changes (null for a new one) and where it starts.
    var editor by remember { mutableStateOf<EditorRequest?>(null) }

    LifecycleEffect(
        onStart = { enhancer.recover() },
        onStop = { enhancer.endPreview() },
    )

    // Read after the version, so that a change draws this again.
    val presets = remember(version) { enhancer.presets() }
    val activeId = remember(version) { enhancer.active.id }

    GameSpaceScaffold(title = stringResource(R.string.display_enhancer_title), onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(padding),
        ) {
            PreferenceGroup(title = stringResource(R.string.display_presets_builtin)) {
                presets.filter { it.builtIn }.forEach { preset ->
                    item { shape ->
                        PreferenceRow(
                            title = enhancer.labelOf(preset),
                            summary = describe(preset),
                            shape = shape,
                            trailing = { if (preset.id == activeId) ActiveMark() },
                            onClick = {
                                enhancer.select(preset.id)
                                version++
                            },
                        )
                    }
                }
            }

            PreferenceGroup(title = stringResource(R.string.display_presets_custom)) {
                presets.filter { !it.builtIn }.forEach { preset ->
                    item { shape ->
                        PreferenceRow(
                            title = enhancer.labelOf(preset),
                            summary = describe(preset),
                            shape = shape,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editor = EditorRequest(preset, preset) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_edit_preset),
                                            contentDescription = stringResource(R.string.display_preset_edit),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (preset.id == activeId) ActiveMark()
                                }
                            },
                            onClick = {
                                enhancer.select(preset.id)
                                version++
                            },
                        )
                    }
                }
                item { shape ->
                    PreferenceRow(
                        title = stringResource(R.string.display_preset_create),
                        summary = stringResource(R.string.display_preset_create_summary),
                        shape = shape,
                        leading = {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            if (enhancer.customPresets().size >= DisplayEnhancer.MAX_CUSTOM) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.display_preset_limit, DisplayEnhancer.MAX_CUSTOM),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                // A new preset starts from the one that is in use.
                                editor = EditorRequest(null, enhancer.active)
                            }
                        },
                    )
                }
            }

            // A card of its own, with room above it so it does not touch the presets.
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = groupItemShape(0, 1),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.display_enhancer_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editor?.let { request ->
        PresetEditorDialog(
            enhancer = enhancer,
            request = request,
            onDismiss = { editor = null },
            onChanged = { version++ },
        )
    }
}

@Composable
private fun ActiveMark() {
    Icon(
        painter = painterResource(R.drawable.ic_check),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun describe(preset: DisplayPreset): String {
    if (preset.id == DisplayPreset.STANDARD_ID) {
        return stringResource(R.string.display_preset_standard_summary)
    }
    return stringResource(R.string.display_preset_values, preset.saturation, warmthText(preset.warmth))
}

private fun warmthText(warmth: Int) = if (warmth > 0) "+$warmth" else warmth.toString()

/** [existing] is the preset that is changed, null for a new one; [seed] gives the first values. */
private class EditorRequest(val existing: DisplayPreset?, val seed: DisplayPreset)

/**
 * The dialog that makes a custom preset, or changes one. While it is open the sliders show their
 * result on the screen; closing it, however it is closed, puts the screen back.
 */
@Composable
private fun PresetEditorDialog(
    enhancer: DisplayEnhancer,
    request: EditorRequest,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val existing = request.existing
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var saturation by remember { mutableStateOf(request.seed.saturation) }
    var warmth by remember { mutableStateOf(request.seed.warmth) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun current() = DisplayPreset(
        id = existing?.id.orEmpty(),
        name = existing?.name ?: "",
        saturation = saturation,
        warmth = warmth,
    )

    DisposableEffect(Unit) {
        onDispose { enhancer.endPreview() }
    }

    fun save() {
        val clean = DisplayPreset.cleanName(name).ifEmpty { enhancer.defaultName() }
        val preset = DisplayPreset(
            id = existing?.id ?: enhancer.newId(),
            name = clean,
            saturation = saturation,
            warmth = warmth,
        )
        if (!enhancer.saveCustom(preset)) {
            Toast.makeText(
                context,
                context.getString(R.string.display_preset_limit, DisplayEnhancer.MAX_CUSTOM),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        // A new preset is what it was made for: the one to use.
        if (existing == null) enhancer.select(preset.id)
        onChanged()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.display_preset_new else R.string.display_preset_edit
                )
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextField(
                    value = name,
                    onValueChange = { name = it.take(DisplayPreset.MAX_NAME) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.display_preset_name_hint)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                EditorSlider(
                    label = stringResource(R.string.display_saturation),
                    valueText = stringResource(R.string.display_value_percent, saturation),
                    value = saturation,
                    min = DisplayPreset.SATURATION_MIN,
                    max = DisplayPreset.SATURATION_MAX,
                    modifier = Modifier.padding(top = 16.dp),
                    onValueChange = {
                        saturation = it
                        enhancer.preview(current().copy(saturation = it))
                    },
                )
                EditorSlider(
                    label = stringResource(R.string.display_warmth),
                    valueText = warmthText(warmth),
                    value = warmth,
                    min = DisplayPreset.WARMTH_MIN,
                    max = DisplayPreset.WARMTH_MAX,
                    modifier = Modifier.padding(top = 8.dp),
                    onValueChange = {
                        warmth = it
                        enhancer.preview(current().copy(warmth = it))
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::save) { Text(stringResource(R.string.display_preset_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.display_preset_delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.display_preset_delete_message, existing.name.orEmpty())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        enhancer.delete(existing.id)
                        onChanged()
                        confirmDelete = false
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.display_preset_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/** A label with its value at the end, and a slider for a whole number between [min] and [max]. */
@Composable
private fun EditorSlider(
    label: String,
    valueText: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = (max - min).coerceAtLeast(1)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GapSlider(
            fraction = (value.coerceIn(min, max) - min) / span.toFloat(),
            onFractionChange = { fraction ->
                val next = min + (fraction * span).roundToInt()
                if (next != value) onValueChange(next)
            },
            style = GapSliderStyle.Editor,
        )
    }
}
