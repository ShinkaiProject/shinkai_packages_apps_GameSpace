package com.android.gamespace.widget.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class BatterySaverTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val powerManager: PowerManager? by lazy {
        context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.battery_saver_title)
        icon?.setImageResource(R.drawable.ic_battery_saver)
        refreshState()
        context.registerReceiver(
            stateReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        )
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(stateReceiver) }
        super.onDetachedFromWindow()
    }

    private fun isPowerSaveEnabled(): Boolean =
        powerManager?.isPowerSaveMode == true ||
            Settings.Global.getInt(context.contentResolver, "low_power", 0) == 1

    private fun refreshState() {
        val enabled = isPowerSaveEnabled()
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = !isPowerSaveEnabled()
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Thread {
            val handled = runCatching { powerManager?.setPowerSaveModeEnabled(target) == true }.getOrDefault(false)
            if (!handled) {
                runCatching {
                    Settings.Global.putInt(context.contentResolver, "low_power", if (target) 1 else 0)
                }
            }
        }.start()
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        return true
    }
}
