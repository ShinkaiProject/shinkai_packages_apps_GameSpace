package com.android.gamespace.ui.stats

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** More points than there are pixels across add nothing to the picture. */
const val CHART_MAX_POINTS = 1200

/**
 * A line chart of [series] against time, drawn on a canvas: dashed grid, labels on both axes
 * (seconds, or minutes for long sessions) and the line over a soft fill.
 *
 * @param minAxisMax the Y axis is at least this tall, e.g. 100 for percentages
 */
@Composable
fun LineChart(
    series: ChartDecimator.Series,
    color: Color,
    minAxisMax: Float,
    modifier: Modifier = Modifier,
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        drawLineChart(series, color, labelColor, minAxisMax)
    }
}

private fun DrawScope.drawLineChart(
    series: ChartDecimator.Series,
    lineColor: Color,
    labelColor: Color,
    minAxisMax: Float,
) {
    val xSeconds = series.x
    val values = series.y
    val dataMax = values.maxOrNull() ?: 0f
    val density = this.density

    val gridColor = labelColor.copy(alpha = 60f / 255f)
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor.toArgb()
        textSize = 10.sp.toPx()
    }

    val (yMax, yStep) = niceAxis(max(dataMax, minAxisMax))
    // Time axis: seconds, or minutes for long sessions.
    val duration = xSeconds.lastOrNull() ?: 0f
    val inMinutes = duration > 120f
    val xUnit = if (inMinutes) 60f else 1f
    val xMax = max(duration / xUnit, 1f)
    var xStep = niceAxis(xMax).second
    if (!inMinutes) xStep = max(xStep, 1f)

    val yLabels = ticks(yMax, yStep).map { it to formatTick(it, yStep) }
    val labelWidth = yLabels.maxOf { labelPaint.measureText(it.second) }
    val labelHeight = labelPaint.fontMetrics.let { it.descent - it.ascent }

    val left = labelWidth + 8 * density
    val right = size.width - 12 * density
    val top = 8 * density
    val bottom = size.height - labelHeight - 8 * density
    if (right <= left || bottom <= top) return

    fun px(seconds: Float) = left + (seconds / xUnit) / xMax * (right - left)
    fun py(value: Float) = bottom - value / yMax * (bottom - top)

    val dash = PathEffect.dashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
    val native = drawContext.canvas.nativeCanvas

    // Y grid and labels
    for ((value, label) in yLabels) {
        val y = py(value)
        drawLine(gridColor, Offset(left, y), Offset(right, y), strokeWidth = density, pathEffect = dash)
        native.drawText(
            label, left - 6 * density - labelPaint.measureText(label),
            y + labelHeight / 3f, labelPaint
        )
    }
    // X grid and labels
    val suffix = if (inMinutes) "m" else "s"
    for (t in ticks(xMax, xStep)) {
        val x = px(t * xUnit)
        drawLine(gridColor, Offset(x, top), Offset(x, bottom), strokeWidth = density, pathEffect = dash)
        val label = formatTick(t, xStep) + suffix
        val w = labelPaint.measureText(label)
        native.drawText(
            label, (x - w / 2f).coerceIn(0f, size.width - w),
            bottom + 6 * density + labelHeight, labelPaint
        )
    }

    when (values.size) {
        0 -> return
        1 -> {
            drawCircle(lineColor, 3 * density, Offset(px(xSeconds[0]), py(values[0])))
            return
        }
    }

    val linePath = Path()
    val fillPath = Path()
    for (i in values.indices) {
        val x = px(xSeconds[i])
        val y = py(values[i])
        if (i == 0) {
            linePath.moveTo(x, y)
            fillPath.moveTo(x, bottom)
            fillPath.lineTo(x, y)
        } else {
            linePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }
    fillPath.lineTo(px(xSeconds.last()), bottom)
    fillPath.close()

    drawPath(
        fillPath,
        Brush.verticalGradient(
            colors = listOf(lineColor.copy(alpha = 120f / 255f), lineColor.copy(alpha = 10f / 255f)),
            startY = top,
            endY = bottom,
        )
    )
    drawPath(linePath, lineColor, style = Stroke(width = 1.5f * density, join = StrokeJoin.Round))
}

/** Tick values 0, step, 2*step ... up to and including [axisMax]. */
private fun ticks(axisMax: Float, step: Float): List<Float> {
    val count = (axisMax / step + 1e-4f).toInt()
    return (0..count).map { it * step }
}

private fun formatTick(value: Float, step: Float) =
    if (step >= 1f) value.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.1f", value)

/** @return (axis maximum, tick step) with a round step giving about [targetTicks] ticks. */
private fun niceAxis(max: Float, targetTicks: Int = 5): Pair<Float, Float> {
    if (max <= 0f) return 1f to 0.25f
    val raw = max / targetTicks
    val magnitude = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
    val normalized = raw / magnitude
    val step = magnitude * when {
        normalized <= 1f -> 1f
        normalized <= 2f -> 2f
        normalized <= 5f -> 5f
        else -> 10f
    }
    return ceil(max / step - 1e-4f) * step to step
}
