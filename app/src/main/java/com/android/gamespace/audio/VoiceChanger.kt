/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gamespace.audio

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls the system "Voice Changer" input effect. The native AIDL effect is attached
 * to every microphone-like input device (built-in mic, wired/USB headset, Bluetooth SCO),
 * so whatever device a game pins for capture gets the processed audio while the effect is
 * enabled.
 */
@Singleton
class VoiceChanger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettings,
) {
    data class State(
        val available: Boolean = false,
        val enabled: Boolean = false,
        val preset: Int = Presets.ORIGINAL,
        val intensity: Int = 100,
        val previewing: Boolean = false,
        val error: String? = null,
        val uiVisible: Boolean = false,
    )

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val stateFlow = MutableStateFlow(State())
    val state: StateFlow<State> = stateFlow.asStateFlow()

    /** One effect handle per attached input device, keyed by "<type>:<address>". */
    private val effects = mutableMapOf<String, AudioEffect>()
    private var sessionActive = false
    private var preview: VoicePreview? = null
    private val handler = Handler(Looper.getMainLooper())

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            if (sessionActive) handler.post { syncAll() }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            if (sessionActive) handler.post { syncAll() }
        }
    }

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppSettings.KEY_VOICE_CHANGER_ENABLED) {
            handler.post { syncAll() }
        }
    }

    /** Called when the game session starts. */
    fun startSession() {
        if (!sessionActive) {
            appSettings.registerOnChange(listener)
            audioManager?.registerAudioDeviceCallback(deviceCallback, handler)
            sessionActive = true
            refreshCommonState()
        }
        syncAll()
    }

    /** Called when the game session ends. */
    fun stopSession() {
        handler.post { releaseAll() }
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        appSettings.unregisterOnChange(listener)
        sessionActive = false
        stateFlow.value = State()
    }

    fun setEnabled(enabled: Boolean) {
        appSettings.voiceChangerEnabled = enabled
        stateFlow.value = stateFlow.value.copy(enabled = enabled)
        syncAll()
    }

    fun selectPreset(index: Int) {
        appSettings.voiceChangerPreset = index
        stateFlow.value = stateFlow.value.copy(preset = index)
        applyParamsAll()
    }

    fun setIntensity(intensity: Int) {
        appSettings.voiceChangerIntensity = intensity.coerceIn(0, 100)
        stateFlow.value = stateFlow.value.copy(intensity = intensity.coerceIn(0, 100))
        applyParamsAll()
    }

    /** Opens the preset selection UI from the tile long press. */
    fun openUi() {
        refreshCommonState()
        stateFlow.value = stateFlow.value.copy(uiVisible = true)
    }

    /** Closes the preset selection UI. */
    fun closeUi() {
        stateFlow.value = stateFlow.value.copy(uiVisible = false)
    }

    fun labelOf(index: Int): String = when (index) {
        Presets.ORIGINAL -> context.getString(R.string.voice_preset_original)
        Presets.MALE -> context.getString(R.string.voice_preset_male)
        Presets.FEMALE -> context.getString(R.string.voice_preset_female)
        Presets.CHILD -> context.getString(R.string.voice_preset_child)
        Presets.ROBOT -> context.getString(R.string.voice_preset_robot)
        Presets.DEEP -> context.getString(R.string.voice_preset_deep)
        Presets.HIGH -> context.getString(R.string.voice_preset_high)
        Presets.MONSTER -> context.getString(R.string.voice_preset_monster)
        Presets.ALIEN -> context.getString(R.string.voice_preset_alien)
        Presets.CARTOON -> context.getString(R.string.voice_preset_cartoon)
        else -> context.getString(R.string.voice_preset_original)
    }

    /** Whether the effect engine can be attached to at least one input device. */
    fun isAvailable(): Boolean = supportedInputDevices().isNotEmpty()

    private fun refreshCommonState() {
        stateFlow.value = stateFlow.value.copy(
            available = isAvailable(),
            enabled = appSettings.voiceChangerEnabled,
            preset = appSettings.voiceChangerPreset,
            intensity = appSettings.voiceChangerIntensity,
        )
    }

    private fun keyOf(device: AudioDeviceAttributes): String =
        "${device.type}:${device.address}"

    /** Every input device the native effect can be attached to. */
    private fun supportedInputDevices(): List<AudioDeviceAttributes> {
        val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS) ?: return emptyList()
        return devices.filter { it.type in MIC_DEVICE_TYPES }.mapNotNull { info ->
            runCatching { AudioDeviceAttributes(info) }.getOrNull()
        }.distinctBy { keyOf(it) }.filter { device ->
            runCatching {
                AudioEffect.isEffectSupportedForDevice(EFFECT_UUID, device)
            }.getOrDefault(false)
        }.also {
            if (it.isNotEmpty()) Log.i(TAG, "Supported input devices: $it")
        }
    }

    private fun createHandle(device: AudioDeviceAttributes): AudioEffect? =
        runCatching { AudioEffect(EFFECT_UUID, device) }
            .onSuccess { Log.i(TAG, "Attached to $device") }
            .onFailure { Log.w(TAG, "Cannot attach to $device", it) }
            .getOrNull()

    private fun applyParams(fx: AudioEffect) {
        runCatching {
            fx.setParameter(PARAM_PRESET, stateFlow.value.preset)
            fx.setParameter(PARAM_INTENSITY, stateFlow.value.intensity)
        }.onFailure { Log.w(TAG, "setParameter failed", it) }
    }

    /** Attach / refresh the effect on every supported input device. */
    private fun syncAll() {
        val needed = supportedInputDevices()
        if (needed.isEmpty()) {
            releaseAll()
            refreshCommonState()
            stateFlow.value = stateFlow.value.copy(
                error = context.getString(R.string.voice_unavailable),
            )
            return
        }

        val neededDevices = needed.associateBy { keyOf(it) }

        // Remove handles whose device disappeared.
        val stale = effects.keys.filter { it !in neededDevices.keys }
        stale.forEach { key ->
            runCatching { effects.remove(key)?.release() }
        }

        // Create missing handles and apply parameters.
        neededDevices.forEach { (key, device) ->
            if (!effects.containsKey(key)) {
                effects[key] = createHandle(device) ?: return@forEach
            }
            effects[key]?.let { applyParams(it) }
        }

        // Apply the enable state; recreate handles that died (e.g. after an audioserver
        // restart) and retry once.
        val enabled = stateFlow.value.enabled
        val failed = mutableSetOf<String>()
        neededDevices.keys.forEach { key ->
            val fx = effects[key] ?: return@forEach
            val status = runCatching { fx.setEnabled(enabled) }
                .getOrElse { AudioEffect.ERROR_INVALID_OPERATION }
            if (status != AudioEffect.SUCCESS) {
                Log.w(TAG, "setEnabled($enabled) on $key returned $status")
                failed += key
            }
        }
        var anyEnabledError = false
        if (failed.isNotEmpty()) {
            failed.forEach { key -> runCatching { effects.remove(key)?.release() } }
            failed.forEach { key ->
                val device = neededDevices[key] ?: return@forEach
                val fx = createHandle(device) ?: return@forEach
                effects[key] = fx
                applyParams(fx)
                val status = runCatching { fx.setEnabled(enabled) }
                    .getOrElse { AudioEffect.ERROR_INVALID_OPERATION }
                if (status != AudioEffect.SUCCESS) {
                    Log.w(TAG, "setEnabled($enabled) on $key retry returned $status")
                    anyEnabledError = true
                }
            }
        }

        refreshCommonState()
        stateFlow.value = stateFlow.value.copy(
            error = when {
                !isAvailable() -> context.getString(R.string.voice_unavailable)
                anyEnabledError -> context.getString(R.string.voice_enable_failed)
                else -> null
            },
        )
        Log.i(TAG, "syncAll: devices=${needed.size} enabled=$enabled")
    }

    private fun applyParamsAll() {
        effects.values.forEach { fx -> applyParams(fx) }
    }

    private fun releaseAll() {
        effects.values.forEach { fx -> runCatching { fx.setEnabled(false) } }
        effects.values.forEach { fx -> runCatching { fx.release() } }
        effects.clear()
    }

    /** Starts or stops a short preview recording + playback while a game is running. */
    fun togglePreview() {
        if (preview?.isRunning == true) {
            preview?.stop()
            preview = null
            stateFlow.value = stateFlow.value.copy(previewing = false)
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            stateFlow.value = stateFlow.value.copy(
                error = context.getString(R.string.voice_needs_mic_permission),
            )
            return
        }
        val p = VoicePreview(context) { previewing ->
            stateFlow.value = stateFlow.value.copy(previewing = previewing)
        }
        preview = p
        p.start()
    }

    object Presets {
        const val ORIGINAL = 0
        const val MALE = 1
        const val FEMALE = 2
        const val CHILD = 3
        const val ROBOT = 4
        const val DEEP = 5
        const val HIGH = 6
        const val MONSTER = 7
        const val ALIEN = 8
        const val CARTOON = 9

        val ALL = listOf(
            ORIGINAL, MALE, FEMALE, CHILD, ROBOT, DEEP, HIGH, MONSTER, ALIEN, CARTOON,
        )
    }

    companion object {
        private const val TAG = "GameSpaceVoiceChanger"

        // Must match kVoiceChangerImplUuid / kVoiceChangerTypeUuid in VoiceChangerTypes.h.
        val EFFECT_UUID: UUID = UUID.fromString("c53f4ac5-8f5a-4d77-a8e4-3a6b9e2f5c02")
        val EFFECT_TYPE_UUID: UUID = UUID.fromString("b32f39b4-ba49-4e6a-9e3c-2f5c7d1e8a01")
        const val PARAM_PRESET = 0x56430001
        const val PARAM_INTENSITY = 0x56430002

        /** Input device types that can carry the game microphone capture. */
        private val MIC_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_MIC,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
