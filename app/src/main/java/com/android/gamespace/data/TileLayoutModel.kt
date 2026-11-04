package com.android.gamespace.data

/** Where a row sits in a group of rows that are drawn as one rounded block. */
enum class GroupPosition {
    SINGLE, TOP, MIDDLE, BOTTOM;

    companion object {
        fun of(index: Int, size: Int) = when {
            size <= 1 -> SINGLE
            index <= 0 -> TOP
            index >= size - 1 -> BOTTOM
            else -> MIDDLE
        }
    }
}

/**
 * The tiles of the game panel, in the order they are shown.
 *
 * Tiles are identified by their id. [active] are the tiles in the panel, in order, and
 * [available] are the known tiles that are not in the panel. Every operation keeps the list free
 * of duplicates and of unknown ids, and returns whether anything changed.
 */
class TileLayoutModel(
    private val allIds: List<String>,
    initialActive: List<String>,
    private val defaultIds: List<String>,
) {
    private val _active = initialActive.filter { it in allIds }.distinct().toMutableList()

    /** Tiles in the panel, in display order. */
    val active: List<String> get() = _active

    /** Known tiles that are not in the panel. */
    val available: List<String> get() = allIds.filter { it !in _active }

    /** 1-based page the tile at [index] ends up on. */
    fun pageOf(index: Int) = index / TILES_PER_PAGE + 1

    /** Appends [id] to the end of the panel. */
    fun add(id: String): Boolean {
        if (id !in allIds || id in _active) return false
        _active.add(id)
        return true
    }

    fun remove(id: String) = _active.remove(id)

    /** Moves [id] to [index] (counted in the list as it is after the move). */
    fun move(id: String, index: Int): Boolean {
        val from = _active.indexOf(id)
        if (from < 0) return false
        val to = index.coerceIn(0, _active.lastIndex)
        if (to == from) return false
        _active.add(to, _active.removeAt(from))
        return true
    }

    fun moveToFront(id: String) = move(id, 0)

    /** Moves [id] by [delta] places: negative towards the front, positive towards the back. */
    fun moveBy(id: String, delta: Int): Boolean {
        val from = _active.indexOf(id)
        return from >= 0 && move(id, from + delta)
    }

    /** Moves the tile at index [from] to index [to], as a drag does. */
    fun reorder(from: Int, to: Int): Boolean {
        if (from !in _active.indices || to !in _active.indices || from == to) return false
        _active.add(to, _active.removeAt(from))
        return true
    }

    /** Restores the default tiles and order. @return false if it was already like that. */
    fun reset(): Boolean {
        val defaults = defaultIds.filter { it in allIds }.distinct()
        if (defaults == _active) return false
        _active.clear()
        _active.addAll(defaults)
        return true
    }

    companion object {
        /** The panel shows its tiles in pages of this many. */
        const val TILES_PER_PAGE = 4
    }
}
