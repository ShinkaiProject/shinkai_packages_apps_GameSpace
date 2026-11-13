package com.android.gamespace.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.gamespace.R
import com.android.gamespace.utils.TaskFpsMonitor
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlinx.coroutines.launch

/**
 * What the bar shows. The service owns it and changes it; the bar only reads it.
 */
@Stable
class GameBarState {
    /** The buttons are out and the bar is a pill; otherwise it is a tab on the edge. */
    var expanded by mutableStateOf(false)

    /** The menu switcher is being dragged. */
    var dragging by mutableStateOf(false)

    /** The side it is docked on: the end of the screen, or else the start. */
    var dockedEnd by mutableStateOf(false)

    /** The frame rate stands in the collapsed bar, where the arrow would be. */
    var showFps by mutableStateOf(false)

    /** An FPS recording is running: the collapsed bar is tinted for it. */
    var fpsRecording by mutableStateOf(false)

    /** The screen recorder is recording. */
    var screenRecording by mutableStateOf(false)

    /** There is a screen recorder, and so a button for it. */
    var hasRecorder by mutableStateOf(false)

    /** 0..1, the opacity that the user has set for the bar. */
    var opacity by mutableStateOf(1f)

    /** False until the bar has been put in its place; it is not shown before. */
    var revealed by mutableStateOf(false)
}

/** The sizes in pixels that the bar was given in px and not dp: its margin and its padding. */
internal const val EXPANDED_MARGIN_PX = 48
private const val EXPANDED_PADDING_PX = 8

/** How far the docked bar is moved over the edge, so that its outer corners are out of sight. */
private const val EDGE_SHIFT_PX = 22f

/**
 * The bar of the game: a menu switcher that is a tab on the edge of the screen, and that opens
 * into a column of buttons.
 */
@Composable
fun GameBar(
    state: GameBarState,
    initialPosition: () -> IntOffset,
    onMove: (x: Int, y: Int) -> Unit,
    onMoveComplete: () -> Unit,
    onToggle: () -> Unit,
    onPanel: () -> Unit,
    onPanelLongClick: () -> Unit,
    onSidebar: () -> Unit,
    onScreenshot: () -> Unit,
    onRecord: () -> Unit,
) {
    val colors = LocalOverlayColors.current
    val density = LocalDensity.current
    val reveal by animateFloatAsState(if (state.revealed) 1f else 0f, tween(300), label = "reveal")

    val shape = when {
        state.expanded || state.dragging -> AbsoluteRoundedCornerShape(32.dp)
        // A tab: round on the side that faces the screen.
        !state.dockedEnd -> AbsoluteRoundedCornerShape(
            topLeft = 0.dp, bottomLeft = 0.dp, topRight = 36.dp, bottomRight = 36.dp,
        )
        else -> AbsoluteRoundedCornerShape(
            topLeft = 36.dp, bottomLeft = 36.dp, topRight = 0.dp, bottomRight = 0.dp,
        )
    }
    val margin = with(density) { (if (state.expanded) EXPANDED_MARGIN_PX else 0).toDp() }
    val padding = with(density) { (if (state.expanded) EXPANDED_PADDING_PX else 0).toDp() }

    Column(
        Modifier
            // One opacity for the bar as a whole.
            .alpha(state.opacity * reveal)
            .padding(horizontal = margin)
            .graphicsLayer {
                translationX = when {
                    state.dragging -> 0f
                    state.dockedEnd -> EDGE_SHIFT_PX
                    else -> -EDGE_SHIFT_PX
                }
            }
            .clip(shape)
            .background(colors.bar)
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MenuSwitcher(
            state = state,
            dragModifier = Modifier.windowDrag(
                initialPosition = initialPosition,
                onMove = onMove,
                onComplete = onMoveComplete,
                onClick = onToggle,
            ),
        )
        AnimatedVisibility(
            visible = state.expanded,
            enter = fadeIn(tween(220)) + expandVertically(tween(220)),
            exit = fadeOut(tween(220)) + shrinkVertically(tween(220)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BarButton(R.drawable.ic_panel, 16.dp, colors.active, onPanel, onPanelLongClick)
                BarButton(R.drawable.ic_action_sidebar, 20.dp, colors.active, onSidebar)
                BarButton(R.drawable.ic_action_screenshot, 20.dp, colors.active, onScreenshot)
                if (state.hasRecorder) {
                    BarButton(
                        icon = R.drawable.ic_action_record,
                        iconSize = 20.dp,
                        background = if (state.screenRecording) colors.recording else colors.active,
                        onClick = onRecord,
                    )
                }
            }
        }
    }
}

/** A button of the bar: a circle, with the icon in the middle at its own size. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarButton(
    icon: Int,
    iconSize: Dp,
    background: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalOverlayColors.current
    Box(
        Modifier
            .padding(top = 5.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = colors.ripple),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.onActive,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The top of the bar: an arrow towards the edge it sits at while collapsed, a cross while it is
 * open, and the grip of a drag while it is moved. With the frame rate switched on, the frame rate
 * stands in the place of the arrow.
 *
 * It is 36dp high and the icon over the (empty) line of text is a little taller than that, as it
 * was in the XML: the contents are centred and cut off at the edges of the 36dp.
 */
@Composable
private fun MenuSwitcher(state: GameBarState, dragModifier: Modifier) {
    val colors = LocalOverlayColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val format = remember { DecimalFormat("#").apply { roundingMode = RoundingMode.HALF_EVEN } }
    var fpsText by remember { mutableStateOf("") }

    // Shared with the FPS recorder: the system reports FPS to one listener per task only.
    DisposableEffect(state.showFps) {
        if (!state.showFps) return@DisposableEffect onDispose {}
        val monitor = context.entryPointOf<ServiceViewEntryPoint>().taskFpsMonitor()
        val listener = TaskFpsMonitor.Listener { fps -> scope.launch { fpsText = format.format(fps) } }
        monitor.addListener(listener)
        onDispose { monitor.removeListener(listener) }
    }

    val icon: Int? = when {
        state.showFps -> null
        state.dragging -> R.drawable.ic_drag
        state.expanded -> R.drawable.ic_close
        state.dockedEnd -> R.drawable.ic_arrow_right
        else -> R.drawable.ic_arrow_left
    }
    // The cross and the grip make it 36dp wide; an arrow or a number, at least three ems.
    val square = icon == R.drawable.ic_close || icon == R.drawable.ic_drag
    val minWidth = with(LocalDensity.current) { 13.sp.toDp() * 3 }
    val recording = state.fpsRecording
    val iconTint = if (recording && icon != R.drawable.ic_close) colors.recording else colors.accent
    val textColor = if (recording) colors.recording else colors.onBarText

    Box(
        Modifier
            .height(36.dp)
            .then(if (square) Modifier.width(36.dp) else Modifier.widthIn(min = minWidth))
            .clipToBounds()
            .then(dragModifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.wrapContentHeight(unbounded = true),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = if (state.showFps) fpsText else "",
                color = textColor,
                style = overlayText(13.sp).copy(lineHeight = 15.sp),
                maxLines = 1,
            )
        }
    }
}
