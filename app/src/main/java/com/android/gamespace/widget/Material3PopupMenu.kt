package com.android.gamespace.widget

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.annotation.MenuRes
import com.android.gamespace.R

/**
 * Shows [menuRes] as a Material 3 popup menu anchored to [anchor].
 *
 * The popup is built here rather than left to the action bar's overflow, so its look does not
 * depend on how the host toolbar themes its own popups. It gets a rounded tonal surface and
 * Monet colours from [R.style.Widget_GameSpace_PopupMenu] and
 * [R.style.ThemeOverlay_GameSpace_PopupMenu].
 *
 * @param prepare called with the inflated menu before it is shown, to hide or disable entries.
 * @param onItemClick return true when the click was handled.
 */
fun showMaterial3PopupMenu(
    anchor: View,
    @MenuRes menuRes: Int,
    prepare: (Menu) -> Unit = {},
    onItemClick: (MenuItem) -> Boolean,
) {
    val themed = ContextThemeWrapper(anchor.context, R.style.ThemeOverlay_GameSpace_PopupMenu)
    val popup = PopupMenu(themed, anchor, Gravity.END, 0, R.style.Widget_GameSpace_PopupMenu)
    popup.menuInflater.inflate(menuRes, popup.menu)
    prepare(popup.menu)
    popup.setOnMenuItemClickListener { onItemClick(it) }
    popup.show()
}
