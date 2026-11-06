package com.android.gamespace.widget.tiles

import android.content.ContentResolver
import android.content.Context
import android.content.SyncStatusObserver
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class AutoSyncTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private var statusHandle: Any? = null
    private val syncObserver = SyncStatusObserver {
        post { refreshState() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.auto_sync_title)
        icon?.setImageResource(R.drawable.ic_sync)
        refreshState()
        statusHandle = ContentResolver.addStatusChangeListener(
            ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
            syncObserver
        )
    }

    override fun onDetachedFromWindow() {
        statusHandle?.let { ContentResolver.removeStatusChangeListener(it) }
        statusHandle = null
        super.onDetachedFromWindow()
    }

    private fun refreshState() {
        val enabled = ContentResolver.getMasterSyncAutomatically()
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = !ContentResolver.getMasterSyncAutomatically()
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Thread {
            ContentResolver.setMasterSyncAutomatically(target)
        }.start()
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_SYNC_SETTINGS)
        return true
    }
}
