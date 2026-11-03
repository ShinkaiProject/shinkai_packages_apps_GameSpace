package com.android.gamespace.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File
import kotlin.math.abs

/**
 * Reads the battery, power and CPU figures stored next to every FPS sample.
 *
 * Battery temperature and power use public APIs. CPU usage (/proc/stat) and CPU temperature
 * (/sys/class/thermal) depend on what SELinux allows the app to read on the ROM; whatever
 * cannot be read is reported as NaN and simply left out of the statistics.
 *
 * Not thread safe: call [reset] and [read] from one thread at a time.
 */
class SystemStatsSampler(private val context: Context) {

    class Reading(
        val batteryTempC: Float,
        val powerW: Float,
        val cpuUsagePct: Float,
        val cpuTempC: Float,
    )

    private val batteryManager by lazy { context.getSystemService(BatteryManager::class.java) }

    private var previousTotal = -1L
    private var previousIdle = -1L
    private var cpuTempZones: List<File> = emptyList()

    /** Call right before recording starts: sets the CPU usage baseline and finds the CPU sensors. */
    fun reset() {
        val times = readCpuTimes()
        previousTotal = times?.first ?: -1L
        previousIdle = times?.second ?: -1L
        cpuTempZones = findCpuTempZones()
    }

    fun read(): Reading {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return Reading(
            batteryTempC = batteryTemp(battery),
            powerW = power(battery),
            cpuUsagePct = cpuUsage(),
            cpuTempC = cpuTemp(),
        )
    }

    private fun batteryTemp(battery: Intent?): Float {
        val tenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (tenths == null || tenths == Int.MIN_VALUE) Float.NaN else tenths / 10f
    }

    /** Battery power draw (or charge rate while plugged in) in watts. */
    private fun power(battery: Intent?): Float = runCatching {
        val current = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val voltage = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (current == null || current == Int.MIN_VALUE || voltage <= 0) return Float.NaN

        // The sign of the current differs between devices; the magnitude is what matters.
        // The property is specified in microamps but some kernels report milliamps.
        val rawMagnitude = abs(current.toDouble())
        val amps = if (rawMagnitude < 10_000) rawMagnitude / 1_000.0 else rawMagnitude / 1_000_000.0
        // Voltage is in millivolts, but some devices report microvolts.
        val volts = if (voltage > 10_000) voltage / 1_000_000.0 else voltage / 1_000.0
        (amps * volts).toFloat()
    }.getOrDefault(Float.NaN)

    private fun cpuUsage(): Float {
        val (total, idle) = readCpuTimes() ?: return Float.NaN
        val totalDelta = total - previousTotal
        val idleDelta = idle - previousIdle
        val hadBaseline = previousTotal >= 0
        previousTotal = total
        previousIdle = idle
        if (!hadBaseline || totalDelta <= 0) return Float.NaN
        return ((totalDelta - idleDelta) * 100f / totalDelta).coerceIn(0f, 100f)
    }

    /** @return (total, idle) jiffies from the aggregate "cpu" line of /proc/stat. */
    private fun readCpuTimes(): Pair<Long, Long>? = runCatching {
        val line = File("/proc/stat").bufferedReader().use { it.readLine() } ?: return null
        if (!line.startsWith("cpu ")) return null
        val fields = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (fields.size < 5) return null
        // user nice system idle iowait irq softirq steal
        val total = fields.take(8).sum()
        val idle = fields[3] + fields[4]
        total to idle
    }.getOrNull()

    private fun findCpuTempZones(): List<File> = runCatching {
        File(THERMAL_DIR).listFiles { f -> f.name.startsWith("thermal_zone") }
            .orEmpty()
            .sortedBy { it.name }
            .filter { zone ->
                val type = runCatching { File(zone, "type").readText().trim().lowercase() }
                    .getOrNull() ?: return@filter false
                CPU_ZONE_HINTS.any { type.contains(it) }
            }
            .take(MAX_CPU_ZONES)
            .map { File(it, "temp") }
    }.getOrDefault(emptyList())

    /** Hottest CPU sensor in °C. */
    private fun cpuTemp(): Float {
        var hottest = Float.NaN
        for (file in cpuTempZones) {
            var value = runCatching { file.readText().trim().toFloat() }.getOrNull() ?: continue
            if (value > 1000f) value /= 1000f // millidegrees
            if (value <= 0f || value >= 150f) continue
            if (hottest.isNaN() || value > hottest) hottest = value
        }
        return hottest
    }

    companion object {
        private const val THERMAL_DIR = "/sys/class/thermal"
        private const val MAX_CPU_ZONES = 16
        private val CPU_ZONE_HINTS = listOf("cpu", "big", "little", "gold", "prime")
    }
}
