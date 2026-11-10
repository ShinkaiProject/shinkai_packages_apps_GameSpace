package com.android.gamespace.display

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceScreen
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import com.android.gamespace.R

/**
 * Game Display Enhanced: the presets that come with the app, the custom ones, and a way to make
 * more. Choosing a preset is a tap on its row; it is what games start with, and what the panel
 * tile begins from.
 */
class DisplayEnhancerFragment : SettingsBasePreferenceFragment() {

    private val enhancer by lazy { requireContext().displayEnhancer() }
    private val rows = LinkedHashMap<String, PresetPreference>()
    private lateinit var screen: PreferenceScreen

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        screen = preferenceManager.createPreferenceScreen(requireContext())
        setPreferenceScreen(screen)
        rebuild()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The note is a card of its own. Without this it sits right against the last custom preset.
        listView.addItemDecoration(
            GapBeforeDecoration(
                NOTE_KEY,
                requireContext().resources.getDimensionPixelSize(R.dimen.display_enhancer_note_gap)
            )
        )
    }

    override fun onStart() {
        super.onStart()
        // A look left on the screen by a process that is gone would stay until the next game.
        enhancer.recover()
    }

    override fun onStop() {
        enhancer.endPreview()
        super.onStop()
    }

    private fun rebuild() {
        val context = requireContext()
        screen.removeAll()
        rows.clear()

        val presets = enhancer.presets()
        val activeId = enhancer.active.id

        val builtIn = PreferenceCategory(context).apply {
            title = getString(R.string.display_presets_builtin)
            isIconSpaceReserved = false
        }
        screen.addPreference(builtIn)
        presets.filter { it.builtIn }.forEach { builtIn.addPreference(row(it, activeId)) }

        val custom = PreferenceCategory(context).apply {
            title = getString(R.string.display_presets_custom)
            isIconSpaceReserved = false
        }
        screen.addPreference(custom)
        presets.filter { !it.builtIn }.forEach { custom.addPreference(row(it, activeId)) }
        custom.addPreference(Preference(context).apply {
            isPersistent = false
            setIcon(R.drawable.ic_add)
            title = getString(R.string.display_preset_create)
            summary = getString(R.string.display_preset_create_summary)
            setOnPreferenceClickListener {
                create()
                true
            }
        })

        screen.addPreference(Preference(context).apply {
            key = NOTE_KEY
            isPersistent = false
            isSelectable = false
            isIconSpaceReserved = false
            summary = getString(R.string.display_enhancer_note)
        })
    }

    private fun row(preset: DisplayPreset, activeId: String) = PresetPreference(requireContext()).apply {
        title = enhancer.labelOf(preset)
        summary = describe(preset)
        isActive = preset.id == activeId
        if (!preset.builtIn) onEdit = { PresetEditor(requireContext(), enhancer, preset, preset, ::rebuild).show() }
        setOnPreferenceClickListener {
            choose(preset.id)
            true
        }
        rows[preset.id] = this
    }

    private fun describe(preset: DisplayPreset): String {
        if (preset.id == DisplayPreset.STANDARD_ID) return getString(R.string.display_preset_standard_summary)
        val warmth = if (preset.warmth > 0) "+${preset.warmth}" else preset.warmth.toString()
        return getString(
            R.string.display_preset_values,
            preset.saturation, warmth
        )
    }

    private fun choose(id: String) {
        enhancer.select(id)
        rows.forEach { (rowId, pref) -> pref.isActive = rowId == id }
    }

    private fun create() {
        if (enhancer.customPresets().size >= DisplayEnhancer.MAX_CUSTOM) {
            Toast.makeText(
                requireContext(),
                getString(R.string.display_preset_limit, DisplayEnhancer.MAX_CUSTOM),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        PresetEditor(requireContext(), enhancer, null, enhancer.active, ::rebuild).show()
    }

    private companion object {
        /** Tells the list which row is the note, so that it can be given space above it. */
        const val NOTE_KEY = "display_enhancer_note"
    }
}
