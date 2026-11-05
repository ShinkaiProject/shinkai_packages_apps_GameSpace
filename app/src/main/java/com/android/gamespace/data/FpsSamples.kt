package com.android.gamespace.data

/**
 * The readings taken while recording, kept in primitive arrays: one slot per sample and no object
 * per sample, so a long session costs 28 bytes a sample and puts nothing on the garbage collector.
 * Metrics the device could not provide are NaN.
 *
 * Not thread safe. The arrays are exposed for reading; only the first [size] entries are valid.
 */
class FpsSamples(initialCapacity: Int = DEFAULT_CAPACITY) {

    var size = 0
        private set

    var elapsedMs = LongArray(initialCapacity.coerceAtLeast(1))
        private set
    var fps = FloatArray(elapsedMs.size)
        private set
    var batteryTempC = FloatArray(elapsedMs.size)
        private set
    var powerW = FloatArray(elapsedMs.size)
        private set
    var cpuUsagePct = FloatArray(elapsedMs.size)
        private set
    var cpuTempC = FloatArray(elapsedMs.size)
        private set

    fun isEmpty() = size == 0

    fun add(
        elapsedMs: Long,
        fps: Float,
        batteryTempC: Float = Float.NaN,
        powerW: Float = Float.NaN,
        cpuUsagePct: Float = Float.NaN,
        cpuTempC: Float = Float.NaN,
    ) {
        if (size == this.elapsedMs.size) grow()
        this.elapsedMs[size] = elapsedMs
        this.fps[size] = fps
        this.batteryTempC[size] = batteryTempC
        this.powerW[size] = powerW
        this.cpuUsagePct[size] = cpuUsagePct
        this.cpuTempC[size] = cpuTempC
        size++
    }

    private fun grow() {
        val capacity = elapsedMs.size + maxOf(elapsedMs.size / 2, MIN_GROWTH)
        elapsedMs = elapsedMs.copyOf(capacity)
        fps = fps.copyOf(capacity)
        batteryTempC = batteryTempC.copyOf(capacity)
        powerW = powerW.copyOf(capacity)
        cpuUsagePct = cpuUsagePct.copyOf(capacity)
        cpuTempC = cpuTempC.copyOf(capacity)
    }

    private companion object {
        const val DEFAULT_CAPACITY = 256
        const val MIN_GROWTH = 64
    }
}
