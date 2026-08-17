package com.android.gamespace.widget.tiles

import android.content.Context

enum class TileType(
    val id: String,
    val create: (Context) -> BaseTile
) {
    GAME_MODE("game_mode", { GameModeTile(it) }),
    FPS_INFO("fps_info", { FPSInfoTile(it) }),
    STAY_AWAKE("stay_awake", { StayAwakeTile(it) }),
    LOCK_GESTURE("lock_gesture", { LockGestureTile(it) }),
    WIFI("wifi", { WifiTile(it) }),
    BLUETOOTH("bluetooth", { BluetoothTile(it) }),
    MOBILE_DATA("mobile_data", { MobileDataTile(it) }),
    AIRPLANE_MODE("airplane_mode", { AirplaneModeTile(it) }),
    DND("dnd", { DndTile(it) });

    companion object {
        fun fromId(id: String) = entries.find { it.id == id }
    }
}
