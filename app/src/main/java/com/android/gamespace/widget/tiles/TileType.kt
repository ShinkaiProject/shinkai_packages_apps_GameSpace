package com.android.gamespace.widget.tiles

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.gamespace.R

enum class TileType(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int,
    val create: (Context) -> BaseTile
) {
    GAME_MODE("game_mode", R.string.game_mode_title, R.drawable.ic_speed, { GameModeTile(it) }),
    FPS_INFO("fps_info", R.string.fps_Info_title, R.drawable.ic_fps, { FPSInfoTile(it) }),
    FPS_RECORD("fps_record", R.string.fps_record_title, R.drawable.ic_fps_record, { FpsRecordTile(it) }),
    STAY_AWAKE("stay_awake", R.string.stay_awake_title, R.drawable.ic_awake, { StayAwakeTile(it) }),
    LOCK_GESTURE("lock_gesture", R.string.lock_gesture_title, R.drawable.ic_gesture, { LockGestureTile(it) }),
    WIFI("wifi", R.string.wifi_title, R.drawable.ic_wifi, { WifiTile(it) }),
    BLUETOOTH("bluetooth", R.string.bluetooth_title, R.drawable.ic_bluetooth, { BluetoothTile(it) }),
    MOBILE_DATA("mobile_data", R.string.mobile_data_title, R.drawable.ic_mobile_4_4_bar, { MobileDataTile(it) }),
    AIRPLANE_MODE("airplane_mode", R.string.airplane_mode_title, R.drawable.ic_airplane, { AirplaneModeTile(it) }),
    DND("dnd", R.string.dnd_title, R.drawable.ic_dnd, { DndTile(it) }),
    AUTO_ROTATE("auto_rotate", R.string.auto_rotate_title, R.drawable.ic_auto_rotate, { AutoRotateTile(it) }),
    BATTERY_SAVER("battery_saver", R.string.battery_saver_title, R.drawable.ic_battery_saver, { BatterySaverTile(it) }),
    HOTSPOT("hotspot", R.string.hotspot_title, R.drawable.ic_hotspot, { HotspotTile(it) }),
    LOCATION("location", R.string.location_title, R.drawable.ic_location, { LocationTile(it) }),
    PRIVATE_DNS("private_dns", R.string.private_dns_title, R.drawable.ic_dns, { PrivateDnsTile(it) }),
    FLASHLIGHT("flashlight", R.string.flashlight_title, R.drawable.ic_flashlight, { FlashlightTile(it) }),
    NIGHT_LIGHT("night_light", R.string.night_light_title, R.drawable.ic_night_light, { NightLightTile(it) }),
    ADAPTIVE_BRIGHTNESS("adaptive_brightness", R.string.adaptive_brightness_title, R.drawable.ic_adaptive_brightness, { AdaptiveBrightnessTile(it) }),
    AUTO_SYNC("auto_sync", R.string.auto_sync_title, R.drawable.ic_sync, { AutoSyncTile(it) }),
    BYPASS_CHARGE("bypass_charge", R.string.bypass_charge_tile_title, R.drawable.ic_bypass_charge, { BypassChargeTile(it) });

    companion object {
        fun fromId(id: String) = entries.find { it.id == id }
    }
}
