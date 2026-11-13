/*
 * Copyright (C) 2021 Chaldeaprjkt
 * Copyright (C) 2023 risingOS Android Project
 *               2022 crDroid Android Project
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
package com.android.gamespace.data

import android.app.Service
import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager
import com.android.gamespace.utils.dp
import com.android.gamespace.utils.statusbarHeight
import javax.inject.Inject

class AppSettings @Inject constructor(private val context: Context) {

    // The file that the preference library called the default one, so that what was saved there
    // by earlier versions is still read: the package name and "_preferences".
    private val db by lazy {
        context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    }
    private val wm by lazy { context.getSystemService(Service.WINDOW_SERVICE) as WindowManager }

    /**
     * Calls [listener] on the main thread when a setting changes. The preferences keep only a weak
     * reference to it, so the caller has to hold on to it for as long as it wants to listen.
     */
    fun registerOnChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        db.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterOnChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        db.unregisterOnSharedPreferenceChangeListener(listener)

    var x
        get() = db.getInt("offset_x", wm.maximumWindowMetrics.bounds.width() / 2)
        set(point) = db.edit().putInt("offset_x", point).apply()

    var y
        get() = db.getInt("offset_y", context.statusbarHeight + 8.dp)
        set(point) = db.edit().putInt("offset_y", point).apply()

    var showFps
        get() = db.getBoolean("show_fps", false)
        set(point) = db.edit().putBoolean("show_fps", point).apply()

    var noAutoBrightness
        get() = db.getBoolean(KEY_AUTO_BRIGHTNESS_DISABLE, true)
        set(it) = db.edit().putBoolean(KEY_AUTO_BRIGHTNESS_DISABLE, it).apply()

    var noThreeScreenshot
        get() = db.getBoolean(KEY_3SCREENSHOT_DISABLE, false)
        set(it) = db.edit().putBoolean(KEY_3SCREENSHOT_DISABLE, it).apply()

    var stayAwake
        get() = db.getBoolean(KEY_STAY_AWAKE, false)
        set(value) = db.edit().putBoolean(KEY_STAY_AWAKE, value).apply()

    var danmakuNotification
        get() = db.getBoolean(KEY_DANMAKU_NOTIFICATION_MODE, false)
        set(value) = db.edit().putBoolean(KEY_DANMAKU_NOTIFICATION_MODE, value).apply()

    var callsMode: Int
        get() = db.getString(KEY_CALLS_MODE, "0")?.toIntOrNull() ?: 0
        set(value) = db.edit().putString(KEY_CALLS_MODE, value.toString()).apply()

    var ringerMode: Int
        get() = db.getString(KEY_RINGER_MODE, "3")?.toIntOrNull() ?: 3
        set(value) = db.edit().putString(KEY_RINGER_MODE, value.toString()).apply()

    var menuOpacity: Int
        get() = db.getInt(KEY_MENU_OPACITY, 100)
        set(value) = db.edit().putInt(KEY_MENU_OPACITY, value).apply()

    var lockGesture
        get() = db.getBoolean(KEY_LOCK_GESTURE, false)
        set(value) = db.edit().putBoolean(KEY_LOCK_GESTURE, value).apply()

    var autoDnd
        get() = db.getBoolean(KEY_AUTO_DND, false)
        set(value) = db.edit().putBoolean(KEY_AUTO_DND, value).apply()

    var barAutoHideEnabled
        get() = db.getBoolean(KEY_BAR_AUTO_HIDE_ENABLED, true)
        set(value) = db.edit().putBoolean(KEY_BAR_AUTO_HIDE_ENABLED, value).apply()

    var barAutoHideDelaySec
        get() = db.getInt(KEY_BAR_AUTO_HIDE_DELAY, DEFAULT_BAR_AUTO_HIDE_DELAY_SEC)
        set(value) = db.edit().putInt(KEY_BAR_AUTO_HIDE_DELAY, value).apply()

    var selectedTiles: List<String>
        get() = db.getString(KEY_SELECTED_TILES, DEFAULT_TILES)!!.split(",")
        set(value) = db.edit().putString(KEY_SELECTED_TILES, value.joinToString(",")).apply()

    /** The Game Display Enhanced preset that games start with. */
    var displayPresetId: String
        get() = db.getString(KEY_DISPLAY_PRESET, DEFAULT_DISPLAY_PRESET) ?: DEFAULT_DISPLAY_PRESET
        set(value) = db.edit().putString(KEY_DISPLAY_PRESET, value).apply()

    /** The custom presets in the format of DisplayPresetCodec. */
    var displayCustomPresets: String
        get() = db.getString(KEY_DISPLAY_CUSTOM_PRESETS, "") ?: ""
        set(value) = db.edit().putString(KEY_DISPLAY_CUSTOM_PRESETS, value).apply()

    companion object {
        const val KEY_AUTO_BRIGHTNESS_DISABLE = "gamespace_auto_brightness_disabled"
        const val KEY_3SCREENSHOT_DISABLE = "gamespace_tfgesture_disabled"
        const val KEY_STAY_AWAKE = "gamespace_stay_awake"
        const val KEY_DANMAKU_NOTIFICATION_MODE = "gamespace_danmaku_notification_mode"
        const val KEY_CALLS_MODE = "gamespace_calls_mode"
        const val KEY_RINGER_MODE = "gamespace_ringer_mode"
        const val KEY_LOCK_GESTURE = "gamespace_lock_gesture"
        const val KEY_MENU_OPACITY = "gamespace_menu_opacity"
        const val KEY_AUTO_DND = "gamespace_auto_dnd"
        const val KEY_BAR_AUTO_HIDE_ENABLED = "gamespace_bar_auto_hide_enabled"
        const val KEY_BAR_AUTO_HIDE_DELAY = "gamespace_bar_auto_hide_delay_sec"
        const val DEFAULT_BAR_AUTO_HIDE_DELAY_SEC = 5
        const val KEY_SELECTED_TILES = "gamespace_selected_tiles"
        const val DEFAULT_TILES = "game_mode,fps_info,fps_record,stay_awake,lock_gesture,wifi,dnd,mobile_data,airplane_mode"
        const val KEY_DISPLAY_PRESET = "gamespace_display_preset"
        const val KEY_DISPLAY_CUSTOM_PRESETS = "gamespace_display_custom_presets"
        const val DEFAULT_DISPLAY_PRESET = "standard"
    }
}
