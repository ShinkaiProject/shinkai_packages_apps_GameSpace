package com.android.gamespace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Material 3 theme of every screen and of the overlay. The colours are the dynamic (Monet)
 * ones, so they follow the wallpaper and the light / dark setting of the system, the same
 * palette the old XML screens took their colours from.
 */
@Composable
fun GameSpaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/** Corners of the grouped lists: large on the outside of a group, small between its rows. */
object GroupCorners {
    val Outer: Dp = 28.dp
    val Inner: Dp = 6.dp
}

/**
 * The shape of row [index] of a group of [count] rows: the first one is round at the top, the
 * last one at the bottom, a lone row all around.
 */
fun groupItemShape(index: Int, count: Int): RoundedCornerShape {
    val outer = GroupCorners.Outer
    val inner = GroupCorners.Inner
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = CornerSize(if (first) outer else inner),
        topEnd = CornerSize(if (first) outer else inner),
        bottomEnd = CornerSize(if (last) outer else inner),
        bottomStart = CornerSize(if (last) outer else inner),
    )
}
