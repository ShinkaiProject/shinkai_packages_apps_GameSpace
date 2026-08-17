package com.android.gamespace.widget.tiles

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView

class TilePagerAdapter(
    private val context: Context,
    private val pages: List<List<TileType>>
) : RecyclerView.Adapter<TilePagerAdapter.PageHolder>() {

    class PageHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = PageHolder(
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    )

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.container.removeAllViews()
        pages[position].chunked(2).forEach { rowTiles ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            repeat(2) { index ->
                val cell: View = if (index < rowTiles.size) {
                    rowTiles[index].create(context)
                } else {
                    View(context).apply { visibility = View.INVISIBLE }
                }
                cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                row.addView(cell)
            }
            holder.container.addView(row)
        }
    }

    override fun getItemCount() = pages.size
}
