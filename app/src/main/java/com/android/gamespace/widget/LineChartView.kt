package com.android.gamespace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Minimal filled line chart: dashed grid, value labels on the Y axis and elapsed time on the
 * X axis. Both axes start at zero.
 */
class LineChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var xSeconds = FloatArray(0)
    private var values = FloatArray(0)
    private var lineColor = Color.GREEN
    private var minAxisMax = 0f

    private val density = resources.displayMetrics.density
    private val labelColor: Int = context
        .obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        .let { a -> a.getColor(0, Color.GRAY).also { a.recycle() } }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        alpha = 60
        style = Paint.Style.STROKE
        strokeWidth = density
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = labelColor
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics
        )
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePath = Path()
    private val fillPath = Path()

    /**
     * @param xSeconds elapsed time of every point, ascending
     * @param values value of every point (same length as [xSeconds])
     * @param minAxisMax the Y axis is at least this tall, e.g. 100 for percentages
     */
    fun setData(xSeconds: FloatArray, values: FloatArray, color: Int, minAxisMax: Float = 0f) {
        require(xSeconds.size == values.size) { "x and y must have the same length" }
        this.xSeconds = xSeconds.copyOf()
        this.values = values.copyOf()
        this.lineColor = color
        this.minAxisMax = minAxisMax
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val dataMax = values.maxOrNull() ?: 0f
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

        val left = paddingLeft + labelWidth + 8 * density
        val right = width - paddingRight - 12 * density
        val top = paddingTop + 8 * density
        val bottom = height - paddingBottom - labelHeight - 8 * density
        if (right <= left || bottom <= top) return

        fun px(seconds: Float) = left + (seconds / xUnit) / xMax * (right - left)
        fun py(value: Float) = bottom - value / yMax * (bottom - top)

        // Y grid and labels
        for ((value, label) in yLabels) {
            val y = py(value)
            canvas.drawLine(left, y, right, y, gridPaint)
            canvas.drawText(
                label, left - 6 * density - labelPaint.measureText(label),
                y + labelHeight / 3f, labelPaint
            )
        }

        // X grid and labels
        val suffix = if (inMinutes) "m" else "s"
        for (t in ticks(xMax, xStep)) {
            val x = px(t * xUnit)
            canvas.drawLine(x, top, x, bottom, gridPaint)
            val label = formatTick(t, xStep) + suffix
            val w = labelPaint.measureText(label)
            canvas.drawText(
                label, (x - w / 2f).coerceIn(0f, width - w),
                bottom + 6 * density + labelHeight, labelPaint
            )
        }

        when (values.size) {
            0 -> return
            1 -> {
                dotPaint.color = lineColor
                canvas.drawCircle(px(xSeconds[0]), py(values[0]), 3 * density, dotPaint)
                return
            }
        }

        linePath.reset()
        fillPath.reset()
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

        fillPaint.shader = LinearGradient(
            0f, top, 0f, bottom,
            withAlpha(lineColor, 120), withAlpha(lineColor, 10), Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)
        linePaint.color = lineColor
        canvas.drawPath(linePath, linePaint)
    }

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

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
}
