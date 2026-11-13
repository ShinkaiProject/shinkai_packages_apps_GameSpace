package com.android.gamespace.ui.overlay.tiles

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.gamespace.R

/**
 * The tiles that the panel can show: what each is called, its icon and the id it is saved by.
 *
 * The mobile data tile used R.drawable.ic_mobile_4_4_bar, which is not in the resources; ic_data,
 * the signal bars that were added together with the tile, is what it is meant to show.
 */
enum class TileType(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
) {
    GAME_MODE("game_mode", R.string.game_mode_title, R.drawable.ic_speed),
    FPS_INFO("fps_info", R.string.fps_Info_title, R.drawable.ic_fps),
    FPS_RECORD("fps_record", R.string.fps_record_title, R.drawable.ic_fps_record),
    STAY_AWAKE("stay_awake", R.string.stay_awake_title, R.drawable.ic_awake),
    LOCK_GESTURE("lock_gesture", R.string.lock_gesture_title, R.drawable.ic_gesture),
    WIFI("wifi", R.string.wifi_title, R.drawable.ic_wifi),
    BLUETOOTH("bluetooth", R.string.bluetooth_title, R.drawable.ic_bluetooth),
    MOBILE_DATA("mobile_data", R.string.mobile_data_title, R.drawable.ic_data),
    AIRPLANE_MODE("airplane_mode", R.string.airplane_mode_title, R.drawable.ic_airplane),
    DND("dnd", R.string.dnd_title, R.drawable.ic_dnd),
    AUTO_ROTATE("auto_rotate", R.string.auto_rotate_title, R.drawable.ic_auto_rotate),
    BATTERY_SAVER("battery_saver", R.string.battery_saver_title, R.drawable.ic_battery_saver),
    HOTSPOT("hotspot", R.string.hotspot_title, R.drawable.ic_hotspot),
    LOCATION("location", R.string.location_title, R.drawable.ic_location),
    PRIVATE_DNS("private_dns", R.string.private_dns_title, R.drawable.ic_dns),
    FLASHLIGHT("flashlight", R.string.flashlight_title, R.drawable.ic_flashlight),
    DISPLAY_ENHANCER("display_enhancer", R.string.display_enhancer_tile_title, R.drawable.ic_display_enhanced),
    NIGHT_LIGHT("night_light", R.string.night_light_title, R.drawable.ic_night_light),
    AUTO_SYNC("auto_sync", R.string.auto_sync_title, R.drawable.ic_sync),
    BYPASS_CHARGE("bypass_charge", R.string.bypass_charge_tile_title, R.drawable.ic_bypass_charge),
    DANMAKU("danmaku", R.string.notification_danmaku, R.drawable.ic_danmaku);

    companion object {
        fun fromId(id: String): TileType? = values().find { it.id == id }
    }
}
