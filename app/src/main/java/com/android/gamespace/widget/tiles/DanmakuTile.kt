package com.android.gamespace.widget.tiles

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf

/** Turns the Danmaku notifications of the running game on or off. */
class DanmakuTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val gameSession by lazy { context.entryPointOf<ServiceViewEntryPoint>().gameSession() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.notification_danmaku)
        icon?.setImageResource(R.drawable.ic_danmaku)
        refreshState()
    }

    private fun refreshState() {
        val enabled = appSettings.danmakuNotification
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        gameSession.setDanmakuNotification(!appSettings.danmakuNotification)
        refreshState()
    }
}
