package com.android.gamespace.display

/**
 * One look for the screen, as two numbers that both mean "no change" at their middle value.
 *
 * - [saturation], in percent: 100 leaves the colours alone, more makes them stronger.
 * - [warmth]: 0 leaves the white point alone, positive is warmer, negative is cooler.
 *
 * Nothing in here knows about Android, so everything it does can be checked on its own.
 *
 * [name] is null for the presets that come with the app; their label is a string resource, so it
 * follows the language. Custom presets carry the name that was typed.
 */
data class DisplayPreset(
    val id: String,
    val name: String?,
    val saturation: Int = 100,
    val warmth: Int = 0,
) {
    val builtIn: Boolean get() = name == null

    /** Warmth is a colour matrix. Saturation is not: it is sent on its own. */
    val needsMatrix: Boolean get() = warmth != 0

    /** Does nothing to the screen. */
    val isIdentity: Boolean get() = saturation == 100 && !needsMatrix

    /** Equal for presets that give the same matrix, so that one is not sent twice. */
    val matrixKey: Int get() = warmth

    /**
     * Writes the matrix for [warmth] into [out], in the layout that SurfaceFlinger reads: 16
     * floats, column by column, the last row being 0, 0, 0, 1.
     *
     * It is a scale of the three channels and nothing else, and no channel is ever scaled up:
     * the warm side lowers blue and a little green, the cool side lowers red and a little green.
     * That is on purpose. The display driver of a phone can have a colour engine that is only a
     * 3x3 matrix with a fixed range. A matrix with an offset (which is what a contrast change
     * needs) or with a gain above 1 turned the whole screen white on a MediaTek panel, while this
     * kind of matrix, the kind that Night Light is, was shown correctly. So this stays within
     * what Night Light does.
     */
    fun writeMatrix(out: FloatArray) {
        out.fill(0f)
        val k = warmth / WARMTH_MAX.toFloat()
        val red: Float
        val green: Float
        val blue: Float
        if (k >= 0f) {
            red = 1f
            green = 1f - 0.12f * k
            blue = 1f - 0.30f * k
        } else {
            red = 1f + 0.25f * k
            green = 1f + 0.08f * k
            blue = 1f
        }
        out[0] = red
        out[5] = green
        out[10] = blue
        out[15] = 1f
    }

    companion object {
        const val STANDARD_ID = "standard"
        const val CUSTOM_PREFIX = "custom_"

        const val SATURATION_MIN = 50
        const val SATURATION_MAX = 200
        const val WARMTH_MIN = -50
        const val WARMTH_MAX = 50

        const val MAX_NAME = 24

        val STANDARD = DisplayPreset(STANDARD_ID, null)

        /** In the order they are shown, and the order the tile goes through them. */
        val BUILT_IN: List<DisplayPreset> = listOf(
            STANDARD,
            DisplayPreset("vivid", null, saturation = 125),
            DisplayPreset("natural", null, saturation = 90, warmth = 4),
            DisplayPreset("saturated", null, saturation = 155),
            // A look that imitates HDR: strong colour and a slightly cooler white. It does not
            // turn HDR output on.
            DisplayPreset("hdr", null, saturation = 135, warmth = -6),
        )

        /** What is allowed in a name: it is stored on one line, between separators. */
        fun cleanName(raw: String): String =
            raw.map { if (it == '|' || it == '\n' || it == '\r' || it == '\t') ' ' else it }
                .joinToString("")
                .trim()
                .take(MAX_NAME)
                .trim()

        fun clamp(preset: DisplayPreset) = preset.copy(
            saturation = preset.saturation.coerceIn(SATURATION_MIN, SATURATION_MAX),
            warmth = preset.warmth.coerceIn(WARMTH_MIN, WARMTH_MAX),
        )
    }
}

/**
 * The custom presets, as one string: a line each, `id|name|saturation|warmth`. A few short lines
 * do not need a JSON library, and reading them back is a split and two parses.
 */
object DisplayPresetCodec {

    fun encode(presets: List<DisplayPreset>): String = buildString {
        presets.forEach {
            if (isNotEmpty()) append('\n')
            append(it.id).append('|')
                .append(DisplayPreset.cleanName(it.name.orEmpty())).append('|')
                .append(it.saturation).append('|')
                .append(it.warmth)
        }
    }

    /**
     * Whatever cannot be read is left out instead of failing: the string comes from preferences
     * that anything could have written to. Numbers are brought back into range, and an id that
     * belongs to a built-in preset, or that came before, is dropped.
     *
     * An earlier version of this app also kept a contrast, between the saturation and the
     * warmth. Those lines (five fields) are still read, without it, so presets that were made
     * with that version are not lost.
     */
    fun decode(raw: String): List<DisplayPreset> {
        if (raw.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        DisplayPreset.BUILT_IN.forEach { seen.add(it.id) }
        val out = ArrayList<DisplayPreset>()
        for (line in raw.split('\n')) {
            val parts = line.split('|')
            if (parts.size != 4 && parts.size != 5) continue
            val id = parts[0]
            if (!id.startsWith(DisplayPreset.CUSTOM_PREFIX) || !seen.add(id)) continue
            val saturation = parts[2].toIntOrNull() ?: continue
            val warmth = parts[parts.size - 1].toIntOrNull() ?: continue
            val name = DisplayPreset.cleanName(parts[1]).ifEmpty { "-" }
            out.add(DisplayPreset.clamp(DisplayPreset(id, name, saturation, warmth)))
        }
        return out
    }
}
