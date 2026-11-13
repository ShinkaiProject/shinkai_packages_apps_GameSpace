package com.android.gamespace.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.android.gamespace.ui.theme.groupItemShape
import kotlin.math.roundToInt

/** Collects the rows of a [PreferenceGroup], so that each one gets the corners of its place. */
class PreferenceGroupScope {
    internal val items = mutableListOf<@Composable (Shape) -> Unit>()

    /** One row; it is given the shape that fits where it stands in the group. */
    fun item(content: @Composable (Shape) -> Unit) {
        items.add(content)
    }
}

/** A titled group of rows on separate rounded cards: round on the outside, tight in between. */
@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: PreferenceGroupScope.() -> Unit,
) {
    val scope = PreferenceGroupScope().apply(content)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        val count = scope.items.size
        scope.items.forEachIndexed { index, item -> item(groupItemShape(index, count)) }
    }
}

@Composable
private fun PreferenceCard(
    shape: Shape,
    enabled: Boolean,
    modifier: Modifier,
    rowModifier: Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = rowModifier
                .alpha(if (enabled) 1f else 0.38f)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun RowScope.PreferenceText(
    title: String,
    summary: String?,
    extra: (@Composable ColumnScope.() -> Unit)?,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        if (!summary.isNullOrEmpty()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        extra?.invoke(this)
    }
}

/** A row of a settings list: an optional icon, a title, a summary, and whatever goes at the end. */
@Composable
fun PreferenceRow(
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier,
    summary: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val interaction = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    PreferenceCard(shape, enabled, modifier, interaction) {
        if (leading != null) {
            Box(Modifier.padding(end = 16.dp)) { leading() }
        }
        PreferenceText(title, summary, extra)
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    summary: String? = null,
    enabled: Boolean = true,
) {
    val interaction = Modifier.toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        onValueChange = onCheckedChange,
    )
    PreferenceCard(shape, enabled, modifier, interaction) {
        PreferenceText(title, summary, null)
        Spacer(Modifier.width(12.dp))
        // The row handles the touch, so the switch itself only shows the state.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * A row that shows the chosen entry and opens a dialog with all of them. [values] are what is
 * stored, [entries] what is shown; they go in the same order.
 */
@Composable
fun ListPreference(
    title: String,
    entries: List<String>,
    values: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dialogTitle: String = title,
    dialogMessage: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val selected = values.indexOf(value)
    PreferenceRow(
        title = title,
        shape = shape,
        modifier = modifier,
        summary = entries.getOrNull(selected),
        enabled = enabled,
        onClick = { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(dialogTitle) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (dialogMessage != null) {
                        Text(
                            text = dialogMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Column(Modifier.selectableGroup()) {
                        entries.forEachIndexed { index, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = index == selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            open = false
                                            if (index != selected) onValueChange(values[index])
                                        },
                                    )
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = index == selected, onClick = null)
                                Spacer(Modifier.width(16.dp))
                                Text(entry, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

/**
 * A row with a slider under its title, for a whole number between [min] and [max]. The value is
 * only passed on while it changes to another whole number.
 */
@Composable
fun SliderPreference(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
) {
    val span = (max - min).coerceAtLeast(1)
    PreferenceRow(
        title = title,
        shape = shape,
        modifier = modifier,
        summary = summary,
        enabled = enabled,
        extra = {
            GapSlider(
                fraction = (value.coerceIn(min, max) - min) / span.toFloat(),
                onFractionChange = { fraction ->
                    val next = min + (fraction * span).roundToInt()
                    if (next != value) onValueChange(next)
                },
                enabled = enabled,
                modifier = Modifier.padding(top = 4.dp),
                style = GapSliderStyle.Setting,
            )
        },
    )
}
