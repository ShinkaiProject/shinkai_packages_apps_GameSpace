package com.android.gamespace.ui.stats

/**
 * Thins out a line chart's points. A chart is only a few hundred to a thousand pixels wide, so a
 * session of 100 000 samples would make the view build and draw paths with a hundred times more
 * segments than it can show.
 */
object ChartDecimator {

    class Series(val x: FloatArray, val y: FloatArray)

    /**
     * Reduces the first [count] points of [x] and [y] to at most [maxPoints] points.
     *
     * The first and the last point are kept. Between them the points are split into equal stretches
     * and each stretch keeps its lowest and its highest point, so peaks and dips survive and x stays
     * ascending. At or below [maxPoints] the points are returned unchanged. The result is always a
     * new pair of arrays, never [x] or [y] themselves.
     */
    fun decimate(x: FloatArray, y: FloatArray, count: Int, maxPoints: Int): Series {
        if (count <= maxPoints || maxPoints < MIN_POINTS) {
            return Series(x.copyOf(count), y.copyOf(count))
        }

        val interior = count - 2
        val buckets = (maxPoints - 2) / 2
        val outX = FloatArray(2 + 2 * buckets)
        val outY = FloatArray(outX.size)
        var out = 0

        outX[out] = x[0]
        outY[out++] = y[0]

        for (bucket in 0 until buckets) {
            val from = 1 + (bucket.toLong() * interior / buckets).toInt()
            val to = 1 + ((bucket + 1).toLong() * interior / buckets).toInt()
            if (from >= to) continue

            var lowest = from
            var highest = from
            for (i in from + 1 until to) {
                if (y[i] < y[lowest]) lowest = i
                if (y[i] > y[highest]) highest = i
            }
            val first = minOf(lowest, highest)
            val second = maxOf(lowest, highest)
            outX[out] = x[first]
            outY[out++] = y[first]
            if (second != first) {
                outX[out] = x[second]
                outY[out++] = y[second]
            }
        }

        outX[out] = x[count - 1]
        outY[out++] = y[count - 1]
        return Series(outX.copyOf(out), outY.copyOf(out))
    }

    private const val MIN_POINTS = 4
}
