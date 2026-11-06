package com.android.gamespace.widget.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class LocationTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val locationManager: LocationManager by lazy {
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.location_title)
        icon?.setImageResource(R.drawable.ic_location)
        refreshState()
        context.registerReceiver(stateReceiver, IntentFilter(LocationManager.MODE_CHANGED_ACTION))
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(stateReceiver) }
        super.onDetachedFromWindow()
    }

    private fun refreshState() {
        val enabled = locationManager.isLocationEnabled
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = !locationManager.isLocationEnabled
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Thread {
            val updated = runCatching {
                locationManager.setLocationEnabledForUser(target, UserHandle.CURRENT)
                true
            }.getOrDefault(false)
            if (!updated) {
                runCatching {
                    Settings.Secure.putInt(
                        context.contentResolver,
                        Settings.Secure.LOCATION_MODE,
                        if (target) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                        else Settings.Secure.LOCATION_MODE_OFF
                    )
                }
            }
        }.start()
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        return true
    }
}
