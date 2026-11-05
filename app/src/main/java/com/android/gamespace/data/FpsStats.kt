package com.android.gamespace.data

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

class Range(val min: Float, val avg: Float, val max: Float) {
    companion object {
        /**
         * Minimum, mean and maximum of the finite values among the first [size] of [values].
         * @return null when there is no finite value to summarize.
         */
        fun of(values: FloatArray, size: Int = values.size): Range? {
            var count = 0
            var sum = 0.0
            var min = 0f
            var max = 0f
            for (i in 0 until size) {
                val v = values[i]
                if (v.isNaN() || v.isInfinite()) continue
                if (count == 0) {
                    min = v
                    max = v
                } else {
                    min = minOf(min, v)
                    max = maxOf(max, v)
                }
                sum += v
                count++
            }
            if (count == 0) return null
            return Range(min, (sum / count).toFloat(), max)
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
 *
 * Everything is computed from primitive arrays in one sorted copy of the FPS values, so the work
 * and the memory it needs stay small even for a session of 100 000 samples.
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
        fun of(samples: FpsSamples): FpsStats? {
            val n = samples.size
            if (n == 0) return null

            val fps = samples.fps
            val fpsRange = Range.of(fps, n) ?: return null

            // Ascending; NaN, if there is any, sorts after everything else.
            val sorted = fps.copyOf(n).also { it.sort() }

            val mean = fpsRange.avg
            var squares = 0.0
            for (i in 0 until n) {
                val d = fps[i] - mean
                squares += d * d
            }
            val variance = squares / n

            // Frame time is 1000 / fps, so the ascending frame times are the positive FPS values
            // taken from the highest down. That gives them sorted without a second sort.
            var end = n
            while (end > 0 && sorted[end - 1].isNaN()) end--
            var start = end
            while (start > 0 && sorted[start - 1] > 0f) start--
            val frameTimeCount = end - start
            fun frameTime(rank: Int) = 1000f / sorted[end - 1 - rank]

            return FpsStats(
                sampleCount = n,
                durationMs = maxElapsed(samples),
                fps = fpsRange,
                low1 = lowAverage(sorted, n, 0.01),
                low01 = lowAverage(sorted, n, 0.001),
                low5 = lowAverage(sorted, n, 0.05),
                fpsStdDev = sqrt(variance).toFloat(),
                frameTime = frameTimeRange(frameTimeCount, ::frameTime),
                frameTimeP95 = if (frameTimeCount == 0) 0f else frameTime(percentileRank(95.0, frameTimeCount)),
                frameTimeP99 = if (frameTimeCount == 0) 0f else frameTime(percentileRank(99.0, frameTimeCount)),
                batteryTemp = Range.of(samples.batteryTempC, n),
                power = Range.of(samples.powerW, n),
                cpuUsage = Range.of(samples.cpuUsagePct, n),
                cpuTemp = Range.of(samples.cpuTempC, n),
            )
        }

        private fun maxElapsed(samples: FpsSamples): Long {
            var max = samples.elapsedMs[0]
            for (i in 1 until samples.size) if (samples.elapsedMs[i] > max) max = samples.elapsedMs[i]
            return max
        }

        /** Range of the finite frame times; all zero when there are none. */
        private fun frameTimeRange(count: Int, frameTime: (Int) -> Float): Range {
            var finite = 0
            var sum = 0.0
            var min = 0f
            var max = 0f
            for (rank in 0 until count) {
                val v = frameTime(rank)
                if (v.isNaN() || v.isInfinite()) continue
                if (finite == 0) {
                    min = v
                    max = v
                } else {
                    min = minOf(min, v)
                    max = maxOf(max, v)
                }
                sum += v
                finite++
            }
            return if (finite == 0) Range(0f, 0f, 0f) else Range(min, (sum / finite).toFloat(), max)
        }

        /**
         * Mean of the lowest [fraction] of the first [size] values of [sortedAscending] (at least
         * one value).
         */
        fun lowAverage(sortedAscending: FloatArray, size: Int, fraction: Double): Float {
            if (size == 0) return 0f
            // The epsilon keeps e.g. 700 * 0.01 (= 7.000000000000001) from rounding up to 8.
            val count = max(1, ceil(size * fraction - 1e-9).toInt())
            var sum = 0.0
            for (i in 0 until count) sum += sortedAscending[i]
            return (sum / count).toFloat()
        }

        /** Zero-based index of the nearest-rank percentile in a sorted list of [size] values. */
        private fun percentileRank(percent: Double, size: Int): Int {
            val rank = ceil(percent / 100.0 * size - 1e-9).toInt()
            return (rank - 1).coerceIn(0, size - 1)
        }
    }
}
