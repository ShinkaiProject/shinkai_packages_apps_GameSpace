package com.android.gamespace.utils

import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

/**
 * Reads the battery, power and CPU figures stored next to every FPS sample.
 *
 * Battery temperature and power use public APIs. CPU usage (/proc/stat) and CPU temperature
 * (/sys/class/thermal) depend on what SELinux allows the app to read on the ROM; whatever
 * cannot be read is reported as NaN and simply left out of the statistics.
 *
 * Called twice a second while recording, so it avoids repeating work that cannot have changed:
 * the battery broadcast is sticky and only updated by the system every so often, and the CPU
 * temperature is refreshed once a second (see [CpuStatsReader]). Only the battery current and the
 * CPU load are read for every sample.
 *
 * Not thread safe: call [reset] and [read] from one thread at a time.
 */
class SystemStatsSampler(
    private val context: Context,
    private val cpu: CpuStatsReader = CpuStatsReader(),
) {

    class Reading(
        val batteryTempC: Float,
        val powerW: Float,
        val cpuUsagePct: Float,
        val cpuTempC: Float,
    )

    private val batteryManager by lazy { context.getSystemService(BatteryManager::class.java) }

    private var batteryTempC = Float.NaN
    private var voltageMv = 0
    private var batteryReadAt = NEVER

    /** Call right before recording starts: sets the CPU usage baseline and finds the CPU sensors. */
    fun reset() {
        cpu.reset()
        batteryReadAt = NEVER
    }

    /** @param now a steadily increasing clock in milliseconds, e.g. `SystemClock.elapsedRealtime()`. */
    fun read(now: Long): Reading {
        refreshBattery(now)
        return Reading(
            batteryTempC = batteryTempC,
            powerW = power(),
            cpuUsagePct = cpu.usagePct(),
            cpuTempC = cpu.tempC(now),
        )
    }

    private fun refreshBattery(now: Long) {
        if (batteryReadAt != NEVER && now - batteryReadAt < BATTERY_REFRESH_MS) return
        batteryReadAt = now

        val battery = context.registerReceiver(null, BATTERY_FILTER)
        val tenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        batteryTempC = if (tenths == null || tenths == Int.MIN_VALUE) Float.NaN else tenths / 10f
        voltageMv = battery?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
    }

    /** Battery power draw (or charge rate while plugged in) in watts. */
    private fun power(): Float = runCatching {
        val current = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val voltage = voltageMv
        if (current == null || current == Int.MIN_VALUE || voltage <= 0) return Float.NaN

        // The sign of the current differs between devices; the magnitude is what matters.
        // The property is specified in microamps but some kernels report milliamps.
        val rawMagnitude = abs(current.toDouble())
        val amps = if (rawMagnitude < 10_000) rawMagnitude / 1_000.0 else rawMagnitude / 1_000_000.0
        // Voltage is in millivolts, but some devices report microvolts.
        val volts = if (voltage > 10_000) voltage / 1_000_000.0 else voltage / 1_000.0
        (amps * volts).toFloat()
    }.getOrDefault(Float.NaN)

    private companion object {
        /** The sticky battery broadcast changes slowly; asking for it more often gains nothing. */
        const val BATTERY_REFRESH_MS = 2000L
        const val NEVER = Long.MIN_VALUE
        val BATTERY_FILTER = IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
    }
}
