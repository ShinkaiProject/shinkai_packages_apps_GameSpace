package com.android.gamespace.ui.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.ui.overlay.tiles.TileType
import androidx.compose.ui.unit.max

/**
 * What the panel window shows. It fades in by itself once it is on the screen; [closing] fades it
 * out, and the window is taken off the screen by whoever set that, when the fade is over.
 */
@Stable
class PanelState {
    var closing by mutableStateOf(false)
}

/** How long the panel takes to fade in or out. */
const val PANEL_FADE_MS = 300

/** DecelerateInterpolator with its factor of 1. */
private val DecelerateEasing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

/** The space around the card at the edges of the screen, in pixels. */
private const val EDGE_PX = 16

/** The least height of the tile editor, for when the content was shorter. */
private const val MIN_EDIT_HEIGHT_DP = 240

/**
 * The window of the panel: the whole screen, with the card of the panel next to the bar. A touch
 * anywhere else than the card closes it ([onDismiss]).
 *
 * @param dockedEnd the bar is at the end of the screen, and the card is put at its inner side.
 * @param barWidthPx how much of the side the bar takes, so that the card does not cover it.
 */
@Composable
fun GamePanel(
    state: PanelState,
    appSettings: AppSettings,
    dockedEnd: Boolean,
    barWidthPx: Int,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    // Set once it is on the screen: a value that is there from the start does not animate.
    var opened by remember { mutableStateOf(false) }
    val fade by animateFloatAsState(
        if (opened && !state.closing) 1f else 0f,
        tween(PANEL_FADE_MS),
        label = "panelFade",
    )

    // The card slides down to the height of the bar when the panel opens.
    var targetTop by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        opened = true
        targetTop = appSettings.y
    }
    val top by animateIntAsState(targetTop, tween(300, easing = DecelerateEasing), label = "panelTop")

    val edge = with(density) { EDGE_PX.toDp() }
    val bar = with(density) { barWidthPx.toDp() }
    val noFeedback = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(fade)
            .clickable(interactionSource = noFeedback, indication = null, onClick = onDismiss)
            .padding(
                start = if (dockedEnd) edge else bar,
                end = if (dockedEnd) bar else edge,
                top = edge,
                bottom = edge,
            ),
    ) {
        PanelCard(
            appSettings = appSettings,
            modifier = Modifier
                .align(if (dockedEnd) Alignment.TopEnd else Alignment.TopStart)
                .padding(top = with(density) { top.toDp() }),
        )
    }
}

@Composable
private fun PanelCard(appSettings: AppSettings, modifier: Modifier = Modifier) {
    val colors = LocalOverlayColors.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(28.dp)
    val noFeedback = remember { MutableInteractionSource() }
    val opacity = remember { appSettings.menuOpacity / 100f }

    var editing by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var turns by remember { mutableStateOf(0) }
    val nowPlaying = rememberNowPlaying()
    val scroll = rememberScrollState()
    var normalHeight by remember { mutableStateOf(0) }
    LaunchedEffect(editing) { if (editing) scroll.scrollTo(0) }

    // The editor is as tall as the content that it stands in for, and never less than 240dp; it
    // cannot be taller than what the card may be, and gets cut there.
    val editHeight = remember(editing) {
        max(
            MIN_EDIT_HEIGHT_DP.dp,
            // The content, the margin at its end that the list takes over, less the list's own.
            with(density) { normalHeight.toDp() } + 16.dp - 8.dp,
        )
    }
    // Ending the edit shows the new tiles right away.
    val tiles = remember(editing) { appSettings.selectedTiles.mapNotNull { TileType.fromId(it) } }

    Column(
        modifier
            .width(280.dp)
            .alpha(opacity)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(colors.panel)
            // The card takes the touches that are on it; the rest of the window closes the panel.
            .clickable(interactionSource = noFeedback, indication = null, onClick = {})
            // While editing, the list scrolls on its own and the card stays where it is.
            .then(if (editing) Modifier else Modifier.verticalScroll(scroll))
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = if (editing) 0.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // The pill stays in the middle of what the edit button leaves.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { StatusPill() }
            EditButton(editing, onClick = { editing = !editing }, modifier = Modifier.padding(start = 8.dp))
        }

        if (!editing) {
            Column(Modifier.fillMaxWidth().onSizeChanged { normalHeight = it.height }) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    ControlsFlipper(page, nowPlaying, Modifier.padding(end = 8.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 10.dp)
                            .size(20.dp)
                            .rotate(animatedTurn(turns))
                            .clickable(interactionSource = noFeedback, indication = null) {
                                page = 1 - page
                                turns += 1
                            }
                            .padding(4.dp),
                    )
                }
                QuickStartRow()
                TilePager(tiles)
                MemoryRow()
            }
        } else {
            TileEditor(
                settings = appSettings,
                modifier = Modifier.padding(top = 8.dp).height(editHeight).clipToBounds(),
            )
        }
    }
}

@Composable
private fun animatedTurn(turns: Int): Float {
    val angle by animateFloatAsState(turns * 180f, tween(200), label = "chevron")
    return angle
}

/**
 * The page of the controls that is shown: the sliders or the media card. The other comes in from
 * the left and the card changes its height to it.
 */
@Composable
private fun ControlsFlipper(page: Int, nowPlaying: NowPlayingState, modifier: Modifier = Modifier) {
    val slide = remember { Animatable(0f) }
    val firstTime = remember { BooleanArray(1) { true } }
    LaunchedEffect(page) {
        if (firstTime[0]) {
            firstTime[0] = false
            return@LaunchedEffect
        }
        slide.snapTo(-1f)
        slide.animateTo(0f, tween(300))
    }
    Box(
        modifier
            .fillMaxWidth()
            .animateContentSize(tween(200, easing = FastOutSlowInEasing))
            .clipToBounds(),
    ) {
        Box(Modifier.graphicsLayer { translationX = slide.value * size.width }) {
            if (page == 0) PanelSliders() else MediaCard(nowPlaying)
        }
    }
}
