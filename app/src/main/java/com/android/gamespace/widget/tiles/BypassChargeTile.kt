package com.android.gamespace.widget.tiles

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R
import com.android.gamespace.utils.di.ServiceViewEntryPoint
import com.android.gamespace.utils.entryPointOf

class BypassChargeTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val screenUtils by lazy {
        context.entryPointOf<ServiceViewEntryPoint>().screenUtils()
    }

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.bypass_charge_tile_title)
        icon?.setImageResource(R.drawable.ic_bypass_charge)
        refreshState()
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(KEY_BYPASS_CHARGE),
            false,
            observer
        )
    }

    override fun onDetachedFromWindow() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        super.onDetachedFromWindow()
    }

    private fun isBypassEnabled(): Boolean =
        Settings.System.getIntForUser(
            context.contentResolver,
            KEY_BYPASS_CHARGE,
            0,
            UserHandle.USER_CURRENT
        ) == 1

    private fun refreshState() {
        val enabled = isBypassEnabled()
        summary?.text = context.getString(if (enabled) R.string.state_enabled else R.string.state_disabled)
        isSelected = enabled
    }

    override fun onClick(v: View?) {
        super.onClick(v)
        val target = !isBypassEnabled()
        summary?.text = context.getString(if (target) R.string.state_enabled else R.string.state_disabled)
        isSelected = target
        Settings.System.putIntForUser(
            context.contentResolver,
            KEY_BYPASS_CHARGE,
            if (target) 1 else 0,
            UserHandle.USER_CURRENT
        )
        runCatching { screenUtils.bypassCharge(target) }
    }

    private companion object {
        const val KEY_BYPASS_CHARGE = "bypass_charge_enabled"
    }
}
