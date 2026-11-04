package com.android.gamespace.tilelayout

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.android.gamespace.R
import com.android.gamespace.data.GroupPosition
import com.android.gamespace.widget.tiles.TileType

/** One row of the tile layout screen. */
sealed class TileRow {
    /** Title and hint above a section. */
    data class Header(@StringRes val titleRes: Int, @StringRes val hintRes: Int) : TileRow()

    /**
     * A tile. [active] tiles are in the panel and can be dragged; [page] is the panel page they
     * are on. [position] decides which corners of the row are rounded.
     */
    data class Item(
        val type: TileType,
        val active: Boolean,
        val page: Int,
        val position: GroupPosition,
    ) : TileRow()

    /** Shown in place of a section that has no tiles. */
    data class Empty(@StringRes val textRes: Int) : TileRow()
}

class TileLayoutAdapter(
    private val onItemClick: (TileRow.Item, View) -> Unit,
    private val onActionClick: (TileRow.Item) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: MutableList<TileRow> = mutableListOf()

    fun rowAt(position: Int): TileRow? = rows.getOrNull(position)

    /** Replaces the rows and animates what changed. */
    fun submit(newRows: List<TileRow>) {
        val old = rows
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newRows.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                val a = old[oldPos]
                val b = newRows[newPos]
                return when {
                    a is TileRow.Header && b is TileRow.Header -> a.titleRes == b.titleRes
                    a is TileRow.Item && b is TileRow.Item -> a.type == b.type
                    a is TileRow.Empty && b is TileRow.Empty -> a.textRes == b.textRes
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
        is TileRow.Header -> TYPE_HEADER
        is TileRow.Item -> TYPE_ITEM
        is TileRow.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_tile_header, parent, false))
            TYPE_EMPTY -> EmptyHolder(inflater.inflate(R.layout.item_tile_empty, parent, false))
            else -> ItemHolder(inflater.inflate(R.layout.item_tile_layout, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is TileRow.Header -> (holder as HeaderHolder).bind(row)
            is TileRow.Empty -> (holder as EmptyHolder).text.setText(row.textRes)
            is TileRow.Item -> (holder as ItemHolder).bind(row)
        }
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.header_title)
        private val hint: TextView = view.findViewById(R.id.header_hint)

        fun bind(row: TileRow.Header) {
            title.setText(row.titleRes)
            hint.setText(row.hintRes)
        }
    }

    private class EmptyHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.empty_text)
    }

    private inner class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val handle: ImageView = view.findViewById(R.id.tile_handle)
        private val icon: ImageView = view.findViewById(R.id.tile_icon)
        private val title: TextView = view.findViewById(R.id.tile_title)
        private val summary: TextView = view.findViewById(R.id.tile_summary)
        private val action: ImageButton = view.findViewById(R.id.tile_action)

        @SuppressLint("ClickableViewAccessibility") // the row menu offers the same moves
        fun bind(row: TileRow.Item) {
            val context = itemView.context

            // Rows of a section form one grouped block: big corners outside, small ones between.
            itemView.setBackgroundResource(
                when (row.position) {
                    GroupPosition.SINGLE -> R.drawable.bg_grouped_item_single
                    GroupPosition.TOP -> R.drawable.bg_grouped_item_top
                    GroupPosition.MIDDLE -> R.drawable.bg_grouped_item_middle
                    GroupPosition.BOTTOM -> R.drawable.bg_grouped_item_bottom
                }
            )

            icon.setImageResource(row.type.iconRes)
            title.setText(row.type.titleRes)

            if (row.active) {
                summary.visibility = View.VISIBLE
                summary.text = context.getString(R.string.tile_layout_page, row.page)
                handle.visibility = View.VISIBLE
                handle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                        true
                    } else {
                        false
                    }
                }
                action.setImageResource(R.drawable.ic_minus)
                action.contentDescription = context.getString(R.string.tile_layout_remove)
            } else {
                summary.visibility = View.GONE
                handle.visibility = View.GONE
                handle.setOnTouchListener(null)
                action.setImageResource(R.drawable.ic_add)
                action.contentDescription = context.getString(R.string.tile_layout_add)
            }

            // The row may have moved since it was bound, so ask for its current state.
            itemView.setOnClickListener {
                (rowAt(bindingAdapterPosition) as? TileRow.Item)?.let { onItemClick(it, itemView) }
            }
            action.setOnClickListener {
                (rowAt(bindingAdapterPosition) as? TileRow.Item)?.let(onActionClick)
            }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
        const val TYPE_EMPTY = 2
    }
}
