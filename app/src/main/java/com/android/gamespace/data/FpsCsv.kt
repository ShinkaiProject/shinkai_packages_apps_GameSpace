package com.android.gamespace.data

import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Reads and writes a recorded session as CSV, the format that is exported to Download/GameSpace
 * and kept for the statistics screen.
 *
 * Both directions work on bytes and primitive arrays, one chunk at a time: a session of 100 000
 * samples needs a few kilobytes of buffer instead of building a string of several megabytes, and
 * the numbers are formatted and parsed by hand because `String.format` and `split` are what made
 * saving and opening long sessions slow. The output is byte for byte what `String.format` and
 * `SimpleDateFormat` produce, so files written by earlier versions still load.
 */
object FpsCsv {

    const val HEADER = "timestamp,elapsed_s,fps,battery_temp_c,power_w,cpu_usage_pct,cpu_temp_c"

    private const val BUFFER_SIZE = 16 * 1024

    // ---- writing ------------------------------------------------------------------------------

    /** Writes [samples] to [out]. Metrics the device could not provide are left empty. */
    fun write(samples: FpsSamples, startWallMs: Long, out: OutputStream) {
        val w = AsciiWriter(out)
        w.put(HEADER)
        w.put('\n'.code)

        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        var secondCached = Long.MIN_VALUE
        var secondText = ByteArray(0)

        for (i in 0 until samples.size) {
            val wall = startWallMs + samples.elapsedMs[i]
            val second = Math.floorDiv(wall, 1000L)
            if (second != secondCached) {
                // The date and time only change once a second; samples come twice a second.
                secondCached = second
                secondText = timeFormat.format(Date(second * 1000L)).toByteArray(Charsets.US_ASCII)
            }
            w.put(secondText)
            w.put('.'.code)
            w.putPadded(Math.floorMod(wall, 1000L).toInt(), 3)
            w.put(','.code)
            w.putFixed(samples.elapsedMs[i] / 1000f, 1)
            w.put(','.code)
            w.putFixed(samples.fps[i], 1)
            w.put(','.code)
            w.putFixed(samples.batteryTempC[i], 1)
            w.put(','.code)
            w.putFixed(samples.powerW[i], 2)
            w.put(','.code)
            w.putFixed(samples.cpuUsagePct[i], 1)
            w.put(','.code)
            w.putFixed(samples.cpuTempC[i], 1)
            w.put('\n'.code)
        }
        w.flushBuffer()
    }

    /** Collects ASCII text in a buffer and hands it to the stream in large pieces. */
    private class AsciiWriter(private val out: OutputStream) {
        private val buf = ByteArray(BUFFER_SIZE)
        private var pos = 0

        fun flushBuffer() {
            if (pos > 0) {
                out.write(buf, 0, pos)
                pos = 0
            }
        }

        fun put(b: Int) {
            if (pos == buf.size) flushBuffer()
            buf[pos++] = b.toByte()
        }

        fun put(bytes: ByteArray) {
            for (b in bytes) put(b.toInt())
        }

        fun put(text: String) {
            for (c in text) put(c.code)
        }

        /** [value] (not negative) with leading zeros up to [width] digits. */
        fun putPadded(value: Int, width: Int) {
            var divisor = 1
            repeat(width - 1) { divisor *= 10 }
            while (divisor > 0) {
                put('0'.code + value / divisor % 10)
                divisor /= 10
            }
        }

        /** Same text as `String.format(Locale.US, "%.<digits>f", value)`; nothing for NaN. */
        fun putFixed(value: Float, digits: Int) {
            if (value.isNaN()) return
            if (value.isInfinite()) {
                put(if (value < 0f) "-Infinity" else "Infinity")
                return
            }
            val scale = POW10[digits]
            val scaled = Math.abs(value.toDouble()) * scale // exact: a float has only 24 bits
            if (scaled >= EXACT_LIMIT) {
                // Far outside anything a reading can be; let the formatter deal with it.
                put(String.format(Locale.US, "%.${digits}f", value))
                return
            }
            val rounded = Math.round(scaled) // half up, like Formatter
            if (java.lang.Float.floatToRawIntBits(value) < 0) put('-'.code) // also for -0.0
            val whole = rounded / scale.toLong()
            putLong(whole)
            if (digits > 0) {
                put('.'.code)
                putPadded((rounded % scale.toLong()).toInt(), digits)
            }
        }

        private fun putLong(value: Long) {
            if (value < 10) {
                put('0'.code + value.toInt())
                return
            }
            var divisor = 1L
            while (value / divisor >= 10) divisor *= 10
            var rest = value
            while (divisor > 0) {
                put('0'.code + (rest / divisor).toInt())
                rest %= divisor
                divisor /= 10
            }
        }
    }

    private val POW10 = doubleArrayOf(1.0, 10.0, 100.0, 1000.0)

    /** Below this the scaled value is a whole number of units we can count exactly in a Long. */
    private const val EXACT_LIMIT = 1e15

    // ---- reading ------------------------------------------------------------------------------

    /**
     * Inverse of [write]. Malformed lines are skipped. [sizeHint] (the length of the data in bytes,
     * if known) only sizes the arrays.
     */
    fun read(input: InputStream, sizeHint: Long = 0L): FpsSamples {
        val samples = FpsSamples((sizeHint / BYTES_PER_LINE).coerceIn(256L, MAX_PRESIZE).toInt())
        val parser = LineParser(samples)

        val chunk = ByteArray(BUFFER_SIZE)
        var line = ByteArray(256)
        var length = 0
        var overlong = false
        var headerSeen = false

        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            for (i in 0 until read) {
                val b = chunk[i]
                if (b != NEWLINE) {
                    if (length == line.size) {
                        if (line.size >= MAX_LINE) {
                            overlong = true
                            continue
                        }
                        line = line.copyOf(line.size * 2)
                    }
                    if (!overlong) line[length++] = b
                    continue
                }
                if (headerSeen) {
                    if (!overlong) parser.parse(line, length)
                } else {
                    headerSeen = true // the first line is the header, whatever it says
                }
                length = 0
                overlong = false
            }
        }
        if (headerSeen && !overlong && length > 0) parser.parse(line, length)
        return samples
    }

    private const val NEWLINE = '\n'.code.toByte()
    private const val CARRIAGE_RETURN = '\r'.code.toByte()
    private const val COMMA = ','.code.toByte()
    private const val BYTES_PER_LINE = 48L
    private const val MAX_PRESIZE = 200_000L
    private const val MAX_LINE = 4096
    private const val FIELDS = 7

    /** Turns lines into samples; keeps its scratch space so a line costs no allocation. */
    private class LineParser(private val samples: FpsSamples) {
        private val starts = IntArray(FIELDS)
        private val ends = IntArray(FIELDS)

        /** One line: timestamp, elapsed seconds, fps, then the optional metrics. */
        fun parse(line: ByteArray, rawLength: Int) {
            var length = rawLength
            if (length > 0 && line[length - 1] == CARRIAGE_RETURN) length-- // CRLF files

            // Field i is line[starts[i] until ends[i]].
            var fields = 0
            var start = 0
            for (i in 0..length) {
                if (i == length || line[i] == COMMA) {
                    if (fields < FIELDS) {
                        starts[fields] = start
                        ends[fields] = i
                    }
                    fields++
                    start = i + 1
                }
            }
            if (fields < 3) return

            val elapsed = parseFloat(line, starts[1], ends[1])
            val fps = parseFloat(line, starts[2], ends[2])
            if (elapsed.isNaN() || fps.isNaN()) return

            samples.add(
                elapsedMs = (elapsed * 1000f).roundToLong(),
                fps = fps,
                batteryTempC = optional(line, fields, 3),
                powerW = optional(line, fields, 4),
                cpuUsagePct = optional(line, fields, 5),
                cpuTempC = optional(line, fields, 6),
            )
        }

        private fun optional(line: ByteArray, fields: Int, i: Int) =
            if (i < fields) parseFloat(line, starts[i], ends[i]) else Float.NaN
    }

    private val POW10_FLOAT = floatArrayOf(1f, 10f, 100f, 1_000f, 10_000f, 100_000f, 1_000_000f, 10_000_000f)

    /**
     * Parses a number; NaN when it is empty or not a number.
     *
     * Plain decimals of up to 7 digits (what [write] produces) are exact in a float together with
     * their power of ten, and one correctly rounded division then gives the same float that
     * `toFloat()` would. Anything else takes the slow way.
     */
    private fun parseFloat(b: ByteArray, start: Int, end: Int): Float {
        if (start >= end) return Float.NaN // a metric the device could not provide
        var i = start
        var negative = false
        if (i < end && b[i] == '-'.code.toByte()) {
            negative = true
            i++
        }
        var mantissa = 0
        var digits = 0
        var fractionDigits = 0
        var seenDot = false
        while (i < end) {
            val c = b[i].toInt()
            if (c >= '0'.code && c <= '9'.code) {
                if (++digits > 7) return parseSlowly(b, start, end)
                mantissa = mantissa * 10 + (c - '0'.code)
                if (seenDot) fractionDigits++
            } else if (c == '.'.code && !seenDot) {
                seenDot = true
            } else {
                return parseSlowly(b, start, end)
            }
            i++
        }
        if (digits == 0) return parseSlowly(b, start, end)
        val value = mantissa.toFloat() / POW10_FLOAT[fractionDigits]
        return if (negative) -value else value
    }

    private fun parseSlowly(b: ByteArray, start: Int, end: Int): Float =
        String(b, start, end - start, Charsets.ISO_8859_1).toFloatOrNull() ?: Float.NaN
}
