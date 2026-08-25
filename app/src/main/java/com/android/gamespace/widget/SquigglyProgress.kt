package com.android.gamespace.widget

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.sin

class SquigglyProgressDrawable : Drawable() {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        color = 0x4DFFFFFF
    }

    private val path = Path()

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    var animateWave: Boolean = true
        set(value) {
            field = value
            invalidateSelf()
        }

    private var phase = 0f

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val width = bounds.width().toFloat()
        val centerY = bounds.centerY().toFloat()

        if (width <= 0) return

        val activeWidth = width * progress

        if (activeWidth > 0) {
            path.reset()
            
            val waveLength = 64f 
            val waveHeight = 5f
            val rampDistance = 32f

            path.moveTo(0f, centerY)
            var x = 0f
            while (x <= activeWidth) {
                val startDamping = (x / rampDistance).coerceIn(0f, 1f)
                val endDamping = ((activeWidth - x) / rampDistance).coerceIn(0f, 1f)
                
                val currentAmplitude = waveHeight * minOf(startDamping, endDamping)

                val y = if (animateWave) {
                    centerY + sin((x - phase) / waveLength * 2 * Math.PI).toFloat() * currentAmplitude
                } else {
                    centerY
                }
                path.lineTo(x, y)
                x += 2f
            }
            canvas.drawPath(path, wavePaint)
        }

        if (activeWidth < width) {
            canvas.drawLine(activeWidth, centerY, width, centerY, trackPaint)
        }

        if (animateWave) {
            phase += 0.35f
            invalidateSelf()
        }
    }

    fun setWaveColor(color: Int) {
        wavePaint.color = color
        trackPaint.color = (color and 0x00FFFFFF) or 0x4D000000
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        wavePaint.alpha = alpha
        trackPaint.alpha = alpha / 3
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        wavePaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
