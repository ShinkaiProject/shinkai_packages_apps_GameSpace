@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.android.gamespace.widget.tiles

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.gamespace.R
import com.android.gamespace.data.TileLayoutModel
import kotlin.math.absoluteValue

/**
 * Immutable UI state model for the GameSpace tile pager.
 */
@Immutable
data class TilePagerUiState(
    val tiles: List<TileType> = emptyList(),
    val tilesPerPage: Int = TileLayoutModel.TILES_PER_PAGE,
    val columnsPerRow: Int = COLUMNS_PER_ROW,
) {
    val pages: List<List<TileType>> =
        if (tiles.isEmpty()) emptyList() else tiles.chunked(tilesPerPage.coerceAtLeast(1))

    val pageCount: Int get() = pages.size

    val rowsPerPage: Int =
        ((tilesPerPage + columnsPerRow - 1) / columnsPerRow).coerceAtLeast(1)

    companion object {
        const val COLUMNS_PER_ROW = 2
    }
}

/**
 * Material 3 Expressive theme wrapper for GameSpace tiles.
 *
 * Combines Android dynamic color support with GameSpace's Monet palette tokens, expressive
 * rounded corner geometry, and compact panel typography.
 */
@Composable
fun GameSpaceTileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    val colorScheme = baseScheme.copy(
        primaryContainer = colorResource(id = R.color.accentActive),
        onPrimaryContainer = colorResource(id = R.color.activeTextColor),
        surfaceContainerHigh = colorResource(id = R.color.accentInactive),
        onSurface = colorResource(id = R.color.inActiveTextColor),
        surface = colorResource(id = R.color.surface),
    )

    val expressiveShapes = remember {
        Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(22.dp),
        )
    }

    val panelTypography = remember {
        Typography(
            labelMedium = TextStyle(
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            bodySmall = TextStyle(
                fontSize = 9.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = expressiveShapes,
        typography = panelTypography,
        content = content,
    )
}

/**
 * Top-level Pure Jetpack Compose tile pager replacing XML `ViewPager2` and `TilePagerAdapter`.
 *
 * Preserves `TileLayoutModel.TILES_PER_PAGE`, two-column arrangement, incomplete row stability,
 * and responsive portrait/landscape metrics while applying Material 3 Expressive transitions.
 */
@Composable
fun GameSpaceTilePager(
    tiles: List<TileType>,
    modifier: Modifier = Modifier,
) {
    val uiState = remember(tiles) { TilePagerUiState(tiles = tiles) }
    GameSpaceTilePager(
        uiState = uiState,
        modifier = modifier,
    )
}

/**
 * Stateful overload of [GameSpaceTilePager] driven by [TilePagerUiState].
 */
@Composable
fun GameSpaceTilePager(
    uiState: TilePagerUiState,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCompactLandscape = isLandscape && configuration.screenHeightDp < 440

    val pagerTopMargin = if (isLandscape) 4.dp else 8.dp
    val minPagerHeight = if (isCompactLandscape) 112.dp else 120.dp

    GameSpaceTileTheme {
        if (uiState.pages.isEmpty()) {
            Spacer(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = pagerTopMargin)
            )
            return@GameSpaceTileTheme
        }

        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { uiState.pageCount },
        )

        LaunchedEffect(uiState.pageCount) {
            val maxPage = (uiState.pageCount - 1).coerceAtLeast(0)
            if (pagerState.currentPage > maxPage) {
                pagerState.scrollToPage(maxPage)
            }
        }

        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
            beyondBoundsPageCount = 1,
            key = { pageIndex ->
                uiState.pages.getOrNull(pageIndex)?.joinToString(separator = ",") { it.id }
                    ?: "empty_$pageIndex"
            },
            verticalAlignment = Alignment.Top,
            modifier = modifier
                .fillMaxWidth()
                .padding(top = pagerTopMargin)
                .heightIn(min = minPagerHeight),
        ) { pageIndex ->
            val pageTiles = uiState.pages.getOrNull(pageIndex).orEmpty()
            GameSpaceTilePage(
                pageTiles = pageTiles,
                pageIndex = pageIndex,
                pagerState = pagerState,
                columnsPerRow = uiState.columnsPerRow,
                rowsPerPage = uiState.rowsPerPage,
                isCompact = isCompactLandscape,
            )
        }
    }
}

/**
 * Renders a single page inside [GameSpaceTilePager] with Material 3 Expressive page transition
 * interpolation and delegates the 2x2 layout to [GameSpaceTileGrid].
 */
@Composable
fun GameSpaceTilePage(
    pageTiles: List<TileType>,
    pageIndex: Int,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    columnsPerRow: Int = TilePagerUiState.COLUMNS_PER_ROW,
    rowsPerPage: Int = TileLayoutModel.TILES_PER_PAGE / TilePagerUiState.COLUMNS_PER_ROW,
    isCompact: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val pageOffset = (
                    (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                    ).absoluteValue.coerceIn(0f, 1f)
                val scale = 1f - (0.04f * pageOffset)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (0.25f * pageOffset)
            },
    ) {
        GameSpaceTileGrid(
            pageTiles = pageTiles,
            columnsPerRow = columnsPerRow,
            rowsPerPage = rowsPerPage,
            isCompact = isCompact,
        )
    }
}

/**
 * Renders the two-column tile arrangement for a page using Compose `Column` and `Row`.
 *
 * Preserves exact tile ordering and uses weighted invisible placeholders for incomplete rows
 * or pages so existing tiles never stretch or shift position.
 */
@Composable
fun GameSpaceTileGrid(
    pageTiles: List<TileType>,
    modifier: Modifier = Modifier,
    columnsPerRow: Int = TilePagerUiState.COLUMNS_PER_ROW,
    rowsPerPage: Int = TileLayoutModel.TILES_PER_PAGE / TilePagerUiState.COLUMNS_PER_ROW,
    isCompact: Boolean = false,
) {
    val rows = remember(pageTiles, columnsPerRow) {
        pageTiles.chunked(columnsPerRow.coerceAtLeast(1))
    }
    val cellMinHeight = if (isCompact) 48.dp else 52.dp

    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
        modifier = modifier.fillMaxWidth(),
    ) {
        for (rowIndex in 0 until rowsPerPage) {
            val rowTiles = rows.getOrNull(rowIndex).orEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (colIndex in 0 until columnsPerRow) {
                    val tileType = rowTiles.getOrNull(colIndex)
                    if (tileType != null) {
                        val tile = rememberTileController(tileType = tileType)
                        GameSpaceTileCell(
                            tile = tile,
                            isCompact = isCompact,
                            minHeight = cellMinHeight,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                                .defaultMinSize(minHeight = cellMinHeight),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders an individual GameSpace quick-settings tile using Material 3 Expressive shapes,
 * spring-animated container/content colors, marquee typography, and touch/long-press handling.
 */
@Composable
fun GameSpaceTileCell(
    tile: BaseTile,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    minHeight: Dp = 52.dp,
) {
    val uiState by tile.uiState
    GameSpaceTileCell(
        uiState = uiState,
        onClick = { tile.performClick() },
        onLongClick = { tile.performLongClick() },
        modifier = modifier,
        isCompact = isCompact,
        minHeight = minHeight,
    )
}

/**
 * Stateless overload of [GameSpaceTileCell] driven by [TileUiState] and interaction callbacks.
 */
@Composable
fun GameSpaceTileCell(
    uiState: TileUiState,
    onClick: () -> Unit,
    onLongClick: () -> Boolean,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    minHeight: Dp = 52.dp,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetCornerRadius = when {
        isPressed -> 14.dp
        else -> 18.dp
    }
    val cornerRadius by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "TileCornerRadius",
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "TilePressScale",
    )

    val containerColor by animateColorAsState(
        targetValue = if (uiState.isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "TileContainerColor",
    )

    val contentColor by animateColorAsState(
        targetValue = if (uiState.isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "TileContentColor",
    )

    val tileShape = RoundedCornerShape(cornerRadius)
    val verticalPadding = if (isCompact) 8.dp else 10.dp

    Surface(
        shape = tileShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .padding(4.dp)
            .defaultMinSize(minHeight = minHeight)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(tileShape)
            .alpha(if (uiState.isEnabled) 1f else 0.5f)
            .semantics {
                selected = uiState.isSelected
                if (uiState.summary.isNotEmpty()) {
                    stateDescription = uiState.summary
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = uiState.isEnabled,
                role = Role.Switch,
                onClick = onClick,
                onLongClick = {
                    if (onLongClick()) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = verticalPadding),
        ) {
            val iconRes = uiState.iconRes.takeIf { it != 0 } ?: R.drawable.ic_search
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                )

                Text(
                    text = uiState.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                )
            }
        }
    }
}

/**
 * Remembers and manages the attach/detach lifecycle of a [BaseTile] instance for [tileType].
 */
@Composable
fun rememberTileController(tileType: TileType): BaseTile {
    val context = LocalContext.current
    val tile = remember(tileType.id, context) {
        tileType.create(context)
    }
    DisposableEffect(tile) {
        tile.attach()
        onDispose {
            tile.detach()
        }
    }
    return tile
}
