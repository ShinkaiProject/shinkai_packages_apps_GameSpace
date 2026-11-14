package com.android.gamespace.widget.compose.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private object FallbackColors {
    val background = Color(0xFF111714)
    val surface = Color(0xFF151E19)
    val surfaceContainer = Color(0xFF1D2922)
    val inactiveSurface = Color(0xFF202B24)
    val iconSurface = Color(0xFF202D25)
    val sliderTrack = Color(0xFF1D2621)
    val activeSurface = Color(0xFFB1EDC9)
    val sliderAccent = Color(0xFF9ACDAE)
    val onActive = Color(0xFF0E1511)
    val onSurface = Color(0xFFE1E8E2)
    val onSurfaceVariant = Color(0xFFAAB7AD)
    val inverseOnActive = Color(0xFF24563D)
    val outline = Color(0xFF8C9A90)
    val outlineVariant = Color(0xFF3A453D)

    val lightBackground = Color(0xFFF6FBF7)
    val lightInactiveSurface = Color(0xFFDCE8DF)
    val lightElevatedSurface = Color(0xFFD2E1D6)
    val lightSurfaceVariant = Color(0xFFDCE8DF)
    val lightOnSurface = Color(0xFF0E1511)
    val lightOnSurfaceVariant = Color(0xFF4A5A50)
    val lightPrimary = Color(0xFF4C7D62)
    val lightOutline = Color(0xFF6F8177)
    val lightOutlineVariant = Color(0xFFBCCAC0)

    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)
}

private val GameSpaceDarkColors = darkColorScheme(
    primary = FallbackColors.sliderAccent,
    onPrimary = FallbackColors.onActive,
    primaryContainer = FallbackColors.activeSurface,
    onPrimaryContainer = FallbackColors.onActive,
    inversePrimary = FallbackColors.inverseOnActive,
    secondary = FallbackColors.sliderAccent,
    onSecondary = FallbackColors.onActive,
    secondaryContainer = FallbackColors.activeSurface,
    onSecondaryContainer = FallbackColors.onActive,
    tertiary = FallbackColors.sliderAccent,
    onTertiary = FallbackColors.onActive,
    tertiaryContainer = FallbackColors.activeSurface,
    onTertiaryContainer = FallbackColors.onActive,
    background = FallbackColors.background,
    onBackground = FallbackColors.onSurface,
    surface = FallbackColors.surface,
    onSurface = FallbackColors.onSurface,
    surfaceVariant = FallbackColors.sliderTrack,
    onSurfaceVariant = FallbackColors.onSurfaceVariant,
    inverseSurface = FallbackColors.onSurface,
    inverseOnSurface = FallbackColors.surface,
    surfaceContainerLowest = FallbackColors.background,
    surfaceContainerLow = FallbackColors.surface,
    surfaceContainer = FallbackColors.surfaceContainer,
    surfaceContainerHigh = FallbackColors.inactiveSurface,
    surfaceContainerHighest = FallbackColors.iconSurface,
    outline = FallbackColors.outline,
    outlineVariant = FallbackColors.outlineVariant,
    error = FallbackColors.error,
    onError = FallbackColors.onError,
    errorContainer = FallbackColors.errorContainer,
    onErrorContainer = FallbackColors.onErrorContainer,
)

private val GameSpaceLightColors = lightColorScheme(
    primary = FallbackColors.lightPrimary,
    onPrimary = Color.White,
    primaryContainer = FallbackColors.activeSurface,
    onPrimaryContainer = FallbackColors.onActive,
    inversePrimary = FallbackColors.sliderAccent,
    secondary = FallbackColors.lightPrimary,
    onSecondary = Color.White,
    secondaryContainer = FallbackColors.activeSurface,
    onSecondaryContainer = FallbackColors.onActive,
    tertiary = FallbackColors.lightPrimary,
    onTertiary = Color.White,
    tertiaryContainer = FallbackColors.activeSurface,
    onTertiaryContainer = FallbackColors.onActive,
    background = FallbackColors.lightBackground,
    onBackground = FallbackColors.lightOnSurface,
    surface = FallbackColors.lightBackground,
    onSurface = FallbackColors.lightOnSurface,
    surfaceVariant = FallbackColors.lightSurfaceVariant,
    onSurfaceVariant = FallbackColors.lightOnSurfaceVariant,
    inverseSurface = FallbackColors.lightOnSurface,
    inverseOnSurface = FallbackColors.lightBackground,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = FallbackColors.lightBackground,
    surfaceContainer = FallbackColors.lightElevatedSurface,
    surfaceContainerHigh = FallbackColors.lightInactiveSurface,
    surfaceContainerHighest = FallbackColors.lightElevatedSurface,
    outline = FallbackColors.lightOutline,
    outlineVariant = FallbackColors.lightOutlineVariant,
    error = FallbackColors.onError,
    onError = Color.White,
    errorContainer = FallbackColors.error,
    onErrorContainer = FallbackColors.onError,
)

internal fun gameSpaceColorScheme(context: Context, darkTheme: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    }
    return if (darkTheme) GameSpaceDarkColors else GameSpaceLightColors
}

private fun dynamicDarkColorScheme(context: Context): ColorScheme {
    fun neutral(tone: Int, fallback: Color): Color =
        systemColor(context, "system_neutral1_$tone", fallback)

    fun neutral2(tone: Int, fallback: Color): Color =
        systemColor(context, "system_neutral2_$tone", fallback)

    fun accent(tone: Int, fallback: Color): Color =
        systemColor(context, "system_accent1_$tone", fallback)

    fun accent2(tone: Int, fallback: Color): Color =
        systemColor(context, "system_accent2_$tone", fallback)

    fun inactive(factor: Float, fallback: Color): Color =
        darkenedAccent(context, factor, fallback)

    return darkColorScheme(
        primary = accent(200, FallbackColors.sliderAccent),
        onPrimary = accent(900, FallbackColors.onActive),
        primaryContainer = accent(100, FallbackColors.activeSurface),
        onPrimaryContainer = accent(900, FallbackColors.onActive),
        inversePrimary = accent(800, FallbackColors.inverseOnActive),
        secondary = accent(200, FallbackColors.sliderAccent),
        onSecondary = accent(900, FallbackColors.onActive),
        secondaryContainer = accent(100, FallbackColors.activeSurface),
        onSecondaryContainer = accent(900, FallbackColors.onActive),
        tertiary = accent2(200, FallbackColors.sliderAccent),
        onTertiary = accent2(900, FallbackColors.onActive),
        tertiaryContainer = accent2(100, FallbackColors.activeSurface),
        onTertiaryContainer = accent2(900, FallbackColors.onActive),
        background = neutral(1000, FallbackColors.background),
        onBackground = neutral(10, FallbackColors.onSurface),
        surface = neutral(900, FallbackColors.surface),
        onSurface = neutral(10, FallbackColors.onSurface),
        surfaceVariant = inactive(0.162f, FallbackColors.sliderTrack),
        onSurfaceVariant = neutral(200, FallbackColors.onSurfaceVariant),
        inverseSurface = neutral(10, FallbackColors.onSurface),
        inverseOnSurface = neutral(900, FallbackColors.surface),
        surfaceContainerLowest = neutral(1000, FallbackColors.background),
        surfaceContainerLow = neutral(900, FallbackColors.surface),
        surfaceContainer = inactive(0.17f, FallbackColors.surfaceContainer),
        surfaceContainerHigh = inactive(0.181f, FallbackColors.inactiveSurface),
        surfaceContainerHighest = inactive(0.185f, FallbackColors.iconSurface),
        outline = neutral2(500, FallbackColors.outline),
        outlineVariant = neutral2(700, FallbackColors.outlineVariant),
        error = FallbackColors.error,
        onError = FallbackColors.onError,
        errorContainer = FallbackColors.errorContainer,
        onErrorContainer = FallbackColors.onErrorContainer,
    )
}

private fun dynamicLightColorScheme(context: Context): ColorScheme {
    fun neutral(tone: Int, fallback: Color): Color =
        systemColor(context, "system_neutral1_$tone", fallback)

    fun neutral2(tone: Int, fallback: Color): Color =
        systemColor(context, "system_neutral2_$tone", fallback)

    fun accent(tone: Int, fallback: Color): Color =
        systemColor(context, "system_accent1_$tone", fallback)

    fun accent2(tone: Int, fallback: Color): Color =
        systemColor(context, "system_accent2_$tone", fallback)

    return lightColorScheme(
        primary = accent(600, FallbackColors.lightPrimary),
        onPrimary = accent(0, Color.White),
        primaryContainer = accent(100, FallbackColors.activeSurface),
        onPrimaryContainer = accent(900, FallbackColors.onActive),
        inversePrimary = accent(300, FallbackColors.sliderAccent),
        secondary = accent(600, FallbackColors.lightPrimary),
        onSecondary = accent(0, Color.White),
        secondaryContainer = accent(100, FallbackColors.activeSurface),
        onSecondaryContainer = accent(900, FallbackColors.onActive),
        tertiary = accent2(600, FallbackColors.lightPrimary),
        onTertiary = accent2(0, Color.White),
        tertiaryContainer = accent2(100, FallbackColors.activeSurface),
        onTertiaryContainer = accent2(900, FallbackColors.onActive),
        background = neutral(0, FallbackColors.lightBackground),
        onBackground = neutral(1000, FallbackColors.lightOnSurface),
        surface = neutral(0, FallbackColors.lightBackground),
        onSurface = neutral(1000, FallbackColors.lightOnSurface),
        surfaceVariant = neutral(100, FallbackColors.lightSurfaceVariant),
        onSurfaceVariant = neutral(600, FallbackColors.lightOnSurfaceVariant),
        inverseSurface = neutral(900, FallbackColors.lightOnSurface),
        inverseOnSurface = neutral(10, FallbackColors.lightBackground),
        surfaceContainerLowest = neutral(0, Color.White),
        surfaceContainerLow = neutral(10, FallbackColors.lightBackground),
        surfaceContainer = neutral(200, FallbackColors.lightElevatedSurface),
        surfaceContainerHigh = neutral(100, FallbackColors.lightInactiveSurface),
        surfaceContainerHighest = neutral(200, FallbackColors.lightElevatedSurface),
        outline = neutral2(500, FallbackColors.lightOutline),
        outlineVariant = neutral2(200, FallbackColors.lightOutlineVariant),
        error = FallbackColors.onError,
        onError = Color.White,
        errorContainer = FallbackColors.error,
        onErrorContainer = FallbackColors.onError,
    )
}

private fun darkenedAccent(
    context: Context,
    factor: Float,
    fallback: Color,
): Color {
    val accent = systemColorOrNull(context, "system_accent1_100") ?: return fallback
    return Color(
        red = accent.red * factor,
        green = accent.green * factor,
        blue = accent.blue * factor,
        alpha = 1f,
    )
}

private fun systemColor(context: Context, name: String, fallback: Color): Color =
    systemColorOrNull(context, name) ?: fallback

private fun systemColorOrNull(context: Context, name: String): Color? {
    val id = context.resources.getIdentifier(name, "color", "android")
    if (id == 0) return null
    return runCatching { Color(context.getColor(id)) }.getOrNull()
}
