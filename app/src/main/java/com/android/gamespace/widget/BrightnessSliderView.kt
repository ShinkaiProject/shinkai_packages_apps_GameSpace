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
        max = 255
        progressDrawable = GapSliderTrackDrawable(context)
        setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, progress.coerceAtLeast(1))
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

    private fun sync() { progress = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) }
}
