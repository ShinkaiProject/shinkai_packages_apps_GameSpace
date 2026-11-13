package com.android.gamespace.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.ui.overlay.tiles.TileType
import kotlinx.coroutines.launch

/** Where a tile is held and dragged: which one, and how far it is from the cell it sits in. */
@Stable
private class TileDrag {
    var key by mutableStateOf<String?>(null)
    var offset by mutableStateOf(Offset.Zero)
    var moved = false
}

/** The rows in front of the first tile of the panel: the reset button and the label. */
private const val ACTIVE_OFFSET = 2
private const val DRAG_ALPHA = 0.85f

/**
 * Edits the tiles of the game panel in place, on a grid that stands in for the panel's content.
 *
 * Tiles in the panel come first and can be reordered by holding one and dragging it; tapping one
 * takes it out. Tiles that are not in the panel follow below, and tapping one puts it at the end.
 * Every change is saved at once; the panel shows it when editing ends.
 */
@Composable
fun TileEditor(settings: AppSettings, modifier: Modifier = Modifier) {
    val model = remember {
        TileLayoutModel(
            allIds = TileType.values().map { it.id },
            initialActive = settings.selectedTiles,
            defaultIds = AppSettings.DEFAULT_TILES.split(","),
        )
    }
    var active by remember { mutableStateOf(model.active.toList()) }
    var available by remember { mutableStateOf(model.available) }
    fun refresh() {
        active = model.active.toList()
        available = model.available
    }
    fun persist() {
        settings.selectedTiles = model.active.toList()
    }
    fun changed() {
        persist()
        refresh()
    }

    val activeTypes = active.mapNotNull { TileType.fromId(it) }
    val availableTypes = available.mapNotNull { TileType.fromId(it) }

    val grid = rememberLazyGridState()
    val drag = remember { TileDrag() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { SCROLL_EDGE_DP.dp.toPx() }
    val step = with(LocalDensity.current) { SCROLL_STEP_DP.dp.toPx() }

    fun dragBy(amount: Offset) {
        val key = drag.key ?: return
        drag.offset += amount
        val info = grid.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        // The layout may not have caught up with the last move yet; the model is ahead of it.
        if (dragged.index != ACTIVE_OFFSET + model.active.indexOf(key)) return

        val centerX = dragged.offset.x + dragged.size.width / 2f + drag.offset.x
        val centerY = dragged.offset.y + dragged.size.height / 2f + drag.offset.y
        val target = info.visibleItemsInfo.firstOrNull { other ->
            val otherKey = other.key as? String
            otherKey != null && otherKey != key && otherKey in model.active &&
                centerX >= other.offset.x && centerX <= other.offset.x + other.size.width &&
                centerY >= other.offset.y && centerY <= other.offset.y + other.size.height
        }
        if (target != null) {
            val from = model.active.indexOf(key)
            val to = model.active.indexOf(target.key as String)
            if (model.reorder(from, to)) {
                // The tile takes the cell of the one it was dropped on; it must stay under the
                // finger, so the distance to its cell is what changes.
                drag.offset += Offset(
                    (dragged.offset.x - target.offset.x).toFloat(),
                    (dragged.offset.y - target.offset.y).toFloat(),
                )
                drag.moved = true
                refresh()
            }
        }

        // Near the top or the bottom edge the list goes along with the finger.
        val top = dragged.offset.y + drag.offset.y
        val bottom = top + dragged.size.height
        val delta = when {
            top < edge -> -step
            bottom > info.viewportSize.height - edge -> step
            else -> 0f
        }
        if (delta != 0f) {
            scope.launch {
                val consumed = grid.scrollBy(delta)
                // What scrolls away under the finger is added back to where the tile is held.
                drag.offset += Offset(0f, consumed)
            }
        }
    }

    fun endDrag() {
        if (drag.moved) persist()
        drag.moved = false
        drag.key = null
        drag.offset = Offset.Zero
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = grid,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        item(key = "reset", span = { GridItemSpan(maxLineSpan) }) {
            ResetRow(onClick = { if (model.reset()) changed() })
        }

        item(key = "label_active", span = { GridItemSpan(maxLineSpan) }) {
            LabelRow(R.string.panel_edit_active)
        }
        if (activeTypes.isEmpty()) {
            item(key = "note_active", span = { GridItemSpan(maxLineSpan) }) {
                NoteRow(R.string.tile_layout_empty_active)
            }
        }
        items(activeTypes, key = { it.id }) { type ->
            val dragging = drag.key == type.id
            EditTile(
                type = type,
                active = true,
                onClick = { if (model.remove(type.id)) changed() },
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        if (dragging) {
                            translationX = drag.offset.x
                            translationY = drag.offset.y
                            alpha = DRAG_ALPHA
                        }
                    },
                dragModifier = Modifier.pointerInput(type.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            drag.key = type.id
                            drag.offset = Offset.Zero
                            drag.moved = false
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragBy(amount)
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() },
                    )
                },
            )
        }

        item(key = "label_available", span = { GridItemSpan(maxLineSpan) }) {
            LabelRow(R.string.panel_edit_available)
        }
        if (availableTypes.isEmpty()) {
            item(key = "note_available", span = { GridItemSpan(maxLineSpan) }) {
                NoteRow(R.string.tile_layout_empty_available)
            }
        }
        items(availableTypes, key = { it.id }) { type ->
            EditTile(
                type = type,
                active = false,
                onClick = { if (model.add(type.id)) changed() },
            )
        }

        item(key = "hint", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.panel_edit_hint),
                color = LocalOverlayColors.current.secondaryText,
                style = overlayText(11.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 24.dp),
            )
        }
    }
}

private const val SCROLL_EDGE_DP = 48
private const val SCROLL_STEP_DP = 8

@Composable
private fun ResetRow(onClick: () -> Unit) {
    val colors = LocalOverlayColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = colors.ripple),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tile_layout_reset),
            color = colors.onTile,
            style = overlayText(12.sp),
        )
    }
}

@Composable
private fun LabelRow(text: Int) {
    Text(
        text = stringResource(text),
        color = LocalOverlayColors.current.accent,
        style = overlayText(12.sp, FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun NoteRow(text: Int) {
    Text(
        text = stringResource(text),
        color = LocalOverlayColors.current.secondaryText,
        style = overlayText(11.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

/** A tile of the editor: the look of a tile that is on if it is in the panel, off if it is not. */
@Composable
private fun EditTile(
    type: TileType,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    val colors = LocalOverlayColors.current
    val content = if (active) colors.onActive else colors.onTile
    Row(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) colors.active else colors.tile)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = colors.ripple),
                onClick = onClick,
            )
            // After the click, so it sees the touch first and keeps the tap that ends a drag.
            .then(dragModifier)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(type.iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = stringResource(type.titleRes),
                color = content,
                style = overlayText(11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
