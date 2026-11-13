package com.android.gamespace.ui.overlay

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The text of the overlay is small and tight, the size of a TextView with that text size: no
 * letter spacing, and a line that is a little taller than the letters.
 */
internal fun overlayText(size: TextUnit, weight: FontWeight? = null) = TextStyle(
    fontSize = size,
    fontWeight = weight,
    lineHeight = size * 1.25f,
    letterSpacing = 0.sp,
)
