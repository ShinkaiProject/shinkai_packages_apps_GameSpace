package com.android.gamespace.widget

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.widget.SeekBar

class BrightnessSliderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SeekBar(context, attrs) {
    private val cr = context.contentResolver
    private var dragging = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { if (!dragging) sync() }
    }

    init {
        max = 1000
        progressDrawable = GapSliderTrackDrawable(context)
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val gamma = progress.toFloat() / max
                    val linearFloat = convertGammaToLinear(gamma)
                    val linearInt = Math.round(linearFloat * 254f + 1f).toInt().coerceIn(1, 255)
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, linearInt)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(sb: SeekBar) { dragging = false }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sync()
        cr.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, observer)
    }

    override fun onDetachedFromWindow() {
        cr.unregisterContentObserver(observer)
        super.onDetachedFromWindow()
    }

    private fun sync() {
        val linearInt = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
        val linearFloat = (linearInt.coerceIn(1, 255) - 1) / 254f
        val gamma = convertLinearToGamma(linearFloat)
        progress = Math.round(gamma * max).toInt().coerceIn(0, max)
    }

    companion object {
        // Hybrid Log Gamma (HLG) constants per AOSP Display BrightnessUtils
        private const val R = 0.5f
        private const val A = 0.17883277f
        private const val B = 0.28466892f
        private const val C = 0.55991073f

        private fun convertGammaToLinear(gamma: Float): Float {
            val ret = if (gamma <= R) {
                (gamma / R) * (gamma / R)
            } else {
                Math.exp(((gamma - C) / A).toDouble()).toFloat() + B
            }
            return (ret.coerceIn(0f, 12f)) / 12f
        }

        private fun convertLinearToGamma(linear: Float): Float {
            val norm = linear * 12f
            return if (norm <= 1f) {
                Math.sqrt(norm.toDouble()).toFloat() * R
            } else {
                A * Math.log((norm - B).toDouble()).toFloat() + C
            }.coerceIn(0f, 1f)
        }
    }
}
