package com.android.gamespace.widget.compose.pager

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf
import com.android.gamespace.widget.tiles.TileType

@Composable
fun GameSpaceTilePager(
    selectedTiles: List<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bypassChargeSupported = remember(context) {
        context.entryPointOf<ServiceViewEntryPoint>().screenUtils().isBypassChargeSupported()
    }
    val pages = remember(selectedTiles, bypassChargeSupported) {
        selectedTiles
            .mapNotNull { TileType.fromId(it) }
            .filter { it != TileType.BYPASS_CHARGE || bypassChargeSupported }
            .chunked(TileLayoutModel.TILES_PER_PAGE)
    }
    val pageCount = pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        key = { it },
    ) { page ->
        GameSpaceTilePage(tiles = pages.getOrNull(page).orEmpty())
    }
}
