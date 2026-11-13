package com.android.gamespace.ui.overlay

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.android.gamespace.ui.theme.GameSpaceTheme

/**
 * The colours of the overlay (the bar and the panel over the game). They are the Monet tonal
 * palette of the system, picked the same way for light and dark as the XML overlay did, so the
 * overlay keeps the look it had; the screens of the app use the Material 3 roles of the same
 * palette.
 */
@Immutable
class OverlayColors(
    /** The panel. */
    val panel: Color,
    /** A tile that is off, and the track of a slider. */
    val tile: Color,
    val onTile: Color,
    /** A tile that is on, the status pill and the buttons of the bar. */
    val active: Color,
    val onActive: Color,
    /** The bar behind its buttons. */
    val bar: Color,
    val onBarText: Color,
    val ripple: Color,
    /** The accent: titles, the memory text, the handle of a slider. */
    val accent: Color,
    val recording: Color,
    val secondaryText: Color,
    /** The round button behind the icon of a slider. */
    val sliderIcon: Color,
    val onSliderIcon: Color,
)

val LocalOverlayColors = staticCompositionLocalOf<OverlayColors> {
    error("OverlayTheme is missing")
}

@Composable
fun OverlayTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    // Monet colours may change with the wallpaper, which comes as a change of the configuration.
    val colors = remember(dark, configuration) { overlayColors(context, dark) }
    GameSpaceTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalOverlayColors provides colors, content = content)
    }
}

private fun overlayColors(context: Context, dark: Boolean): OverlayColors {
    fun c(id: Int) = Color(context.getColor(id))
    return if (dark) {
        OverlayColors(
            panel = c(android.R.color.system_neutral1_900),
            tile = c(android.R.color.system_neutral1_800),
            onTile = c(android.R.color.system_neutral1_0),
            active = c(android.R.color.system_accent1_100),
            onActive = c(android.R.color.system_neutral1_1000),
            bar = c(android.R.color.system_neutral1_900),
            onBarText = c(android.R.color.system_neutral1_0),
            ripple = c(android.R.color.system_accent2_50),
            accent = c(android.R.color.system_accent1_100),
            recording = Color(0xFFEF4040),
            secondaryText = c(android.R.color.system_neutral1_0).copy(alpha = 0.7f),
            sliderIcon = c(android.R.color.system_neutral2_800),
            onSliderIcon = c(android.R.color.system_accent1_100),
        )
    } else {
        OverlayColors(
            panel = c(android.R.color.system_neutral1_100),
            tile = c(android.R.color.system_neutral1_0),
            onTile = c(android.R.color.system_neutral1_1000),
            active = c(android.R.color.system_accent1_600),
            onActive = c(android.R.color.system_neutral1_0),
            bar = c(android.R.color.system_neutral1_50),
            onBarText = c(android.R.color.system_neutral1_1000),
            ripple = c(android.R.color.system_accent2_500),
            accent = c(android.R.color.system_accent1_600),
            recording = Color(0xFFEF4040),
            secondaryText = c(android.R.color.system_neutral1_1000).copy(alpha = 0.7f),
            sliderIcon = c(android.R.color.system_neutral2_800),
            onSliderIcon = c(android.R.color.system_accent1_100),
        )
    }
}
