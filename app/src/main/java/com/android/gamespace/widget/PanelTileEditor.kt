package com.android.gamespace.widget

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.widget.tiles.TileType

/**
 * Edits the tiles of the game panel in place, on a grid that stands in for the panel's content.
 *
 * Tiles in the panel come first and can be reordered by holding one and dragging it; tapping one
 * takes it out. Tiles that are not in the panel follow below, and tapping one puts it at the end.
 * Every change is saved at once; the panel shows it when editing ends.
 *
 * Nothing is built until [open] is called, so the panel pays nothing for this while it is used
 * normally.
 */
class PanelTileEditor(
    private val list: RecyclerView,
    private val settings: AppSettings,
) {

    private lateinit var model: TileLayoutModel

    private val adapter = PanelEditAdapter(
        onTileClick = { row -> if (row.active) removeTile(row.type) else addTile(row.type) },
        onResetClick = { if (model.reset()) changed() },
    )

    private val touchHelper = ItemTouchHelper(DragCallback())

    private var prepared = false

    /** Shows the tiles as they are saved now. */
    fun open() {
        if (!prepared) prepare()
        model = newModel()
        adapter.submit(buildRows())
        list.scrollToPosition(0)
    }

    /** Releases the rows; the grid is hidden after this. */
    fun close() {
        if (prepared) adapter.submit(emptyList())
    }

    private fun prepare() {
        prepared = true
        list.layoutManager = GridLayoutManager(list.context, SPAN_COUNT).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                // Tiles sit side by side; everything else takes a whole row.
                override fun getSpanSize(position: Int) =
                    if (adapter.rowAt(position) is PanelEditRow.Tile) 1 else SPAN_COUNT
            }
        }
        list.adapter = adapter
        // Tiles change state when they are added or removed; a cross-fade of that only flickers.
        (list.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        touchHelper.attachToRecyclerView(list)

        // The panel scrolls too, and would take a vertical gesture away from the grid; the grid
        // keeps the ones that start on it.
        list.isNestedScrollingEnabled = false
        list.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> rv.parent?.requestDisallowInterceptTouchEvent(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                }
                return false
            }
        })
    }

    private fun newModel() = TileLayoutModel(
        allIds = TileType.entries.map { it.id },
        initialActive = settings.selectedTiles,
        defaultIds = AppSettings.DEFAULT_TILES.split(","),
    )

    private fun buildRows(): List<PanelEditRow> {
        val rows = mutableListOf<PanelEditRow>()
        rows += PanelEditRow.Reset

        rows += PanelEditRow.Label(R.string.panel_edit_active)
        val active = model.active.mapNotNull { TileType.fromId(it) }
        if (active.isEmpty()) rows += PanelEditRow.Note(R.string.tile_layout_empty_active)
        active.mapTo(rows) { PanelEditRow.Tile(it, active = true) }

        rows += PanelEditRow.Label(R.string.panel_edit_available)
        val available = model.available.mapNotNull { TileType.fromId(it) }
        if (available.isEmpty()) rows += PanelEditRow.Note(R.string.tile_layout_empty_available)
        available.mapTo(rows) { PanelEditRow.Tile(it, active = false) }

        rows += PanelEditRow.Note(R.string.panel_edit_hint)
        return rows
    }

    private fun persist() {
        settings.selectedTiles = model.active
    }

    private fun changed() {
        persist()
        adapter.submit(buildRows())
    }

    private fun addTile(type: TileType) {
        if (model.add(type.id)) changed()
    }

    private fun removeTile(type: TileType) {
        if (model.remove(type.id)) changed()
    }

    /** Holding a tile of the panel drags it; only those move, and only among themselves. */
    private inner class DragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
    ) {
        private var dragged = false

        override fun isLongPressDragEnabled() = true

        override fun isItemViewSwipeEnabled() = false

        override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val row = adapter.rowAt(viewHolder.bindingAdapterPosition)
            return if (row is PanelEditRow.Tile && row.active) {
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END
            } else {
                0
            }
        }

        override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val row = adapter.rowAt(target.bindingAdapterPosition)
            return row is PanelEditRow.Tile && row.active
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            // The panel's tiles follow the reset button and their label, so row N is tile N - 2.
            if (!model.reorder(from - ACTIVE_OFFSET, to - ACTIVE_OFFSET)) return false
            adapter.moveRow(from, to)
            dragged = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.apply {
                    alpha = DRAG_ALPHA
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                }
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1f
            if (dragged) {
                dragged = false
                persist()
            }
        }
    }

    private companion object {
        const val SPAN_COUNT = 2

        /** Rows in front of the first tile of the panel: the reset button and the label. */
        const val ACTIVE_OFFSET = 2
        const val DRAG_ALPHA = 0.85f
    }
}
