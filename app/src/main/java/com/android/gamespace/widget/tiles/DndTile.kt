package com.android.gamespace.widget.tiles

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class DndTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val notificationManager: NotificationManager? by lazy {
        context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.dnd_title)
        icon?.setImageResource(R.drawable.ic_dnd)
        refreshState()
        context.registerReceiver(
            stateReceiver,
            IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        runCatching { context.unregisterReceiver(stateReceiver) }
    }

    private fun refreshState() {
        val enabled = notificationManager?.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = notificationManager?.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL
        val newFilter = if (target) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        else NotificationManager.INTERRUPTION_FILTER_ALL
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Thread {
            runCatching { notificationManager?.setInterruptionFilter(newFilter) }
        }.start()
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_ZEN_MODE_SETTINGS)
        return true
    }
}
