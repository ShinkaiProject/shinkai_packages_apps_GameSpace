package com.android.gamespace.widget.compose.quickstart

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SETTING_QUICK_START_APPS = "quick_start_apps"

@Composable
fun QuickStartApps(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var packages by remember { mutableStateOf(readQuickStartPackages(context)) }

    LifecycleStartEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                packages = readQuickStartPackages(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_QUICK_START_APPS),
            true,
            observer,
        )
        packages = readQuickStartPackages(context)
        onStopOrDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    val packageManager = context.packageManager
    val visiblePackages = remember(packages) {
        packages.filter { packageName ->
            runCatching { packageManager.getApplicationInfo(packageName, 0) }.isSuccess
        }
    }
    if (visiblePackages.isEmpty()) return

    LazyRow(modifier = modifier.fillMaxWidth()) {
        items(visiblePackages, key = { it }) { packageName ->
            QuickStartAppItem(packageName = packageName)
        }
    }
}

@Composable
private fun QuickStartAppItem(packageName: String) {
    val context = LocalContext.current
    val loaded by produceState<QuickStartAppIcon?>(
        initialValue = null,
        key1 = packageName,
    ) {
        value = withContext(Dispatchers.IO) {
            loadIcon(context, packageName)
        }
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { launchInFreeform(context, packageName) },
    ) {
        loaded?.let { app ->
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private data class QuickStartAppIcon(
    val label: String,
    val icon: Bitmap,
)

private fun readQuickStartPackages(context: Context): List<String> =
    Settings.System.getString(context.contentResolver, SETTING_QUICK_START_APPS)
        ?.takeIf { it.isNotEmpty() }
        ?.split(",")
        ?.filter { it.isNotEmpty() }
        ?: emptyList()

private fun loadIcon(context: Context, packageName: String): QuickStartAppIcon? = runCatching {
    val manager = context.packageManager
    val info = manager.getApplicationInfo(packageName, 0)
    val drawable = manager.getApplicationIcon(info)
    QuickStartAppIcon(
        label = manager.getApplicationLabel(info).toString(),
        icon = drawable.toBitmap(),
    )
}.getOrNull()

private fun launchInFreeform(context: Context, packageName: String) {
    runCatching {
        val manager = context.packageManager
        val launchIntent = manager.getLaunchIntentForPackage(packageName) ?: return
        val activityName = launchIntent.component?.className ?: return
        val freeformIntent = Intent("com.libremobileos.freeform.START_FREEFORM")
            .setPackage("com.libremobileos.freeform")
            .putExtra("packageName", packageName)
            .putExtra("activityName", activityName)
            .putExtra("userId", UserHandle.myUserId())
        context.sendBroadcast(freeformIntent)
    }
}
