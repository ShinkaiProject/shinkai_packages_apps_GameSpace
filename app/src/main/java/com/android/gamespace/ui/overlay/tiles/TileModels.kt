package com.android.gamespace.ui.overlay.tiles

import android.app.GameManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.SoftApConfiguration
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.UserHandle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.gamespace.R
import com.android.gamespace.display.displayEnhancer
import com.android.gamespace.display.DisplayEnhancerActivity
import com.android.gamespace.ui.overlay.ChangeSource
import com.android.gamespace.ui.overlay.broadcasts
import com.android.gamespace.ui.overlay.observing
import com.android.gamespace.ui.overlay.rememberLive
import com.android.gamespace.utils.FpsRecorder
import com.android.gamespace.utils.GameModeUtils.Companion.describeGameMode
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf

/**
 * What a tile of the panel shows and does: whether it is on, the line under its title, and what a
 * tap and a long press do. [onLongClick] is null for a tile without one.
 */
@Immutable
class TileModel(
    val selected: Boolean,
    val summary: String,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * The model of [type], live for as long as the tile is on the screen: it listens to the system
 * for changes of what it shows and stops when it leaves.
 */
@Composable
fun rememberTile(type: TileType): TileModel = when (type) {
    TileType.GAME_MODE -> rememberGameModeTile()
    TileType.FPS_INFO -> rememberFpsInfoTile()
    TileType.FPS_RECORD -> rememberFpsRecordTile()
    TileType.STAY_AWAKE -> rememberStayAwakeTile()
    TileType.LOCK_GESTURE -> rememberLockGestureTile()
    TileType.WIFI -> rememberWifiTile()
    TileType.BLUETOOTH -> rememberBluetoothTile()
    TileType.MOBILE_DATA -> rememberMobileDataTile()
    TileType.AIRPLANE_MODE -> rememberAirplaneModeTile()
    TileType.DND -> rememberDndTile()
    TileType.AUTO_ROTATE -> rememberAutoRotateTile()
    TileType.BATTERY_SAVER -> rememberBatterySaverTile()
    TileType.HOTSPOT -> rememberHotspotTile()
    TileType.LOCATION -> rememberLocationTile()
    TileType.PRIVATE_DNS -> rememberPrivateDnsTile()
    TileType.FLASHLIGHT -> rememberFlashlightTile()
    TileType.DISPLAY_ENHANCER -> rememberDisplayEnhancerTile()
    TileType.NIGHT_LIGHT -> rememberNightLightTile()
    TileType.AUTO_SYNC -> rememberAutoSyncTile()
    TileType.BYPASS_CHARGE -> rememberBypassChargeTile()
    TileType.DANMAKU -> rememberDanmakuTile()
}

private val Context.entry get() = entryPointOf<ServiceViewEntryPoint>()

/** Opens the system screen of [action] on top of the game. */
private fun Context.openSettings(action: String) {
    runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Work that may block, such as asking a radio to switch on, is done off the main thread. */
private fun inBackground(block: () -> Unit) {
    Thread(block).start()
}

@Composable
private fun enabledText(on: Boolean) =
    stringResource(if (on) R.string.state_enabled else R.string.state_disabled)

/**
 * A tile that is on or off. [read] says which, [source] when that changes (null for never), and
 * [apply] switches it to the value it is given. A tap shows the new state at once.
 */
@Composable
private fun rememberToggleTile(
    source: ChangeSource?,
    read: () -> Boolean,
    longClickAction: String? = null,
    apply: (Boolean) -> Unit,
): TileModel {
    val context = LocalContext.current
    var on by rememberLive(source, read)
    return TileModel(
        selected = on,
        summary = enabledText(on),
        onClick = {
            val target = !read()
            on = target
            apply(target)
        },
        onLongClick = longClickAction?.let { action -> { context.openSettings(action) } },
    )
}

// --- Game ---------------------------------------------------------------------------------------

@Composable
private fun rememberGameModeTile(): TileModel {
    val context = LocalContext.current
    val entry = context.entry
    val modes = remember {
        listOf(
            GameManager.GAME_MODE_STANDARD,
            GameManager.GAME_MODE_PERFORMANCE,
            GameManager.GAME_MODE_BATTERY,
        )
    }
    var mode by remember {
        mutableStateOf(entry.gameModeUtils().activeGame?.mode ?: GameManager.GAME_MODE_STANDARD)
    }
    return TileModel(
        selected = mode != GameManager.GAME_MODE_STANDARD,
        summary = context.describeGameMode(mode),
        onClick = {
            val current = modes.indexOf(mode)
            val next = modes[if (current == modes.size - 1) 0 else current + 1]
            mode = next
            entry.gameModeUtils().setActiveGameMode(entry.systemSettings(), next)
        },
    )
}

@Composable
private fun rememberFpsInfoTile(): TileModel {
    val settings = LocalContext.current.entry.appSettings()
    var on by remember { mutableStateOf(settings.showFps) }
    return TileModel(
        selected = on,
        summary = enabledText(on),
        onClick = {
            on = !on
            settings.showFps = on
        },
    )
}

/** The recorder outlives the panel, so the tile only mirrors its state while it is shown. */
@Composable
private fun rememberFpsRecordTile(): TileModel {
    val recorder = LocalContext.current.entry.fpsRecorder()
    var isRecording by remember { mutableStateOf(recorder.isRecording) }
    var seconds by remember { mutableStateOf(recorder.elapsedMs / 1000) }

    DisposableEffect(recorder) {
        val listener = object : FpsRecorder.Listener {
            override fun onStateChanged(recording: Boolean) {
                isRecording = recording
                seconds = recorder.elapsedMs / 1000
            }

            // Samples arrive twice a second but the timer only counts whole seconds.
            override fun onSample(elapsedMs: Long, fps: Float) {
                if (elapsedMs / 1000 != seconds) seconds = elapsedMs / 1000
            }
        }
        recorder.addListener(listener, wantsSamples = true)
        isRecording = recorder.isRecording
        seconds = recorder.elapsedMs / 1000
        onDispose { recorder.removeListener(listener) }
    }

    return TileModel(
        selected = isRecording,
        summary = if (isRecording) {
            stringResource(R.string.fps_record_summary_recording, DateUtils.formatElapsedTime(seconds))
        } else {
            stringResource(R.string.fps_record_summary_idle)
        },
        onClick = {
            if (recorder.isRecording) recorder.stop() else recorder.start()
            isRecording = recorder.isRecording
            seconds = recorder.elapsedMs / 1000
        },
    )
}

@Composable
private fun rememberStayAwakeTile(): TileModel {
    val entry = LocalContext.current.entry
    var on by remember { mutableStateOf(entry.appSettings().stayAwake) }
    return TileModel(
        selected = on,
        summary = enabledText(on),
        onClick = {
            on = !on
            entry.appSettings().stayAwake = on
            entry.screenUtils().stayAwake = on
        },
    )
}

@Composable
private fun rememberLockGestureTile(): TileModel {
    val entry = LocalContext.current.entry
    var on by remember { mutableStateOf(entry.appSettings().lockGesture) }
    return TileModel(
        selected = on,
        summary = enabledText(on),
        onClick = {
            on = !on
            entry.appSettings().lockGesture = on
            entry.screenUtils().lockGesture = on
        },
    )
}

@Composable
private fun rememberDanmakuTile(): TileModel {
    val entry = LocalContext.current.entry
    var on by remember { mutableStateOf(entry.appSettings().danmakuNotification) }
    return TileModel(
        selected = on,
        summary = enabledText(on),
        onClick = {
            entry.gameSession().setDanmakuNotification(!entry.appSettings().danmakuNotification)
            on = entry.appSettings().danmakuNotification
        },
    )
}

/**
 * Game Display Enhanced. A tap goes to the next preset (the last one is followed by the first,
 * Standard), a long press opens the screen where the presets are made.
 */
@Composable
private fun rememberDisplayEnhancerTile(): TileModel {
    val context = LocalContext.current
    val enhancer = remember { context.displayEnhancer() }
    var preset by remember { mutableStateOf(enhancer.active) }
    return TileModel(
        selected = !preset.isIdentity,
        summary = enhancer.labelOf(preset),
        onClick = {
            enhancer.selectNext()
            preset = enhancer.active
        },
        onLongClick = {
            runCatching {
                context.startActivity(
                    Intent(context, DisplayEnhancerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
    )
}

@Composable
private fun rememberBypassChargeTile(): TileModel {
    val context = LocalContext.current
    val screenUtils = context.entry.screenUtils()
    val key = "bypass_charge_enabled"
    return rememberToggleTile(
        source = context.observing(Settings.System.getUriFor(key)),
        read = {
            Settings.System.getIntForUser(
                context.contentResolver, key, 0, UserHandle.USER_CURRENT
            ) == 1
        },
    ) { target ->
        Settings.System.putIntForUser(
            context.contentResolver, key, if (target) 1 else 0, UserHandle.USER_CURRENT
        )
        runCatching { screenUtils.bypassCharge(target) }
    }
}

// --- Connectivity -------------------------------------------------------------------------------

@Composable
private fun rememberWifiTile(): TileModel {
    val context = LocalContext.current
    val wifi = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    return rememberToggleTile(
        source = context.broadcasts(WifiManager.WIFI_STATE_CHANGED_ACTION),
        read = { wifi.isWifiEnabled },
        longClickAction = Settings.ACTION_WIFI_SETTINGS,
    ) { target -> inBackground { wifi.isWifiEnabled = target } }
}

@Composable
private fun rememberBluetoothTile(): TileModel {
    val context = LocalContext.current
    val adapter: BluetoothAdapter? = remember {
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    return rememberToggleTile(
        source = context.broadcasts(BluetoothAdapter.ACTION_STATE_CHANGED),
        read = { adapter?.isEnabled == true },
    ) { target -> inBackground { if (target) adapter?.enable() else adapter?.disable() } }
}

@Composable
private fun rememberMobileDataTile(): TileModel {
    val context = LocalContext.current
    val telephony = remember {
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    return rememberToggleTile(
        source = null,
        read = { telephony.isDataEnabled },
        longClickAction = Settings.ACTION_NETWORK_OPERATOR_SETTINGS,
    ) { target -> inBackground { telephony.isDataEnabled = target } }
}

@Composable
private fun rememberAirplaneModeTile(): TileModel {
    val context = LocalContext.current
    return rememberToggleTile(
        source = context.broadcasts(Intent.ACTION_AIRPLANE_MODE_CHANGED),
        read = {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0
        },
        longClickAction = Settings.ACTION_AIRPLANE_MODE_SETTINGS,
    ) { target ->
        inBackground {
            Settings.Global.putInt(
                context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, if (target) 1 else 0
            )
            context.sendBroadcast(Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", target))
        }
    }
}

@Composable
private fun rememberHotspotTile(): TileModel {
    val context = LocalContext.current
    val wifi = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val connectivity = remember {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    return rememberToggleTile(
        source = context.broadcasts(ACTION_WIFI_AP_STATE_CHANGED),
        read = {
            runCatching {
                val method = wifi.javaClass.getDeclaredMethod("getWifiApState")
                method.isAccessible = true
                val state = method.invoke(wifi) as? Int ?: 0
                state == WIFI_AP_STATE_ENABLED || state == WIFI_AP_STATE_ENABLING
            }.getOrDefault(false)
        },
        longClickAction = Settings.ACTION_WIRELESS_SETTINGS,
    ) { target ->
        inBackground {
            val toggled = runCatching {
                if (target) {
                    wifi.javaClass.getMethod("startTetheredHotspot", SoftApConfiguration::class.java)
                        .invoke(wifi, null)
                } else {
                    wifi.javaClass.getMethod("stopSoftAp").invoke(wifi)
                }
                true
            }.getOrDefault(false)

            if (!toggled) {
                runCatching {
                    val stop = connectivity.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                    if (!target) stop.invoke(connectivity, TETHERING_WIFI)
                }
            }
        }
    }
}

private const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
private const val WIFI_AP_STATE_ENABLING = 12
private const val WIFI_AP_STATE_ENABLED = 13
private const val TETHERING_WIFI = 0

@Composable
private fun rememberLocationTile(): TileModel {
    val context = LocalContext.current
    val location = remember {
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    return rememberToggleTile(
        source = context.broadcasts(LocationManager.MODE_CHANGED_ACTION),
        read = { location.isLocationEnabled },
        longClickAction = Settings.ACTION_LOCATION_SOURCE_SETTINGS,
    ) { target ->
        inBackground {
            val updated = runCatching {
                location.setLocationEnabledForUser(target, UserHandle.CURRENT)
                true
            }.getOrDefault(false)
            if (!updated) {
                runCatching {
                    Settings.Secure.putInt(
                        context.contentResolver,
                        Settings.Secure.LOCATION_MODE,
                        if (target) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                        else Settings.Secure.LOCATION_MODE_OFF
                    )
                }
            }
        }
    }
}

/** What Private DNS is set to: its mode and, for a host name, the host. */
private class DnsInfo(val mode: String, val host: String)

@Composable
private fun rememberPrivateDnsTile(): TileModel {
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun read() = DnsInfo(
        mode = Settings.Global.getString(resolver, KEY_PRIVATE_DNS_MODE) ?: MODE_OPPORTUNISTIC,
        host = Settings.Global.getString(resolver, KEY_PRIVATE_DNS_SPECIFIER).orEmpty(),
    )

    var info by rememberLive(context.observing(Settings.Global.getUriFor(KEY_PRIVATE_DNS_MODE)), ::read)
    val off = stringResource(R.string.private_dns_off)
    val strict = stringResource(R.string.private_dns_Strict)
    val auto = stringResource(R.string.private_dns_auto)

    val summary = when (info.mode) {
        MODE_OFF -> off
        MODE_HOSTNAME -> info.host.ifBlank { strict }
        else -> auto
    }
    return TileModel(
        selected = info.mode != MODE_OFF,
        summary = summary,
        onClick = {
            val now = read()
            val next = when (now.mode) {
                MODE_OFF -> MODE_OPPORTUNISTIC
                MODE_OPPORTUNISTIC -> if (now.host.isNotBlank()) MODE_HOSTNAME else MODE_OFF
                else -> MODE_OFF
            }
            Settings.Global.putString(resolver, KEY_PRIVATE_DNS_MODE, next)
            info = read()
        },
        onLongClick = { context.openSettings(Settings.ACTION_WIRELESS_SETTINGS) },
    )
}

private const val KEY_PRIVATE_DNS_MODE = "private_dns_mode"
private const val KEY_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
private const val MODE_OFF = "off"
private const val MODE_OPPORTUNISTIC = "opportunistic"
private const val MODE_HOSTNAME = "hostname"

// --- System -------------------------------------------------------------------------------------

@Composable
private fun rememberDndTile(): TileModel {
    val context = LocalContext.current
    val manager = remember {
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }
    return rememberToggleTile(
        source = context.broadcasts(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED),
        read = { manager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL },
        longClickAction = Settings.ACTION_ZEN_MODE_SETTINGS,
    ) { target ->
        val filter = if (target) NotificationManager.INTERRUPTION_FILTER_PRIORITY
        else NotificationManager.INTERRUPTION_FILTER_ALL
        inBackground { runCatching { manager?.setInterruptionFilter(filter) } }
    }
}

@Composable
private fun rememberAutoRotateTile(): TileModel {
    val context = LocalContext.current
    return rememberToggleTile(
        source = context.observing(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)),
        read = {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        },
        longClickAction = Settings.ACTION_DISPLAY_SETTINGS,
    ) { target ->
        Settings.System.putInt(
            context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (target) 1 else 0
        )
    }
}

@Composable
private fun rememberBatterySaverTile(): TileModel {
    val context = LocalContext.current
    val power = remember {
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    return rememberToggleTile(
        source = context.broadcasts(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
        read = {
            power?.isPowerSaveMode == true ||
                Settings.Global.getInt(context.contentResolver, "low_power", 0) == 1
        },
        longClickAction = Settings.ACTION_BATTERY_SAVER_SETTINGS,
    ) { target ->
        inBackground {
            val handled = runCatching { power?.setPowerSaveModeEnabled(target) == true }.getOrDefault(false)
            if (!handled) {
                runCatching {
                    Settings.Global.putInt(context.contentResolver, "low_power", if (target) 1 else 0)
                }
            }
        }
    }
}

@Composable
private fun rememberNightLightTile(): TileModel {
    val context = LocalContext.current
    val key = "night_display_activated"
    return rememberToggleTile(
        source = context.observing(Settings.Secure.getUriFor(key)),
        read = { Settings.Secure.getInt(context.contentResolver, key, 0) == 1 },
        longClickAction = Settings.ACTION_NIGHT_DISPLAY_SETTINGS,
    ) { target ->
        Settings.Secure.putInt(context.contentResolver, key, if (target) 1 else 0)
    }
}

@Composable
private fun rememberAutoSyncTile(): TileModel {
    // The system reports sync changes on a thread of its own.
    val source = remember {
        ChangeSource { signal ->
            val main = Handler(Looper.getMainLooper())
            val handle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS
            ) { main.post(signal) }
            val stop: () -> Unit = { ContentResolver.removeStatusChangeListener(handle) }
            stop
        }
    }
    return rememberToggleTile(
        source = source,
        read = { ContentResolver.getMasterSyncAutomatically() },
        longClickAction = Settings.ACTION_SYNC_SETTINGS,
    ) { target -> inBackground { ContentResolver.setMasterSyncAutomatically(target) } }
}

@Composable
private fun rememberFlashlightTile(): TileModel {
    val context = LocalContext.current
    val camera = remember {
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }
    val torchId = remember { findTorchCameraId(camera) }
    var on by remember { mutableStateOf(false) }

    DisposableEffect(camera) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchId) on = enabled
            }
        }
        runCatching { camera?.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { camera?.unregisterTorchCallback(callback) } }
    }

    return TileModel(
        selected = on,
        summary = enabledText(on),
        onClick = {
            val id = torchId ?: findTorchCameraId(camera)
            if (id != null) {
                val target = !on
                on = target
                inBackground { runCatching { camera?.setTorchMode(id, target) } }
            }
        },
    )
}

/** The camera with a flash, the one on the back if there are several. */
private fun findTorchCameraId(camera: CameraManager?): String? = runCatching {
    val manager = camera ?: return null
    manager.cameraIdList.firstOrNull { id ->
        val chars = manager.getCameraCharacteristics(id)
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val facing = chars.get(CameraCharacteristics.LENS_FACING)
        hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
    } ?: manager.cameraIdList.firstOrNull { id ->
        manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
}.getOrNull()
