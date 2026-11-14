package com.android.gamespace.widget.compose.tiles

import android.app.GameManager
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SyncStatusObserver
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.UserHandle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.display.DisplayEnhancerActivity
import com.android.gamespace.display.displayEnhancer
import com.android.gamespace.utils.FpsRecorder
import com.android.gamespace.utils.GameModeUtils.Companion.describeGameMode
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf
import com.android.gamespace.widget.tiles.TileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TileUiState(
    @StringRes val titleRes: Int,
    val summary: String,
    val enabled: Boolean,
    @DrawableRes val iconRes: Int,
)

interface TileController {
    val type: TileType
    val state: StateFlow<TileUiState>

    fun start()
    fun stop()
    fun toggle()
    fun longPress()
}

abstract class BaseTileController(
    protected val context: Context,
    override val type: TileType,
) : TileController {

    private val mutableState = MutableStateFlow(
        TileUiState(type.titleRes, "", false, type.iconRes)
    )

    override val state: StateFlow<TileUiState> = mutableState.asStateFlow()

    protected fun publish(summary: String, enabled: Boolean) {
        mutableState.value = mutableState.value.copy(summary = summary, enabled = enabled)
    }

    protected fun openSettings(action: String) {
        runCatching {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun start() = Unit
    override fun stop() = Unit
    override fun longPress() = Unit
}

private class ToggleTileController(
    context: Context,
    type: TileType,
    private val read: () -> Boolean,
    private val write: (Boolean) -> Unit,
    private val summary: (Boolean) -> String,
    private val watcher: ((() -> Unit) -> () -> Unit)? = null,
    private val onLongClick: (() -> Unit)? = null,
) : BaseTileController(context, type) {

    private var stopWatching: (() -> Unit)? = null

    override fun start() {
        stopWatching?.invoke()
        stopWatching = watcher?.invoke { refresh() }
        refresh()
    }

    override fun stop() {
        stopWatching?.invoke()
        stopWatching = null
    }

    override fun toggle() {
        val target = !read()
        publish(summary(target), target)
        write(target)
    }

    override fun longPress() {
        onLongClick?.invoke()
    }

    private fun refresh() {
        val enabled = read()
        publish(summary(enabled), enabled)
    }
}

private class GameModeTileController(
    context: Context,
    type: TileType,
) : BaseTileController(context, type) {

    private val gameModeUtils by lazy { context.entryPointOf<ServiceViewEntryPoint>().gameModeUtils() }
    private val systemSettings by lazy { context.entryPointOf<ServiceViewEntryPoint>().systemSettings() }

    private val modes = listOf(
        GameManager.GAME_MODE_STANDARD,
        GameManager.GAME_MODE_PERFORMANCE,
        GameManager.GAME_MODE_BATTERY,
    )

    override fun start() = refresh()

    override fun toggle() {
        val current = gameModeUtils.activeGame?.mode ?: GameManager.GAME_MODE_STANDARD
        val index = modes.indexOf(current)
        val next = modes[if (index == modes.lastIndex) 0 else index + 1]
        gameModeUtils.setActiveGameMode(systemSettings, next)
        refresh()
    }

    private fun refresh() {
        val mode = gameModeUtils.activeGame?.mode ?: GameManager.GAME_MODE_STANDARD
        publish(context.describeGameMode(mode), mode != GameManager.GAME_MODE_STANDARD)
    }
}

private class FpsRecordTileController(
    context: Context,
    type: TileType,
) : BaseTileController(context, type) {

    private val recorder by lazy { context.entryPointOf<ServiceViewEntryPoint>().fpsRecorder() }
    private var shownSecond = -1L

    private val listener = object : FpsRecorder.Listener {
        override fun onStateChanged(recording: Boolean) = render()

        override fun onSample(elapsedMs: Long, fps: Float) {
            if (elapsedMs / 1000 != shownSecond) render()
        }
    }

    override fun start() {
        recorder.addListener(listener, wantsSamples = true)
        render()
    }

    override fun stop() {
        recorder.removeListener(listener)
    }

    override fun toggle() {
        if (recorder.isRecording) recorder.stop() else recorder.start()
        render()
    }

    private fun render() {
        val recording = recorder.isRecording
        val seconds = recorder.elapsedMs / 1000
        shownSecond = if (recording) seconds else -1L
        val text = if (recording) {
            context.getString(
                R.string.fps_record_summary_recording,
                DateUtils.formatElapsedTime(seconds),
            )
        } else {
            context.getString(R.string.fps_record_summary_idle)
        }
        publish(text, recording)
    }
}

private class DisplayEnhancerTileController(
    context: Context,
    type: TileType,
) : BaseTileController(context, type) {

    private val enhancer by lazy { context.displayEnhancer() }

    override fun start() = render()

    override fun toggle() {
        enhancer.selectNext()
        render()
    }

    override fun longPress() {
        runCatching {
            context.startActivity(
                Intent(context, DisplayEnhancerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun render() {
        val preset = enhancer.active
        publish(enhancer.labelOf(preset), !preset.isIdentity)
    }
}

private class PrivateDnsTileController(
    context: Context,
    type: TileType,
) : BaseTileController(context, type) {

    private var stopWatching: (() -> Unit)? = null

    override fun start() {
        stopWatching?.invoke()
        stopWatching = context.contentResolver.observe(
            Settings.Global.getUriFor(KEY_PRIVATE_DNS_MODE),
        ) { refresh() }
        refresh()
    }

    override fun stop() {
        stopWatching?.invoke()
        stopWatching = null
    }

    override fun toggle() {
        val hasHost = specifier().isNotBlank()
        val next = when (mode()) {
            MODE_OFF -> MODE_OPPORTUNISTIC
            MODE_OPPORTUNISTIC -> if (hasHost) MODE_HOSTNAME else MODE_OFF
            else -> MODE_OFF
        }
        Settings.Global.putString(context.contentResolver, KEY_PRIVATE_DNS_MODE, next)
        refresh()
    }

    override fun longPress() {
        openSettings(Settings.ACTION_WIRELESS_SETTINGS)
    }

    private fun mode(): String =
        Settings.Global.getString(context.contentResolver, KEY_PRIVATE_DNS_MODE)
            ?: MODE_OPPORTUNISTIC

    private fun specifier(): String =
        Settings.Global.getString(context.contentResolver, KEY_PRIVATE_DNS_SPECIFIER).orEmpty()

    private fun refresh() {
        when (mode()) {
            MODE_OFF -> publish(context.getString(R.string.private_dns_off), false)
            MODE_HOSTNAME -> {
                val host = specifier()
                publish(
                    if (host.isNotBlank()) host else context.getString(R.string.private_dns_Strict),
                    true,
                )
            }
            else -> publish(context.getString(R.string.private_dns_auto), true)
        }
    }

    private companion object {
        const val KEY_PRIVATE_DNS_MODE = "private_dns_mode"
        const val KEY_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
        const val MODE_OFF = "off"
        const val MODE_OPPORTUNISTIC = "opportunistic"
        const val MODE_HOSTNAME = "hostname"
    }
}

private class FlashlightTileController(
    context: Context,
    type: TileType,
) : BaseTileController(context, type) {

    private val cameraManager: CameraManager? by lazy {
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private var torchCameraId: String? = null
    private var torchEnabled = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
                torchEnabled = enabled
                refresh()
            }
        }
    }

    override fun start() {
        torchCameraId = findTorchCameraId()
        refresh()
        runCatching {
            cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        }
    }

    override fun stop() {
        runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
    }

    override fun toggle() {
        val cameraId = torchCameraId ?: findTorchCameraId().also { torchCameraId = it } ?: return
        val target = !torchEnabled
        torchEnabled = target
        refresh()
        Thread {
            runCatching { cameraManager?.setTorchMode(cameraId, target) }
        }.start()
    }

    private fun findTorchCameraId(): String? = runCatching {
        val manager = cameraManager ?: return null
        manager.cameraIdList.firstOrNull { id ->
            val chars = manager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()

    private fun refresh() {
        publish(enabledSummary(context, torchEnabled), torchEnabled)
    }
}

object TileControllerFactory {
    fun create(context: Context, type: TileType): TileController {
        val appSettings = context.entryPointOf<ServiceViewEntryPoint>().appSettings()
        val screenUtils by lazy { context.entryPointOf<ServiceViewEntryPoint>().screenUtils() }
        val gameSession by lazy { context.entryPointOf<ServiceViewEntryPoint>().gameSession() }
        return when (type) {
            TileType.GAME_MODE -> GameModeTileController(context, type)
            TileType.FPS_INFO -> settingToggle(
                context, type, appSettings, "show_fps",
                { it.showFps }, { value -> appSettings.showFps = value },
            )
            TileType.FPS_RECORD -> FpsRecordTileController(context, type)
            TileType.STAY_AWAKE -> settingToggle(
                context, type, appSettings, AppSettings.KEY_STAY_AWAKE,
                { it.stayAwake }, { value ->
                    appSettings.stayAwake = value
                    screenUtils.stayAwake = value
                },
            )
            TileType.LOCK_GESTURE -> settingToggle(
                context, type, appSettings, AppSettings.KEY_LOCK_GESTURE,
                { it.lockGesture }, { value ->
                    appSettings.lockGesture = value
                    screenUtils.lockGesture = value
                },
            )
            TileType.WIFI -> wifi(context, type)
            TileType.BLUETOOTH -> bluetooth(context, type)
            TileType.MOBILE_DATA -> mobileData(context, type)
            TileType.AIRPLANE_MODE -> airplaneMode(context, type)
            TileType.DND -> dnd(context, type)
            TileType.AUTO_ROTATE -> autoRotate(context, type)
            TileType.BATTERY_SAVER -> batterySaver(context, type)
            TileType.HOTSPOT -> hotspot(context, type)
            TileType.LOCATION -> location(context, type)
            TileType.PRIVATE_DNS -> PrivateDnsTileController(context, type)
            TileType.FLASHLIGHT -> FlashlightTileController(context, type)
            TileType.DISPLAY_ENHANCER -> DisplayEnhancerTileController(context, type)
            TileType.NIGHT_LIGHT -> nightLight(context, type)
            TileType.AUTO_SYNC -> autoSync(context, type)
            TileType.BYPASS_CHARGE -> bypassCharge(context, type)
            TileType.DANMAKU -> settingToggle(
                context, type, appSettings, AppSettings.KEY_DANMAKU_NOTIFICATION_MODE,
                { it.danmakuNotification }, { gameSession.setDanmakuNotification(it) },
            )
        }
    }

    private fun settingToggle(
        context: Context,
        type: TileType,
        appSettings: AppSettings,
        key: String,
        read: (AppSettings) -> Boolean,
        write: (Boolean) -> Unit,
    ): TileController = ToggleTileController(
        context,
        type,
        read = { read(appSettings) },
        write = write,
        summary = { enabledSummary(context, it) },
        watcher = { onChange ->
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
                if (changed == key) onChange()
            }
            appSettings.registerOnChange(listener)
            val cleanup: () -> Unit = { appSettings.unregisterOnChange(listener) }
            cleanup
        },
    )

    private fun wifi(context: Context, type: TileType): TileController {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return ToggleTileController(
            context,
            type,
            read = { manager.isWifiEnabled },
            write = { enabled -> Thread { manager.isWifiEnabled = enabled }.start() },
            summary = { enabledSummary(context, it) },
            watcher = { onChange -> context.broadcasts(WifiManager.WIFI_STATE_CHANGED_ACTION, onChange) },
            onLongClick = { context.openSettings(Settings.ACTION_WIFI_SETTINGS) },
        )
    }

    private fun bluetooth(context: Context, type: TileType): TileController {
        val adapter = (context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager)?.adapter
        return ToggleTileController(
            context,
            type,
            read = { adapter?.isEnabled == true },
            write = { enabled ->
                Thread { if (enabled) adapter?.enable() else adapter?.disable() }.start()
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                context.broadcasts(BluetoothAdapter.ACTION_STATE_CHANGED, onChange)
            },
        )
    }

    private fun mobileData(context: Context, type: TileType): TileController {
        val manager = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE)
            as TelephonyManager
        return ToggleTileController(
            context,
            type,
            read = { manager.isDataEnabled },
            write = { enabled -> Thread { manager.isDataEnabled = enabled }.start() },
            summary = { enabledSummary(context, it) },
            onLongClick = { context.openSettings(Settings.ACTION_NETWORK_OPERATOR_SETTINGS) },
        )
    }

    private fun airplaneMode(context: Context, type: TileType): TileController =
        ToggleTileController(
            context,
            type,
            read = {
                Settings.Global.getInt(
                    context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0,
                ) != 0
            },
            write = { enabled ->
                Thread {
                    Settings.Global.putInt(
                        context.contentResolver,
                        Settings.Global.AIRPLANE_MODE_ON,
                        if (enabled) 1 else 0,
                    )
                    context.sendBroadcast(
                        Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED).putExtra("state", enabled)
                    )
                }.start()
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                context.broadcasts(Intent.ACTION_AIRPLANE_MODE_CHANGED, onChange)
            },
            onLongClick = { context.openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
        )

    private fun dnd(context: Context, type: TileType): TileController {
        val manager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager
        return ToggleTileController(
            context,
            type,
            read = {
                manager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            },
            write = { enabled ->
                val filter = if (enabled) {
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                Thread { runCatching { manager?.setInterruptionFilter(filter) } }.start()
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                context.broadcasts(
                    NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED,
                    onChange = onChange,
                )
            },
            onLongClick = { context.openSettings(Settings.ACTION_ZEN_MODE_SETTINGS) },
        )
    }

    private fun autoRotate(context: Context, type: TileType): TileController =
        ToggleTileController(
            context,
            type,
            read = {
                Settings.System.getInt(
                    context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0,
                ) == 1
            },
            write = { enabled ->
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.ACCELEROMETER_ROTATION,
                    if (enabled) 1 else 0,
                )
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                context.contentResolver.observe(
                    Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                    onChange = onChange,
                )
            },
            onLongClick = { context.openSettings(Settings.ACTION_DISPLAY_SETTINGS) },
        )

    private fun batterySaver(context: Context, type: TileType): TileController {
        val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE)
            as? PowerManager
        return ToggleTileController(
            context,
            type,
            read = {
                powerManager?.isPowerSaveMode == true ||
                    Settings.Global.getInt(context.contentResolver, "low_power", 0) == 1
            },
            write = { enabled ->
                Thread {
                    val handled = runCatching {
                        powerManager?.setPowerSaveModeEnabled(enabled) == true
                    }.getOrDefault(false)
                    if (!handled) {
                        runCatching {
                            Settings.Global.putInt(
                                context.contentResolver, "low_power", if (enabled) 1 else 0,
                            )
                        }
                    }
                }.start()
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                context.broadcasts(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED, onChange)
            },
            onLongClick = { context.openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS) },
        )
    }

    private fun hotspot(context: Context, type: TileType): TileController {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as WifiManager
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager
        return ToggleTileController(
            context,
            type,
            read = { wifiManager.isHotspotEnabled() },
            write = { enabled ->
                Thread {
                    val toggled = runCatching {
                        if (enabled) {
                            val start = wifiManager.javaClass.getMethod(
                                "startTetheredHotspot",
                                android.net.wifi.SoftApConfiguration::class.java,
                            )
                            start.invoke(wifiManager, null)
                        } else {
                            wifiManager.javaClass.getMethod("stopSoftAp").invoke(wifiManager)
                        }
                        true
                    }.getOrDefault(false)
                    if (!toggled && !enabled) {
                        runCatching {
                            connectivityManager.javaClass.getMethod(
                                "stopTethering",
                                Int::class.javaPrimitiveType,
                            ).invoke(connectivityManager, TETHERING_WIFI)
                        }
                    }
                }.start()
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange -> context.broadcasts(ACTION_WIFI_AP_STATE_CHANGED, onChange) },
            onLongClick = { context.openSettings(Settings.ACTION_WIRELESS_SETTINGS) },
        )
    }

    private fun location(context: Context, type: TileType): TileController {
        val manager = context.applicationContext.getSystemService(Context.LOCATION_SERVICE)
            as LocationManager
        return ToggleTileController(
            context,
            type,
            read = { manager.isLocationEnabled },
            write = { enabled ->
                Thread {
                    val updated = runCatching {
                        manager.setLocationEnabledForUser(enabled, UserHandle.CURRENT)
                        true
                    }.getOrDefault(false)
                    if (!updated) {
                        runCatching {
                            Settings.Secure.putInt(
                                context.contentResolver,
                                Settings.Secure.LOCATION_MODE,
                                if (enabled) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                                else Settings.Secure.LOCATION_MODE_OFF,
                            )
                        }
                    }
                }.start()
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                context.broadcasts(LocationManager.MODE_CHANGED_ACTION, onChange)
            },
            onLongClick = { context.openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
        )
    }

    private fun nightLight(context: Context, type: TileType): TileController =
        ToggleTileController(
            context,
            type,
            read = {
                Settings.Secure.getInt(
                    context.contentResolver, KEY_NIGHT_DISPLAY_ACTIVATED, 0,
                ) == 1
            },
            write = { enabled ->
                Settings.Secure.putInt(
                    context.contentResolver,
                    KEY_NIGHT_DISPLAY_ACTIVATED,
                    if (enabled) 1 else 0,
                )
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                context.contentResolver.observe(
                    Settings.Secure.getUriFor(KEY_NIGHT_DISPLAY_ACTIVATED),
                    onChange = onChange,
                )
            },
            onLongClick = { context.openSettings(Settings.ACTION_NIGHT_DISPLAY_SETTINGS) },
        )

    private fun autoSync(context: Context, type: TileType): TileController =
        ToggleTileController(
            context,
            type,
            read = { ContentResolver.getMasterSyncAutomatically() },
            write = { enabled -> Thread { ContentResolver.setMasterSyncAutomatically(enabled) }.start() },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                val observer = SyncStatusObserver { onChange() }
                val handle = ContentResolver.addStatusChangeListener(
                    ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
                    observer,
                )
                val cleanup: () -> Unit = {
                    ContentResolver.removeStatusChangeListener(handle)
                }
                cleanup
            },
            onLongClick = { context.openSettings(Settings.ACTION_SYNC_SETTINGS) },
        )

    private fun bypassCharge(context: Context, type: TileType): TileController {
        val screenUtils by lazy { context.entryPointOf<ServiceViewEntryPoint>().screenUtils() }
        return ToggleTileController(
            context,
            type,
            read = {
                Settings.System.getIntForUser(
                    context.contentResolver, KEY_BYPASS_CHARGE, 0, UserHandle.USER_CURRENT,
                ) == 1
            },
            write = { enabled ->
                Settings.System.putIntForUser(
                    context.contentResolver,
                    KEY_BYPASS_CHARGE,
                    if (enabled) 1 else 0,
                    UserHandle.USER_CURRENT,
                )
                runCatching { screenUtils.bypassCharge(enabled) }
            },
            summary = { enabledSummary(context, it) },
            watcher = { onChange ->
                context.contentResolver.observe(
                    Settings.System.getUriFor(KEY_BYPASS_CHARGE),
                    onChange = onChange,
                )
            },
        )
    }

    private const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val WIFI_AP_STATE_ENABLED = 13
    private const val WIFI_AP_STATE_ENABLING = 12
    private const val TETHERING_WIFI = 0
    private const val KEY_NIGHT_DISPLAY_ACTIVATED = "night_display_activated"
    private const val KEY_BYPASS_CHARGE = "bypass_charge_enabled"
}

private fun enabledSummary(context: Context, enabled: Boolean): String = context.getString(
    if (enabled) R.string.state_enabled else R.string.state_disabled,
)

private fun Context.openSettings(action: String) {
    runCatching {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun ContentResolver.observe(vararg uris: Uri, onChange: () -> Unit): () -> Unit {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = onChange()

        override fun onChange(selfChange: Boolean, uri: Uri?) = onChange()
    }
    uris.forEach { registerContentObserver(it, false, observer) }
    return { unregisterContentObserver(observer) }
}

private fun Context.broadcasts(action: String, onChange: () -> Unit): () -> Unit {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = onChange()
    }
    ContextCompat.registerReceiver(
        this,
        receiver,
        IntentFilter(action),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    return { runCatching { unregisterReceiver(receiver) } }
}

private fun WifiManager.isHotspotEnabled(): Boolean = runCatching {
    val method = javaClass.getDeclaredMethod("getWifiApState")
    method.isAccessible = true
    val state = method.invoke(this) as? Int ?: 0
    state == 13 || state == 12
}.getOrDefault(false)
