package com.android.gamespace.widget.tiles

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.View
import com.android.gamespace.R
import com.android.gamespace.display.DisplayEnhancerActivity
import com.android.gamespace.display.displayEnhancer

/**
 * Game Display Enhanced. A tap goes to the next preset (the last one is followed by the first,
 * Standard), a long press opens the screen where the presets are made.
 *
 * It does not listen for anything: what it shows is read when it is attached and after a tap, and
 * the panel builds its tiles again every time it is shown.
 */
class DisplayEnhancerTile @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : BaseTile(context, attrs) {

    private val enhancer by lazy { context.displayEnhancer() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        title?.text = context.getString(R.string.display_enhancer_tile_title)
        icon?.setImageResource(R.drawable.ic_display_enhanced)
        render()
    }

    private fun render() {
        val preset = enhancer.active
        summary?.text = enhancer.labelOf(preset)
        isSelected = !preset.isIdentity
    }

    // The base class would flip isSelected, which here follows the preset.
    override fun onClick(v: View?) {
        enhancer.selectNext()
        render()
    }

    override fun onLongClick(v: View?): Boolean {
        runCatching {
            context.startActivity(
                Intent(context, DisplayEnhancerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return true
    }
}
