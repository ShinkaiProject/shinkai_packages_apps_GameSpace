package com.android.gamespace.widget.tiles

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class NightLightTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.night_light_title)
        icon?.setImageResource(R.drawable.ic_night_light)
        refreshState()
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(KEY_NIGHT_DISPLAY_ACTIVATED),
            false,
            observer
        )
    }

    override fun onDetachedFromWindow() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        super.onDetachedFromWindow()
    }

    private fun isNightLightEnabled(): Boolean =
        Settings.Secure.getInt(context.contentResolver, KEY_NIGHT_DISPLAY_ACTIVATED, 0) == 1

    private fun refreshState() {
        val enabled = isNightLightEnabled()
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = !isNightLightEnabled()
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Settings.Secure.putInt(
            context.contentResolver,
            KEY_NIGHT_DISPLAY_ACTIVATED,
            if (target) 1 else 0
        )
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_NIGHT_DISPLAY_SETTINGS)
        return true
    }

    private companion object {
        const val KEY_NIGHT_DISPLAY_ACTIVATED = "night_display_activated"
    }
}
