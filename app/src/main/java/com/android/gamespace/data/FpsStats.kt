package com.android.gamespace.data

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One reading taken while recording. Metrics the device could not provide are NaN.
 */
class FpsSample(
    val elapsedMs: Long,
    val fps: Float,
    val batteryTempC: Float = Float.NaN,
    val powerW: Float = Float.NaN,
    val cpuUsagePct: Float = Float.NaN,
    val cpuTempC: Float = Float.NaN,
)

class Range(val min: Float, val avg: Float, val max: Float) {
    companion object {
        /** @return null when there is no finite value to summarize. */
        fun of(values: List<Float>): Range? {
            val finite = values.filter { !it.isNaN() && !it.isInfinite() }
            if (finite.isEmpty()) return null
            return Range(
                min = finite.minOrNull() ?: return null,
                avg = finite.average().toFloat(),
                max = finite.maxOrNull() ?: return null,
            )
        }
    }
}

/**
 * Statistics of a recorded session.
 *
 * SurfaceFlinger reports FPS at most every 500 ms, and each report is the average over the frames
 * it presented in its recent history (roughly the last half to one second). So min and max are
 * those averaged values and a stutter shorter than that window is smoothed out. The "x% low"
 * values are the mean of the lowest x% of the reports (at least one), not of individual frames.
 * Frame time is derived from the reported FPS (1000 / fps).
 */
class FpsStats(
    val sampleCount: Int,
    val durationMs: Long,
    val fps: Range,
    val low1: Float,
    val low01: Float,
    val low5: Float,
    val fpsStdDev: Float,
    val frameTime: Range,
    val frameTimeP95: Float,
    val frameTimeP99: Float,
    val batteryTemp: Range?,
    val power: Range?,
    val cpuUsage: Range?,
    val cpuTemp: Range?,
) {
    /** Highest temperature seen on any available sensor. */
    val maxTemp: Float?
        get() = listOfNotNull(cpuTemp?.max, batteryTemp?.max).maxOrNull()

    companion object {
        fun of(samples: List<FpsSample>): FpsStats? {
            if (samples.isEmpty()) return null

            val fpsValues = samples.map { it.fps }
            val fpsSorted = fpsValues.sorted()
            val fpsRange = Range.of(fpsValues) ?: return null

            val frameTimes = fpsValues.filter { it > 0f }.map { 1000f / it }.sorted()
            val frameTimeRange = Range.of(frameTimes) ?: Range(0f, 0f, 0f)

            val mean = fpsRange.avg
            val variance = fpsValues.fold(0.0) { acc, v ->
                val d = v - mean
                acc + d * d
            } / fpsValues.size

            return FpsStats(
                sampleCount = samples.size,
                durationMs = samples.maxOf { it.elapsedMs },
                fps = fpsRange,
                low1 = lowAverage(fpsSorted, 0.01),
                low01 = lowAverage(fpsSorted, 0.001),
                low5 = lowAverage(fpsSorted, 0.05),
                fpsStdDev = sqrt(variance).toFloat(),
                frameTime = frameTimeRange,
                frameTimeP95 = percentile(frameTimes, 95.0),
                frameTimeP99 = percentile(frameTimes, 99.0),
                batteryTemp = Range.of(samples.map { it.batteryTempC }),
                power = Range.of(samples.map { it.powerW }),
                cpuUsage = Range.of(samples.map { it.cpuUsagePct }),
                cpuTemp = Range.of(samples.map { it.cpuTempC }),
            )
        }

        /** Mean of the lowest [fraction] of [sortedAscending] (at least one value). */
        fun lowAverage(sortedAscending: List<Float>, fraction: Double): Float {
            if (sortedAscending.isEmpty()) return 0f
            // The epsilon keeps e.g. 700 * 0.01 (= 7.000000000000001) from rounding up to 8.
            val count = max(1, ceil(sortedAscending.size * fraction - 1e-9).toInt())
            return sortedAscending.take(count).average().toFloat()
        }

        /** Nearest-rank percentile of [sortedAscending]; 0 when empty. */
        fun percentile(sortedAscending: List<Float>, percent: Double): Float {
            if (sortedAscending.isEmpty()) return 0f
            val rank = ceil(percent / 100.0 * sortedAscending.size - 1e-9).toInt()
            return sortedAscending[(rank - 1).coerceIn(0, sortedAscending.lastIndex)]
        }
    }
}
