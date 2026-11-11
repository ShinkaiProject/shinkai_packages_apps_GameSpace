package com.android.gamespace.display

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.android.gamespace.R
import com.android.gamespace.ui.components.ExpressiveGapSlider
import com.android.gamespace.ui.components.GameSpaceSettingsScaffold
import com.android.gamespace.ui.components.GroupedPreferenceItem
import com.android.gamespace.ui.components.PreferenceCategoryHeader
import com.android.gamespace.ui.theme.GameSpaceTheme
import kotlin.math.roundToInt

class DisplayEnhancerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameSpaceTheme {
                DisplayEnhancerScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}

private data class EditorTarget(
    val existing: DisplayPreset?,
    val seed: DisplayPreset
)

@Composable
fun DisplayEnhancerScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val enhancer = remember { context.displayEnhancer() }

    var presets by remember { mutableStateOf(enhancer.presets()) }
    var activeId by remember { mutableStateOf(enhancer.active.id) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }
    var deleteCandidate by remember { mutableStateOf<DisplayPreset?>(null) }

    fun refresh() {
        presets = enhancer.presets()
        activeId = enhancer.active.id
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> enhancer.recover()
                Lifecycle.Event.ON_STOP -> enhancer.endPreview()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            enhancer.endPreview()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val builtInPresets = remember(presets) { presets.filter { it.builtIn } }
    val customPresets = remember(presets) { presets.filter { !it.builtIn } }

    @Composable
    fun describePreset(preset: DisplayPreset): String {
        if (preset.id == DisplayPreset.STANDARD_ID) {
            return stringResource(R.string.display_preset_standard_summary)
        }
        val warmth = if (preset.warmth > 0) "+${preset.warmth}" else preset.warmth.toString()
        return stringResource(R.string.display_preset_values, preset.saturation, warmth)
    }

    GameSpaceSettingsScaffold(
        title = stringResource(R.string.display_enhancer_title),
        onBackClick = onBackClick
    ) { paddingModifier ->
        LazyColumn(
            modifier = paddingModifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item(key = "header_builtin") {
                PreferenceCategoryHeader(title = stringResource(R.string.display_presets_builtin))
            }

            item(key = "builtin_group") {
                Column {
                    builtInPresets.forEachIndexed { index, preset ->
                        val isSelected = preset.id == activeId
                        GroupedPreferenceItem(
                            title = enhancer.labelOf(preset),
                            summary = describePreset(preset),
                            index = index,
                            count = builtInPresets.size,
                            selected = isSelected,
                            onClick = {
                                enhancer.select(preset.id)
                                activeId = preset.id
                            },
                            trailingContent = {
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            item(key = "header_custom") {
                PreferenceCategoryHeader(title = stringResource(R.string.display_presets_custom))
            }

            item(key = "custom_group") {
                val totalCustomRows = customPresets.size + 1
                Column {
                    customPresets.forEachIndexed { index, preset ->
                        val isSelected = preset.id == activeId
                        GroupedPreferenceItem(
                            title = enhancer.labelOf(preset),
                            summary = describePreset(preset),
                            index = index,
                            count = totalCustomRows,
                            selected = isSelected,
                            onClick = {
                                enhancer.select(preset.id)
                                activeId = preset.id
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editorTarget = EditorTarget(existing = preset, seed = preset)
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_edit_preset),
                                            contentDescription = stringResource(R.string.display_preset_edit),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }

                    GroupedPreferenceItem(
                        title = stringResource(R.string.display_preset_create),
                        summary = stringResource(R.string.display_preset_create_summary),
                        iconRes = R.drawable.ic_add,
                        index = customPresets.size,
                        count = totalCustomRows,
                        onClick = {
                            if (enhancer.customPresets().size >= DisplayEnhancer.MAX_CUSTOM) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.display_preset_limit, DisplayEnhancer.MAX_CUSTOM),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                editorTarget = EditorTarget(existing = null, seed = enhancer.active)
                            }
                        }
                    )
                }
            }

            item(key = "note_card") {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.display_enhancer_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    editorTarget?.let { target ->
        PresetEditorDialog(
            enhancer = enhancer,
            existing = target.existing,
            seed = target.seed,
            onDismiss = {
                enhancer.endPreview()
                editorTarget = null
            },
            onSaved = {
                enhancer.endPreview()
                editorTarget = null
                refresh()
            },
            onRequestDelete = { presetToDelete ->
                enhancer.endPreview()
                editorTarget = null
                deleteCandidate = presetToDelete
            }
        )
    }

    deleteCandidate?.let { preset ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            text = {
                Text(stringResource(R.string.display_preset_delete_message, preset.name.orEmpty()))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        enhancer.delete(preset.id)
                        deleteCandidate = null
                        refresh()
                    }
                ) {
                    Text(stringResource(R.string.display_preset_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PresetEditorDialog(
    enhancer: DisplayEnhancer,
    existing: DisplayPreset?,
    seed: DisplayPreset,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onRequestDelete: (DisplayPreset) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var saturation by remember {
        mutableIntStateOf(
            seed.saturation.coerceIn(DisplayPreset.SATURATION_MIN, DisplayPreset.SATURATION_MAX)
        )
    }
    var warmth by remember {
        mutableIntStateOf(
            seed.warmth.coerceIn(DisplayPreset.WARMTH_MIN, DisplayPreset.WARMTH_MAX)
        )
    }

    fun previewCurrent(sat: Int, wrm: Int) {
        enhancer.preview(
            DisplayPreset(
                id = existing?.id.orEmpty(),
                name = existing?.name.orEmpty(),
                saturation = sat,
                warmth = wrm
            )
        )
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= DisplayPreset.MAX_NAME) name = it },
                    label = { Text(stringResource(R.string.display_preset_name_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.display_saturation),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.display_value_percent, saturation),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ExpressiveGapSlider(
                        value = saturation.toFloat(),
                        valueRange = DisplayPreset.SATURATION_MIN.toFloat()..DisplayPreset.SATURATION_MAX.toFloat(),
                        onValueChange = {
                            val newSat = it.roundToInt()
                            saturation = newSat
                            previewCurrent(newSat, warmth)
                        }
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.display_warmth),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (warmth > 0) "+$warmth" else warmth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    ExpressiveGapSlider(
                        value = warmth.toFloat(),
                        valueRange = DisplayPreset.WARMTH_MIN.toFloat()..DisplayPreset.WARMTH_MAX.toFloat(),
                        onValueChange = {
                            val newWarmth = it.roundToInt()
                            warmth = newWarmth
                            previewCurrent(saturation, newWarmth)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanedName = DisplayPreset.cleanName(name).ifEmpty { enhancer.defaultName() }
                    val preset = DisplayPreset(
                        id = existing?.id ?: enhancer.newId(),
                        name = cleanedName,
                        saturation = saturation,
                        warmth = warmth
                    )
                    if (!enhancer.saveCustom(preset)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.display_preset_limit, DisplayEnhancer.MAX_CUSTOM),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        if (existing == null) enhancer.select(preset.id)
                        onSaved()
                    }
                }
            ) {
                Text(stringResource(R.string.display_preset_save))
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { onRequestDelete(existing) }) {
                        Text(
                            text = stringResource(R.string.display_preset_delete),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}
