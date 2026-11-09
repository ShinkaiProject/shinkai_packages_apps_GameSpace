package com.android.gamespace.display

import android.content.Context
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.gamespace.R

/**
 * A row of the preset list: the check mark on the one that is active and, for a custom preset,
 * a button to edit it. Touching the row anywhere else chooses the preset.
 */
internal class PresetPreference(context: Context) : Preference(context) {

    var isActive = false
        set(value) {
            if (field == value) return
            field = value
            notifyChanged()
        }

    /** Null for a built-in preset, which cannot be edited. */
    var onEdit: (() -> Unit)? = null

    init {
        widgetLayoutResource = R.layout.preference_preset_widget
        isPersistent = false
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(R.id.preset_check)?.visibility =
            if (isActive) View.VISIBLE else View.INVISIBLE
        holder.findViewById(R.id.preset_edit)?.apply {
            val edit = onEdit
            visibility = if (edit != null) View.VISIBLE else View.GONE
            setOnClickListener(if (edit != null) View.OnClickListener { edit() } else null)
        }
    }
}
