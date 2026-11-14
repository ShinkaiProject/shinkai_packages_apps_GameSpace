package com.android.gamespace.widget.compose.pager

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.gamespace.widget.compose.tiles.TileControllerFactory
import com.android.gamespace.widget.tiles.TileType
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameSpaceTileItem(
    type: TileType,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = remember(type) { TileControllerFactory.create(context, type) }
    LifecycleStartEffect(controller) {
        controller.start()
        onStopOrDispose { controller.stop() }
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val enabled = state.enabled
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val baseShape = MaterialTheme.shapes.medium
    val density = LocalDensity.current
    val baseCorner = remember(baseShape, density) {
        (baseShape as? CornerBasedShape)?.let { shape ->
            val cornerPx = shape.topStart.toPx(Size.Zero, density)
            if (cornerPx > 0f) with(density) { cornerPx.toDp() } else null
        }
    }

    val bounce = remember { Animatable(1f) }
    var lastEnabled by remember { mutableStateOf(enabled) }
    LaunchedEffect(enabled) {
        if (enabled != lastEnabled) {
            lastEnabled = enabled
            delay((type.ordinal % 4) * 24L)
            bounce.snapTo(0f)
            bounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tilePressScale",
    )

    val activeBackground = MaterialTheme.colorScheme.primaryContainer
    val inactiveBackground = MaterialTheme.colorScheme.surfaceContainerHigh
    val activeContent = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveContent = MaterialTheme.colorScheme.onSurfaceVariant
    val stateTransition = updateTransition(targetState = enabled, label = "tileState")
    val backgroundColor by stateTransition.animateColor(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "tileBackgroundColor",
    ) { active -> if (active) activeBackground else inactiveBackground }
    val contentColor by stateTransition.animateColor(
        transitionSpec = {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "tileContentColor",
    ) { active -> if (active) activeContent else inactiveContent }

    val morph = bounce.value
    val stateScale = 0.9f + 0.1f * morph
    val morphCorner = baseCorner?.let { corner ->
        corner * (1f + 0.5f * sin(PI.toFloat() * (1f - morph)))
    }
    val tileShape = if (morphCorner != null) RoundedCornerShape(morphCorner) else baseShape
    val tileAlpha = 0.88f + 0.12f * morph

    Surface(
        modifier = modifier
            .padding(4.dp)
            .heightIn(min = 44.dp)
            .graphicsLayer {
                scaleX = stateScale * pressScale
                scaleY = stateScale * pressScale
                alpha = tileAlpha
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = controller::toggle,
                onLongClick = controller::longPress,
            ),
        shape = tileShape,
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(state.iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(state.titleRes),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.summary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
fun GameSpaceTileGrid(
    tiles: List<TileType>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(2) { index ->
                    val tile = rowTiles.getOrNull(index)
                    if (tile == null) {
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                    } else {
                        GameSpaceTileItem(
                            type = tile,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
