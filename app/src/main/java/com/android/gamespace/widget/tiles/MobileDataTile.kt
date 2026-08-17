package com.android.gamespace.widget.tiles

import android.content.Context
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class MobileDataTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val telephonyManager by lazy {
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.mobile_data_title)
        icon?.setImageResource(R.drawable.ic_mobile_4_4_bar)
        refreshState()
    }

    private fun refreshState() {
        val enabled = telephonyManager.isDataEnabled
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = !telephonyManager.isDataEnabled
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Thread { telephonyManager.isDataEnabled = target }.start()
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
        return true
    }
}
