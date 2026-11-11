package com.android.gamespace.widget.tiles

import android.app.GameManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SyncStatusObserver
import android.database.ContentObserver
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
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.gamespace.R
import com.android.gamespace.display.DisplayEnhancerActivity
import com.android.gamespace.display.displayEnhancer
import com.android.gamespace.utils.FpsRecorder
import com.android.gamespace.utils.GameModeUtils.Companion.describeGameMode
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileCardSurface(
    title: String,
    summary: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "TileContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "TileContentColor"
    )
    val shape = RoundedCornerShape(16.dp)

    Surface(
        shape = shape,
        color = containerColor,
        modifier = modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = contentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    color = contentColor.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun openSystemSettings(context: Context, action: String) {
    runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun GameTileComposable(
    type: TileType,
    modifier: Modifier = Modifier
) {
    when (type) {
        TileType.GAME_MODE -> GameModeTileComposable(modifier)
        TileType.FPS_INFO -> FpsInfoTileComposable(modifier)
        TileType.FPS_RECORD -> FpsRecordTileComposable(modifier)
        TileType.STAY_AWAKE -> StayAwakeTileComposable(modifier)
        TileType.LOCK_GESTURE -> LockGestureTileComposable(modifier)
        TileType.WIFI -> WifiTileComposable(modifier)
        TileType.BLUETOOTH -> BluetoothTileComposable(modifier)
        TileType.MOBILE_DATA -> MobileDataTileComposable(modifier)
        TileType.AIRPLANE_MODE -> AirplaneModeTileComposable(modifier)
        TileType.DND -> DndTileComposable(modifier)
        TileType.AUTO_ROTATE -> AutoRotateTileComposable(modifier)
        TileType.BATTERY_SAVER -> BatterySaverTileComposable(modifier)
        TileType.HOTSPOT -> HotspotTileComposable(modifier)
        TileType.LOCATION -> LocationTileComposable(modifier)
        TileType.PRIVATE_DNS -> PrivateDnsTileComposable(modifier)
        TileType.FLASHLIGHT -> FlashlightTileComposable(modifier)
        TileType.DISPLAY_ENHANCER -> DisplayEnhancerTileComposable(modifier)
        TileType.NIGHT_LIGHT -> NightLightTileComposable(modifier)
        TileType.AUTO_SYNC -> AutoSyncTileComposable(modifier)
        TileType.BYPASS_CHARGE -> BypassChargeTileComposable(modifier)
        TileType.DANMAKU -> DanmakuTileComposable(modifier)
    }
}

@Composable
private fun GameModeTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint = remember { context.entryPointOf<ServiceViewEntryPoint>() }
    val gameModeUtils = remember { entryPoint.gameModeUtils() }
    val systemSettings = remember { entryPoint.systemSettings() }

    val modes = remember {
        listOf(
            GameManager.GAME_MODE_STANDARD,
            GameManager.GAME_MODE_PERFORMANCE,
            GameManager.GAME_MODE_BATTERY
        )
    }
    var activeMode by remember {
        mutableIntStateOf(gameModeUtils.activeGame?.mode ?: GameManager.GAME_MODE_STANDARD)
    }

    TileCardSurface(
        title = stringResource(R.string.game_mode_title),
        summary = context.describeGameMode(activeMode),
        iconRes = R.drawable.ic_speed,
        selected = activeMode != GameManager.GAME_MODE_STANDARD,
        onClick = {
            val currentIdx = modes.indexOf(activeMode)
            val nextMode = modes[if (currentIdx == modes.lastIndex) 0 else currentIdx + 1]
            activeMode = nextMode
            gameModeUtils.setActiveGameMode(systemSettings, nextMode)
        },
        modifier = modifier
    )
}

@Composable
private fun FpsInfoTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appSettings = remember { context.entryPointOf<ServiceViewEntryPoint>().appSettings() }
    var enabled by remember { mutableStateOf(appSettings.showFps) }

    TileCardSurface(
        title = stringResource(R.string.fps_Info_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_fps,
        selected = enabled,
        onClick = {
            val next = !enabled
            enabled = next
            appSettings.showFps = next
        },
        modifier = modifier
    )
}

@Composable
private fun FpsRecordTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recorder = remember { context.entryPointOf<ServiceViewEntryPoint>().fpsRecorder() }
    var recording by remember { mutableStateOf(recorder.isRecording) }
    var elapsedSec by remember { mutableLongStateOf(recorder.elapsedMs / 1000L) }

    DisposableEffect(recorder) {
        val listener = object : FpsRecorder.Listener {
            override fun onStateChanged(recordingState: Boolean) {
                recording = recordingState
                elapsedSec = if (recordingState) recorder.elapsedMs / 1000L else 0L
            }

            override fun onSample(elapsedMs: Long, fps: Float) {
                val sec = elapsedMs / 1000L
                if (sec != elapsedSec) {
                    elapsedSec = sec
                }
            }
        }
        recorder.addListener(listener, wantsSamples = true)
        onDispose { recorder.removeListener(listener) }
    }

    val summary = if (recording) {
        stringResource(R.string.fps_record_summary_recording, DateUtils.formatElapsedTime(elapsedSec))
    } else {
        stringResource(R.string.fps_record_summary_idle)
    }

    TileCardSurface(
        title = stringResource(R.string.fps_record_title),
        summary = summary,
        iconRes = R.drawable.ic_fps_record,
        selected = recording,
        onClick = {
            if (recorder.isRecording) recorder.stop() else recorder.start()
            recording = recorder.isRecording
        },
        modifier = modifier
    )
}

@Composable
private fun StayAwakeTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint = remember { context.entryPointOf<ServiceViewEntryPoint>() }
    val appSettings = remember { entryPoint.appSettings() }
    val screenUtils = remember { entryPoint.screenUtils() }
    var enabled by remember { mutableStateOf(appSettings.stayAwake) }

    TileCardSurface(
        title = stringResource(R.string.stay_awake_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_awake,
        selected = enabled,
        onClick = {
            val next = !enabled
            enabled = next
            appSettings.stayAwake = next
            screenUtils.stayAwake = next
        },
        modifier = modifier
    )
}

@Composable
private fun LockGestureTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint = remember { context.entryPointOf<ServiceViewEntryPoint>() }
    val appSettings = remember { entryPoint.appSettings() }
    val screenUtils = remember { entryPoint.screenUtils() }
    var enabled by remember { mutableStateOf(appSettings.lockGesture) }

    TileCardSurface(
        title = stringResource(R.string.lock_gesture_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_gesture,
        selected = enabled,
        onClick = {
            val next = !enabled
            enabled = next
            appSettings.lockGesture = next
            screenUtils.lockGesture = next
        },
        modifier = modifier
    )
}

@Composable
private fun WifiTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    var enabled by remember { mutableStateOf(wifiManager.isWifiEnabled) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                enabled = wifiManager.isWifiEnabled
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    TileCardSurface(
        title = stringResource(R.string.wifi_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_wifi,
        selected = enabled,
        onClick = {
            val target = !wifiManager.isWifiEnabled
            enabled = target
            Thread { @Suppress("DEPRECATION") wifiManager.setWifiEnabled(target) }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun BluetoothTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adapter = remember {
        (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                enabled = adapter?.isEnabled == true
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    TileCardSurface(
        title = stringResource(R.string.bluetooth_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_bluetooth,
        selected = enabled,
        onClick = {
            val target = adapter?.isEnabled != true
            enabled = target
            Thread {
                @Suppress("DEPRECATION")
                if (target) adapter?.enable() else adapter?.disable()
            }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun MobileDataTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val telephonyManager = remember {
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    var enabled by remember { mutableStateOf(telephonyManager.isDataEnabled) }

    TileCardSurface(
        title = stringResource(R.string.mobile_data_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_data,
        selected = enabled,
        onClick = {
            val target = !telephonyManager.isDataEnabled
            enabled = target
            Thread { telephonyManager.isDataEnabled = target }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_NETWORK_OPERATOR_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun AirplaneModeTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun isAirplaneOn() =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    var enabled by remember { mutableStateOf(isAirplaneOn()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                enabled = isAirplaneOn()
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    TileCardSurface(
        title = stringResource(R.string.airplane_mode_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_airplane,
        selected = enabled,
        onClick = {
            val target = !isAirplaneOn()
            enabled = target
            Thread {
                Settings.Global.putInt(
                    context.contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON,
                    if (target) 1 else 0
                )
                context.sendBroadcast(
                    Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", target)
                )
            }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun DndTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nm = remember {
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }
    fun isDndOn() = nm?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL

    var enabled by remember { mutableStateOf(isDndOn()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                enabled = isDndOn()
            }
        }
        context.registerReceiver(receiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    TileCardSurface(
        title = stringResource(R.string.dnd_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_dnd,
        selected = enabled,
        onClick = {
            val target = nm?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
            val newFilter = if (target) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            enabled = target
            Thread { runCatching { nm?.setInterruptionFilter(newFilter) } }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_ZEN_MODE_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun AutoRotateTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun isAutoRotateOn() =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    var enabled by remember { mutableStateOf(isAutoRotateOn()) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = isAutoRotateOn()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            observer
        )
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }

    TileCardSurface(
        title = stringResource(R.string.auto_rotate_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_auto_rotate,
        selected = enabled,
        onClick = {
            val target = !isAutoRotateOn()
            enabled = target
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (target) 1 else 0
            )
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_DISPLAY_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun BatterySaverTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val powerManager = remember {
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    fun isPowerSaveOn(): Boolean =
        powerManager?.isPowerSaveMode == true ||
            Settings.Global.getInt(context.contentResolver, "low_power", 0) == 1

    var enabled by remember { mutableStateOf(isPowerSaveOn()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                enabled = isPowerSaveOn()
            }
        }
        context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    TileCardSurface(
        title = stringResource(R.string.battery_saver_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_battery_saver,
        selected = enabled,
        onClick = {
            val target = !isPowerSaveOn()
            enabled = target
            Thread {
                val handled = runCatching { powerManager?.setPowerSaveModeEnabled(target) == true }
                    .getOrDefault(false)
                if (!handled) {
                    runCatching {
                        Settings.Global.putInt(context.contentResolver, "low_power", if (target) 1 else 0)
                    }
                }
            }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_BATTERY_SAVER_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun HotspotTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    val connectivityManager = remember {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    fun isHotspotOn(): Boolean = runCatching {
        val method = wifiManager.javaClass.getDeclaredMethod("getWifiApState")
        method.isAccessible = true
        val state = method.invoke(wifiManager) as? Int ?: 0
        state == 12 || state == 13
    }.getOrDefault(false)

    var enabled by remember { mutableStateOf(isHotspotOn()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                enabled = isHotspotOn()
            }
        }
        context.registerReceiver(receiver, IntentFilter("android.net.wifi.WIFI_AP_STATE_CHANGED"))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    TileCardSurface(
        title = stringResource(R.string.hotspot_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_hotspot,
        selected = enabled,
        onClick = {
            val target = !isHotspotOn()
            enabled = target
            Thread {
                val toggled = runCatching {
                    if (target) {
                        val startMethod = wifiManager.javaClass.getMethod(
                            "startTetheredHotspot",
                            SoftApConfiguration::class.java
                        )
                        startMethod.invoke(wifiManager, null)
                    } else {
                        val stopMethod = wifiManager.javaClass.getMethod("stopSoftAp")
                        stopMethod.invoke(wifiManager)
                    }
                    true
                }.getOrDefault(false)
                if (!toggled && !target) {
                    runCatching {
                        val stopMethod = connectivityManager.javaClass.getMethod(
                            "stopTethering",
                            Int::class.javaPrimitiveType
                        )
                        stopMethod.invoke(connectivityManager, 0)
                    }
                }
            }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_WIRELESS_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun LocationTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val locationManager = remember {
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    var enabled by remember { mutableStateOf(locationManager.isLocationEnabled) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                enabled = locationManager.isLocationEnabled
            }
        }
        context.registerReceiver(receiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    TileCardSurface(
        title = stringResource(R.string.location_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_location,
        selected = enabled,
        onClick = {
            val target = !locationManager.isLocationEnabled
            enabled = target
            Thread {
                val updated = runCatching {
                    locationManager.setLocationEnabledForUser(target, UserHandle.CURRENT)
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
            }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun PrivateDnsTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun getDnsMode(): String =
        Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "opportunistic"
    fun getDnsSpecifier(): String =
        Settings.Global.getString(context.contentResolver, "private_dns_specifier").orEmpty()

    var dnsMode by remember { mutableStateOf(getDnsMode()) }
    var dnsHost by remember { mutableStateOf(getDnsSpecifier()) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                dnsMode = getDnsMode()
                dnsHost = getDnsSpecifier()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor("private_dns_mode"),
            false,
            observer
        )
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }

    val selected = dnsMode != "off"
    val summary = when (dnsMode) {
        "off" -> stringResource(R.string.private_dns_off)
        "hostname" -> if (dnsHost.isNotBlank()) dnsHost else stringResource(R.string.private_dns_Strict)
        else -> stringResource(R.string.private_dns_auto)
    }

    TileCardSurface(
        title = stringResource(R.string.private_dns_title),
        summary = summary,
        iconRes = R.drawable.ic_dns,
        selected = selected,
        onClick = {
            val hasHost = getDnsSpecifier().isNotBlank()
            val next = when (dnsMode) {
                "off" -> "opportunistic"
                "opportunistic" -> if (hasHost) "hostname" else "off"
                else -> "off"
            }
            Settings.Global.putString(context.contentResolver, "private_dns_mode", next)
            dnsMode = next
            dnsHost = getDnsSpecifier()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_WIRELESS_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun FlashlightTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraManager = remember {
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }
    val torchCameraId = remember(cameraManager) {
        runCatching {
            val cm = cameraManager ?: return@runCatching null
            cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    var torchEnabled by remember { mutableStateOf(false) }

    DisposableEffect(cameraManager, torchCameraId) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchCameraId) {
                    torchEnabled = enabled
                }
            }
        }
        runCatching {
            cameraManager?.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        }
        onDispose { runCatching { cameraManager?.unregisterTorchCallback(callback) } }
    }

    TileCardSurface(
        title = stringResource(R.string.flashlight_title),
        summary = stringResource(if (torchEnabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_flashlight,
        selected = torchEnabled,
        onClick = {
            val camId = torchCameraId ?: return@TileCardSurface
            val target = !torchEnabled
            torchEnabled = target
            Thread { runCatching { cameraManager?.setTorchMode(camId, target) } }.start()
        },
        modifier = modifier
    )
}

@Composable
private fun DisplayEnhancerTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val enhancer = remember { context.displayEnhancer() }
    var activePreset by remember { mutableStateOf(enhancer.active) }

    TileCardSurface(
        title = stringResource(R.string.display_enhancer_tile_title),
        summary = enhancer.labelOf(activePreset),
        iconRes = R.drawable.ic_display_enhanced,
        selected = !activePreset.isIdentity,
        onClick = {
            activePreset = enhancer.selectNext()
        },
        onLongClick = {
            runCatching {
                context.startActivity(
                    Intent(context, DisplayEnhancerActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
        modifier = modifier
    )
}

@Composable
private fun NightLightTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    fun isNightLightOn() =
        Settings.Secure.getInt(context.contentResolver, "night_display_activated", 0) == 1

    var enabled by remember { mutableStateOf(isNightLightOn()) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = isNightLightOn()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("night_display_activated"),
            false,
            observer
        )
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }

    TileCardSurface(
        title = stringResource(R.string.night_light_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_night_light,
        selected = enabled,
        onClick = {
            val target = !isNightLightOn()
            enabled = target
            Settings.Secure.putInt(
                context.contentResolver,
                "night_display_activated",
                if (target) 1 else 0
            )
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_NIGHT_DISPLAY_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun AutoSyncTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ContentResolver.getMasterSyncAutomatically()) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        val syncObserver = SyncStatusObserver {
            mainHandler.post {
                enabled = ContentResolver.getMasterSyncAutomatically()
            }
        }
        val handle = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
            syncObserver
        )
        onDispose {
            if (handle != null) ContentResolver.removeStatusChangeListener(handle)
        }
    }

    TileCardSurface(
        title = stringResource(R.string.auto_sync_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_sync,
        selected = enabled,
        onClick = {
            val target = !ContentResolver.getMasterSyncAutomatically()
            enabled = target
            Thread { ContentResolver.setMasterSyncAutomatically(target) }.start()
        },
        onLongClick = { openSystemSettings(context, Settings.ACTION_SYNC_SETTINGS) },
        modifier = modifier
    )
}

@Composable
private fun BypassChargeTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val screenUtils = remember { context.entryPointOf<ServiceViewEntryPoint>().screenUtils() }
    fun isBypassOn() =
        Settings.System.getIntForUser(
            context.contentResolver,
            "bypass_charge_enabled",
            0,
            UserHandle.USER_CURRENT
        ) == 1

    var enabled by remember { mutableStateOf(isBypassOn()) }

    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = isBypassOn()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor("bypass_charge_enabled"),
            false,
            observer
        )
        onDispose { runCatching { context.contentResolver.unregisterContentObserver(observer) } }
    }

    TileCardSurface(
        title = stringResource(R.string.bypass_charge_tile_title),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_bypass_charge,
        selected = enabled,
        onClick = {
            val target = !isBypassOn()
            enabled = target
            Settings.System.putIntForUser(
                context.contentResolver,
                "bypass_charge_enabled",
                if (target) 1 else 0,
                UserHandle.USER_CURRENT
            )
            runCatching { screenUtils.bypassCharge(target) }
        },
        modifier = modifier
    )
}

@Composable
private fun DanmakuTileComposable(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint = remember { context.entryPointOf<ServiceViewEntryPoint>() }
    val appSettings = remember { entryPoint.appSettings() }
    val gameSession = remember { entryPoint.gameSession() }
    var enabled by remember { mutableStateOf(appSettings.danmakuNotification) }

    TileCardSurface(
        title = stringResource(R.string.notification_danmaku),
        summary = stringResource(if (enabled) R.string.state_enabled else R.string.state_disabled),
        iconRes = R.drawable.ic_danmaku,
        selected = enabled,
        onClick = {
            val target = !appSettings.danmakuNotification
            gameSession.setDanmakuNotification(target)
            enabled = appSettings.danmakuNotification
        },
        modifier = modifier
    )
}
