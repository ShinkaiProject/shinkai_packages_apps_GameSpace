package com.android.gamespace.display

import android.graphics.Rect
import android.view.View
import androidx.preference.PreferenceGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * Leaves [gap] pixels of space above the one row of a preference list that has the preference
 * with [key].
 *
 * The rows of a preference list are cards that sit a few pixels apart, whether they belong to the
 * same group or not, so a card that stands on its own right after a group touches it. The space is
 * given from outside the row, which leaves the row, its background and the theme alone.
 *
 * The row is found by its key, not by the preference, because the list can be built again with
 * new preferences. The adapter of a preference list can tell where a key is.
 */
internal class GapBeforeDecoration(private val key: String, private val gap: Int) :
    RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        val positions = parent.adapter as? PreferenceGroup.PreferencePositionCallback ?: return
        if (positions.getPreferenceAdapterPosition(key) == position) outRect.top = gap
    }
}
