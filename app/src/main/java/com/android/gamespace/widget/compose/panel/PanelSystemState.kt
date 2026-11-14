package com.android.gamespace.widget.compose.panel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PanelControlsState(
    val brightness: Float = 0f,
    val volume: Int = 0,
    val volumeMax: Int = 1,
    val autoBrightness: Boolean = false,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
)

class PanelSystemState(private val context: Context) {

    private val resolver = context.contentResolver
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val handler = Handler(Looper.getMainLooper())

    private val mutableState = MutableStateFlow(PanelControlsState())
    val state: StateFlow<PanelControlsState> = mutableState.asStateFlow()

    private var brightnessDragging = false
    private var volumeDragging = false
    private var watching = false

    private val brightnessObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (!brightnessDragging) syncBrightness()
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!volumeDragging) syncVolume()
        }
    }

    fun start() {
        if (watching) return
        watching = true
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            brightnessObserver,
        )
        resolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            false,
            brightnessObserver,
        )
        ContextCompat.registerReceiver(
            context,
            volumeReceiver,
            IntentFilter().apply {
                addAction("android.media.VOLUME_CHANGED_ACTION")
                addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        syncBrightness()
        syncVolume()
        syncRingerMode()
    }

    fun stop() {
        if (!watching) return
        watching = false
        resolver.unregisterContentObserver(brightnessObserver)
        runCatching { context.unregisterReceiver(volumeReceiver) }
    }

    fun beginBrightnessDrag() {
        brightnessDragging = true
    }

    fun endBrightnessDrag() {
        brightnessDragging = false
        syncBrightness()
    }

    fun setBrightness(position: Float) {
        val gamma = position.coerceIn(0f, 1f)
        val linear = convertGammaToLinear(gamma)
        val value = (linear * 254f + 1f).roundToInt().coerceIn(1, 255)
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
        mutableState.value = mutableState.value.copy(brightness = gamma)
    }

    fun beginVolumeDrag() {
        volumeDragging = true
    }

    fun endVolumeDrag() {
        volumeDragging = false
        syncVolume()
    }

    fun setVolume(volume: Int) {
        val max = mutableState.value.volumeMax.coerceAtLeast(1)
        val value = volume.coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        mutableState.value = mutableState.value.copy(volume = value)
    }

    fun toggleAutoBrightness() {
        val current = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        val next = if (current == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        } else {
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, next)
        syncBrightness()
    }

    fun cycleRingerMode() {
        audioManager.ringerMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_SILENT
            AudioManager.RINGER_MODE_SILENT -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        syncRingerMode()
    }

    private fun syncBrightness() {
        val linear = (readBrightness().coerceIn(1, 255) - 1) / 254f
        val gamma = convertLinearToGamma(linear)
        val auto = Settings.System.getInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        mutableState.value = mutableState.value.copy(brightness = gamma, autoBrightness = auto)
    }

    private fun syncVolume() {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableState.value = mutableState.value.copy(volume = volume, volumeMax = max)
    }

    private fun syncRingerMode() {
        mutableState.value = mutableState.value.copy(ringerMode = audioManager.ringerMode)
    }

    private fun readBrightness(): Int =
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)

    private companion object {
        const val R = 0.5f
        const val A = 0.17883277f
        const val B = 0.28466892f
        const val C = 0.55991073f

        fun convertGammaToLinear(gamma: Float): Float {
            val ret = if (gamma <= R) {
                (gamma / R) * (gamma / R)
            } else {
                exp(((gamma - C) / A).toDouble()).toFloat() + B
            }
            return (ret.coerceIn(0f, 12f)) / 12f
        }

        fun convertLinearToGamma(linear: Float): Float {
            val norm = linear * 12f
            return if (norm <= 1f) {
                sqrt(norm.toDouble()).toFloat() * R
            } else {
                A * ln((norm - B).toDouble()).toFloat() + C
            }.coerceIn(0f, 1f)
        }
    }
}
