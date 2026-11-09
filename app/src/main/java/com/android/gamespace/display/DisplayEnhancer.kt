package com.android.gamespace.display

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemProperties
import android.provider.Settings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import com.android.gamespace.R
import com.android.gamespace.data.AppSettings
import com.android.gamespace.utils.entryPointOf

/**
 * Game Display Enhanced: puts the colour look of the active [DisplayPreset] on the screen for as
 * long as a game runs, and takes it off again afterwards.
 *
 * It costs nothing while it is not asked to do anything. There is no service, no thread, no
 * observer and no polling: the screen is only touched when a game starts or ends, when a preset
 * is picked, and while a preset is being edited. Then it sends only what is different from what
 * it sent last, and the "Standard" preset sends nothing at all.
 *
 * Call it from the main thread.
 */
@Singleton
class DisplayEnhancer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettings,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val flinger = SurfaceFlingerColor()

    /** Reused for every matrix that is sent; the parcel copies it. */
    private val matrix = FloatArray(16)

    private val statePrefs by lazy {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
    }

    // ---- What has been put on the screen -------------------------------------------------

    /**
     * What this has set in SurfaceFlinger, which is also what it has to take back.
     *
     * [satSent] is the saturation in percent, [SAT_NONE] when SurfaceFlinger still has the value
     * the system gave it, or [SAT_UNKNOWN] when an earlier process changed it and did not get to
     * undo it. [matrixSent] and [matrixKey] are the same for the colour matrix.
     */
    private var satSent = SAT_NONE
    private var matrixSent = false
    private var matrixKey = 0

    private var stateLoaded = false
    private var persistedSat = false
    private var persistedMatrix = false

    private var sessionActive = false
    private var previewing: DisplayPreset? = null
    private var flushPending = false

    private val flush = Runnable {
        flushPending = false
        previewing?.let { apply(it, force = false) }
    }

    // ---- Presets -------------------------------------------------------------------------

    private var rawCustom: String? = null
    private var cached: List<DisplayPreset> = DisplayPreset.BUILT_IN

    /** The built-in presets, then the custom ones, in the order they were made. */
    fun presets(): List<DisplayPreset> {
        val raw = appSettings.displayCustomPresets
        if (raw != rawCustom) {
            rawCustom = raw
            cached = if (raw.isEmpty()) {
                DisplayPreset.BUILT_IN
            } else {
                DisplayPreset.BUILT_IN + DisplayPresetCodec.decode(raw)
            }
        }
        return cached
    }

    fun customPresets(): List<DisplayPreset> = presets().drop(DisplayPreset.BUILT_IN.size)

    val active: DisplayPreset
        get() {
            val id = appSettings.displayPresetId
            return presets().firstOrNull { it.id == id } ?: DisplayPreset.STANDARD
        }

    fun labelOf(preset: DisplayPreset): String = preset.name ?: context.getString(
        when (preset.id) {
            "vivid" -> R.string.display_preset_vivid
            "natural" -> R.string.display_preset_natural
            "saturated" -> R.string.display_preset_saturated
            "hdr" -> R.string.display_preset_hdr
            else -> R.string.display_preset_standard
        }
    )

    fun defaultName(): String =
        context.getString(R.string.display_preset_default_name, customPresets().size + 1)

    fun newId(): String {
        val taken = presets().mapTo(HashSet()) { it.id }
        var id: String
        var n = System.currentTimeMillis()
        do {
            id = DisplayPreset.CUSTOM_PREFIX + n.toString(36)
            n++
        } while (id in taken)
        return id
    }

    /** Makes [id] the preset of the games, and shows it right away if one is running. */
    fun select(id: String) {
        val preset = presets().firstOrNull { it.id == id } ?: return
        appSettings.displayPresetId = preset.id
        if (sessionActive && previewing == null) apply(preset, force = true)
    }

    /** The next one in the list, after the last the first; for a tile that is tapped. */
    fun selectNext(): DisplayPreset {
        val all = presets()
        // What is shown is the active preset, which is Standard (the first) if the id is unknown.
        val current = all.indexOfFirst { it.id == appSettings.displayPresetId }.coerceAtLeast(0)
        val next = all[(current + 1) % all.size]
        select(next.id)
        return next
    }

    /** False when there are already [MAX_CUSTOM] custom presets and this one would be a new one. */
    fun saveCustom(preset: DisplayPreset): Boolean {
        val custom = customPresets().toMutableList()
        val at = custom.indexOfFirst { it.id == preset.id }
        val saved = DisplayPreset.clamp(
            preset.copy(name = DisplayPreset.cleanName(preset.name.orEmpty()).ifEmpty { defaultName() })
        )
        if (at >= 0) {
            custom[at] = saved
        } else {
            if (custom.size >= MAX_CUSTOM) return false
            custom.add(saved)
        }
        appSettings.displayCustomPresets = DisplayPresetCodec.encode(custom)
        if (sessionActive && previewing == null && saved.id == appSettings.displayPresetId) {
            apply(saved, force = true)
        }
        return true
    }

    fun delete(id: String) {
        val custom = customPresets()
        if (custom.none { it.id == id }) return
        appSettings.displayCustomPresets = DisplayPresetCodec.encode(custom.filter { it.id != id })
        if (appSettings.displayPresetId == id) select(DisplayPreset.STANDARD_ID)
    }

    // ---- When a game runs ----------------------------------------------------------------

    fun startSession() {
        sessionActive = true
        cancelPreview()
        apply(active, force = true)
    }

    /** Takes the look off the screen. Safe to call when nothing has been put on it. */
    fun stopSession() {
        sessionActive = false
        cancelPreview()
        apply(DisplayPreset.STANDARD, force = false)
    }

    /**
     * Cleans up after a process that ended while it still had a look on the screen, which nothing
     * else would do before the next game. Does nothing in the normal case.
     */
    fun recover() {
        if (!sessionActive && previewing == null) apply(DisplayPreset.STANDARD, force = false)
    }

    // ---- While a preset is edited --------------------------------------------------------

    /**
     * Shows [preset] on the screen without choosing it. A slider that is dragged calls this for
     * every step, so the screen is only updated once in a while with the latest value.
     */
    fun preview(preset: DisplayPreset) {
        previewing = preset
        if (!flushPending) {
            flushPending = true
            handler.postDelayed(flush, PREVIEW_INTERVAL_MS)
        }
    }

    /** Back to what the screen was showing before [preview]. */
    fun endPreview() {
        if (previewing == null) return
        cancelPreview()
        apply(if (sessionActive) active else DisplayPreset.STANDARD, force = sessionActive)
    }

    private fun cancelPreview() {
        previewing = null
        if (flushPending) {
            flushPending = false
            handler.removeCallbacks(flush)
        }
    }

    // ---- The screen ----------------------------------------------------------------------

    /**
     * Brings the screen to [preset] and keeps track of what that took. With [force] everything
     * the preset needs is sent again, otherwise only what differs from what was sent.
     */
    private fun apply(preset: DisplayPreset, force: Boolean) {
        loadState()

        if (preset.saturation == 100) {
            if (satSent != SAT_NONE) {
                flinger.setSaturation(systemSaturation())
                satSent = SAT_NONE
            }
        } else if (force || satSent != preset.saturation) {
            flinger.setSaturation(preset.saturation / 100f)
            satSent = preset.saturation
        }

        // The colour matrix is shared with Night Light and the other tints, which are written
        // there as well. While one of them is on, whatever it has put there stays.
        val blocked = (preset.needsMatrix || matrixSent) && otherTintActive()
        if (preset.needsMatrix && !blocked) {
            if (force || !matrixSent || matrixKey != preset.matrixKey) {
                preset.writeMatrix(matrix)
                flinger.setColorMatrix(matrix)
            }
            matrixSent = true
            matrixKey = preset.matrixKey
        } else if (matrixSent) {
            // When something else is on now, it has already replaced ours.
            if (!blocked) flinger.setColorMatrix(null)
            matrixSent = false
        }

        saveState()
    }

    /** The saturation the system itself wants: 1.0, or what the colour mode "boosted" asks for. */
    private fun systemSaturation(): Float =
        SystemProperties.get(PROP_SATURATION, "1.0").toFloatOrNull()?.coerceIn(0f, 2f) ?: 1f

    private fun otherTintActive(): Boolean {
        val resolver = context.contentResolver
        return KEYS_TINT.any { Settings.Secure.getInt(resolver, it, 0) == 1 }
    }

    private fun loadState() {
        if (stateLoaded) return
        stateLoaded = true
        persistedSat = statePrefs.getBoolean(KEY_SAT, false)
        persistedMatrix = statePrefs.getBoolean(KEY_MATRIX, false)
        if (persistedSat) satSent = SAT_UNKNOWN
        matrixSent = persistedMatrix
    }

    /** Kept on disk so that a process that dies in the middle of a game can still undo it. */
    private fun saveState() {
        val sat = satSent != SAT_NONE
        if (sat == persistedSat && matrixSent == persistedMatrix) return
        persistedSat = sat
        persistedMatrix = matrixSent
        statePrefs.edit().putBoolean(KEY_SAT, sat).putBoolean(KEY_MATRIX, matrixSent).apply()
    }

    companion object {
        const val MAX_CUSTOM = 10

        private const val PREVIEW_INTERVAL_MS = 32L

        private const val SAT_NONE = -1
        private const val SAT_UNKNOWN = 0

        private const val STATE_PREFS = "display_enhancer_state"
        private const val KEY_SAT = "saturation_applied"
        private const val KEY_MATRIX = "matrix_applied"

        private const val PROP_SATURATION = "persist.sys.sf.color_saturation"

        /** Night Light, colour inversion, Reduce Bright Colors, display white balance. */
        private val KEYS_TINT = arrayOf(
            "night_display_activated",
            "accessibility_display_inversion_enabled",
            "reduce_bright_colors_activated",
            "display_white_balance_enabled",
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DisplayEnhancerEntryPoint {
    fun displayEnhancer(): DisplayEnhancer
}

fun Context.displayEnhancer(): DisplayEnhancer =
    entryPointOf<DisplayEnhancerEntryPoint>().displayEnhancer()
