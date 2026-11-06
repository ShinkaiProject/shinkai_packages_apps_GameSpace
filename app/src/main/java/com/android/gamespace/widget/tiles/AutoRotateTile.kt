package com.android.gamespace.widget.tiles

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class AutoRotateTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.auto_rotate_title)
        icon?.setImageResource(R.drawable.ic_auto_rotate)
        refreshState()
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            observer
        )
    }

    override fun onDetachedFromWindow() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        super.onDetachedFromWindow()
    }

    private fun isAutoRotateEnabled(): Boolean =
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0
        ) == 1

    private fun refreshState() {
        val enabled = isAutoRotateEnabled()
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = !isAutoRotateEnabled()
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (target) 1 else 0
        )
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_DISPLAY_SETTINGS)
        return true
    }
}
