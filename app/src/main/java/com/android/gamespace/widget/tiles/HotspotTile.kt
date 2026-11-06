package com.android.gamespace.widget.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class HotspotTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val connectivityManager: ConnectivityManager by lazy {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            refreshState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.hotspot_title)
        icon?.setImageResource(R.drawable.ic_hotspot)
        refreshState()
        context.registerReceiver(stateReceiver, IntentFilter(ACTION_WIFI_AP_STATE_CHANGED))
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(stateReceiver) }
        super.onDetachedFromWindow()
    }

    private fun isHotspotEnabled(): Boolean = runCatching {
        val method = wifiManager.javaClass.getDeclaredMethod("getWifiApState")
        method.isAccessible = true
        val state = method.invoke(wifiManager) as? Int ?: 0
        state == WIFI_AP_STATE_ENABLED || state == WIFI_AP_STATE_ENABLING
    }.getOrDefault(false)

    private fun refreshState() {
        val enabled = isHotspotEnabled()
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = !isHotspotEnabled()
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Thread {
            val toggled = runCatching {
                if (target) {
                    val startMethod = wifiManager.javaClass.getMethod(
                        "startTetheredHotspot",
                        android.net.wifi.SoftApConfiguration::class.java
                    )
                    startMethod.invoke(wifiManager, null)
                } else {
                    val stopMethod = wifiManager.javaClass.getMethod("stopSoftAp")
                    stopMethod.invoke(wifiManager)
                }
                true
            }.getOrDefault(false)

            if (!toggled) {
                runCatching {
                    val stopMethod = connectivityManager.javaClass.getMethod("stopTethering", Int::class.javaPrimitiveType)
                    if (!target) stopMethod.invoke(connectivityManager, TETHERING_WIFI)
                }
            }
        }.start()
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_WIRELESS_SETTINGS)
        return true
    }

    private companion object {
        const val ACTION_WIFI_AP_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        const val WIFI_AP_STATE_ENABLING = 12
        const val WIFI_AP_STATE_ENABLED = 13
        const val TETHERING_WIFI = 0
    }
}
