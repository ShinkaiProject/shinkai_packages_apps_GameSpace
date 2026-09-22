package com.android.gamespace.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

class GapSliderTrackDrawable(context: Context) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val thumbWidthPx = dp(4f)
    private val gapPx = dp(3f)
    private val outerCornerPx = dp(8f)
    private val innerCornerPx = dp(2f)

    private val activeColor = resolveThemeColor(context, android.R.attr.colorAccent, fallback = Color.WHITE)
    private val inactiveColor = withAlpha(
        resolveThemeColor(context, android.R.attr.textColorPrimaryInverse, fallback = Color.WHITE),
        alpha = 56, // ~22%,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private fun resolveThemeColor(context: Context, attr: Int, fallback: Int): Int {
        val typedArray = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColor(0, fallback)
        } finally {
            typedArray.recycle()
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha shl 24)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val fraction = level / 10000f
        val trackLeft = b.left.toFloat()
        val trackRight = b.right.toFloat()
        val top = b.top.toFloat()
        val bottom = b.bottom.toFloat()
        val maxCorner = (bottom - top) / 2f
        val outerRadius = outerCornerPx.coerceAtMost(maxCorner)
        val innerRadius = innerCornerPx.coerceAtMost(maxCorner)

        val thumbCenterX = trackLeft + fraction * (trackRight - trackLeft)
        val gapStart = thumbCenterX - thumbWidthPx / 2f - gapPx
        val gapEnd = thumbCenterX + thumbWidthPx / 2f + gapPx

        if (gapStart > trackLeft) {
            drawSegment(canvas, trackLeft, top, gapStart, bottom, outerRadius, innerRadius, activeColor)
        }
        if (gapEnd < trackRight) {
            drawSegment(canvas, gapEnd, top, trackRight, bottom, innerRadius, outerRadius, inactiveColor)
        }
    }

    private fun drawSegment(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        leftRadius: Float, rightRadius: Float,
        color: Int,
    ) {
        paint.color = color
        rect.set(left, top, right, bottom)
        path.reset()
        path.addRoundRect(
            rect,
            floatArrayOf(
                leftRadius, leftRadius,   // top-left
                rightRadius, rightRadius, // top-right
                rightRadius, rightRadius, // bottom-right
                leftRadius, leftRadius,   // bottom-left
            ),
            Path.Direction.CW,
        )
        canvas.drawPath(path, paint)
    }

    override fun onLevelChange(level: Int): Boolean = true

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun getAlpha(): Int = paint.alpha
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
