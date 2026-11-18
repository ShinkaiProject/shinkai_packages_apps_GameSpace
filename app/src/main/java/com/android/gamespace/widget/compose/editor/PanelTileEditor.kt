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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.widget.tiles.TileType

// In the grid, the "reset" row and the "Active" label come before the first active tile.
private const val ActiveItemsStartIndex = 2

// The tile being dragged is drawn slightly larger.
private const val DragScale = 1.04f

// A tile swaps places only when the finger is in the middle of it: this fraction of its size is
// ignored on every side.
private const val SwapInset = 0.25f

// Auto-scroll while dragging: the grid scrolls when the finger is within AutoScrollEdge of its
// top or bottom edge, from AutoScrollMinSpeed at the start of that zone up to AutoScrollMaxSpeed
// at (or past) the edge. Speeds are per second.
private val AutoScrollEdge = 64.dp
private val AutoScrollMinSpeed = 100.dp
private val AutoScrollMaxSpeed = 520.dp

// Auto-scroll never pushes the dragged tile further out of view than this: the grid would drop
// it from composition and end the drag.
private val DraggedTileKeepVisible = 24.dp

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
    val density = LocalDensity.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    // Where the finger is, in the grid's own coordinates, and where in the tile it grabbed it.
    // The dragged tile is drawn from these every frame, so it stays under the finger while the
    // grid scrolls or the tile changes slot.
    var dragPointer by remember { mutableStateOf(Offset.Zero) }
    var dragGrab by remember { mutableStateOf(Offset.Zero) }

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

    fun slotOf(id: String) =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "active_$id" }

    fun finishDrag() {
        draggingId = null
        dragPointer = Offset.Zero
        dragGrab = Offset.Zero
        persist()
    }

    // Moves [id] to the tile slot the finger is over. Slots are matched by their position in the
    // grid, not by key: right after a move the layout still lists the old keys until the next
    // measure, and matching on those would move the tile straight back.
    fun reorderUnderPointer(id: String) {
        val from = activeIds.indexOf(id)
        if (from < 0) return
        val pointer = dragPointer
        val slot = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            if ((info.index - ActiveItemsStartIndex) !in activeIds.indices) {
                return@firstOrNull false
            }
            // Only the middle of a slot counts. Right at the border between two tiles, a bit of
            // finger jitter would otherwise swap them back and forth.
            val insetX = info.size.width * SwapInset
            val insetY = info.size.height * SwapInset
            pointer.x >= info.offset.x + insetX &&
                pointer.x < info.offset.x + info.size.width - insetX &&
                pointer.y >= info.offset.y + insetY &&
                pointer.y < info.offset.y + info.size.height - insetY
        } ?: return
        val to = slot.index - ActiveItemsStartIndex
        if (to == from) return
        // The grid keeps the first visible item's key in place. If that is the dragged tile,
        // moving it would make the grid follow it, which looks like a random jump in scroll
        // position. Put the scroll position back where it was.
        val firstIndex = gridState.firstVisibleItemIndex
        val firstOffset = gridState.firstVisibleItemScrollOffset
        if (model.reorder(from, to)) {
            refresh()
            gridState.requestScrollToItem(firstIndex, firstOffset)
        }
    }

    // While a tile is dragged: every frame, scroll if the finger is near the top or bottom edge,
    // then re-check which slot is under the finger. This runs even when the finger is still, which
    // is when the content moves under it and no drag event arrives.
    val draggedId = draggingId
    LaunchedEffect(draggedId) {
        if (draggedId == null) return@LaunchedEffect
        val edge = with(density) { AutoScrollEdge.toPx() }
        val minSpeed = with(density) { AutoScrollMinSpeed.toPx() }
        val maxSpeed = with(density) { AutoScrollMaxSpeed.toPx() }
        val keepVisible = with(density) { DraggedTileKeepVisible.toPx() }
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            // Capped so one slow frame cannot make the grid leap.
            val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = now

            val viewportHeight = gridState.layoutInfo.viewportSize.height.toFloat()
            val zone = minOf(edge, viewportHeight / 3f)
            val y = dragPointer.y
            val depth = when {
                zone <= 0f -> 0f
                y < zone -> (zone - y) / zone
                y > viewportHeight - zone -> (y - (viewportHeight - zone)) / zone
                else -> 0f
            }.coerceIn(0f, 1f)
            val slot = slotOf(draggedId)
            if (depth > 0f && slot != null) {
                val towardsEnd = y > viewportHeight / 2f
                val speed = minSpeed + (maxSpeed - minSpeed) * depth
                // How far the grid can move before the dragged tile is almost out of view.
                val room = if (towardsEnd) {
                    slot.offset.y + slot.size.height - keepVisible
                } else {
                    viewportHeight - slot.offset.y - keepVisible
                }
                val step = minOf(speed * dt, maxOf(room, 0f))
                if (step > 0f) {
                    gridState.dispatchRawDelta(if (towardsEnd) step else -step)
                }
            }
            reorderUnderPointer(draggedId)
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
                // The gesture is outside the graphicsLayer on purpose: its positions are then
                // relative to the tile's slot, unaffected by the drag translation and scale, so
                // slot offset + position is the exact finger position in the grid.
                val dragModifier = Modifier
                    .pointerInput(type.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { touch ->
                                val slot = slotOf(type.id)
                                if (slot != null) {
                                    dragGrab = touch
                                    dragPointer = Offset(
                                        slot.offset.x + touch.x,
                                        slot.offset.y + touch.y,
                                    )
                                    draggingId = type.id
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            },
                            onDragEnd = { finishDrag() },
                            onDragCancel = { finishDrag() },
                            onDrag = { change, _ ->
                                change.consume()
                                val slot = slotOf(type.id)
                                if (draggingId == type.id && slot != null) {
                                    dragPointer = Offset(
                                        slot.offset.x + change.position.x,
                                        slot.offset.y + change.position.y,
                                    )
                                    reorderUnderPointer(type.id)
                                }
                            },
                        )
                    }
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            val slot = slotOf(type.id)
                            if (slot != null) {
                                // Keep the grabbed point of the (scaled) tile under the finger.
                                translationX = dragPointer.x - slot.offset.x -
                                    (dragGrab.x * DragScale + (1f - DragScale) * slot.size.width / 2f)
                                translationY = dragPointer.y - slot.offset.y -
                                    (dragGrab.y * DragScale + (1f - DragScale) * slot.size.height / 2f)
                            }
                            scaleX = DragScale
                            scaleY = DragScale
                            shadowElevation = 8.dp.toPx()
                        }
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
