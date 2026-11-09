package com.android.gamespace.display

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.android.gamespace.R

/**
 * The track of the sliders in the preset editor, drawn like the expressive Material 3 slider:
 * a thick track that is cut in two around the handle, the side that is behind the handle in the
 * accent colour and the side that is ahead of it in a soft container colour with a dot at its end.
 * The handle itself is [R.drawable.display_slider_handle]; this draws everything else, so it
 * has to be used together with it (see `Widget.GameSpace.PresetSlider`).
 *
 * The sizes come from `display_slider.xml`, so that the handle, the track and the height of the
 * slider all change together. It draws from [getLevel] and nothing else: no state, no animation,
 * and nothing is allocated while drawing.
 */
internal class PresetSliderDrawable(context: Context) : Drawable() {

    private val resources = context.resources
    private val handleWidth = resources.getDimension(R.dimen.display_slider_handle_width)
    private val gap = resources.getDimension(R.dimen.display_slider_gap)
    private val outerCorner = resources.getDimension(R.dimen.display_slider_corner)
    private val innerCorner = resources.getDimension(R.dimen.display_slider_inner_corner)
    private val stopRadius = resources.getDimension(R.dimen.display_slider_stop_size) / 2f
    private val stopInset = resources.getDimension(R.dimen.display_slider_stop_inset)

    private val activeColor = themeColor(context, android.R.attr.colorAccent)
    private val inactiveColor = context.getColor(R.color.display_slider_inactive)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val radii = FloatArray(8)
    private var alpha = 255

    private fun themeColor(context: Context, attr: Int): Int {
        val values = context.obtainStyledAttributes(intArrayOf(attr))
        return try {
            values.getColor(0, Color.WHITE)
        } finally {
            values.recycle()
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val left = b.left.toFloat()
        val right = b.right.toFloat()
        val top = b.top.toFloat()
        val bottom = b.bottom.toFloat()
        val outer = minOf(outerCorner, (bottom - top) / 2f)
        val inner = minOf(innerCorner, outer)

        // The slider puts the middle of its handle here (AbsSeekBar, with the handle offset it has
        // by default): the track is as wide as the distance the handle travels.
        val handleX = left + level / LEVEL_MAX * (right - left)
        val activeEnd = handleX - handleWidth / 2f - gap
        val inactiveStart = handleX + handleWidth / 2f + gap

        if (activeEnd > left) {
            segment(canvas, left, top, activeEnd, bottom, outer, inner, activeColor)
        }
        if (inactiveStart < right) {
            segment(canvas, inactiveStart, top, right, bottom, inner, outer, inactiveColor)
            // The dot marks the end of the track. It goes when the handle gets to it.
            val stopX = right - stopInset
            if (stopX - stopRadius >= inactiveStart) {
                paint.color = withAlpha(activeColor)
                canvas.drawCircle(stopX, (top + bottom) / 2f, stopRadius, paint)
            }
        }
    }

    private fun segment(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        leftCorner: Float, rightCorner: Float,
        color: Int,
    ) {
        paint.color = withAlpha(color)
        rect.set(left, top, right, bottom)
        radii[0] = leftCorner; radii[1] = leftCorner      // top left
        radii[2] = rightCorner; radii[3] = rightCorner    // top right
        radii[4] = rightCorner; radii[5] = rightCorner    // bottom right
        radii[6] = leftCorner; radii[7] = leftCorner      // bottom left
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)
        canvas.drawPath(path, paint)
    }

    /** [color] with this drawable's own alpha applied on top of the alpha it has. */
    private fun withAlpha(color: Int): Int =
        if (alpha == 255) color
        else (color and 0x00FFFFFF) or ((Color.alpha(color) * alpha / 255) shl 24)

    override fun onLevelChange(level: Int): Boolean {
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        if (this.alpha == alpha) return
        this.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val LEVEL_MAX = 10000f
    }
}
