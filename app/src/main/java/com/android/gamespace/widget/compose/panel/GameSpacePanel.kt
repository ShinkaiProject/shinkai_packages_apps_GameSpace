package com.android.gamespace.widget.compose.panel

import android.content.SharedPreferences
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.gamespace.data.AppSettings
import com.android.gamespace.widget.NowPlayingController
import com.android.gamespace.widget.compose.editor.PanelTileEditor

@Composable
fun GameSpacePanel(
    appSettings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTiles by remember { mutableStateOf(appSettings.selectedTiles) }
    var editing by rememberSaveable { mutableStateOf(false) }

    LifecycleStartEffect(appSettings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppSettings.KEY_SELECTED_TILES) {
                selectedTiles = appSettings.selectedTiles
            }
        }
        appSettings.registerOnChange(listener)
        selectedTiles = appSettings.selectedTiles
        onStopOrDispose { appSettings.unregisterOnChange(listener) }
    }

    val systemState = remember(context) { PanelSystemState(context) }
    LifecycleStartEffect(systemState) {
        systemState.start()
        onStopOrDispose { systemState.stop() }
    }
    val controlsState by systemState.state.collectAsStateWithLifecycle()

    val nowPlaying = remember(context) { NowPlayingController(context) }
    LifecycleStartEffect(nowPlaying) {
        nowPlaying.start()
        onStopOrDispose { nowPlaying.stop() }
    }
    val nowPlayingState by nowPlaying.state.collectAsStateWithLifecycle()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            PanelHeader(
                editing = editing,
                onToggleEdit = { editing = !editing },
            )

            AnimatedContent(
                targetState = editing,
                label = "panel-mode",
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { isEditing ->
                if (isEditing) {
                    PanelTileEditor(
                        selectedTiles = selectedTiles,
                        onSelectedTilesChange = { tiles ->
                            selectedTiles = tiles
                            appSettings.selectedTiles = tiles
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(min = 240.dp, max = 360.dp),
                    )
                } else {
                    PanelContent(
                        selectedTiles = selectedTiles,
                        controlsState = controlsState,
                        nowPlayingState = nowPlayingState,
                        onBrightnessChange = systemState::setBrightness,
                        onBrightnessDragStart = systemState::beginBrightnessDrag,
                        onBrightnessDragEnd = systemState::endBrightnessDrag,
                        onVolumeChange = systemState::setVolume,
                        onVolumeDragStart = systemState::beginVolumeDrag,
                        onVolumeDragEnd = systemState::endVolumeDrag,
                        onToggleAutoBrightness = systemState::toggleAutoBrightness,
                        onCycleRingerMode = systemState::cycleRingerMode,
                        nowPlaying = nowPlaying,
                    )
                }
            }
        }
    }
}
