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
    WIFI("wifi", { WifiTile(it) });

    companion object {
        fun fromId(id: String) = entries.find { it.id == id }
    }
}
