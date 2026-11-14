package com.android.gamespace.widget.compose.pager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.gamespace.widget.tiles.TileType

@Composable
fun GameSpaceTilePage(
    tiles: List<TileType>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        GameSpaceTileGrid(tiles = tiles)
    }
}
