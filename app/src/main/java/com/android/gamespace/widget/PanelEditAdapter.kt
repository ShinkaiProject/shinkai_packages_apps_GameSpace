package com.android.gamespace.widget

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.android.gamespace.R
import com.android.gamespace.widget.tiles.TileType

/** One cell of the grid shown while the panel's tiles are being edited. */
sealed class PanelEditRow {
    /** Restores the default tiles. */
    object Reset : PanelEditRow()

    /** Title above a group of tiles. */
    data class Label(@StringRes val textRes: Int) : PanelEditRow()

    /** A tile; [active] ones are in the panel, the others can be added to it. */
    data class Tile(val type: TileType, val active: Boolean) : PanelEditRow()

    /** What stands in for a group without tiles. */
    data class Note(@StringRes val textRes: Int) : PanelEditRow()

    /** The hint at the very end of the grid. */
    data class Hint(@StringRes val textRes: Int) : PanelEditRow()
}

class PanelEditAdapter(
    private val onTileClick: (PanelEditRow.Tile) -> Unit,
    private val onResetClick: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: MutableList<PanelEditRow> = mutableListOf()

    fun rowAt(position: Int): PanelEditRow? = rows.getOrNull(position)

    /** Replaces the rows and animates what changed. */
    fun submit(newRows: List<PanelEditRow>) {
        val old = rows
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newRows.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val a = old[oldPos]
                val b = newRows[newPos]
                return when {
                    a is PanelEditRow.Reset && b is PanelEditRow.Reset -> true
                    a is PanelEditRow.Label && b is PanelEditRow.Label -> a.textRes == b.textRes
                    a is PanelEditRow.Tile && b is PanelEditRow.Tile -> a.type == b.type
                    a is PanelEditRow.Note && b is PanelEditRow.Note -> a.textRes == b.textRes
                    a is PanelEditRow.Hint && b is PanelEditRow.Hint -> a.textRes == b.textRes
                    else -> false
                }
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == newRows[newPos]
        })
        rows = newRows.toMutableList()
        diff.dispatchUpdatesTo(this)
    }

    /** Follows a drag: the row at [from] now sits at [to]. */
    fun moveRow(from: Int, to: Int) {
        if (from !in rows.indices || to !in rows.indices || from == to) return
        rows.add(to, rows.removeAt(from))
        notifyItemMoved(from, to)
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is PanelEditRow.Reset -> TYPE_RESET
        is PanelEditRow.Label -> TYPE_LABEL
        is PanelEditRow.Tile -> TYPE_TILE
        is PanelEditRow.Note -> TYPE_NOTE
        is PanelEditRow.Hint -> TYPE_HINT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_RESET -> ResetHolder(inflater.inflate(R.layout.item_panel_edit_reset, parent, false))
            TYPE_LABEL -> TextHolder(inflater.inflate(R.layout.item_panel_edit_label, parent, false))
            TYPE_NOTE -> TextHolder(inflater.inflate(R.layout.item_panel_edit_note, parent, false))
            TYPE_HINT -> TextHolder(inflater.inflate(R.layout.item_panel_edit_hint, parent, false))
            else -> TileHolder(inflater.inflate(R.layout.item_panel_edit_tile, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is PanelEditRow.Reset -> Unit
            is PanelEditRow.Label -> (holder as TextHolder).text.setText(row.textRes)
            is PanelEditRow.Note -> (holder as TextHolder).text.setText(row.textRes)
            is PanelEditRow.Hint -> (holder as TextHolder).text.setText(row.textRes)
            is PanelEditRow.Tile -> (holder as TileHolder).bind(row)
        }
    }

    private class TextHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text = view as TextView
    }

    private inner class ResetHolder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            view.setOnClickListener { onResetClick() }
        }
    }

    private inner class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: ImageView = view.findViewById(R.id.tile_icon)
        private val title: TextView = view.findViewById(R.id.tile_title)

        fun bind(row: PanelEditRow.Tile) {
            icon.setImageResource(row.type.iconRes)
            title.setText(row.type.titleRes)
            // Tiles in the panel look like a tile that is on; the others like one that is off.
            // The state is passed on to the icon and the title, which colour themselves by it.
            itemView.isSelected = row.active
            // The row may have moved since it was bound, so ask for its current state.
            itemView.setOnClickListener {
                (rowAt(bindingAdapterPosition) as? PanelEditRow.Tile)?.let(onTileClick)
            }
        }
    }

    private companion object {
        const val TYPE_RESET = 0
        const val TYPE_LABEL = 1
        const val TYPE_TILE = 2
        const val TYPE_NOTE = 3
        const val TYPE_HINT = 4
    }
}
