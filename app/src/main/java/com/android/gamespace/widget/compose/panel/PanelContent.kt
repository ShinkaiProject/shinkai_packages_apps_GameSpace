package com.android.gamespace.widget.compose.panel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.android.gamespace.widget.NowPlayingController
import com.android.gamespace.widget.NowPlayingState
import com.android.gamespace.widget.compose.memory.MemoryUsageIndicator
import com.android.gamespace.widget.compose.pager.GameSpaceTilePager
import com.android.gamespace.widget.compose.quickstart.QuickStartApps

@Composable
fun PanelContent(
    selectedTiles: List<String>,
    controlsState: PanelControlsState,
    nowPlayingState: NowPlayingState,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessDragStart: () -> Unit,
    onBrightnessDragEnd: () -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeDragStart: () -> Unit,
    onVolumeDragEnd: () -> Unit,
    onToggleAutoBrightness: () -> Unit,
    onCycleRingerMode: () -> Unit,
    nowPlaying: NowPlayingController,
    modifier: Modifier = Modifier,
) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        PanelControls(
            controlsState = controlsState,
            nowPlayingState = nowPlayingState,
            onBrightnessChange = onBrightnessChange,
            onBrightnessDragStart = onBrightnessDragStart,
            onBrightnessDragEnd = onBrightnessDragEnd,
            onVolumeChange = onVolumeChange,
            onVolumeDragStart = onVolumeDragStart,
            onVolumeDragEnd = onVolumeDragEnd,
            onToggleAutoBrightness = onToggleAutoBrightness,
            onCycleRingerMode = onCycleRingerMode,
            nowPlaying = nowPlaying,
        )

        QuickStartApps(
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )

        GameSpaceTilePager(
            selectedTiles = selectedTiles,
            modifier = Modifier
                .padding(top = if (landscape) 4.dp else 8.dp)
                .height(120.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            MemoryUsageIndicator()
        }
    }
}
