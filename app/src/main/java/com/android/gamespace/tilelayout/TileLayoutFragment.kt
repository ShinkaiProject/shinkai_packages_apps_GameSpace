package com.android.gamespace.tilelayout

import android.app.AlertDialog
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.data.GroupPosition
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.widget.showMaterial3PopupMenu
import com.android.gamespace.widget.tiles.TileType

/**
 * Chooses the tiles of the game panel and their order.
 *
 * Tiles in the panel are listed first and can be dragged by their handle, or moved from the menu
 * that opens when a row is tapped. Tiles that are not in the panel follow below; tapping one adds
 * it to the end. Every change is saved at once and shows up the next time the panel opens.
 */
class TileLayoutFragment : Fragment(R.layout.fragment_tile_layout) {

    private val settings by lazy { AppSettings(requireContext().applicationContext) }
    private val model by lazy {
        TileLayoutModel(
            allIds = TileType.entries.map { it.id },
            initialActive = settings.selectedTiles,
            defaultIds = AppSettings.DEFAULT_TILES.split(","),
        )
    }

    private var touchHelper: ItemTouchHelper? = null
    private val adapter = TileLayoutAdapter(
        onItemClick = { row, anchor -> onItemClick(row, anchor) },
        onActionClick = { row -> if (row.active) removeTile(row.type) else addTile(row.type) },
        onStartDrag = { holder -> touchHelper?.startDrag(holder) },
    )

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.tile_layout_menu, menu)
            menu.findItem(R.id.menu_more)?.actionView?.setOnClickListener { showMoreMenu(it) }
        }

        override fun onMenuItemSelected(menuItem: MenuItem) = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val list = view.findViewById<RecyclerView>(R.id.tile_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        touchHelper = ItemTouchHelper(dragCallback).also { it.attachToRecyclerView(list) }

        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        refresh()
    }

    override fun onDestroyView() {
        touchHelper?.attachToRecyclerView(null)
        touchHelper = null
        super.onDestroyView()
    }

    /** Rebuilds the list from the model. */
    private fun refresh() {
        adapter.submit(buildRows())
    }

    private fun buildRows(): List<TileRow> {
        val rows = mutableListOf<TileRow>()

        rows += TileRow.Header(R.string.tile_layout_active_title, R.string.tile_layout_active_hint)
        val active = model.active.mapNotNull { TileType.fromId(it) }
        if (active.isEmpty()) rows += TileRow.Empty(R.string.tile_layout_empty_active)
        active.forEachIndexed { index, type ->
            rows += TileRow.Item(type, true, model.pageOf(index), GroupPosition.of(index, active.size))
        }

        rows += TileRow.Header(R.string.tile_layout_available_title, R.string.tile_layout_available_hint)
        val available = model.available.mapNotNull { TileType.fromId(it) }
        if (available.isEmpty()) rows += TileRow.Empty(R.string.tile_layout_empty_available)
        available.forEachIndexed { index, type ->
            rows += TileRow.Item(type, false, 0, GroupPosition.of(index, available.size))
        }
        return rows
    }

    /** Saves the order for the panel. */
    private fun persist() {
        settings.selectedTiles = model.active
    }

    private fun changed() {
        persist()
        refresh()
    }

    private fun addTile(type: TileType) {
        if (model.add(type.id)) changed()
    }

    private fun removeTile(type: TileType) {
        if (model.remove(type.id)) changed()
    }

    private fun onItemClick(row: TileRow.Item, anchor: View) {
        if (row.active) showTileMenu(row.type, anchor) else addTile(row.type)
    }

    /** What can be done with a tile in the panel; moves that would change nothing are hidden. */
    private fun showTileMenu(type: TileType, anchor: View) {
        val index = model.active.indexOf(type.id)
        if (index < 0) return
        showMaterial3PopupMenu(
            anchor = anchor,
            menuRes = R.menu.tile_layout_item_menu,
            prepare = { menu ->
                // One place from the front, "up" already gets there.
                menu.findItem(R.id.menu_move_front)?.isVisible = index > 1
                menu.findItem(R.id.menu_move_up)?.isVisible = index > 0
                menu.findItem(R.id.menu_move_down)?.isVisible = index < model.active.lastIndex
            },
            onItemClick = { item ->
                val moved = when (item.itemId) {
                    R.id.menu_move_front -> model.moveToFront(type.id)
                    R.id.menu_move_up -> model.moveBy(type.id, -1)
                    R.id.menu_move_down -> model.moveBy(type.id, 1)
                    R.id.menu_remove -> model.remove(type.id)
                    else -> return@showMaterial3PopupMenu false
                }
                if (moved) changed()
                true
            },
        )
    }

    private fun showMoreMenu(anchor: View) {
        showMaterial3PopupMenu(
            anchor = anchor,
            menuRes = R.menu.tile_layout_popup_menu,
            onItemClick = { item ->
                when (item.itemId) {
                    R.id.menu_reset -> { confirmReset(); true }
                    else -> false
                }
            },
        )
    }

    private fun confirmReset() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.tile_layout_reset_message)
            .setPositiveButton(R.string.tile_layout_reset) { _, _ -> if (model.reset()) changed() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Dragging by the handle: only tiles in the panel move, and only among themselves. */
    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        private var dragged = false

        override fun isLongPressDragEnabled() = false

        override fun isItemViewSwipeEnabled() = false

        override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val row = adapter.rowAt(viewHolder.bindingAdapterPosition)
            return if (row is TileRow.Item && row.active) {
                ItemTouchHelper.UP or ItemTouchHelper.DOWN
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
            return row is TileRow.Item && row.active
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            // The panel's tiles follow their header, so row N is tile N - 1.
            if (!model.reorder(from - ACTIVE_OFFSET, to - ACTIVE_OFFSET)) return false
            adapter.moveRow(from, to)
            dragged = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) viewHolder?.itemView?.alpha = DRAG_ALPHA
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1f
            if (dragged) {
                dragged = false
                persist()
                // Page numbers and corners changed; update them once the drop has settled.
                recyclerView.post { if (isAdded && view != null) refresh() }
            }
        }
    }

    private companion object {
        /** Rows in front of the first tile of the panel: just its header. */
        const val ACTIVE_OFFSET = 1
        const val DRAG_ALPHA = 0.85f
    }
}
