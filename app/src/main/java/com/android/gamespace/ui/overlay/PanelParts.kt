package com.android.gamespace.ui.overlay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.UserHandle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.android.gamespace.R
import com.android.gamespace.data.TileLayoutModel
import com.android.gamespace.ui.overlay.tiles.TileType
import com.android.gamespace.ui.overlay.tiles.rememberTile
import com.android.gamespace.utils.MemoryUsageMonitor
import kotlin.math.roundToInt

// ---- The header: the clock, the temperature and the charge in a pill, and the edit button

/** The pill with the time, the temperature of the battery and its charge. */
@Composable
fun StatusPill(modifier: Modifier = Modifier) {
    val colors = LocalOverlayColors.current
    val context = LocalContext.current

    val time = rememberLive(
        context.broadcasts(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED),
    ) { clockText(context) }
    val charge = rememberLive(context.broadcasts(Intent.ACTION_BATTERY_CHANGED)) {
        context.getSystemService(BatteryManager::class.java)!!
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    // Read when the panel opens, not followed.
    val temperature = remember { batteryTemperatureText(context) }

    Row(
        modifier
            .clip(CircleShape)
            .background(colors.active)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusItem(R.drawable.clock_time_two_outline, time.value)
        StatusItem(R.drawable.thermometer, temperature)
        StatusItem(R.drawable.battery_outline, stringResource(R.string.battery_format, charge.value))
    }
}

@Composable
private fun StatusItem(icon: Int, text: String) {
    val color = LocalOverlayColors.current.onActive
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, color = color, style = overlayText(14.sp), maxLines = 1)
    }
}

private fun clockText(context: Context): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "kk:mm" else "h:mm"
    return DateFormat.format(pattern, System.currentTimeMillis()).toString()
}

private fun batteryTemperatureText(context: Context): String {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10
    return "$temp℃"
}

/** The button that switches the panel to the editor of its tiles and back. */
@Composable
fun EditButton(editing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalOverlayColors.current
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.active)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = colors.onActive),
                onClick = onClick,
            )
            .padding(6.dp),
    ) {
        Icon(
            painter = painterResource(if (editing) R.drawable.ic_check else R.drawable.ic_edit_tiles),
            contentDescription = stringResource(
                if (editing) R.string.panel_edit_done else R.string.panel_edit_enter,
            ),
            tint = colors.onActive,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ---- Quick start: the apps that open in a window

private class QuickApp(val packageName: String, val icon: ImageBitmap)

private const val QUICK_START_KEY = "quick_start_apps"

/** The icons of the apps of the setting "quick_start_apps", which open in a freeform window. */
@Composable
fun QuickStartRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val saved = rememberLive(context.observing(Settings.System.getUriFor(QUICK_START_KEY))) {
        Settings.System.getString(context.contentResolver, QUICK_START_KEY)
    }
    val apps = remember(saved.value) { loadQuickApps(context, saved.value) }
    // With nothing to show there is no row, and no space around it.
    if (apps.isEmpty()) return

    LazyRow(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
        items(apps, key = { it.packageName }) { app ->
            Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(36.dp)
                    .clickable { launchInFreeform(context, app.packageName) },
            )
        }
    }
}

private fun loadQuickApps(context: Context, saved: String?): List<QuickApp> {
    if (saved.isNullOrEmpty()) return emptyList()
    val size = (36 * context.resources.displayMetrics.density).roundToInt()
    val packageManager = context.packageManager
    return saved.split(",").mapNotNull { name ->
        if (name.isEmpty()) return@mapNotNull null
        runCatching {
            val info = packageManager.getApplicationInfo(name, 0)
            QuickApp(name, packageManager.getApplicationIcon(info).toBitmap(size, size).asImageBitmap())
        }.getOrNull()
    }.distinctBy { it.packageName }
}

private fun launchInFreeform(context: Context, packageName: String) {
    try {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        val intent = Intent("com.libremobileos.freeform.START_FREEFORM")
            .setPackage("com.libremobileos.freeform")
            .putExtra("packageName", packageName)
            .putExtra("activityName", launch.component!!.className)
            .putExtra("userId", UserHandle.myUserId())
        context.sendBroadcast(intent)
    } catch (_: Exception) {
    }
}

// ---- Memory

/** The use of the RAM, as text and as a bar. */
@Composable
fun MemoryRow(modifier: Modifier = Modifier) {
    val colors = LocalOverlayColors.current
    val context = LocalContext.current
    val percent = remember { mutableStateOf(-1) }
    // The monitor is shared, and only samples while something listens.
    DisposableEffect(Unit) {
        val monitor = MemoryUsageMonitor.get(context)
        val listener = MemoryUsageMonitor.Listener { percent.value = it }
        monitor.addListener(listener)
        onDispose { monitor.removeListener(listener) }
    }

    Row(
        modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.memory),
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = if (percent.value >= 0) stringResource(R.string.ram_usage) + " " + percent.value + "%" else "",
            color = colors.accent,
            style = overlayText(11.sp),
            maxLines = 1,
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.width(52.dp).height(16.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.accent.copy(alpha = 0.3f)))
            Box(
                Modifier
                    .fillMaxWidth(fraction = percent.value.coerceIn(0, 100) / 100f)
                    .height(4.dp)
                    .background(colors.accent),
            )
        }
    }
}

// ---- The tiles

/** The tiles in pages of four, two to a row, that are swiped from one to the next. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TilePager(tiles: List<TileType>, modifier: Modifier = Modifier) {
    val pages = remember(tiles) { tiles.chunked(TileLayoutModel.TILES_PER_PAGE) }
    val state = rememberLazyListState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LazyRow(
        modifier
            .fillMaxWidth()
            .padding(top = if (landscape) 4.dp else 8.dp)
            .height(120.dp),
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
    ) {
        items(pages) { page ->
            Column(Modifier.fillParentMaxWidth().fillMaxHeight()) {
                page.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(2) { index ->
                            if (index < row.size) {
                                TileButton(row[index], Modifier.weight(1f))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One tile of the panel: it shows what it is and where it stands, and a tap switches it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileButton(type: TileType, modifier: Modifier = Modifier) {
    val colors = LocalOverlayColors.current
    val tile = rememberTile(type)
    val background = if (tile.selected) colors.active else colors.tile
    val content = if (tile.selected) colors.onActive else colors.onTile

    Row(
        modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(color = colors.ripple),
                onClick = tile.onClick,
                onLongClick = tile.onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(type.iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f, fill = false).padding(start = 10.dp)) {
            TileText(stringResource(type.titleRes), content, 11, tile.selected)
            TileText(tile.summary, content, 9, tile.selected, Modifier.alpha(0.7f))
        }
    }
}

/** A line of a tile. It runs along, like a marquee, when it is too long for an active tile. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileText(text: String, color: Color, size: Int, scrolls: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = color,
        style = overlayText(size.sp),
        maxLines = 1,
        overflow = if (scrolls) TextOverflow.Clip else TextOverflow.Ellipsis,
        modifier = if (scrolls) modifier.basicMarquee(iterations = Int.MAX_VALUE) else modifier,
    )
}
