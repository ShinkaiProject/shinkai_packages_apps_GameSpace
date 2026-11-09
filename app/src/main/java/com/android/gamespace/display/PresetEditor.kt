package com.android.gamespace.display

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.android.gamespace.R

/**
 * The dialog that makes a custom preset, or changes one. While it is open the sliders show their
 * result on the screen; closing it, however it is closed, puts the screen back.
 *
 * [existing] is the preset that is changed, null for a new one. [seed] gives the starting values:
 * the preset itself, or for a new one the one that is active, so a new preset can start from
 * Vivid or from any other.
 */
internal class PresetEditor(
    private val context: Context,
    private val enhancer: DisplayEnhancer,
    private val existing: DisplayPreset?,
    seed: DisplayPreset,
    private val onChanged: () -> Unit,
) {
    private var saturation = seed.saturation
    private var warmth = seed.warmth

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_display_preset, null)
        val name = view.requireViewById<EditText>(R.id.preset_name).apply {
            filters = arrayOf(InputFilter.LengthFilter(DisplayPreset.MAX_NAME))
            setText(existing?.name.orEmpty())
        }

        slider(
            view, R.id.saturation_seek, R.id.saturation_value,
            DisplayPreset.SATURATION_MIN, DisplayPreset.SATURATION_MAX, saturation,
            format = { context.getString(R.string.display_value_percent, it) },
        ) { saturation = it }
        slider(
            view, R.id.warmth_seek, R.id.warmth_value,
            DisplayPreset.WARMTH_MIN, DisplayPreset.WARMTH_MAX, warmth,
            format = { if (it > 0) "+$it" else it.toString() },
        ) { warmth = it }

        val builder = AlertDialog.Builder(context)
            .setTitle(if (existing == null) R.string.display_preset_new else R.string.display_preset_edit)
            .setView(view)
            .setPositiveButton(R.string.display_preset_save) { _, _ -> save(name.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.display_preset_delete) { _, _ -> confirmDelete(existing) }
        }
        builder.create().apply {
            setOnDismissListener { enhancer.endPreview() }
            show()
        }
    }

    /** Sets up one slider: its range, how its value is written, and what to do when it moves. */
    private fun slider(
        root: View,
        seekId: Int,
        valueId: Int,
        min: Int,
        max: Int,
        initial: Int,
        format: (Int) -> String,
        onValue: (Int) -> Unit,
    ) {
        val seek = root.requireViewById<SeekBar>(seekId)
        val label = root.requireViewById<TextView>(valueId)
        // The handle and the size come from the slider's style; the track is drawn in code.
        seek.progressDrawable = PresetSliderDrawable(context)
        seek.max = max - min
        seek.progress = initial.coerceIn(min, max) - min
        label.text = format(initial.coerceIn(min, max))
        // Set last: the listener must not run for the starting position.
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = min + progress
                label.text = format(value)
                onValue(value)
                enhancer.preview(current())
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    private fun current() = DisplayPreset(
        id = existing?.id.orEmpty(),
        name = existing?.name ?: "",
        saturation = saturation,
        warmth = warmth,
    )

    private fun save(typedName: String) {
        val name = DisplayPreset.cleanName(typedName).ifEmpty { enhancer.defaultName() }
        val preset = DisplayPreset(
            id = existing?.id ?: enhancer.newId(),
            name = name,
            saturation = saturation,
            warmth = warmth,
        )
        if (!enhancer.saveCustom(preset)) {
            Toast.makeText(
                context,
                context.getString(R.string.display_preset_limit, DisplayEnhancer.MAX_CUSTOM),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        // A new preset is what it was made for: the one to use.
        if (existing == null) enhancer.select(preset.id)
        onChanged()
    }

    private fun confirmDelete(preset: DisplayPreset) {
        AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.display_preset_delete_message, preset.name))
            .setPositiveButton(R.string.display_preset_delete) { _, _ ->
                enhancer.delete(preset.id)
                onChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
