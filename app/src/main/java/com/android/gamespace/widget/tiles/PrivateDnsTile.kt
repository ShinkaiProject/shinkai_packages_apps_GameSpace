package com.android.gamespace.widget.tiles

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R

class PrivateDnsTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshState()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.private_dns_title)
        icon?.setImageResource(R.drawable.ic_dns)
        refreshState()
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(KEY_PRIVATE_DNS_MODE),
            false,
            observer
        )
    }

    override fun onDetachedFromWindow() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        super.onDetachedFromWindow()
    }

    private fun getDnsMode(): String =
        Settings.Global.getString(context.contentResolver, KEY_PRIVATE_DNS_MODE) ?: MODE_OPPORTUNISTIC

    private fun getDnsSpecifier(): String =
        Settings.Global.getString(context.contentResolver, KEY_PRIVATE_DNS_SPECIFIER).orEmpty()

    private fun refreshState() {
        when (getDnsMode()) {
            MODE_OFF -> {
                summary?.text = context.getString(R.string.private_dns_off)
                isSelected = false
            }
            MODE_HOSTNAME -> {
                val host = getDnsSpecifier()
                summary?.text = if (host.isNotBlank()) host else context.getString(R.string.private_dns_Strict)
                isSelected = true
            }
            else -> {
                summary?.text = context.getString(R.string.private_dns_auto)
                isSelected = true
            }
        }
    }

    override fun onClick(v: View?) {
        val current = getDnsMode()
        val hasHost = getDnsSpecifier().isNotBlank()
        val next = when (current) {
            MODE_OFF -> MODE_OPPORTUNISTIC
            MODE_OPPORTUNISTIC -> if (hasHost) MODE_HOSTNAME else MODE_OFF
            else -> MODE_OFF
        }
        Settings.Global.putString(context.contentResolver, KEY_PRIVATE_DNS_MODE, next)
        refreshState()
    }

    override fun onLongClick(v: View?): Boolean {
        openSettings(Settings.ACTION_WIRELESS_SETTINGS)
        return true
    }

    private companion object {
        const val KEY_PRIVATE_DNS_MODE = "private_dns_mode"
        const val KEY_PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
        const val MODE_OFF = "off"
        const val MODE_OPPORTUNISTIC = "opportunistic"
        const val MODE_HOSTNAME = "hostname"
    }
}
