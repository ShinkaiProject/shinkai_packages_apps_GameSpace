package com.android.gamespace.utils

import java.io.File
import java.io.FileInputStream

/**
 * Reads the CPU load (/proc/stat) and the CPU temperature (/sys/class/thermal).
 *
 * It is made to be cheap when called twice a second for hours:
 *  - files are read into one reusable buffer and parsed by hand, instead of through readers and
 *    regular expressions that each leave tens of kilobytes of garbage behind;
 *  - the temperature only changes slowly, so the sensors are read at most once per
 *    [TEMP_REFRESH_MS] and the last value is returned in between;
 *  - whatever SELinux does not let us read is given up on after a few tries instead of failing
 *    (and throwing) on every sample.
 *
 * What cannot be read is reported as NaN. Not thread safe: call from one thread at a time.
 */
class CpuStatsReader(
    private val statPath: String = "/proc/stat",
    private val thermalDir: String = "/sys/class/thermal",
) {
    private val buffer = ByteArray(BUFFER_SIZE)

    private var previousTotal = -1L
    private var previousIdle = -1L
    private var statFailures = 0

    private var zones: Array<String> = emptyArray()
    private var zoneFailures = IntArray(0)
    private var lastTemp = Float.NaN
    private var lastTempAt = NEVER

    /** Call right before recording starts: sets the load baseline and finds the CPU sensors. */
    fun reset() {
        statFailures = 0
        val ok = readCpuTimes()
        previousTotal = if (ok) times[0] else -1L
        previousIdle = if (ok) times[1] else -1L

        zones = findCpuTempZones()
        zoneFailures = IntArray(zones.size)
        lastTemp = Float.NaN
        lastTempAt = NEVER
    }

    /** Share of the CPU that was busy since the previous call, in percent. */
    fun usagePct(): Float {
        if (statFailures >= GIVE_UP_AFTER) return Float.NaN
        if (!readCpuTimes()) {
            statFailures++
            return Float.NaN
        }
        statFailures = 0

        val total = times[0]
        val idle = times[1]
        val totalDelta = total - previousTotal
        val idleDelta = idle - previousIdle
        val hadBaseline = previousTotal >= 0
        previousTotal = total
        previousIdle = idle
        if (!hadBaseline || totalDelta <= 0) return Float.NaN
        return ((totalDelta - idleDelta) * 100f / totalDelta).coerceIn(0f, 100f)
    }

    /**
     * Hottest CPU sensor in °C. [now] is any steadily increasing clock in milliseconds; the sensors
     * are only read when [TEMP_REFRESH_MS] have passed since the last time.
     */
    fun tempC(now: Long): Float {
        if (zones.isEmpty()) return Float.NaN
        if (lastTempAt != NEVER && now - lastTempAt < TEMP_REFRESH_MS) return lastTemp
        lastTempAt = now

        var hottest = Float.NaN
        for (i in zones.indices) {
            if (zoneFailures[i] >= GIVE_UP_AFTER) continue
            val milli = readLong(zones[i])
            if (milli == null) {
                zoneFailures[i]++
                continue
            }
            zoneFailures[i] = 0
            var value = milli.toFloat()
            if (value > 1000f) value /= 1000f // millidegrees
            if (value <= 0f || value >= 150f) continue
            if (hottest.isNaN() || value > hottest) hottest = value
        }
        lastTemp = hottest
        return hottest
    }

    // The aggregate "cpu" line of /proc/stat: user nice system idle iowait irq softirq steal.
    private val times = LongArray(2) // total, idle

    /** Fills [times] from /proc/stat. @return false if it cannot be read or understood. */
    private fun readCpuTimes(): Boolean {
        val length = readInto(statPath)
        return length > 0 && parseCpuLine(buffer, length, times)
    }

    private fun findCpuTempZones(): Array<String> = runCatching {
        File(thermalDir).listFiles { f -> f.name.startsWith("thermal_zone") }
            .orEmpty()
            .sortedBy { it.name }
            .filter { zone ->
                val length = readInto(File(zone, "type").path)
                if (length <= 0) return@filter false
                val type = String(buffer, 0, length, Charsets.ISO_8859_1).trim().lowercase()
                CPU_ZONE_HINTS.any { type.contains(it) }
            }
            .take(MAX_CPU_ZONES)
            .map { File(it, "temp").path }
            .toTypedArray()
    }.getOrDefault(emptyArray())

    /** Reads the start of [path] into [buffer]. @return the byte count, or -1 on failure. */
    private fun readInto(path: String): Int = try {
        FileInputStream(path).use { it.read(buffer) }
    } catch (e: Exception) {
        -1
    }

    /** The first line of [path] as a whole number, or null if it cannot be read. */
    private fun readLong(path: String): Long? {
        val length = readInto(path)
        return if (length > 0) parseLong(buffer, length) else null
    }

    companion object {
        const val TEMP_REFRESH_MS = 1000L

        private const val BUFFER_SIZE = 512
        private const val MAX_CPU_ZONES = 16
        private const val GIVE_UP_AFTER = 3
        private const val NEVER = Long.MIN_VALUE
        private val CPU_ZONE_HINTS = listOf("cpu", "big", "little", "gold", "prime")

        /**
         * Parses the aggregate line of /proc/stat into [out] (total and idle jiffies).
         * @return false if [bytes] does not start with that line or it has too few numbers.
         */
        fun parseCpuLine(bytes: ByteArray, length: Int, out: LongArray): Boolean {
            // "cpu " followed by the numbers; "cpu0 ..." is a single core, not the aggregate.
            if (length < 5 || bytes[0] != 'c'.code.toByte() || bytes[1] != 'p'.code.toByte() ||
                bytes[2] != 'u'.code.toByte() || bytes[3] != ' '.code.toByte()
            ) return false

            var count = 0
            var total = 0L
            var idle = 0L
            var i = 4
            while (i < length && count < 8) {
                while (i < length && bytes[i] == ' '.code.toByte()) i++
                if (i >= length || bytes[i] == '\n'.code.toByte()) break
                var value = 0L
                var digits = 0
                while (i < length && bytes[i] >= '0'.code.toByte() && bytes[i] <= '9'.code.toByte()) {
                    value = value * 10 + (bytes[i] - '0'.code.toByte())
                    digits++
                    i++
                }
                if (digits == 0) return false
                if (count == 3 || count == 4) idle += value
                total += value
                count++
            }
            if (count < 5) return false
            out[0] = total
            out[1] = idle
            return true
        }

        /** A whole number, optionally negative, followed by whitespace or nothing; else null. */
        fun parseLong(bytes: ByteArray, length: Int): Long? {
            var i = 0
            while (i < length && bytes[i] <= ' '.code.toByte()) i++
            var negative = false
            if (i < length && bytes[i] == '-'.code.toByte()) {
                negative = true
                i++
            }
            var value = 0L
            var digits = 0
            while (i < length && bytes[i] >= '0'.code.toByte() && bytes[i] <= '9'.code.toByte()) {
                if (digits++ >= 18) return null
                value = value * 10 + (bytes[i] - '0'.code.toByte())
                i++
            }
            if (digits == 0) return null
            while (i < length) {
                if (bytes[i] > ' '.code.toByte()) return null
                i++
            }
            return if (negative) -value else value
        }
    }
}
