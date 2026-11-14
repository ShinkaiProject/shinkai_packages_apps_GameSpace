package com.android.gamespace.widget.compose.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.widget.tiles.TileType

@Composable
fun PanelTileEditor(
    selectedTiles: List<String>,
    onSelectedTilesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allIds = remember { TileType.entries.map { it.id } }
    val model = remember {
        TileLayoutModel(
            allIds = allIds,
            initialActive = selectedTiles,
            defaultIds = AppSettings.DEFAULT_TILES.split(","),
        )
    }
    var activeIds by remember { mutableStateOf(model.active.toList()) }
    val availableIds = allIds.filter { it !in activeIds }

    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragTouch by remember { mutableStateOf(Offset.Zero) }

    fun persist() {
        onSelectedTilesChange(activeIds)
    }

    fun refresh() {
        activeIds = model.active.toList()
    }

    fun add(id: String) {
        if (model.add(id)) {
            refresh()
            persist()
        }
    }

    fun remove(id: String) {
        if (model.remove(id)) {
            refresh()
            persist()
        }
    }

    fun reset() {
        if (model.reset()) {
            refresh()
            persist()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        state = gridState,
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item(
            key = "reset",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.tile_layout_reset),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { reset() }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        item(
            key = "active_label",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            EditorLabel(textRes = R.string.panel_edit_active)
        }

        if (activeIds.isEmpty()) {
            item(
                key = "active_note",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                EditorNote(textRes = R.string.tile_layout_empty_active)
            }
        }

        items(activeIds, key = { "active_$it" }) { id ->
            TileType.fromId(id)?.let { type ->
                val isDragging = draggingId == type.id
                val dragModifier = Modifier
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.04f
                            scaleY = 1.04f
                            shadowElevation = 8.dp.toPx()
                        }
                    }
                    .pointerInput(type.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { touch ->
                                val info = gridState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == "active_${type.id}" }
                                if (info != null) {
                                    draggingId = type.id
                                    dragOffset = Offset.Zero
                                    dragOrigin = Offset(
                                        info.offset.x.toFloat(),
                                        info.offset.y.toFloat(),
                                    )
                                    dragTouch = touch
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = Offset.Zero
                                dragOrigin = Offset.Zero
                                dragTouch = Offset.Zero
                                persist()
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = Offset.Zero
                                dragOrigin = Offset.Zero
                                dragTouch = Offset.Zero
                                persist()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount
                                val pointer = dragOrigin + dragTouch + dragOffset
                                var target: androidx.compose.foundation.lazy.grid.LazyGridItemInfo? = null
                                for (info in gridState.layoutInfo.visibleItemsInfo) {
                                    val key = info.key as? String ?: continue
                                    if (!key.startsWith("active_")) continue
                                    if (key == "active_${type.id}") continue
                                    val insideX = pointer.x >= info.offset.x &&
                                        pointer.x <= info.offset.x + info.size.width
                                    val insideY = pointer.y >= info.offset.y &&
                                        pointer.y <= info.offset.y + info.size.height
                                    if (insideX && insideY) {
                                        target = info
                                        break
                                    }
                                }
                                val targetId = (target?.key as? String)
                                    ?.removePrefix("active_")
                                val found = target
                                if (targetId != null && found != null) {
                                    val from = activeIds.indexOf(type.id)
                                    val to = activeIds.indexOf(targetId)
                                    if (from >= 0 && to >= 0 && from != to) {
                                        if (model.reorder(from, to)) {
                                            refresh()
                                            dragOrigin = Offset(
                                                found.offset.x.toFloat(),
                                                found.offset.y.toFloat(),
                                            )
                                            dragTouch = Offset(
                                                found.size.width / 2f,
                                                found.size.height / 2f,
                                            )
                                            dragOffset = Offset.Zero
                                        }
                                    }
                                }
                            },
                        )
                    }

                ActiveTileCell(
                    type = type,
                    onClick = { remove(type.id) },
                    modifier = dragModifier,
                )
            }
        }

        item(
            key = "available_label",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            EditorLabel(textRes = R.string.panel_edit_available)
        }

        if (availableIds.isEmpty()) {
            item(
                key = "available_note",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                EditorNote(textRes = R.string.tile_layout_empty_available)
            }
        }

        items(availableIds, key = { "available_$it" }) { id ->
            TileType.fromId(id)?.let { type ->
                AvailableTileCell(
                    type = type,
                    onClick = { add(type.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        item(
            key = "hint",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(R.string.panel_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun EditorLabel(textRes: Int) {
    Text(
        text = androidx.compose.ui.res.stringResource(textRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
private fun EditorNote(textRes: Int) {
    Text(
        text = androidx.compose.ui.res.stringResource(textRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}
