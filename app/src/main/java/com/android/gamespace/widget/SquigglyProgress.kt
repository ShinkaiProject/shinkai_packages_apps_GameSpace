package com.android.gamespace.widget

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.SystemClock

class SquigglyProgressDrawable : Drawable() {

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
            val coerced = value.coerceIn(0f, 1f)
            if (field == coerced) return
            field = coerced
            invalidateSelf()
        }

    var animateWave: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            lastFrameTimeMs = -1L
            invalidateSelf()
        }

    private var phase = 0f
    private var amplitudeFraction = 1f
    private var lastFrameTimeMs = -1L

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (changed) {
            lastFrameTimeMs = -1L
            if (visible && (animateWave || amplitudeFraction > 0f)) {
                invalidateSelf()
            }
        }
        return changed
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val width = bounds.width().toFloat()
        val centerY = bounds.centerY().toFloat()

        if (width <= 0f) return

        val now = SystemClock.uptimeMillis()
        val dtSec = if (lastFrameTimeMs > 0L) {
            ((now - lastFrameTimeMs).coerceIn(1L, 100L)) / 1000f
        } else {
            0.016f
        }

        val targetAmp = if (animateWave) 1f else 0f
        if (amplitudeFraction != targetAmp) {
            val step = dtSec / AMPLITUDE_TRANSITION_SEC
            amplitudeFraction = if (amplitudeFraction < targetAmp) {
                (amplitudeFraction + step).coerceAtMost(1f)
            } else {
                (amplitudeFraction - step).coerceAtLeast(0f)
            }
        }

        if (animateWave || amplitudeFraction > 0.005f) {
            phase = (phase + dtSec * PHASE_SPEED_PX_PER_SEC) % WAVE_LENGTH
            lastFrameTimeMs = now
        } else {
            lastFrameTimeMs = -1L
        }

        val activeWidth = width * progress

        if (activeWidth > 0f) {
            if (amplitudeFraction <= 0.005f) {
                canvas.drawLine(0f, centerY, activeWidth, centerY, wavePaint)
            } else {
                path.reset()
                val halfWave = WAVE_LENGTH / 2f
                val baseAmp = WAVE_HEIGHT * amplitudeFraction

                // Start slightly before 0 so the scrolling wave enters seamlessly from the left
                var segStart = -WAVE_LENGTH + (phase % WAVE_LENGTH)
                var sign = 1f

                canvas.save()
                canvas.clipRect(0f, bounds.top.toFloat(), activeWidth, bounds.bottom.toFloat())

                path.moveTo(segStart, centerY)
                while (segStart < activeWidth) {
                    val segEnd = segStart + halfWave
                    val midX = (segStart + segEnd) * 0.5f

                    // Smooth edge damping near 0 and near the thumb (activeWidth)
                    val startDamp = (midX / RAMP_DISTANCE).coerceIn(0f, 1f)
                    val endDamp = ((activeWidth - midX) / RAMP_DISTANCE).coerceIn(0f, 1f)
                    val amp = baseAmp * minOf(startDamp, endDamp) * sign

                    // Cubic Bezier approximation of a half-sine arch (only ~8 segments total, zero sin() calls)
                    val cp1X = segStart + halfWave * BEZIER_CONTROL_X
                    val cp2X = segEnd - halfWave * BEZIER_CONTROL_X
                    val cpY = centerY + amp * BEZIER_CONTROL_Y

                    path.cubicTo(cp1X, cpY, cp2X, cpY, segEnd, centerY)
                    segStart = segEnd
                    sign = -sign
                }
                canvas.drawPath(path, wavePaint)
                canvas.restore()
            }
        }

        if (activeWidth < width) {
            canvas.drawLine(activeWidth, centerY, width, centerY, trackPaint)
        }

        if (isVisible && (animateWave || amplitudeFraction > 0.005f)) {
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

    private companion object {
        const val WAVE_LENGTH = 56f
        const val WAVE_HEIGHT = 4.5f
        const val RAMP_DISTANCE = 24f
        const val PHASE_SPEED_PX_PER_SEC = 24f
        const val AMPLITUDE_TRANSITION_SEC = 0.22f
        const val BEZIER_CONTROL_X = 0.3642f
        const val BEZIER_CONTROL_Y = 1.3333f
    }
}
