package com.android.gamespace.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The icon of the app [packageName], loaded off the main thread; null until it is there. An app
 * that is gone gets the default icon.
 */
@Composable
fun rememberAppIcon(packageName: String, size: Dp = 40.dp): ImageBitmap? {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName, pixels) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val icon = runCatching { pm.getApplicationIcon(packageName) }.getOrNull()
                    ?: pm.defaultActivityIcon
                icon.toBitmap(pixels, pixels).asImageBitmap()
            }.getOrNull()
        }
    }
    return icon
}

/** The icon of an app, with its place kept free until it is loaded. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val icon = rememberAppIcon(packageName, size)
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size))
    } else {
        Spacer(modifier.size(size))
    }
}
